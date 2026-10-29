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

export interface LocalDirectorySummary {
  path: string
  fileCount: number
  directoryCount: number
  totalBytes: number
  largeFileCount: number
  largeFileThresholdBytes: number
  lastModifiedAt: string | null
  inaccessibleCount: number
  truncated: boolean
  scanDurationMs: number
}
