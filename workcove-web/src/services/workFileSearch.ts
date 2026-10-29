import {
  postJsonNoContentWithHeaders,
  postJsonWithHeaders,
} from '@/api/http'

export interface WorkFileSearchItem {
  name: string
  path: string
  rootName: string
  relativePath: string
}

export function canSearchWorkFiles() {
  return true
}

export async function searchWorkFiles(
  roots: string[],
  query: string,
): Promise<WorkFileSearchItem[]> {
  const bridge = window.workCoveDesktop
  if (roots.length === 0 || !query.trim()) {
    return []
  }
  if (bridge?.searchWorkFiles) {
    return bridge.searchWorkFiles(roots, query)
  }
  return postJsonWithHeaders<WorkFileSearchItem[]>(
    '/development/work-files/search',
    { roots, query },
    { 'X-WorkCove-Action': 'search-work-files' },
    10_000,
  )
}

export async function openWorkFile(
  targetPath: string,
  roots: string[],
) {
  const bridge = window.workCoveDesktop
  if (bridge?.openWorkFile) {
    return bridge.openWorkFile(targetPath, roots)
  }
  try {
    await postJsonNoContentWithHeaders(
      '/development/work-files/open',
      { path: targetPath, roots },
      { 'X-WorkCove-Action': 'open-work-file' },
      20_000,
    )
    return ''
  } catch (error) {
    return error instanceof Error
      ? error.message
      : '无法使用默认应用打开文件'
  }
}
