export type QuickCommandScope = 'global' | 'server'

export interface TerminalQuickCommand {
  id: string
  workspaceId: string
  scope: QuickCommandScope
  serverId: number | null
  name: string
  command: string
  createdAt: string
  updatedAt: string
}

export interface TerminalQuickCommandInput {
  id?: string
  workspaceId: string
  scope: QuickCommandScope
  serverId: number | null
  name: string
  command: string
}
