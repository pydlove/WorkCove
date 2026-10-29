import assert from 'node:assert/strict'
import test from 'node:test'

import type { CodeReviewRecord } from '../src/types/codeReview.ts'
import {
  codeReviewPrompt,
  codeReviewRepairPrompt,
  parseCodeReviewResult,
} from '../src/utils/codeReview.ts'

function review(): CodeReviewRecord {
  return {
    id: 'review-1',
    workspaceId: 'default',
    repositoryId: 'repo-1',
    repositoryName: 'WorkCove',
    repositoryPath: '/tmp/workcove',
    branch: 'refs/heads/feature/review',
    branchLabel: 'feature/review',
    branchAuthor: 'Alice',
    branchAuthorEmail: 'alice@example.com',
    reviewSince: '2026-10-29',
    reviewUntil: '2026-10-29',
    baseBranch: 'develop',
    baseCommit: '1111111111111111',
    headCommit: '2222222222222222',
    fromCommit: '1111111111111111',
    commitCount: 3,
    fileCount: 8,
    changedFiles: [
      'src/example.ts',
      'src/auth/session.ts',
    ],
    additions: 120,
    deletions: 24,
    focus: '重点检查权限',
    status: 'reviewing',
    summary: '',
    findings: [],
    manualNote: '',
    rawResult: '',
    iteration: 1,
    previousReviewId: null,
    codexSessionId: '',
    codexTaskId: '',
    repairSessionId: '',
    repairTaskId: '',
    createdAt: '2026-10-29T00:00:00Z',
    updatedAt: '2026-10-29T00:00:00Z',
    reviewedAt: null,
  }
}

test('builds a branch-scoped, read-only review prompt', () => {
  const prompt = codeReviewPrompt(review())

  assert.match(prompt, /1111111111111111\.\.2222222222222222/)
  assert.match(prompt, /不要修改代码/)
  assert.match(prompt, /重点检查权限/)
  assert.match(prompt, /提交时间范围：2026-10-29/)
  assert.match(prompt, /<CODE_REVIEW_RESULT>/)
})

test('parses a pass verdict', () => {
  const result = parseCodeReviewResult(`
检查完成。
<CODE_REVIEW_RESULT>
{
  "verdict": "pass",
  "summary": "差异集中在页面和接口层，未发现需要人工介入的问题。",
  "findings": []
}
</CODE_REVIEW_RESULT>
`)

  assert.equal(result?.verdict, 'pass')
  assert.equal(result?.findings.length, 0)
})

test('high-risk files always require manual review', () => {
  const result = parseCodeReviewResult(`
<CODE_REVIEW_RESULT>
{
  "verdict": "pass",
  "summary": "其他逻辑正常。",
  "findings": [{
    "title": "会话校验分支需要确认",
    "file": "src/auth/session.ts",
    "line": 42,
    "severity": "warning",
    "needsManual": false,
    "reason": "调整了登录态判断。",
    "suggestion": "人工确认过期会话行为。"
  }]
}
</CODE_REVIEW_RESULT>
`)

  assert.equal(result?.verdict, 'manual')
  assert.equal(result?.findings[0]?.needsManual, true)
})

test('changed high-risk files are manual even when Codex returns pass', () => {
  const result = parseCodeReviewResult(
    `
<CODE_REVIEW_RESULT>
{
  "verdict": "pass",
  "summary": "未发现具体问题。",
  "findings": []
}
</CODE_REVIEW_RESULT>
`,
    ['src/services/order.ts', 'src/auth/session.ts'],
  )

  assert.equal(result?.verdict, 'manual')
  assert.deepEqual(
    result?.findings.map((finding) => finding.file),
    ['src/auth/session.ts'],
  )
})

test('malformed output never becomes an automatic approval', () => {
  assert.equal(
    parseCodeReviewResult('Codex 检查完成，没有问题。'),
    null,
  )
})

test('repair prompt contains confirmed review scope and notes', () => {
  const record = review()
  record.manualNote = '需要补充失败场景测试。'
  const prompt = codeReviewRepairPrompt(record, [
    {
      id: 'finding-1',
      title: '缺少异常分支测试',
      file: 'src/example.ts',
      line: 10,
      severity: 'warning',
      needsManual: true,
      reason: '新增分支没有测试覆盖。',
      suggestion: '补充失败请求用例。',
      status: 'confirmed',
    },
  ])

  assert.match(prompt, /src\/example\.ts:10/)
  assert.match(prompt, /需要补充失败场景测试/)
  assert.match(prompt, /不要自动提交/)
})
