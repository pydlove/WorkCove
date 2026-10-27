/// <reference types="vite/client" />

interface WorkCoveDesktopBridge {
  platform: string
  isDesktop: true
  close: () => Promise<void>
  minimize: () => Promise<void>
  toggleMaximize: () => Promise<void>
  chooseApplication: () => Promise<{
    path: string
    name: string
    iconDataUrl: string | null
  } | null>
  openPath: (targetPath: string) => Promise<string>
  openExternal: (targetUrl: string) => Promise<string>
}

interface Window {
  workCoveDesktop?: WorkCoveDesktopBridge
}
