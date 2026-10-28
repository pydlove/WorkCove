import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { materializeSymlinks } from '../scripts/runtime-package.mjs'

test('materializes bundled symlinks before app signing', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'workcove-jre-test-'))
  const baseDirectory = path.join(root, 'legal', 'java.base')
  const moduleDirectory = path.join(root, 'legal', 'java.sql')
  fs.mkdirSync(baseDirectory, { recursive: true })
  fs.mkdirSync(moduleDirectory, { recursive: true })
  fs.writeFileSync(path.join(baseDirectory, 'LICENSE'), 'license text')
  fs.symlinkSync('../java.base/LICENSE', path.join(moduleDirectory, 'LICENSE'))

  materializeSymlinks(root)

  assert.equal(
    fs.lstatSync(path.join(moduleDirectory, 'LICENSE')).isSymbolicLink(),
    false,
  )
  assert.equal(
    fs.readFileSync(path.join(moduleDirectory, 'LICENSE'), 'utf8'),
    'license text',
  )
})
