import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyCodexGitBranchPolicy,
  validateCodexDevelopmentBranchName,
} from '../src/utils/codexGitPolicy.ts'

test('defaults Codex tasks to the current branch without creating another branch', () => {
  const prompt = applyCodexGitBranchPolicy('修复登录问题', {
    currentBranch: 'develop',
  })

  assert.match(prompt, /当前分支「develop」/)
  assert.match(prompt, /不得创建、切换、重命名或删除分支/)
  assert.match(prompt, /git switch -c/)
  assert.match(prompt, /修复登录问题/)
})

test('allows branch creation only with an explicit development branch', () => {
  const prompt = applyCodexGitBranchPolicy('开发新功能', {
    currentBranch: 'develop',
    createBranch: 'feature/codex-login',
  })

  assert.match(prompt, /明确授权创建分支/)
  assert.match(prompt, /feature\/codex-login/)
  assert.match(prompt, /目标分支已存在/)
  assert.doesNotMatch(prompt, /没有“新增分支开发”授权/)
})

test('does not add duplicate branch policies', () => {
  const first = applyCodexGitBranchPolicy('执行任务')
  const second = applyCodexGitBranchPolicy(first)

  assert.equal(second, first)
})

test('rejects unsafe branch names', () => {
  assert.match(
    validateCodexDevelopmentBranchName('feature/bad branch'),
    /只能包含/,
  )
  assert.match(
    validateCodexDevelopmentBranchName('feature/../main'),
    /格式无效/,
  )
  assert.match(
    validateCodexDevelopmentBranchName('-danger'),
    /格式无效/,
  )
  assert.equal(
    validateCodexDevelopmentBranchName('feature/codex-登录'),
    '',
  )
})
