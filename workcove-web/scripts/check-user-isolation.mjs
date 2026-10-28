import { readFile, readdir } from 'node:fs/promises'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const sourceRoot = join(root, 'src')
const allowedFiles = new Set([
  'src/services/userScopedStorage.ts',
])
const forbiddenBrowserStorage = [
  'localStorage',
  'sessionStorage',
  'indexedDB',
]
const violations = []
let checkedFiles = 0

async function checkFile(path) {
  checkedFiles += 1
  const file = relative(root, path)
  if (allowedFiles.has(file)) return

  const content = await readFile(path, 'utf8')
  content.split('\n').forEach((line, index) => {
    const api = forbiddenBrowserStorage.find((name) =>
      line.includes(name),
    )
    if (api) {
      violations.push(
        `${file}:${index + 1} 禁止使用 ${api}，业务数据必须保存到 MySQL`,
      )
    }
  })
}

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  await Promise.all(
    entries.map(async (entry) => {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) {
        await walk(path)
        return
      }
      if (!['.ts', '.vue'].includes(extname(entry.name))) return

      await checkFile(path)
    }),
  )
}

await walk(sourceRoot)
await checkFile(join(root, 'index.html'))

if (violations.length > 0) {
  console.error('WorkCove user isolation check failed:\n')
  violations.forEach((violation) => console.error(`- ${violation}`))
  process.exit(1)
}

console.log(`WorkCove user isolation passed (${checkedFiles} files).`)
