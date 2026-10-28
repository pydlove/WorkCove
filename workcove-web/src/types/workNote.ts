export type WorkNoteFilter =
  | 'active'
  | 'important'
  | 'resolved'
  | 'archived'

export interface WorkNote {
  id: string
  workspaceId: string
  title: string
  titleAuto: boolean
  content: string
  important: boolean
  resolved: boolean
  pinned: boolean
  archived: boolean
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface WorkNoteInput {
  id?: string
  workspaceId: string
  title: string
  content: string
  important: boolean
  resolved: boolean
  pinned: boolean
  archived: boolean
  tags: string[]
}
