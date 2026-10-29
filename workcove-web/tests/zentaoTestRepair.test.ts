import assert from 'node:assert/strict'
import test from 'node:test'

import {
  shouldAutoRepairZentaoTest,
  zentaoTestRepairPrompt,
  zentaoTestRepairTitle,
} from '../src/services/zentaoTestRepair.ts'
import type { ZentaoBug } from '../src/types/zentao.ts'
import type { ZentaoTestResult } from '../src/types/zentaoTest.ts'

const result: ZentaoTestResult = {
  id: 'test-1',
  bugId: 1,
  remoteId: '31844',
  workspaceId: 'default',
  taskId: 'task-1',
  sessionId: 'session-1',
  runId: 'run-1',
  status: 'failed',
  environment: '研发环境 · https://example.test',
  problemDescription: '最后一页按钮置灰',
  proof: '点击下一页后仍停留在最后一页。',
  screenshots: ['result.png'],
  createdAt: '2026-10-29T00:00:00.000Z',
}

const bug = {
  id: 1,
  remoteId: '31844',
  title: '最后一页下一页按钮应禁用',
  steps: '进入告警列表\n翻到最后一页',
} as ZentaoBug

test('failed and blocked tests trigger automatic repair', () => {
  assert.equal(shouldAutoRepairZentaoTest(result), true)
  assert.equal(
    shouldAutoRepairZentaoTest({ ...result, status: 'blocked' }),
    true,
  )
  assert.equal(
    shouldAutoRepairZentaoTest({ ...result, status: 'passed' }),
    false,
  )
})

test('repair prompt contains bug, evidence and required output', () => {
  const prompt = zentaoTestRepairPrompt(bug, result)

  assert.match(prompt, /Bug #31844/)
  assert.match(prompt, /研发环境/)
  assert.match(prompt, /result\.png/)
  assert.match(prompt, /解决方案：/)
  assert.match(prompt, /定位原因：/)
  assert.match(prompt, /验证结果：/)
  assert.equal(
    zentaoTestRepairTitle(bug, result),
    '修复测试未通过：Bug #31844 最后一页下一页按钮应禁用',
  )
})
