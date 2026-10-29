import assert from 'node:assert/strict'
import test from 'node:test'

import {
  normalizeTextUrl,
  splitTextUrls,
} from '../src/utils/textUrls.ts'

test('splits http, https and www urls without consuming punctuation', () => {
  const segments = splitTextUrls(
    '访问 https://example.com/login?next=%2Fhome，备用 www.example.org/test。',
  )

  assert.deepEqual(
    segments.map((segment) => segment.value),
    [
      '访问 ',
      'https://example.com/login?next=%2Fhome',
      '，备用 ',
      'www.example.org/test',
      '。',
    ],
  )
  assert.equal(
    segments[1].href,
    'https://example.com/login?next=%2Fhome',
  )
  assert.equal(
    segments[3].href,
    'https://www.example.org/test',
  )
})

test('keeps unsafe and invalid urls as plain text', () => {
  assert.deepEqual(splitTextUrls('javascript:alert(1)'), [
    { type: 'text', value: 'javascript:alert(1)' },
  ])
  assert.equal(normalizeTextUrl('ftp://example.com'), '')
})
