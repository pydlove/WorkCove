export type ServerTool = 'TERMIUS' | 'FILEZILLA'

export interface ManagedServer {
  id: number
  workspaceId: string
  name: string
  host: string
  port: number
  username: string
  hostKeyFingerprint: string | null
  createdAt: string
  updatedAt: string
}

export interface ManagedServerInput {
  id?: number
  workspaceId?: string
  name: string
  host: string
  port: number
  username: string
  password: string
}

export interface OpenManagedServerResult {
  tool: ServerTool
  passwordCopied: boolean
}
