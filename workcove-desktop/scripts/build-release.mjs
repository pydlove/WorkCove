import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  buildJre,
  buildThinBackend,
  createRuntimeArchive,
  resolveClientConfigFile,
  run,
  sha256File,
  stageRuntime,
  stageShell,
} from './runtime-package.mjs'

const desktopDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
)
const projectDirectory = path.resolve(desktopDirectory, '..')
const packageJson = JSON.parse(
  fs.readFileSync(path.join(desktopDirectory, 'package.json'), 'utf8'),
)
const releaseInfo = JSON.parse(
  fs.readFileSync(path.join(desktopDirectory, 'release-info.json'), 'utf8'),
)
const version = process.env.WORKCOVE_VERSION?.trim() || packageJson.version
const releaseCode =
  process.env.WORKCOVE_RELEASE_CODE?.trim() || releaseInfo.releaseCode
const shellRelease =
  process.env.WORKCOVE_SHELL_RELEASE?.trim() ||
  releaseInfo.shellRelease ||
  releaseInfo.releaseCode
if (!/^R\d+B\d+$/.test(releaseCode)) {
  throw new Error(`发布码格式无效：${releaseCode}`)
}
if (!/^R\d+B\d+$/.test(shellRelease)) {
  throw new Error(`客户端壳发布码格式无效：${shellRelease}`)
}
const arch = process.env.WORKCOVE_MAC_ARCH?.trim() || process.arch
if (!['arm64', 'x64'].includes(arch)) {
  throw new Error('当前构建只支持与本机 JDK 一致的 arm64 或 x64')
}
const artifactVersion = `${version}-${releaseCode}`
const outputDirectory = path.join(desktopDirectory, 'dist')
const workDirectory = path.join(outputDirectory, 'work')
const runtimeDirectory = path.join(workDirectory, 'runtime')
const shellDirectory = path.join(workDirectory, 'shell')
const buildDirectory = path.join(workDirectory, 'build')
const libDirectory = path.join(workDirectory, 'backend-lib')
const jreDirectory = path.join(workDirectory, 'jre')
const electronPackager = path.join(
  desktopDirectory,
  'node_modules',
  '.bin',
  'electron-packager',
)
const electronVersion = JSON.parse(
  fs.readFileSync(
    path.join(
      desktopDirectory,
      'node_modules',
      'electron',
      'package.json',
    ),
    'utf8',
  ),
).version

function findCachedElectronZip(filename) {
  const roots = [
    process.env.ELECTRON_CACHE,
    path.join(os.homedir(), 'Library', 'Caches', 'electron'),
    path.join(os.homedir(), '.cache', 'electron'),
  ].filter(Boolean)
  for (const root of roots) {
    if (!fs.existsSync(root)) continue
    const pending = [root]
    while (pending.length > 0) {
      const directory = pending.pop()
      for (const entry of fs.readdirSync(directory, {
        withFileTypes: true,
      })) {
        const fullPath = path.join(directory, entry.name)
        if (entry.isDirectory()) pending.push(fullPath)
        if (entry.isFile() && entry.name === filename) return fullPath
      }
    }
  }
  return null
}

function electronZipDirectory() {
  const filename = `electron-v${electronVersion}-darwin-${arch}.zip`
  const source = findCachedElectronZip(filename)
  if (!source) return null
  const destinationDirectory = path.join(workDirectory, 'electron-zips')
  fs.mkdirSync(destinationDirectory, { recursive: true })
  try {
    fs.symlinkSync(source, path.join(destinationDirectory, filename))
  } catch {
    fs.copyFileSync(source, path.join(destinationDirectory, filename))
  }
  return destinationDirectory
}

function buildWeb() {
  run('pnpm', ['build'], {
    cwd: path.join(projectDirectory, 'workcove-web'),
  })
  return path.join(projectDirectory, 'workcove-web', 'dist')
}

function packageMac(zipDirectory) {
  if (!fs.existsSync(electronPackager)) {
    throw new Error('缺少 @electron/packager，请先安装桌面端依赖')
  }
  const macOutput = path.join(workDirectory, 'mac')
  const runtimeResource = path.join(workDirectory, 'runtime')
  run(
    electronPackager,
    [
      shellDirectory,
      'WorkCove',
      '--platform=darwin',
      `--arch=${arch}`,
      `--out=${macOutput}`,
      '--overwrite',
      '--asar',
      '--app-bundle-id=com.pydlove.workcove',
      `--app-version=${version}`,
      `--build-version=${version}`,
      `--icon=${path.join(desktopDirectory, 'assets', 'WorkCove.icns')}`,
      `--extra-resource=${runtimeResource}`,
      `--extra-resource=${jreDirectory}`,
      `--extra-resource=${libDirectory}`,
      `--extra-resource=${path.join(projectDirectory, 'deploy', 'mysql', 'setup-client-db.sql')}`,
      ...(zipDirectory ? [`--electron-zip-dir=${zipDirectory}`] : []),
      '--ignore=^/dist',
      '--ignore=^/scripts',
      '--ignore=^/tests',
      '--ignore=^/README\\.md$',
    ],
    {
      env: {
        ELECTRON_MIRROR:
          process.env.ELECTRON_MIRROR ||
          'https://npmmirror.com/mirrors/electron/',
      },
    },
  )
  return path.join(macOutput, `WorkCove-darwin-${arch}`, 'WorkCove.app')
}

function buildDmg(application) {
  const plist = path.join(application, 'Contents', 'Info.plist')
  run('plutil', [
    '-replace',
    'CFBundleDisplayName',
    '-string',
    '8小时工作台',
    plist,
  ])
  run('plutil', [
    '-replace',
    'NSHumanReadableCopyright',
    '-string',
    '© 2026 panyong',
    plist,
  ])
  run('codesign', ['--force', '--deep', '--sign', '-', application])
  const stage = path.join(workDirectory, 'dmg-stage')
  fs.rmSync(stage, { recursive: true, force: true })
  fs.mkdirSync(stage, { recursive: true })
  const stagedApplication = path.join(stage, 'WorkCove.app')
  run('ditto', [application, stagedApplication])
  run('codesign', ['--force', '--deep', '--sign', '-', stagedApplication])
  run('codesign', [
    '--verify',
    '--deep',
    '--strict',
    '--verbose=2',
    stagedApplication,
  ])
  fs.symlinkSync('/Applications', path.join(stage, 'Applications'))
  fs.copyFileSync(
    path.join(
      projectDirectory,
      'deploy',
      'mysql',
      'setup-client-db.sql',
    ),
    path.join(stage, '初始化客户端数据库.sql'),
  )
  fs.writeFileSync(
    path.join(stage, '安装说明.txt'),
    `8小时工作台 ${artifactVersion}\n\n` +
      '1. 将 WorkCove.app 拖到 Applications。\n' +
      '2. 使用 MySQL 管理员执行“初始化客户端数据库.sql”，仅需一次。\n' +
      '3. 首次打开如被 macOS 拦截，请在 Finder 中右键后选择“打开”。\n' +
      '4. 客户端与开发版使用独立数据库、端口和本地目录，可同时运行。\n',
    'utf8',
  )
  const dmg = path.join(
    outputDirectory,
    `WorkCove-${artifactVersion}-macOS-${arch}.dmg`,
  )
  fs.rmSync(dmg, { force: true })
  run('hdiutil', [
    'create',
    '-volname',
    `WorkCove ${artifactVersion}`,
    '-srcfolder',
    stage,
    '-ov',
    '-format',
    'UDZO',
    dmg,
  ])
  return dmg
}

fs.rmSync(outputDirectory, { recursive: true, force: true })
fs.mkdirSync(workDirectory, { recursive: true })

const configuredRendererDirectory =
  process.env.WORKCOVE_RENDERER_DIR?.trim()
const rendererDirectory = configuredRendererDirectory
  ? path.resolve(configuredRendererDirectory)
  : buildWeb()
if (
  configuredRendererDirectory &&
  !fs.existsSync(path.join(rendererDirectory, 'index.html'))
) {
  throw new Error(`指定的前端资源目录无效：${rendererDirectory}`)
}
const backend = buildThinBackend(projectDirectory, buildDirectory)
fs.cpSync(backend.librariesDirectory, libDirectory, { recursive: true })
buildJre(jreDirectory)

stageRuntime({
  destination: runtimeDirectory,
  rendererDirectory,
  backendJar: backend.thinJar,
  releaseCode,
  clientConfigFile: resolveClientConfigFile(projectDirectory),
  minShellRelease:
    process.env.WORKCOVE_MIN_SHELL_RELEASE?.trim() ||
    shellRelease,
})
const runtimeArchive = path.join(
  outputDirectory,
  `WorkCove-${artifactVersion}-runtime.zip`,
)
createRuntimeArchive(runtimeDirectory, runtimeArchive)

stageShell({
  projectDirectory: desktopDirectory,
  destination: shellDirectory,
  version,
  releaseCode,
  shellRelease,
})
const macApplication = packageMac(electronZipDirectory())
const dmg = buildDmg(macApplication)

const latest = {
  schemaVersion: 1,
  version,
  releaseCode,
  releasedAt: new Date().toISOString().slice(0, 10),
  notes: process.env.WORKCOVE_RELEASE_NOTES?.trim() ||
    '包含最近的功能更新和问题修复。',
  runtime: {
    releaseCode,
    minShellRelease:
      process.env.WORKCOVE_MIN_SHELL_RELEASE?.trim() ||
      shellRelease,
    url: `/downloads/${path.basename(runtimeArchive)}`,
    sha256: sha256File(runtimeArchive),
    sizeBytes: fs.statSync(runtimeArchive).size,
  },
  downloads: {
    macos: {
      url: `/downloads/${path.basename(dmg)}`,
      sha256: sha256File(dmg),
      sizeBytes: fs.statSync(dmg).size,
    },
  },
}
fs.writeFileSync(
  path.join(outputDirectory, 'latest.json'),
  `${JSON.stringify(latest, null, 2)}\n`,
  'utf8',
)

console.log('\n客户端发布产物：')
console.log(`macOS: ${dmg}`)
console.log(`Runtime: ${runtimeArchive}`)
console.log(`清单: ${path.join(outputDirectory, 'latest.json')}`)
console.log(`瘦 JAR: ${(backend.thinJarSize / 1024).toFixed(1)} KB`)
