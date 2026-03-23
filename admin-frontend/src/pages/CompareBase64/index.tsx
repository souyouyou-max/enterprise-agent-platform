import React, { useState } from 'react'
import {
  Card, Button, Space, Alert, Tag, Typography, Table, Upload,
  Checkbox, Tooltip, Progress, Badge, Descriptions, Divider, Collapse,
} from 'antd'
import {
  InboxOutlined, PlayCircleOutlined,
  CheckCircleOutlined, CloseCircleOutlined,
  FileImageOutlined, FilePdfOutlined, FileWordOutlined, FileOutlined,
} from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { useConfig } from '@/store/config'
import { apiFetch, toBase64 } from '@/api/client'

const { Dragger } = Upload
const { Text, Title } = Typography

// ── 类型定义 ──────────────────────────────────────────────────────────────

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
  highSimPages?: number
  totalPagesA?: number
  totalPagesB?: number
}

interface FileSimilarity {
  ok?: boolean
  exactMatch?: boolean
  sizeMatch?: boolean
  sha256A?: string
  sha256B?: string
  sizeA?: number
  sizeB?: number
  visualSim?: number | null
}

interface TextResult {
  ok?: boolean
  reason?: string
  tfidfCosine?: number
  difflibRatio?: number
  longestCommonRunChars?: number
  matchingSegments50?: number
  'matchingSegments50+'?: number
  'commonBlocksCount500+'?: number
  lenA?: number
  lenB?: number
  'commonBlocks500+'?: { size: number; a_pos: number; b_pos: number; snippet: string }[]
}

interface Comparison {
  a: string
  b: string
  result?: TextResult
  visualSimilarity?: VisualSim
  fileSimilarity?: FileSimilarity
  key?: number
}

interface CompareBase64Data {
  fileMetas?: FileMeta[]
  comparisons?: Comparison[]
  summary?: {
    riskLevel?: string
    riskLabel?: string
    maxTfidfCosine?: number
    maxDifflibRatio?: number
    maxLongestCommonRunChars?: number
  }
  [key: string]: unknown
}

// ── 工具函数 ──────────────────────────────────────────────────────────────

const RISK_COLOR: Record<string, string> = {
  high: '#ff4d4f', medium: '#faad14', low: '#52c41a', unknown: '#8c8c8c',
}
const RISK_LABEL: Record<string, string> = {
  high: '高风险', medium: '中风险', low: '低风险', unknown: '未知',
}

function pct(v?: number | null, decimals = 1): string {
  if (v == null) return '—'
  return `${(v * 100).toFixed(decimals)}%`
}

function simColor(v?: number | null): string {
  if (v == null) return '#8c8c8c'
  if (v > 0.85) return '#ff4d4f'
  if (v > 0.5) return '#faad14'
  return '#52c41a'
}

function fileIcon(name: string): React.ReactNode {
  const lower = name.toLowerCase()
  if (lower.endsWith('.pdf')) return <FilePdfOutlined style={{ color: '#ff4d4f' }} />
  if (lower.endsWith('.docx') || lower.endsWith('.doc')) return <FileWordOutlined style={{ color: '#1677ff' }} />
  if (['.png', '.jpg', '.jpeg', '.bmp', '.tiff', '.webp'].some(ext => lower.endsWith(ext)))
    return <FileImageOutlined style={{ color: '#52c41a' }} />
  return <FileOutlined style={{ color: '#8c8c8c' }} />
}

function inferRisk(rec: Comparison): string {
  if (rec.fileSimilarity?.exactMatch) return 'high'
  const tfidf = rec.result?.tfidfCosine ?? 0
  const ratio = rec.result?.difflibRatio ?? 0
  const longest = rec.result?.longestCommonRunChars ?? 0
  const score = tfidf * 0.5 + ratio * 0.3 + (longest > 500 ? 0.2 : longest > 100 ? 0.1 : 0)
  if (score > 0.85) return 'high'
  if (score > 0.5) return 'medium'
  return 'low'
}

// ── 主组件 ────────────────────────────────────────────────────────────────

export default function CompareBase64Page() {
  const { baseUrl } = useConfig()
  const [files, setFiles] = useState<File[]>([])
  const [loading, setLoading] = useState(false)
  const [includePreview, setIncludePreview] = useState(false)
  const [result, setResult] = useState<{ data: CompareBase64Data; ms: number } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([])

  const draggerProps: UploadProps = {
    multiple: true,
    beforeUpload: (file) => {
      setFiles(prev => prev.find(f => f.name === file.name) ? prev : [...prev, file])
      return false
    },
    showUploadList: false,
    accept: '.pdf,.docx,.doc,.txt,.png,.jpg,.jpeg,.bmp,.tiff,.webp',
  }

  const run = async () => {
    if (files.length < 2) return
    setLoading(true)
    setError(null)
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
      setExpandedKeys([])
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  // ── 列定义：文件元数据 ──────────────────────────────────────────────────

  const metaCols = [
    {
      title: '文件名', dataIndex: 'name', key: 'name', ellipsis: true,
      render: (v: string) => (
        <Space size={4}>
          {fileIcon(v)}
          <Text ellipsis style={{ maxWidth: 200 }}>{v}</Text>
        </Space>
      ),
    },
    {
      title: '大小', dataIndex: 'sizeBytes', key: 'size', width: 90,
      render: (v: number) => v ? `${(v / 1024).toFixed(1)} KB` : '—',
    },
    {
      title: '文本长度', dataIndex: 'textLen', key: 'textLen', width: 110,
      render: (v: number) => v != null ? `${v.toLocaleString()} 字` : '—',
    },
    {
      title: 'SHA256', dataIndex: 'sha256', key: 'sha256', ellipsis: true,
      render: (v: string) => v
        ? <Tooltip title={v}><Text code style={{ fontSize: 11 }}>{v.slice(0, 12)}…</Text></Tooltip>
        : '—',
    },
  ]

  // ── 列定义：对比矩阵 ────────────────────────────────────────────────────

  const cmpCols = [
    {
      title: '文件 A', dataIndex: 'a', key: 'a', ellipsis: true, width: 160,
      render: (v: string) => (
        <Space size={4}>{fileIcon(v)}<Text ellipsis style={{ maxWidth: 130 }}>{v}</Text></Space>
      ),
    },
    {
      title: '文件 B', dataIndex: 'b', key: 'b', ellipsis: true, width: 160,
      render: (v: string) => (
        <Space size={4}>{fileIcon(v)}<Text ellipsis style={{ maxWidth: 130 }}>{v}</Text></Space>
      ),
    },
    {
      title: '文字相似度（TF-IDF）', key: 'tfidf', width: 170,
      render: (_: unknown, rec: Comparison) => {
        const v = rec.result?.tfidfCosine
        if (v == null) return <Text type="secondary">—</Text>
        return (
          <Space direction="vertical" size={2} style={{ width: 140 }}>
            <Text style={{ color: simColor(v), fontWeight: 700 }}>{pct(v)}</Text>
            <Progress
              percent={Math.round((v ?? 0) * 100)}
              size="small"
              strokeColor={simColor(v)}
              showInfo={false}
              style={{ marginBottom: 0 }}
            />
          </Space>
        )
      },
    },
    {
      title: '文字相似度（difflib）', key: 'difflib', width: 160,
      render: (_: unknown, rec: Comparison) => {
        const v = rec.result?.difflibRatio
        if (v == null) return <Text type="secondary">—</Text>
        return (
          <Space direction="vertical" size={2} style={{ width: 130 }}>
            <Text style={{ color: simColor(v), fontWeight: 700 }}>{pct(v)}</Text>
            <Progress
              percent={Math.round((v ?? 0) * 100)}
              size="small"
              strokeColor={simColor(v)}
              showInfo={false}
              style={{ marginBottom: 0 }}
            />
          </Space>
        )
      },
    },
    {
      title: '文件整体相似度', key: 'fileSim', width: 180,
      render: (_: unknown, rec: Comparison) => {
        const fs = rec.fileSimilarity
        if (!fs?.ok) return <Text type="secondary">—</Text>
        return (
          <Space direction="vertical" size={4}>
            {fs.exactMatch
              ? (
                <Space size={4}>
                  <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                  <Text style={{ color: '#ff4d4f', fontWeight: 700 }}>文件完全相同</Text>
                </Space>
              )
              : (
                <Space size={4}>
                  <CheckCircleOutlined style={{ color: '#52c41a' }} />
                  <Text style={{ color: '#52c41a' }}>文件内容不同</Text>
                </Space>
              )}
            {fs.visualSim != null && (
              <Text style={{ fontSize: 12 }}>
                视觉哈希均值：
                <span style={{ color: simColor(fs.visualSim), fontWeight: 600 }}>
                  {pct(fs.visualSim)}
                </span>
              </Text>
            )}
          </Space>
        )
      },
    },
    {
      title: '视觉相似（PDF分页）', key: 'visual', width: 150,
      render: (_: unknown, rec: Comparison) => {
        const vs = rec.visualSimilarity
        if (!vs?.ok) return <Text type="secondary">—</Text>
        return (
          <Tooltip title={`高相似页：${vs.highSimPages ?? 0} / ${vs.totalPagesA ?? 0}`}>
            <Text style={{ color: simColor(vs.avgPageSim) }}>{pct(vs.avgPageSim)}</Text>
          </Tooltip>
        )
      },
    },
    {
      title: '综合风险', key: 'risk', width: 100, fixed: 'right' as const,
      render: (_: unknown, rec: Comparison) => {
        const level = inferRisk(rec)
        return (
          <Tag
            color={level === 'high' ? 'error' : level === 'medium' ? 'warning' : 'success'}
            style={{ fontWeight: 600 }}
          >
            {RISK_LABEL[level]}
          </Tag>
        )
      },
    },
  ]

  // ── 展开行：详细信息 ────────────────────────────────────────────────────

  const renderExpandRow = (rec: Comparison) => {
    const r = rec.result
    const fs = rec.fileSimilarity
    const vs = rec.visualSimilarity

    return (
      <div style={{ padding: '8px 32px 16px' }}>
        {/* 核心指标 */}
        <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="TF-IDF 余弦（文字）">
            <Text style={{ color: simColor(r?.tfidfCosine), fontWeight: 700 }}>{pct(r?.tfidfCosine)}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="difflib ratio（文字）">
            <Text style={{ color: simColor(r?.difflibRatio), fontWeight: 700 }}>{pct(r?.difflibRatio)}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="最长公共连续块">
            <b>{r?.longestCommonRunChars?.toLocaleString() ?? '—'}</b> 字符
          </Descriptions.Item>
          <Descriptions.Item label="匹配片段（≥50字符）">
            {r?.['matchingSegments50+'] ?? r?.matchingSegments50 ?? '—'} 个
          </Descriptions.Item>
          <Descriptions.Item label="公共块（≥500字符）">
            {r?.['commonBlocksCount500+'] ?? 0} 处
          </Descriptions.Item>
          <Descriptions.Item label="文字来源长度">
            A：{r?.lenA?.toLocaleString() ?? '—'} 字 &nbsp;|&nbsp; B：{r?.lenB?.toLocaleString() ?? '—'} 字
          </Descriptions.Item>

          {/* 文件整体相似度 */}
          <Descriptions.Item label="文件整体对比（SHA-256）">
            {fs?.ok
              ? fs.exactMatch
                ? <Badge status="error" text={<Text type="danger">完全相同（高风险）</Text>} />
                : <Badge status="success" text={<Text type="success">文件内容不同</Text>} />
              : <Text type="secondary">—</Text>}
          </Descriptions.Item>
          <Descriptions.Item label="文件整体视觉哈希均值">
            {fs?.visualSim != null
              ? <Text style={{ color: simColor(fs.visualSim), fontWeight: 600 }}>{pct(fs.visualSim)}</Text>
              : <Text type="secondary">不支持（非 PDF / 图片）</Text>}
          </Descriptions.Item>
          <Descriptions.Item label="文件大小">
            A：{fs?.sizeA ? `${(fs.sizeA / 1024).toFixed(1)} KB` : '—'} &nbsp;|&nbsp;
            B：{fs?.sizeB ? `${(fs.sizeB / 1024).toFixed(1)} KB` : '—'}
          </Descriptions.Item>

          {/* PDF 分页视觉 */}
          {vs?.ok && (
            <>
              <Descriptions.Item label="PDF 分页视觉均值">
                <Text style={{ color: simColor(vs.avgPageSim), fontWeight: 600 }}>{pct(vs.avgPageSim)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="高相似页">
                {vs.highSimPages ?? 0} / {vs.totalPagesA ?? 0} 页
              </Descriptions.Item>
              <Descriptions.Item label="—">
                <Text type="secondary">—</Text>
              </Descriptions.Item>
            </>
          )}
        </Descriptions>

        {/* SHA256 */}
        {(fs?.sha256A || fs?.sha256B) && (
          <Descriptions bordered size="small" column={1} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="文件A SHA-256">
              <Text code style={{ fontSize: 11 }}>{fs?.sha256A ?? '—'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="文件B SHA-256">
              <Text code style={{ fontSize: 11 }}>{fs?.sha256B ?? '—'}</Text>
            </Descriptions.Item>
          </Descriptions>
        )}

        {/* 公共块片段列表 */}
        {(r?.['commonBlocks500+']?.length ?? 0) > 0 && (
          <Collapse ghost size="small">
            <Collapse.Panel
              header={
                <Text type="secondary">
                  公共块详情（≥500字符，共 {r!['commonBlocks500+']!.length} 处）
                </Text>
              }
              key="blocks"
            >
              {r!['commonBlocks500+']!.map((blk, idx) => (
                <Card key={idx} size="small" style={{ marginBottom: 8, background: '#fffbe6' }}>
                  <Space size={16} style={{ marginBottom: 6 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>块大小：<b>{blk.size}</b> 字符</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>A 起始位置：{blk.a_pos}</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>B 起始位置：{blk.b_pos}</Text>
                  </Space>
                  <pre style={{
                    margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', color: '#595959',
                    background: '#fffff8', padding: 8, borderRadius: 4, border: '1px solid #ffd666',
                  }}>
                    {blk.snippet}
                    {blk.size > blk.snippet.length ? '…（截断）' : ''}
                  </pre>
                </Card>
              ))}
            </Collapse.Panel>
          </Collapse>
        )}
      </div>
    )
  }

  // ── 渲染 ──────────────────────────────────────────────────────────────

  const summary = result?.data?.summary

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>

      {/* 上传区 */}
      <Card
        title="多附件相似度对比"
        extra={
          <Space>
            <Checkbox checked={includePreview} onChange={e => setIncludePreview(e.target.checked)}>
              包含文本预览
            </Checkbox>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={loading}
              disabled={files.length < 2}
              onClick={run}
            >
              开始对比（{files.length} 个文件）
            </Button>
          </Space>
        }
      >
        {error && (
          <Alert type="error" message={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
        )}

        <Dragger {...draggerProps} style={{ marginBottom: 16 }}>
          <p><InboxOutlined style={{ fontSize: 36, color: '#8c8c8c' }} /></p>
          <p style={{ fontSize: 15 }}>拖拽或点击批量上传文件</p>
          <p style={{ fontSize: 12, color: '#aaa' }}>
            PDF / DOCX / DOC / TXT 及图片（PNG / JPG / JPEG / BMP / TIFF / WEBP），至少上传 2 个文件
          </p>
        </Dragger>

        {files.length > 0 && (
          <Space wrap style={{ marginTop: 4 }}>
            {files.map(f => (
              <Tag
                key={f.name}
                closable
                onClose={() => setFiles(prev => prev.filter(x => x.name !== f.name))}
                icon={fileIcon(f.name)}
                style={{ padding: '4px 10px', fontSize: 13, userSelect: 'none' }}
              >
                {f.name}
                <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
                  ({(f.size / 1024).toFixed(0)} KB)
                </Text>
              </Tag>
            ))}
          </Space>
        )}
      </Card>

      {/* ── 结果区 ── */}
      {result && (
        <>
          {/* 总体风险摘要 */}
          {summary && (
            <Card
              style={{
                background: summary.riskLevel === 'high'
                  ? '#fff2f0' : summary.riskLevel === 'medium' ? '#fffbe6' : '#f6ffed',
                border: `1px solid ${RISK_COLOR[summary.riskLevel ?? 'unknown']}55`,
              }}
            >
              <Space size={32} align="center" wrap>
                <Space direction="vertical" size={4}>
                  <Text type="secondary" style={{ fontSize: 12 }}>综合风险等级</Text>
                  <Tag
                    color={summary.riskLevel === 'high' ? 'error' : summary.riskLevel === 'medium' ? 'warning' : 'success'}
                    style={{ fontSize: 16, padding: '4px 16px', fontWeight: 700, margin: 0 }}
                  >
                    {RISK_LABEL[summary.riskLevel ?? 'unknown']}
                  </Tag>
                  {summary.riskLabel && (
                    <Text style={{ color: RISK_COLOR[summary.riskLevel ?? 'unknown'] }}>
                      {summary.riskLabel}
                    </Text>
                  )}
                </Space>

                <Divider type="vertical" style={{ height: 64 }} />

                {[
                  { label: '最高 TF-IDF', value: pct(summary.maxTfidfCosine), color: simColor(summary.maxTfidfCosine) },
                  { label: '最高 difflib', value: pct(summary.maxDifflibRatio), color: simColor(summary.maxDifflibRatio) },
                  { label: '最长公共块', value: `${summary.maxLongestCommonRunChars?.toLocaleString() ?? '—'} 字符`, color: '#262626' },
                  { label: '接口耗时', value: `${result.ms.toFixed(0)} ms`, color: '#595959' },
                ].map(({ label, value, color }) => (
                  <Space key={label} direction="vertical" size={2} style={{ textAlign: 'center' }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>{label}</Text>
                    <Text style={{ fontSize: 22, fontWeight: 700, color }}>{value}</Text>
                  </Space>
                ))}
              </Space>
            </Card>
          )}

          {/* 文件元数据 */}
          {result.data.fileMetas && result.data.fileMetas.length > 0 && (
            <Card
              title={
                <Space>
                  <Title level={5} style={{ margin: 0 }}>文件信息</Title>
                  <Tag>{result.data.fileMetas.length} 个</Tag>
                </Space>
              }
              size="small"
            >
              <Table
                dataSource={result.data.fileMetas.map((m, i) => ({ ...m, key: i }))}
                columns={metaCols}
                size="small"
                pagination={false}
                expandable={includePreview ? {
                  expandedRowRender: (row: FileMeta & { key: number }) =>
                    row.textPreview
                      ? (
                        <pre style={{
                          margin: 0, padding: '8px 32px', fontSize: 12,
                          whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'auto', color: '#595959',
                        }}>
                          {row.textPreview}
                        </pre>
                      )
                      : <Text type="secondary" style={{ paddingLeft: 32 }}>（无文本预览）</Text>,
                } : undefined}
              />
            </Card>
          )}

          {/* 对比矩阵 */}
          <Card
            title={
              <Space>
                <Title level={5} style={{ margin: 0 }}>相似度对比矩阵</Title>
                <Tag>{result.data.comparisons?.length ?? 0} 对</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>点击行可展开详细数据</Text>
              </Space>
            }
          >
            <Table
              dataSource={result.data.comparisons?.map((c, i) => ({ ...c, key: i })) ?? []}
              columns={cmpCols}
              size="small"
              pagination={false}
              scroll={{ x: 1100 }}
              expandable={{
                expandedRowKeys: expandedKeys,
                onExpand: (expanded, record: Comparison) => {
                  const key = record.key as React.Key
                  setExpandedKeys(expanded
                    ? [...expandedKeys, key]
                    : expandedKeys.filter(k => k !== key)
                  )
                },
                expandedRowRender: (rec: Comparison) => renderExpandRow(rec),
              }}
              rowClassName={(rec: Comparison) => {
                const level = inferRisk(rec)
                return level === 'high' ? 'row-high-risk' : ''
              }}
            />
          </Card>

          {/* 原始响应（可折叠） */}
          <Collapse ghost>
            <Collapse.Panel header={<Text type="secondary">原始 JSON 响应</Text>} key="raw">
              <pre style={{
                margin: 0, padding: 16, background: '#1e1e1e', color: '#d4d4d4',
                fontSize: 12, borderRadius: 8, overflow: 'auto', maxHeight: 500,
              }}>
                {JSON.stringify(result.data, null, 2)}
              </pre>
            </Collapse.Panel>
          </Collapse>
        </>
      )}

      {/* 高风险行样式覆盖 */}
      <style>{`
        .row-high-risk > td { background: #fff2f0 !important; }
        .row-high-risk:hover > td { background: #ffe8e6 !important; }
      `}</style>
    </Space>
  )
}
