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

export interface ZentaoSyncResult {
  projects: number
  bugs: number
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
