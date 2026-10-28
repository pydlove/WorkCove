const { contextBridge, ipcRenderer } = require('electron')

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
  openWithIdea: (targetPath) =>
    ipcRenderer.invoke('development:open-with-idea', targetPath),
  openPath: (targetPath) =>
    ipcRenderer.invoke('shortcut:open-path', targetPath),
  forceQuitApplication: (targetPath) =>
    ipcRenderer.invoke('shortcut:force-quit-application', targetPath),
  openExternal: (targetUrl) =>
    ipcRenderer.invoke('shortcut:open-external', targetUrl),
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
