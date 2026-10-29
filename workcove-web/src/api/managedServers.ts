import {
  deleteNoContentWithHeaders,
  get,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  ManagedServer,
  ManagedServerInput,
  OpenManagedServerResult,
  ServerTool,
} from '@/types/managedServer'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-servers' }

function requestBody(input: ManagedServerInput) {
  return {
    workspaceId: input.workspaceId,
    name: input.name,
    host: input.host,
    port: input.port,
    username: input.username,
    password: input.password,
  }
}

export const listManagedServers = (workspaceId: string) =>
  get<ManagedServer[]>(
    `/development/servers?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const createManagedServer = (input: ManagedServerInput) =>
  postJsonWithHeaders<ManagedServer>(
    '/development/servers',
    requestBody(input),
    ACTION_HEADER,
  )

export const updateManagedServer = (input: ManagedServerInput) =>
  putJsonWithHeaders<ManagedServer>(
    `/development/servers/${input.id}`,
    requestBody(input),
    ACTION_HEADER,
  )

export const deleteManagedServer = (
  id: number,
  workspaceId: string,
) =>
  deleteNoContentWithHeaders(
    `/development/servers/${id}?workspaceId=${encodeURIComponent(workspaceId)}`,
    ACTION_HEADER,
  )

export const openManagedServer = (
  id: number,
  workspaceId: string,
  tool: ServerTool,
) =>
  postJsonWithHeaders<OpenManagedServerResult>(
    `/development/servers/${id}/open?workspaceId=${encodeURIComponent(workspaceId)}`,
    { tool },
    ACTION_HEADER,
  )

export const copyManagedServer = (
  id: number,
  sourceWorkspaceId: string,
  targetWorkspaceId: string,
) =>
  postJsonWithHeaders<ManagedServer>(
    `/development/servers/${id}/copy`,
    { sourceWorkspaceId, targetWorkspaceId },
    ACTION_HEADER,
  )

export const moveManagedServer = (
  id: number,
  sourceWorkspaceId: string,
  targetWorkspaceId: string,
) =>
  postJsonWithHeaders<ManagedServer>(
    `/development/servers/${id}/move`,
    { sourceWorkspaceId, targetWorkspaceId },
    ACTION_HEADER,
  )
