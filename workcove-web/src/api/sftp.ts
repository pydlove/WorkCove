import {
  deleteNoContentWithHeaders,
  get,
  postFormWithHeaders,
  postJsonNoContentWithHeaders,
} from '@/api/http'
import type { RemoteFile } from '@/types/remoteFile'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-server-files' }

function filePath(serverId: number, suffix = '') {
  return `/development/servers/${serverId}/files${suffix}`
}

export const listRemoteFiles = (
  serverId: number,
  workspaceId: string,
  path: string,
) =>
  get<RemoteFile[]>(
    `${filePath(serverId)}?workspaceId=${encodeURIComponent(workspaceId)}&path=${encodeURIComponent(path)}`,
  )

export const uploadRemoteFile = (
  serverId: number,
  workspaceId: string,
  directory: string,
  file: File,
) => {
  const form = new FormData()
  form.append('file', file)
  return postFormWithHeaders(
    `${filePath(serverId, '/upload')}?workspaceId=${encodeURIComponent(workspaceId)}&path=${encodeURIComponent(directory)}`,
    form,
    ACTION_HEADER,
    120_000,
  )
}

export const createRemoteDirectory = (
  serverId: number,
  workspaceId: string,
  path: string,
) =>
  postJsonNoContentWithHeaders(
    `${filePath(serverId, '/directory')}?workspaceId=${encodeURIComponent(workspaceId)}`,
    { path },
    ACTION_HEADER,
  )

export const deleteRemoteFile = (
  serverId: number,
  workspaceId: string,
  path: string,
  directory: boolean,
) =>
  deleteNoContentWithHeaders(
    `${filePath(serverId)}?workspaceId=${encodeURIComponent(workspaceId)}&path=${encodeURIComponent(path)}&directory=${directory}`,
    ACTION_HEADER,
  )

export function remoteDownloadUrl(
  serverId: number,
  workspaceId: string,
  path: string,
) {
  return `/api${filePath(serverId, '/download')}?workspaceId=${encodeURIComponent(workspaceId)}&path=${encodeURIComponent(path)}`
}
