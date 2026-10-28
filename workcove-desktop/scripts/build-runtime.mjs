import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  buildThinBackend,
  createRuntimeArchive,
  run,
  sha256File,
  stageRuntime,
} from './runtime-package.mjs'

const desktopDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
)
const projectDirectory = path.resolve(desktopDirectory, '..')
const packageJson = JSON.parse(
  fs.readFileSync(path.join(desktopDirectory, 'package.json'), 'utf8'),
)
const releaseCode = process.env.WORKCOVE_RELEASE_CODE?.trim()
const releaseInfo = JSON.parse(
  fs.readFileSync(path.join(desktopDirectory, 'release-info.json'), 'utf8'),
)
if (!releaseCode || !/^R\d+B\d+$/.test(releaseCode)) {
  throw new Error('请设置有效的 WORKCOVE_RELEASE_CODE，例如 R100B002')
}
const outputDirectory = path.join(desktopDirectory, 'dist')
const workDirectory = path.join(outputDirectory, 'work-runtime')
const buildDirectory = path.join(workDirectory, 'build')
const runtimeDirectory = path.join(workDirectory, 'runtime')

fs.rmSync(workDirectory, { recursive: true, force: true })
fs.mkdirSync(outputDirectory, { recursive: true })
const configuredRendererDirectory =
  process.env.WORKCOVE_RENDERER_DIR?.trim()
const rendererDirectory = configuredRendererDirectory
  ? path.resolve(configuredRendererDirectory)
  : path.join(projectDirectory, 'workcove-web', 'dist')
if (
  configuredRendererDirectory &&
  !fs.existsSync(path.join(rendererDirectory, 'index.html'))
) {
  throw new Error(`指定的前端资源目录无效：${rendererDirectory}`)
}
if (
  !configuredRendererDirectory &&
  process.env.WORKCOVE_SKIP_WEB_BUILD !== '1'
) {
  run('pnpm', ['build'], {
    cwd: path.join(projectDirectory, 'workcove-web'),
  })
}
const configuredBackendJar = process.env.WORKCOVE_BACKEND_JAR?.trim()
let backend
if (configuredBackendJar) {
  const thinJar = path.resolve(configuredBackendJar)
  if (!fs.existsSync(thinJar)) {
    throw new Error(`指定的后端 JAR 无效：${thinJar}`)
  }
  backend = {
    thinJar,
    thinJarSize: fs.statSync(thinJar).size,
  }
} else {
  backend = buildThinBackend(projectDirectory, buildDirectory)
}
stageRuntime({
  destination: runtimeDirectory,
  rendererDirectory,
  backendJar: backend.thinJar,
  releaseCode,
  minShellRelease:
    process.env.WORKCOVE_MIN_SHELL_RELEASE?.trim() ||
    releaseInfo.shellRelease ||
    releaseInfo.releaseCode,
})
const archive = path.join(
  outputDirectory,
  `WorkCove-${packageJson.version}-${releaseCode}-runtime.zip`,
)
createRuntimeArchive(runtimeDirectory, archive)
const manifestPath = path.join(outputDirectory, 'latest.json')
let latest = {}
try {
  latest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
} catch {
  // The first Runtime-only build can create a standalone manifest.
}
const minShellRelease =
  process.env.WORKCOVE_MIN_SHELL_RELEASE?.trim() ||
  releaseInfo.shellRelease ||
  releaseInfo.releaseCode
const runtime = {
  releaseCode,
  minShellRelease,
  url: `/downloads/${path.basename(archive)}`,
  sha256: sha256File(archive),
  sizeBytes: fs.statSync(archive).size,
}
fs.writeFileSync(
  manifestPath,
  `${JSON.stringify(
    {
      ...latest,
      schemaVersion: 1,
      version: packageJson.version,
      releaseCode,
      releasedAt: new Date().toISOString().slice(0, 10),
      notes:
        process.env.WORKCOVE_RELEASE_NOTES?.trim() ||
        latest.notes ||
        '包含最近的功能更新和问题修复。',
      runtime,
    },
    null,
    2,
  )}\n`,
  'utf8',
)
console.log(
  JSON.stringify({
    archive,
    sizeBytes: fs.statSync(archive).size,
    sha256: runtime.sha256,
    thinJarBytes: backend.thinJarSize,
    manifestPath,
  }, null, 2),
)
