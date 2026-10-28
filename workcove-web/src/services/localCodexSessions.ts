import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type { CodexSession } from '@/types/codex'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.codex-sessions.v1'
const PREFERRED_DIRECTORY_STORAGE_KEY =
  'workcove.codex-preferred-directories.v1'

export type CodexPreferredDirectoriesByWorkspace = Record<string, string>

export function loadLocalCodexSessions(email: string) {
  const sessions = readUserScopedValue<CodexSession[]>(STORAGE_KEY, email)
  return Array.isArray(sessions)
    ? sessions
        .filter(
          (session) =>
            session &&
            typeof session.id === 'string' &&
            typeof session.title === 'string' &&
            Array.isArray(session.tasks),
        )
        .map((session) => ({
          ...session,
          workspaceId:
            typeof session.workspaceId === 'string' &&
            session.workspaceId
              ? session.workspaceId
              : DEFAULT_WORKSPACE_ID,
          tasks: session.tasks.map((task) => ({
            ...task,
            timeline: Array.isArray(task.timeline)
              ? task.timeline
              : [],
          })),
        }))
    : []
}

export function saveLocalCodexSessions(
  email: string,
  sessions: CodexSession[],
) {
  writeUserScopedValue(STORAGE_KEY, email, sessions)
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
