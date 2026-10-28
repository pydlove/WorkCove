export type CodexSessionStatus = 'idle' | 'running' | 'error'
export type CodexAccessMode = 'workspace' | 'full'
export type CodexTaskStatus =
  | 'queued'
  | 'running'
  | 'completed'
  | 'failed'
  | 'interrupted'

export type CodexActivityType =
  | 'command'
  | 'fileChange'
  | 'tool'
  | 'plan'
  | 'webSearch'
  | 'reasoning'
  | 'other'

export interface CodexPlanStep {
  step: string
  status: 'pending' | 'inProgress' | 'completed'
}

export interface CodexActivity {
  id: string
  type: CodexActivityType
  title: string
  detail: string
  status: 'running' | 'completed' | 'failed' | 'declined'
  startedAt: string
  completedAt: string | null
  output: string
}

export interface CodexMessageTimelineEntry {
  id: string
  kind: 'message'
  itemId: string
  text: string
  streaming: boolean
  startedAt: string
  completedAt: string | null
}

export interface CodexActivityTimelineEntry {
  id: string
  kind: 'activity'
  activityId: string
}

export type CodexTimelineEntry =
  | CodexMessageTimelineEntry
  | CodexActivityTimelineEntry

export interface CodexTaskAttachment {
  id: string
  name: string
  contentType: string
  size: number
  kind: 'image' | 'file'
}

export interface CodexTask {
  id: string
  turnId: string
  prompt: string
  displayPrompt?: string
  response: string
  responseItemId: string
  responseStreaming: boolean
  status: CodexTaskStatus
  startedAt: string
  lastActivityAt?: string
  completedAt: string | null
  durationMs: number | null
  itemCount: number
  commandCount: number
  fileChangeCount: number
  toolCount: number
  inputTokens?: number
  outputTokens?: number
  cachedInputTokens?: number
  totalTokens: number
  error: string
  model?: string
  reasoningEffort?: string
  accessMode?: CodexAccessMode
  zentaoBugId?: number
  zentaoBugIds?: number[]
  zentaoLocalSavedAt?: string | null
  zentaoPushedAt?: string | null
  zentaoPushedBugIds?: number[]
  zentaoPushError?: string
  zentaoTestBugId?: number
  zentaoTestRemoteId?: string
  zentaoTestWorkspaceId?: string
  zentaoTestRunId?: string
  zentaoTestEvidenceDirectory?: string
  zentaoTestResultSavedAt?: string | null
  attachments?: CodexTaskAttachment[]
  activities: CodexActivity[]
  timeline?: CodexTimelineEntry[]
  plan: CodexPlanStep[]
}

export interface CodexSession {
  id: string
  workspaceId: string
  threadId: string
  title: string
  cwd: string
  pinned?: boolean
  accessMode?: CodexAccessMode
  model?: string
  reasoningEffort?: string
  status: CodexSessionStatus
  createdAt: string
  updatedAt: string
  activeTaskId: string
  tasks: CodexTask[]
}
