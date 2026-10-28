export type ZentaoTestStatus = 'passed' | 'failed' | 'blocked'

export interface ZentaoTestRun {
  runId: string
  evidenceDirectory: string
}

export interface ZentaoTestEvidence {
  filename: string
  contentType: string
  size: number
}

export interface ZentaoTestResult {
  id: string
  bugId: number
  remoteId: string
  workspaceId: string
  taskId: string
  sessionId: string
  runId: string
  status: ZentaoTestStatus
  environment: string
  problemDescription: string
  proof: string
  screenshots: string[]
  createdAt: string
}
