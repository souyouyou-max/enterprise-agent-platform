export interface ApiResult<T = unknown> {
  data: T
  ms: number
}

export async function apiFetch<T = unknown>(
  baseUrl: string,
  path: string,
  init?: RequestInit,
): Promise<ApiResult<T>> {
  const t0 = Date.now()
  const res = await fetch(baseUrl.replace(/\/$/, '') + path, init)
  const ms = Date.now() - t0
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  const data = (await res.json()) as T
  return { data, ms }
}

export function toBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      resolve(result.split(',')[1] ?? '')
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}
