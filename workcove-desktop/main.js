const {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Menu,
  safeStorage,
  session,
  shell,
  systemPreferences,
  WebContentsView,
} = require('electron')
const crypto = require('node:crypto')
const { execFile, spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { pathToFileURL } = require('node:url')
const { promisify } = require('node:util')
const {
  DEFAULT_REPOSITORY_PATH,
  updateCapabilitySet,
} = require('./capability-set-update')
const { getCcSwitchUsage } = require('./ccSwitchUsage')
const {
  fillCredentialsIntoContents,
} = require('./credential-autofill')
const releaseInfo = require('./release-info.json')
const { RuntimeManager, releaseRank, sha256 } = require('./runtime-manager')
const {
  resolveWorkFile,
  searchWorkDirectoryFiles,
} = require('./workFileSearch')
const {
  nextBrowserTabId,
  normalizeBrowserBounds,
  normalizeBrowserStartUrl,
  normalizeHttpUrl,
} = require('./embedded-browser')
const {
  codexFileChangePatch,
} = require('./codex-file-changes')
const {
  startBrowserControlServer: startBrowserControlEndpoint,
} = require('./browser-control-server')
const {
  resolveCapabilityResources,
} = require('./workcove-capability-resources')

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
const CLIENT_BROWSER_PARTITION = 'persist:workcove-browser-client'
const DEV_BROWSER_PARTITION = 'persist:workcove-browser-dev'
const UPDATE_INTERVAL_MS = 30 * 60 * 1000
const TOUCH_ID_STORE_VERSION = 1
const TOUCH_ID_STORE_FILE = 'vault-touch-id.json'
const BROWSER_TRUST_STORE_VERSION = 1
const BROWSER_TRUST_STORE_FILE = 'browser-certificate-trust.json'
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
const embeddedBrowserTabs = new Map()
let embeddedBrowserActiveTabId = ''
let embeddedBrowserBounds = null
let embeddedBrowserVisible = false
let embeddedBrowserSessionConfigured = false
let embeddedBrowserFullscreenRestore = null
let browserControlEndpoint = null
let browserControlToken = ''
let browserControlUrl = ''
let browserMcpScriptPath = ''
let browserControlDescriptorPath = ''
const embeddedBrowserDownloads = []
const pendingCertificateDecisions = new Map()
const pendingBrowserCredentialFills = new Map()

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

function browserTrustStorePath() {
  return path.join(
    app.getPath('userData'),
    BROWSER_TRUST_STORE_FILE,
  )
}

function readBrowserTrustStore() {
  try {
    const value = JSON.parse(
      fs.readFileSync(browserTrustStorePath(), 'utf8'),
    )
    if (
      value?.version !== BROWSER_TRUST_STORE_VERSION ||
      !value.certificates ||
      typeof value.certificates !== 'object'
    ) {
      return {
        version: BROWSER_TRUST_STORE_VERSION,
        certificates: {},
      }
    }
    return value
  } catch {
    return {
      version: BROWSER_TRUST_STORE_VERSION,
      certificates: {},
    }
  }
}

function writeBrowserTrustStore(store) {
  const target = browserTrustStorePath()
  const temporary = `${target}.tmp`
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.writeFileSync(temporary, JSON.stringify(store), {
    mode: 0o600,
  })
  fs.renameSync(temporary, target)
  fs.chmodSync(target, 0o600)
}

function normalizeCertificateFingerprint(fingerprint) {
  return String(fingerprint || '')
    .replace(/[^a-f0-9]/gi, '')
    .toLocaleLowerCase('en-US')
}

function trustedBrowserCertificate(origin, fingerprint) {
  const stored =
    readBrowserTrustStore().certificates[origin]?.fingerprint
  return (
    Boolean(stored) &&
    normalizeCertificateFingerprint(stored) ===
      normalizeCertificateFingerprint(fingerprint)
  )
}

function persistBrowserCertificate(origin, fingerprint, certificate) {
  const store = readBrowserTrustStore()
  store.certificates[origin] = {
    fingerprint: normalizeCertificateFingerprint(fingerprint),
    subjectName: certificate.subjectName || '',
    issuerName: certificate.issuerName || '',
    trustedAt: new Date().toISOString(),
  }
  writeBrowserTrustStore(store)
}

async function decideBrowserCertificate(
  url,
  error,
  certificate,
) {
  let origin
  try {
    origin = new URL(url).origin
  } catch {
    return false
  }
  const fingerprint = normalizeCertificateFingerprint(
    certificate?.fingerprint,
  )
  if (!fingerprint) return false
  if (trustedBrowserCertificate(origin, fingerprint)) return true

  const key = `${origin}\n${fingerprint}\n${error}`
  const pending = pendingCertificateDecisions.get(key)
  if (pending) return pending

  const decision = (async () => {
    if (!mainWindow || mainWindow.isDestroyed()) return false
    const result = await dialog.showMessageBox(mainWindow, {
      type: 'warning',
      title: 'HTTPS 证书不受信任',
      message: `是否继续访问 ${origin}？`,
      detail: [
        `证书错误：${error}`,
        `证书主题：${certificate?.subjectName || '未知'}`,
        `签发者：${certificate?.issuerName || '未知'}`,
        '',
        '继续访问会暂时忽略该证书错误。始终信任只对当前证书指纹生效，证书变化后会重新询问。',
      ].join('\n'),
      buttons: ['取消', '仅本次继续访问', '始终信任此证书'],
      defaultId: 0,
      cancelId: 0,
      noLink: true,
    })
    if (result.response === 2) {
      persistBrowserCertificate(origin, fingerprint, certificate)
      return true
    }
    return result.response === 1
  })()
  pendingCertificateDecisions.set(key, decision)
  try {
    return await decision
  } finally {
    pendingCertificateDecisions.delete(key)
  }
}

function browserPartition() {
  return app.isPackaged
    ? CLIENT_BROWSER_PARTITION
    : DEV_BROWSER_PARTITION
}

function uniqueDownloadPath(filename) {
  const directory = app.getPath('downloads')
  const parsed = path.parse(path.basename(filename || 'download'))
  const safeName = parsed.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
  const extension = parsed.ext.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '')
  let candidate = path.join(
    directory,
    `${safeName || 'download'}${extension}`,
  )
  let index = 1
  while (fs.existsSync(candidate)) {
    candidate = path.join(
      directory,
      `${safeName || 'download'} (${index})${extension}`,
    )
    index += 1
  }
  return candidate
}

function configureEmbeddedBrowserSession() {
  if (embeddedBrowserSessionConfigured) return
  const browserSession = session.fromPartition(browserPartition())
  browserSession.setPermissionCheckHandler(() => false)
  browserSession.setPermissionRequestHandler(
    (_webContents, _permission, callback) => callback(false),
  )
  browserSession.on('will-download', (_event, item) => {
    const record = {
      id: crypto.randomUUID(),
      filename: item.getFilename(),
      url: item.getURL(),
      state: 'progressing',
      receivedBytes: 0,
      totalBytes: item.getTotalBytes(),
      startedAt: new Date().toISOString(),
      completedAt: null,
      savePath: '',
      error: '',
    }
    embeddedBrowserDownloads.unshift(record)
    if (embeddedBrowserDownloads.length > 50) {
      embeddedBrowserDownloads.length = 50
    }
    try {
      item.setSavePath(uniqueDownloadPath(record.filename))
    } catch {
      // Electron falls back to the system download directory.
    }
    item.on('updated', (_downloadEvent, state) => {
      record.state = state
      record.receivedBytes = item.getReceivedBytes()
      record.totalBytes = item.getTotalBytes()
      record.savePath = item.getSavePath()
    })
    item.once('done', (_downloadEvent, state) => {
      record.state = state
      record.receivedBytes = item.getReceivedBytes()
      record.totalBytes = item.getTotalBytes()
      record.savePath = item.getSavePath()
      record.completedAt = new Date().toISOString()
      record.error =
        state === 'completed' ? '' : `下载状态：${state}`
      sendEmbeddedBrowserState()
    })
  })
  embeddedBrowserSessionConfigured = true
}

function activeEmbeddedBrowserTab() {
  const tab = embeddedBrowserTabs.get(embeddedBrowserActiveTabId)
  if (!tab || tab.view.webContents.isDestroyed()) return null
  return tab
}

function embeddedBrowserTabByContents(contents) {
  for (const tab of embeddedBrowserTabs.values()) {
    if (tab.view.webContents === contents) return tab
  }
  return null
}

function embeddedBrowserTabState(tab) {
  const empty = {
    id: tab?.id ?? '',
    url: '',
    title: '',
    loading: false,
    canGoBack: false,
    canGoForward: false,
    error: tab?.error ?? '',
  }
  if (!tab || tab.view.webContents.isDestroyed()) return empty
  const contents = tab.view.webContents
  return {
    id: tab.id,
    url: contents.getURL(),
    title: contents.getTitle(),
    loading: contents.isLoading(),
    canGoBack: contents.navigationHistory.canGoBack(),
    canGoForward: contents.navigationHistory.canGoForward(),
    error: tab.error,
  }
}

function embeddedBrowserState(
  active = embeddedBrowserTabs.size > 0,
) {
  const activeTab = activeEmbeddedBrowserTab()
  const current = embeddedBrowserTabState(activeTab)
  return {
    active,
    ...current,
    fullscreen: Boolean(mainWindow?.isFullScreen()),
    tabs: [...embeddedBrowserTabs.values()].map(
      (tab) => embeddedBrowserTabState(tab),
    ),
    activeTabId: activeTab?.id ?? '',
  }
}

function sendEmbeddedBrowserState(
  active = embeddedBrowserTabs.size > 0,
) {
  if (
    !mainWindow ||
    mainWindow.isDestroyed() ||
    mainWindow.webContents.isDestroyed()
  ) {
    return
  }
  mainWindow.webContents.send(
    'browser:state',
    embeddedBrowserState(active),
  )
}

function setEmbeddedBrowserTabError(tab, message) {
  tab.error = message
}

function isAllowedBrowserNavigation(event, fallbackUrl) {
  const target = fallbackUrl || event?.url || ''
  if (target === 'about:blank') return true
  try {
    normalizeHttpUrl(target)
    return true
  } catch {
    event?.preventDefault?.()
    return false
  }
}

function createEmbeddedBrowserTab(rawUrl) {
  if (!mainWindow || mainWindow.isDestroyed()) {
    throw new Error('客户端窗口尚未就绪')
  }
  const target = normalizeBrowserStartUrl(rawUrl)
  configureEmbeddedBrowserSession()

  const view = new WebContentsView({
    webPreferences: {
      partition: browserPartition(),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      spellcheck: true,
    },
  })
  const tab = {
    id: crypto.randomUUID(),
    view,
    error: '',
  }
  embeddedBrowserTabs.set(tab.id, tab)
  view.setVisible(false)
  view.setBorderRadius(8)
  mainWindow.contentView.addChildView(view)

  const contents = view.webContents
  contents.setWindowOpenHandler(({ url }) => {
    try {
      createEmbeddedBrowserTab(url)
    } catch {
      // Popups with unsupported protocols stay blocked.
    }
    return { action: 'deny' }
  })
  contents.on('will-navigate', (event, url) => {
    if (!isAllowedBrowserNavigation(event, url)) {
      setEmbeddedBrowserTabError(tab, '已阻止不安全的网址协议')
      sendEmbeddedBrowserState()
    }
  })
  contents.on('will-redirect', (event, url) => {
    if (!isAllowedBrowserNavigation(event, url)) {
      setEmbeddedBrowserTabError(tab, '已阻止不安全的网址跳转')
      sendEmbeddedBrowserState()
    }
  })
  contents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return
    if (input.key === 'F11') {
      event.preventDefault()
      toggleEmbeddedBrowserFullscreen()
      return
    }
    if (input.key === 'Escape') {
      event.preventDefault()
      mainWindow?.webContents.send('browser:command', 'close')
    }
  })
  contents.on('did-start-loading', () => {
    setEmbeddedBrowserTabError(tab, '')
    sendEmbeddedBrowserState()
  })
  contents.on('did-stop-loading', () => {
    sendEmbeddedBrowserState()
  })
  contents.on('did-navigate', () => {
    setEmbeddedBrowserTabError(tab, '')
    sendEmbeddedBrowserState()
  })
  contents.on('did-navigate-in-page', () => {
    sendEmbeddedBrowserState()
  })
  contents.on('page-title-updated', () => {
    sendEmbeddedBrowserState()
  })
  contents.on(
    'did-fail-load',
    (_event, errorCode, errorDescription, _url, isMainFrame) => {
      if (!isMainFrame || errorCode === -3) return
      setEmbeddedBrowserTabError(
        tab,
        errorDescription || `网页加载失败（${errorCode}）`,
      )
      sendEmbeddedBrowserState()
    },
  )
  contents.on('render-process-gone', () => {
    setEmbeddedBrowserTabError(tab, '网页渲染进程已退出')
    sendEmbeddedBrowserState()
  })

  activateEmbeddedBrowserTab(tab.id)
  void contents.loadURL(target).catch((error) => {
    if (!embeddedBrowserTabs.has(tab.id)) return
    setEmbeddedBrowserTabError(
      tab,
      error instanceof Error ? error.message : '网页加载失败',
    )
    sendEmbeddedBrowserState()
  })
  return tab.id
}

function activateEmbeddedBrowserTab(tabId) {
  const tab = embeddedBrowserTabs.get(tabId)
  if (!tab || tab.view.webContents.isDestroyed()) {
    throw new Error('浏览器标签页不存在')
  }
  const previous = activeEmbeddedBrowserTab()
  if (previous && previous.id !== tab.id) {
    previous.view.setVisible(false)
  }
  embeddedBrowserActiveTabId = tab.id
  if (embeddedBrowserBounds) {
    tab.view.setBounds(embeddedBrowserBounds)
  }
  tab.view.setVisible(
    embeddedBrowserVisible && Boolean(embeddedBrowserBounds),
  )
  sendEmbeddedBrowserState()
  return ''
}

function positionEmbeddedBrowser(rawBounds) {
  const tab = activeEmbeddedBrowserTab()
  if (!tab || !mainWindow) return
  const bounds = normalizeBrowserBounds(
    rawBounds,
    mainWindow.getContentBounds(),
  )
  embeddedBrowserBounds = bounds
  embeddedBrowserVisible = true
  tab.view.setBounds(bounds)
  tab.view.setVisible(true)
}

function setEmbeddedBrowserVisible(visible) {
  embeddedBrowserVisible = Boolean(visible)
  activeEmbeddedBrowserTab()?.view.setVisible(
    embeddedBrowserVisible && Boolean(embeddedBrowserBounds),
  )
}

function toggleEmbeddedBrowserFullscreen() {
  if (!mainWindow || mainWindow.isDestroyed()) return false
  const next = !mainWindow.isFullScreen()
  mainWindow.setFullScreen(next)
  sendEmbeddedBrowserState()
  return next
}

function showEmbeddedBrowserMenu() {
  const tab = activeEmbeddedBrowserTab()
  if (!mainWindow || !tab) return
  const contents = tab.view.webContents
  const menu = Menu.buildFromTemplate([
    {
      label: '在默认浏览器中打开',
      enabled: Boolean(contents.getURL()),
      click: () => {
        try {
          void shell.openExternal(
            normalizeHttpUrl(contents.getURL()),
          )
        } catch {
          // Ignore invalid current URLs.
        }
      },
    },
  ])
  menu.popup({ window: mainWindow })
}

function openEmbeddedBrowser(rawUrl) {
  if (embeddedBrowserFullscreenRestore === null) {
    embeddedBrowserFullscreenRestore = Boolean(
      mainWindow?.isFullScreen(),
    )
  }
  createEmbeddedBrowserTab(rawUrl)
  return ''
}

function navigateEmbeddedBrowser(rawUrl, tabId = '') {
  const tab = tabId
    ? embeddedBrowserTabs.get(tabId)
    : activeEmbeddedBrowserTab()
  if (!tab || tab.view.webContents.isDestroyed()) {
    throw new Error('内置浏览器尚未打开')
  }
  const target = normalizeHttpUrl(rawUrl)
  setEmbeddedBrowserTabError(tab, '')
  void tab.view.webContents
    .loadURL(target)
    .catch(() => undefined)
  return ''
}

function disposeEmbeddedBrowserTab(tab) {
  try {
    tab.view.setVisible(false)
    mainWindow?.contentView.removeChildView(tab.view)
    if (!tab.view.webContents.isDestroyed()) {
      tab.view.webContents.close()
    }
  } catch {
    // The view may already be gone during app shutdown.
  }
}

function closeEmbeddedBrowserTab(tabId) {
  const tab = embeddedBrowserTabs.get(tabId)
  if (!tab) {
    return { closedAll: embeddedBrowserTabs.size === 0 }
  }
  const ids = [...embeddedBrowserTabs.keys()]
  const closedActiveTab = embeddedBrowserActiveTabId === tab.id
  const nextTabId = closedActiveTab
    ? nextBrowserTabId(ids, tab.id)
    : embeddedBrowserActiveTabId

  embeddedBrowserTabs.delete(tab.id)
  disposeEmbeddedBrowserTab(tab)

  if (embeddedBrowserTabs.size === 0) {
    closeEmbeddedBrowser()
    return { closedAll: true }
  }
  if (closedActiveTab) {
    embeddedBrowserActiveTabId = ''
    if (nextTabId) {
      activateEmbeddedBrowserTab(nextTabId)
    }
  } else {
    sendEmbeddedBrowserState()
  }
  return { closedAll: false }
}

function closeEmbeddedBrowser() {
  for (const pending of pendingBrowserCredentialFills.values()) {
    clearTimeout(pending.timer)
    pending.resolve({
      cancelled: true,
      message: '内置浏览器已关闭，密码库填充已取消',
    })
  }
  pendingBrowserCredentialFills.clear()
  const tabs = [...embeddedBrowserTabs.values()]
  embeddedBrowserTabs.clear()
  embeddedBrowserActiveTabId = ''
  embeddedBrowserBounds = null
  embeddedBrowserVisible = false
  if (
    mainWindow &&
    !mainWindow.isDestroyed() &&
    embeddedBrowserFullscreenRestore !== null &&
    mainWindow.isFullScreen() !== embeddedBrowserFullscreenRestore
  ) {
    mainWindow.setFullScreen(embeddedBrowserFullscreenRestore)
  }
  embeddedBrowserFullscreenRestore = null
  tabs.forEach(disposeEmbeddedBrowserTab)
  sendEmbeddedBrowserState(false)
}

function browserControlError(message, statusCode = 409) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function embeddedBrowserContents(tabId = '') {
  const tab = tabId
    ? embeddedBrowserTabs.get(tabId)
    : activeEmbeddedBrowserTab()
  const contents = tab?.view.webContents
  if (!contents || contents.isDestroyed()) {
    throw browserControlError(
      '内置浏览器尚未打开，请先调用 browser_open',
    )
  }
  return contents
}

function delay(milliseconds) {
  return new Promise((resolve) =>
    setTimeout(resolve, Math.max(0, milliseconds)),
  )
}

function waitForValue(check, timeoutMs, message) {
  const startedAt = Date.now()
  return new Promise((resolve, reject) => {
    const poll = () => {
      const value = check()
      if (value) {
        resolve(value)
        return
      }
      if (Date.now() - startedAt >= timeoutMs) {
        reject(browserControlError(message, 408))
        return
      }
      setTimeout(poll, 100)
    }
    poll()
  })
}

function waitForBrowserNavigation(contents, timeoutMs = 8_000) {
  if (!contents.isLoading() && contents.getURL()) {
    return Promise.resolve(true)
  }
  return new Promise((resolve) => {
    let settled = false
    const finish = (ready) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      for (const eventName of events) {
        contents.removeListener(eventName, onEvent)
      }
      resolve(ready)
    }
    const onEvent = (_event, _errorCode, _errorDescription, _url, isMainFrame) => {
      finish(isMainFrame !== false)
    }
    const events = [
      'did-navigate',
      'did-navigate-in-page',
      'did-stop-loading',
      'did-fail-load',
    ]
    const timer = setTimeout(() => finish(false), timeoutMs)
    for (const eventName of events) {
      contents.on(eventName, onEvent)
    }
    if (!contents.isLoading() && contents.getURL()) finish(true)
  })
}

async function navigateEmbeddedBrowserWithTimeout(
  contents,
  target,
  timeoutMs = 8_000,
) {
  let navigationError = ''
  void contents.loadURL(target).catch((error) => {
    navigationError =
      error instanceof Error ? error.message : '网页加载失败'
  })
  await delay(60)
  const ready = await waitForBrowserNavigation(contents, timeoutMs)
  await delay(80)
  return {
    ready,
    error: navigationError,
    needsUserAction:
      !ready ||
      Boolean(navigationError) ||
      pendingCertificateDecisions.size > 0,
  }
}

function settleEmbeddedBrowser(contents, timeoutMs = 3_000) {
  if (!contents.isLoading()) return Promise.resolve(true)
  return waitForBrowserNavigation(contents, timeoutMs)
}

function executeEmbeddedBrowserScript(
  contents,
  script,
  timeoutMs = 6_000,
) {
  return Promise.race([
    contents.executeJavaScript(script, true),
    delay(timeoutMs).then(() => {
      throw browserControlError(
        '页面仍在加载或正在等待用户授权，请先完成内置浏览器中的操作',
        409,
      )
    }),
  ])
}

function requestEmbeddedBrowserForAutomation(url) {
  if (
    !mainWindow ||
    mainWindow.isDestroyed() ||
    mainWindow.webContents.isDestroyed()
  ) {
    throw browserControlError('客户端窗口尚未就绪')
  }
  mainWindow.webContents.send('browser:automation-open', {
    requestId: crypto.randomUUID(),
    url,
    title: 'Codex 内置浏览器',
  })
}

async function openEmbeddedBrowserForAutomation(rawUrl) {
  let target
  try {
    target = normalizeBrowserStartUrl(rawUrl)
  } catch (error) {
    throw browserControlError(
      error instanceof Error ? error.message : '网址无效',
      400,
    )
  }
  const hadBrowser = embeddedBrowserTabs.size > 0
  if (!hadBrowser) {
    requestEmbeddedBrowserForAutomation(target)
    await waitForValue(
      () =>
        activeEmbeddedBrowserTab(),
      12_000,
      '等待内置浏览器打开超时',
    )
  } else {
    createEmbeddedBrowserTab(target)
  }
  const contents = embeddedBrowserContents()
  setEmbeddedBrowserVisible(true)
  await delay(120)
  const navigation = contents.getURL() || contents.isLoading()
    ? {
        ready: await waitForBrowserNavigation(contents),
        error: '',
        needsUserAction:
          pendingCertificateDecisions.size > 0,
      }
    : await navigateEmbeddedBrowserWithTimeout(contents, target)
  return {
    ...embeddedBrowserState(true),
    navigationReady: navigation.ready,
    needsUserAction: navigation.needsUserAction,
    message: navigation.needsUserAction
      ? '内置浏览器已弹出，请用户完成证书确认或登录后继续。'
      : '',
    downloads: embeddedBrowserDownloads.slice(0, 10),
  }
}

async function navigateEmbeddedBrowserForAutomation(rawUrl) {
  let target
  try {
    target = normalizeHttpUrl(rawUrl)
  } catch (error) {
    throw browserControlError(
      error instanceof Error ? error.message : '网址无效',
      400,
    )
  }
  const contents = embeddedBrowserContents()
  const navigation = await navigateEmbeddedBrowserWithTimeout(
    contents,
    target,
  )
  return {
    ...embeddedBrowserState(true),
    navigationReady: navigation.ready,
    needsUserAction: navigation.needsUserAction,
    message: navigation.needsUserAction
      ? '页面仍在加载或需要用户确认，请在内置浏览器中完成操作。'
      : '',
  }
}

async function embeddedBrowserSnapshot() {
  const contents = embeddedBrowserContents()
  await settleEmbeddedBrowser(contents)
  return executeEmbeddedBrowserScript(
    contents,
    `(() => {
      document.querySelectorAll('[data-workcove-ref]').forEach((element) => {
        element.removeAttribute('data-workcove-ref')
      })
      const isVisible = (element) => {
        const style = window.getComputedStyle(element)
        const rect = element.getBoundingClientRect()
        return (
          style.display !== 'none' &&
          style.visibility !== 'hidden' &&
          Number(style.opacity || 1) > 0 &&
          rect.width > 0 &&
          rect.height > 0
        )
      }
      const describe = (element, index) => {
        const ref = 'wc-' + index
        element.setAttribute('data-workcove-ref', ref)
        const text = (
          element.innerText ||
          element.getAttribute('aria-label') ||
          element.getAttribute('title') ||
          element.getAttribute('placeholder') ||
          ''
        ).replace(/\\s+/g, ' ').trim().slice(0, 240)
        const tag = element.tagName.toLowerCase()
        const type =
          tag === 'input' ? String(element.type || 'text').toLowerCase() : ''
        const value =
          type === 'password' || typeof element.value !== 'string'
            ? ''
            : element.value.slice(0, 240)
        return {
          ref,
          tag,
          type,
          role: element.getAttribute('role') || '',
          text,
          value,
          name: element.getAttribute('name') || '',
          placeholder: element.getAttribute('placeholder') || '',
          ariaLabel: element.getAttribute('aria-label') || '',
          href:
            tag === 'a' && element.href
              ? String(element.href).slice(0, 500)
              : '',
          disabled: Boolean(element.disabled),
          checked:
            typeof element.checked === 'boolean'
              ? element.checked
              : undefined,
        }
      }
      const candidates = Array.from(
        document.querySelectorAll(
          'a[href],button,input,textarea,select,[role="button"],[role="link"],[role="tab"],[role="option"],[contenteditable="true"],[tabindex]:not([tabindex="-1"])'
        )
      )
        .filter(isVisible)
        .slice(0, 240)
      const elements = candidates.map(describe)
      return {
        url: location.href,
        title: document.title,
        readyState: document.readyState,
        viewport: {
          width: window.innerWidth,
          height: window.innerHeight,
        },
        visibleText: (document.body?.innerText || '')
          .replace(/\\n{3,}/g, '\\n\\n')
          .slice(0, 30000),
        elements,
      }
    })()`,
  )
}

function browserSelector(payload) {
  const ref = typeof payload?.ref === 'string' ? payload.ref.trim() : ''
  if (ref) {
    if (!/^wc-\\d+$/.test(ref)) {
      throw browserControlError('页面元素 ref 无效', 400)
    }
    return `[data-workcove-ref="${ref}"]`
  }
  const selector =
    typeof payload?.selector === 'string'
      ? payload.selector.trim()
      : ''
  if (!selector) {
    throw browserControlError('请提供 ref 或 selector', 400)
  }
  if (selector.length > 2000) {
    throw browserControlError('CSS selector 过长', 400)
  }
  return selector
}

async function clickEmbeddedBrowserElement(payload) {
  const contents = embeddedBrowserContents()
  const selector = browserSelector(payload)
  const clicked = await executeEmbeddedBrowserScript(
    contents,
    `(() => {
      const element = document.querySelector(${JSON.stringify(selector)})
      if (!element) throw new Error('未找到目标元素')
      if (element.disabled) throw new Error('目标元素已禁用')
      element.scrollIntoView({ block: 'center', inline: 'center' })
      element.focus?.()
      element.click()
      return {
        tag: element.tagName.toLowerCase(),
        text: (
          element.innerText ||
          element.getAttribute('aria-label') ||
          element.getAttribute('title') ||
          ''
        ).replace(/\\s+/g, ' ').trim().slice(0, 240),
      }
    })()`,
  )
  await delay(150)
  await settleEmbeddedBrowser(contents)
  return {
    clicked,
    ...embeddedBrowserState(true),
  }
}

async function typeIntoEmbeddedBrowser(payload) {
  const contents = embeddedBrowserContents()
  const selector = browserSelector(payload)
  const text = typeof payload?.text === 'string' ? payload.text : ''
  const focused = await executeEmbeddedBrowserScript(
    contents,
    `(() => {
      const element = document.querySelector(${JSON.stringify(selector)})
      if (!element) throw new Error('未找到输入元素')
      if (element.disabled || element.readOnly) {
        throw new Error('输入元素不可编辑')
      }
      element.scrollIntoView({ block: 'center', inline: 'center' })
      element.focus()
      if (element.isContentEditable) {
        element.textContent = ''
        element.dispatchEvent(
          new InputEvent('input', { bubbles: true, inputType: 'deleteContentBackward' })
        )
      } else {
        const prototype =
          element instanceof HTMLInputElement
            ? HTMLInputElement.prototype
            : element instanceof HTMLTextAreaElement
              ? HTMLTextAreaElement.prototype
              : null
        const setter = prototype
          ? Object.getOwnPropertyDescriptor(prototype, 'value')?.set
          : null
        if (setter) {
          setter.call(element, '')
          element.dispatchEvent(
            new InputEvent('input', { bubbles: true, inputType: 'deleteContentBackward' })
          )
        } else if (typeof element.select === 'function') {
          element.select()
        }
      }
      return {
        tag: element.tagName.toLowerCase(),
        type: String(element.type || '').toLowerCase(),
      }
    })()`,
  )
  if (text) contents.insertText(text)
  if (payload?.submit) {
    contents.sendInputEvent({ type: 'keyDown', keyCode: 'Enter' })
    contents.sendInputEvent({ type: 'keyUp', keyCode: 'Enter' })
    await delay(150)
  }
  await settleEmbeddedBrowser(contents)
  return {
    focused,
    ...embeddedBrowserState(true),
  }
}

async function fillEmbeddedBrowserCredentials(payload) {
  const contents = embeddedBrowserContents()
  await settleEmbeddedBrowser(contents)
  const result = await fillCredentialsIntoContents(contents, payload)
  await delay(100)
  return {
    ...result,
    ...embeddedBrowserState(true),
  }
}

function requestEmbeddedBrowserCredentialFill(payload) {
  const contents = embeddedBrowserContents()
  let current
  try {
    current = new URL(contents.getURL())
  } catch {
    throw browserControlError('当前页面地址无效，无法使用密码库')
  }
  if (current.protocol !== 'http:' && current.protocol !== 'https:') {
    throw browserControlError('当前页面不是可填充的登录网页')
  }
  if (
    !mainWindow ||
    mainWindow.isDestroyed() ||
    mainWindow.webContents.isDestroyed()
  ) {
    throw browserControlError('客户端窗口尚未就绪')
  }

  const requestId = crypto.randomUUID()
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pendingBrowserCredentialFills.delete(requestId)
      reject(
        browserControlError(
          '等待用户从密码库选择登录信息超时',
          408,
        ),
      )
    }, 120_000)
    pendingBrowserCredentialFills.set(requestId, {
      resolve,
      reject,
      timer,
    })
  })
  mainWindow.webContents.send('browser:credential-fill-request', {
    requestId,
    origin: current.origin,
    url: current.toString(),
    title: contents.getTitle(),
    credentialName:
      typeof payload?.credentialName === 'string'
        ? payload.credentialName.trim()
        : '',
  })
  return promise
}

function completeEmbeddedBrowserCredentialFill(payload) {
  const requestId =
    typeof payload?.requestId === 'string' ? payload.requestId : ''
  const pending = pendingBrowserCredentialFills.get(requestId)
  if (!pending) return ''
  pendingBrowserCredentialFills.delete(requestId)
  clearTimeout(pending.timer)
  if (payload?.cancelled) {
    pending.resolve({
      cancelled: true,
      message:
        typeof payload.message === 'string'
          ? payload.message
          : '用户取消了密码库填充',
    })
  } else if (payload?.error) {
    pending.reject(
      browserControlError(String(payload.error), 409),
    )
  } else {
    pending.resolve(payload?.result || { filledPassword: false })
  }
  return ''
}

async function pressEmbeddedBrowserKey(payload) {
  const contents = embeddedBrowserContents()
  const key = typeof payload?.key === 'string' ? payload.key.trim() : ''
  if (!key || key.length > 32) {
    throw browserControlError('按键无效', 400)
  }
  const allowedModifiers = new Set([
    'shift',
    'control',
    'alt',
    'meta',
  ])
  const modifiers = Array.isArray(payload?.modifiers)
    ? payload.modifiers.filter((item) => allowedModifiers.has(item))
    : []
  contents.sendInputEvent({
    type: 'keyDown',
    keyCode: key,
    modifiers,
  })
  contents.sendInputEvent({
    type: 'keyUp',
    keyCode: key,
    modifiers,
  })
  await delay(120)
  await settleEmbeddedBrowser(contents)
  return embeddedBrowserState(true)
}

async function waitInEmbeddedBrowser(payload) {
  const contents = embeddedBrowserContents()
  const timeoutMs = Math.min(
    30_000,
    Math.max(0, Number(payload?.timeoutMs) || 10_000),
  )
  const selector =
    typeof payload?.selector === 'string'
      ? payload.selector.trim()
      : ''
  if (selector) {
    const startedAt = Date.now()
    let found = false
    while (Date.now() - startedAt < timeoutMs) {
      found = await executeEmbeddedBrowserScript(
        contents,
        `Boolean(document.querySelector(${JSON.stringify(selector)}))`,
      )
      if (found) break
      await delay(150)
    }
    if (!found) {
      throw browserControlError('等待页面元素超时', 408)
    }
  } else {
    const milliseconds = Math.min(
      30_000,
      Math.max(0, Number(payload?.milliseconds) || 500),
    )
    await delay(milliseconds)
  }
  await settleEmbeddedBrowser(contents)
  return embeddedBrowserState(true)
}

async function evaluateInEmbeddedBrowser(payload) {
  const script =
    typeof payload?.script === 'string' ? payload.script.trim() : ''
  if (!script) throw browserControlError('JavaScript 不能为空', 400)
  if (script.length > 100_000) {
    throw browserControlError('JavaScript 内容过长', 400)
  }
  const contents = embeddedBrowserContents()
  const result = await executeEmbeddedBrowserScript(
    contents,
    script,
  )
  if (result === undefined) return { result: null }
  try {
    return { result: JSON.parse(JSON.stringify(result)) }
  } catch {
    return { result: String(result) }
  }
}

async function captureEmbeddedBrowserScreenshot(payload) {
  const contents = embeddedBrowserContents()
  const image = await contents.capturePage()
  const buffer = image.toPNG()
  const size = image.getSize()
  let savePath = ''
  if (payload?.path !== undefined) {
    if (
      typeof payload.path !== 'string' ||
      !path.isAbsolute(payload.path) ||
      path.extname(payload.path).toLowerCase() !== '.png'
    ) {
      throw browserControlError('截图路径必须是绝对 PNG 路径', 400)
    }
    fs.mkdirSync(path.dirname(payload.path), { recursive: true })
    fs.writeFileSync(payload.path, buffer)
    savePath = payload.path
  }
  return {
    mimeType: 'image/png',
    width: size.width,
    height: size.height,
    bytes: buffer.length,
    path: savePath,
    data: buffer.toString('base64'),
  }
}

function workCoveApiUrl(pathname) {
  const currentUrl =
    mainWindow && !mainWindow.isDestroyed()
      ? mainWindow.webContents.getURL()
      : ''
  let origin = app.isPackaged ? CLIENT_ORIGIN : ''
  if (!origin && currentUrl) {
    try {
      const parsed = new URL(currentUrl)
      if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
        origin = parsed.origin
      }
    } catch {
      // Fall back to the default development renderer origin below.
    }
  }
  if (!origin) origin = new URL(DEV_WEB_URL).origin
  return new URL(
    `/api/${String(pathname || '').replace(/^\/+/, '')}`,
    origin,
  )
}

async function workCoveApiRequest(pathname) {
  const url = workCoveApiUrl(pathname)
  const appSession = session.fromPartition(
    app.isPackaged ? CLIENT_PARTITION : DEV_PARTITION,
  )
  const cookies = await appSession.cookies.get({ url: url.toString() })
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
      'Cache-Control': 'no-store',
      Cookie: cookies
        .map((cookie) => `${cookie.name}=${cookie.value}`)
        .join('; '),
    },
    signal: AbortSignal.timeout(20_000),
  })
  const text = await response.text()
  let payload = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      throw browserControlError(
        `WorkCove 接口返回了非 JSON 内容（HTTP ${response.status}）`,
        response.status || 502,
      )
    }
  }
  if (!response.ok) {
    throw browserControlError(
      payload?.message ||
        `WorkCove 接口请求失败（HTTP ${response.status}）`,
      response.status,
    )
  }
  return payload
}

async function currentGitBranch(repoPath) {
  if (!path.isAbsolute(repoPath)) {
    throw browserControlError('app_audit 仓库路径必须是绝对路径', 400)
  }
  const [rootResult, branchResult] = await Promise.all([
    execFileAsync(
      'git',
      ['-C', repoPath, 'rev-parse', '--show-toplevel'],
      { timeout: 10_000 },
    ),
    execFileAsync(
      'git',
      ['-C', repoPath, 'branch', '--show-current'],
      { timeout: 10_000 },
    ),
  ])
  const root = path.resolve(String(rootResult.stdout || '').trim())
  if (root !== path.resolve(repoPath)) {
    throw browserControlError(
      '指定路径不是 app_audit Git 仓库根目录',
      400,
    )
  }
  const branch = String(branchResult.stdout || '').trim()
  if (!branch) {
    throw browserControlError(
      'app_audit 当前处于 detached HEAD，无法匹配能力级',
      409,
    )
  }
  return branch
}

async function updateCapabilitySetFromWorkCove(payload) {
  const repoPath =
    typeof payload?.repoPath === 'string' && payload.repoPath.trim()
      ? path.resolve(payload.repoPath.trim())
      : DEFAULT_REPOSITORY_PATH
  const branch = await currentGitBranch(repoPath)
  const resources = await resolveCapabilityResources({
    apiRequest: workCoveApiRequest,
    websiteName:
      typeof payload?.websiteName === 'string'
        ? payload.websiteName
        : '',
    credentialName:
      typeof payload?.credentialName === 'string'
        ? payload.credentialName
        : '',
  })
  const result = await updateCapabilitySet({
    siteUrl: resources.website.url,
    username: resources.credential.username,
    password: resources.password,
    branch,
    productLine:
      typeof payload?.productLine === 'string'
        ? payload.productLine
        : '',
    repoPath,
    backupDirectory: clientDataPath('capability-set-updates'),
    confirm: payload?.confirm === true,
  })
  return {
    ...result,
    website: {
      id: resources.website.id,
      name: resources.website.name,
    },
    credential: {
      id: resources.credential.id,
      name: resources.credential.name,
      username: resources.credential.username,
    },
  }
}

async function handleBrowserControlAction(action, payload) {
  if (action === 'status') {
    return {
      ...embeddedBrowserState(embeddedBrowserTabs.size > 0),
      downloads: embeddedBrowserDownloads.slice(0, 10),
    }
  }
  if (action === 'open') {
    return openEmbeddedBrowserForAutomation(payload?.url)
  }
  if (action === 'navigate') {
    return navigateEmbeddedBrowserForAutomation(payload?.url)
  }
  if (action === 'snapshot') {
    return embeddedBrowserSnapshot()
  }
  if (action === 'click') {
    return clickEmbeddedBrowserElement(payload)
  }
  if (action === 'type') {
    return typeIntoEmbeddedBrowser(payload)
  }
  if (action === 'press') {
    return pressEmbeddedBrowserKey(payload)
  }
  if (action === 'fillCredentials') {
    return requestEmbeddedBrowserCredentialFill(payload)
  }
  if (action === 'wait') {
    return waitInEmbeddedBrowser(payload)
  }
  if (action === 'evaluate') {
    return evaluateInEmbeddedBrowser(payload)
  }
  if (action === 'screenshot') {
    return captureEmbeddedBrowserScreenshot(payload)
  }
  if (action === 'downloads') {
    return {
      downloads: embeddedBrowserDownloads.slice(0, 50),
    }
  }
  if (action === 'capabilityUpdate') {
    return updateCapabilitySetFromWorkCove(payload)
  }
  throw browserControlError('不支持的浏览器操作', 404)
}

async function startEmbeddedBrowserControl() {
  const controlDirectory = clientDataPath('browser-control')
  fs.mkdirSync(controlDirectory, { recursive: true })
  browserMcpScriptPath = path.join(
    controlDirectory,
    'browser-mcp.js',
  )
  fs.copyFileSync(
    path.join(__dirname, 'browser-mcp.js'),
    browserMcpScriptPath,
  )
  browserControlToken = crypto.randomBytes(32).toString('hex')
  browserControlEndpoint = await startBrowserControlEndpoint({
    token: browserControlToken,
    dispatch: handleBrowserControlAction,
  })
  browserControlUrl = browserControlEndpoint.url
  browserControlDescriptorPath = clientDataPath('browser-control.json')
  fs.writeFileSync(
    browserControlDescriptorPath,
    `${JSON.stringify(
      {
        version: 1,
        url: browserControlUrl,
        token: browserControlToken,
        command: process.execPath,
        script: browserMcpScriptPath,
      },
      null,
      2,
    )}\n`,
    { encoding: 'utf8', mode: 0o600 },
  )
  fs.chmodSync(browserControlDescriptorPath, 0o600)
}

function assertMainRenderer(event) {
  if (
    !mainWindow ||
    mainWindow.isDestroyed() ||
    event.sender !== mainWindow.webContents
  ) {
    throw new Error('无权控制内置浏览器')
  }
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
    closeEmbeddedBrowser()
    if (updateTimer) {
      clearInterval(updateTimer)
      updateTimer = null
    }
    mainWindow = null
  })
  mainWindow.on('enter-full-screen', () => {
    sendEmbeddedBrowserState()
  })
  mainWindow.on('leave-full-screen', () => {
    sendEmbeddedBrowserState()
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

async function revertCodexFileChanges(payload) {
  const cwd =
    payload && typeof payload.cwd === 'string'
      ? payload.cwd.trim()
      : ''
  const changes = Array.isArray(payload?.changes)
    ? payload.changes
    : []
  if (!cwd || changes.length === 0) return '没有可撤销的文件变更'
  let root
  try {
    const result = await execFileAsync(
      'git',
      ['-C', cwd, 'rev-parse', '--show-toplevel'],
      { encoding: 'utf8', timeout: 10_000 },
    )
    root = result.stdout.trim()
  } catch {
    return '当前工作目录不是 Git 仓库，无法安全撤销'
  }
  if (!root) return '无法识别 Git 仓库目录'

  let patch
  try {
    patch = codexFileChangePatch(root, changes)
  } catch (error) {
    return error instanceof Error ? error.message : '文件变更数据无效'
  }
  if (!patch) return '没有可撤销的文件变更'

  const temporaryDirectory = fs.mkdtempSync(
    path.join(app.getPath('temp'), 'workcove-undo-'),
  )
  const patchFile = path.join(temporaryDirectory, 'changes.patch')
  try {
    fs.writeFileSync(patchFile, `${patch}\n`, {
      encoding: 'utf8',
      mode: 0o600,
    })
    await execFileAsync(
      'git',
      [
        '-C',
        root,
        'apply',
        '--reverse',
        '--check',
        '--whitespace=nowarn',
        patchFile,
      ],
      { encoding: 'utf8', timeout: 30_000 },
    )
    await execFileAsync(
      'git',
      [
        '-C',
        root,
        'apply',
        '--reverse',
        '--whitespace=nowarn',
        patchFile,
      ],
      { encoding: 'utf8', timeout: 30_000 },
    )
    return ''
  } catch (error) {
    const detail =
      typeof error?.stderr === 'string' ? error.stderr.trim() : ''
    return detail || '文件已发生变化，无法安全撤销'
  } finally {
    fs.rmSync(temporaryDirectory, { recursive: true, force: true })
  }
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
    ...(browserControlUrl && browserMcpScriptPath
      ? {
          WORKCOVE_BROWSER_CONTROL_URL: browserControlUrl,
          WORKCOVE_BROWSER_CONTROL_TOKEN: browserControlToken,
          WORKCOVE_BROWSER_MCP_COMMAND: process.execPath,
          WORKCOVE_BROWSER_MCP_SCRIPT: browserMcpScriptPath,
          WORKCOVE_BROWSER_CONTROL_FILE: browserControlDescriptorPath,
        }
      : {}),
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
  try {
    await startEmbeddedBrowserControl()
  } catch (error) {
    console.error(
      '[browser-control] 初始化失败',
      error instanceof Error ? error.stack || error.message : error,
    )
  }
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
  app.on(
    'certificate-error',
    (
      event,
      webContents,
      url,
      error,
      certificate,
      callback,
    ) => {
      if (!embeddedBrowserTabByContents(webContents)) {
        return
      }
      event.preventDefault()
      void decideBrowserCertificate(
        url,
        error,
        certificate,
      ).then(callback, () => callback(false))
    },
  )
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
  ipcMain.handle(
    'development:search-work-files',
    (_event, roots, query) => searchWorkDirectoryFiles(roots, query),
  )
  ipcMain.handle(
    'development:open-work-file',
    async (_event, targetPath, roots) => {
      try {
        const filePath = await resolveWorkFile(targetPath, roots)
        return shell.openPath(filePath)
      } catch (error) {
        return error instanceof Error ? error.message : '无法打开工作目录文件'
      }
    },
  )
  ipcMain.handle('development:open-with-idea', async (_event, targetPath) =>
    openProjectWithIdea(targetPath),
  )
  ipcMain.handle(
    'development:revert-codex-file-changes',
    (event, payload) => {
      assertMainRenderer(event)
      return revertCodexFileChanges(payload)
    },
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
  ipcMain.handle('browser:open', (event, targetUrl) => {
    assertMainRenderer(event)
    return openEmbeddedBrowser(targetUrl)
  })
  ipcMain.handle('browser:new-tab', (event, targetUrl = 'about:blank') => {
    assertMainRenderer(event)
    createEmbeddedBrowserTab(targetUrl)
    return ''
  })
  ipcMain.handle('browser:activate-tab', (event, tabId) => {
    assertMainRenderer(event)
    return activateEmbeddedBrowserTab(tabId)
  })
  ipcMain.handle('browser:close-tab', (event, tabId) => {
    assertMainRenderer(event)
    return closeEmbeddedBrowserTab(tabId)
  })
  ipcMain.handle('browser:navigate', (event, targetUrl, tabId = '') => {
    assertMainRenderer(event)
    return navigateEmbeddedBrowser(targetUrl, tabId)
  })
  ipcMain.handle('browser:back', (event, tabId = '') => {
    assertMainRenderer(event)
    const contents = embeddedBrowserContents(tabId)
    if (contents?.navigationHistory.canGoBack()) {
      contents.navigationHistory.goBack()
    }
  })
  ipcMain.handle('browser:forward', (event, tabId = '') => {
    assertMainRenderer(event)
    const contents = embeddedBrowserContents(tabId)
    if (contents?.navigationHistory.canGoForward()) {
      contents.navigationHistory.goForward()
    }
  })
  ipcMain.handle('browser:reload', (event, tabId = '') => {
    assertMainRenderer(event)
    embeddedBrowserContents(tabId).reload()
  })
  ipcMain.handle('browser:stop', (event, tabId = '') => {
    assertMainRenderer(event)
    embeddedBrowserContents(tabId).stop()
  })
  ipcMain.handle('browser:fill-credentials', (event, payload) => {
    assertMainRenderer(event)
    return fillEmbeddedBrowserCredentials(payload)
  })
  ipcMain.handle(
    'browser:credential-fill-complete',
    (event, payload) => {
      assertMainRenderer(event)
      return completeEmbeddedBrowserCredentialFill(payload)
    },
  )
  ipcMain.handle('browser:set-visible', (event, visible) => {
    assertMainRenderer(event)
    setEmbeddedBrowserVisible(visible)
  })
  ipcMain.handle('browser:toggle-fullscreen', (event) => {
    assertMainRenderer(event)
    return toggleEmbeddedBrowserFullscreen()
  })
  ipcMain.handle('browser:show-menu', (event) => {
    assertMainRenderer(event)
    showEmbeddedBrowserMenu()
  })
  ipcMain.handle('browser:open-external', async (event, targetUrl) => {
    assertMainRenderer(event)
    await shell.openExternal(normalizeHttpUrl(targetUrl))
  })
  ipcMain.handle('browser:close', (event) => {
    assertMainRenderer(event)
    closeEmbeddedBrowser()
  })
  ipcMain.on('browser:set-bounds', (event, bounds) => {
    try {
      assertMainRenderer(event)
      positionEmbeddedBrowser(bounds)
    } catch {
      // Layout can briefly be zero-sized while the dialog is opening.
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

app.on('before-quit', () => {
  closeEmbeddedBrowser()
  browserControlEndpoint?.server.close()
  browserControlEndpoint = null
  if (browserControlDescriptorPath) {
    fs.rmSync(browserControlDescriptorPath, { force: true })
  }
  stopBackend()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
