import {
  DEFAULT_WORK_DIRECTORY_CATEGORY,
  type WorkDirectory,
} from '@/types/workDirectory'
import {
  readUserScopedMap,
  requireUserScope,
  writeUserScopedMap,
} from '@/services/userScopedStorage'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.work-directories.v1'
const CATEGORY_STORAGE_KEY = 'workcove.work-directory-categories.v2'
const LEGACY_CATEGORY_STORAGE_KEY =
  'workcove.work-directory-categories.v1'

export type WorkDirectoryCategoriesByWorkspace =
  Record<string, string[]>

function readMap(): Record<string, WorkDirectory[]> {
  return readUserScopedMap<WorkDirectory[]>(STORAGE_KEY)
}

function normalizeEmail(email: string) {
  return requireUserScope(email)
}

export function loadLocalWorkDirectories(email: string) {
  const directories = readMap()[normalizeEmail(email)]
  return Array.isArray(directories)
    ? directories
        .filter(
          (directory) =>
            directory &&
            typeof directory.id === 'string' &&
            typeof directory.name === 'string' &&
            typeof directory.path === 'string',
        )
        .map((directory) => ({
          ...directory,
          workspaceId:
            typeof directory.workspaceId === 'string' &&
            directory.workspaceId
              ? directory.workspaceId
              : DEFAULT_WORKSPACE_ID,
          category:
            typeof directory.category === 'string' &&
            directory.category.trim()
              ? directory.category.trim()
              : DEFAULT_WORK_DIRECTORY_CATEGORY,
          description:
            typeof directory.description === 'string'
              ? directory.description
              : '',
        }))
    : []
}

export function saveLocalWorkDirectories(
  email: string,
  directories: WorkDirectory[],
) {
  const map = readMap()
  map[normalizeEmail(email)] = directories
  writeUserScopedMap(STORAGE_KEY, map)
}

export function loadLocalWorkDirectoryCategories(email: string) {
  const normalizedEmail = normalizeEmail(email)
  const categoriesByWorkspace =
    readUserScopedMap<WorkDirectoryCategoriesByWorkspace>(
      CATEGORY_STORAGE_KEY,
    )[normalizedEmail]
  if (categoriesByWorkspace && typeof categoriesByWorkspace === 'object') {
    const normalized: WorkDirectoryCategoriesByWorkspace = {}
    for (const [workspaceId, categories] of Object.entries(
      categoriesByWorkspace,
    )) {
      if (!Array.isArray(categories)) continue
      normalized[workspaceId] = categories
        .filter((category) => typeof category === 'string')
        .map((category) => category.trim())
        .filter(Boolean)
    }
    return normalized
  }

  const legacy =
    readUserScopedMap<string[]>(LEGACY_CATEGORY_STORAGE_KEY)[
      normalizedEmail
    ]
  return Array.isArray(legacy)
    ? {
        [DEFAULT_WORKSPACE_ID]: legacy
          .filter((category) => typeof category === 'string')
          .map((category) => category.trim())
          .filter(Boolean),
      }
    : {}
}

export function saveLocalWorkDirectoryCategories(
  email: string,
  categoriesByWorkspace: WorkDirectoryCategoriesByWorkspace,
) {
  const map =
    readUserScopedMap<WorkDirectoryCategoriesByWorkspace>(
      CATEGORY_STORAGE_KEY,
    )
  map[normalizeEmail(email)] = categoriesByWorkspace
  writeUserScopedMap(CATEGORY_STORAGE_KEY, map)
}

export function newWorkDirectoryId() {
  return window.crypto.randomUUID()
}
