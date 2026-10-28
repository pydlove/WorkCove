import { readFile, readdir } from 'node:fs/promises'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const sourceRoot = join(root, 'src')
const allowedExtensions = new Set(['.css', '.vue'])
const violations = []
let checkedFiles = 0

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  await Promise.all(
    entries.map(async (entry) => {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) {
        await walk(path)
        return
      }
      if (!allowedExtensions.has(extname(entry.name))) return

      checkedFiles += 1
      const content = await readFile(path, 'utf8')
      const lines = content.split('\n')
      const file = relative(root, path)

      lines.forEach((line, index) => {
        const location = `${file}:${index + 1}`
        const fontSize = line.match(/font-size:\s*([0-9.]+)px/)
        if (fontSize && Number(fontSize[1]) < 11) {
          violations.push(`${location} 字号不得小于 11px：${line.trim()}`)
        }

        if (
          line.includes('font-family:') &&
          !line.includes('var(--font-') &&
          !line.includes('font-family: inherit') &&
          !file.endsWith('styles/fonts.css') &&
          !file.endsWith('styles/tokens.css')
        ) {
          violations.push(`${location} 字体必须引用 --font-sans / --font-mono`)
        }

        if (/letter-spacing:\s*-/.test(line)) {
          violations.push(`${location} 禁止使用负字距`)
        }

        if (file.endsWith('.vue') && /<select\b/.test(line)) {
          violations.push(
            `${location} 禁止使用原生 select，统一使用 UiSelect / UiCombobox`,
          )
        }

        if (
          file.endsWith('.vue') &&
          /scroll-mode=["']page["']/.test(line)
        ) {
          violations.push(
            `${location} 弹窗禁止整窗滚动，请使用 UiDialog 的固定头部、正文滚动和 #footer`,
          )
        }

        if (
          file.endsWith('.vue') &&
          !file.endsWith('components/ui/UiDialog.vue') &&
          /role=["']dialog["']/.test(line)
        ) {
          violations.push(
            `${location} 业务弹窗必须使用 UiDialog / UiConfirmDialog`,
          )
        }
      })

      if (
        file.endsWith('.vue') &&
        content.includes('<UiDialog') &&
        content.includes('<footer') &&
        !content.includes('#footer')
      ) {
        violations.push(
          `${file} 弹窗操作区必须通过 UiDialog 的 #footer 插槽固定`,
        )
      }
    }),
  )
}

await walk(sourceRoot)

if (violations.length > 0) {
  console.error('WorkCove UI conventions failed:\n')
  violations.forEach((violation) => console.error(`- ${violation}`))
  process.exit(1)
}

console.log(`WorkCove UI conventions passed (${checkedFiles} files).`)
