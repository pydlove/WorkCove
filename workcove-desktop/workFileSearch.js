const fs = require('node:fs/promises')
const path = require('node:path')

const DEFAULT_MAX_RESULTS = 8
const DEFAULT_MAX_ENTRIES = 20_000
const DEFAULT_MAX_DEPTH = 8
const DEFAULT_TIMEOUT_MS = 3_500
const MAX_ROOTS = 16
const IGNORED_DIRECTORIES = new Set([
  '.git',
  '.idea',
  '.next',
  '.nuxt',
  '.output',
  '.turbo',
  '.venv',
  '.yarn',
  'build',
  'coverage',
  'dist',
  'node_modules',
  'out',
  'target',
  'vendor',
])

function normalizeRoots(rawRoots) {
  if (!Array.isArray(rawRoots)) return []
  return [
    ...new Set(
      rawRoots
        .filter((value) => typeof value === 'string')
        .map((value) => value.trim())
        .filter((value) => value && path.isAbsolute(value))
        .slice(0, MAX_ROOTS),
    ),
  ]
}

function resultScore(name, relativePath, query) {
  const normalizedName = normalizeSearchText(name)
  const normalizedPath = normalizeSearchText(relativePath)
  if (normalizedName === query) return 0
  if (normalizedName.startsWith(query)) return 1
  if (normalizedName.includes(query)) return 2
  if (normalizedPath.includes(query)) return 3
  return 4
}

function normalizeSearchText(value) {
  return String(value)
    .normalize('NFKC')
    .toLocaleLowerCase('zh-CN')
    .replace(/[\s\-_·]+/g, '')
}

async function searchWorkDirectoryFiles(rawRoots, rawQuery, options = {}) {
  const query = String(rawQuery || '').trim()
  if (!query) return []
  if (query.length > 120) throw new Error('搜索关键词过长')

  const normalizedQuery = normalizeSearchText(query)
  const maxResults = Math.max(
    1,
    Number(options.maxResults) || DEFAULT_MAX_RESULTS,
  )
  const maxEntries = Math.max(
    100,
    Number(options.maxEntries) || DEFAULT_MAX_ENTRIES,
  )
  const maxDepth = Math.max(
    1,
    Number(options.maxDepth) || DEFAULT_MAX_DEPTH,
  )
  const timeoutMs = Math.max(
    100,
    Number(options.timeoutMs) || DEFAULT_TIMEOUT_MS,
  )
  const deadline = Date.now() + timeoutMs
  const matches = []
  let scannedEntries = 0

  const roots = []
  for (const value of normalizeRoots(rawRoots)) {
    try {
      const realRoot = await fs.realpath(value)
      const stats = await fs.stat(realRoot)
      if (stats.isDirectory()) roots.push(realRoot)
    } catch {
      // Ignore directories that disappeared or cannot be accessed.
    }
  }

  for (const root of roots) {
    if (Date.now() >= deadline || scannedEntries >= maxEntries) break
    const rootName = path.basename(root) || root
    const pending = [{ directory: root, depth: 0 }]

    while (
      pending.length > 0 &&
      Date.now() < deadline &&
      scannedEntries < maxEntries
    ) {
      const current = pending.pop()
      let directory
      try {
        directory = await fs.opendir(current.directory)
      } catch {
        continue
      }
      try {
        for await (const entry of directory) {
          scannedEntries += 1
          if (scannedEntries >= maxEntries || Date.now() >= deadline) break
          if (entry.isSymbolicLink()) continue

          const targetPath = path.join(current.directory, entry.name)
          if (entry.isDirectory()) {
            if (
              current.depth < maxDepth &&
              !entry.name.startsWith('.') &&
              !IGNORED_DIRECTORIES.has(entry.name)
            ) {
              pending.push({
                directory: targetPath,
                depth: current.depth + 1,
              })
            }
            continue
          }
          if (!entry.isFile()) continue

          const relativePath = path.relative(root, targetPath)
          const searchable = normalizeSearchText(
            `${entry.name}\n${relativePath}`,
          )
          if (!searchable.includes(normalizedQuery)) continue
          matches.push({
            name: entry.name,
            path: targetPath,
            rootName,
            relativePath,
            score: resultScore(entry.name, relativePath, normalizedQuery),
          })
        }
      } finally {
        if (typeof directory.close === 'function') {
          await directory.close().catch(() => undefined)
        }
      }
    }
  }

  return matches
    .sort(
      (left, right) =>
        left.score - right.score ||
        left.relativePath.length - right.relativePath.length ||
        left.relativePath.localeCompare(right.relativePath, 'zh-CN'),
    )
    .slice(0, maxResults)
    .map(({ score, ...result }) => result)
}

async function resolveWorkFile(rawTargetPath, rawRoots) {
  if (
    typeof rawTargetPath !== 'string' ||
    !rawTargetPath.trim() ||
    !path.isAbsolute(rawTargetPath)
  ) {
    throw new Error('文件路径无效')
  }
  const targetPath = await fs.realpath(rawTargetPath)
  const stats = await fs.stat(targetPath)
  if (!stats.isFile()) throw new Error('目标不是文件')

  for (const value of normalizeRoots(rawRoots)) {
    let root
    try {
      root = await fs.realpath(value)
    } catch {
      continue
    }
    const relative = path.relative(root, targetPath)
    if (
      relative &&
      !relative.startsWith(`..${path.sep}`) &&
      relative !== '..' &&
      !path.isAbsolute(relative)
    ) {
      return targetPath
    }
  }
  throw new Error('文件不在已配置的工作目录中')
}

module.exports = {
  searchWorkDirectoryFiles,
  resolveWorkFile,
}
