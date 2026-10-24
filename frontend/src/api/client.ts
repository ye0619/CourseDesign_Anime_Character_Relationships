import type { ApiEnvelope } from '../types/api'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly data: unknown = null,
    public readonly requestId: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  return request<T>(path, { signal })
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    credentials: 'same-origin',
    cache: 'no-store',
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  })
  let body: ApiEnvelope<T>
  try {
    body = await response.json() as ApiEnvelope<T>
  } catch {
    throw new ApiError(response.status, 'INVALID_RESPONSE', '服务返回了无法读取的响应')
  }
  if (!body || typeof body.code !== 'string' || !('data' in body)) {
    throw new ApiError(response.status, 'INVALID_RESPONSE', '服务响应格式不正确')
  }
  if (!response.ok || body.code !== 'OK') {
    throw new ApiError(response.status, body.code, body.message || '请求失败', body.data, body.requestId)
  }
  return body.data
}

export async function mutate<T>(path: string, method: string, data?: unknown): Promise<T> {
  const csrf = await get<{ token: string; headerName: string }>('/session/csrf')
  const form = data instanceof FormData || data instanceof URLSearchParams
  return request<T>(path, { method, headers: { [csrf.headerName]: csrf.token, ...(form ? {} : { 'Content-Type': 'application/json' }) }, body: form ? data : data === undefined ? undefined : JSON.stringify(data) })
}
