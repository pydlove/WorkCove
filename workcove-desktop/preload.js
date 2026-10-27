const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('workCoveDesktop', {
  platform: process.platform,
  isDesktop: true,
  close: () => ipcRenderer.invoke('window:close'),
  minimize: () => ipcRenderer.invoke('window:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('window:toggle-maximize'),
})
