import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'

const STORAGE_KEY = 'workcove.global-search-history.v1'
const MAX_ENTRIES = 60

export interface SearchHistoryEntry {
  id: string
  workspaceId: string
  query: string
  kind: string
  targetId: string
  targetView: string
  group: string
  title: string
  subtitle: string
  createdAt: string
}

function normalizeEntry(value: unknown): SearchHistoryEntry | null {
  if (!value || typeof value !== 'object') return null
  const entry = value as Partial<SearchHistoryEntry>
  if (
    typeof entry.id !== 'string' ||
    typeof entry.workspaceId !== 'string' ||
    typeof entry.kind !== 'string' ||
    typeof entry.targetId !== 'string' ||
    typeof entry.targetView !== 'string' ||
    typeof entry.title !== 'string' ||
    typeof entry.subtitle !== 'string' ||
    typeof entry.createdAt !== 'string'
  ) {
    return null
  }
  return {
    id: entry.id,
    workspaceId: entry.workspaceId,
    query: typeof entry.query === 'string' ? entry.query : '',
    kind: entry.kind,
    targetId: entry.targetId,
    targetView: entry.targetView,
    group: typeof entry.group === 'string' ? entry.group : '',
    title: entry.title,
    subtitle: entry.subtitle,
    createdAt: entry.createdAt,
  }
}

export function loadSearchHistory(email: string) {
  const value = readUserScopedValue<unknown[]>(STORAGE_KEY, email)
  if (!Array.isArray(value)) return []
  return value
    .map(normalizeEntry)
    .filter((entry): entry is SearchHistoryEntry => Boolean(entry))
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
    .slice(0, MAX_ENTRIES)
}

export function saveSearchHistory(
  email: string,
  entries: SearchHistoryEntry[],
) {
  writeUserScopedValue(
    STORAGE_KEY,
    email,
    entries.slice(0, MAX_ENTRIES),
  )
}
