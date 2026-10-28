import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import {
  RuntimeManager,
  releaseRank,
  sha256,
  validReleaseCode,
} from '../runtime-manager.js'

function makeRuntime(directory, releaseCode, minShellRelease = 'R100B001') {
  fs.mkdirSync(path.join(directory, 'renderer'), { recursive: true })
  fs.mkdirSync(path.join(directory, 'backend'), { recursive: true })
  fs.writeFileSync(
    path.join(directory, 'renderer', 'index.html'),
    '<!doctype html>',
  )
  fs.writeFileSync(
    path.join(directory, 'backend', 'workcove-boot.jar'),
    'thin-jar',
  )
  fs.writeFileSync(
    path.join(directory, 'runtime-manifest.json'),
    `${JSON.stringify({
      schemaVersion: 1,
      releaseCode,
      minShellRelease,
    })}\n`,
  )
}

test('release codes are ranked and validated', () => {
  assert.equal(validReleaseCode('R100B002'), true)
  assert.equal(validReleaseCode('dev'), false)
  assert.ok(releaseRank('R100B002') > releaseRank('R100B001'))
})

test('installs a complete runtime atomically', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'workcove-runtime-test-'))
  const builtin = path.join(root, 'builtin')
  makeRuntime(builtin, 'R100B001')
  const manager = new RuntimeManager({
    userData: path.join(root, 'user-data'),
    builtinDirectory: builtin,
    builtinRelease: 'R100B001',
    shellRelease: 'R100B001',
  })

  const source = path.join(root, 'source')
  makeRuntime(source, 'R100B002')
  const archive = path.join(root, 'runtime.zip')
  execFileSync('ditto', ['-c', '-k', '--norsrc', '--noextattr', '.', archive], {
    cwd: source,
  })
  const content = fs.readFileSync(archive)
  manager.install(archive, {
    releaseCode: 'R100B002',
    sha256: sha256(content),
  })

  const startup = manager.prepareStartup()
  assert.equal(startup.releaseCode, 'R100B002')
  assert.equal(
    fs.existsSync(
      path.join(startup.directory, 'backend', 'workcove-boot.jar'),
    ),
    true,
  )
  manager.markHealthy()
  assert.equal(manager.readState().pending, false)
})

test('prefers a newer builtin runtime over an older active runtime', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'workcove-runtime-test-'))
  const userData = path.join(root, 'user-data')
  const builtin = path.join(root, 'builtin')
  makeRuntime(builtin, 'R100B003', 'R100B003')
  const active = path.join(userData, 'runtime', 'releases', 'R100B002')
  makeRuntime(active, 'R100B002', 'R100B001')
  fs.mkdirSync(path.join(userData, 'runtime'), { recursive: true })
  fs.writeFileSync(
    path.join(userData, 'runtime', 'state.json'),
    `${JSON.stringify({
      schemaVersion: 1,
      current: 'R100B002',
      previous: 'R100B001',
      failed: null,
      pending: false,
      attempts: 0,
    })}\n`,
  )
  const manager = new RuntimeManager({
    userData,
    builtinDirectory: builtin,
    builtinRelease: 'R100B003',
    shellRelease: 'R100B003',
  })

  const startup = manager.prepareStartup()

  assert.equal(startup.releaseCode, 'R100B003')
  assert.equal(startup.directory, builtin)
  assert.equal(manager.readState().current, null)
  assert.equal(manager.readState().pending, false)
})
