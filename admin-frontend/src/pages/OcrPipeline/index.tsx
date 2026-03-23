import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Checkbox,
  Collapse,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd'
import {
  CloudUploadOutlined,
  LeftOutlined,
  ReloadOutlined,
  RotateLeftOutlined,
  RotateRightOutlined,
  RightOutlined,
  ScanOutlined,
  FileSearchOutlined,
  FundOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons'
import type { UploadFile, UploadProps } from 'antd'
import type { TableProps } from 'antd'
import { apiFetch } from '@/api/client'
import { useConfig } from '@/store/config'
import { JsonView, allExpanded, darkStyles } from 'react-json-view-lite'
import 'react-json-view-lite/dist/index.css'

const { Text, Paragraph } = Typography
const { Dragger } = Upload

// ─── Types ────────────────────────────────────────────────────────────────────

// 对应后端 FileStatusSummary record（不含 ocrResult / prompt / extraInfo 等 CLOB）
interface FileProgress {
  id: string
  fileName: string
  fileType: string
  fileSize: number
  ocrStatus: string
  analysisStatus: string
  errorMessage?: string
}

interface OcrFileSplitPreview {
  id: string
  splitIndex?: number
  pageNo?: number
  fileType?: string
  ocrStatus?: string
  ocrResult?: string
  llmResult?: string
  imageBase64?: string | null
}

// 对应后端 AnalysisSummary record（不含 analysisRaw / analysisPrompt 等 CLOB）
interface AnalysisResult {
  id: string
  mainId: string
  batchNo?: string
  docType?: string
  /** 大模型输出的 doc_type_label（中文），后端优先用此字段 */
  docTypeLabel?: string
  hasStamp?: number
  stampText?: string
  companyName?: string
  licenseNo?: string
  totalAmount?: number
  keyDates?: string
  docSummary?: string
  /** 证件/身份证等扩展字段 JSON 字符串 */
  structuredExtra?: string
  status?: string
  errorMessage?: string
}

interface SimilarityPair {
  fileAName: string
  fileBName: string
  textSimilarity?: number
  fileSimilarity?: number
  overallSimilarity?: number
}

// 对应后端 BatchProgressView record（batch 子对象含 batchNo / status 等）
interface BatchBrief {
  id?: string
  batchNo: string
  status: string
  totalFiles: number
  ocrDoneFiles: number
  analysisDoneFiles: number
  errorMessage?: string
}

interface BatchProgressView {
  batch: BatchBrief
  files: FileProgress[]
  analyses: AnalysisResult[]
  totalFiles: number
  ocrDone: number
  analysisDone: number
  compareFinished: boolean
  /** 与后端 SimilarityPairSummary 一致 */
  similarityPairs?: SimilarityPair[]
}

type BatchProgressRaw = Omit<BatchProgressView, 'files' | 'analyses' | 'batch'> & {
  batch: BatchBrief & { id?: string | number }
  files: Array<Omit<FileProgress, 'id'> & { id: string | number }>
  analyses: Array<Omit<AnalysisResult, 'id' | 'mainId'> & { id: string | number; mainId: string | number }>
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const BATCH_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  PENDING:         { label: '待处理',   color: 'default' },
  OCR_PROCESSING:  { label: 'OCR 处理中', color: 'processing' },
  OCR_DONE:        { label: 'OCR 完成', color: 'cyan' },
  ANALYZING:       { label: '分析中',   color: 'processing' },
  ANALYZED:        { label: '分析完成', color: 'geekblue' },
  COMPARING:       { label: '对比中',   color: 'processing' },
  DONE:            { label: '全部完成', color: 'success' },
  PARTIAL_FAIL:    { label: '部分失败', color: 'warning' },
  FAILED:          { label: '失败',     color: 'error' },
}

const FILE_STATUS_ICON: Record<string, React.ReactNode> = {
  PENDING:    <ClockCircleOutlined style={{ color: '#8c8c8c' }} />,
  PROCESSING: <LoadingOutlined style={{ color: '#1677ff' }} />,
  SUCCESS:    <CheckCircleOutlined style={{ color: '#52c41a' }} />,
  DONE:       <CheckCircleOutlined style={{ color: '#52c41a' }} />,
  FAILED:     <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
  SKIPPED:    <WarningOutlined style={{ color: '#faad14' }} />,
}

const DOC_TYPE_LABELS: Record<string, string> = {
  BUSINESS_LICENSE: '营业执照',
  WORK_SAFETY_LICENSE: '安全生产许可证',
  CONSTRUCTION_QUALIFICATION: '建筑业资质证书',
  ID_CARD:          '身份证',
  QUOTATION:        '报价单',
  CONTRACT:         '合同',
  INVOICE:          '发票',
  SEAL_PAGE:        '印章页',
  OTHER:            '其他',
}

function batchStep(status: string): number {
  const steps: Record<string, number> = {
    PENDING: 0,
    OCR_PROCESSING: 1,
    OCR_DONE: 1,
    ANALYZING: 2,
    ANALYZED: 2,
    COMPARING: 3,
    DONE: 3,
    PARTIAL_FAIL: 3,
    FAILED: 3,
  }
  return steps[status] ?? 0
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      // strip data:...;base64,
      resolve(result.split(',')[1] ?? result)
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function fmt(n?: number): string {
  if (n == null) return '—'
  return (n * 100).toFixed(1) + '%'
}

function normalizeBatchProgressIds(raw: BatchProgressRaw): BatchProgressView {
  return {
    ...raw,
    batch: { ...raw.batch },
    files: (raw.files ?? []).map((f) => ({
      ...f,
      id: String(f.id),
    })),
    analyses: (raw.analyses ?? []).map((a) => ({
      ...a,
      id: String(a.id),
      mainId: String(a.mainId),
    })),
  }
}

function safeToString(v: unknown): string {
  return v == null ? '' : String(v)
}

function mimeFromFileType(fileType?: string): string {
  const t = (fileType ?? '').toLowerCase()
  if (t.includes('png')) return 'image/png'
  if (t.includes('tiff') || t.includes('tif')) return 'image/tiff'
  if (t.includes('jpg') || t.includes('jpeg')) return 'image/jpeg'
  return 'image/*'
}

function tryParseJsonLike(raw: unknown): unknown | null {
  const text = safeToString(raw).trim()
  if (!text) return null
  const withoutFence = text
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/\s*```$/, '')
    .trim()
  try {
    return JSON.parse(withoutFence)
  } catch {
    const start = withoutFence.indexOf('{')
    const end = withoutFence.lastIndexOf('}')
    if (start >= 0 && end > start) {
      try {
        return JSON.parse(withoutFence.slice(start, end + 1))
      } catch {
        return null
      }
    }
    return null
  }
}

function JsonOrTextBlock({ value }: { value?: string | null }) {
  const parsed = tryParseJsonLike(value)
  if (parsed != null) {
    return (
      <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, background: '#1f1f1f' }}>
        <JsonView data={parsed} shouldExpandNode={allExpanded} style={darkStyles} />
      </div>
    )
  }
  return (
    <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 12, lineHeight: 1.45 }}>
      {safeToString(value) || '—'}
    </pre>
  )
}

/** 表头：标题 + 灰色问号，悬停查看计算说明 */
function simColumnTitle(label: string, hint: string) {
  return (
    <Space size={4}>
      <span>{label}</span>
      <Tooltip title={hint} placement="topLeft">
        <QuestionCircleOutlined style={{ color: '#8c8c8c', fontSize: 12, cursor: 'help' }} />
      </Tooltip>
    </Space>
  )
}

const SIM_HINT_TEXT = `两文件将 OCR 分片文字按顺序拼接成长文本后，由 Python 分析服务计算。
• 优先展示 TF‑IDF 余弦相似度（0~1，越高表示字面内容越接近）。
• 若 TF‑IDF 未返回，则界面用 difflib 序列匹配比例代替。
衡量的是「文本层面」是否雷同，与版式/像素无关。`

const SIM_HINT_FILE = `优先使用 Python 返回的视觉相似度 file_visual_sim（感知哈希，0~1，表示版式/图像有多像）。
若无视觉分，后端会用字节级信息给出可解释的 0% 或 100%：SHA‑256 一致或 file_exact_match 为真 → 100%；已判定非同一文件或两 SHA 均存在且不同 → 0%。
注意：0% 表示「不是同一份字节」，不等于版式不像；无足够信息时仍显示「—」。`

const SIM_HINT_OVERALL = `有视觉相似度时：综合 =（文字相似度 + 视觉相似度）/ 2。
无视觉相似度时：综合与「仅文字」一致（TF‑IDF 与 difflib 取较大值等），不把字节级 0/1 与文字做平均，避免综合被拉偏。
仅文字缺失时才会主要依赖文件维度。`

/** 与后端合并逻辑一致：空字符串表示不传该键（沿用全局 eap.pipeline） */
type TriBool = '' | 'true' | 'false'

const TRI_BOOL_OPTIONS = [
  { value: '' as const, label: '与全局一致' },
  { value: 'true' as const, label: '开启' },
  { value: 'false' as const, label: '关闭' },
]

/** 仅在勾选「本批指定模板」时写入 extra_info.multimodalPromptKey */
const MULTIMODAL_KEY_OPTIONS = [
  { value: 'default', label: 'default（通用抽取）' },
  { value: 'quotation', label: 'quotation（报价/投标/分项报价表）' },
  { value: 'bid_screening', label: 'bid_screening（投标摘要）' },
  { value: 'certificates', label: 'certificates（营业执照/资质/安许等证件）' },
  { value: 'id_card', label: 'id_card（身份证）' },
]

function applyTriBool(target: Record<string, unknown>, key: string, v: TriBool) {
  if (v === '') return
  target[key] = v === 'true'
}

/** 合并业务 JSON 与流水线选项，生成 submit-batch 的 extraInfo */
function buildBatchExtraInfo(params: {
  bizJson: string
  ocr: TriBool
  analysis: TriBool
  compare: TriBool
  /** 为 true 时才传 multimodalPromptKeys */
  overrideMultimodalPrompt: boolean
  multimodalKeys: string[]
  maxImages: number | null
  failTol: number | null
}): { ok: true; json?: string } | { ok: false; message: string } {
  const obj: Record<string, unknown> = {}
  const raw = params.bizJson.trim()
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        Object.assign(obj, parsed as Record<string, unknown>)
      } else {
        return { ok: false, message: '业务扩展须为 JSON 对象，如 {"projectId":"P1"}' }
      }
    } catch {
      return { ok: false, message: '业务扩展 JSON 无法解析' }
    }
  }
  applyTriBool(obj, 'ocrEnabled', params.ocr)
  applyTriBool(obj, 'analysisEnabled', params.analysis)
  applyTriBool(obj, 'compareEnabled', params.compare)
  if (params.overrideMultimodalPrompt && params.multimodalKeys.length > 0) {
    obj.multimodalPromptKeys = [...params.multimodalKeys]
  }
  if (params.maxImages != null && params.maxImages > 0) {
    obj.maxImagesPerFile = params.maxImages
  }
  if (params.failTol != null && !Number.isNaN(params.failTol)) {
    obj.failToleranceRatio = params.failTol
  }
  if (Object.keys(obj).length === 0) return { ok: true, json: undefined }
  return { ok: true, json: JSON.stringify(obj) }
}

// ─── Component ────────────────────────────────────────────────────────────────

export default function OcrPipelinePage() {
  const { javaBaseUrl } = useConfig()
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [appCode, setAppCode] = useState('')
  const [pipeOcr, setPipeOcr] = useState<TriBool>('')
  const [pipeAnalysis, setPipeAnalysis] = useState<TriBool>('')
  const [pipeCompare, setPipeCompare] = useState<TriBool>('')
  const [overrideMultimodalPrompt, setOverrideMultimodalPrompt] = useState(false)
  const [multimodalKeys, setMultimodalKeys] = useState<string[]>(['default'])
  const [maxImages, setMaxImages] = useState<number | null>(null)
  const [failTol, setFailTol] = useState<number | null>(null)
  const [bizExtraJson, setBizExtraJson] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [batchNo, setBatchNo] = useState<string | null>(null)
  const [historyQuery, setHistoryQuery] = useState('')
  const [progress, setProgress] = useState<BatchProgressView | null>(null)
  const [polling, setPolling] = useState(false)
  const [includeSplitImage, setIncludeSplitImage] = useState(true)
  const [selectedMainId, setSelectedMainId] = useState<string | null>(null)
  const [splitRows, setSplitRows] = useState<OcrFileSplitPreview[]>([])
  const [splitLoading, setSplitLoading] = useState(false)
  const [splitError, setSplitError] = useState<string | null>(null)
  const [imageModalSplit, setImageModalSplit] = useState<OcrFileSplitPreview | null>(null)
  const [imageRotateDeg, setImageRotateDeg] = useState(0)
  const [messageApi, contextHolder] = message.useMessage()
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // ── Auto-poll while batch is running ────────────────────────────────────────

  const fetchProgress = useCallback(async (bn: string, options?: { silent?: boolean }) => {
    const silent = options?.silent ?? false
    try {
      const { data: body } = await apiFetch<{ code: number; message?: string; data: BatchProgressRaw }>(
        javaBaseUrl, `/api/v1/enterprise/pipeline/batch/${bn}`,
      )
      if (body.code === 200) {
        const normalized = normalizeBatchProgressIds(body.data)
        setProgress(normalized)
        const done = ['DONE', 'FAILED', 'PARTIAL_FAIL'].includes(normalized?.batch?.status)
        if (done) {
          setPolling(false)
          clearInterval(pollRef.current!)
        }
      } else if (!silent) {
        messageApi.error(body.message ?? '查询失败')
      }
    } catch (e: unknown) {
      if (!silent) {
        messageApi.error('请求失败：' + String(e))
      }
    }
  }, [javaBaseUrl, messageApi])

  useEffect(() => {
    if (!batchNo) return
    if (polling) {
      pollRef.current = setInterval(() => fetchProgress(batchNo, { silent: true }), 3000)
    }
    return () => { if (pollRef.current) clearInterval(pollRef.current) }
  }, [batchNo, polling, fetchProgress])

  const fetchSplitsByMainId = useCallback(async (mainId: string, withImage: boolean) => {
    setSplitLoading(true)
    setSplitError(null)
    try {
      const { data: body } = await apiFetch<{ code: number; message?: string; data: OcrFileSplitPreview[] }>(
        javaBaseUrl,
        `/api/v1/admin/ocr/files/${mainId}/splits?includeImageBase64=${withImage}`,
      )
      if (body.code === 200) {
        setSplitRows(body.data ?? [])
      } else {
        setSplitRows([])
        setSplitError(body.message ?? '分片加载失败')
      }
    } catch (e: unknown) {
      setSplitRows([])
      setSplitError(String(e))
    } finally {
      setSplitLoading(false)
    }
  }, [javaBaseUrl])

  useEffect(() => {
    const files = progress?.files ?? []
    if (!files.length) {
      setSelectedMainId(null)
      setSplitRows([])
      return
    }
    const exists = selectedMainId != null && files.some(f => f.id === selectedMainId)
    if (!exists) {
      setSelectedMainId(files[0].id)
    }
  }, [progress?.files, selectedMainId])

  useEffect(() => {
    if (!selectedMainId) {
      setSplitRows([])
      return
    }
    void fetchSplitsByMainId(selectedMainId, includeSplitImage)
  }, [selectedMainId, includeSplitImage, fetchSplitsByMainId])

  useEffect(() => {
    setImageRotateDeg(0)
  }, [imageModalSplit?.id])

  const modalSplitIndex = imageModalSplit
    ? splitRows.findIndex((s) => s.id === imageModalSplit.id)
    : -1
  const hasPrevSplit = modalSplitIndex > 0
  const hasNextSplit = modalSplitIndex >= 0 && modalSplitIndex < splitRows.length - 1
  const openPrevSplit = () => {
    if (!hasPrevSplit) return
    const prev = splitRows[modalSplitIndex - 1]
    if (prev) setImageModalSplit(prev)
  }
  const openNextSplit = () => {
    if (!hasNextSplit) return
    const next = splitRows[modalSplitIndex + 1]
    if (next) setImageModalSplit(next)
  }

  // ── Submit ───────────────────────────────────────────────────────────────────

  const handleSubmit = async () => {
    if (fileList.length < 1) {
      messageApi.warning('请至少选择 1 个文件')
      return
    }
    if (overrideMultimodalPrompt && multimodalKeys.length === 0) {
      messageApi.warning('已勾选「本批指定话术模板」时，请至少选择一个模板')
      return
    }
    const built = buildBatchExtraInfo({
      bizJson: bizExtraJson,
      ocr: pipeOcr,
      analysis: pipeAnalysis,
      compare: pipeCompare,
      overrideMultimodalPrompt,
      multimodalKeys,
      maxImages,
      failTol,
    })
    if (!built.ok) {
      messageApi.error(built.message)
      return
    }
    setSubmitting(true)
    try {
      const files = await Promise.all(
        fileList.map(async (f) => {
          const raw = f.originFileObj as File
          const base64 = await fileToBase64(raw)
          return {
            fileName:    f.name,
            fileType:    f.name.split('.').pop()?.toLowerCase() ?? 'unknown',
            fileSize:    raw.size,
            base64Content: base64,
          }
        })
      )

      const { data: body } = await apiFetch<{ code: number; message?: string; data: { batchNo: string } }>(
        javaBaseUrl, '/api/v1/enterprise/pipeline/submit-batch', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            appCode:   appCode || undefined,
            extraInfo: built.json,
            files,
          }),
        },
      )
      if (body.code !== 200) {
        messageApi.error('提交失败：' + (body.message ?? '未知错误'))
        return
      }
      const bn: string = body.data.batchNo
      setBatchNo(bn)
      setHistoryQuery(bn)
      setProgress(null)
      setPolling(true)
      messageApi.success(`批次已提交：${bn}`)
    } catch (e: unknown) {
      messageApi.error('请求失败：' + String(e))
    } finally {
      setSubmitting(false)
    }
  }

  // ── Manual triggers ───────────────────────────────────────────────────────────

  const trigger = async (path: string, label: string) => {
    try {
      const { data: body } = await apiFetch<{ code: number; message?: string }>(
        javaBaseUrl, path, { method: 'POST' },
      )
      if (body.code === 200) {
        messageApi.success(`已触发：${label}`)
        if (batchNo) { setPolling(true); fetchProgress(batchNo) }
      } else {
        messageApi.error(`触发失败：${body.message}`)
      }
    } catch (e: unknown) {
      messageApi.error('请求失败：' + String(e))
    }
  }

  // ── Upload props ──────────────────────────────────────────────────────────────

  const uploadProps: UploadProps = {
    multiple: true,
    beforeUpload: () => false,             // manual control
    fileList,
    onChange: ({ fileList: fl }) => setFileList(fl),
    accept: '.pdf,.png,.jpg,.jpeg,.tiff,.bmp,.docx,.doc',
  }

  // ── File-progress table ───────────────────────────────────────────────────────

  const fileColumns: TableProps<FileProgress>['columns'] = [
    { title: '文件名', dataIndex: 'fileName', ellipsis: true, width: 200 },
    {
      title: 'OCR 状态', dataIndex: 'ocrStatus', width: 110,
      render: (v: string) => (
        <Space size={4}>{FILE_STATUS_ICON[v] ?? FILE_STATUS_ICON['PENDING']}<Text>{v}</Text></Space>
      ),
    },
    {
      title: '分析状态', dataIndex: 'analysisStatus', width: 110,
      render: (v: string) => (
        <Space size={4}>{FILE_STATUS_ICON[v] ?? FILE_STATUS_ICON['PENDING']}<Text>{v}</Text></Space>
      ),
    },
    {
      title: '错误信息', dataIndex: 'errorMessage', ellipsis: true,
      render: (v?: string) => v ? <Text type="danger">{v}</Text> : <Text type="secondary">—</Text>,
    },
  ]

  // ── Analysis result table ─────────────────────────────────────────────────────

  const analysisColumns: TableProps<AnalysisResult>['columns'] = [
    { title: '文件名', dataIndex: 'fileName', ellipsis: true, width: 160 },
    {
      title: '证件/文档类型',
      dataIndex: 'docTypeLabel',
      width: 130,
      render: (_: unknown, row: AnalysisResult) => {
        const label = row.docTypeLabel ?? (row.docType ? DOC_TYPE_LABELS[row.docType] ?? row.docType : undefined)
        return label ? (
          <Tooltip title={row.docType ? `doc_type: ${row.docType}` : undefined}>
            <Tag color="blue">{label}</Tag>
          </Tooltip>
        ) : (
          <Text type="secondary">—</Text>
        )
      },
    },
    {
      title: '印章', dataIndex: 'hasStamp', width: 60,
      render: (v?: number) => v ? <Tag color="green">有</Tag> : <Tag>无</Tag>,
    },
    { title: '印章文字', dataIndex: 'stampText', ellipsis: true, width: 140,
      render: (v?: string) => v || <Text type="secondary">—</Text> },
    { title: '公司名称', dataIndex: 'companyName', ellipsis: true, width: 150,
      render: (v?: string) => v || <Text type="secondary">—</Text> },
    { title: '信用代码', dataIndex: 'licenseNo', width: 160,
      render: (v?: string) => v || <Text type="secondary">—</Text> },
    { title: '金额（元）', dataIndex: 'totalAmount', width: 100,
      render: (v?: number) => v != null ? <Text strong>{v.toLocaleString()}</Text> : <Text type="secondary">—</Text> },
    { title: '关键日期', dataIndex: 'keyDates', ellipsis: true,
      render: (v?: string) => v || <Text type="secondary">—</Text> },
    { title: '分析状态', dataIndex: 'status', width: 90,
      render: (v?: string) => (
        <Space size={4}>{FILE_STATUS_ICON[v ?? 'PENDING']}<Text>{v}</Text></Space>
      ),
    },
  ]

  // ── Similarity table ──────────────────────────────────────────────────────────

  const simColumns: TableProps<SimilarityPair>['columns'] = [
    { title: '文件 A', dataIndex: 'fileAName', ellipsis: true },
    { title: '文件 B', dataIndex: 'fileBName', ellipsis: true },
    {
      title: simColumnTitle('文字相似度', SIM_HINT_TEXT),
      dataIndex: 'textSimilarity',
      render: (v?: number) =>
        v != null ? (
          <Tooltip title={SIM_HINT_TEXT}>
            <Progress percent={Math.round(v * 100)} size="small" format={() => fmt(v)} />
          </Tooltip>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: simColumnTitle('文件相似度', SIM_HINT_FILE),
      dataIndex: 'fileSimilarity',
      render: (v?: number) =>
        v != null ? (
          <Tooltip title={SIM_HINT_FILE}>
            <Progress percent={Math.round(v * 100)} size="small" format={() => fmt(v)} />
          </Tooltip>
        ) : (
          <Tooltip title={SIM_HINT_FILE}>
            <Text type="secondary" style={{ cursor: 'help' }}>—</Text>
          </Tooltip>
        ),
    },
    {
      title: simColumnTitle('综合相似度', SIM_HINT_OVERALL),
      dataIndex: 'overallSimilarity',
      render: (v?: number) => {
        if (v == null) return <Text type="secondary">—</Text>
        const pct = Math.round(v * 100)
        const status = pct >= 80 ? 'exception' : pct >= 60 ? 'normal' : 'success'
        return (
          <Tooltip title={SIM_HINT_OVERALL}>
            <Progress percent={pct} size="small" status={status} format={() => fmt(v)} />
          </Tooltip>
        )
      },
    },
  ]

  const batchStatus = progress?.batch?.status ?? ''
  const statusMeta = BATCH_STATUS_LABELS[batchStatus] ?? { label: batchStatus, color: 'default' }
  const splitColumns: TableProps<OcrFileSplitPreview>['columns'] = [
    { title: '页码', dataIndex: 'pageNo', width: 72, render: (v?: number) => v ?? '—' },
    { title: 'splitIndex', dataIndex: 'splitIndex', width: 92, render: (v?: number) => v ?? '—' },
    {
      title: '图片',
      dataIndex: 'imageBase64',
      width: 140,
      render: (_: unknown, row: OcrFileSplitPreview) => {
        if (!includeSplitImage || !row.imageBase64) return <Text type="secondary">—</Text>
        return (
          <img
            alt="split-thumb"
            src={`data:${mimeFromFileType(row.fileType)};base64,${row.imageBase64}`}
            style={{ maxWidth: 120, maxHeight: 80, objectFit: 'contain', border: '1px solid #eee', cursor: 'pointer' }}
            onDoubleClick={() => setImageModalSplit(row)}
          />
        )
      },
    },
    {
      title: 'OCR识别内容',
      dataIndex: 'ocrResult',
      render: (v?: string) => (
        <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 12, lineHeight: 1.45 }}>
          {safeToString(v) || '—'}
        </pre>
      ),
    },
    {
      title: '大模型识别内容',
      dataIndex: 'llmResult',
      render: (v?: string) => <JsonOrTextBlock value={v} />,
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {contextHolder}

      {/* ── 提交表单 ─────────────────────────────────────────────── */}
      <Card
        title={<Space><CloudUploadOutlined /><span>提交批次文件</span></Space>}
        extra={
          <Button type="primary" icon={<ScanOutlined />} loading={submitting} onClick={handleSubmit}>
            提交并启动流水线
          </Button>
        }
      >
        <Row gutter={16}>
          <Col xs={24} lg={10}>
            <Form layout="vertical" size="small">
              <Form.Item label="应用编码（可选）">
                <Input
                  placeholder="如：bid-system"
                  value={appCode}
                  onChange={e => setAppCode(e.target.value)}
                />
              </Form.Item>
              <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                本批流水线（可选；未选则与后端全局 eap.pipeline 一致）
              </Text>
              <Row gutter={[8, 8]}>
                <Col span={24}>
                  <Space wrap size={[8, 8]}>
                    <span style={{ width: 72, display: 'inline-block' }}>OCR</span>
                    <Select
                      style={{ minWidth: 140 }}
                      value={pipeOcr}
                      onChange={v => setPipeOcr(v as TriBool)}
                      options={TRI_BOOL_OPTIONS}
                    />
                  </Space>
                </Col>
                <Col span={24}>
                  <Space wrap size={[8, 8]}>
                    <span style={{ width: 72, display: 'inline-block' }}>语义分析</span>
                    <Select
                      style={{ minWidth: 140 }}
                      value={pipeAnalysis}
                      onChange={v => setPipeAnalysis(v as TriBool)}
                      options={TRI_BOOL_OPTIONS}
                    />
                  </Space>
                </Col>
                <Col span={24}>
                  <Space wrap size={[8, 8]}>
                    <span style={{ width: 72, display: 'inline-block' }}>相似度</span>
                    <Select
                      style={{ minWidth: 140 }}
                      value={pipeCompare}
                      onChange={v => setPipeCompare(v as TriBool)}
                      options={TRI_BOOL_OPTIONS}
                    />
                  </Space>
                </Col>
                <Col span={24}>
                  <Form.Item
                    label="话术模板（语义分析）"
                    tooltip="不勾选时使用服务端 eap.pipeline.analysis.multimodal-prompt-key；勾选后本批使用所选模板"
                    style={{ marginBottom: 0 }}
                  >
                    <Space direction="vertical" size={8} style={{ width: '100%' }}>
                      <Checkbox
                        checked={overrideMultimodalPrompt}
                        onChange={e => {
                          const on = e.target.checked
                          setOverrideMultimodalPrompt(on)
                          if (on && multimodalKeys.length === 0) setMultimodalKeys(['default'])
                        }}
                      >
                        本批指定话术模板（可多选，合并为一次语义分析）
                      </Checkbox>
                      {overrideMultimodalPrompt ? (
                        <Select
                          mode="multiple"
                          allowClear
                          style={{ width: '100%' }}
                          value={multimodalKeys}
                          onChange={v => setMultimodalKeys(v ?? [])}
                          options={MULTIMODAL_KEY_OPTIONS}
                          placeholder="请选择一套或多套模板"
                          maxTagCount="responsive"
                        />
                      ) : (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          未指定：与全局配置一致（不在请求中传 multimodalPromptKey）
                        </Text>
                      )}
                    </Space>
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item label="每文件最多送图数" tooltip="多页 PDF 报价单建议 8～15；过大可能触发正言请求体限制">
                    <InputNumber
                      style={{ width: '100%' }}
                      min={1}
                      max={30}
                      placeholder="默认"
                      value={maxImages ?? undefined}
                      onChange={v => setMaxImages(v == null ? null : Number(v))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item label="OCR 失败容忍比例" tooltip="0~1，覆盖 failToleranceRatio">
                    <InputNumber
                      style={{ width: '100%' }}
                      min={0}
                      max={1}
                      step={0.05}
                      placeholder="默认"
                      value={failTol ?? undefined}
                      onChange={v => setFailTol(v == null ? null : Number(v))}
                    />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item
                    label="业务扩展 JSON（可选）"
                    tooltip="与上方选项合并为一条 extra_info；如 projectId、申请人等"
                  >
                    <Input.TextArea
                      rows={2}
                      placeholder='{"projectId":"P001","applicant":"张三"}'
                      value={bizExtraJson}
                      onChange={e => setBizExtraJson(e.target.value)}
                    />
                  </Form.Item>
                </Col>
              </Row>
            </Form>
          </Col>
          <Col xs={24} lg={14}>
            <Dragger {...uploadProps} style={{ borderRadius: 12 }}>
              <p className="ant-upload-drag-icon"><CloudUploadOutlined /></p>
              <p className="ant-upload-text">拖放文件到此处，或点击选择文件</p>
              <p className="ant-upload-hint">
                支持 PDF、PNG、JPG、TIFF、DOCX 等格式；单文件即可 OCR / 多模态分析，上传 2 个及以上时才会进行文件间相似度对比
              </p>
            </Dragger>
          </Col>
        </Row>
      </Card>

      {/* ── 进度面板 ─────────────────────────────────────────────── */}
      {batchNo && (
        <Card
          title={
            <Space>
              <FundOutlined />
              <span>流水线进度</span>
              <Text type="secondary" style={{ fontSize: 12 }}>{batchNo}</Text>
              {batchStatus && (
                <Badge
                  status={statusMeta.color as 'default' | 'processing' | 'success' | 'error' | 'warning'}
                  text={statusMeta.label}
                />
              )}
            </Space>
          }
          extra={
            <Space>
              <Tooltip title="刷新进度">
                <Button
                  icon={<ReloadOutlined spin={polling} />}
                  onClick={() => { if (batchNo) fetchProgress(batchNo) }}
                  size="small"
                />
              </Tooltip>
              {/* 手动触发按钮（仅在卡住时使用） */}
              <Button size="small" onClick={() => trigger(`/api/v1/enterprise/pipeline/batch/${batchNo}/trigger-ocr`, 'OCR')}>
                触发 OCR
              </Button>
              <Button size="small" onClick={() => trigger(`/api/v1/enterprise/pipeline/batch/${batchNo}/trigger-analysis`, '分析')}>
                触发分析
              </Button>
              <Button size="small" onClick={() => trigger(`/api/v1/enterprise/pipeline/batch/${batchNo}/trigger-compare`, '对比')}>
                触发对比
              </Button>
            </Space>
          }
        >
          {/* 阶段进度条 */}
          <Steps
            current={batchStep(batchStatus)}
            status={batchStatus === 'FAILED' ? 'error' : batchStatus === 'PARTIAL_FAIL' ? 'wait' : undefined}
            style={{ marginBottom: 24 }}
            items={[
              {
                title: 'OCR 识别',
                description: progress ? `${progress.ocrDone} / ${progress.totalFiles} 文件` : '—',
                icon: <ScanOutlined />,
              },
              {
                title: '语义分析',
                description: progress ? `${progress.analysisDone} / ${progress.totalFiles} 文件` : '—',
                icon: <FileSearchOutlined />,
              },
              {
                title: '相似度对比',
                description: batchStatus === 'DONE' ? '已完成' : batchStatus === 'COMPARING' ? '进行中…' : '待执行',
                icon: <FundOutlined />,
              },
            ]}
          />

          {progress?.batch?.errorMessage && (
            <Alert type="warning" message={progress.batch.errorMessage} showIcon style={{ marginBottom: 16 }} />
          )}

          {/* 文件状态列表 */}
          <Collapse
            defaultActiveKey={['files']}
            items={[
              {
                key: 'files',
                label: <Space><FileSearchOutlined /><span>文件处理状态</span></Space>,
                children: (
                  <Space direction="vertical" style={{ width: '100%' }} size={10}>
                    <Table<FileProgress>
                      dataSource={progress?.files ?? []}
                      columns={fileColumns}
                      rowKey="id"
                      size="small"
                      pagination={false}
                      locale={{ emptyText: <Empty description="暂无文件数据" /> }}
                      onRow={(record) => ({
                        onClick: () => setSelectedMainId(record.id),
                      })}
                      rowClassName={(record) => (record.id === selectedMainId ? 'ant-table-row-selected' : '')}
                    />
                    <Text type="secondary">点击文件行可查看该文件按页分片的 OCR / 大模型识别结果。</Text>
                  </Space>
                ),
              },
              {
                key: 'splitPreview',
                label: <Space><ScanOutlined /><span>按页识别详情（图片/OCR/大模型）</span></Space>,
                children: (
                  <Space direction="vertical" style={{ width: '100%' }} size={10}>
                    <Space>
                      <Text>当前文件：</Text>
                      <Select
                        style={{ width: 360 }}
                        value={selectedMainId ?? undefined}
                        placeholder="请选择文件"
                        options={(progress?.files ?? []).map(f => ({ label: f.fileName, value: f.id }))}
                        onChange={(v) => setSelectedMainId(v)}
                      />
                      <Checkbox checked={includeSplitImage} onChange={e => setIncludeSplitImage(e.target.checked)}>
                        展示分片图片
                      </Checkbox>
                    </Space>
                    {splitError ? <Alert type="warning" showIcon message={splitError} /> : null}
                    <Table<OcrFileSplitPreview>
                      loading={splitLoading}
                      dataSource={splitRows}
                      columns={splitColumns}
                      rowKey="id"
                      size="small"
                      pagination={false}
                      scroll={{ x: 1300 }}
                      locale={{ emptyText: <Empty description={selectedMainId ? '暂无分片详情' : '请先选择文件'} /> }}
                    />
                  </Space>
                ),
              },
              {
                key: 'analysis',
                label: <Space><CheckCircleOutlined /><span>语义分析结果</span></Space>,
                children: (
                  <Table<AnalysisResult>
                    dataSource={progress?.analyses ?? []}
                    columns={analysisColumns}
                    rowKey="id"
                    size="small"
                    pagination={false}
                    scroll={{ x: 1100 }}
                    locale={{ emptyText: <Empty description="分析完成后显示结果" /> }}
                    expandable={{
                      expandedRowRender: (row) => (row.docSummary || row.structuredExtra) ? (
                        <Descriptions size="small" column={1}>
                          {row.docSummary ? (
                            <Descriptions.Item label="文档摘要">
                              <Paragraph style={{ margin: 0 }}>{row.docSummary}</Paragraph>
                            </Descriptions.Item>
                          ) : null}
                          {row.structuredExtra ? (
                            <Descriptions.Item label="扩展字段 (JSON)">
                              <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12 }}>
                                {(() => {
                                  try {
                                    return JSON.stringify(JSON.parse(row.structuredExtra), null, 2)
                                  } catch {
                                    return row.structuredExtra
                                  }
                                })()}
                              </pre>
                            </Descriptions.Item>
                          ) : null}
                        </Descriptions>
                      ) : null,
                      rowExpandable: (row) => !!(row.docSummary || row.structuredExtra),
                    }}
                  />
                ),
              },
              {
                key: 'similarity',
                label: <Space><FundOutlined /><span>相似度对比结果</span></Space>,
                children: (
                  <Table<SimilarityPair>
                    dataSource={progress?.similarityPairs ?? []}
                    columns={simColumns}
                    rowKey={(r) => `${r.fileAName}__${r.fileBName}`}
                    size="small"
                    pagination={false}
                    locale={{ emptyText: <Empty description="对比完成后显示结果" /> }}
                  />
                ),
              },
            ]}
          />
        </Card>
      )}

      {/* ── 历史查询 ─────────────────────────────────────────────── */}
      <Card title="历史批次查询" size="small">
        <Space>
          <Input
            placeholder="输入 batch_no 查询"
            style={{ width: 320 }}
            allowClear
            value={historyQuery}
            onChange={e => setHistoryQuery(e.target.value)}
            onPressEnter={() => {
              const val = historyQuery.trim()
              if (!val) {
                messageApi.warning('请输入批次号')
                return
              }
              setBatchNo(val)
              void fetchProgress(val)
            }}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              const val = historyQuery.trim()
              if (!val) {
                messageApi.warning('请输入批次号')
                return
              }
              setBatchNo(val)
              void fetchProgress(val)
            }}
          >
            查询
          </Button>
        </Space>
      </Card>

      <Modal
        open={!!imageModalSplit}
        title={imageModalSplit ? `按页详情（pageNo=${imageModalSplit.pageNo ?? '—'}）` : '按页详情'}
        footer={null}
        onCancel={() => setImageModalSplit(null)}
        destroyOnHidden
        width="100vw"
        style={{ top: 0, padding: 0 }}
        styles={{ body: { height: 'calc(100vh - 55px)', padding: 16, overflow: 'auto' } }}
      >
        {imageModalSplit ? (
          <div style={{ display: 'flex', gap: 16, height: '100%' }}>
            <div style={{ flex: '0 0 42%', minWidth: 0, overflow: 'hidden' }}>
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
                  {modalSplitIndex >= 0 ? `${modalSplitIndex + 1}/${splitRows.length}` : '—'}
                </Text>
              </Space>
              {imageModalSplit.imageBase64 ? (
                <div style={{ height: 'calc(100% - 36px)', border: '1px solid #eee', overflow: 'hidden' }}>
                  <img
                    alt="split-full"
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
                <Text type="secondary">该分片未返回图片数据</Text>
              )}
            </div>
            <div style={{ flex: 1, minWidth: 0, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ minWidth: 0 }}>
                <Text strong>OCR识别内容</Text>
                <pre style={{ margin: '8px 0 0', whiteSpace: 'pre-wrap', wordBreak: 'break-word', lineHeight: 1.45, maxHeight: '72vh', overflow: 'auto' }}>
                  {safeToString(imageModalSplit.ocrResult) || '—'}
                </pre>
              </div>
              <div style={{ minWidth: 0 }}>
                <Text strong>大模型识别内容</Text>
                <div style={{ marginTop: 8, maxHeight: '72vh', overflow: 'auto' }}>
                  <JsonOrTextBlock value={imageModalSplit.llmResult} />
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </Modal>
    </Space>
  )
}
