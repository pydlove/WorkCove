import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import {
  codexFileChangePatch,
} from '../codex-file-changes.js'

test('builds a reverse-applicable patch within the project root', () => {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), 'workcove-file-changes-'),
  )
  try {
    const filePath = path.join(directory, 'src', 'example file.js')
    fs.mkdirSync(path.dirname(filePath), { recursive: true })
    execFileSync('git', ['init', '-q'], { cwd: directory })
    execFileSync('git', ['config', 'user.name', 'Test'], {
      cwd: directory,
    })
    execFileSync('git', ['config', 'user.email', 'test@example.com'], {
      cwd: directory,
    })
    fs.writeFileSync(filePath, 'const value = 1\n')
    execFileSync('git', ['add', '.'], { cwd: directory })
    execFileSync('git', ['commit', '-qm', 'initial'], {
      cwd: directory,
    })
    fs.writeFileSync(filePath, 'const value = 2\n')
    const diff = execFileSync(
      'git',
      ['diff', '--unified=3', '--', 'src/example file.js'],
      { cwd: directory, encoding: 'utf8' },
    )

    const patch = codexFileChangePatch(directory, [
      {
        path: 'src/example file.js',
        kind: 'update',
        diff,
      },
    ])
    const patchFile = path.join(directory, 'reverse.patch')
    fs.writeFileSync(patchFile, patch)
    execFileSync(
      'git',
      ['apply', '--reverse', '--check', patchFile],
      { cwd: directory },
    )
    execFileSync('git', ['apply', '--reverse', patchFile], {
      cwd: directory,
    })
    assert.equal(fs.readFileSync(filePath, 'utf8'), 'const value = 1\n')
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('rejects patches outside the current project root', () => {
  assert.throws(
    () =>
      codexFileChangePatch('/tmp/project', [
        {
          path: '../outside.js',
          kind: 'update',
          diff: '@@ -1 +1 @@\n-old\n+new',
        },
      ]),
    /不在当前项目内/,
  )
})

test('reverse-applies added and deleted file patches', () => {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), 'workcove-file-kinds-'),
  )
  try {
    execFileSync('git', ['init', '-q'], { cwd: directory })
    execFileSync('git', ['config', 'user.name', 'Test'], {
      cwd: directory,
    })
    execFileSync('git', ['config', 'user.email', 'test@example.com'], {
      cwd: directory,
    })

    const addedPath = path.join(directory, 'added.js')
    fs.writeFileSync(addedPath, 'export const added = true\n')
    execFileSync('git', ['add', '-N', 'added.js'], { cwd: directory })
    const addedDiff = execFileSync(
      'git',
      ['diff', '--unified=3', '--', 'added.js'],
      { cwd: directory, encoding: 'utf8' },
    )
    const addedPatch = path.join(directory, 'added.patch')
    fs.writeFileSync(
      addedPatch,
      codexFileChangePatch(directory, [
        { path: 'added.js', kind: 'add', diff: addedDiff },
      ]),
    )
    execFileSync(
      'git',
      ['apply', '--reverse', '--check', addedPatch],
      { cwd: directory },
    )
    execFileSync('git', ['apply', '--reverse', addedPatch], {
      cwd: directory,
    })
    assert.equal(fs.existsSync(addedPath), false)

    const deletedPath = path.join(directory, 'deleted.js')
    fs.writeFileSync(deletedPath, 'export const deleted = true\n')
    execFileSync('git', ['add', 'deleted.js'], { cwd: directory })
    execFileSync('git', ['commit', '-qm', 'add deleted file'], {
      cwd: directory,
    })
    fs.rmSync(deletedPath)
    const deletedDiff = execFileSync(
      'git',
      ['diff', '--unified=3', '--', 'deleted.js'],
      { cwd: directory, encoding: 'utf8' },
    )
    const deletedPatch = path.join(directory, 'deleted.patch')
    fs.writeFileSync(
      deletedPatch,
      codexFileChangePatch(directory, [
        { path: 'deleted.js', kind: 'delete', diff: deletedDiff },
      ]),
    )
    execFileSync(
      'git',
      ['apply', '--reverse', '--check', deletedPatch],
      { cwd: directory },
    )
    execFileSync('git', ['apply', '--reverse', deletedPatch], {
      cwd: directory,
    })
    assert.equal(
      fs.readFileSync(deletedPath, 'utf8'),
      'export const deleted = true\n',
    )
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
