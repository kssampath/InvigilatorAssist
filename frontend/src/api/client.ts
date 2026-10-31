const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? ''
const TOKEN_KEY = 'ia.token'

/** Error carrying the API's problem+json detail, so screens can show the server's own message. */
export class ApiError extends Error {
  status: number
  errors: string[]

  constructor(status: number, message: string, errors: string[] = []) {
    super(message)
    this.status = status
    this.errors = errors
  }
}

// The token lives in sessionStorage: it survives a reload but not a closed tab.
export const tokenStore = {
  get: (): string | null => {
    try {
      return sessionStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string | null) => {
    try {
      if (token) sessionStorage.setItem(TOKEN_KEY, token)
      else sessionStorage.removeItem(TOKEN_KEY)
    } catch {
      /* storage unavailable: stay logged in for this page only */
    }
  },
}

let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  const res = await fetch(`${BASE}${path}`, { ...init, headers })
  if (res.status === 401 && token) {
    tokenStore.set(null)
    onUnauthorized()
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    let errors: string[] = []
    try {
      const body = await res.json()
      message = body.detail ?? body.title ?? message
      errors = Array.isArray(body.errors) ? body.errors : []
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, message, errors)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const del = (path: string) => api<void>(path, { method: 'DELETE' })
