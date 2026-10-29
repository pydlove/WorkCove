export type TaskPriority = 'high' | 'medium' | 'low'
export type TaskExecutionStatus =
  | 'todo'
  | 'running'
  | 'completed'
  | 'failed'
  | 'interrupted'

export interface WorkTaskAttachment {
  id: string
  name: string
  contentType: string
  size: number
  kind: 'image' | 'file'
}

export interface WorkTask {
  id: number
  workspaceId?: string
  title: string
  project: string
  projectPath?: string
  time: string
  done: boolean
  priority: TaskPriority
  description?: string
  attachments?: WorkTaskAttachment[]
  executionStatus?: TaskExecutionStatus
  codexSessionId?: string
  codexTaskId?: string
  executionResult?: string
  executionError?: string
  lastExecutedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface WorkTaskInput {
  title: string
  description: string
  attachments?: WorkTaskAttachment[]
  project: string
  projectPath?: string
  priority: TaskPriority
}

export interface WorkTaskProjectOption {
  id: string
  name: string
  path: string
}

export interface WorkTaskExecutionUpdate {
  taskId: number
  codexSessionId: string
  codexTaskId: string
  status: Extract<
    TaskExecutionStatus,
    'running' | 'completed' | 'failed' | 'interrupted'
  >
  result?: string
  error?: string
}

export interface WorkNote {
  id: number
  title: string
  excerpt: string
  updatedAt: string
  tone: 'sage' | 'sand' | 'rose'
}

export interface WorkProject {
  id: number
  name: string
  description: string
  progress: number
  color: 'green' | 'coral' | 'amber'
}

export interface SavedBookmark {
  id: number
  title: string
  url: string
  category: string
}
