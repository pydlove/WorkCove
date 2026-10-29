import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const desktopDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
)

test('exposes the WorkCove browser tools over MCP stdio', async () => {
  const child = spawn(
    process.execPath,
    [path.join(desktopDirectory, 'browser-mcp.js')],
    {
      stdio: ['pipe', 'pipe', 'pipe'],
      env: {
        ...process.env,
        WORKCOVE_BROWSER_CONTROL_URL: 'http://127.0.0.1:1',
        WORKCOVE_BROWSER_CONTROL_TOKEN: 'test-token',
      },
    },
  )
  let output = ''
  const messages = []
  child.stdout.setEncoding('utf8')
  child.stdout.on('data', (chunk) => {
    output += chunk
    let newline = output.indexOf('\n')
    while (newline >= 0) {
      const line = output.slice(0, newline).trim()
      output = output.slice(newline + 1)
      newline = output.indexOf('\n')
      if (line) messages.push(JSON.parse(line))
    }
  })

  const waitForMessage = async (id) => {
    const startedAt = Date.now()
    while (Date.now() - startedAt < 5000) {
      const message = messages.find((item) => item.id === id)
      if (message) return message
      await new Promise((resolve) => setTimeout(resolve, 20))
    }
    throw new Error(`Timed out waiting for MCP response ${id}`)
  }

  try {
    child.stdin.write(
      `${JSON.stringify({
        jsonrpc: '2.0',
        id: 1,
        method: 'initialize',
        params: {
          protocolVersion: '2025-06-18',
          capabilities: {},
          clientInfo: { name: 'test', version: '1.0.0' },
        },
      })}\n`,
    )
    const initialized = await waitForMessage(1)
    assert.equal(initialized.result.serverInfo.name, 'workcove-browser')
    assert.match(initialized.result.instructions, /logged-in web session/)

    child.stdin.write(
      `${JSON.stringify({
        jsonrpc: '2.0',
        id: 2,
        method: 'tools/list',
        params: {},
      })}\n`,
    )
    const tools = await waitForMessage(2)
    const names = tools.result.tools.map((tool) => tool.name)
    assert.deepEqual(names, [
      'browser_open',
      'browser_status',
      'browser_snapshot',
      'browser_click',
      'browser_type',
      'browser_press',
      'browser_fill_credentials',
      'browser_wait',
      'browser_evaluate',
      'browser_screenshot',
      'browser_downloads',
      'capability_set_update',
    ])
  } finally {
    child.kill()
  }
})
