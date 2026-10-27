/// <reference types="vite/client" />

interface WorkCoveDesktopBridge {
  platform: string
  isDesktop: true
  close: () => Promise<void>
  minimize: () => Promise<void>
  toggleMaximize: () => Promise<void>
}

interface Window {
  workCoveDesktop?: WorkCoveDesktopBridge
}
