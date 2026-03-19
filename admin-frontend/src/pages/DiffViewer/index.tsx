import { useState, useRef } from 'react'
import {
  Card, Button, Row, Col, Space, Alert, Statistic, Tag, Upload,
} from 'antd'
import { InboxOutlined, PlayCircleOutlined, ClearOutlined } from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { useConfig } from '@/store/config'
import { apiFetch, toBase64 } from '@/api/client'
import { computeDiff, charDiff } from './diffAlgo'
import type { DiffOp } from './diffAlgo'

const { Dragger } = Upload

interface FileMeta {
  name: string
  textPreview?: string
}

interface VisualSim {
  ok?: boolean
  avgPageSim?: number
}

interface ApiComparison {
  a: string
  b: string
  result?: { tfidfCosine?: number }
  visualSimilarity?: VisualSim
}

interface ApiData {
  fileMetas?: FileMeta[]
  comparisons?: ApiComparison[]
}

interface PairInfo {
  i: number
  j: number
  tfidf: number | null
  visual: VisualSim | null
}

function escHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function buildRows(ops: DiffOp[]): string {
  // Collapse long equal runs to 3 context lines
  const CONTEXT = 3
  let rows = ''
  let lnA = 1, lnB = 1

  interface EqBuf { op: DiffOp; lnA: number; lnB: number }
  let eqBuf: EqBuf[] = []

  const flushEq = (all = false) => {
    const show = all ? eqBuf : [
      ...eqBuf.slice(0, CONTEXT),
      ...(eqBuf.length > CONTEXT * 2
        ? [{ op: null as unknown as DiffOp, lnA: -1, lnB: -1, ellipsis: true }]
        : []),
      ...eqBuf.slice(-CONTEXT),
    ]
    for (const item of show) {
      if ('ellipsis' in item && item.ellipsis) {
        rows += `<tr class="dl-eq"><td class="dl-num" colspan="2" style="text-align:center;color:#aaa">… ${eqBuf.length - CONTEXT * 2} 行相同 …</td><td class="dl-sep"></td><td class="dl-num" colspan="2" style="text-align:center;color:#aaa">… ${eqBuf.length - CONTEXT * 2} 行相同 …</td></tr>`
        continue
      }
      rows += `<tr class="dl-eq"><td class="dl-num">${item.lnA}</td><td class="dl-text">${escHtml(item.op.aLine ?? '')}</td><td class="dl-sep"></td><td class="dl-num">${item.lnB}</td><td class="dl-text">${escHtml(item.op.bLine ?? '')}</td></tr>`
    }
    eqBuf = []
  }

  for (const op of ops) {
    if (op.type === 'equal') {
      eqBuf.push({ op, lnA, lnB })
      lnA++; lnB++
    } else {
      if (eqBuf.length) flushEq()
      if (op.type === 'delete') {
        rows += `<tr class="dl-del"><td class="dl-num">${lnA}</td><td class="dl-text">${escHtml(op.aLine ?? '')}</td><td class="dl-sep"></td><td class="dl-num"></td><td class="dl-text"></td></tr>`
        lnA++
      } else if (op.type === 'insert') {
        rows += `<tr class="dl-ins"><td class="dl-num"></td><td class="dl-text"></td><td class="dl-sep"></td><td class="dl-num">${lnB}</td><td class="dl-text">${escHtml(op.bLine ?? '')}</td></tr>`
        lnB++
      } else {
        const { aHtml, bHtml } = charDiff(op.aLine ?? '', op.bLine ?? '')
        rows += `<tr class="dl-chg"><td class="dl-num">${lnA}</td><td class="dl-text">${aHtml}</td><td class="dl-sep"></td><td class="dl-num">${lnB}</td><td class="dl-text">${bHtml}</td></tr>`
        lnA++; lnB++
      }
    }
  }
  if (eqBuf.length) flushEq(true)
  return rows
}

export default function DiffViewerPage() {
  const { baseUrl } = useConfig()
  const [files, setFiles] = useState<File[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<string[]>([])
  const [texts, setTexts] = useState<string[]>([])
  const [pairs, setPairs] = useState<PairInfo[]>([])
  const [activePair, setActivePair] = useState<[number, number] | null>(null)
  const tbodyRef = useRef<HTMLTableSectionElement>(null)

  const draggerProps: UploadProps = {
    multiple: true,
    beforeUpload: (file) => {
      setFiles(prev => prev.find(f => f.name === file.name) ? prev : [...prev, file])
      return false
    },
    showUploadList: false,
    accept: '.pdf,.docx,.doc,.txt',
  }

  const clear = () => {
    setFiles([]); setNames([]); setTexts([]); setPairs([]); setActivePair(null); setError(null)
  }

  const run = async () => {
    if (files.length < 2) return
    setLoading(true); setError(null)
    try {
      const b64List = await Promise.all(files.map(f => toBase64(f)))
      const apiFiles = files.map((f, i) => ({ filename: f.name, content_b64: b64List[i] }))
      const { data } = await apiFetch<ApiData>(baseUrl, '/analyze/compare-base64', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ files: apiFiles, include_text_preview: true, preview_chars: 0 }),
      })
      const ns = (data.fileMetas ?? []).map(m => m.name)
      const ts = (data.fileMetas ?? []).map(m => m.textPreview ?? '')
      setNames(ns); setTexts(ts)
      const pairList: PairInfo[] = []
      for (const c of data.comparisons ?? []) {
        const i = ns.indexOf(c.a), j = ns.indexOf(c.b)
        if (i >= 0 && j >= 0) {
          pairList.push({ i, j, tfidf: c.result?.tfidfCosine ?? null, visual: c.visualSimilarity ?? null })
        }
      }
      setPairs(pairList)
      if (pairList.length > 0) selectPair(0, 1, ts, pairList)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const [activeStats, setActiveStats] = useState<{ eq: number; del: number; ins: number; chg: number; simPct: string } | null>(null)
  const [activeVisual, setActiveVisual] = useState<VisualSim | null>(null)

  const selectPair = (i: number, j: number, ts?: string[], ps?: PairInfo[]) => {
    const textArr = ts ?? texts
    const pairArr = ps ?? pairs
    setActivePair([i, j])
    const { ops, stats } = computeDiff(textArr[i] ?? '', textArr[j] ?? '')
    setActiveStats(stats)
    const found = pairArr.find(p => p.i === i && p.j === j)
    setActiveVisual(found?.visual ?? null)
    setTimeout(() => {
      if (tbodyRef.current) {
        tbodyRef.current.innerHTML = buildRows(ops)
      }
    }, 0)
  }

  const shortName = (n: string) => n.length > 20 ? n.slice(0, 18) + '…' : n

  const chipColor = (tfidf: number | null) =>
    tfidf === null ? '#8c8c8c' : tfidf > 0.85 ? '#ff4d4f' : tfidf > 0.5 ? '#faad14' : '#52c41a'

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title="文件相似度 Diff 视图"
        extra={
          <Space>
            <Button icon={<ClearOutlined />} onClick={clear}>清空</Button>
            <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} disabled={files.length < 2} onClick={run}>
              开始对比
            </Button>
          </Space>
        }
      >
        {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}

        <Dragger {...draggerProps} style={{ marginBottom: 16 }}>
          <p><InboxOutlined style={{ fontSize: 32, color: '#8c8c8c' }} /></p>
          <p>拖拽或点击上传（至少 2 个文件）</p>
          <p style={{ fontSize: 12, color: '#aaa' }}>PDF / DOCX / DOC / TXT</p>
        </Dragger>

        {files.length > 0 && (
          <Space wrap>
            {files.map(f => (
              <Tag key={f.name} closable onClose={() => setFiles(prev => prev.filter(x => x.name !== f.name))}>
                📄 {f.name}
              </Tag>
            ))}
          </Space>
        )}
      </Card>

      {pairs.length > 0 && (
        <>
          {/* Pair chips */}
          <Card size="small" title="选择对比对">
            <Space wrap>
              {pairs.map(p => {
                const active = activePair?.[0] === p.i && activePair?.[1] === p.j
                const color = chipColor(p.tfidf)
                const vpct = p.visual?.ok ? Math.round((p.visual.avgPageSim ?? 0) * 100) : null
                return (
                  <Button
                    key={`${p.i}-${p.j}`}
                    type={active ? 'primary' : 'default'}
                    size="small"
                    onClick={() => selectPair(p.i, p.j)}
                    style={{ height: 'auto', padding: '4px 10px', textAlign: 'left' }}
                  >
                    <div style={{ fontSize: 12 }}>{shortName(names[p.i])} vs {shortName(names[p.j])}</div>
                    <div style={{ marginTop: 2 }}>
                      <span style={{ color: active ? '#fff' : color, fontSize: 11, fontWeight: 600 }}>
                        文本 {p.tfidf !== null ? Math.round(p.tfidf * 100) : '?'}%
                      </span>
                      {vpct !== null && (
                        <span style={{ color: active ? '#ddd' : '#6366f1', fontSize: 11, marginLeft: 6 }}>
                          视觉 {vpct}%
                        </span>
                      )}
                    </div>
                  </Button>
                )
              })}
            </Space>
          </Card>

          {/* Stats bar */}
          {activeStats && (
            <Card size="small">
              <Row gutter={16}>
                <Col><Statistic title="相同段" value={activeStats.eq} valueStyle={{ color: '#52c41a', fontSize: 18 }} /></Col>
                <Col><Statistic title="仅左侧" value={activeStats.del} valueStyle={{ color: '#ff4d4f', fontSize: 18 }} /></Col>
                <Col><Statistic title="仅右侧" value={activeStats.ins} valueStyle={{ color: '#1677ff', fontSize: 18 }} /></Col>
                <Col><Statistic title="已修改" value={activeStats.chg} valueStyle={{ color: '#faad14', fontSize: 18 }} /></Col>
                <Col><Statistic title="文本相似" value={activeStats.simPct} suffix="%" valueStyle={{ fontSize: 18 }} /></Col>
                <Col>
                  <div style={{ fontSize: 12, color: '#888' }}>视觉相似</div>
                  <div style={{ fontSize: 18, fontWeight: 600, color: '#6366f1' }}>
                    {activeVisual?.ok ? `${Math.round((activeVisual.avgPageSim ?? 0) * 100)}%` : '—'}
                  </div>
                </Col>
              </Row>
            </Card>
          )}

          {/* Legend */}
          <Space size="large" style={{ padding: '0 4px' }}>
            <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#fff', border: '1px solid #e0e0e0', marginRight: 4 }} />相同</span>
            <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#fff0f0', marginRight: 4 }} />仅左侧</span>
            <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#f0fff4', marginRight: 4 }} />仅右侧</span>
            <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#fffbea', marginRight: 4 }} />已修改</span>
            <span><mark className="cdel" style={{ padding: '0 4px' }}>字符删除</mark></span>
            <span><mark className="cins" style={{ padding: '0 4px' }}>字符新增</mark></span>
          </Space>

          {/* Diff table */}
          {activePair && (
            <Card
              size="small"
              title={
                <Row>
                  <Col span={12}>📄 {names[activePair[0]]}</Col>
                  <Col span={12}>📄 {names[activePair[1]]}</Col>
                </Row>
              }
              styles={{ body: { padding: 0, overflow: 'auto', maxHeight: 'calc(100vh - 420px)' } }}
            >
              <table className="diff-table">
                <colgroup>
                  <col style={{ width: 48 }} /><col />
                  <col style={{ width: 8 }} />
                  <col style={{ width: 48 }} /><col />
                </colgroup>
                <tbody ref={tbodyRef} />
              </table>
            </Card>
          )}
        </>
      )}
    </Space>
  )
}
