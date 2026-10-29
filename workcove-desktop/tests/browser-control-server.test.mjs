import assert from 'node:assert/strict'
import test from 'node:test'

import {
  bearerTokenMatches,
  isLoopbackAddress,
  startBrowserControlServer,
} from '../browser-control-server.js'

test('recognizes loopback clients and bearer tokens', () => {
  assert.equal(isLoopbackAddress('127.0.0.1'), true)
  assert.equal(isLoopbackAddress('::1'), true)
  assert.equal(isLoopbackAddress('::ffff:127.0.0.1'), true)
  assert.equal(isLoopbackAddress('192.168.1.20'), false)
  assert.equal(
    bearerTokenMatches('Bearer test-token', 'test-token'),
    true,
  )
  assert.equal(
    bearerTokenMatches('Bearer wrong-token', 'test-token'),
    false,
  )
})

test('authenticates and dispatches browser control requests', async () => {
  const requests = []
  const endpoint = await startBrowserControlServer({
    token: 'test-token-with-enough-entropy',
    dispatch: async (action, payload) => {
      requests.push({ action, payload })
      return { action, ok: true }
    },
  })
  try {
    const unauthorized = await fetch(`${endpoint.url}/v1/status`)
    assert.equal(unauthorized.status, 401)

    const status = await fetch(`${endpoint.url}/v1/status`, {
      headers: {
        Authorization: 'Bearer test-token-with-enough-entropy',
      },
    })
    assert.equal(status.status, 200)
    assert.deepEqual(await status.json(), {
      action: 'status',
      ok: true,
    })

    const click = await fetch(`${endpoint.url}/v1/click`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer test-token-with-enough-entropy',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ ref: 'wc-2' }),
    })
    assert.equal(click.status, 200)
    assert.deepEqual(requests, [
      { action: 'status', payload: {} },
      { action: 'click', payload: { ref: 'wc-2' } },
    ])

    const capabilityUpdate = await fetch(
      `${endpoint.url}/v1/capability-set/update`,
      {
        method: 'POST',
        headers: {
          Authorization: 'Bearer test-token-with-enough-entropy',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ confirm: false }),
      },
    )
    assert.equal(capabilityUpdate.status, 200)
    assert.deepEqual(requests.at(-1), {
      action: 'capabilityUpdate',
      payload: { confirm: false },
    })
  } finally {
    await endpoint.close()
  }
})
