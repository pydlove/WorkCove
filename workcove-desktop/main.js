const { app, BrowserWindow, dialog, ipcMain, shell } = require('electron')
const fs = require('node:fs')
const path = require('node:path')

const APP_TITLE = 'WorkCove 栖作'
const DEV_WEB_URL = process.env.WORKCOVE_WEB_URL ?? 'http://127.0.0.1:32581'

let mainWindow = null

function createWindow() {
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
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  mainWindow.once('ready-to-show', () => mainWindow?.show())
  mainWindow.on('closed', () => {
    mainWindow = null
  })
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })

  if (!app.isPackaged) {
    void mainWindow.loadURL(DEV_WEB_URL)
  } else {
    void mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'))
  }
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

app.whenReady().then(() => {
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
  ipcMain.handle('shortcut:open-path', async (_event, targetPath) => {
    if (typeof targetPath !== 'string' || !targetPath.trim()) {
      return '应用路径无效'
    }
    if (!fs.existsSync(targetPath)) return '应用不存在或已被移动'
    return shell.openPath(targetPath)
  })
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

  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
