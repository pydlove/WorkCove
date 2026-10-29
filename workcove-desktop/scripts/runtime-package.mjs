import { spawnSync } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

export const MAIN_CLASS =
  'com.pydlove.workcove.boot.WorkCoveApplication'

export function run(command, args, options = {}) {
  console.log(`\n$ ${command} ${args.join(' ')}`)
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    stdio: 'inherit',
    env: { ...process.env, ...options.env },
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    throw new Error(`${command} 执行失败，退出码 ${result.status}`)
  }
}

export function findFile(directory, pattern) {
  const files = fs
    .readdirSync(directory)
    .filter((filename) => pattern.test(filename))
    .map((filename) => path.join(directory, filename))
    .sort()
  if (files.length === 0) {
    throw new Error(`目录中没有匹配文件：${directory} ${pattern}`)
  }
  return files[0]
}

export function sha256File(filename) {
  return crypto
    .createHash('sha256')
    .update(fs.readFileSync(filename))
    .digest('hex')
}

export function resolveClientConfigFile(projectDirectory) {
  const configured = process.env.WORKCOVE_CLIENT_CONFIG_FILE?.trim()
  const filename = configured
    ? path.resolve(configured)
    : path.join(
        projectDirectory,
        'workcove-boot',
        'src',
        'main',
        'resources',
        'application-local.yml',
      )
  if (!fs.existsSync(filename) || !fs.statSync(filename).isFile()) {
    throw new Error(
      `缺少客户端运行配置：${filename}。请通过 WORKCOVE_CLIENT_CONFIG_FILE 指定加密配置。`,
    )
  }
  return filename
}

export function stageRuntime({
  destination,
  rendererDirectory,
  backendJar,
  releaseCode,
  minShellRelease,
  clientConfigFile,
}) {
  fs.rmSync(destination, { recursive: true, force: true })
  fs.mkdirSync(path.join(destination, 'backend'), { recursive: true })
  fs.cpSync(
    rendererDirectory,
    path.join(destination, 'renderer'),
    { recursive: true },
  )
  fs.copyFileSync(
    backendJar,
    path.join(destination, 'backend', 'workcove-boot.jar'),
  )
  const configDirectory = path.join(destination, 'config')
  fs.mkdirSync(configDirectory, { recursive: true })
  fs.copyFileSync(
    clientConfigFile,
    path.join(configDirectory, 'application-local.yml'),
  )
  fs.writeFileSync(
    path.join(destination, 'runtime-manifest.json'),
    `${JSON.stringify(
      {
        schemaVersion: 1,
        releaseCode,
        minShellRelease: minShellRelease || null,
        builtAt: new Date().toISOString(),
      },
      null,
      2,
    )}\n`,
    'utf8',
  )
  return destination
}

export function createRuntimeArchive(runtimeDirectory, destination) {
  fs.rmSync(destination, { force: true })
  run(
    'ditto',
    [
      '-c',
      '-k',
      '--norsrc',
      '--noextattr',
      '.',
      destination,
    ],
    { cwd: runtimeDirectory },
  )
  return destination
}

export function stageShell({
  projectDirectory,
  destination,
  version,
  releaseCode,
  shellRelease,
}) {
  fs.rmSync(destination, { recursive: true, force: true })
  fs.mkdirSync(destination, { recursive: true })
  const shellFiles = fs.readdirSync(projectDirectory, {
    withFileTypes: true,
  })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.js'))
    .map((entry) => entry.name)
  for (const filename of ['main.js', 'preload.js']) {
    if (!shellFiles.includes(filename)) {
      throw new Error(`客户端壳缺少必需文件：${filename}`)
    }
  }
  for (const filename of shellFiles) {
    fs.copyFileSync(
      path.join(projectDirectory, filename),
      path.join(destination, filename),
    )
  }
  fs.cpSync(
    path.join(projectDirectory, 'assets'),
    path.join(destination, 'assets'),
    { recursive: true },
  )
  fs.writeFileSync(
    path.join(destination, 'release-info.json'),
    `${JSON.stringify(
      {
        version,
        releaseCode,
        shellRelease,
        releasedAt: new Date().toISOString().slice(0, 10),
      },
      null,
      2,
    )}\n`,
    'utf8',
  )
  fs.writeFileSync(
    path.join(destination, 'package.json'),
    `${JSON.stringify(
      {
        name: 'workcove',
        productName: 'WorkCove',
        version,
        description: '8小时工作台',
        main: 'main.js',
        private: true,
      },
      null,
      2,
    )}\n`,
    'utf8',
  )
  return destination
}

export function buildThinBackend(projectDirectory, outputDirectory) {
  fs.rmSync(outputDirectory, { recursive: true, force: true })
  fs.mkdirSync(outputDirectory, { recursive: true })

  run(
    'mvn',
    [
      '-q',
      '-pl',
      'workcove-boot',
      '-am',
      'clean',
      'install',
      '-Dmaven.test.skip=true',
      '-Dspring-boot.repackage.skip=true',
    ],
    { cwd: projectDirectory },
  )

  const backendJar = findFile(
    path.join(projectDirectory, 'workcove-boot', 'target'),
    /^workcove-boot-.*\.jar$/,
  )
  const librariesDirectory = path.join(outputDirectory, 'backend-lib')
  fs.mkdirSync(librariesDirectory, { recursive: true })
  run(
    'mvn',
    [
      '-q',
      '-f',
      'workcove-boot/pom.xml',
      'dependency:copy-dependencies',
      '-DincludeScope=runtime',
      `-DoutputDirectory=${librariesDirectory}`,
    ],
    { cwd: projectDirectory },
  )

  const thinJar = path.join(outputDirectory, 'workcove-boot.jar')
  fs.copyFileSync(backendJar, thinJar)
  run(
    'mvn',
    [
      '-q',
      '-pl',
      'workcove-boot',
      '-am',
      'package',
      '-Dmaven.test.skip=true',
    ],
    { cwd: projectDirectory },
  )
  return {
    thinJar,
    librariesDirectory,
    thinJarSize: fs.statSync(thinJar).size,
  }
}

export function buildJre(outputDirectory) {
  fs.rmSync(outputDirectory, { recursive: true, force: true })
  const javaHome = process.env.JAVA_HOME?.trim()
    || spawnSync('/usr/libexec/java_home', [], {
      encoding: 'utf8',
    }).stdout?.trim()
  if (!javaHome) {
    throw new Error('无法定位 JDK，请设置 JAVA_HOME')
  }
  const jlink = path.join(javaHome, 'bin', 'jlink')
  const modules = path.join(javaHome, 'jmods')
  if (!fs.existsSync(jlink) || !fs.existsSync(modules)) {
    throw new Error('当前 JDK 缺少 jlink 或 jmods')
  }
  run(jlink, [
    '--module-path',
    modules,
    '--add-modules',
    [
      'java.base',
      'java.compiler',
      'java.desktop',
      'java.instrument',
      'java.management',
      'java.naming',
      'java.net.http',
      'java.prefs',
      'java.rmi',
      'java.scripting',
      'java.security.jgss',
      'java.security.sasl',
      'java.sql',
      'java.sql.rowset',
      'java.transaction.xa',
      'java.xml',
      'jdk.attach',
      'jdk.charsets',
      'jdk.crypto.ec',
      'jdk.jdi',
      'jdk.jfr',
      'jdk.localedata',
      'jdk.management',
      'jdk.naming.dns',
      'jdk.unsupported',
      'jdk.zipfs',
    ].join(','),
    '--strip-debug',
    '--no-header-files',
    '--no-man-pages',
    '--compress=2',
    '--output',
    outputDirectory,
  ])
  materializeSymlinks(outputDirectory)
  return outputDirectory
}

export function materializeSymlinks(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isSymbolicLink()) {
      const targetPath = fs.realpathSync(entryPath)
      const targetStats = fs.statSync(targetPath)
      fs.rmSync(entryPath, { force: true })
      if (targetStats.isDirectory()) {
        fs.cpSync(targetPath, entryPath, {
          recursive: true,
          dereference: true,
          preserveTimestamps: true,
        })
        materializeSymlinks(entryPath)
      } else {
        fs.copyFileSync(targetPath, entryPath)
        fs.chmodSync(entryPath, targetStats.mode & 0o777)
      }
      continue
    }
    if (entry.isDirectory()) {
      materializeSymlinks(entryPath)
    }
  }
}
