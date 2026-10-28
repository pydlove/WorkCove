import {
  readUserScopedMap,
  requireUserScope,
  writeUserScopedMap,
} from '@/services/userScopedStorage'
import type { TerminalQuickCommand } from '@/types/quickCommand'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.terminal-quick-commands.v1'

function normalizeQuickCommand(
  value: TerminalQuickCommand,
): TerminalQuickCommand | null {
  const scope = value?.scope === 'global' ? 'global' : 'server'
  const serverId =
    typeof value?.serverId === 'number' &&
    Number.isInteger(value.serverId) &&
    value.serverId > 0
      ? value.serverId
      : null
  if (
    !value ||
    typeof value.id !== 'string' ||
    typeof value.name !== 'string' ||
    typeof value.command !== 'string' ||
    (scope === 'server' && serverId === null)
  ) {
    return null
  }
  return {
    id: value.id,
    workspaceId:
      typeof value.workspaceId === 'string' && value.workspaceId
        ? value.workspaceId
        : DEFAULT_WORKSPACE_ID,
    scope,
    serverId: scope === 'global' ? null : serverId,
    name: value.name.trim(),
    command: value.command,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  }
}

export function loadLocalQuickCommands(email: string) {
  const normalizedEmail = requireUserScope(email)
  const commands =
    readUserScopedMap<TerminalQuickCommand[]>(STORAGE_KEY)[normalizedEmail]
  return Array.isArray(commands)
    ? commands
        .map(normalizeQuickCommand)
        .filter((item): item is TerminalQuickCommand => item !== null)
    : []
}

export function saveLocalQuickCommands(
  email: string,
  commands: TerminalQuickCommand[],
) {
  const map = readUserScopedMap<TerminalQuickCommand[]>(STORAGE_KEY)
  map[requireUserScope(email)] = commands
  writeUserScopedMap(STORAGE_KEY, map)
}

export function newQuickCommandId() {
  return window.crypto.randomUUID()
}
