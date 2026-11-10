import type { ApiErrorBody } from '../types'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (response.ok) {
    if (response.status === 204) {
      return undefined as T
    }
    return (await response.json()) as T
  }

  let message = `请求失败（HTTP ${response.status}）`
  let code = 'HTTP_ERROR'
  try {
    const body = (await response.json()) as ApiErrorBody
    message = body.message || message
    code = body.code || code
  } catch {
    // 非 JSON 错误响应，保留默认信息
  }
  throw new ApiError(response.status, code, message)
}
