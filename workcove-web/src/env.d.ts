/// <reference types="vite/client" />

declare module 'virtual:workcove-release-info' {
  export const version: string
  export const releaseCode: string
}

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
  applicationStatuses: (targetPaths: string[]) => Promise<Array<{
    path: string
    running: boolean
  }>>
  ccSwitchStatus: () => Promise<{
    available: boolean
    appPath: string | null
    dataPath: string | null
  }>
  ccSwitchUsage: (range: string) => Promise<{
    available: boolean
    source: 'cc-switch'
    range: string
    reason: string
    costUsd: number | null
    requestCount: number
    successCount: number
    inputTokens: number
    outputTokens: number
    cacheReadTokens: number
    cacheCreationTokens: number
    totalTokens: number
    cacheHitRate: number | null
    updatedAt: string
  }>
  chooseDirectory: () => Promise<string | null>
  openWithIdea: (targetPath: string) => Promise<string>
  openPath: (targetPath: string) => Promise<string>
  forceQuitApplication: (targetPath: string) => Promise<string>
  openExternal: (targetUrl: string) => Promise<string>
  touchIdAvailable: () => Promise<boolean>
  touchIdStatus: (accountKey: string) => Promise<boolean>
  storeTouchIdSecret: (
    accountKey: string,
    secret: string,
  ) => Promise<void>
  unlockTouchIdSecret: (accountKey: string) => Promise<string>
  clearTouchIdSecret: (accountKey: string) => Promise<void>
}

interface Window {
  workCoveDesktop?: WorkCoveDesktopBridge
}
