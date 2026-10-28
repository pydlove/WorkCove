import {
  readUserScopedValue,
  requireUserScope,
  writeUserScopedValue,
} from '@/services/userScopedStorage'

const STORAGE_KEY = 'workcove.mail.ui.v1'

interface MailUiState {
  expandedAccountByWorkspace: Record<string, number>
}

function normalizeState(value: MailUiState | null): MailUiState {
  if (!value || typeof value !== 'object') {
    return { expandedAccountByWorkspace: {} }
  }
  const source = value.expandedAccountByWorkspace
  const expandedAccountByWorkspace: Record<string, number> = {}
  if (source && typeof source === 'object') {
    for (const [workspaceId, accountId] of Object.entries(source)) {
      if (Number.isInteger(accountId) && accountId >= 0) {
        expandedAccountByWorkspace[workspaceId] = accountId
      }
    }
  }
  return { expandedAccountByWorkspace }
}

export function loadMailExpandedAccount(
  email: string,
  workspaceId: string,
) {
  const scope = requireUserScope(email)
  const state = normalizeState(
    readUserScopedValue<MailUiState>(STORAGE_KEY, scope),
  )
  return state.expandedAccountByWorkspace[workspaceId] ?? null
}

export function saveMailExpandedAccount(
  email: string,
  workspaceId: string,
  accountId: number | null,
) {
  const scope = requireUserScope(email)
  const state = normalizeState(
    readUserScopedValue<MailUiState>(STORAGE_KEY, scope),
  )
  if (
    accountId !== null &&
    Number.isInteger(accountId) &&
    accountId >= 0
  ) {
    state.expandedAccountByWorkspace[workspaceId] = accountId
  } else {
    delete state.expandedAccountByWorkspace[workspaceId]
  }
  writeUserScopedValue(STORAGE_KEY, scope, state)
}
