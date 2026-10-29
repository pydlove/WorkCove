const crypto = require('node:crypto')
const http = require('node:http')

const MAX_REQUEST_BYTES = 1024 * 1024

const ROUTES = new Map([
  ['/v1/status', { method: 'GET', action: 'status' }],
  ['/v1/open', { method: 'POST', action: 'open' }],
  ['/v1/navigate', { method: 'POST', action: 'navigate' }],
  ['/v1/snapshot', { method: 'GET', action: 'snapshot' }],
  ['/v1/click', { method: 'POST', action: 'click' }],
  ['/v1/type', { method: 'POST', action: 'type' }],
  ['/v1/press', { method: 'POST', action: 'press' }],
  ['/v1/fill-credentials', { method: 'POST', action: 'fillCredentials' }],
  ['/v1/evaluate', { method: 'POST', action: 'evaluate' }],
  ['/v1/wait', { method: 'POST', action: 'wait' }],
  ['/v1/screenshot', { method: 'POST', action: 'screenshot' }],
  ['/v1/downloads', { method: 'GET', action: 'downloads' }],
  [
    '/v1/capability-set/update',
    { method: 'POST', action: 'capabilityUpdate' },
  ],
])

function isLoopbackAddress(address) {
  const value = String(address || '').toLowerCase()
  return (
    value === '127.0.0.1' ||
    value === '::1' ||
    value === '::ffff:127.0.0.1'
  )
}

function bearerTokenMatches(header, expectedToken) {
  if (typeof header !== 'string' || typeof expectedToken !== 'string') {
    return false
  }
  const prefix = 'Bearer '
  if (!header.startsWith(prefix)) return false
  const actual = Buffer.from(header.slice(prefix.length))
  const expected = Buffer.from(expectedToken)
  return (
    actual.length === expected.length &&
    crypto.timingSafeEqual(actual, expected)
  )
}

function requestError(message, statusCode = 400) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

async function readJsonBody(request) {
  const chunks = []
  let received = 0
  for await (const chunk of request) {
    received += chunk.length
    if (received > MAX_REQUEST_BYTES) {
      throw requestError('请求内容过大', 413)
    }
    chunks.push(chunk)
  }
  if (chunks.length === 0) return {}
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'))
  } catch {
    throw requestError('请求内容不是有效 JSON')
  }
}

function writeJson(response, statusCode, payload) {
  const body = JSON.stringify(payload)
  response.writeHead(statusCode, {
    'Cache-Control': 'no-store',
    'Content-Length': Buffer.byteLength(body),
    'Content-Type': 'application/json; charset=utf-8',
  })
  response.end(body)
}

function createBrowserControlServer({ token, dispatch }) {
  if (typeof token !== 'string' || token.length < 24) {
    throw new Error('浏览器控制令牌无效')
  }
  if (typeof dispatch !== 'function') {
    throw new Error('浏览器控制处理器无效')
  }

  return http.createServer(async (request, response) => {
    try {
      if (!isLoopbackAddress(request.socket.remoteAddress)) {
        writeJson(response, 403, { error: '仅允许本机访问' })
        return
      }
      if (
        !bearerTokenMatches(
          request.headers.authorization,
          token,
        )
      ) {
        writeJson(response, 401, { error: '未授权' })
        return
      }

      const requestUrl = new URL(
        request.url || '/',
        'http://127.0.0.1',
      )
      const route = ROUTES.get(requestUrl.pathname)
      if (!route) {
        writeJson(response, 404, { error: '接口不存在' })
        return
      }
      if (request.method !== route.method) {
        response.setHeader('Allow', route.method)
        writeJson(response, 405, { error: '请求方法不受支持' })
        return
      }

      const body =
        route.method === 'POST'
          ? await readJsonBody(request)
          : {}
      const result = await dispatch(route.action, body)
      writeJson(response, 200, result ?? { ok: true })
    } catch (error) {
      const statusCode = Number(error?.statusCode) || 500
      writeJson(response, statusCode, {
        error:
          error instanceof Error ? error.message : '浏览器操作失败',
      })
    }
  })
}

function startBrowserControlServer(options) {
  const server = createBrowserControlServer(options)
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      server.off('error', reject)
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close()
        reject(new Error('无法获取浏览器控制端口'))
        return
      }
      resolve({
        server,
        url: `http://127.0.0.1:${address.port}`,
        close: () =>
          new Promise((closeResolve, closeReject) => {
            server.close((error) =>
              error ? closeReject(error) : closeResolve(),
            )
          }),
      })
    })
  })
}

module.exports = {
  bearerTokenMatches,
  createBrowserControlServer,
  isLoopbackAddress,
  startBrowserControlServer,
}
