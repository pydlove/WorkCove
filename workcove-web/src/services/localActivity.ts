import {
  readUserScopedMap,
  requireUserScope,
  writeUserScopedMap,
} from '@/services/userScopedStorage'
import type { DailyActivity } from '@/types/activity'

const STORAGE_KEY = 'workcove.daily-activity.v1'

function normalizeActivity(value: DailyActivity): DailyActivity | null {
  if (
    !value ||
    typeof value.date !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(value.date)
  ) {
    return null
  }
  return {
    date: value.date,
    typedCharacters: Number.isFinite(value.typedCharacters)
      ? Math.max(0, Math.floor(value.typedCharacters))
      : 0,
    mouseClicks: Number.isFinite(value.mouseClicks)
      ? Math.max(0, Math.floor(value.mouseClicks))
      : 0,
    updatedAt:
      typeof value.updatedAt === 'string'
        ? value.updatedAt
        : new Date().toISOString(),
  }
}

export function localDateKey(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function loadLocalActivity(email: string): DailyActivity[] {
  const normalizedEmail = requireUserScope(email)
  const stored =
    readUserScopedMap<DailyActivity[]>(STORAGE_KEY)[normalizedEmail]
  return Array.isArray(stored)
    ? stored
        .map(normalizeActivity)
        .filter((item): item is DailyActivity => item !== null)
        .sort((left, right) => right.date.localeCompare(left.date))
    : []
}

export function saveLocalActivity(
  email: string,
  days: DailyActivity[],
) {
  const map = readUserScopedMap<DailyActivity[]>(STORAGE_KEY)
  map[requireUserScope(email)] = days
  writeUserScopedMap(STORAGE_KEY, map)
}
