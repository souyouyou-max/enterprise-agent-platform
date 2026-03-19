import { useState } from 'react'
import { Card, Button, Space, Alert, Tag, Typography, Table, Upload, Checkbox } from 'antd'
import { InboxOutlined, PlayCircleOutlined, DeleteOutlined } from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { useConfig } from '@/store/config'
import { apiFetch, toBase64 } from '@/api/client'

const { Dragger } = Upload
const { Text } = Typography

interface FileMeta {
  name: string
  sizeBytes?: number
  sha256?: string
  textLen?: number
  textPreview?: string
}

interface VisualSim {
  ok?: boolean
  avgPageSim?: number
  pageCount?: number
}

interface Comparison {
  a: string
  b: string
  result?: { tfidfCosine?: number; difflibRatio?: number; riskLevel?: string }
  visualSimilarity?: VisualSim
}

interface CompareBase64Data {
  fileMetas?: FileMeta[]
  comparisons?: Comparison[]
  overallRisk?: string
  [key: string]: unknown
}

const RISK_LABEL: Record<string, string> = { high: '高风险', medium: '中风险', low: '低风险' }

export default function CompareBase64Page() {
  const { baseUrl } = useConfig()
  const [files, setFiles] = useState<File[]>([])
  const [loading, setLoading] = useState(false)
  const [includePreview, setIncludePreview] = useState(false)
  const [result, setResult] = useState<{ data: CompareBase64Data; ms: number } | null>(null)
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

  const run = async () => {
    if (files.length < 2) return
    setLoading(true); setError(null)
    try {
      const b64List = await Promise.all(files.map(f => toBase64(f)))
      const apiFiles = files.map((f, i) => ({ filename: f.name, content_b64: b64List[i] }))
      const res = await apiFetch<CompareBase64Data>(baseUrl, '/analyze/compare-base64', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          files: apiFiles,
          include_text_preview: includePreview,
          preview_chars: includePreview ? 500 : 0,
        }),
      })
      setResult(res)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const metaCols = [
    { title: '文件名', dataIndex: 'name', key: 'name', ellipsis: true },
    { title: '大小', dataIndex: 'sizeBytes', key: 'size', render: (v: number) => v ? `${(v / 1024).toFixed(1)} KB` : '—' },
    { title: '文本长度', dataIndex: 'textLen', key: 'textLen', render: (v: number) => v != null ? `${v} 字` : '—' },
    { title: 'SHA256', dataIndex: 'sha256', key: 'sha256', ellipsis: true, render: (v: string) => v ? <Text code style={{ fontSize: 11 }}>{v.slice(0, 16)}…</Text> : '—' },
  ]

  const cmpCols = [
    { title: '文件 A', dataIndex: 'a', key: 'a', ellipsis: true, width: 180 },
    { title: '文件 B', dataIndex: 'b', key: 'b', ellipsis: true, width: 180 },
    {
      title: 'TF-IDF', key: 'tfidf',
      render: (_: unknown, rec: Comparison) => {
        const v = rec.result?.tfidfCosine ?? 0
        const color = v > 0.85 ? '#ff4d4f' : v > 0.5 ? '#faad14' : '#52c41a'
        return <span style={{ color, fontWeight: 600 }}>{(v * 100).toFixed(1)}%</span>
      },
    },
    {
      title: '视觉相似', key: 'visual',
      render: (_: unknown, rec: Comparison) =>
        rec.visualSimilarity?.ok
          ? `${((rec.visualSimilarity.avgPageSim ?? 0) * 100).toFixed(1)}%`
          : '—',
    },
    {
      title: '风险', key: 'risk',
      render: (_: unknown, rec: Comparison) => {
        const r = rec.result?.riskLevel
        return r ? <Tag color={r === 'high' ? 'error' : r === 'medium' ? 'warning' : 'success'}>{RISK_LABEL[r] ?? r}</Tag> : '—'
      },
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title="Base64 文件对比"
        extra={
          <Space>
            <Checkbox checked={includePreview} onChange={e => setIncludePreview(e.target.checked)}>
              包含文本预览
            </Checkbox>
            <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} disabled={files.length < 2} onClick={run}>
              开始对比
            </Button>
          </Space>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}

        <Dragger {...draggerProps} style={{ marginBottom: 16 }}>
          <p><InboxOutlined style={{ fontSize: 32, color: '#8c8c8c' }} /></p>
          <p>拖拽或点击批量上传文件（将以 Base64 编码发送）</p>
          <p style={{ fontSize: 12, color: '#aaa' }}>PDF / DOCX / DOC / TXT，至少 2 个</p>
        </Dragger>

        {files.length > 0 && (
          <Space wrap>
            {files.map(f => (
              <Tag
                key={f.name}
                closable
                onClose={() => setFiles(prev => prev.filter(x => x.name !== f.name))}
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
                <Text type="secondary">{result.ms.toFixed(0)} ms</Text>
              </Space>
            </Card>
          )}

          {result.data.fileMetas && result.data.fileMetas.length > 0 && (
            <Card title="文件元数据">
              <Table
                dataSource={result.data.fileMetas.map((m, i) => ({ ...m, key: i }))}
                columns={metaCols}
                size="small"
                pagination={false}
              />
            </Card>
          )}

          <Card title={`对比矩阵（${result.data.comparisons?.length ?? 0} 对）`}>
            <Table
              dataSource={result.data.comparisons?.map((c, i) => ({ ...c, key: i }))}
              columns={cmpCols}
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
