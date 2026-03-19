import { useState, useRef } from 'react'
import { Card, Button, Row, Col, Space, Alert, Statistic, Tag, Typography, Upload } from 'antd'
import { InboxOutlined, SwapOutlined, FileOutlined } from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { useConfig } from '@/store/config'
import { apiFetch } from '@/api/client'

const { Dragger } = Upload
const { Text } = Typography

interface VisualSim {
  ok?: boolean
  avgPageSim?: number
  pageCount?: number
}

interface CompareResult {
  tfidfCosine?: number
  difflibRatio?: number
  commonBlocks?: number
  matchingSegments?: number
  riskLevel?: string
  visualSimilarity?: VisualSim
  [key: string]: unknown
}

function FileDropZone({ label, file, onFile }: { label: string; file: File | null; onFile: (f: File) => void }) {
  const inputRef = useRef<HTMLInputElement>(null)

  const draggerProps: UploadProps = {
    multiple: false,
    beforeUpload: (file) => { onFile(file); return false },
    showUploadList: false,
    accept: '.pdf,.docx,.doc,.txt',
  }

  return (
    <div>
      <Text strong style={{ display: 'block', marginBottom: 8 }}>{label}</Text>
      <Dragger {...draggerProps} style={{ padding: '8px 0' }}>
        {file ? (
          <div style={{ padding: '16px 0' }}>
            <FileOutlined style={{ fontSize: 32, color: '#1677ff' }} />
            <p style={{ margin: '8px 0 0', fontWeight: 600 }}>{file.name}</p>
            <p style={{ color: '#888', fontSize: 12 }}>{(file.size / 1024).toFixed(1)} KB</p>
          </div>
        ) : (
          <div style={{ padding: '16px 0' }}>
            <InboxOutlined style={{ fontSize: 32, color: '#8c8c8c' }} />
            <p style={{ margin: '8px 0 0', color: '#666' }}>拖拽或点击上传</p>
            <p style={{ color: '#aaa', fontSize: 12 }}>PDF / DOCX / DOC / TXT</p>
          </div>
        )}
      </Dragger>
      <input ref={inputRef} type="file" style={{ display: 'none' }} />
    </div>
  )
}

export default function CompareTwoFilesPage() {
  const { baseUrl } = useConfig()
  const [fileA, setFileA] = useState<File | null>(null)
  const [fileB, setFileB] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<{ data: CompareResult; ms: number } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async () => {
    if (!fileA || !fileB) return
    setLoading(true); setError(null)
    try {
      const form = new FormData()
      form.append('file_a', fileA)
      form.append('file_b', fileB)
      const res = await apiFetch<CompareResult>(baseUrl, '/analyze/compare-two-files', {
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

  const d = result?.data
  const riskLabel = d?.riskLevel === 'high' ? '高风险' : d?.riskLevel === 'medium' ? '中风险' : '低风险'

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={<><SwapOutlined style={{ marginRight: 8 }} />两文件对比</>}
        extra={
          <Button type="primary" loading={loading} disabled={!fileA || !fileB} onClick={run} icon={<SwapOutlined />}>
            开始对比
          </Button>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
        <Row gutter={24}>
          <Col span={12}><FileDropZone label="文件 A" file={fileA} onFile={setFileA} /></Col>
          <Col span={12}><FileDropZone label="文件 B" file={fileB} onFile={setFileB} /></Col>
        </Row>
      </Card>

      {d && (
        <Card title="对比结果" extra={<Text type="secondary">{result!.ms.toFixed(0)} ms</Text>}>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={5}>
              <Statistic
                title="TF-IDF 余弦相似度"
                value={((d.tfidfCosine ?? 0) * 100).toFixed(1)}
                suffix="%"
                valueStyle={{ color: (d.tfidfCosine ?? 0) > 0.85 ? '#ff4d4f' : '#52c41a' }}
              />
            </Col>
            <Col span={5}>
              <Statistic
                title="Difflib 相似度"
                value={((d.difflibRatio ?? 0) * 100).toFixed(1)}
                suffix="%"
              />
            </Col>
            <Col span={4}>
              <Statistic title="共同块" value={d.commonBlocks ?? 0} />
            </Col>
            <Col span={4}>
              <Statistic title="匹配段落" value={d.matchingSegments ?? 0} />
            </Col>
            <Col span={3}>
              <Statistic
                title="视觉相似"
                value={d.visualSimilarity?.ok ? ((d.visualSimilarity.avgPageSim ?? 0) * 100).toFixed(1) : '—'}
                suffix={d.visualSimilarity?.ok ? '%' : ''}
              />
            </Col>
            <Col span={3}>
              {d.riskLevel && (
                <div>
                  <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>风险等级</div>
                  <Tag color={d.riskLevel === 'high' ? 'error' : d.riskLevel === 'medium' ? 'warning' : 'success'} style={{ fontSize: 14 }}>
                    {riskLabel}
                  </Tag>
                </div>
              )}
            </Col>
          </Row>

          <Card size="small" title="原始响应" style={{ background: '#1e1e1e' }} styles={{ body: { padding: 0 } }}>
            <pre style={{ margin: 0, padding: 16, color: '#d4d4d4', fontSize: 12, overflow: 'auto', maxHeight: 400 }}>
              {JSON.stringify(d, null, 2)}
            </pre>
          </Card>
        </Card>
      )}
    </Space>
  )
}
