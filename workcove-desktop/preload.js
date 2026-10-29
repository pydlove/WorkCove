const { contextBridge, ipcRenderer } = require('electron')

let embeddedBrowserStateListener = null
let embeddedBrowserCommandListener = null
let embeddedBrowserAutomationOpenListener = null
let embeddedBrowserCredentialFillListener = null

contextBridge.exposeInMainWorld('workCoveDesktop', {
  platform: process.platform,
  isDesktop: true,
  close: () => ipcRenderer.invoke('window:close'),
  minimize: () => ipcRenderer.invoke('window:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('window:toggle-maximize'),
  chooseApplication: () => ipcRenderer.invoke('shortcut:choose-application'),
  applicationStatuses: (targetPaths) =>
    ipcRenderer.invoke('shortcut:application-statuses', targetPaths),
  ccSwitchStatus: () => ipcRenderer.invoke('local:cc-switch-status'),
  ccSwitchUsage: (range) =>
    ipcRenderer.invoke('local:cc-switch-usage', range),
  chooseDirectory: () =>
    ipcRenderer.invoke('development:choose-directory'),
  searchWorkFiles: (roots, query) =>
    ipcRenderer.invoke('development:search-work-files', roots, query),
  openWorkFile: (targetPath, roots) =>
    ipcRenderer.invoke('development:open-work-file', targetPath, roots),
  openWithIdea: (targetPath) =>
    ipcRenderer.invoke('development:open-with-idea', targetPath),
  revertCodexFileChanges: (payload) =>
    ipcRenderer.invoke(
      'development:revert-codex-file-changes',
      payload,
    ),
  openPath: (targetPath) =>
    ipcRenderer.invoke('shortcut:open-path', targetPath),
  forceQuitApplication: (targetPath) =>
    ipcRenderer.invoke('shortcut:force-quit-application', targetPath),
  openExternal: (targetUrl) =>
    ipcRenderer.invoke('shortcut:open-external', targetUrl),
  openEmbeddedBrowser: (targetUrl) =>
    ipcRenderer.invoke('browser:open', targetUrl),
  newEmbeddedBrowserTab: (targetUrl = 'about:blank') =>
    ipcRenderer.invoke('browser:new-tab', targetUrl),
  activateEmbeddedBrowserTab: (tabId) =>
    ipcRenderer.invoke('browser:activate-tab', tabId),
  closeEmbeddedBrowserTab: (tabId) =>
    ipcRenderer.invoke('browser:close-tab', tabId),
  navigateEmbeddedBrowser: (targetUrl, tabId) =>
    ipcRenderer.invoke('browser:navigate', targetUrl, tabId),
  setEmbeddedBrowserBounds: (bounds) =>
    ipcRenderer.send('browser:set-bounds', bounds),
  backEmbeddedBrowser: (tabId) =>
    ipcRenderer.invoke('browser:back', tabId),
  forwardEmbeddedBrowser: (tabId) =>
    ipcRenderer.invoke('browser:forward', tabId),
  reloadEmbeddedBrowser: (tabId) =>
    ipcRenderer.invoke('browser:reload', tabId),
  stopEmbeddedBrowser: (tabId) =>
    ipcRenderer.invoke('browser:stop', tabId),
  fillEmbeddedBrowserCredentials: (payload) =>
    ipcRenderer.invoke('browser:fill-credentials', payload),
  completeEmbeddedBrowserCredentialFill: (payload) =>
    ipcRenderer.invoke('browser:credential-fill-complete', payload),
  setEmbeddedBrowserVisible: (visible) =>
    ipcRenderer.invoke('browser:set-visible', visible),
  toggleEmbeddedBrowserFullscreen: () =>
    ipcRenderer.invoke('browser:toggle-fullscreen'),
  showEmbeddedBrowserMenu: () =>
    ipcRenderer.invoke('browser:show-menu'),
  openEmbeddedBrowserExternal: (targetUrl) =>
    ipcRenderer.invoke('browser:open-external', targetUrl),
  closeEmbeddedBrowser: () =>
    ipcRenderer.invoke('browser:close'),
  onEmbeddedBrowserState: (callback) => {
    if (embeddedBrowserStateListener) {
      ipcRenderer.removeListener(
        'browser:state',
        embeddedBrowserStateListener,
      )
    }
    embeddedBrowserStateListener = (_event, state) => callback(state)
    ipcRenderer.on('browser:state', embeddedBrowserStateListener)
  },
  offEmbeddedBrowserState: () => {
    if (!embeddedBrowserStateListener) return
    ipcRenderer.removeListener(
      'browser:state',
      embeddedBrowserStateListener,
    )
    embeddedBrowserStateListener = null
  },
  onEmbeddedBrowserCommand: (callback) => {
    if (embeddedBrowserCommandListener) {
      ipcRenderer.removeListener(
        'browser:command',
        embeddedBrowserCommandListener,
      )
    }
    embeddedBrowserCommandListener = (_event, command) =>
      callback(command)
    ipcRenderer.on(
      'browser:command',
      embeddedBrowserCommandListener,
    )
  },
  offEmbeddedBrowserCommand: () => {
    if (!embeddedBrowserCommandListener) return
    ipcRenderer.removeListener(
      'browser:command',
      embeddedBrowserCommandListener,
    )
    embeddedBrowserCommandListener = null
  },
  onEmbeddedBrowserAutomationOpen: (callback) => {
    if (embeddedBrowserAutomationOpenListener) {
      ipcRenderer.removeListener(
        'browser:automation-open',
        embeddedBrowserAutomationOpenListener,
      )
    }
    embeddedBrowserAutomationOpenListener = (_event, request) =>
      callback(request)
    ipcRenderer.on(
      'browser:automation-open',
      embeddedBrowserAutomationOpenListener,
    )
  },
  offEmbeddedBrowserAutomationOpen: () => {
    if (!embeddedBrowserAutomationOpenListener) return
    ipcRenderer.removeListener(
      'browser:automation-open',
      embeddedBrowserAutomationOpenListener,
    )
    embeddedBrowserAutomationOpenListener = null
  },
  onEmbeddedBrowserCredentialFillRequest: (callback) => {
    if (embeddedBrowserCredentialFillListener) {
      ipcRenderer.removeListener(
        'browser:credential-fill-request',
        embeddedBrowserCredentialFillListener,
      )
    }
    embeddedBrowserCredentialFillListener = (_event, request) =>
      callback(request)
    ipcRenderer.on(
      'browser:credential-fill-request',
      embeddedBrowserCredentialFillListener,
    )
  },
  offEmbeddedBrowserCredentialFillRequest: () => {
    if (!embeddedBrowserCredentialFillListener) return
    ipcRenderer.removeListener(
      'browser:credential-fill-request',
      embeddedBrowserCredentialFillListener,
    )
    embeddedBrowserCredentialFillListener = null
  },
  touchIdAvailable: () =>
    ipcRenderer.invoke('vault:touch-id-available'),
  touchIdStatus: (accountKey) =>
    ipcRenderer.invoke('vault:touch-id-status', accountKey),
  storeTouchIdSecret: (accountKey, secret) =>
    ipcRenderer.invoke('vault:touch-id-store', accountKey, secret),
  unlockTouchIdSecret: (accountKey) =>
    ipcRenderer.invoke('vault:touch-id-unlock', accountKey),
  clearTouchIdSecret: (accountKey) =>
    ipcRenderer.invoke('vault:touch-id-clear', accountKey),
})
