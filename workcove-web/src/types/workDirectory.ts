export const DEFAULT_WORK_DIRECTORY_CATEGORY = '开发项目'

export interface WorkDirectory {
  id: string
  workspaceId: string
  name: string
  path: string
  category: string
  description: string
  createdAt: string
  updatedAt: string
}

export interface WorkDirectoryInput {
  id?: string
  workspaceId?: string
  name: string
  path: string
  category: string
  description: string
}
