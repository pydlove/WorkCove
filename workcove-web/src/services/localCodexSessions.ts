import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type {
  CodexActivity,
  CodexSession,
  CodexTask,
  CodexTimelineEntry,
} from '@/types/codex'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.codex-sessions.v1'
const SESSION_INDEX_KEY = `${STORAGE_KEY}.index`
const SESSION_KEY_PREFIX = `${STORAGE_KEY}.session.`
const PREFERRED_DIRECTORY_STORAGE_KEY =
  'workcove.codex-preferred-directories.v1'
const MAX_SESSION_PAYLOAD_BYTES = 8 * 1024 * 1024
const RECENT_DETAILED_TASKS = 8

export type CodexPreferredDirectoriesByWorkspace = Record<string, string>

export function loadLocalCodexSessions(email: string) {
  const sharded = loadShardedSessions(email)
  if (sharded !== null) return sharded

  const legacy = normalizeSessions(
    readUserScopedValue<unknown>(STORAGE_KEY, email),
  )
  if (legacy.length > 0) {
    // Migrate the old single payload on first load so an existing
    // oversized value is replaced by bounded per-session rows.
    saveLocalCodexSessions(email, legacy)
  }
  return legacy
}

export function saveLocalCodexSessions(
  email: string,
  sessions: CodexSession[],
) {
  const normalized = normalizeSessions(sessions)
  const previousIndex = readUserScopedValue<unknown>(
    SESSION_INDEX_KEY,
    email,
  )
  const nextIds = normalized.map((session) => session.id)

  for (const session of normalized) {
    const key = codexSessionStateKey(session.id)
    const payload = compactSessionForStorage(session)
    if (
      JSON.stringify(readUserScopedValue<CodexSession>(key, email)) !==
      JSON.stringify(payload)
    ) {
      writeUserScopedValue(key, email, payload)
    }
  }

  if (Array.isArray(previousIndex)) {
    const nextIdSet = new Set(nextIds)
    for (const id of previousIndex) {
      if (typeof id !== 'string' || nextIdSet.has(id)) continue
      writeUserScopedValue(codexSessionStateKey(id), email, null)
    }
  }

  if (
    JSON.stringify(previousIndex) !== JSON.stringify(nextIds)
  ) {
    writeUserScopedValue(SESSION_INDEX_KEY, email, nextIds)
  }

  if (
    JSON.stringify(readUserScopedValue<unknown>(STORAGE_KEY, email)) !==
    'null'
  ) {
    writeUserScopedValue(STORAGE_KEY, email, null)
  }
}

export function loadPreferredCodexDirectory(
  email: string,
  workspaceId: string,
) {
  const directories =
    readUserScopedValue<CodexPreferredDirectoriesByWorkspace>(
      PREFERRED_DIRECTORY_STORAGE_KEY,
      email,
    )
  const directory = directories?.[workspaceId]
  return typeof directory === 'string' ? directory.trim() : ''
}

export function savePreferredCodexDirectory(
  email: string,
  workspaceId: string,
  directoryPath: string,
) {
  const directory = directoryPath.trim()
  if (!workspaceId) return

  const directories =
    readUserScopedValue<CodexPreferredDirectoriesByWorkspace>(
      PREFERRED_DIRECTORY_STORAGE_KEY,
      email,
    ) ?? {}
  if (!directory) {
    delete directories[workspaceId]
    writeUserScopedValue(
      PREFERRED_DIRECTORY_STORAGE_KEY,
      email,
      directories,
    )
    return
  }
  writeUserScopedValue(
    PREFERRED_DIRECTORY_STORAGE_KEY,
    email,
    {
      ...directories,
      [workspaceId]: directory,
    },
  )
}

export function newCodexSessionId() {
  return window.crypto.randomUUID()
}

function loadShardedSessions(email: string) {
  const index = readUserScopedValue<unknown>(
    SESSION_INDEX_KEY,
    email,
  )
  if (!Array.isArray(index)) return null

  return index
    .filter((id): id is string => typeof id === 'string')
    .map((id) =>
      normalizeSession(
        readUserScopedValue<unknown>(codexSessionStateKey(id), email),
      ),
    )
    .filter((session): session is CodexSession => Boolean(session))
}

function normalizeSessions(value: unknown): CodexSession[] {
  if (!Array.isArray(value)) return []
  return value
    .map((session) => normalizeSession(session))
    .filter((session): session is CodexSession => Boolean(session))
}

function normalizeSession(value: unknown): CodexSession | null {
  if (!value || typeof value !== 'object') return null
  const session = value as Partial<CodexSession>
  if (
    typeof session.id !== 'string' ||
    typeof session.title !== 'string' ||
    !Array.isArray(session.tasks)
  ) {
    return null
  }
  const now = new Date().toISOString()
  const createdAt =
    typeof session.createdAt === 'string'
      ? session.createdAt
      : now
  return {
    ...session,
    id: session.id,
    title: session.title,
    threadId:
      typeof session.threadId === 'string' ? session.threadId : '',
    cwd: typeof session.cwd === 'string' ? session.cwd : '',
    status:
      session.status === 'running' || session.status === 'error'
        ? session.status
        : 'idle',
    createdAt,
    updatedAt:
      typeof session.updatedAt === 'string'
        ? session.updatedAt
        : createdAt,
    activeTaskId:
      typeof session.activeTaskId === 'string'
        ? session.activeTaskId
        : '',
    workspaceId:
      typeof session.workspaceId === 'string' &&
      session.workspaceId
        ? session.workspaceId
        : DEFAULT_WORKSPACE_ID,
    unread: Boolean(session.unread),
    tasks: session.tasks
      .map((task) => normalizeTask(task))
      .filter((task): task is CodexTask => Boolean(task)),
  }
}

function normalizeTask(value: unknown): CodexTask | null {
  if (!value || typeof value !== 'object') return null
  const task = value as Partial<CodexTask>
  if (typeof task.id !== 'string') return null
  const now = new Date().toISOString()
  const startedAt =
    typeof task.startedAt === 'string' ? task.startedAt : now
  return {
    ...task,
    id: task.id,
    prompt: typeof task.prompt === 'string' ? task.prompt : '',
    response: typeof task.response === 'string' ? task.response : '',
    responseItemId:
      typeof task.responseItemId === 'string'
        ? task.responseItemId
        : '',
    responseStreaming: Boolean(task.responseStreaming),
    error: typeof task.error === 'string' ? task.error : '',
    turnId: typeof task.turnId === 'string' ? task.turnId : '',
    status: task.status ?? 'interrupted',
    startedAt,
    lastActivityAt:
      typeof task.lastActivityAt === 'string'
        ? task.lastActivityAt
        : startedAt,
    completedAt:
      typeof task.completedAt === 'string' ? task.completedAt : null,
    durationMs:
      typeof task.durationMs === 'number' ? task.durationMs : null,
    itemCount: numberValue(task.itemCount),
    commandCount: numberValue(task.commandCount),
    fileChangeCount: numberValue(task.fileChangeCount),
    toolCount: numberValue(task.toolCount),
    inputTokens: numberValue(task.inputTokens),
    outputTokens: numberValue(task.outputTokens),
    cachedInputTokens: numberValue(task.cachedInputTokens),
    totalTokens: numberValue(task.totalTokens),
    activities: Array.isArray(task.activities)
      ? task.activities.filter(isActivity)
      : [],
    timeline: Array.isArray(task.timeline)
      ? task.timeline.filter(isTimelineEntry)
      : [],
    plan: Array.isArray(task.plan) ? task.plan : [],
    attachments: Array.isArray(task.attachments)
      ? task.attachments
      : [],
  }
}

function isActivity(value: unknown): value is CodexActivity {
  return Boolean(
    value &&
      typeof value === 'object' &&
      typeof (value as CodexActivity).id === 'string',
  )
}

function isTimelineEntry(value: unknown): value is CodexTimelineEntry {
  return Boolean(
    value &&
      typeof value === 'object' &&
      typeof (value as CodexTimelineEntry).id === 'string' &&
      ((value as CodexTimelineEntry).kind === 'message' ||
        (value as CodexTimelineEntry).kind === 'userMessage' ||
        (value as CodexTimelineEntry).kind === 'activity'),
  )
}

function codexSessionStateKey(id: string) {
  const safeId = id
    .toLocaleLowerCase('en-US')
    .replace(/[^a-z0-9_-]/g, '_')
    .slice(0, 90)
  return `${SESSION_KEY_PREFIX}${safeId || 'session'}`
}

function numberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

function compactSessionForStorage(session: CodexSession): CodexSession {
  const tasks = session.tasks.map((task, index) => {
    const detailed =
      task.status === 'running' ||
      task.status === 'queued' ||
      index >= session.tasks.length - RECENT_DETAILED_TASKS
    return compactTask(task, detailed ? 1 : 2)
  })
  let candidate: CodexSession = { ...session, tasks }
  if (payloadBytes(candidate) <= MAX_SESSION_PAYLOAD_BYTES) {
    return candidate
  }

  candidate = {
    ...session,
    tasks: session.tasks.map((task) => compactTask(task, 3)),
  }
  if (payloadBytes(candidate) <= MAX_SESSION_PAYLOAD_BYTES) {
    return candidate
  }

  // Keep the newest task records, including running and queued work.
  return {
    ...session,
    tasks: session.tasks.slice(-40).map((task) => compactTask(task, 3)),
  }
}

function compactTask(task: CodexTask, level: number): CodexTask {
  const promptLimit = level === 1 ? 120_000 : level === 2 ? 40_000 : 16_000
  const responseLimit =
    level === 1 ? 120_000 : level === 2 ? 40_000 : 16_000
  const activityLimit = level === 1 ? 80 : level === 2 ? 12 : 4
  const activityDetailLimit =
    level === 1 ? 4_000 : level === 2 ? 1_000 : 500
  const activityOutputLimit =
    level === 1 ? 8_000 : level === 2 ? 2_000 : 1_000
  const fileDiffLimit =
    level === 1 ? 80_000 : level === 2 ? 20_000 : 8_000
  const timelineLimit = level === 1 ? 80 : level === 2 ? 12 : 4
  const messageLimit =
    level === 1 ? 120_000 : level === 2 ? 40_000 : 16_000

  return {
    ...task,
    prompt: truncate(task.prompt, promptLimit),
    response: truncate(task.response, responseLimit),
    error: truncate(task.error, 4_000),
    activities: task.activities
      .slice(-activityLimit)
      .map((activity) => ({
        ...activity,
        detail: truncate(activity.detail, activityDetailLimit),
        output: truncate(activity.output, activityOutputLimit),
        fileChanges: activity.fileChanges
          ?.slice(0, 100)
          .map((change) => ({
            ...change,
            diff: truncate(change.diff, fileDiffLimit),
          })),
      })),
    timeline: (task.timeline ?? [])
      .slice(-timelineLimit)
      .map((entry) =>
        entry.kind === 'message'
          ? { ...entry, text: truncate(entry.text, messageLimit) }
          : entry,
      ),
    plan: task.plan.slice(0, 50),
  }
}

function truncate(value: string, limit: number) {
  if (value.length <= limit) return value
  return `${value.slice(0, limit)}\n\n[已省略超长内容]`
}

function payloadBytes(value: unknown) {
  return new TextEncoder().encode(JSON.stringify(value)).byteLength
}
