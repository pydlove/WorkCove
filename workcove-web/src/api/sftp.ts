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

export const listRemoteFiles = (serverId: number, path: string) =>
  get<RemoteFile[]>(
    `${filePath(serverId)}?path=${encodeURIComponent(path)}`,
  )

export const uploadRemoteFile = (
  serverId: number,
  directory: string,
  file: File,
) => {
  const form = new FormData()
  form.append('file', file)
  return postFormWithHeaders(
    `${filePath(serverId, '/upload')}?path=${encodeURIComponent(directory)}`,
    form,
    ACTION_HEADER,
    120_000,
  )
}

export const createRemoteDirectory = (
  serverId: number,
  path: string,
) =>
  postJsonNoContentWithHeaders(
    filePath(serverId, '/directory'),
    { path },
    ACTION_HEADER,
  )

export const deleteRemoteFile = (
  serverId: number,
  path: string,
  directory: boolean,
) =>
  deleteNoContentWithHeaders(
    `${filePath(serverId)}?path=${encodeURIComponent(path)}&directory=${directory}`,
    ACTION_HEADER,
  )

export function remoteDownloadUrl(serverId: number, path: string) {
  return `/api${filePath(serverId, '/download')}?path=${encodeURIComponent(path)}`
}
