import {
  apiUrl,
  get,
  postFormJsonWithHeaders,
  postJsonWithHeaders,
} from '@/api/http'

const START_TIMEOUT_MS = 120_000

export interface CodexStatus {
  available: boolean
  connected: boolean
  executable: string
}

export interface CodexModelInfo {
  id: string
  model: string
  displayName: string
  description: string
  hidden: boolean
  defaultReasoningEffort: string
  supportedReasoningEfforts: Array<{
    reasoningEffort: string
    description: string
  }>
  inputModalities?: string[]
  isDefault: boolean
}

export interface CodexModelListResponse {
  data: CodexModelInfo[]
  nextCursor?: string | null
}

export interface CodexAttachmentInfo {
  id: string
  name: string
  contentType: string
  size: number
  kind: 'image' | 'file'
}

interface CodexThreadResponse {
  thread: {
    id: string
    preview?: string
    name?: string | null
    cwd?: string
    createdAt?: number
    updatedAt?: number
  }
  model?: string
}

interface CodexTurnResponse {
  turn: {
    id: string
    status?: string
    startedAt?: number | null
  }
}

export interface CodexGatewayEvent {
  type: 'notification' | 'serverRequest' | 'bridge'
  method: string
  requestId?: string | number
  params?: Record<string, unknown>
  emittedAtMs?: number
}

export async function getCodexStatus(): Promise<CodexStatus> {
  return get<CodexStatus>('/codex/status')
}

export async function listCodexModels() {
  return get<CodexModelListResponse>('/codex/models')
}

export async function uploadCodexAttachment(
  file: File,
  workspaceId: string,
) {
  const form = new FormData()
  form.append('file', file)
  return postFormJsonWithHeaders<CodexAttachmentInfo>(
    `/codex/attachments?workspaceId=${encodeURIComponent(workspaceId)}`,
    form,
    { 'X-WorkCove-Action': 'upload-codex-attachment' },
    120_000,
  )
}

export async function chooseCodexDirectory() {
  const response = await postJsonWithHeaders<{ path: string | null }>(
    '/codex/directory-picker',
    {},
    { 'X-WorkCove-Action': 'choose-directory' },
    120_000,
  )
  return response.path
}

export type CodexSandboxMode =
  | 'workspace-write'
  | 'danger-full-access'

export async function startCodexThread(options: {
  cwd?: string
  sandbox?: CodexSandboxMode
}) {
  return postJsonWithHeaders<CodexThreadResponse>(
    '/codex/threads',
    options,
    {},
    START_TIMEOUT_MS,
  )
}

export async function resumeCodexThread(
  threadId: string,
  options: {
    cwd?: string
    sandbox?: CodexSandboxMode
  } = {},
) {
  return postJsonWithHeaders<CodexThreadResponse>(
    '/codex/threads/resume',
    {
      threadId,
      cwd: options.cwd,
      sandbox: options.sandbox,
    },
    {},
    START_TIMEOUT_MS,
  )
}

export async function startCodexTurn(input: {
  workspaceId: string
  threadId: string
  text?: string
  cwd?: string
  attachmentIds?: string[]
  model?: string
  effort?: string
  writableRoots?: string[]
}) {
  return postJsonWithHeaders<CodexTurnResponse>(
    '/codex/turns',
    input,
    {},
    START_TIMEOUT_MS,
  )
}

export async function interruptCodexTurn(
  threadId: string,
  turnId: string,
) {
  return postJsonWithHeaders<{
    alreadyStopped?: boolean
    forced?: boolean
  }>(
    '/codex/turns/interrupt',
    { threadId, turnId },
    {},
    30_000,
  )
}

export async function steerCodexTurn(input: {
  workspaceId: string
  threadId: string
  expectedTurnId: string
  text: string
  attachmentIds?: string[]
}) {
  return postJsonWithHeaders<{ turnId: string }>(
    '/codex/turns/steer',
    input,
    {},
    60_000,
  )
}

export async function setCodexThreadName(
  threadId: string,
  name: string,
) {
  return postJsonWithHeaders<Record<string, never>>(
    '/codex/threads/name',
    { threadId, name },
    {},
    30_000,
  )
}

export function subscribeCodexEvents(
  callback: (event: CodexGatewayEvent) => void,
) {
  const source = new EventSource(apiUrl('/codex/events'), {
    withCredentials: true,
  })
  source.onmessage = (message) => {
    try {
      callback(JSON.parse(message.data) as CodexGatewayEvent)
    } catch {
      // Ignore malformed transport frames; the backend remains connected.
    }
  }
  return () => source.close()
}
