const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { execFile } = require('node:child_process')

const DEFAULT_REPOSITORY_PATH = '/Users/panyong/shanjie/app_audit'
const TARGET_RELATIVE_PATH = path.join(
  'server',
  'apps',
  'aisg',
  'src',
  'main',
  'resources',
  'capa',
  'CAPA_CONFIG_AISG.json',
)
const MAX_PACKAGE_BYTES = 100 * 1024 * 1024
const MAX_CONFIG_BYTES = 20 * 1024 * 1024
const RECORD_WAIT_ATTEMPTS = 45
const RECORD_WAIT_INTERVAL_MS = 1000
const UNZIP_COMMAND =
  process.env.WORKCOVE_UNZIP_PATH ||
  (process.platform === 'darwin' ? '/usr/bin/unzip' : 'unzip')

function operationError(message, statusCode = 409) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function requireText(value, message, maxLength = 512) {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) throw operationError(message, 400)
  if (text.length > maxLength) {
    throw operationError(`${message}，内容过长`, 400)
  }
  return text
}

function capabilityVersionKey(value) {
  const text = typeof value === 'string' ? value.trim() : ''
  const match = text.match(/v\d+(?:\.\d+){1,}(?:_R\d+)?/i)
  return match ? match[0].toUpperCase() : ''
}

function parseCapabilityProductLine(siteUrl) {
  let parsed
  try {
    parsed = new URL(siteUrl)
  } catch {
    throw operationError('能力集网站地址无效', 400)
  }
  const match = parsed.hash.match(
    /\/productCapabilityList\/([^/?#]+)/i,
  )
  if (!match) return ''
  try {
    return decodeURIComponent(match[1]).trim()
  } catch {
    return match[1].trim()
  }
}

function capabilitySiteBaseUrl(siteUrl) {
  let parsed
  try {
    parsed = new URL(siteUrl)
  } catch {
    throw operationError('能力集网站地址无效', 400)
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw operationError('能力集网站仅支持 HTTP 或 HTTPS', 400)
  }
  parsed.hash = ''
  parsed.search = ''
  if (/\/index\.html?$/i.test(parsed.pathname)) {
    parsed.pathname = parsed.pathname.replace(/[^/]+$/i, '')
  } else if (!parsed.pathname.endsWith('/')) {
    parsed.pathname = parsed.pathname.replace(/[^/]*$/, '')
  }
  return parsed.toString()
}

function resolveCapabilityRow(rows, branch) {
  const version = capabilityVersionKey(branch)
  const candidates = (Array.isArray(rows) ? rows : []).filter(
    (row) =>
      row &&
      (version
        ? capabilityVersionKey(row.name) === version
        : String(row.name || '').trim() === branch.trim()),
  )
  if (candidates.length === 0) {
    throw operationError(
      `能力集列表中没有找到与分支 ${branch} 对应的能力级`,
      404,
    )
  }
  if (candidates.length > 1) {
    throw operationError(
      `能力集列表中存在多个同版本能力级：${candidates
        .map((row) => row.name)
        .join('、')}`,
    )
  }
  return candidates[0]
}

function latestCapabilityRecord(records) {
  return [...(Array.isArray(records) ? records : [])]
    .filter(
      (record) =>
        record &&
        typeof record.filePath === 'string' &&
        record.filePath.trim(),
    )
    .sort(
      (left, right) =>
        String(right.createTime || '').localeCompare(
          String(left.createTime || ''),
        ) ||
        Number(right.id || 0) - Number(left.id || 0),
    )[0] || null
}

function recordIdentity(record) {
  if (!record) return ''
  if (record.id !== undefined && record.id !== null) {
    return `id:${record.id}`
  }
  return `path:${String(record.filePath || '')}`
}

function serializeCapabilityMenus(menus) {
  if (!Array.isArray(menus)) return []
  return menus.map((menu) => ({
    id: menu?.id ?? null,
    orderNo: menu?.orderNo ?? null,
    roleCodeList: Array.isArray(menu?.roleCodeList)
      ? menu.roleCodeList
      : null,
    prdCodeList: Array.isArray(menu?.prdCodeList)
      ? menu.prdCodeList
      : null,
    child: serializeCapabilityMenus(menu?.child),
  }))
}

function buildCapabilitySavePayload(detail, productLine) {
  if (!detail || typeof detail !== 'object') {
    throw operationError('能力集详情为空，无法保存配置')
  }
  const menus =
    detail.config && Array.isArray(detail.config.menus)
      ? detail.config.menus
      : detail.menus
  if (!Array.isArray(menus)) {
    throw operationError('能力集详情缺少菜单配置，无法保存配置')
  }
  return {
    id: detail.id,
    name: detail.name,
    description:
      typeof detail.description === 'string'
        ? detail.description
        : '',
    productLine,
    config: {
      ...(detail.config && typeof detail.config === 'object'
        ? detail.config
        : {}),
      menus: serializeCapabilityMenus(menus),
    },
  }
}

function chooseCapabilityZipEntry(entries) {
  const files = (Array.isArray(entries) ? entries : [])
    .map((entry) => String(entry || '').replace(/\\/g, '/'))
    .filter(
      (entry) =>
        entry &&
        !entry.endsWith('/') &&
        path.posix.basename(entry).toLowerCase().startsWith(
          'capa_config_',
        ) &&
        path.posix.basename(entry).toLowerCase().endsWith('.json'),
    )
  const aisg = files.find(
    (entry) =>
      path.posix.basename(entry).toUpperCase() ===
      'CAPA_CONFIG_AISG.JSON',
  )
  if (aisg) return aisg
  const maf = files.find(
    (entry) =>
      path.posix.basename(entry).toUpperCase() ===
      'CAPA_CONFIG_MAF.JSON',
  )
  if (maf) return maf
  if (files.length === 1) return files[0]
  throw operationError(
    files.length === 0
      ? '下载压缩包中没有 CAPA_CONFIG_*.json'
      : `下载压缩包中包含多个能力集文件：${files.join('、')}`,
  )
}

function validateCapabilityConfigContent(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
    throw operationError('能力集文件为空')
  }
  if (buffer.length > MAX_CONFIG_BYTES) {
    throw operationError('能力集文件超过 20MB')
  }
  if (buffer.includes(0)) {
    throw operationError('能力集文件包含非法二进制内容')
  }
  const text = buffer.toString('utf8').trim()
  if (!text) throw operationError('能力集文件内容为空')
  if (text.startsWith('{') || text.startsWith('[')) {
    try {
      JSON.parse(text)
      return
    } catch {
      throw operationError('能力集 JSON 格式无效')
    }
  }
  if (
    !/^[A-Za-z0-9+/=\r\n]+$/.test(text) ||
    Buffer.from(text.replace(/\s+/g, ''), 'base64').length < 128
  ) {
    throw operationError('能力集文件内容不是有效的加密配置')
  }
}

function zipEntries(archivePath) {
  return new Promise((resolve, reject) => {
    execFile(
      UNZIP_COMMAND,
      ['-Z1', archivePath],
      { maxBuffer: 4 * 1024 * 1024 },
      (error, stdout, stderr) => {
        if (error) {
          reject(
            operationError(
              `读取能力集压缩包失败：${String(stderr || '').trim() || error.message}`,
            ),
          )
          return
        }
        resolve(
          String(stdout)
            .split(/\r?\n/)
            .map((entry) => entry.trim())
            .filter(Boolean),
        )
      },
    )
  })
}

function extractZipEntry(archivePath, entry) {
  return new Promise((resolve, reject) => {
    execFile(
      UNZIP_COMMAND,
      ['-p', archivePath, entry],
      {
        encoding: 'buffer',
        maxBuffer: MAX_CONFIG_BYTES,
      },
      (error, stdout, stderr) => {
        if (error) {
          reject(
            operationError(
              `解压能力集文件失败：${
                Buffer.isBuffer(stderr)
                  ? stderr.toString('utf8').trim()
                  : error.message
              }`,
            ),
          )
          return
        }
        resolve(Buffer.from(stdout))
      },
    )
  })
}

function sha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex')
}

async function capabilityApiRequest(
  siteBaseUrl,
  pathname,
  {
    method = 'GET',
    token = '',
    body,
    timeoutMs = 45_000,
  } = {},
) {
  const url = new URL(pathname.replace(/^\//, ''), siteBaseUrl)
  const headers = {
    Accept: 'application/json',
    'Cache-Control': 'no-cache',
    'Content-Type': 'application/json;charset=utf-8',
  }
  if (token) headers.token = token
  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  })
  const text = await response.text()
  let payload
  try {
    payload = JSON.parse(text || '{}')
  } catch {
    throw operationError(
      `能力集接口返回了非 JSON 内容（HTTP ${response.status}）`,
      response.status || 502,
    )
  }
  if (!response.ok) {
    throw operationError(
      payload?.msg ||
        payload?.message ||
        `能力集接口请求失败（HTTP ${response.status}）`,
      response.status,
    )
  }
  if (
    payload?.status !== undefined &&
    Number(payload.status) !== 0
  ) {
    throw operationError(
      payload.msg ||
        payload.message ||
        payload.errorMsg ||
        '能力集接口返回失败',
    )
  }
  return payload?.data
}

async function loginCapabilitySite(siteBaseUrl, username, password) {
  const data = await capabilityApiRequest(siteBaseUrl, 'login', {
    method: 'POST',
    body: { username, password },
  })
  const token =
    data && typeof data.token === 'string' ? data.token.trim() : ''
  if (!token) throw operationError('能力集网站登录成功但未返回 token')
  return token
}

async function listCapabilityRows(
  siteBaseUrl,
  token,
  productLine,
  branch,
) {
  const search = capabilityVersionKey(branch) || branch
  const query = new URLSearchParams({
    rows: '100',
    page: '1',
    productLine,
    name: search,
  })
  const data = await capabilityApiRequest(
    siteBaseUrl,
    `config/page?${query}`,
    { token },
  )
  return Array.isArray(data?.rows) ? data.rows : []
}

async function waitForNewCapabilityRecord(
  siteBaseUrl,
  token,
  productLine,
  branch,
  previousRecordKeys,
) {
  let lastError = null
  for (let attempt = 0; attempt < RECORD_WAIT_ATTEMPTS; attempt++) {
    if (attempt > 0) {
      await new Promise((resolve) =>
        setTimeout(resolve, RECORD_WAIT_INTERVAL_MS),
      )
    }
    try {
      const rows = await listCapabilityRows(
        siteBaseUrl,
        token,
        productLine,
        branch,
      )
      const row = resolveCapabilityRow(rows, branch)
      const record = latestCapabilityRecord(row.records)
      if (
        record &&
        !previousRecordKeys.has(recordIdentity(record))
      ) {
        return { row, record }
      }
    } catch (error) {
      lastError = error
    }
  }
  throw operationError(
    lastError instanceof Error
      ? `等待新能力集压缩包超时：${lastError.message}`
      : '等待新能力集压缩包超时，列表中没有出现新的下载记录',
    408,
  )
}

async function downloadCapabilityPackage(
  siteBaseUrl,
  token,
  record,
  destination,
) {
  const url = new URL('file/download', siteBaseUrl)
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Accept: '*/*',
      'Cache-Control': 'no-cache',
      'Content-Type': 'application/json;charset=utf-8',
      token,
    },
    body: JSON.stringify({ filePath: record.filePath }),
    signal: AbortSignal.timeout(120_000),
  })
  if (!response.ok) {
    throw operationError(
      `下载能力集压缩包失败（HTTP ${response.status}）`,
      response.status || 502,
    )
  }
  const declaredLength = Number(response.headers.get('content-length') || 0)
  if (declaredLength > MAX_PACKAGE_BYTES) {
    throw operationError('能力集压缩包超过 100MB')
  }
  const buffer = Buffer.from(await response.arrayBuffer())
  if (buffer.length === 0 || buffer.length > MAX_PACKAGE_BYTES) {
    throw operationError('能力集压缩包为空或超过 100MB')
  }
  if (
    buffer[0] !== 0x50 ||
    buffer[1] !== 0x4b
  ) {
    const text = buffer.toString('utf8').trim()
    throw operationError(
      text.startsWith('{')
        ? '能力集下载接口返回了错误信息而不是压缩包'
        : '下载内容不是有效的 ZIP 压缩包',
    )
  }
  fs.mkdirSync(path.dirname(destination), { recursive: true })
  fs.writeFileSync(destination, buffer)
  return buffer
}

function repositoryFilePath(repoPath) {
  const resolved = path.resolve(repoPath || DEFAULT_REPOSITORY_PATH)
  const absolute = path.resolve(resolved, TARGET_RELATIVE_PATH)
  if (!absolute.startsWith(`${resolved}${path.sep}`)) {
    throw operationError('能力集目标路径越界', 400)
  }
  return absolute
}

function installCapabilityConfig(
  content,
  targetPath,
  backupDirectory,
  version,
) {
  validateCapabilityConfigContent(content)
  if (!fs.existsSync(targetPath)) {
    throw operationError(`目标能力集文件不存在：${targetPath}`, 404)
  }
  fs.mkdirSync(backupDirectory, { recursive: true })
  const stamp = new Date()
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\..+$/, '')
  const safeVersion =
    (capabilityVersionKey(version) || 'capability').replace(
      /[^A-Za-z0-9._-]/g,
      '_',
    )
  const previous = fs.readFileSync(targetPath)
  const backupPath = path.join(
    backupDirectory,
    `CAPA_CONFIG_AISG-${safeVersion}-${stamp}-${sha256(previous).slice(0, 8)}.json`,
  )
  fs.writeFileSync(backupPath, previous)

  const tempPath = `${targetPath}.workcove-${process.pid}-${crypto
    .randomBytes(4)
    .toString('hex')}.tmp`
  const mode = fs.statSync(targetPath).mode & 0o777
  try {
    fs.writeFileSync(tempPath, content, { mode })
    fs.renameSync(tempPath, targetPath)
  } finally {
    fs.rmSync(tempPath, { force: true })
  }
  return {
    backupPath,
    sha256: sha256(content),
    bytes: content.length,
  }
}

async function updateCapabilitySet({
  siteUrl,
  username,
  password,
  branch,
  productLine,
  repoPath = DEFAULT_REPOSITORY_PATH,
  backupDirectory,
  confirm = false,
}) {
  const siteBaseUrl = capabilitySiteBaseUrl(siteUrl)
  const resolvedProductLine =
    requireText(productLine || parseCapabilityProductLine(siteUrl), '无法识别能力集产品线', 64)
  const resolvedBranch = requireText(branch, '无法识别 app_audit 当前分支', 200)
  const resolvedUsername = requireText(username, '密码库用户名不能为空', 256)
  const resolvedPassword = requireText(password, '密码库密码不能为空', 1024)
  const targetPath = repositoryFilePath(repoPath)
  if (!fs.existsSync(targetPath)) {
    throw operationError(`目标能力集文件不存在：${targetPath}`, 404)
  }

  const token = await loginCapabilitySite(
    siteBaseUrl,
    resolvedUsername,
    resolvedPassword,
  )
  const rows = await listCapabilityRows(
    siteBaseUrl,
    token,
    resolvedProductLine,
    resolvedBranch,
  )
  const row = resolveCapabilityRow(rows, resolvedBranch)
  const currentRecord = latestCapabilityRecord(row.records)
  const preview = {
    site: siteBaseUrl,
    productLine: resolvedProductLine,
    branch: resolvedBranch,
    capabilityId: row.id,
    capabilityName: row.name,
    currentPackage: currentRecord?.fileName || '',
    targetPath,
  }
  if (!confirm) {
    return {
      status: 'preview',
      ...preview,
    }
  }

  const detail = await capabilityApiRequest(
    siteBaseUrl,
    `config/detail?${new URLSearchParams({
      id: String(row.id),
      productLine: resolvedProductLine,
    })}`,
    { token },
  )
  const savePayload = buildCapabilitySavePayload(
    detail,
    resolvedProductLine,
  )
  const previousRecordKeys = new Set(
    (Array.isArray(row.records) ? row.records : [])
      .map(recordIdentity)
      .filter(Boolean),
  )
  await capabilityApiRequest(siteBaseUrl, 'config/save', {
    method: 'POST',
    token,
    body: savePayload,
  })
  const { row: updatedRow, record } =
    await waitForNewCapabilityRecord(
      siteBaseUrl,
      token,
      resolvedProductLine,
      resolvedBranch,
      previousRecordKeys,
    )

  fs.mkdirSync(backupDirectory, { recursive: true })
  const stamp = new Date()
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\..+$/, '')
  const archivePath = path.join(
    backupDirectory,
    `capability-${capabilityVersionKey(resolvedBranch) || 'package'}-${stamp}.zip`,
  )
  await downloadCapabilityPackage(
    siteBaseUrl,
    token,
    record,
    archivePath,
  )
  const entries = await zipEntries(archivePath)
  const entry = chooseCapabilityZipEntry(entries)
  const configContent = await extractZipEntry(archivePath, entry)
  const installed = installCapabilityConfig(
    configContent,
    targetPath,
    backupDirectory,
    resolvedBranch,
  )
  return {
    status: 'updated',
    ...preview,
    capabilityName: updatedRow.name,
    packageName: record.fileName,
    packagePath: archivePath,
    packageEntry: entry,
    ...installed,
  }
}

module.exports = {
  DEFAULT_REPOSITORY_PATH,
  TARGET_RELATIVE_PATH,
  buildCapabilitySavePayload,
  capabilitySiteBaseUrl,
  capabilityVersionKey,
  chooseCapabilityZipEntry,
  installCapabilityConfig,
  latestCapabilityRecord,
  parseCapabilityProductLine,
  resolveCapabilityRow,
  serializeCapabilityMenus,
  updateCapabilitySet,
  validateCapabilityConfigContent,
}
