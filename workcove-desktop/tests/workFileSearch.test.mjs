import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import {
  resolveWorkFile,
  searchWorkDirectoryFiles,
} from '../workFileSearch.js'

async function createWorkspace() {
  return fs.mkdtemp(path.join(os.tmpdir(), 'workcove-file-search-'))
}

test('searches project files without entering generated directories', async () => {
  const root = await createWorkspace()
  try {
    await fs.mkdir(path.join(root, 'src'), { recursive: true })
    await fs.mkdir(path.join(root, 'node_modules', 'package'), {
      recursive: true,
    })
    await fs.writeFile(
      path.join(root, 'src', 'global-search.ts'),
      'search',
    )
    await fs.writeFile(
      path.join(root, 'node_modules', 'package', 'global-search.ts'),
      'ignored',
    )

    const results = await searchWorkDirectoryFiles(
      [root],
      'global search',
    )

    assert.equal(results.length, 1)
    assert.equal(results[0].name, 'global-search.ts')
    assert.equal(results[0].relativePath, path.join('src', 'global-search.ts'))
  } finally {
    await fs.rm(root, { recursive: true, force: true })
  }
})

test('returns files from at most the configured number of roots', async () => {
  const first = await createWorkspace()
  const second = await createWorkspace()
  try {
    await fs.writeFile(path.join(first, 'alpha.txt'), 'a')
    await fs.writeFile(path.join(second, 'beta.txt'), 'b')

    const results = await searchWorkDirectoryFiles(
      [first, second],
      '.txt',
    )

    assert.deepEqual(
      results.map((result) => result.name).sort(),
      ['alpha.txt', 'beta.txt'],
    )
  } finally {
    await Promise.all([
      fs.rm(first, { recursive: true, force: true }),
      fs.rm(second, { recursive: true, force: true }),
    ])
  }
})

test('only resolves files contained in configured roots', async () => {
  const root = await createWorkspace()
  const outside = await createWorkspace()
  try {
    const insideFile = path.join(root, 'inside.txt')
    const outsideFile = path.join(outside, 'outside.txt')
    await fs.writeFile(insideFile, 'inside')
    await fs.writeFile(outsideFile, 'outside')

    assert.equal(
      await resolveWorkFile(insideFile, [root]),
      await fs.realpath(insideFile),
    )
    await assert.rejects(
      () => resolveWorkFile(outsideFile, [root]),
      /不在已配置的工作目录中/,
    )
  } finally {
    await Promise.all([
      fs.rm(root, { recursive: true, force: true }),
      fs.rm(outside, { recursive: true, force: true }),
    ])
  }
})
