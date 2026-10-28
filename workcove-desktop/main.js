const {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  safeStorage,
  session,
  shell,
  systemPreferences,
} = require('electron')
const { execFile, spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { pathToFileURL } = require('node:url')
const { promisify } = require('node:util')
const { getCcSwitchUsage } = require('./ccSwitchUsage')
const releaseInfo = require('./release-info.json')
const { RuntimeManager, releaseRank, sha256 } = require('./runtime-manager')

const APP_TITLE = app.isPackaged
  ? '8小时工作台'
  : '8小时工作台 Dev'
const DEV_WEB_URL = process.env.WORKCOVE_WEB_URL ?? 'http://127.0.0.1:32581'
const CLIENT_PORT = 8890
const CLIENT_ORIGIN = `http://127.0.0.1:${CLIENT_PORT}`
const CLIENT_DB_URL =
  'jdbc:mysql://127.0.0.1:3306/workcove_client?characterEncoding=UTF-8&connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true'
const CLIENT_PARTITION = 'persist:workcove-client'
const DEV_PARTITION = 'persist:workcove-dev'
const UPDATE_INTERVAL_MS = 30 * 60 * 1000
const TOUCH_ID_STORE_VERSION = 1
const TOUCH_ID_STORE_FILE = 'vault-touch-id.json'
const execFileAsync = promisify(execFile)

app.setPath(
  'userData',
  path.join(
    app.getPath('appData'),
    app.isPackaged ? 'com.pydlove.workcove' : 'com.pydlove.workcove.dev',
  ),
)

let mainWindow = null
let backendProcess = null
let backendLogStream = null
let runtimeManager = null
let runtimeStartup = null
let updateTimer = null
let updateCheckRunning = false
let promptedReleaseCode = null

function nativeTouchIdAvailable() {
  return (
    process.platform === 'darwin' &&
    safeStorage.isEncryptionAvailable() &&
    systemPreferences.canPromptTouchID()
  )
}

function touchIdStorePath() {
  return path.join(app.getPath('userData'), TOUCH_ID_STORE_FILE)
}

function touchIdSecretKey(accountKey) {
  return sha256(
    typeof accountKey === 'string' && accountKey.trim()
      ? accountKey.trim().toLocaleLowerCase('en-US')
      : 'default',
  )
}

function readTouchIdStore() {
  try {
    const parsed = JSON.parse(
      fs.readFileSync(touchIdStorePath(), 'utf8'),
    )
    if (
      parsed?.version !== TOUCH_ID_STORE_VERSION ||
      !parsed.secrets ||
      typeof parsed.secrets !== 'object'
    ) {
      return { version: TOUCH_ID_STORE_VERSION, secrets: {} }
    }
    return parsed
  } catch {
    return { version: TOUCH_ID_STORE_VERSION, secrets: {} }
  }
}

function writeTouchIdStore(store) {
  const target = touchIdStorePath()
  const temporary = `${target}.tmp`
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.writeFileSync(temporary, JSON.stringify(store), { mode: 0o600 })
  fs.renameSync(temporary, target)
  fs.chmodSync(target, 0o600)
}

function hasTouchIdSecret(accountKey) {
  if (!nativeTouchIdAvailable()) return false
  return Boolean(
    readTouchIdStore().secrets[touchIdSecretKey(accountKey)]?.cipher,
  )
}

function storeTouchIdSecret(accountKey, secret) {
  if (!nativeTouchIdAvailable()) {
    throw new Error('当前系统不支持触控 ID')
  }
  if (
    typeof secret !== 'string' ||
    !secret ||
    secret.length > 4096
  ) {
    throw new Error('密码无效')
  }
  const store = readTouchIdStore()
  store.secrets[touchIdSecretKey(accountKey)] = {
    cipher: safeStorage.encryptString(secret).toString('base64'),
    updatedAt: new Date().toISOString(),
  }
  writeTouchIdStore(store)
}

async function unlockTouchIdSecret(accountKey) {
  if (!nativeTouchIdAvailable()) {
    throw new Error('当前系统不支持触控 ID')
  }
  const entry =
    readTouchIdStore().secrets[touchIdSecretKey(accountKey)]
  if (!entry?.cipher) {
    throw new Error('尚未启用触控 ID')
  }
  await systemPreferences.promptTouchID('解锁 WorkCove 密码库')
  try {
    return safeStorage.decryptString(
      Buffer.from(entry.cipher, 'base64'),
    )
  } catch {
    throw new Error('触控 ID 凭据已失效，请重新启用')
  }
}

function clearTouchIdSecret(accountKey) {
  const store = readTouchIdStore()
  delete store.secrets[touchIdSecretKey(accountKey)]
  writeTouchIdStore(store)
}

async function createWindow() {
  if (app.isPackaged) {
    await session
      .fromPartition(CLIENT_PARTITION)
      .clearCache()
  }
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 980,
    minHeight: 680,
    title: APP_TITLE,
    backgroundColor: '#eceef2',
    show: false,
    frame: false,
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      partition: app.isPackaged ? CLIENT_PARTITION : DEV_PARTITION,
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  mainWindow.once('ready-to-show', () => mainWindow?.show())
  mainWindow.on('closed', () => {
    if (updateTimer) {
      clearInterval(updateTimer)
      updateTimer = null
    }
    mainWindow = null
  })
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.once('did-finish-load', () => {
    runtimeManager?.markHealthy()
    if (app.isPackaged) {
      void checkForClientUpdate()
      updateTimer = setInterval(
        () => void checkForClientUpdate(),
        UPDATE_INTERVAL_MS,
      )
    }
  })

  void mainWindow.loadURL(
    app.isPackaged
      ? `${CLIENT_ORIGIN}/login?runtime=${encodeURIComponent(
          runtimeStartup?.releaseCode || releaseInfo.releaseCode,
        )}`
      : DEV_WEB_URL,
  )
}

async function chooseApplicationFromDialog() {
  const filters = process.platform === 'darwin'
    ? [{ name: 'macOS 应用', extensions: ['app'] }]
    : process.platform === 'win32'
      ? [{ name: 'Windows 应用', extensions: ['exe', 'lnk', 'bat', 'cmd'] }]
      : []
  const result = await dialog.showOpenDialog(mainWindow, {
    title: '选择本地软件',
    properties: ['openFile', 'openDirectory'],
    filters,
  })
  if (result.canceled || result.filePaths.length === 0) return null

  const applicationPath = result.filePaths[0]
  if (!fs.existsSync(applicationPath)) {
    throw new Error('所选应用不存在')
  }
  if (process.platform === 'darwin' && !applicationPath.endsWith('.app')) {
    throw new Error('请选择 macOS .app 应用')
  }

  const icon = await app.getFileIcon(applicationPath, { size: 'large' })
  return {
    path: applicationPath,
    name: path.basename(applicationPath, path.extname(applicationPath)),
    iconDataUrl: icon.isEmpty() ? null : icon.toDataURL(),
  }
}

async function chooseDirectoryFromDialog() {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: '选择项目目录',
    properties: ['openDirectory', 'createDirectory'],
  })
  return result.canceled ? null : result.filePaths[0] ?? null
}

async function getCcSwitchStatus() {
  const home = os.homedir()
  const applicationCandidates = [
    '/Applications/CC Switch.app',
    path.join(home, 'Applications/CC Switch.app'),
  ]
  const appPath =
    applicationCandidates.find((candidate) => fs.existsSync(candidate)) ??
    null
  const dataDirectory = path.join(home, '.cc-switch')
  const dataPath = fs.existsSync(dataDirectory) ? dataDirectory : null

  return {
    available: Boolean(appPath || dataPath),
    appPath,
    dataPath,
  }
}

async function getApplicationStatuses(targetPaths) {
  const paths = Array.isArray(targetPaths)
    ? [
        ...new Set(
          targetPaths.filter(
            (targetPath) =>
              typeof targetPath === 'string' && targetPath.trim(),
          ),
        ),
      ]
    : []
  if (paths.length === 0) return []
  if (paths.length > 200) throw new Error('一次最多检测 200 个应用')
  if (process.platform !== 'darwin') {
    return paths.map((targetPath) => ({ path: targetPath, running: false }))
  }

  let output
  try {
    const result = await execFileAsync('/bin/ps', ['-axo', 'pid=,command='], {
      maxBuffer: 4 * 1024 * 1024,
    })
    output = result.stdout
  } catch {
    throw new Error('无法读取正在运行的应用')
  }

  return paths.map((targetPath) => {
    const applicationPath = path.resolve(targetPath)
    const executablePrefix = `${applicationPath}/Contents/MacOS/`
    return {
      path: targetPath,
      running: output.includes(executablePrefix),
    }
  })
}

async function forceQuitApplication(targetPath) {
  if (typeof targetPath !== 'string' || !targetPath.trim()) {
    return '应用路径无效'
  }

  const applicationPath = path.resolve(targetPath)
  if (!fs.existsSync(applicationPath)) {
    return '应用不存在或已被移动'
  }
  if (process.platform !== 'darwin') {
    return '当前系统暂不支持强制退出应用'
  }
  if (!applicationPath.endsWith('.app')) {
    return '请选择 macOS 应用'
  }

  let output
  try {
    const result = await execFileAsync('/bin/ps', ['-axo', 'pid=,command='], {
      maxBuffer: 4 * 1024 * 1024,
    })
    output = result.stdout
  } catch {
    return '无法读取正在运行的应用'
  }

  const executablePrefix = `${applicationPath}/Contents/MacOS/`
  const processIds = output
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.includes(executablePrefix))
    .map((line) => Number(line.split(/\s+/, 1)[0]))
    .filter((pid) => Number.isInteger(pid) && pid > 0)

  if (processIds.length === 0) return '应用未在运行'

  let failed = false
  processIds.forEach((pid) => {
    try {
      process.kill(pid, 'SIGKILL')
    } catch {
      failed = true
    }
  })
  return failed ? '部分应用进程未能退出' : ''
}

function findIdeaExecutable() {
  if (process.platform !== 'darwin') return null
  const home = os.homedir()
  const candidates = [
    process.env.WORKCOVE_IDEA_PATH,
    '/Applications/IntelliJ IDEA.app/Contents/MacOS/idea',
    path.join(home, 'Applications/IntelliJ IDEA.app/Contents/MacOS/idea'),
    '/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea',
    path.join(
      home,
      'Applications/JetBrains Toolbox/IntelliJ IDEA Ultimate.app/Contents/MacOS/idea',
    ),
    path.join(
      home,
      'Applications/JetBrains Toolbox/IntelliJ IDEA Community Edition.app/Contents/MacOS/idea',
    ),
  ].filter(Boolean)
  return candidates.find((candidate) => fs.existsSync(candidate)) ?? null
}

async function openProjectWithIdea(targetPath) {
  if (typeof targetPath !== 'string' || !targetPath.trim()) {
    return '项目目录无效'
  }
  let stats
  try {
    stats = fs.statSync(targetPath)
  } catch {
    return '项目目录不存在或无法访问'
  }
  if (!stats.isDirectory()) return '项目路径不是目录'

  const ideaExecutable = findIdeaExecutable()
  if (!ideaExecutable) {
    return '未找到 IntelliJ IDEA，请确认已安装'
  }

  return new Promise((resolve) => {
    const child = spawn(ideaExecutable, [targetPath], {
      detached: true,
      stdio: 'ignore',
    })
    child.once('error', () => resolve('无法启动 IntelliJ IDEA'))
    child.once('spawn', () => {
      child.unref()
      resolve('')
    })
  })
}

function clientDataPath(...parts) {
  return path.join(app.getPath('userData'), ...parts)
}

function preparePackagedRuntime() {
  const builtinDirectory = path.join(process.resourcesPath, 'runtime')
  runtimeManager = new RuntimeManager({
    userData: app.getPath('userData'),
    builtinDirectory,
    builtinRelease: releaseInfo.releaseCode,
    shellRelease: releaseInfo.shellRelease,
  })
  runtimeStartup = runtimeManager.prepareStartup()
  runtimeManager.log(
    `Starting client ${releaseInfo.releaseCode}, runtime ${runtimeStartup.releaseCode}`,
  )
}

function backendJavaExecutable() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java'
  return path.join(process.resourcesPath, 'jre', 'bin', executable)
}

function backendEnvironment(runtimeDirectory) {
  const backendHome = clientDataPath('backend')
  const rendererDirectory = path.join(runtimeDirectory, 'renderer')
  const configDirectory = path.join(runtimeDirectory, 'config')
  return {
    ...process.env,
    WORKCOVE_PROFILE: 'client,local',
    WORKCOVE_SERVER_PORT: String(CLIENT_PORT),
    WORKCOVE_DB_URL: process.env.WORKCOVE_CLIENT_DB_URL || CLIENT_DB_URL,
    WORKCOVE_HOME: backendHome,
    WORKCOVE_RELEASES_DIR: path.join(backendHome, 'downloads'),
    WORKCOVE_ATTACHMENTS_DIR: path.join(backendHome, 'attachments'),
    WORKCOVE_WEB_ALLOWED_ORIGINS: CLIENT_ORIGIN,
    WORKCOVE_VAULT_ALLOWED_ORIGINS: CLIENT_ORIGIN,
    SPRING_WEB_RESOURCES_STATIC_LOCATIONS:
      `${pathToFileURL(rendererDirectory).href}/`,
    SPRING_CONFIG_ADDITIONAL_LOCATION:
      `${pathToFileURL(configDirectory).href}/`,
  }
}

async function startBackend() {
  if (!app.isPackaged) return
  const runtimeDirectory = runtimeStartup.directory
  const jarPath = path.join(
    runtimeDirectory,
    'backend',
    'workcove-boot.jar',
  )
  const libraryDirectory = path.join(process.resourcesPath, 'backend-lib')
  const javaPath = backendJavaExecutable()
  if (!fs.existsSync(javaPath)) {
    throw new Error(`客户端缺少 Java 运行时：${javaPath}`)
  }
  if (!fs.existsSync(jarPath)) {
    throw new Error(`客户端缺少后端程序：${jarPath}`)
  }
  if (!fs.existsSync(libraryDirectory)) {
    throw new Error(`客户端缺少后端依赖：${libraryDirectory}`)
  }

  const backendHome = clientDataPath('backend')
  fs.mkdirSync(backendHome, { recursive: true })
  const logPath = path.join(backendHome, 'backend.log')
  backendLogStream = fs.createWriteStream(logPath, { flags: 'a' })
  const classPath = [
    jarPath,
    path.join(libraryDirectory, '*'),
  ].join(path.delimiter)
  backendProcess = spawn(
    javaPath,
    [
      '-cp',
      classPath,
      'com.pydlove.workcove.boot.WorkCoveApplication',
    ],
    {
      cwd: backendHome,
      env: backendEnvironment(runtimeDirectory),
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    },
  )
  backendProcess.stdout.pipe(backendLogStream, { end: false })
  backendProcess.stderr.pipe(backendLogStream, { end: false })
  backendProcess.once('exit', () => {
    backendProcess = null
  })

  await waitForBackend()
}

async function waitForBackend() {
  const deadline = Date.now() + 90_000
  while (Date.now() < deadline) {
    if (!backendProcess) {
      throw new Error(
        '客户端后端启动失败。首次使用请让 MySQL 管理员执行安装包内的 setup-client-db.sql，然后查看客户端后端日志。',
      )
    }
    try {
      const response = await fetch(
        `${CLIENT_ORIGIN}/actuator/health`,
        { signal: AbortSignal.timeout(1500) },
      )
      if (response.ok) return
    } catch {
      // Keep polling while the embedded backend starts.
    }
    await new Promise((resolve) => setTimeout(resolve, 400))
  }
  throw new Error(
    '客户端后端启动超时。首次使用请让 MySQL 管理员执行安装包内的 setup-client-db.sql，然后查看客户端后端日志。',
  )
}

function stopBackend() {
  if (backendProcess) {
    backendProcess.kill('SIGTERM')
    backendProcess = null
  }
  backendLogStream?.end()
  backendLogStream = null
}

function updatePreferencesPath() {
  return clientDataPath('update-preferences.json')
}

function readSkippedRelease() {
  try {
    const value = JSON.parse(
      fs.readFileSync(updatePreferencesPath(), 'utf8'),
    )
    return releaseRank(value.skippedRelease) !== null
      ? value.skippedRelease
      : null
  } catch {
    return null
  }
}

function writeSkippedRelease(releaseCode) {
  if (releaseRank(releaseCode) === null) return null
  fs.mkdirSync(path.dirname(updatePreferencesPath()), { recursive: true })
  fs.writeFileSync(
    updatePreferencesPath(),
    `${JSON.stringify({ skippedRelease: releaseCode }, null, 2)}\n`,
    'utf8',
  )
  return releaseCode
}

function updateManifestUrl() {
  const configured = process.env.WORKCOVE_UPDATE_MANIFEST_URL?.trim()
  if (configured) return configured
  try {
    const value = JSON.parse(
      fs.readFileSync(
        clientDataPath('update-source.json'),
        'utf8',
      ),
    )
    if (
      typeof value.manifestUrl === 'string' &&
      value.manifestUrl.trim()
    ) {
      return value.manifestUrl.trim()
    }
  } catch {
    // Fall back to the local release endpoint.
  }
  return `${CLIENT_ORIGIN}/api/release/latest`
}

async function checkForClientUpdate() {
  if (
    !app.isPackaged ||
    !mainWindow ||
    mainWindow.isDestroyed() ||
    updateCheckRunning
  ) {
    return
  }
  updateCheckRunning = true
  try {
    const manifestUrl = updateManifestUrl()
    const response = await fetch(manifestUrl, {
      cache: 'no-store',
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) return
    const latest = await response.json()
    const runtime = latest.runtime
    const releaseCode = runtime?.releaseCode
    const currentRank = releaseRank(runtimeStartup.releaseCode)
    const targetRank = releaseRank(releaseCode)
    if (
      targetRank === null ||
      currentRank === null ||
      targetRank <= currentRank ||
      releaseCode === promptedReleaseCode ||
      releaseCode === readSkippedRelease()
    ) {
      return
    }
    promptedReleaseCode = releaseCode
    const minimumShell = releaseRank(runtime.minShellRelease)
    const shellRank = releaseRank(releaseInfo.shellRelease)
    if (
      minimumShell !== null &&
      (shellRank === null || minimumShell > shellRank)
    ) {
      const downloadUrl = latest.downloads?.macos?.url
      const { response } = await dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: '需要完整客户端更新',
        message: `新版本 ${latest.version || releaseCode} 需要更新客户端本体`,
        detail: '当前增量更新与客户端壳不兼容，请下载并安装完整客户端。',
        buttons: downloadUrl ? ['打开下载', '稍后'] : ['知道了'],
        defaultId: 0,
        cancelId: downloadUrl ? 1 : 0,
      })
      if (response === 0 && downloadUrl) {
        await shell.openExternal(
          new URL(downloadUrl, manifestUrl).toString(),
        )
      }
      return
    }
    const { response: choice } = await dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: '发现客户端更新',
      message: `新版本 ${latest.version || releaseCode} 已可用`,
      detail: String(
        latest.notes || '包含功能更新和问题修复，升级不会影响当前数据库。',
      ),
      buttons: ['立即更新', '稍后', '跳过此版本'],
      defaultId: 0,
      cancelId: 1,
    })
    if (choice === 2) {
      writeSkippedRelease(releaseCode)
      return
    }
    if (choice === 0) {
      await installRuntimeUpdate(runtime, manifestUrl)
    }
  } catch {
    // Update checks stay silent so a missing release server never blocks work.
  } finally {
    updateCheckRunning = false
  }
}

async function installRuntimeUpdate(runtime, manifestUrl) {
  if (!runtime?.url || !runtime?.sha256) {
    throw new Error('更新清单缺少 Runtime 下载信息')
  }
  const workDirectory = fs.mkdtempSync(
    path.join(app.getPath('temp'), 'workcove-update-'),
  )
  const archivePath = path.join(workDirectory, 'workcove-runtime.zip')
  try {
    mainWindow?.setTitle(`${APP_TITLE} · 正在下载更新`)
    await downloadFile(
      new URL(runtime.url, manifestUrl).toString(),
      archivePath,
      runtime.sizeBytes,
    )
    mainWindow?.setProgressBar(1)
    const content = fs.readFileSync(archivePath)
    if (sha256(content) !== String(runtime.sha256).toLowerCase()) {
      throw new Error('更新包校验失败，请稍后重试')
    }
    runtimeManager.install(archivePath, runtime)
    const { response } = await dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: '更新已准备好',
      message: '新版本已下载并安装',
      detail: '重启客户端后生效。',
      buttons: ['立即重启', '稍后'],
      defaultId: 0,
      cancelId: 1,
    })
    if (response === 0) {
      app.relaunch()
      app.quit()
    }
  } catch (error) {
    await dialog.showMessageBox(mainWindow, {
      type: 'error',
      title: '客户端更新失败',
      message: '无法安装新版本',
      detail: error instanceof Error ? error.message : String(error),
    })
    throw error
  } finally {
    mainWindow?.setProgressBar(-1)
    mainWindow?.setTitle(APP_TITLE)
    fs.rmSync(workDirectory, { recursive: true, force: true })
  }
}

async function downloadFile(url, destination, expectedSize) {
  const response = await fetch(url, {
    cache: 'no-store',
    signal: AbortSignal.timeout(120_000),
  })
  if (!response.ok || !response.body) {
    throw new Error(`下载更新失败（HTTP ${response.status}）`)
  }
  const total = Number(expectedSize || response.headers.get('content-length') || 0)
  const output = fs.createWriteStream(destination)
  const reader = response.body.getReader()
  let received = 0
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      received += value.byteLength
      await new Promise((resolve, reject) => {
        output.write(Buffer.from(value), (error) =>
          error ? reject(error) : resolve(),
        )
      })
      if (total > 0) {
        mainWindow?.setProgressBar(
          Math.min(0.98, received / total),
          { mode: 'normal' },
        )
      }
    }
  } finally {
    await new Promise((resolve) => output.end(resolve))
  }
  if (total > 0 && received !== total) {
    throw new Error('更新包下载不完整')
  }
}

async function initializeClient() {
  if (app.isPackaged) {
    preparePackagedRuntime()
    await startBackend()
  }
  await createWindow()
}

const gotSingleInstanceLock = app.requestSingleInstanceLock()
if (!gotSingleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.focus()
    }
  })
}

app.whenReady().then(async () => {
  if (!gotSingleInstanceLock) return
  if (process.platform === 'darwin' && app.dock) {
    app.dock.setIcon(path.join(__dirname, 'assets', 'icon.png'))
  }

  ipcMain.handle('window:close', () => mainWindow?.close())
  ipcMain.handle('window:minimize', () => mainWindow?.minimize())
  ipcMain.handle('window:toggle-maximize', () => {
    if (!mainWindow) return
    if (mainWindow.isMaximized()) mainWindow.unmaximize()
    else mainWindow.maximize()
  })
  ipcMain.handle('shortcut:choose-application', chooseApplicationFromDialog)
  ipcMain.handle('shortcut:application-statuses', (_event, targetPaths) =>
    getApplicationStatuses(targetPaths),
  )
  ipcMain.handle('local:cc-switch-status', getCcSwitchStatus)
  ipcMain.handle('local:cc-switch-usage', (_event, range) =>
    getCcSwitchUsage(range),
  )
  ipcMain.handle('development:choose-directory', chooseDirectoryFromDialog)
  ipcMain.handle('development:open-with-idea', async (_event, targetPath) =>
    openProjectWithIdea(targetPath),
  )
  ipcMain.handle('shortcut:open-path', async (_event, targetPath) => {
    if (typeof targetPath !== 'string' || !targetPath.trim()) {
      return '应用路径无效'
    }
    if (!fs.existsSync(targetPath)) return '应用不存在或已被移动'
    return shell.openPath(targetPath)
  })
  ipcMain.handle('shortcut:force-quit-application', async (_event, targetPath) =>
    forceQuitApplication(targetPath),
  )
  ipcMain.handle('shortcut:open-external', async (_event, targetUrl) => {
    try {
      const parsed = new URL(targetUrl)
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        return '网址协议不受支持'
      }
      await shell.openExternal(parsed.toString())
      return ''
    } catch {
      return '网址格式无效'
    }
  })
  ipcMain.handle('vault:touch-id-available', () =>
    nativeTouchIdAvailable(),
  )
  ipcMain.handle('vault:touch-id-status', (_event, accountKey) =>
    hasTouchIdSecret(accountKey),
  )
  ipcMain.handle(
    'vault:touch-id-store',
    (_event, accountKey, secret) =>
      storeTouchIdSecret(accountKey, secret),
  )
  ipcMain.handle(
    'vault:touch-id-unlock',
    (_event, accountKey) => unlockTouchIdSecret(accountKey),
  )
  ipcMain.handle('vault:touch-id-clear', (_event, accountKey) =>
    clearTouchIdSecret(accountKey),
  )

  try {
    await initializeClient()
  } catch (error) {
    dialog.showErrorBox(
      `${APP_TITLE} 启动失败`,
      error instanceof Error ? error.stack || error.message : String(error),
    )
    stopBackend()
    app.quit()
    return
  }
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      void createWindow()
    }
  })
})

app.on('before-quit', stopBackend)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
