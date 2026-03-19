import { useState } from 'react'
import { Card, Button, Space, Alert, Tag, Typography, Table, Upload } from 'antd'
import { InboxOutlined, PlayCircleOutlined, DeleteOutlined } from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { useConfig } from '@/store/config'
import { apiFetch } from '@/api/client'

const { Dragger } = Upload
const { Text } = Typography

interface Comparison {
  a: string
  b: string
  result?: {
    tfidfCosine?: number
    riskLevel?: string
  }
  visualSimilarity?: { ok?: boolean; avgPageSim?: number }
}

interface CompareFilesData {
  comparisons?: Comparison[]
  overallRisk?: string
  [key: string]: unknown
}

const RISK_LABEL: Record<string, string> = { high: '高风险', medium: '中风险', low: '低风险' }

export default function CompareFilesPage() {
  const { baseUrl } = useConfig()
  const [files, setFiles] = useState<File[]>([])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<{ data: CompareFilesData; ms: number } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const draggerProps: UploadProps = {
    multiple: true,
    beforeUpload: (file) => {
      setFiles(prev => prev.find(f => f.name === file.name) ? prev : [...prev, file])
      return false
    },
    showUploadList: false,
    accept: '.pdf,.docx,.doc,.txt',
  }

  const removeFile = (name: string) => setFiles(f => f.filter(x => x.name !== name))

  const run = async () => {
    if (files.length < 2) return
    setLoading(true); setError(null)
    try {
      const form = new FormData()
      files.forEach(f => form.append('files', f))
      const res = await apiFetch<CompareFilesData>(baseUrl, '/analyze/compare-files', {
        method: 'POST',
        body: form,
      })
      setResult(res)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const cols = [
    { title: '文件 A', dataIndex: 'a', key: 'a', ellipsis: true, width: 180 },
    { title: '文件 B', dataIndex: 'b', key: 'b', ellipsis: true, width: 180 },
    {
      title: 'TF-IDF',
      key: 'tfidf',
      render: (_: unknown, rec: Comparison) => {
        const v = rec.result?.tfidfCosine ?? 0
        const pct = (v * 100).toFixed(1)
        const color = v > 0.85 ? '#ff4d4f' : v > 0.5 ? '#faad14' : '#52c41a'
        return <span style={{ color, fontWeight: 600 }}>{pct}%</span>
      },
    },
    {
      title: '视觉相似',
      key: 'visual',
      render: (_: unknown, rec: Comparison) =>
        rec.visualSimilarity?.ok
          ? `${((rec.visualSimilarity.avgPageSim ?? 0) * 100).toFixed(1)}%`
          : '—',
    },
    {
      title: '风险',
      key: 'risk',
      render: (_: unknown, rec: Comparison) => {
        const r = rec.result?.riskLevel
        return r ? <Tag color={r === 'high' ? 'error' : r === 'medium' ? 'warning' : 'success'}>{RISK_LABEL[r] ?? r}</Tag> : '—'
      },
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title="多文件对比"
        extra={
          <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} disabled={files.length < 2} onClick={run}>
            开始对比
          </Button>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}

        <Dragger {...draggerProps} style={{ marginBottom: 16 }}>
          <p><InboxOutlined style={{ fontSize: 32, color: '#8c8c8c' }} /></p>
          <p>拖拽或点击批量上传文件</p>
          <p style={{ fontSize: 12, color: '#aaa' }}>PDF / DOCX / DOC / TXT，至少 2 个</p>
        </Dragger>

        {files.length > 0 && (
          <Space wrap>
            {files.map(f => (
              <Tag
                key={f.name}
                closable
                onClose={() => removeFile(f.name)}
                icon={<DeleteOutlined />}
                style={{ padding: '4px 8px' }}
              >
                {f.name} <Text type="secondary" style={{ fontSize: 11 }}>({(f.size / 1024).toFixed(0)}KB)</Text>
              </Tag>
            ))}
          </Space>
        )}
      </Card>

      {result && (
        <>
          {result.data.overallRisk && (
            <Card size="small">
              <Space>
                <Text strong>总体风险：</Text>
                <Tag
                  color={result.data.overallRisk === 'high' ? 'error' : result.data.overallRisk === 'medium' ? 'warning' : 'success'}
                  style={{ fontSize: 14 }}
                >
                  {RISK_LABEL[result.data.overallRisk] ?? result.data.overallRisk}
                </Tag>
                <Text type="secondary">{result.ms.toFixed(0)} ms · {files.length} 个文件</Text>
              </Space>
            </Card>
          )}

          <Card title={`对比矩阵（${result.data.comparisons?.length ?? 0} 对）`}>
            <Table
              dataSource={result.data.comparisons?.map((c, i) => ({ ...c, key: i }))}
              columns={cols}
              size="small"
              pagination={false}
              scroll={{ x: 700 }}
            />
          </Card>

          <Card size="small" title="原始响应" style={{ background: '#1e1e1e' }} styles={{ body: { padding: 0 } }}>
            <pre style={{ margin: 0, padding: 16, color: '#d4d4d4', fontSize: 12, overflow: 'auto', maxHeight: 400 }}>
              {JSON.stringify(result.data, null, 2)}
            </pre>
          </Card>
        </>
      )}
    </Space>
  )
}
