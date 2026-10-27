export type TaskPriority = 'high' | 'medium' | 'low'

export interface WorkTask {
  id: number
  title: string
  project: string
  time: string
  done: boolean
  priority: TaskPriority
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
