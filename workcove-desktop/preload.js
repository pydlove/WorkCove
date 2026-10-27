const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('workCoveDesktop', {
  platform: process.platform,
  isDesktop: true,
  close: () => ipcRenderer.invoke('window:close'),
  minimize: () => ipcRenderer.invoke('window:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('window:toggle-maximize'),
  chooseApplication: () => ipcRenderer.invoke('shortcut:choose-application'),
  openPath: (targetPath) =>
    ipcRenderer.invoke('shortcut:open-path', targetPath),
  openExternal: (targetUrl) =>
    ipcRenderer.invoke('shortcut:open-external', targetUrl),
})
