interface ApiErrorBody {
  code?: string
  message?: string
}

const DEFAULT_TIMEOUT_MS = 20_000
const GET_RETRY_DELAY_MS = 400

export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function apiUrl(path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `/api${normalized}`
}

async function request(
  path: string,
  init: RequestInit = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
) {
  const controller = new AbortController()
  const timer = globalThis.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(apiUrl(path), {
      ...init,
      credentials: 'include',
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...init.headers,
      },
    })

    if (!response.ok) {
      let body: ApiErrorBody = {}
      try {
        body = (await response.json()) as ApiErrorBody
      } catch {
        // 非 JSON 错误由统一文案兜底，避免把解析异常泄露给页面。
      }
      throw new ApiError(
        response.status,
        body.code ?? 'REQUEST_FAILED',
        body.message ?? `请求失败（HTTP ${response.status}）`,
      )
    }

    return response
  } catch (error) {
    if (controller.signal.aborted) {
      throw new ApiError(0, 'TIMEOUT', '请求超时，请稍后重试')
    }
    throw error
  } finally {
    globalThis.clearTimeout(timer)
  }
}

function shouldRetryGet(error: unknown) {
  if (error instanceof ApiError) {
    return (
      error.code === 'TIMEOUT' ||
      error.status === 502 ||
      error.status === 503 ||
      error.status === 504
    )
  }
  return error instanceof TypeError
}

async function retryGetAfterTransientError(path: string) {
  await new Promise((resolve) => {
    globalThis.setTimeout(resolve, GET_RETRY_DELAY_MS)
  })
  return request(path)
}

export async function get<T>(path: string): Promise<T> {
  let response: Response
  try {
    response = await request(path)
  } catch (error) {
    if (!shouldRetryGet(error)) throw error
    response = await retryGetAfterTransientError(path)
  }
  return (await response.json()) as T
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  return postJsonWithHeaders<T>(path, body)
}

export async function postJsonWithHeaders<T>(
  path: string,
  body: unknown,
  headers: Record<string, string> = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const response = await request(
    path,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(body ?? {}),
    },
    timeoutMs,
  )
  return (await response.json()) as T
}

export async function postFormWithHeaders(
  path: string,
  form: FormData,
  headers: Record<string, string> = {},
  timeoutMs = 60_000,
): Promise<void> {
  await request(
    path,
    {
      method: 'POST',
      headers,
      body: form,
    },
    timeoutMs,
  )
}

export async function postFormJsonWithHeaders<T>(
  path: string,
  form: FormData,
  headers: Record<string, string> = {},
  timeoutMs = 60_000,
): Promise<T> {
  const response = await request(
    path,
    {
      method: 'POST',
      headers,
      body: form,
    },
    timeoutMs,
  )
  return (await response.json()) as T
}

export async function postJsonNoContentWithHeaders(
  path: string,
  body: unknown,
  headers: Record<string, string> = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<void> {
  await request(
    path,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(body ?? {}),
    },
    timeoutMs,
  )
}

export async function putJsonWithHeaders<T>(
  path: string,
  body: unknown,
  headers: Record<string, string> = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const response = await request(
    path,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(body ?? {}),
    },
    timeoutMs,
  )
  return (await response.json()) as T
}

export async function putJsonNoContentWithHeaders(
  path: string,
  body: unknown,
  headers: Record<string, string> = {},
): Promise<void> {
  await request(path, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    body: JSON.stringify(body ?? {}),
  })
}

export async function deleteNoContentWithHeaders(
  path: string,
  headers: Record<string, string> = {},
): Promise<void> {
  await request(path, {
    method: 'DELETE',
    headers,
  })
}

export async function postJsonNoContent(
  path: string,
  body: unknown,
): Promise<void> {
  await request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })
}

export async function postNoContent(path: string): Promise<void> {
  await request(path, { method: 'POST' })
}
