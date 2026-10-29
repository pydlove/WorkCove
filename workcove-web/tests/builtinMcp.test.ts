import assert from 'node:assert/strict'
import test from 'node:test'

import {
  promptWithBuiltinMcpContext,
  selectedBuiltinMcpDefinitions,
} from '../src/services/builtinMcp.ts'

test('returns built-in MCP Chinese names in catalog order', () => {
  const definitions = selectedBuiltinMcpDefinitions([
    'capability_set_update',
    'workcove_browser',
  ])
  assert.deepEqual(
    definitions.map((definition) => definition.name),
    ['内置浏览器', '能力集更新'],
  )
})

test('adds selected MCP tools and a default task to the prompt', () => {
  const prompt = promptWithBuiltinMcpContext('', [
    'capability_set_update',
  ])
  assert.match(prompt, /【指定内置 MCP】/)
  assert.match(prompt, /能力集更新（capability_set_update）/)
  assert.match(prompt, /capability_set_update/)
  assert.match(prompt, /请预览当前 app_audit 分支/)
})

test('keeps the user task when an MCP is selected', () => {
  const prompt = promptWithBuiltinMcpContext('更新能力集', [
    'capability_set_update',
  ])
  assert.match(prompt, /更新能力集/)
})
