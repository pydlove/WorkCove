import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyWorkTaskExecutionUpdate,
  normalizeWorkTask,
} from '../src/services/userTasks.ts'
import type { WorkTask } from '../src/types/workbench.ts'

const task: WorkTask = {
  id: 42,
  workspaceId: 'workspace-1',
  title: '验证任务执行',
  project: '任务测试',
  time: '10:00',
  done: false,
  priority: 'medium',
  description: '验证 Codex 执行状态回写',
  executionStatus: 'todo',
}

test('normalizes legacy tasks into the current execution model', () => {
  const normalized = normalizeWorkTask(
    {
      ...task,
      workspaceId: undefined,
      executionStatus: undefined,
      done: true,
    },
    'workspace-default',
  )

  assert.equal(normalized.workspaceId, 'workspace-default')
  assert.equal(normalized.executionStatus, 'completed')
  assert.equal(normalized.done, true)
  assert.equal(normalized.description, '验证 Codex 执行状态回写')
})

test('keeps valid task attachments and drops malformed rows', () => {
  const normalized = normalizeWorkTask(
    {
      ...task,
      attachments: [
        {
          id: 'attachment-1',
          name: 'screenshot.png',
          contentType: 'image/png',
          size: 2048,
          kind: 'image',
        },
        {
          id: 'invalid',
          name: 'broken.bin',
        },
      ],
    } as unknown as WorkTask,
    'workspace-default',
  )

  assert.deepEqual(normalized.attachments, [
    {
      id: 'attachment-1',
      name: 'screenshot.png',
      contentType: 'image/png',
      size: 2048,
      kind: 'image',
    },
  ])
})

test('keeps a task running while Codex starts', () => {
  const [updated] = applyWorkTaskExecutionUpdate(
    [task],
    {
      taskId: 42,
      codexSessionId: 'session-1',
      codexTaskId: 'codex-task-1',
      status: 'running',
    },
    '2026-10-28T12:00:00.000Z',
  )

  assert.equal(updated.executionStatus, 'running')
  assert.equal(updated.done, false)
  assert.equal(updated.codexSessionId, 'session-1')
  assert.equal(updated.codexTaskId, 'codex-task-1')
  assert.equal(updated.updatedAt, '2026-10-28T12:00:00.000Z')
})

test('writes the final Codex result back to the task', () => {
  const [updated] = applyWorkTaskExecutionUpdate(
    [task],
    {
      taskId: 42,
      codexSessionId: 'session-1',
      codexTaskId: 'codex-task-1',
      status: 'completed',
      result: '  执行成功，验证通过。  ',
      error: '',
    },
    '2026-10-28T12:30:00.000Z',
  )

  assert.equal(updated.executionStatus, 'completed')
  assert.equal(updated.done, true)
  assert.equal(updated.executionResult, '执行成功，验证通过。')
  assert.equal(updated.executionError, '')
  assert.equal(updated.updatedAt, '2026-10-28T12:30:00.000Z')
})

test('records failure details without mixing them into success text', () => {
  const [updated] = applyWorkTaskExecutionUpdate(
    [task],
    {
      taskId: 42,
      codexSessionId: 'session-1',
      codexTaskId: 'codex-task-1',
      status: 'failed',
      result: '',
      error: '测试命令失败',
    },
  )

  assert.equal(updated.executionStatus, 'failed')
  assert.equal(updated.done, false)
  assert.equal(updated.executionResult, '')
  assert.equal(updated.executionError, '测试命令失败')
})
