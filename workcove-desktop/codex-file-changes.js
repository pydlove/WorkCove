const path = require('node:path')

function gitPatchPath(value) {
  return JSON.stringify(value.replace(/\\/g, '/'))
}

function codexFileChangePatch(cwd, changes) {
  const root = path.resolve(cwd)
  const blocks = []
  for (const change of changes) {
    if (!change || typeof change !== 'object') {
      throw new Error('文件变更数据无效')
    }
    const rawPath =
      typeof change.path === 'string' ? change.path.trim() : ''
    const diff =
      typeof change.diff === 'string' ? change.diff.trim() : ''
    if (!rawPath || !diff) continue
    if (rawPath.length > 4096 || diff.length > 250_000) {
      throw new Error('文件变更数据过大，无法撤销')
    }
    const absolutePath = path.isAbsolute(rawPath)
      ? path.resolve(rawPath)
      : path.resolve(root, rawPath)
    const relativePath = path.relative(root, absolutePath)
    if (
      !relativePath ||
      relativePath.startsWith('..') ||
      path.isAbsolute(relativePath) ||
      relativePath.includes('\0')
    ) {
      throw new Error(`文件不在当前项目内：${rawPath}`)
    }
    const normalizedPath = relativePath.replace(/\\/g, '/')
    const lines = diff.replace(/\r\n/g, '\n').split('\n')
    const diffHeaderIndex = lines.findIndex((line) =>
      line.startsWith('diff --git '),
    )
    if (diffHeaderIndex >= 0) {
      blocks.push(lines.slice(diffHeaderIndex).join('\n'))
      continue
    }
    const hunkIndex = lines.findIndex((line) => line.startsWith('@@'))
    if (hunkIndex < 0) {
      throw new Error(`文件缺少可撤销的补丁：${normalizedPath}`)
    }
    const kind =
      change.kind === 'add' ||
      change.kind === 'delete' ||
      change.kind === 'update'
        ? change.kind
        : 'unknown'
    const aPath = gitPatchPath(`a/${normalizedPath}`)
    const bPath = gitPatchPath(`b/${normalizedPath}`)
    const header = [`diff --git ${aPath} ${bPath}`]
    if (kind === 'add') {
      header.push('new file mode 100644')
      header.push('--- /dev/null', `+++ ${bPath}`)
    } else if (kind === 'delete') {
      header.push('deleted file mode 100644')
      header.push(`--- ${aPath}`, '+++ /dev/null')
    } else {
      header.push(`--- ${aPath}`, `+++ ${bPath}`)
    }
    blocks.push(
      `${header.join('\n')}\n${lines.slice(hunkIndex).join('\n')}`,
    )
  }
  return blocks.length > 0 ? `${blocks.join('\n')}\n` : ''
}

module.exports = {
  codexFileChangePatch,
}
