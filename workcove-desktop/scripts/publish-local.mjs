import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const userApproved = process.argv.includes('--user-approved')
const environmentApproved =
  process.env.WORKCOVE_CLIENT_UPDATE_APPROVED === '1'
if (!userApproved || !environmentApproved) {
  console.error(
    [
      '拒绝发布：客户端更新必须由用户在当前对话中明确授权。',
      '普通任务只能在研发环境更新和验证，禁止自动更新本机客户端。',
      '获得明确授权后执行：',
      'WORKCOVE_CLIENT_UPDATE_APPROVED=1 pnpm publish:local -- --user-approved',
    ].join('\n'),
  )
  process.exit(1)
}

const desktopDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
)
const distDirectory = path.join(desktopDirectory, 'dist')
const manifestPath = path.join(distDirectory, 'latest.json')
if (!fs.existsSync(manifestPath)) {
  throw new Error('缺少 dist/latest.json，请先执行 pnpm build:release')
}
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
const targetDirectory =
  process.env.WORKCOVE_RELEASES_DIR?.trim() ||
  path.join(
    os.homedir(),
    'Library',
    'Application Support',
    'com.pydlove.workcove',
    'backend',
    'downloads',
  )
fs.mkdirSync(targetDirectory, { recursive: true })

const filenames = new Set(['latest.json'])
for (const descriptor of [
  manifest.runtime,
  manifest.downloads?.macos,
]) {
  if (descriptor?.url) filenames.add(path.basename(descriptor.url))
}
for (const filename of filenames) {
  fs.copyFileSync(
    path.join(distDirectory, filename),
    path.join(targetDirectory, filename),
  )
}
console.log(`已发布到本机客户端更新目录：${targetDirectory}`)
