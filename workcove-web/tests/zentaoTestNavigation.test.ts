import assert from 'node:assert/strict'
import test from 'node:test'

import type { CodexSession } from '../src/types/codex.ts'
import { activeZentaoTestSessionId } from '../src/utils/zentaoTestNavigation.ts'

function session(
  id: string,
  workspaceId: string,
  bugId: number,
  status: 'queued' | 'running' | 'completed',
  lastActivityAt: string,
) {
  return {
    id,
    workspaceId,
    tasks: [
      {
        id: `${id}-task`,
        zentaoTestBugId: bugId,
        status,
        startedAt: lastActivityAt,
        lastActivityAt,
      },
    ],
  } as CodexSession
}

test('finds the active Codex session for a Zentao test bug', () => {
  const sessions = [
    session(
      'completed',
      'workspace',
      31887,
      'completed',
      '2026-10-29T08:03:00Z',
    ),
    session(
      'older',
      'workspace',
      31887,
      'running',
      '2026-10-29T08:01:00Z',
    ),
    session(
      'newer',
      'workspace',
      31887,
      'queued',
      '2026-10-29T08:02:00Z',
    ),
    session(
      'other-workspace',
      'other',
      31887,
      'running',
      '2026-10-29T08:04:00Z',
    ),
    session(
      'other-bug',
      'workspace',
      31888,
      'running',
      '2026-10-29T08:05:00Z',
    ),
  ]

  assert.equal(
    activeZentaoTestSessionId(sessions, 'workspace', 31887),
    'newer',
  )
  assert.equal(
    activeZentaoTestSessionId(sessions, 'workspace', 31889),
    '',
  )
})
