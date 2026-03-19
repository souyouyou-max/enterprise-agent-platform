import { apiFetch } from '@/api/client'

export interface CreateSessionResponse {
  data?: { sessionId?: string }
}

export async function createChatSession(baseUrl: string): Promise<string> {
  try {
    const res = await apiFetch<CreateSessionResponse>(baseUrl, '/api/v1/chat/session', { method: 'POST' })
    const sessionId = res.data?.data?.sessionId
    return sessionId && sessionId.trim() ? sessionId : crypto.randomUUID()
  } catch {
    // 后端不可用时也允许继续本地对话（仅用于演示）
    return crypto.randomUUID()
  }
}

export type StreamChunkHandler = (chunk: string) => void

export interface Img2TextPayload {
  text?: string
  image?: string
  messages?: Array<{
    role: string
    content: Array<
      | { type: 'image_url'; image_url: { url: string } }
      | { type: 'text'; text: string }
    >
  }>
  user_info: {
    user_id: string
    user_name: string
    user_dept_name: string
    user_company: string
  }
}

export async function streamChat(
  baseUrl: string,
  sessionId: string,
  message: string,
  onChunk: StreamChunkHandler,
  signal?: AbortSignal,
): Promise<void> {
  const res = await fetch(baseUrl.replace(/\/$/, '') + '/api/v1/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message }),
    signal,
  })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  if (!res.body) throw new Error('No response body')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    if (signal?.aborted) break

    buffer += decoder.decode(value, { stream: true })
    // SSE: data: xxx\n\n
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const chunk = line.slice(5)
        if (chunk) onChunk(chunk)
      }
    }
  }
}

export async function img2TextChat(baseUrl: string, payload: Img2TextPayload): Promise<string> {
  const res = await apiFetch<any>(baseUrl, '/api/v1/enterprise/semantics/img2text', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

  // 兼容多层包装（例如 ResponseResult 再次被网关包装）
  const unwrapData = (value: any): any => {
    let cur = value
    for (let i = 0; i < 4; i += 1) {
      if (cur && typeof cur === 'object' && cur.data && typeof cur.data === 'object') {
        cur = cur.data
      } else {
        break
      }
    }
    return cur ?? {}
  }

  const payloadData = unwrapData(res.data)
  const candidates = [
    payloadData?.content,
    payloadData?.result,
    payloadData?.text,
    payloadData?.response?.choices?.[0]?.message?.content,
    payloadData?.response?.text,
    payloadData?.response?.data?.text,
    res.data?.content,
    res.data?.result,
    res.data?.response?.choices?.[0]?.message?.content,
  ]
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.trim()) {
      return candidate
    }
  }
  return '图片已上传，但未获取到可读文本结果。'
}

