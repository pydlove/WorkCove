import type {
  DeploymentConfig,
  DeploymentRun,
  DeploymentRunStatus,
  DeploymentState,
} from '@/types/deployment'
import {
  readUserScopedMap,
  requireUserScope,
  writeUserScopedMap,
} from '@/services/userScopedStorage'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.deployments.v1'
const RUN_STATUSES = new Set<DeploymentRunStatus>([
  'SUCCESS',
  'FAILED',
  'TIMEOUT',
])

function normalizeDeployment(value: DeploymentConfig): DeploymentConfig | null {
  if (
    !value ||
    typeof value.id !== 'string' ||
    typeof value.name !== 'string' ||
    typeof value.remoteDirectory !== 'string' ||
    typeof value.scriptFilename !== 'string' ||
    typeof value.scriptContent !== 'string'
  ) {
    return null
  }
  return {
    id: value.id,
    workspaceId:
      typeof value.workspaceId === 'string' && value.workspaceId
        ? value.workspaceId
        : DEFAULT_WORKSPACE_ID,
    name: value.name.trim(),
    serverId:
      typeof value.serverId === 'number' &&
      Number.isInteger(value.serverId) &&
      value.serverId > 0
        ? value.serverId
        : null,
    serverName:
      typeof value.serverName === 'string' ? value.serverName : '',
    remoteDirectory: value.remoteDirectory.trim(),
    scriptFilename: value.scriptFilename.trim(),
    scriptContent: value.scriptContent,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  }
}

function normalizeRun(value: DeploymentRun): DeploymentRun | null {
  if (
    !value ||
    typeof value.id !== 'string' ||
    typeof value.deploymentId !== 'string' ||
    !RUN_STATUSES.has(value.status)
  ) {
    return null
  }
  return {
    id: value.id,
    workspaceId:
      typeof value.workspaceId === 'string' && value.workspaceId
        ? value.workspaceId
        : DEFAULT_WORKSPACE_ID,
    deploymentId: value.deploymentId,
    deploymentName:
      typeof value.deploymentName === 'string'
        ? value.deploymentName
        : '',
    serverId:
      typeof value.serverId === 'number' ? value.serverId : null,
    serverName:
      typeof value.serverName === 'string' ? value.serverName : '',
    status: value.status,
    exitCode:
      typeof value.exitCode === 'number' ? value.exitCode : null,
    summary: typeof value.summary === 'string' ? value.summary : '',
    output: typeof value.output === 'string' ? value.output : '',
    startedAt: value.startedAt,
    finishedAt: value.finishedAt,
  }
}

function normalizeState(value: DeploymentState | null): DeploymentState {
  const deployments = Array.isArray(value?.deployments)
    ? value.deployments
        .map(normalizeDeployment)
        .filter((item): item is DeploymentConfig => item !== null)
    : []
  const runs = Array.isArray(value?.runs)
    ? value.runs
        .map(normalizeRun)
        .filter((item): item is DeploymentRun => item !== null)
        .slice(0, 200)
    : []
  return { deployments, runs }
}

export function loadLocalDeployments(email: string): DeploymentState {
  const normalizedEmail = requireUserScope(email)
  const stored =
    readUserScopedMap<DeploymentState>(STORAGE_KEY)[normalizedEmail]
  return normalizeState(stored ?? null)
}

export function saveLocalDeployments(
  email: string,
  state: DeploymentState,
) {
  const map = readUserScopedMap<DeploymentState>(STORAGE_KEY)
  map[requireUserScope(email)] = normalizeState(state)
  writeUserScopedMap(STORAGE_KEY, map)
}

export function newDeploymentId() {
  return window.crypto.randomUUID()
}
