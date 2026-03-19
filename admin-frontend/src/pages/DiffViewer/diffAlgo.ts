export type DiffOp = {
  type: 'equal' | 'delete' | 'insert' | 'change'
  aLine: string | null
  bLine: string | null
}

function splitSegments(text: string): string[] {
  const lines = text.split('\n').map(l => l.trim()).filter(l => l.length > 0)
  if (lines.length > 2000) lines.splice(2000)
  return lines
}

function lcsDP(a: string[], b: string[]): { dp: Int32Array; n: number } {
  const m = a.length, n = b.length
  const dp = new Int32Array((m + 1) * (n + 1))
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i * (n + 1) + j] = a[i - 1] === b[j - 1]
        ? dp[(i - 1) * (n + 1) + (j - 1)] + 1
        : Math.max(dp[(i - 1) * (n + 1) + j], dp[i * (n + 1) + (j - 1)])
    }
  }
  return { dp, n }
}

function computeLineDiff(seqA: string[], seqB: string[]): DiffOp[] {
  const m = seqA.length, n = seqB.length

  if (m * n > 3_000_000) {
    const result: DiffOp[] = []
    const len = Math.max(m, n)
    for (let i = 0; i < len; i++) {
      const a = i < m ? seqA[i] : null
      const b = i < n ? seqB[i] : null
      if (a !== null && b !== null) result.push({ type: 'change', aLine: a, bLine: b })
      else if (a !== null) result.push({ type: 'delete', aLine: a, bLine: null })
      else result.push({ type: 'insert', aLine: null, bLine: b! })
    }
    return result
  }

  const { dp, n: N } = lcsDP(seqA, seqB)
  const raw: DiffOp[] = []
  let i = m, j = n
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && seqA[i - 1] === seqB[j - 1]) {
      raw.push({ type: 'equal', aLine: seqA[i - 1], bLine: seqB[j - 1] })
      i--; j--
    } else if (j > 0 && (i === 0 || dp[i * (N + 1) + (j - 1)] >= dp[(i - 1) * (N + 1) + j])) {
      raw.push({ type: 'insert', aLine: null, bLine: seqB[j - 1] })
      j--
    } else {
      raw.push({ type: 'delete', aLine: seqA[i - 1], bLine: null })
      i--
    }
  }
  raw.reverse()

  const result: DiffOp[] = []
  let k = 0
  while (k < raw.length) {
    if (raw[k].type !== 'delete') { result.push(raw[k++]); continue }
    const dels: string[] = [], ins: string[] = []
    while (k < raw.length && raw[k].type === 'delete') dels.push(raw[k++].aLine!)
    while (k < raw.length && raw[k].type === 'insert') ins.push(raw[k++].bLine!)
    const len = Math.max(dels.length, ins.length)
    for (let x = 0; x < len; x++) {
      const a = x < dels.length ? dels[x] : null
      const b = x < ins.length ? ins[x] : null
      if (a !== null && b !== null) result.push({ type: 'change', aLine: a, bLine: b })
      else if (a !== null) result.push({ type: 'delete', aLine: a, bLine: null })
      else result.push({ type: 'insert', aLine: null, bLine: b! })
    }
  }
  return result
}

export interface CharDiffResult {
  aHtml: string
  bHtml: string
}

function escHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function charDiff(strA: string, strB: string): CharDiffResult {
  if (strA.length + strB.length > 600) {
    return {
      aHtml: `<mark class="cdel">${escHtml(strA)}</mark>`,
      bHtml: `<mark class="cins">${escHtml(strB)}</mark>`,
    }
  }
  const ac = [...strA], bc = [...strB]
  const m = ac.length, n = bc.length
  const dp = new Int32Array((m + 1) * (n + 1))
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i * (n + 1) + j] = ac[i - 1] === bc[j - 1]
        ? dp[(i - 1) * (n + 1) + (j - 1)] + 1
        : Math.max(dp[(i - 1) * (n + 1) + j], dp[i * (n + 1) + (j - 1)])

  type Op = { t: 'eq' | 'del' | 'ins'; c: string }
  const aOps: Op[] = [], bOps: Op[] = []
  let i = m, j = n
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && ac[i - 1] === bc[j - 1]) {
      aOps.push({ t: 'eq', c: ac[i - 1] }); bOps.push({ t: 'eq', c: bc[j - 1] }); i--; j--
    } else if (j > 0 && (i === 0 || dp[i * (n + 1) + (j - 1)] >= dp[(i - 1) * (n + 1) + j])) {
      bOps.push({ t: 'ins', c: bc[j - 1] }); j--
    } else {
      aOps.push({ t: 'del', c: ac[i - 1] }); i--
    }
  }
  aOps.reverse(); bOps.reverse()

  const build = (ops: Op[], delCls: string | null, insCls: string | null): string => {
    let html = '', cur: string | null = null
    for (const op of ops) {
      const cls = op.t === 'eq' ? null : op.t === 'del' ? delCls : insCls
      if (cls !== cur) { if (cur) html += '</mark>'; if (cls) html += `<mark class="${cls}">` ; cur = cls }
      html += escHtml(op.c)
    }
    if (cur) html += '</mark>'
    return html
  }

  return { aHtml: build(aOps, 'cdel', null), bHtml: build(bOps, null, 'cins') }
}

export interface DiffStats {
  eq: number
  del: number
  ins: number
  chg: number
  simPct: string
}

export interface DiffResult {
  ops: DiffOp[]
  stats: DiffStats
}

export function computeDiff(textA: string, textB: string): DiffResult {
  const seqA = splitSegments(textA)
  const seqB = splitSegments(textB)
  const ops = computeLineDiff(seqA, seqB)
  let eq = 0, del = 0, ins = 0, chg = 0
  for (const o of ops) {
    if (o.type === 'equal') eq++
    else if (o.type === 'delete') del++
    else if (o.type === 'insert') ins++
    else chg++
  }
  const total = eq + del + ins + chg
  const simPct = total > 0 ? ((eq / total) * 100).toFixed(1) : '0.0'
  return { ops, stats: { eq, del, ins, chg, simPct } }
}
