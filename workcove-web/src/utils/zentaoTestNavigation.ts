import type { CodexSession } from '@/types/codex'

const activeTestStatuses = new Set(['queued', 'running'])

export function activeZentaoTestSessionId(
  sessions: CodexSession[],
  workspaceId: string,
  bugId: number,
) {
  let activeSessionId = ''
  let latestActivity = ''
  for (const session of sessions) {
    if (session.workspaceId !== workspaceId) continue
    for (const task of session.tasks) {
      if (
        task.zentaoTestBugId !== bugId ||
        !activeTestStatuses.has(task.status)
      ) {
        continue
      }
      const activity = task.lastActivityAt || task.startedAt || ''
      if (!activeSessionId || activity > latestActivity) {
        activeSessionId = session.id
        latestActivity = activity
      }
    }
  }
  return activeSessionId
}
