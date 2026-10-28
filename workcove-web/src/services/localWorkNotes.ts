import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type { WorkNote } from '@/types/workNote'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.work-notes.v1'

function normalizeTags(value: unknown) {
  if (!Array.isArray(value)) return []
  return [
    ...new Set(
      value
        .filter((tag): tag is string => typeof tag === 'string')
        .map((tag) => tag.trim().replace(/^#/, ''))
        .filter(Boolean)
        .slice(0, 8),
    ),
  ]
}

function normalizeNote(value: unknown): WorkNote | null {
  if (!value || typeof value !== 'object') return null
  const note = value as Partial<WorkNote>
  if (
    typeof note.id !== 'string' ||
    typeof note.title !== 'string' ||
    typeof note.content !== 'string' ||
    !note.content.trim()
  ) {
    return null
  }

  return {
    id: note.id,
    workspaceId:
      typeof note.workspaceId === 'string' && note.workspaceId
        ? note.workspaceId
        : DEFAULT_WORKSPACE_ID,
    title: note.title.trim() || '未命名记录',
    titleAuto: Boolean(note.titleAuto),
    content: note.content,
    important: Boolean(note.important),
    resolved: Boolean(note.resolved),
    pinned: Boolean(note.pinned),
    archived: Boolean(note.archived),
    tags: normalizeTags(note.tags),
    createdAt:
      typeof note.createdAt === 'string'
        ? note.createdAt
        : new Date().toISOString(),
    updatedAt:
      typeof note.updatedAt === 'string'
        ? note.updatedAt
        : new Date().toISOString(),
  }
}

export function loadLocalWorkNotes(email: string) {
  const notes = readUserScopedValue<WorkNote[]>(STORAGE_KEY, email)
  return Array.isArray(notes)
    ? notes
        .map(normalizeNote)
        .filter((note): note is WorkNote => Boolean(note))
    : []
}

export function saveLocalWorkNotes(email: string, notes: WorkNote[]) {
  writeUserScopedValue(STORAGE_KEY, email, notes)
}

export function normalizeWorkNoteTags(tags: string[]) {
  return normalizeTags(tags)
}
