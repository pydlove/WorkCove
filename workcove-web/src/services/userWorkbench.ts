import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import {
  DEFAULT_VAULT_UNLOCK_MINUTES,
  normalizeVaultUnlockMinutes,
} from '@/services/vaultPreferences'
import type {
  WorkNote,
  WorkProject,
  WorkTask,
} from '@/types/workbench'

const WORKBENCH_DATA_KEY = 'workcove.workbench.home.v1'
const WORKBENCH_SETTINGS_KEY = 'workcove.workbench.settings.v1'

export const DEFAULT_CODEX_ASSISTANT_NAME = 'Codex'

export interface UserWorkbenchData {
  tasks: WorkTask[]
  notes: WorkNote[]
  projects: WorkProject[]
}

export interface UserWorkbenchSettings {
  launchOnLogin: boolean
  compactSidebar: boolean
  navigationMode: 'normal' | 'accordion'
  vaultUnlockMinutes: number
  codexName: string
  codexIconDataUrl: string | null
}

export function loadUserWorkbenchData(email: string) {
  const value = readUserScopedValue<UserWorkbenchData>(
    WORKBENCH_DATA_KEY,
    email,
  )
  if (!value || typeof value !== 'object') return null

  return {
    tasks: Array.isArray(value.tasks) ? value.tasks : [],
    notes: Array.isArray(value.notes) ? value.notes : [],
    projects: Array.isArray(value.projects) ? value.projects : [],
  } satisfies UserWorkbenchData
}

export function saveUserWorkbenchData(
  email: string,
  data: UserWorkbenchData,
) {
  writeUserScopedValue(WORKBENCH_DATA_KEY, email, data)
}

export function loadUserWorkbenchSettings(email: string) {
  const value = readUserScopedValue<UserWorkbenchSettings>(
    WORKBENCH_SETTINGS_KEY,
    email,
  )
  if (!value || typeof value !== 'object') return null

  return {
    launchOnLogin:
      typeof value.launchOnLogin === 'boolean'
        ? value.launchOnLogin
        : true,
    compactSidebar:
      typeof value.compactSidebar === 'boolean'
        ? value.compactSidebar
        : false,
    navigationMode:
      value.navigationMode === 'accordion' ? 'accordion' : 'normal',
    vaultUnlockMinutes: normalizeVaultUnlockMinutes(
      value.vaultUnlockMinutes ?? DEFAULT_VAULT_UNLOCK_MINUTES,
    ),
    codexName:
      typeof value.codexName === 'string' && value.codexName.trim()
        ? value.codexName.trim()
        : DEFAULT_CODEX_ASSISTANT_NAME,
    codexIconDataUrl:
      typeof value.codexIconDataUrl === 'string' &&
      value.codexIconDataUrl.startsWith('data:image/')
        ? value.codexIconDataUrl
        : null,
  } satisfies UserWorkbenchSettings
}

export function saveUserWorkbenchSettings(
  email: string,
  settings: UserWorkbenchSettings,
) {
  writeUserScopedValue(WORKBENCH_SETTINGS_KEY, email, settings)
}
