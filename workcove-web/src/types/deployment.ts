export type DeploymentRunStatus = 'SUCCESS' | 'FAILED' | 'TIMEOUT'

export interface DeploymentConfig {
  id: string
  workspaceId: string
  name: string
  serverId: number | null
  serverName: string
  remoteDirectory: string
  scriptFilename: string
  scriptContent: string
  createdAt: string
  updatedAt: string
}

export interface DeploymentRun {
  id: string
  workspaceId: string
  deploymentId: string
  deploymentName: string
  serverId: number | null
  serverName: string
  status: DeploymentRunStatus
  exitCode: number | null
  summary: string
  output: string
  startedAt: string
  finishedAt: string
}

export interface DeploymentState {
  deployments: DeploymentConfig[]
  runs: DeploymentRun[]
}

export interface DeploymentInput {
  id?: string
  workspaceId?: string
  name: string
  serverId: number
  serverName: string
  remoteDirectory: string
  scriptFilename: string
  scriptContent: string
}

export interface DeploymentExecutionResponse {
  status: DeploymentRunStatus
  exitCode: number | null
  summary: string
  output: string
  startedAt: string
  finishedAt: string
}
