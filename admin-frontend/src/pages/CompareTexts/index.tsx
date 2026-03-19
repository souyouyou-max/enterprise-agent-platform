import { useState } from 'react'
import { Card, Button, Input, Space, Alert, Tag, Typography, Switch, Table, Row, Col, Statistic, type TableProps } from 'antd'
import { PlusOutlined, MinusCircleOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { useConfig } from '@/store/config'
import { apiFetch } from '@/api/client'

const { TextArea } = Input
const { Text } = Typography

interface Comparison {
  a: string
  b: string
  result: {
    tfidfCosine?: number
    difflibRatio?: number
    commonBlocks?: number
    riskLevel?: string
  }
}

interface CompareTextsData {
  comparisons?: Comparison[]
  priceStats?: {
    mean?: number
    stdDev?: number
    hasDuplicate?: boolean
    concentrationRatio?: number
  }
  overallRisk?: string
  [key: string]: unknown
}

const RISK_COLOR: Record<string, string> = { high: '#ff4d4f', medium: '#faad14', low: '#52c41a' }
const RISK_LABEL: Record<string, string> = { high: '高风险', medium: '中风险', low: '低风险' }

export default function CompareTextsPage() {
  const { baseUrl } = useConfig()
  const [texts, setTexts] = useState(['', ''])
  const [prices, setPrices] = useState<string[]>([])
  const [usePrices, setUsePrices] = useState(false)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<{ data: CompareTextsData; ms: number } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const addText = () => {
    setTexts(t => [...t, ''])
    if (usePrices) setPrices(p => [...p, ''])
  }

  const removeText = (i: number) => {
    setTexts(t => t.filter((_, idx) => idx !== i))
    if (usePrices) setPrices(p => p.filter((_, idx) => idx !== i))
  }

  const run = async () => {
    if (texts.filter(t => t.trim()).length < 2) return
    setLoading(true); setError(null)
    try {
      const body: Record<string, unknown> = { texts }
      if (usePrices && prices.some(p => p.trim())) {
        body.prices = prices.map(p => parseFloat(p) || 0)
        body.include_price_analysis = true
      }
      const res = await apiFetch<CompareTextsData>(baseUrl, '/analyze/compare-texts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      setResult(res)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const cols: TableProps<Comparison & { key: number }>['columns'] = [
    { title: '文件 A', dataIndex: 'a', key: 'a', ellipsis: true, width: 150 },
    { title: '文件 B', dataIndex: 'b', key: 'b', ellipsis: true, width: 150 },
    {
      title: 'TF-IDF', key: 'tfidf',
      render: (_: unknown, rec) => {
        const v = rec.result?.tfidfCosine ?? 0
        const pct = (v * 100).toFixed(1)
        const color = v > 0.85 ? '#ff4d4f' : v > 0.5 ? '#faad14' : '#52c41a'
        return <span style={{ color, fontWeight: 600 }}>{pct}%</span>
      },
    },
    {
      title: '风险', key: 'risk',
      render: (_: unknown, rec) => {
        const v = rec.result?.riskLevel
        return v ? <Tag color={v === 'high' ? 'error' : v === 'medium' ? 'warning' : 'success'}>{RISK_LABEL[v] ?? v}</Tag> : '—'
      },
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title="多文本对比"
        extra={
          <Space>
            <Text>价格分析</Text>
            <Switch checked={usePrices} onChange={v => { setUsePrices(v); if (v) setPrices(texts.map(() => '')) }} />
            <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} onClick={run}>开始对比</Button>
          </Space>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
        <Space direction="vertical" style={{ width: '100%' }}>
          {texts.map((t, i) => (
            <Row key={i} gutter={8} align="top">
              <Col flex="none">
                <Text style={{ lineHeight: '32px', width: 60, display: 'inline-block' }}>文本 {i + 1}</Text>
              </Col>
              {usePrices && (
                <Col flex="none">
                  <Input
                    style={{ width: 120 }}
                    placeholder="报价金额"
                    value={prices[i] ?? ''}
                    onChange={e => setPrices(p => { const n = [...p]; n[i] = e.target.value; return n })}
                    prefix="¥"
                  />
                </Col>
              )}
              <Col flex="1">
                <TextArea
                  rows={4}
                  value={t}
                  onChange={e => setTexts(arr => { const n = [...arr]; n[i] = e.target.value; return n })}
                  placeholder={`文本 ${i + 1}…`}
                  style={{ fontFamily: 'monospace', fontSize: 12 }}
                />
              </Col>
              {texts.length > 2 && (
                <Col flex="none">
                  <Button danger icon={<MinusCircleOutlined />} onClick={() => removeText(i)} />
                </Col>
              )}
            </Row>
          ))}
          <Button icon={<PlusOutlined />} onClick={addText} style={{ width: '100%' }}>添加文本</Button>
        </Space>
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

          {result.data.priceStats && (
            <Card title="价格分析">
              <Row gutter={16}>
                <Col span={6}><Statistic title="均值" value={(result.data.priceStats.mean ?? 0).toFixed(0)} prefix="¥" /></Col>
                <Col span={6}><Statistic title="标准差" value={(result.data.priceStats.stdDev ?? 0).toFixed(0)} prefix="¥" /></Col>
                <Col span={6}><Statistic title="集中度" value={((result.data.priceStats.concentrationRatio ?? 0) * 100).toFixed(1)} suffix="%" /></Col>
                <Col span={6}>
                  <div>
                    <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>价格重复</div>
                    <Tag color={result.data.priceStats.hasDuplicate ? 'error' : 'success'}>
                      {result.data.priceStats.hasDuplicate ? '存在重复' : '无重复'}
                    </Tag>
                  </div>
                </Col>
              </Row>
            </Card>
          )}

          <Card title={`对比矩阵（共 ${result.data.comparisons?.length ?? 0} 对）`}>
            <Table
              dataSource={result.data.comparisons?.map((c, i) => ({ ...c, key: i }))}
              columns={cols}
              size="small"
              pagination={false}
              scroll={{ x: 600 }}
            />
          </Card>

          <Card size="small" title="原始响应" style={{ background: '#1e1e1e' }} styles={{ body: { padding: 0 } }}>
            <pre style={{ margin: 0, padding: 16, color: '#d4d4d4', fontSize: 12, overflow: 'auto', maxHeight: 400 }}>
              {JSON.stringify(result.data, null, 2)}
            </pre>
          </Card>
        </>
      )}

      {/* Risk legend */}
      <Card size="small">
        <Space>
          {Object.entries(RISK_COLOR).map(([k, c]) => (
            <span key={k}><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: c, marginRight: 4 }} /><Text style={{ color: c }}>{RISK_LABEL[k]}</Text></span>
          ))}
          <Text type="secondary" style={{ marginLeft: 8 }}>高风险：TF-IDF &gt; 85%</Text>
        </Space>
      </Card>
    </Space>
  )
}
