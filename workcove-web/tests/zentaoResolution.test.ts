import assert from 'node:assert/strict'
import test from 'node:test'

import { extractZentaoResolution } from '../src/utils/zentaoResolution.ts'

test('extracts labeled sections written on separate lines', () => {
  assert.deepEqual(
    extractZentaoResolution(
      [
        '已修复。',
        '',
        '解决方案：',
        '调整告警切换参数。',
        '',
        '原因：',
        '旧请求覆盖了新告警上下文。',
      ].join('\n'),
      '31887',
    ),
    {
      solution: '调整告警切换参数。',
      reason: '旧请求覆盖了新告警上下文。',
    },
  )
})

test('extracts inline labeled sections from the same paragraph', () => {
  assert.deepEqual(
    extractZentaoResolution(
      '构建通过。解决方案：修复详情缓存；原因：缓存 key 未包含告警 ID。',
      '31887',
    ),
    {
      solution: '修复详情缓存',
      reason: '缓存 key 未包含告警 ID。',
    },
  )
})

test('extracts markdown labels and alternative reason wording', () => {
  assert.deepEqual(
    extractZentaoResolution(
      [
        '- **解决方案：** 修复详情缓存。',
        '- **定位原因：** 缓存 key 未包含告警 ID。',
      ].join('\n'),
      '31887',
    ),
    {
      solution: '修复详情缓存。',
      reason: '缓存 key 未包含告警 ID。',
    },
  )
})

test('extracts the matching section from a batch response', () => {
  assert.deepEqual(
    extractZentaoResolution(
      [
        '【Bug #31887】',
        '解决方案：修复 A',
        '原因：根因 A',
        '',
        '【Bug #31888】',
        '解决方案：修复 B',
        '原因：根因 B',
      ].join('\n'),
      '31888',
      true,
    ),
    {
      solution: '修复 B',
      reason: '根因 B',
    },
  )
})

test('returns null when a batch response omits the linked bug', () => {
  assert.equal(
    extractZentaoResolution(
      '【Bug #31887】\n解决方案：修复 A\n原因：根因 A',
      '31888',
      true,
    ),
    null,
  )
})
