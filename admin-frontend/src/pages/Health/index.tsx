import { useState } from 'react'
import { Card, Button, Row, Col, Statistic, Alert, Typography, Space } from 'antd'
import { HeartOutlined, ClockCircleOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import { useConfig } from '@/store/config'
import { apiFetch } from '@/api/client'

const { Text } = Typography

interface HealthData {
  status?: string
  [key: string]: unknown
}

export default function HealthPage() {
  const { baseUrl } = useConfig()
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<{ data: HealthData; ms: number } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const check = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await apiFetch<HealthData>(baseUrl, '/health')
      setResult(res)
    } catch (e) {
      setError((e as Error).message)
      setResult(null)
    } finally {
      setLoading(false)
    }
  }

  const isOk = result?.data?.status === 'ok'

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={<><HeartOutlined style={{ marginRight: 8 }} />服务健康检查</>}
        extra={<Button type="primary" loading={loading} onClick={check}>发送请求</Button>}
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}

        <Row gutter={24}>
          <Col span={8}>
            <Card variant="borderless" style={{ background: '#fafafa' }}>
              <Statistic
                title="服务状态"
                value={result ? (isOk ? '正常' : '异常') : '未检测'}
                prefix={result ? (isOk
                  ? <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  : <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                ) : <HeartOutlined style={{ color: '#8c8c8c' }} />}
                valueStyle={{ color: result ? (isOk ? '#52c41a' : '#ff4d4f') : '#8c8c8c' }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card variant="borderless" style={{ background: '#fafafa' }}>
              <Statistic
                title="响应延迟"
                value={result ? result.ms : '—'}
                suffix={result ? 'ms' : ''}
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: result && result.ms < 200 ? '#52c41a' : result && result.ms < 1000 ? '#faad14' : '#ff4d4f' }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card variant="borderless" style={{ background: '#fafafa' }}>
              <Statistic
                title="检查时间"
                value={result ? new Date().toLocaleTimeString() : '—'}
                prefix={<ClockCircleOutlined />}
              />
            </Card>
          </Col>
        </Row>

        {result && (
          <Card
            size="small"
            title="响应数据"
            style={{ marginTop: 16, background: '#1e1e1e' }}
            styles={{ body: { padding: 0 } }}
          >
            <pre style={{
              margin: 0,
              padding: 16,
              color: '#d4d4d4',
              fontSize: 12,
              overflow: 'auto',
              maxHeight: 300,
            }}>
              {JSON.stringify(result.data, null, 2)}
            </pre>
          </Card>
        )}
      </Card>

      <Card size="small" style={{ background: '#fffbe6', border: '1px solid #ffe58f' }}>
        <Text type="secondary">接口地址：</Text>
        <Text code>GET {baseUrl}/health</Text>
      </Card>
    </Space>
  )
}
