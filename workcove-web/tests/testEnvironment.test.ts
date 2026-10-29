import assert from 'node:assert/strict'
import test from 'node:test'

import {
  redactTestEnvironmentCredentials,
  testEnvironmentValuesFromWebsite,
} from '../src/utils/testEnvironment.ts'

test('maps a saved website to an environment draft', () => {
  assert.deepEqual(
    testEnvironmentValuesFromWebsite({
      name: ' V3.0.2 研发环境 ',
      url: ' https://dev.example.com/login ',
    }),
    {
      name: 'V3.0.2 研发环境',
      url: 'https://dev.example.com/login',
    },
  )
})

test('removes transient credentials from the persisted prompt', () => {
  const prompt = [
    '【测试环境】',
    '用户名：alice',
    '密码：secret-value',
    '',
    '【测试目标】',
    '验证 Bug',
  ].join('\n')

  const redacted = redactTestEnvironmentCredentials(
    prompt,
    'alice',
    'secret-value',
  )

  assert.equal(redacted.includes('secret-value'), false)
  assert.equal(redacted.includes('alice'), false)
  assert.equal(redacted.includes('仅在本次运行内存中传递'), true)
  assert.equal(redacted.includes('验证 Bug'), true)
})
