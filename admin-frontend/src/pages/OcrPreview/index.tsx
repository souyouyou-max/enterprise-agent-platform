import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Input,
  Modal,
  Space,
  Select,
  Table,
  Tag,
  Typography,
} from 'antd'
import { LeftOutlined, RightOutlined, RotateLeftOutlined, RotateRightOutlined } from '@ant-design/icons'
import type { TableProps } from 'antd'
import { apiFetch } from '@/api/client'

const { Text } = Typography

interface OcrFileMainPreview {
  // id 很大时会超出 JS Number 精度范围，必须用 string。
  id: string
  businessNo?: string
  source?: string
  fileName?: string
  fileType?: string
  fileSize?: number
  ocrStatus?: string
  totalPages?: number
  errorMessage?: string
  ocrResultPreview?: string
  prompt?: string
  createdAt?: string
  updatedAt?: string
}

interface OcrFileSplitPreview {
  id: string
  splitIndex?: number
  pageNo?: number
  filePath?: string
  fileType?: string
  fileSize?: number
  ocrStatus?: string
  errorMessage?: string
  prompt?: string
  ocrResult?: string
  imageBase64?: string | null
}

function safeToString(v: unknown): string {
  return v == null ? '' : String(v)
}

function truncate(s: string, maxLen: number): string {
  if (!s) return ''
  return s.length <= maxLen ? s : s.slice(0, maxLen) + '…'
}

function formatDate(s?: string): string {
  if (!s) return '—'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  return d.toLocaleString()
}

function statusTag(status?: string) {
  const st = status ?? ''
  const upper = st.toUpperCase()
  const color = upper === 'SUCCESS' ? 'success' : upper === 'FAILED' ? 'error' : upper === 'PROCESSING' ? 'processing' : 'default'
  return <Tag color={color}>{upper || '—'}</Tag>
}

function mimeFromFileType(fileType?: string): string {
  const t = (fileType ?? '').toLowerCase()
  if (t.includes('png')) return 'image/png'
  if (t.includes('tiff') || t.includes('tif')) return 'image/tiff'
  if (t.includes('jpg') || t.includes('jpeg')) return 'image/jpeg'
  return 'image/*'
}

export default function OcrPreviewPage() {
  // 统一经网关访问，避免前端直连后端服务。
  const eapBaseUrl = 'http://localhost:8079'

  const [loadingMain, setLoadingMain] = useState(false)
  const [loadingSplits, setLoadingSplits] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [source, setSource] = useState<string | undefined>(undefined)
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [limit, setLimit] = useState(50)

  const [files, setFiles] = useState<OcrFileMainPreview[]>([])
  const [selectedMainId, setSelectedMainId] = useState<string | null>(null)
  const [splits, setSplits] = useState<OcrFileSplitPreview[]>([])

  const [includeImageBase64, setIncludeImageBase64] = useState(false)
  const [imageModalSplit, setImageModalSplit] = useState<OcrFileSplitPreview | null>(null)
  const [imageRotateDeg, setImageRotateDeg] = useState(0)
  const imageModalOpen = imageModalSplit != null
  // refresh 后自动兜底：如果默认选中的 mainId 分片为空，则尝试切换到下一条。
  const autoPickSplitsEnabledRef = useRef(false)
  const autoPickTriesRef = useRef(0)

  const refresh = async () => {
    setError(null)
    setLoadingMain(true)
    autoPickSplitsEnabledRef.current = true
    autoPickTriesRef.current = 0
    try {
      const params = new URLSearchParams()
      if (source) params.set('source', source)
      if (status) params.set('status', status)
      params.set('limit', String(limit))
      const res = await apiFetch<unknown>(
        eapBaseUrl,
        `/api/v1/admin/ocr/files?${params.toString()}`,
      )
      const unwrapped = unwrapData<any>(res.data)
      setFiles(unwrapped ?? [])
      // 默认先选第一条；如果这条的 splits 为空，loadSplits 会自动切到下一条。
      if (unwrapped?.length) setSelectedMainId(unwrapped[0].id)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoadingMain(false)
    }
  }

  const loadSplits = async (mainId: string) => {
    setError(null)
    setLoadingSplits(true)
    try {
      const res = await apiFetch<unknown>(
        eapBaseUrl,
        `/api/v1/admin/ocr/files/${mainId}/splits?includeImageBase64=${includeImageBase64}`,
      )
      const unwrapped = unwrapData<any>(res.data)
      const nextSplits = unwrapped ?? []
      setSplits(nextSplits)

      // refresh 之后自动兜底：如果当前 mainId 没有分片，则尝试选择下一条 main。
      if (
        autoPickSplitsEnabledRef.current
        && nextSplits.length === 0
        && files.length > 1
        && autoPickTriesRef.current < files.length - 1
      ) {
        autoPickTriesRef.current += 1
        const currentIndex = files.findIndex(f => f.id === mainId)
        const nextIndex = currentIndex >= 0 ? currentIndex + 1 : 0
        const nextMainId = files[nextIndex]?.id ?? files.find(f => f.id !== mainId)?.id
        if (nextMainId != null && nextMainId !== mainId) {
          setSelectedMainId(nextMainId)
        }
      } else {
        // 找到非空分片后结束自动兜底。
        autoPickSplitsEnabledRef.current = false
      }
    } catch (e) {
      setError((e as Error).message)
      setSplits([])
    } finally {
      setLoadingSplits(false)
    }
  }

  const unwrapData = <T,>(value: unknown): T | null => {
    let cur: any = value
    for (let i = 0; i < 4; i += 1) {
      if (cur && typeof cur === 'object' && 'data' in cur && typeof cur.data === 'object') {
        cur = cur.data
      } else {
        break
      }
    }
    return (cur ?? null) as T | null
  }

  useEffect(() => {
    refresh().catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (selectedMainId != null) {
      loadSplits(selectedMainId).catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedMainId, includeImageBase64])

  useEffect(() => {
    setImageRotateDeg(0)
  }, [imageModalSplit?.id])

  const modalSplitIndex = imageModalSplit
    ? splits.findIndex((s) => s.id === imageModalSplit.id)
    : -1
  const hasPrevSplit = modalSplitIndex > 0
  const hasNextSplit = modalSplitIndex >= 0 && modalSplitIndex < splits.length - 1
  const openPrevSplit = () => {
    if (!hasPrevSplit) return
    const prev = splits[modalSplitIndex - 1]
    if (prev) setImageModalSplit(prev)
  }
  const openNextSplit = () => {
    if (!hasNextSplit) return
    const next = splits[modalSplitIndex + 1]
    if (next) setImageModalSplit(next)
  }

  const mainColumns = useMemo<TableProps<OcrFileMainPreview>['columns']>(() => ([
    {
      title: '业务号',
      dataIndex: 'businessNo',
      key: 'businessNo',
      ellipsis: true,
      render: v => <Text style={{ maxWidth: 180 }} ellipsis>{safeToString(v)}</Text>,
    },
    { title: '来源', dataIndex: 'source', key: 'source', width: 140, ellipsis: true },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true, width: 180 },
    { title: '类型', dataIndex: 'fileType', key: 'fileType', ellipsis: true, width: 100 },
    {
      title: '状态',
      dataIndex: 'ocrStatus',
      key: 'ocrStatus',
      width: 120,
      render: v => statusTag(v),
    },
    {
      title: '总页数',
      dataIndex: 'totalPages',
      key: 'totalPages',
      width: 100,
      render: v => (v == null ? '—' : v),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 200,
      render: v => <Text>{formatDate(typeof v === 'string' ? v : undefined)}</Text>,
    },
    {
      title: '结果预览',
      dataIndex: 'ocrResultPreview',
      key: 'ocrResultPreview',
      ellipsis: true,
      render: v => <Text>{truncate(safeToString(v), 160)}</Text>,
    },
  ]), [])

  const splitColumns = useMemo<TableProps<OcrFileSplitPreview>['columns']>(() => ([
    { title: 'splitIndex', dataIndex: 'splitIndex', key: 'splitIndex', width: 120 },
    { title: 'pageNo', dataIndex: 'pageNo', key: 'pageNo', width: 90 },
    { title: '类型', dataIndex: 'fileType', key: 'fileType', width: 100, ellipsis: true },
    { title: '状态', dataIndex: 'ocrStatus', key: 'ocrStatus', width: 120, render: v => statusTag(v) },
    {
      title: 'OCR结果',
      dataIndex: 'ocrResult',
      key: 'ocrResult',
      onCell: () => ({
        style: { whiteSpace: 'pre-wrap', overflow: 'visible' },
      }),
      render: v => (
        <pre
          style={{
            margin: 0,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontSize: 12,
            lineHeight: 1.4,
            overflow: 'visible',
          }}
        >
          {safeToString(v)}
        </pre>
      ),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      onCell: () => ({
        style: { whiteSpace: 'pre-wrap', overflow: 'visible' },
      }),
      render: v => (v ? <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-word', lineHeight: 1.4, overflow: 'visible' }}>{safeToString(v)}</pre> : '—'),
    },
    {
      title: '图片预览',
      dataIndex: 'imageBase64',
      key: 'imageBase64',
      width: 160,
      render: (_v, row) => {
        if (!includeImageBase64 || !row.imageBase64) return '—'
        const mime = mimeFromFileType(row.fileType)
        return (
          <img
            alt="split"
            src={`data:${mime};base64,${row.imageBase64}`}
            style={{ maxWidth: 140, maxHeight: 90, objectFit: 'contain', border: '1px solid #eee', cursor: 'pointer' }}
            onDoubleClick={() => setImageModalSplit(row)}
          />
        )
      },
    },
  ]), [includeImageBase64])

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title="OCR 识别预览"
        extra={
          <Space>
            <Select
              allowClear
              style={{ width: 180 }}
              placeholder="来源"
              value={source}
              onChange={v => setSource(v ?? undefined)}
              options={[
                { label: '大智部OCR', value: 'DAZHI_OCR' },
                { label: '正言多模态', value: 'ZHENGYAN_MULTIMODAL' },
              ]}
            />
            <Select
              allowClear
              style={{ width: 180 }}
              placeholder="状态"
              value={status}
              onChange={v => setStatus(v ?? undefined)}
              options={[
                { label: 'PENDING', value: 'PENDING' },
                { label: 'PROCESSING', value: 'PROCESSING' },
                { label: 'SUCCESS', value: 'SUCCESS' },
                { label: 'FAILED', value: 'FAILED' },
              ]}
            />
            <Input
              style={{ width: 90 }}
              value={limit}
              onChange={e => setLimit(Number(e.target.value) || 50)}
            />
            <Button type="primary" loading={loadingMain} onClick={() => refresh()}>
              刷新
            </Button>
          </Space>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}

        <Table
          loading={loadingMain}
          size="small"
          rowKey="id"
          dataSource={files}
          columns={mainColumns}
          pagination={false}
          onRow={record => ({
            onClick: () => {
              // 用户手动选择则关闭自动兜底，避免和用户预期冲突。
              autoPickSplitsEnabledRef.current = false
              setSelectedMainId(record.id)
            },
          })}
          rowClassName={record => (record.id === selectedMainId ? 'ant-table-row-selected' : '')}
        />
      </Card>

      <Card
        title={selectedMainId ? `分片详情（mainId=${selectedMainId}）` : '分片详情'}
        extra={
          <Space>
            <Checkbox checked={includeImageBase64} onChange={e => setIncludeImageBase64(e.target.checked)}>
              展示图片（base64）
            </Checkbox>
          </Space>
        }
      >
        {selectedMainId == null ? (
          <Text type="secondary">请先在上表选择一条主记录。</Text>
        ) : (
          <Table
            loading={loadingSplits}
            size="small"
            rowKey="id"
            dataSource={splits}
            columns={splitColumns}
            pagination={false}
          />
        )}
      </Card>

      <Modal
        open={imageModalOpen}
        title={imageModalSplit
          ? `分片详情（splitIndex=${imageModalSplit.splitIndex ?? '—'}, pageNo=${imageModalSplit.pageNo ?? '—'}）`
          : '分片详情'}
        footer={null}
        onCancel={() => setImageModalSplit(null)}
        destroyOnHidden
        width="100vw"
        style={{ top: 0, padding: 0 }}
        styles={{ body: { height: 'calc(100vh - 55px)', padding: 16, overflow: 'auto' } }}
        maskClosable
      >
        {imageModalSplit ? (
          <div style={{ display: 'flex', gap: 16, height: '100%' }}>
            <div style={{ flex: '0 0 50%', minWidth: 0, overflow: 'hidden' }}>
              <Space style={{ marginBottom: 8 }}>
                <Button
                  size="small"
                  icon={<LeftOutlined />}
                  onClick={openPrevSplit}
                  disabled={!hasPrevSplit}
                >
                  上一页
                </Button>
                <Button
                  size="small"
                  icon={<RightOutlined />}
                  onClick={openNextSplit}
                  disabled={!hasNextSplit}
                >
                  下一页
                </Button>
                <Button
                  size="small"
                  icon={<RotateLeftOutlined />}
                  onClick={() => setImageRotateDeg((d) => d - 90)}
                >
                  左旋
                </Button>
                <Button
                  size="small"
                  icon={<RotateRightOutlined />}
                  onClick={() => setImageRotateDeg((d) => d + 90)}
                >
                  右旋
                </Button>
                <Button size="small" onClick={() => setImageRotateDeg(0)}>重置</Button>
                <Text type="secondary">角度：{((imageRotateDeg % 360) + 360) % 360}°</Text>
                <Text type="secondary">
                  {modalSplitIndex >= 0 ? `${modalSplitIndex + 1}/${splits.length}` : '—'}
                </Text>
              </Space>
              {imageModalSplit.imageBase64 ? (
                <div style={{ height: 'calc(100% - 36px)', border: '1px solid #eee', overflow: 'hidden' }}>
                  <img
                    alt="split-fullscreen"
                    src={`data:${mimeFromFileType(imageModalSplit.fileType)};base64,${imageModalSplit.imageBase64}`}
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'contain',
                      transform: `rotate(${imageRotateDeg}deg)`,
                      transformOrigin: 'center center',
                      transition: 'transform 0.2s ease',
                    }}
                  />
                </div>
              ) : (
                <Text type="secondary">未开启图片展示（base64 为空）</Text>
              )}
            </div>
            <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
              <Text strong style={{ marginBottom: 8 }}>OCR 识别结果</Text>
              <pre
                style={{
                  margin: 0,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  lineHeight: 1.4,
                  overflow: 'auto',
                  flex: 1,
                  fontFamily: 'inherit',
                }}
              >
                {safeToString(imageModalSplit.ocrResult)}
              </pre>
            </div>
          </div>
        ) : null}
      </Modal>
    </Space>
  )
}

