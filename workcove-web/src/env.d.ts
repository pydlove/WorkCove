/// <reference types="vite/client" />

declare module 'virtual:workcove-release-info' {
  export const version: string
  export const releaseCode: string
}

interface WorkFileSearchResult {
  name: string
  path: string
  rootName: string
  relativePath: string
}

interface EmbeddedBrowserBounds {
  x: number
  y: number
  width: number
  height: number
}

interface EmbeddedBrowserTabState {
  id: string
  url: string
  title: string
  loading: boolean
  canGoBack: boolean
  canGoForward: boolean
  error: string
}

interface EmbeddedBrowserState {
  active: boolean
  url: string
  title: string
  loading: boolean
  canGoBack: boolean
  canGoForward: boolean
  fullscreen: boolean
  error: string
  tabs: EmbeddedBrowserTabState[]
  activeTabId: string
}

interface EmbeddedBrowserAutomationOpenRequest {
  requestId: string
  url: string
  title: string
}

interface EmbeddedBrowserCredentialFillRequest {
  requestId: string
  origin: string
  url: string
  title: string
  credentialName: string
}

interface EmbeddedBrowserCredentialFillResult {
  filledUsername: boolean
  filledPassword: boolean
  url: string
  active: boolean
  title: string
  loading: boolean
  canGoBack: boolean
  canGoForward: boolean
  fullscreen: boolean
  error: string
}

interface CodexFileChangeRevertInput {
  cwd: string
  changes: Array<{
    path: string
    kind: 'add' | 'update' | 'delete' | 'unknown'
    diff: string
  }>
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
  searchWorkFiles: (
    roots: string[],
    query: string,
  ) => Promise<WorkFileSearchResult[]>
  openWorkFile: (
    targetPath: string,
    roots: string[],
  ) => Promise<string>
  openWithIdea: (targetPath: string) => Promise<string>
  revertCodexFileChanges: (
    payload: CodexFileChangeRevertInput,
  ) => Promise<string>
  openPath: (targetPath: string) => Promise<string>
  forceQuitApplication: (targetPath: string) => Promise<string>
  openExternal: (targetUrl: string) => Promise<string>
  openEmbeddedBrowser: (targetUrl: string) => Promise<string>
  newEmbeddedBrowserTab: (targetUrl?: string) => Promise<string>
  activateEmbeddedBrowserTab: (tabId: string) => Promise<string>
  closeEmbeddedBrowserTab: (
    tabId: string,
  ) => Promise<{ closedAll: boolean }>
  navigateEmbeddedBrowser: (
    targetUrl: string,
    tabId?: string,
  ) => Promise<string>
  setEmbeddedBrowserBounds: (bounds: EmbeddedBrowserBounds) => void
  backEmbeddedBrowser: (tabId?: string) => Promise<void>
  forwardEmbeddedBrowser: (tabId?: string) => Promise<void>
  reloadEmbeddedBrowser: (tabId?: string) => Promise<void>
  stopEmbeddedBrowser: (tabId?: string) => Promise<void>
  fillEmbeddedBrowserCredentials: (payload: {
    origin: string
    username: string
    password: string
  }) => Promise<EmbeddedBrowserCredentialFillResult>
  completeEmbeddedBrowserCredentialFill: (payload: {
    requestId: string
    result?: EmbeddedBrowserCredentialFillResult
    cancelled?: boolean
    error?: string
    message?: string
  }) => Promise<string>
  setEmbeddedBrowserVisible: (visible: boolean) => Promise<void>
  toggleEmbeddedBrowserFullscreen: () => Promise<boolean>
  showEmbeddedBrowserMenu: () => Promise<void>
  openEmbeddedBrowserExternal: (targetUrl: string) => Promise<void>
  closeEmbeddedBrowser: () => Promise<void>
  onEmbeddedBrowserState: (
    callback: (state: EmbeddedBrowserState) => void,
  ) => void
  offEmbeddedBrowserState: () => void
  onEmbeddedBrowserCommand: (
    callback: (command: string) => void,
  ) => void
  offEmbeddedBrowserCommand: () => void
  onEmbeddedBrowserAutomationOpen: (
    callback: (request: EmbeddedBrowserAutomationOpenRequest) => void,
  ) => void
  offEmbeddedBrowserAutomationOpen: () => void
  onEmbeddedBrowserCredentialFillRequest: (
    callback: (
      request: EmbeddedBrowserCredentialFillRequest,
    ) => void,
  ) => void
  offEmbeddedBrowserCredentialFillRequest: () => void
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
