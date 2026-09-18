import { getAuthToken } from './authToken'

export class ApiError extends Error {
  status: number
  /**
   * Field name to message, when the server rejected specific fields (its `fieldErrors`).
   * Undefined for every other failure — a network error or a 500 has no field to blame.
   */
  fieldErrors?: Record<string, string>

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getAuthToken()
  const headers = new Headers(options.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const res = await fetch(path, { ...options, headers })

  if (res.status === 204) return undefined as T

  const text = await res.text()
  const data = text ? safeJsonParse(text) : null

  if (!res.ok) {
    const message = (data && (data.message || data.error)) || res.statusText || 'Request failed'
    throw new ApiError(res.status, message, data?.fieldErrors ?? undefined)
  }

  return data as T
}

function safeJsonParse(text: string) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body !== undefined ? JSON.stringify(body) : undefined }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

/** Builds a query string from an object, skipping null/undefined/empty values. */
export function toQueryString(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') search.set(key, String(value))
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}
