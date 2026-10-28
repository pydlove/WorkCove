import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type { GitRepository } from '@/types/gitRepository'
import { DEFAULT_GIT_REPOSITORY_CATEGORY } from '@/types/gitRepository'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const STORAGE_KEY = 'workcove.git-repositories.v1'
const CATEGORY_STORAGE_KEY = 'workcove.git-repository-categories.v1'

export type GitRepositoryCategoriesByWorkspace =
  Record<string, string[]>

export function loadLocalGitRepositories(email: string): GitRepository[] {
  const repositories = readUserScopedValue<GitRepository[]>(
    STORAGE_KEY,
    email,
  )
  return Array.isArray(repositories)
    ? repositories
        .filter(
          (repository) =>
            repository &&
            typeof repository.id === 'string' &&
            typeof repository.path === 'string' &&
            typeof repository.name === 'string',
        )
        .map((repository) => ({
          ...repository,
          workspaceId:
            typeof repository.workspaceId === 'string' &&
            repository.workspaceId
              ? repository.workspaceId
              : DEFAULT_WORKSPACE_ID,
          category:
            typeof repository.category === 'string' &&
            repository.category.trim()
              ? repository.category.trim()
              : DEFAULT_GIT_REPOSITORY_CATEGORY,
          remoteUrl:
            typeof repository.remoteUrl === 'string'
              ? repository.remoteUrl.trim()
              : '',
          source:
            repository.source === 'remote' || !repository.path
              ? 'remote'
              : 'local',
        }))
    : []
}

export function saveLocalGitRepositories(
  email: string,
  repositories: GitRepository[],
) {
  writeUserScopedValue(STORAGE_KEY, email, repositories)
}

export function loadLocalGitRepositoryCategories(email: string) {
  const categoriesByWorkspace =
    readUserScopedValue<GitRepositoryCategoriesByWorkspace>(
      CATEGORY_STORAGE_KEY,
      email,
    )
  if (!categoriesByWorkspace || typeof categoriesByWorkspace !== 'object') {
    return {} as GitRepositoryCategoriesByWorkspace
  }
  const normalized: GitRepositoryCategoriesByWorkspace = {}
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

export function saveLocalGitRepositoryCategories(
  email: string,
  categoriesByWorkspace: GitRepositoryCategoriesByWorkspace,
) {
  writeUserScopedValue(
    CATEGORY_STORAGE_KEY,
    email,
    categoriesByWorkspace,
  )
}

export function newGitRepositoryId() {
  return window.crypto.randomUUID()
}
