import assert from 'node:assert/strict'
import test from 'node:test'

import {
  calculateWorkDirectoryStats,
  isDirectoryOrDescendant,
} from '../src/services/workDirectoryInsights.ts'

const directory = {
  id: 'directory-1',
  workspaceId: 'workspace-1',
  name: 'App',
  path: '/work/app',
  category: '开发项目',
  description: '',
  createdAt: '2026-10-01T00:00:00+08:00',
  updatedAt: '2026-10-01T00:00:00+08:00',
}

const sessions = [
  {
    id: 'session-1',
    workspaceId: 'workspace-1',
    cwd: '/work/app/src',
    tasks: [
      {
        startedAt: '2026-10-28T10:00:00+08:00',
        lastActivityAt: '2026-10-28T10:02:00+08:00',
        completedAt: '2026-10-28T10:02:00+08:00',
        durationMs: 120_000,
        status: 'completed',
        inputTokens: 100,
        outputTokens: 50,
        totalTokens: 200,
        commandCount: 2,
        fileChangeCount: 3,
        toolCount: 1,
      },
      {
        startedAt: '2026-10-20T10:00:00+08:00',
        lastActivityAt: '2026-10-20T10:01:00+08:00',
        completedAt: '2026-10-20T10:01:00+08:00',
        durationMs: null,
        status: 'failed',
        inputTokens: 30,
        outputTokens: 20,
        totalTokens: 0,
        commandCount: 1,
        fileChangeCount: 0,
        toolCount: 0,
      },
      {
        startedAt: '2026-10-29T09:00:00+08:00',
        lastActivityAt: '2026-10-29T11:00:00+08:00',
        completedAt: null,
        durationMs: null,
        status: 'running',
        inputTokens: 10,
        outputTokens: 0,
        totalTokens: 10,
        commandCount: 0,
        fileChangeCount: 1,
        toolCount: 0,
      },
    ],
  },
  {
    id: 'session-2',
    workspaceId: 'workspace-1',
    cwd: '/work/app',
    tasks: [
      {
        startedAt: '2026-10-29T10:00:00+08:00',
        lastActivityAt: '2026-10-29T10:00:00+08:00',
        completedAt: null,
        durationMs: null,
        status: 'queued',
        inputTokens: 0,
        outputTokens: 0,
        totalTokens: 0,
        commandCount: 0,
        fileChangeCount: 0,
        toolCount: 0,
      },
    ],
  },
  {
    id: 'session-sibling',
    workspaceId: 'workspace-1',
    cwd: '/work/application',
    tasks: [
      {
        startedAt: '2026-10-29T09:00:00+08:00',
        completedAt: '2026-10-29T10:00:00+08:00',
        status: 'completed',
        totalTokens: 999,
      },
    ],
  },
  {
    id: 'session-other-workspace',
    workspaceId: 'workspace-2',
    cwd: '/work/app',
    tasks: [
      {
        startedAt: '2026-10-29T09:00:00+08:00',
        completedAt: '2026-10-29T10:00:00+08:00',
        status: 'completed',
        totalTokens: 999,
      },
    ],
  },
] as never[]

test('matches only the directory itself and its descendants', () => {
  assert.equal(
    isDirectoryOrDescendant('/work/app', '/work/app'),
    true,
  )
  assert.equal(
    isDirectoryOrDescendant('/work/app/src', '/work/app'),
    true,
  )
  assert.equal(
    isDirectoryOrDescendant('/work/application', '/work/app'),
    false,
  )
})

test('aggregates workload, delivery and engineering activity', () => {
  const stats = calculateWorkDirectoryStats(
    directory,
    sessions,
    '30d',
    new Date('2026-10-29T12:00:00+08:00'),
  )

  assert.equal(stats.sessionCount, 2)
  assert.equal(stats.taskCount, 4)
  assert.equal(stats.completedTaskCount, 1)
  assert.equal(stats.endedTaskCount, 2)
  assert.equal(stats.completionRate, 50)
  assert.equal(stats.recordedDurationMs, 180_000)
  assert.equal(stats.totalTokens, 260)
  assert.equal(stats.commandCount, 3)
  assert.equal(stats.fileChangeCount, 4)
  assert.equal(stats.toolCount, 1)
  assert.equal(stats.activeDayCount, 3)
  assert.deepEqual(stats.statusCounts, {
    queued: 1,
    running: 1,
    completed: 1,
    failed: 1,
    interrupted: 0,
  })
})

test('applies the seven day boundary before calculating rates', () => {
  const stats = calculateWorkDirectoryStats(
    directory,
    sessions,
    '7d',
    new Date('2026-10-29T12:00:00+08:00'),
  )

  assert.equal(stats.taskCount, 3)
  assert.equal(stats.endedTaskCount, 1)
  assert.equal(stats.completionRate, 100)
  assert.equal(stats.recordedDurationMs, 120_000)
  assert.equal(stats.totalTokens, 210)
})
