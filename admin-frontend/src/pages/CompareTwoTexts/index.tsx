import { useState } from 'react'
import { Card, Button, Row, Col, Input, Space, Alert, Statistic, Tag, Typography } from 'antd'
import { FileTextOutlined, SwapOutlined } from '@ant-design/icons'
import { useConfig } from '@/store/config'
import { apiFetch } from '@/api/client'

const { TextArea } = Input
const { Text } = Typography

const SAMPLE_A = `甲方：ABC科技有限公司
工程名称：办公楼改造项目
合同金额：人民币壹佰万元整（¥1,000,000）
工期：自合同签订之日起180日历天内竣工`

const SAMPLE_B = `甲方：ABC科技有限公司
工程名称：办公楼改造项目
合同金额：人民币壹佰叁拾万元整（¥1,300,000）
工期：自合同签订之日起240日历天内竣工`

interface CompareResult {
  tfidfCosine?: number
  difflibRatio?: number
  commonBlocks?: number
  matchingSegments?: number
  riskLevel?: string
  [key: string]: unknown
}

export default function CompareTwoTextsPage() {
  const { baseUrl } = useConfig()
  const [textA, setTextA] = useState('')
  const [textB, setTextB] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<{ data: CompareResult; ms: number } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async () => {
    if (!textA.trim() || !textB.trim()) return
    setLoading(true)
    setError(null)
    try {
      const res = await apiFetch<CompareResult>(baseUrl, '/analyze/compare-two-texts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text_a: textA, text_b: textB }),
      })
      setResult(res)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const riskColor = (level?: string) =>
    level === 'high' ? 'error' : level === 'medium' ? 'warning' : 'success'

  const riskLabel = (level?: string) =>
    level === 'high' ? '高风险' : level === 'medium' ? '中风险' : '低风险'

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={<><FileTextOutlined style={{ marginRight: 8 }} />两文本对比</>}
        extra={
          <Space>
            <Button onClick={() => { setTextA(SAMPLE_A); setTextB(SAMPLE_B) }}>载入示例</Button>
            <Button type="primary" loading={loading} icon={<SwapOutlined />} onClick={run}>
              开始对比
            </Button>
          </Space>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
        <Row gutter={16}>
          <Col span={12}>
            <Text strong>文本 A</Text>
            <TextArea
              value={textA}
              onChange={e => setTextA(e.target.value)}
              rows={10}
              placeholder="粘贴文本 A…"
              style={{ marginTop: 8, fontFamily: 'monospace', fontSize: 12 }}
            />
          </Col>
          <Col span={12}>
            <Text strong>文本 B</Text>
            <TextArea
              value={textB}
              onChange={e => setTextB(e.target.value)}
              rows={10}
              placeholder="粘贴文本 B…"
              style={{ marginTop: 8, fontFamily: 'monospace', fontSize: 12 }}
            />
          </Col>
        </Row>
      </Card>

      {result && (
        <Card title="对比结果" extra={<Text type="secondary">{result.ms.toFixed(0)} ms</Text>}>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={6}>
              <Statistic
                title="TF-IDF 余弦相似度"
                value={((result.data.tfidfCosine ?? 0) * 100).toFixed(1)}
                suffix="%"
                valueStyle={{ color: (result.data.tfidfCosine ?? 0) > 0.85 ? '#ff4d4f' : '#52c41a' }}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Difflib 相似度"
                value={((result.data.difflibRatio ?? 0) * 100).toFixed(1)}
                suffix="%"
              />
            </Col>
            <Col span={6}>
              <Statistic title="共同块数" value={result.data.commonBlocks ?? 0} />
            </Col>
            <Col span={6}>
              <Statistic title="匹配段落" value={result.data.matchingSegments ?? 0} />
            </Col>
          </Row>
          {result.data.riskLevel && (
            <div style={{ marginBottom: 16 }}>
              <Text>风险等级：</Text>
              <Tag color={riskColor(result.data.riskLevel)} style={{ fontSize: 13 }}>
                {riskLabel(result.data.riskLevel)}
              </Tag>
            </div>
          )}
          <Card size="small" title="原始响应" style={{ background: '#1e1e1e' }} styles={{ body: { padding: 0 } }}>
            <pre style={{ margin: 0, padding: 16, color: '#d4d4d4', fontSize: 12, overflow: 'auto', maxHeight: 300 }}>
              {JSON.stringify(result.data, null, 2)}
            </pre>
          </Card>
        </Card>
      )}
    </Space>
  )
}
