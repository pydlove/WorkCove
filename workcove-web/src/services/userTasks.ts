import type {
  WorkTask,
  WorkTaskAttachment,
  WorkTaskExecutionUpdate,
} from '@/types/workbench'

export function normalizeWorkTask(
  task: WorkTask,
  workspaceId: string,
): WorkTask {
  const executionStatus =
    task.executionStatus ?? (task.done ? 'completed' : 'todo')
  return {
    ...task,
    workspaceId: task.workspaceId ?? workspaceId,
    description:
      typeof task.description === 'string' ? task.description : '',
    executionStatus,
    executionResult:
      typeof task.executionResult === 'string'
        ? task.executionResult
        : '',
    executionError:
      typeof task.executionError === 'string'
        ? task.executionError
        : '',
    attachments: Array.isArray(task.attachments)
      ? task.attachments
          .filter(isWorkTaskAttachment)
          .slice(0, 8)
      : [],
    done: executionStatus === 'completed',
  }
}

function isWorkTaskAttachment(
  value: unknown,
): value is WorkTaskAttachment {
  if (!value || typeof value !== 'object') return false
  const attachment = value as Partial<WorkTaskAttachment>
  return (
    typeof attachment.id === 'string' &&
    typeof attachment.name === 'string' &&
    typeof attachment.contentType === 'string' &&
    typeof attachment.size === 'number' &&
    Number.isFinite(attachment.size) &&
    attachment.size >= 0 &&
    (attachment.kind === 'image' || attachment.kind === 'file')
  )
}

export function applyWorkTaskExecutionUpdate(
  tasks: WorkTask[],
  update: WorkTaskExecutionUpdate,
  now = new Date().toISOString(),
) {
  let changed = false
  const next = tasks.map((task) => {
    if (task.id !== update.taskId) return task
    changed = true
    const running = update.status === 'running'
    const completed = update.status === 'completed'
    return {
      ...task,
      executionStatus: update.status,
      codexSessionId: update.codexSessionId,
      codexTaskId: update.codexTaskId,
      executionResult: running
        ? task.executionResult ?? ''
        : update.result?.trim() ?? '',
      executionError: running
        ? ''
        : update.error?.trim() ?? '',
      done: completed,
      lastExecutedAt: now,
      updatedAt: now,
    } satisfies WorkTask
  })
  return changed ? next : tasks
}
