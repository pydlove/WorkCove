import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs/promises'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import {
  buildCapabilitySavePayload,
  capabilityVersionKey,
  chooseCapabilityZipEntry,
  resolveCapabilityRow,
  updateCapabilitySet,
  validateCapabilityConfigContent,
} from '../capability-set-update.js'
import {
  resolveCapabilityResources,
  selectCredential,
  selectWebsite,
} from '../workcove-capability-resources.js'

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      server.off('error', reject)
      const address = server.address()
      if (!address || typeof address === 'string') {
        reject(new Error('Failed to bind test server'))
        return
      }
      resolve(`http://127.0.0.1:${address.port}/`)
    })
  })
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () =>
      resolve(Buffer.concat(chunks).toString('utf8')),
    )
    request.on('error', reject)
  })
}

test('matches app_audit branches to capability versions', () => {
  const row = resolveCapabilityRow(
    [
      { id: 1, name: 'AISG_V3.0.2_R100' },
      { id: 2, name: 'AISG_V3.0.1_R90' },
    ],
    'feature_V3.0.2_R100',
  )
  assert.equal(row.id, 1)
  assert.equal(
    capabilityVersionKey('feature/v3.0.2_r100'),
    'V3.0.2_R100',
  )
  assert.equal(
    chooseCapabilityZipEntry([
      'docs/readme.txt',
      'config/CAPA_CONFIG_MAF.json',
    ]),
    'config/CAPA_CONFIG_MAF.json',
  )
})

test('serializes existing menu configuration into the save payload', () => {
  const payload = buildCapabilitySavePayload(
    {
      id: 9,
      name: 'AISG_V3.0.2_R100',
      description: 'test',
      menus: [
        {
          id: 10,
          orderNo: 1,
          roleCodeList: ['admin'],
          prdCodeList: ['AISG'],
          child: [],
        },
      ],
    },
    'maf',
  )
  assert.equal(payload.productLine, 'maf')
  assert.deepEqual(payload.config.menus, [
    {
      id: 10,
      orderNo: 1,
      roleCodeList: ['admin'],
      prdCodeList: ['AISG'],
      child: [],
    },
  ])
})

test('resolves the website and vault credential for the current user', async () => {
  const websites = [
    {
      id: 'website-1',
      workspaceId: 'default',
      name: '产品能力集管理系统',
      url: 'http://172.16.32.34:8087/index.html#/productCapabilityList/maf',
    },
  ]
  assert.equal(selectWebsite(websites).id, 'website-1')
  assert.equal(
    selectCredential(
      [
        {
          id: 7,
          name: '能力集管理员',
          username: 'admin',
          url: 'http://172.16.32.34:8087/index.html',
        },
      ],
      websites[0],
    ).id,
    7,
  )

  const resources = await resolveCapabilityResources({
    apiRequest: async (pathname) => {
      if (pathname === '/auth/me') {
        return { email: 'Panyong@example.com' }
      }
      if (pathname === '/development/user-state') {
        return {
          'workcove.workbench.shortcuts.v1': JSON.stringify({
            'panyong@example.com': { websites },
          }),
        }
      }
      if (pathname.startsWith('/vault/entries?')) {
        return [
          {
            id: 7,
            name: '能力集管理员',
            username: 'admin',
            url: 'http://172.16.32.34:8087/index.html',
          },
        ]
      }
      if (pathname.startsWith('/vault/entries/7/secret')) {
        return { password: 'secret' }
      }
      throw new Error(`Unexpected path: ${pathname}`)
    },
  })
  assert.equal(resources.website.id, 'website-1')
  assert.equal(resources.credential.username, 'admin')
  assert.equal(resources.password, 'secret')
})

test('saves, downloads, validates, and installs a capability package', async () => {
  const root = await fs.mkdtemp(
    path.join(os.tmpdir(), 'workcove-capability-set-'),
  )
  const targetPath = path.join(
    root,
    'repo',
    'server',
    'apps',
    'aisg',
    'src',
    'main',
    'resources',
    'capa',
    'CAPA_CONFIG_AISG.json',
  )
  const backupDirectory = path.join(root, 'backups')
  const zipDirectory = path.join(root, 'zip')
  let server
  try {
    await fs.mkdir(path.dirname(targetPath), { recursive: true })
    await fs.mkdir(zipDirectory, { recursive: true })
    const oldContent = Buffer.alloc(1024, 1).toString('base64')
    const newContent = Buffer.alloc(1536, 2).toString('base64')
    await fs.writeFile(targetPath, oldContent)
    await fs.writeFile(
      path.join(zipDirectory, 'CAPA_CONFIG_MAF.json'),
      newContent,
    )
    execFileSync(
      'zip',
      ['-q', 'capability.zip', 'CAPA_CONFIG_MAF.json'],
      { cwd: zipDirectory },
    )
    const zipBuffer = await fs.readFile(
      path.join(zipDirectory, 'capability.zip'),
    )

    let saved = false
    let saveCalls = 0
    let authenticated = false
    server = http.createServer(async (request, response) => {
      const url = new URL(request.url || '/', 'http://127.0.0.1')
      if (url.pathname === '/login' && request.method === 'POST') {
        const body = JSON.parse(await readBody(request))
        assert.equal(body.username, 'admin')
        assert.equal(body.password, 'secret')
        authenticated = true
        response.setHeader('Content-Type', 'application/json')
        response.end(
          JSON.stringify({
            status: 0,
            data: { token: 'capability-token' },
          }),
        )
        return
      }
      if (url.pathname === '/config/page') {
        assert.equal(request.headers.token, 'capability-token')
        const records = saved
          ? [
              {
                id: 2,
                fileName: 'CAPA_CONFIG_MAF-2.zip',
                filePath: '/packages/2.zip',
                createTime: '2026-10-29 16:00:00',
              },
            ]
          : [
              {
                id: 1,
                fileName: 'CAPA_CONFIG_MAF-1.zip',
                filePath: '/packages/1.zip',
                createTime: '2026-10-29 15:00:00',
              },
            ]
        response.setHeader('Content-Type', 'application/json')
        response.end(
          JSON.stringify({
            status: 0,
            data: {
              rows: [
                {
                  id: 11,
                  name: 'AISG_V3.0.2_R100',
                  records,
                },
              ],
            },
          }),
        )
        return
      }
      if (url.pathname === '/config/detail') {
        response.setHeader('Content-Type', 'application/json')
        response.end(
          JSON.stringify({
            status: 0,
            data: {
              id: 11,
              name: 'AISG_V3.0.2_R100',
              description: 'test',
              menus: [{ id: 101, orderNo: 1, child: [] }],
            },
          }),
        )
        return
      }
      if (url.pathname === '/config/save' && request.method === 'POST') {
        const body = JSON.parse(await readBody(request))
        assert.equal(body.id, 11)
        assert.equal(body.productLine, 'maf')
        assert.equal(body.config.menus[0].id, 101)
        saveCalls += 1
        saved = true
        response.setHeader('Content-Type', 'application/json')
        response.end(JSON.stringify({ status: 0 }))
        return
      }
      if (url.pathname === '/file/download' && request.method === 'POST') {
        const body = JSON.parse(await readBody(request))
        assert.equal(body.filePath, '/packages/2.zip')
        response.setHeader('Content-Type', 'application/zip')
        response.end(zipBuffer)
        return
      }
      response.statusCode = 404
      response.end(JSON.stringify({ status: 1, msg: 'not found' }))
    })
    const siteBase = await listen(server)
    const siteUrl = `${siteBase}index.html#/productCapabilityList/maf`
    const preview = await updateCapabilitySet({
      siteUrl,
      username: 'admin',
      password: 'secret',
      branch: 'feature_V3.0.2_R100',
      repoPath: path.join(root, 'repo'),
      backupDirectory,
      confirm: false,
    })
    assert.equal(preview.status, 'preview')
    assert.equal(preview.capabilityName, 'AISG_V3.0.2_R100')
    assert.equal(saveCalls, 0)

    const updated = await updateCapabilitySet({
      siteUrl,
      username: 'admin',
      password: 'secret',
      branch: 'feature_V3.0.2_R100',
      repoPath: path.join(root, 'repo'),
      backupDirectory,
      confirm: true,
    })
    assert.equal(authenticated, true)
    assert.equal(saveCalls, 1)
    assert.equal(updated.status, 'updated')
    assert.equal(updated.packageEntry, 'CAPA_CONFIG_MAF.json')
    assert.equal(await fs.readFile(targetPath, 'utf8'), newContent)
    validateCapabilityConfigContent(await fs.readFile(targetPath))
    assert.equal(
      (await fs.readdir(backupDirectory)).some((name) =>
        name.startsWith('CAPA_CONFIG_AISG-'),
      ),
      true,
    )
  } finally {
    if (server) {
      await new Promise((resolve) => server.close(resolve))
    }
    await fs.rm(root, { recursive: true, force: true })
  }
})
