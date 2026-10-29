import assert from 'node:assert/strict'
import test from 'node:test'

import {
  nextBrowserTabId,
  normalizeBrowserBounds,
  normalizeBrowserStartUrl,
  normalizeHttpUrl,
} from '../embedded-browser.js'
import {
  buildCredentialFillScript,
  fillCredentialsIntoContents,
} from '../credential-autofill.js'

test('accepts only absolute http and https browser URLs', () => {
  assert.equal(
    normalizeHttpUrl('http://172.16.66.95/path'),
    'http://172.16.66.95/path',
  )
  assert.equal(
    normalizeHttpUrl(' https://example.com '),
    'https://example.com/',
  )
  assert.throws(() => normalizeHttpUrl('file:///etc/passwd'))
  assert.throws(() => normalizeHttpUrl('javascript:alert(1)'))
  assert.throws(() => normalizeHttpUrl('172.16.66.95'))
  assert.equal(normalizeBrowserStartUrl(' about:blank '), 'about:blank')
  assert.equal(
    normalizeBrowserStartUrl('https://example.com'),
    'https://example.com/',
  )
  assert.throws(() => normalizeBrowserStartUrl('file:///etc/passwd'))
})

test('clamps browser view bounds to the window content area', () => {
  assert.deepEqual(
    normalizeBrowserBounds(
      { x: 40, y: 60, width: 900, height: 600 },
      { width: 800, height: 500 },
    ),
    { x: 40, y: 60, width: 760, height: 440 },
  )
  assert.throws(() =>
    normalizeBrowserBounds(
      { x: 0, y: 0, width: 0, height: 20 },
      { width: 800, height: 500 },
    ),
  )
})

test('selects the nearest remaining browser tab after closing', () => {
  assert.equal(nextBrowserTabId(['a', 'b', 'c'], 'b'), 'c')
  assert.equal(nextBrowserTabId(['a', 'b', 'c'], 'c'), 'b')
  assert.equal(nextBrowserTabId(['a'], 'a'), '')
  assert.equal(nextBrowserTabId([], 'missing'), '')
})

test('builds credential scripts without plaintext secrets', () => {
  const script = buildCredentialFillScript(
    '测试账号',
    'S3cret-密码',
  )
  assert.equal(script.includes('测试账号'), false)
  assert.equal(script.includes('S3cret-密码'), false)
  assert.match(script, /input\[type="password"\]/)
})

test('validates the page origin before filling credentials', async () => {
  const filled = await fillCredentialsIntoContents(
    {
      getURL: () => 'http://172.16.32.34:8087/index.html',
      executeJavaScript: async () => ({
        filledUsername: true,
        filledPassword: true,
      }),
    },
    {
      origin: 'http://172.16.32.34:8087',
      username: 'admin',
      password: 'secret',
    },
  )
  assert.equal(filled.filledUsername, true)
  assert.equal(filled.filledPassword, true)

  await assert.rejects(
    fillCredentialsIntoContents(
      {
        getURL: () => 'http://other.example/index.html',
        executeJavaScript: async () => ({
          filledUsername: true,
          filledPassword: true,
        }),
      },
      {
        origin: 'http://172.16.32.34:8087',
        username: 'admin',
        password: 'secret',
      },
    ),
    /页面已跳转到其他网站/,
  )
})
