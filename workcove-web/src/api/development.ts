import {
  postJsonNoContentWithHeaders,
  postJsonWithHeaders,
} from '@/api/http'
import type { LocalDirectorySummary } from '@/types/workDirectory'

interface DirectoryPickerResponse {
  path: string | null
}

export async function chooseLocalDirectory() {
  const response = await postJsonWithHeaders<DirectoryPickerResponse>(
    '/development/directory-picker',
    {},
    { 'X-WorkCove-Action': 'choose-directory' },
    120_000,
  )
  return response.path
}

export async function openLocalDirectory(path: string) {
  if (window.workCoveDesktop?.openPath) {
    return window.workCoveDesktop.openPath(path)
  }
  try {
    await postJsonNoContentWithHeaders(
      '/development/open-directory',
      { path },
      { 'X-WorkCove-Action': 'open-directory' },
    )
    return ''
  } catch (error) {
    return error instanceof Error ? error.message : '无法在访达中打开目录'
  }
}

export function inspectLocalDirectory(
  path: string,
  largeFileThresholdBytes = 100 * 1024 * 1024,
) {
  return postJsonWithHeaders<LocalDirectorySummary>(
    '/development/directory-summary',
    { path, largeFileThresholdBytes },
    { 'X-WorkCove-Action': 'inspect-directory' },
    60_000,
  )
}

export async function openLocalProjectWithIdea(path: string) {
  if (window.workCoveDesktop?.openWithIdea) {
    return window.workCoveDesktop.openWithIdea(path)
  }
  try {
    await postJsonNoContentWithHeaders(
      '/development/open-with-idea',
      { path },
      { 'X-WorkCove-Action': 'open-with-idea' },
    )
    return ''
  } catch (error) {
    return error instanceof Error
      ? error.message
      : '无法启动 IntelliJ IDEA'
  }
}
