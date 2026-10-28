import assert from 'node:assert/strict'
import test from 'node:test'

import { extractZentaoTestResult } from '../src/utils/zentaoTestResult.ts'

test('extracts a passing Codex test result', () => {
  assert.deepEqual(
    extractZentaoTestResult(
      [
        '测试结论：通过',
        '测试环境：研发环境，Chrome 141，分支 feature_V3.0.2_R100',
        '问题描述：列表最后一条告警的下一页按钮仍可点击。',
        '修复证明：最后一条告警时按钮已置灰，截图验证通过。',
      ].join('\n'),
    ),
    {
      status: 'passed',
      environment: '研发环境，Chrome 141，分支 feature_V3.0.2_R100',
      problemDescription: '列表最后一条告警的下一页按钮仍可点击。',
      proof: '最后一条告警时按钮已置灰，截图验证通过。',
    },
  )
})

test('supports markdown headings and failed results', () => {
  const result = extractZentaoTestResult(
    [
      '## 测试结论',
      '未通过',
      '## 测试环境',
      '研发环境 / Safari',
      '## 问题描述',
      '点击下一条仍然报错。',
      '## 修复证明',
      '控制台仍有异常。',
    ].join('\n'),
  )
  assert.equal(result.status, 'failed')
  assert.equal(result.environment, '研发环境 / Safari')
  assert.equal(result.problemDescription, '点击下一条仍然报错。')
  assert.equal(result.proof, '控制台仍有异常。')
})

test('falls back to the full response when structured labels are absent', () => {
  const result = extractZentaoTestResult(
    '页面可以正常打开，但没有可用的测试截图。',
  )
  assert.equal(result.status, 'blocked')
  assert.equal(
    result.proof,
    '页面可以正常打开，但没有可用的测试截图。',
  )
})
