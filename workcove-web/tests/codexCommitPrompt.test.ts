import assert from 'node:assert/strict'
import test from 'node:test'

import { buildCodexCommitPrompt } from '../src/utils/codexCommitPrompt.ts'

test('commit prompt disables bug detection and does not block on unrelated changes', () => {
  const prompt = buildCodexCommitPrompt({
    name: 'WorkCove',
    path: '/tmp/WorkCove',
  })

  assert.match(prompt, /不要进行 Bug 检测/)
  assert.match(prompt, /不得因为同时存在多个功能/)
  assert.match(prompt, /暂存所有剩余的当前改动/)
  assert.doesNotMatch(prompt, /发现异常时停止/)
  assert.doesNotMatch(prompt, /与当前任务无关的改动.*停止/)
})

test('commit prompt still guards conflicts and confirmed plaintext secrets', () => {
  const prompt = buildCodexCommitPrompt({
    name: 'WorkCove',
    path: '/tmp/WorkCove',
  })

  assert.match(prompt, /未解决的合并冲突/)
  assert.match(prompt, /明文密码、令牌、私钥/)
  assert.match(prompt, /不要仅凭文件名/)
})
