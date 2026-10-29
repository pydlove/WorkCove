export type ZentaoResolution =
  | 'fixed'
  | 'notrepro'
  | 'bydesign'
  | 'duplicate'
  | 'external'
  | 'postponed'
  | 'willnotfix'
  | 'tostory'

export interface ZentaoConnection {
  configured: boolean
  baseUrl: string
  username: string
  password: string
  projectIds: string[]
  lastSyncedAt: string | null
}

export interface ZentaoConnectionInput {
  workspaceId: string
  baseUrl: string
  username: string
  password: string
  projectIds: string[]
}

export interface ZentaoProject {
  id: string
  name: string
  status: string
}

export interface ZentaoProjectInput {
  workspaceId: string
  baseUrl: string
  username: string
  password: string
}

export interface ZentaoBug {
  id: number
  remoteId: string
  projectId: string
  projectName: string
  productId: string
  productName: string
  title: string
  severity: string
  priority: string
  status: string
  confirmed: boolean
  steps: string
  openedBy: string
  openedDate: string
  assignedTo: string
  active: boolean
  localDeleted: boolean
  localResolved: boolean
  localStatus: string
  resolution: string
  solutionText: string | null
  reasonText: string | null
  pushedAt: string | null
  lastSyncedAt: string
}

export interface ZentaoBuild {
  id: number
  remoteId: string
  projectId: string
  projectName: string
  productId: string
  productName: string
  executionId: string
  name: string
  status: string
  builder: string
  buildDate: string
  description: string
  scmRevision: string
  lastSyncedAt: string
}

export interface ZentaoExecution {
  id: string
  projectId: string
  projectName: string
  productId: string
  name: string
  status: string
}

export interface ZentaoProduct {
  id: string
  name: string
}

export interface ZentaoUser {
  account: string
  name: string
}

export interface ZentaoSystem {
  id: string
  productId: string
  name: string
}

export interface ZentaoTestReport {
  id: string
  title: string
}

export interface ZentaoBuildOptions {
  executions: ZentaoExecution[]
  products: ZentaoProduct[]
  users: ZentaoUser[]
}

export interface ZentaoTestTaskOptions {
  users: ZentaoUser[]
  reports: ZentaoTestReport[]
}

export interface ZentaoBuildCreateInput {
  workspaceId: string
  projectId: string
  executionId: string
  productId: string
  systemId: string
  name: string
  builder: string
  date: string
  downloadUrl: string
}

export interface ZentaoTestTaskCreateInput {
  workspaceId: string
  types: string[]
  owner: string
  members: string[]
  status: string
  begin: string
  end: string
  testReport: string
  name: string
}

export interface ZentaoCreatedBuild {
  remoteId: string
  name: string
}

export interface ZentaoCreatedTestTask {
  remoteId: string
  name: string
}

export interface ZentaoSyncResult {
  projects: number
  bugs: number
  builds: number
  syncedAt: string
}

export interface ZentaoResolveInput {
  workspaceId: string
  resolution: ZentaoResolution
  solution: string
  reason: string
}

export interface ZentaoLocalResolveInput {
  workspaceId: string
  solution: string
  reason: string
}
