import {
  postJsonNoContentWithHeaders,
  postJsonWithHeaders,
} from '@/api/http'

export interface HostApplication {
  path: string
  name: string
  iconDataUrl: string | null
}

export interface HostApplicationStatus {
  path: string
  running: boolean
}

function errorText(error: unknown) {
  return error instanceof Error ? error.message : '本机应用操作失败'
}

export async function chooseHostApplication() {
  if (window.workCoveDesktop?.chooseApplication) {
    return window.workCoveDesktop.chooseApplication()
  }
  return postJsonWithHeaders<HostApplication | null>(
    '/host/applications/picker',
    {},
    { 'X-WorkCove-Action': 'choose-application' },
    120_000,
  )
}

export async function openHostApplication(applicationPath: string) {
  if (window.workCoveDesktop?.openPath) {
    return window.workCoveDesktop.openPath(applicationPath)
  }
  try {
    await postJsonNoContentWithHeaders(
      '/host/applications/open',
      { path: applicationPath },
      { 'X-WorkCove-Action': 'open-application' },
    )
    return ''
  } catch (error) {
    return errorText(error)
  }
}

export async function getHostApplicationStatuses(paths: string[]) {
  if (window.workCoveDesktop?.applicationStatuses) {
    return window.workCoveDesktop.applicationStatuses(paths)
  }
  return postJsonWithHeaders<HostApplicationStatus[]>(
    '/host/applications/running',
    { paths },
    { 'X-WorkCove-Action': 'application-running' },
  )
}

export async function forceQuitHostApplication(
  applicationPath: string,
) {
  if (window.workCoveDesktop?.forceQuitApplication) {
    return window.workCoveDesktop.forceQuitApplication(applicationPath)
  }
  try {
    await postJsonNoContentWithHeaders(
      '/host/applications/force-quit',
      { path: applicationPath },
      { 'X-WorkCove-Action': 'force-quit-application' },
    )
    return ''
  } catch (error) {
    return errorText(error)
  }
}
