import { execFile, spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const ACTION_HEADER = 'x-workcove-action'
const ICON_SCRIPT = `
ObjC.import('AppKit')
ObjC.import('Foundation')
function run(argv) {
  const filePath = argv[0]
  const workspace = $.NSWorkspace.sharedWorkspace
  const image = workspace.iconForFile($(filePath))
  image.size = $.NSMakeSize(128, 128)
  const bitmap = $.NSBitmapImageRep.imageRepWithData(image.TIFFRepresentation)
  const png = bitmap.representationUsingTypeProperties(
    $.NSBitmapImageFileTypePNG,
    $()
  )
  return 'data:image/png;base64,' + png.base64EncodedStringWithOptions(0).js
}
`

function sendJson(response, status, body) {
  response.statusCode = status
  response.setHeader('Content-Type', 'application/json; charset=utf-8')
  response.end(JSON.stringify(body))
}

function sendError(response, status, message) {
  sendJson(response, status, {
    code: status === 400 ? 'INVALID_REQUEST' : 'HOST_ACTION_FAILED',
    message,
  })
}

function requireHostAction(request, response, action) {
  if (request.headers[ACTION_HEADER] !== action) {
    sendError(response, 400, '本机操作请求无效')
    return false
  }
  return true
}

function applicationPath(value) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error('应用路径无效')
  }
  const resolved = path.resolve(value.trim())
  if (!resolved.endsWith('.app') || !fs.existsSync(resolved)) {
    throw new Error('应用不存在或不是有效的 macOS 应用')
  }
  return resolved
}

async function readJsonBody(request) {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  if (chunks.length === 0) return {}
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

async function readApplicationIcon(appPath) {
  try {
    const { stdout } = await execFileAsync(
      '/usr/bin/osascript',
      ['-l', 'JavaScript', '-e', ICON_SCRIPT, appPath],
      { maxBuffer: 4 * 1024 * 1024, timeout: 30_000 },
    )
    const icon = stdout.trim()
    return icon.startsWith('data:image/') ? icon : null
  } catch {
    return null
  }
}

async function chooseApplication() {
  if (process.platform !== 'darwin') {
    throw new Error('当前系统暂不支持选择应用')
  }
  let stdout
  try {
    const result = await execFileAsync(
      '/usr/bin/osascript',
      [
        '-e',
        'POSIX path of (choose file with prompt "选择 8小时工作台应用" of type {"com.apple.application-bundle"} default location (path to applications folder))',
      ],
      { maxBuffer: 1024 * 1024, timeout: 120_000 },
    )
    stdout = result.stdout
  } catch (error) {
    const details = `${error?.stderr ?? ''} ${error?.message ?? ''}`
    if (details.includes('User canceled') || details.includes('(-128)')) {
      return null
    }
    console.error('[application-host] choose failed:', details.trim())
    throw new Error(
      details.includes('not authorized') || details.includes('-1743')
        ? '系统未授权 8小时工作台打开应用选择器'
        : '无法打开系统应用选择器',
    )
  }

  const selectedPath = stdout
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('/'))
    .at(-1)
  const appPath = applicationPath(selectedPath)
  return {
    path: appPath,
    name: path.basename(appPath, '.app'),
    iconDataUrl: await readApplicationIcon(appPath),
  }
}

function openApplication(appPath) {
  return new Promise((resolve, reject) => {
    const child = spawn('/usr/bin/open', [applicationPath(appPath)], {
      detached: true,
      stdio: 'ignore',
    })
    child.once('error', () => reject(new Error('无法启动应用')))
    child.once('spawn', () => {
      child.unref()
      resolve()
    })
  })
}

async function forceQuitApplication(appPath) {
  if (process.platform !== 'darwin') {
    throw new Error('当前系统暂不支持强制退出应用')
  }
  const application = applicationPath(appPath)
  const { stdout } = await execFileAsync(
    '/bin/ps',
    ['-axo', 'pid=,command='],
    { maxBuffer: 4 * 1024 * 1024 },
  )
  const executablePrefix = `${application}/Contents/MacOS/`
  const processIds = stdout
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.includes(executablePrefix))
    .map((line) => Number(line.split(/\s+/, 1)[0]))
    .filter((pid) => Number.isInteger(pid) && pid > 0)

  if (processIds.length === 0) {
    throw new Error('应用未在运行')
  }

  let failed = false
  for (const pid of processIds) {
    try {
      process.kill(pid, 'SIGKILL')
    } catch {
      failed = true
    }
  }
  if (failed) throw new Error('部分应用进程未能退出')
}

async function readApplicationStatuses(rawPaths) {
  if (!Array.isArray(rawPaths)) return []
  const paths = [
    ...new Set(
      rawPaths
        .filter(
          (item) =>
            typeof item === 'string' &&
            item.trim() &&
            item.length <= 1024,
        )
        .map((item) => item.trim()),
    ),
  ]
  if (paths.length > 200) {
    throw new Error('一次最多检测 200 个应用')
  }
  if (process.platform !== 'darwin') {
    return paths.map((item) => ({ path: item, running: false }))
  }

  const { stdout } = await execFileAsync(
    '/bin/ps',
    ['-axo', 'pid=,command='],
    { maxBuffer: 4 * 1024 * 1024 },
  )
  return paths.map((item) => ({
    path: item,
    running: stdout.includes(
      `${path.resolve(item)}/Contents/MacOS/`,
    ),
  }))
}

async function handleRequest(request, response) {
  const pathname = new URL(
    request.url ?? '/',
    'http://127.0.0.1',
  ).pathname
  if (!pathname.startsWith('/api/host/applications/')) return false
  if (request.method !== 'POST') {
    sendError(response, 400, '请求方法不受支持')
    return true
  }

  try {
    if (pathname.endsWith('/running')) {
      if (!requireHostAction(request, response, 'application-running')) {
        return true
      }
      const body = await readJsonBody(request)
      sendJson(
        response,
        200,
        await readApplicationStatuses(body.paths),
      )
      return true
    }

    if (pathname.endsWith('/picker')) {
      if (
        !requireHostAction(
          request,
          response,
          'choose-application',
        )
      ) {
        return true
      }
      sendJson(response, 200, await chooseApplication())
      return true
    }

    if (pathname.endsWith('/open')) {
      if (!requireHostAction(request, response, 'open-application')) {
        return true
      }
      const body = await readJsonBody(request)
      await openApplication(body.path)
      sendJson(response, 200, {})
      return true
    }

    if (pathname.endsWith('/force-quit')) {
      if (
        !requireHostAction(
          request,
          response,
          'force-quit-application',
        )
      ) {
        return true
      }
      const body = await readJsonBody(request)
      await forceQuitApplication(body.path)
      sendJson(response, 200, {})
      return true
    }

    sendError(response, 404, '本机操作接口不存在')
    return true
  } catch (error) {
    sendError(
      response,
      400,
      error instanceof Error ? error.message : '本机操作失败',
    )
    return true
  }
}

export function applicationHostPlugin() {
  return {
    name: 'workcove-application-host',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        if (await handleRequest(request, response)) return
        next()
      })
    },
  }
}
