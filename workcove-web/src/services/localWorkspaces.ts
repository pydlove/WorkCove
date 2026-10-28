import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import {
  DEFAULT_WORKSPACE_ID,
  DEFAULT_WORKSPACE_ICON,
  DEFAULT_WORKSPACE_ICON_TONE,
  DEFAULT_WORKSPACE_NAME,
  MAX_WORKSPACE_ICON_DATA_URL_LENGTH,
  WORKSPACE_ICON_KEYS,
  WORKSPACE_ICON_TONES,
  type Workspace,
  type WorkspaceIcon,
  type WorkspaceIconTone,
  type WorkspaceState,
} from '@/types/workspace'

const STORAGE_KEY = 'workcove.workspaces.v1'

function defaultWorkspace(): Workspace {
  const now = new Date().toISOString()
  return {
    id: DEFAULT_WORKSPACE_ID,
    name: DEFAULT_WORKSPACE_NAME,
    icon: DEFAULT_WORKSPACE_ICON,
    iconTone: DEFAULT_WORKSPACE_ICON_TONE,
    iconImage: '',
    createdAt: now,
    updatedAt: now,
  }
}

function normalizeState(value: WorkspaceState | null): WorkspaceState {
  const workspaces = Array.isArray(value?.workspaces)
    ? value.workspaces
        .filter(
          (workspace) =>
            workspace &&
            typeof workspace.id === 'string' &&
            typeof workspace.name === 'string',
        )
        .map((workspace) => ({
          ...workspace,
          id: workspace.id.trim(),
          name: workspace.name.trim(),
          icon: WORKSPACE_ICON_KEYS.includes(
            workspace.icon as WorkspaceIcon,
          )
            ? workspace.icon
            : DEFAULT_WORKSPACE_ICON,
          iconTone: WORKSPACE_ICON_TONES.includes(
            workspace.iconTone as WorkspaceIconTone,
          )
            ? workspace.iconTone
            : DEFAULT_WORKSPACE_ICON_TONE,
          iconImage: normalizeWorkspaceIconImage(workspace.iconImage),
        }))
        .filter((workspace) => workspace.id && workspace.name)
    : []

  if (!workspaces.some((workspace) => workspace.id === DEFAULT_WORKSPACE_ID)) {
    workspaces.unshift(defaultWorkspace())
  }

  const activeWorkspaceId = workspaces.some(
    (workspace) => workspace.id === value?.activeWorkspaceId,
  )
    ? value!.activeWorkspaceId
    : workspaces[0].id

  return { workspaces, activeWorkspaceId }
}

export function normalizeWorkspaceIconImage(value: unknown) {
  if (
    typeof value !== 'string' ||
    value.length > MAX_WORKSPACE_ICON_DATA_URL_LENGTH ||
    !/^data:image\/(?:png|jpeg|webp);base64,/i.test(value)
  ) {
    return ''
  }
  return value
}

export function loadLocalWorkspaceState(email: string) {
  return normalizeState(
    readUserScopedValue<WorkspaceState>(STORAGE_KEY, email),
  )
}

export function saveLocalWorkspaceState(
  email: string,
  state: WorkspaceState,
) {
  writeUserScopedValue(STORAGE_KEY, email, normalizeState(state))
}

export function newWorkspaceId() {
  return window.crypto.randomUUID()
}
