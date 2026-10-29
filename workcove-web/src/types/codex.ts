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
  | 'fileRead'
  | 'fileChange'
  | 'tool'
  | 'toolLoad'
  | 'plan'
  | 'webSearch'
  | 'imageView'
  | 'contextCompaction'
  | 'reasoning'
  | 'other'

export interface CodexPlanStep {
  step: string
  status: 'pending' | 'inProgress' | 'completed'
}

export interface CodexFileChange {
  path: string
  kind: 'add' | 'update' | 'delete' | 'unknown'
  additions: number
  deletions: number
  diff: string
  reverted?: boolean
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
  fileChanges?: CodexFileChange[]
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

export interface CodexUserMessageTimelineEntry {
  id: string
  kind: 'userMessage'
  text: string
  createdAt: string
}

export interface CodexActivityTimelineEntry {
  id: string
  kind: 'activity'
  activityId: string
}

export type CodexTimelineEntry =
  | CodexMessageTimelineEntry
  | CodexUserMessageTimelineEntry
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
  gitBranchMode?: 'current' | 'create'
  developmentBranch?: string
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
  zentaoTestEnvironmentName?: string
  zentaoTestEnvironmentUrl?: string
  zentaoTestResultSavedAt?: string | null
  zentaoTestRepairTaskId?: string
  zentaoTestRepairTriggeredAt?: string | null
  userTaskId?: number
  codeReviewId?: string
  codeReviewMode?: 'initial' | 'repair'
  attachments?: CodexTaskAttachment[]
  activities: CodexActivity[]
  timeline?: CodexTimelineEntry[]
  plan: CodexPlanStep[]
}

export interface CodexTestRepairTaskPayload {
  workspaceId: string
  codexSessionId: string
  codexTaskId: string
  title: string
  description: string
  project: string
  projectPath: string
  bugId: number
  remoteId: string
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
  unread?: boolean
  createdAt: string
  updatedAt: string
  activeTaskId: string
  tasks: CodexTask[]
}
