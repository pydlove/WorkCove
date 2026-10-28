const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const RELEASE_PATTERN = /^R(\d+)B(\d+)$/

function releaseRank(value) {
  const match = String(value || '').match(RELEASE_PATTERN)
  if (!match) return null
  return Number(match[1]) * 1_000_000 + Number(match[2])
}

function validReleaseCode(value) {
  return typeof value === 'string' && RELEASE_PATTERN.test(value)
}

function sha256(content) {
  return crypto.createHash('sha256').update(content).digest('hex')
}

class RuntimeManager {
  constructor({
    userData,
    builtinDirectory,
    builtinRelease,
    shellRelease = builtinRelease,
    platform = process.platform,
    now = () => new Date(),
  }) {
    this.userData = userData
    this.builtinDirectory = builtinDirectory
    this.builtinRelease = builtinRelease
    this.shellRelease = shellRelease
    this.platform = platform
    this.now = now
    this.runtimeRoot = path.join(userData, 'runtime')
    this.releasesDirectory = path.join(this.runtimeRoot, 'releases')
    this.stateFile = path.join(this.runtimeRoot, 'state.json')
    this.logFile = path.join(this.runtimeRoot, 'bootstrap.log')
  }

  log(message) {
    try {
      fs.mkdirSync(this.runtimeRoot, { recursive: true })
      fs.appendFileSync(
        this.logFile,
        `[${this.now().toISOString()}] ${message}\n`,
        'utf8',
      )
    } catch {
      // Startup logging must never prevent the client from opening.
    }
  }

  emptyState() {
    return {
      schemaVersion: 1,
      current: null,
      previous: null,
      failed: null,
      pending: false,
      attempts: 0,
    }
  }

  readState() {
    try {
      const value = JSON.parse(fs.readFileSync(this.stateFile, 'utf8'))
      return {
        schemaVersion: 1,
        current: validReleaseCode(value.current) ? value.current : null,
        previous: validReleaseCode(value.previous) ? value.previous : null,
        failed: validReleaseCode(value.failed) ? value.failed : null,
        pending: value.pending === true,
        attempts:
          Number.isInteger(value.attempts) && value.attempts >= 0
            ? value.attempts
            : 0,
      }
    } catch {
      return this.emptyState()
    }
  }

  writeState(state) {
    fs.mkdirSync(this.runtimeRoot, { recursive: true })
    const temporary = `${this.stateFile}.tmp-${process.pid}-${Date.now()}`
    fs.writeFileSync(
      temporary,
      `${JSON.stringify(state, null, 2)}\n`,
      'utf8',
    )
    try {
      fs.renameSync(temporary, this.stateFile)
    } catch {
      fs.copyFileSync(temporary, this.stateFile)
      fs.rmSync(temporary, { force: true })
    }
  }

  releaseDirectory(releaseCode) {
    if (!validReleaseCode(releaseCode)) {
      throw new Error(`Runtime 版本号不合法：${releaseCode}`)
    }
    return path.join(this.releasesDirectory, releaseCode)
  }

  directoryFor(state) {
    return state.current
      ? this.releaseDirectory(state.current)
      : this.builtinDirectory
  }

  releaseFor(state) {
    return state.current || this.builtinRelease
  }

  validateRuntimeDirectory(directory, expectedRelease) {
    if (
      !fs.existsSync(path.join(directory, 'renderer', 'index.html')) ||
      !fs.existsSync(path.join(directory, 'backend', 'workcove-boot.jar'))
    ) {
      return false
    }
    try {
      const manifest = JSON.parse(
        fs.readFileSync(
          path.join(directory, 'runtime-manifest.json'),
          'utf8',
        ),
      )
      const releaseMatches = (
        !expectedRelease ||
        !manifest.releaseCode ||
        manifest.releaseCode === expectedRelease
      )
      const minimum = releaseRank(manifest.minShellRelease)
      const shell = releaseRank(this.shellRelease)
      return (
        releaseMatches &&
        (minimum === null || (shell !== null && minimum <= shell))
      )
    } catch {
      return directory === this.builtinDirectory
    }
  }

  rollback(state, reason) {
    const previous = validReleaseCode(state.previous)
      ? state.previous
      : null
    const previousDirectory = previous
      ? this.releaseDirectory(previous)
      : this.builtinDirectory
    const hasPrevious = previous
      ? this.validateRuntimeDirectory(previousDirectory, previous)
      : this.validateRuntimeDirectory(
          this.builtinDirectory,
          this.builtinRelease,
        )
    const next = {
      ...state,
      current: hasPrevious ? previous : null,
      previous: null,
      failed: state.current || state.failed || null,
      pending: false,
      attempts: 0,
    }
    this.writeState(next)
    this.log(`Rolled back runtime: ${reason}`)
    return next
  }

  prepareStartup() {
    let state = this.readState()
    if (state.pending) {
      const currentDirectory = state.current
        ? this.releaseDirectory(state.current)
        : null
      const valid =
        currentDirectory &&
        this.validateRuntimeDirectory(currentDirectory, state.current)
      if (!valid || state.attempts >= 1) {
        state = this.rollback(
          state,
          valid ? 'previous-startup-not-confirmed' : 'runtime-missing',
        )
      } else {
        state.attempts += 1
        this.writeState(state)
      }
    }

    const activeRank = releaseRank(state.current)
    const builtinRank = releaseRank(this.builtinRelease)
    if (
      activeRank !== null &&
      builtinRank !== null &&
      builtinRank > activeRank &&
      this.validateRuntimeDirectory(
        this.builtinDirectory,
        this.builtinRelease,
      )
    ) {
      const staleRelease = state.current
      state = {
        ...state,
        current: null,
        previous: null,
        failed: null,
        pending: false,
        attempts: 0,
      }
      this.writeState(state)
      this.log(
        `Selected builtin runtime ${this.builtinRelease} over ${staleRelease}`,
      )
    }

    const directory = this.directoryFor(state)
    if (!this.validateRuntimeDirectory(directory, this.releaseFor(state))) {
      state = this.rollback(state, 'active-runtime-invalid')
    }
    return {
      state,
      directory: this.directoryFor(state),
      releaseCode: this.releaseFor(state),
    }
  }

  markHealthy() {
    const state = this.readState()
    if (!state.pending) return
    this.writeState({
      ...state,
      failed: null,
      pending: false,
      attempts: 0,
    })
    this.log(
      `Runtime ${state.current || this.builtinRelease} confirmed healthy`,
    )
  }

  extract(zipPath, destination) {
    if (this.platform === 'darwin') {
      execFileSync('ditto', ['-x', '-k', zipPath, destination], {
        stdio: 'ignore',
      })
      return
    }
    if (this.platform === 'win32') {
      const quote = (value) =>
        `'${String(value).replaceAll("'", "''")}'`
      execFileSync(
        'powershell.exe',
        [
          '-NoProfile',
          '-NonInteractive',
          '-Command',
          `Expand-Archive -LiteralPath ${quote(zipPath)} -DestinationPath ${quote(destination)} -Force`,
        ],
        { stdio: 'ignore' },
      )
      return
    }
    execFileSync('unzip', ['-q', zipPath, '-d', destination], {
      stdio: 'ignore',
    })
  }

  install(zipPath, descriptor) {
    const releaseCode = descriptor?.releaseCode
    if (!validReleaseCode(releaseCode)) {
      throw new Error('Runtime 发布版本号不合法')
    }
    const content = fs.readFileSync(zipPath)
    if (
      !descriptor.sha256 ||
      sha256(content) !== String(descriptor.sha256).toLowerCase()
    ) {
      throw new Error('Runtime 包 SHA-256 校验失败')
    }

    fs.mkdirSync(this.releasesDirectory, { recursive: true })
    const staging = path.join(
      this.runtimeRoot,
      `.staging-${releaseCode}-${process.pid}-${Date.now()}`,
    )
    fs.mkdirSync(staging, { recursive: true })
    try {
      this.extract(zipPath, staging)
      if (!this.validateRuntimeDirectory(staging, releaseCode)) {
        throw new Error('Runtime 包内容不完整或版本不一致')
      }

      const target = this.releaseDirectory(releaseCode)
      let replaced = null
      if (fs.existsSync(target)) {
        replaced = path.join(
          this.runtimeRoot,
          `.replaced-${releaseCode}-${Date.now()}`,
        )
        fs.renameSync(target, replaced)
      }
      try {
        fs.renameSync(staging, target)
      } catch (error) {
        if (replaced && fs.existsSync(replaced)) {
          fs.renameSync(replaced, target)
        }
        throw error
      }
      if (replaced) {
        fs.rmSync(replaced, { recursive: true, force: true })
      }

      const state = this.readState()
      this.writeState({
        schemaVersion: 1,
        current: releaseCode,
        previous: state.current,
        failed: null,
        pending: true,
        attempts: 0,
      })
      this.prune(state.current, releaseCode)
      this.log(`Installed runtime ${releaseCode}`)
      return target
    } catch (error) {
      fs.rmSync(staging, { recursive: true, force: true })
      throw error
    }
  }

  prune(previous, current) {
    const keep = new Set([previous, current].filter(Boolean))
    let entries
    try {
      entries = fs
        .readdirSync(this.releasesDirectory, { withFileTypes: true })
        .filter(
          (entry) => entry.isDirectory() && validReleaseCode(entry.name),
        )
        .sort(
          (left, right) =>
            releaseRank(right.name) - releaseRank(left.name),
        )
    } catch {
      return
    }
    for (const entry of entries) {
      if (keep.has(entry.name)) continue
      fs.rmSync(path.join(this.releasesDirectory, entry.name), {
        recursive: true,
        force: true,
      })
    }
  }
}

module.exports = {
  RuntimeManager,
  releaseRank,
  sha256,
  validReleaseCode,
}
