import type {
  CodexSession,
  CodexTaskStatus,
} from '@/types/codex'
import type { WorkDirectory } from '@/types/workDirectory'

export type WorkDirectoryStatsRange = '7d' | '30d' | 'all'

export type WorkDirectoryTaskStatusCounts = Record<
  CodexTaskStatus,
  number
>

export interface WorkDirectoryStats {
  sessionCount: number
  taskCount: number
  completedTaskCount: number
  endedTaskCount: number
  completionRate: number | null
  recordedDurationMs: number
  totalTokens: number
  commandCount: number
  fileChangeCount: number
  toolCount: number
  activeDayCount: number
  latestActivityAt: string | null
  statusCounts: WorkDirectoryTaskStatusCounts
}

function normalizePath(value: string) {
  const normalized = value
    .trim()
    .replaceAll('\\', '/')
    .replace(/\/+$/, '')
  return normalized || '/'
}

export function isDirectoryOrDescendant(
  candidatePath: string,
  directoryPath: string,
) {
  if (!candidatePath.trim() || !directoryPath.trim()) return false
  const candidate = normalizePath(candidatePath).toLocaleLowerCase('en-US')
  const directory = normalizePath(directoryPath).toLocaleLowerCase('en-US')
  if (candidate === directory) return true
  return directory === '/'
    ? candidate.startsWith('/')
    : candidate.startsWith(`${directory}/`)
}

function rangeStart(range: WorkDirectoryStatsRange, now: Date) {
  if (range === 'all') return null
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - (range === '7d' ? 6 : 29))
  return start.getTime()
}

function taskTimestamp(value: string | null | undefined) {
  if (!value) return 0
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

function taskDuration(task: {
  durationMs: number | null
  startedAt: string
  completedAt: string | null
}) {
  if (
    task.durationMs !== null &&
    Number.isFinite(task.durationMs) &&
    task.durationMs >= 0
  ) {
    return task.durationMs
  }
  const startedAt = taskTimestamp(task.startedAt)
  const completedAt = taskTimestamp(task.completedAt)
  return startedAt > 0 && completedAt >= startedAt
    ? completedAt - startedAt
    : 0
}

function taskTokens(task: {
  totalTokens: number
  inputTokens?: number
  outputTokens?: number
}) {
  if (Number.isFinite(task.totalTokens) && task.totalTokens > 0) {
    return task.totalTokens
  }
  return Math.max(0, task.inputTokens ?? 0)
    + Math.max(0, task.outputTokens ?? 0)
}

function localDateKey(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

export function calculateWorkDirectoryStats(
  directory: WorkDirectory,
  sessions: CodexSession[],
  range: WorkDirectoryStatsRange,
  now = new Date(),
): WorkDirectoryStats {
  const start = rangeStart(range, now)
  const matchingSessions = sessions.filter(
    (session) =>
      session.workspaceId === directory.workspaceId &&
      isDirectoryOrDescendant(session.cwd, directory.path),
  )
  const tasks = matchingSessions.flatMap((session) =>
    session.tasks.filter((task) => {
      if (start === null) return true
      const startedAt = taskTimestamp(task.startedAt)
      return startedAt > 0 && startedAt >= start
    }),
  )
  const statusCounts: WorkDirectoryTaskStatusCounts = {
    queued: 0,
    running: 0,
    completed: 0,
    failed: 0,
    interrupted: 0,
  }
  const activeDays = new Set<string>()
  let recordedDurationMs = 0
  let totalTokens = 0
  let commandCount = 0
  let fileChangeCount = 0
  let toolCount = 0
  let latestTimestamp = 0

  for (const task of tasks) {
    statusCounts[task.status] += 1
    recordedDurationMs += taskDuration(task)
    totalTokens += taskTokens(task)
    commandCount += Math.max(0, task.commandCount)
    fileChangeCount += Math.max(0, task.fileChangeCount)
    toolCount += Math.max(0, task.toolCount)
    const dateKey = localDateKey(task.startedAt)
    if (dateKey) activeDays.add(dateKey)
    latestTimestamp = Math.max(
      latestTimestamp,
      taskTimestamp(task.lastActivityAt),
      taskTimestamp(task.completedAt),
      taskTimestamp(task.startedAt),
    )
  }

  const endedTaskCount =
    statusCounts.completed +
    statusCounts.failed +
    statusCounts.interrupted
  const includedTasks = new Set(tasks)
  const sessionsWithTasks = matchingSessions.filter((session) =>
    session.tasks.some((task) => includedTasks.has(task)),
  ).length

  return {
    sessionCount: sessionsWithTasks,
    taskCount: tasks.length,
    completedTaskCount: statusCounts.completed,
    endedTaskCount,
    completionRate:
      endedTaskCount > 0
        ? (statusCounts.completed / endedTaskCount) * 100
        : null,
    recordedDurationMs,
    totalTokens,
    commandCount,
    fileChangeCount,
    toolCount,
    activeDayCount: activeDays.size,
    latestActivityAt:
      latestTimestamp > 0
        ? new Date(latestTimestamp).toISOString()
        : null,
    statusCounts,
  }
}
