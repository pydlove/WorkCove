export const DEFAULT_WORKSPACE_ID = 'default'
export const DEFAULT_WORKSPACE_NAME = '默认工作空间'
export const DEFAULT_WORKSPACE_ICON = 'layers'
export const DEFAULT_WORKSPACE_ICON_TONE = 'blue'
export const MAX_WORKSPACE_ICON_DATA_URL_LENGTH = 500_000

export type WorkspaceIcon =
  | 'layers'
  | 'briefcase'
  | 'code'
  | 'calendar'
  | 'rocket'
  | 'book'
  | 'home'
  | 'star'

export type WorkspaceIconTone =
  | 'blue'
  | 'green'
  | 'orange'
  | 'red'
  | 'purple'
  | 'graphite'

export const WORKSPACE_ICON_KEYS: readonly WorkspaceIcon[] = [
  'layers',
  'briefcase',
  'code',
  'calendar',
  'rocket',
  'book',
  'home',
  'star',
]

export const WORKSPACE_ICON_TONES: readonly WorkspaceIconTone[] = [
  'blue',
  'green',
  'orange',
  'red',
  'purple',
  'graphite',
]

export interface Workspace {
  id: string
  name: string
  icon: WorkspaceIcon
  iconTone: WorkspaceIconTone
  iconImage: string
  createdAt: string
  updatedAt: string
}

export interface WorkspaceState {
  workspaces: Workspace[]
  activeWorkspaceId: string
}
