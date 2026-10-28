import { defineStore } from 'pinia'

import { inspectGitRepository } from '@/api/gitRepositories'
import {
  loadLocalGitRepositoryCategories,
  loadLocalGitRepositories,
  newGitRepositoryId,
  saveLocalGitRepositoryCategories,
  saveLocalGitRepositories,
} from '@/services/localGitRepositories'
import type { GitRepositoryCategoriesByWorkspace } from '@/services/localGitRepositories'
import {
  DEFAULT_GIT_REPOSITORY_CATEGORY,
  type GitRepository,
  type GitRepositoryStatus,
} from '@/types/gitRepository'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

function normalizePath(value: string) {
  return value.trim().replace(/\/+$/, '') || '/'
}

function sanitizeRemoteUrl(value: string) {
  const raw = value.trim()
  if (!/^(https?|ssh):\/\//i.test(raw)) return raw
  try {
    const url = new URL(raw)
    url.username = ''
    url.password = ''
    url.search = ''
    url.hash = ''
    return url.toString().replace(/\/$/, '')
  } catch {
    return raw
  }
}

function remoteIdentity(value: string) {
  return sanitizeRemoteUrl(value)
    .replace(/\/+$/, '')
    .replace(/\.git$/i, '')
    .toLocaleLowerCase('en-US')
}

function normalizeCategories(
  categoriesByWorkspace: GitRepositoryCategoriesByWorkspace,
  repositories: GitRepository[],
) {
  const workspaceIds = new Set([
    DEFAULT_WORKSPACE_ID,
    ...Object.keys(categoriesByWorkspace),
    ...repositories.map((repository) => repository.workspaceId),
  ])
  const normalized: GitRepositoryCategoriesByWorkspace = {}

  for (const workspaceId of workspaceIds) {
    const categories = [
      ...new Set([
        ...(categoriesByWorkspace[workspaceId] ?? []),
        ...repositories
          .filter((repository) => repository.workspaceId === workspaceId)
          .map((repository) => repository.category),
        DEFAULT_GIT_REPOSITORY_CATEGORY,
      ]),
    ]
      .map((category) => category.trim())
      .filter((category) => category && category !== '全部')

    normalized[workspaceId] =
      categories.length > 0
        ? categories
        : [DEFAULT_GIT_REPOSITORY_CATEGORY]
  }
  return normalized
}

export const useGitRepositoryStore = defineStore('gitRepositories', {
  state: () => ({
    ownerEmail: '',
    repositories: [] as GitRepository[],
    categoriesByWorkspace: {
      [DEFAULT_WORKSPACE_ID]: [DEFAULT_GIT_REPOSITORY_CATEGORY],
    } as GitRepositoryCategoriesByWorkspace,
    statuses: {} as Record<string, GitRepositoryStatus>,
    errors: {} as Record<string, string>,
    loadingIds: [] as string[],
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.repositories = []
        this.categoriesByWorkspace = {
          [DEFAULT_WORKSPACE_ID]: [DEFAULT_GIT_REPOSITORY_CATEGORY],
        }
        this.statuses = {}
        this.errors = {}
        this.loadingIds = []
        return
      }
      if (this.ownerEmail === normalizedEmail) return

      this.ownerEmail = normalizedEmail
      this.repositories = loadLocalGitRepositories(normalizedEmail)
      this.categoriesByWorkspace = normalizeCategories(
        loadLocalGitRepositoryCategories(normalizedEmail),
        this.repositories,
      )
      this.statuses = {}
      this.errors = {}
      this.loadingIds = []
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalGitRepositories(this.ownerEmail, this.repositories)
      saveLocalGitRepositoryCategories(
        this.ownerEmail,
        this.categoriesByWorkspace,
      )
    },
    async addRepository(
      workspaceId: string,
      path: string,
      category = DEFAULT_GIT_REPOSITORY_CATEGORY,
    ) {
      const status = await inspectGitRepository(path)
      const existing = this.repositories.find(
        (repository) =>
          repository.workspaceId === workspaceId &&
          repository.path === normalizePath(status.rootPath),
      )
      if (existing) {
        this.statuses[existing.id] = status
        throw new Error(`「${status.name}」已在当前工作空间`)
      }
      const remoteOnly = status.remoteUrl
        ? this.repositories.find(
            (repository) =>
              repository.workspaceId === workspaceId &&
              repository.source === 'remote' &&
              remoteIdentity(repository.remoteUrl) ===
                remoteIdentity(status.remoteUrl),
          )
        : null

      const now = new Date().toISOString()
      const repository: GitRepository = {
        id: remoteOnly?.id ?? newGitRepositoryId(),
        workspaceId,
        name: status.name,
        path: normalizePath(status.rootPath),
        remoteUrl: status.remoteUrl,
        source: 'local',
        category: category.trim() || DEFAULT_GIT_REPOSITORY_CATEGORY,
        createdAt: remoteOnly?.createdAt ?? now,
        updatedAt: now,
      }
      this.repositories = remoteOnly
        ? this.repositories.map((item) =>
            item.id === remoteOnly.id ? repository : item,
          )
        : [repository, ...this.repositories]
      this.statuses[repository.id] = status
      this.errors[repository.id] = ''
      this.ensureCategory(repository.category, workspaceId)
      this.persist()
      return repository
    },
    addRemoteRepository(
      workspaceId: string,
      rawRemoteUrl: string,
      rawName: string,
      category = DEFAULT_GIT_REPOSITORY_CATEGORY,
    ) {
      const remoteUrl = sanitizeRemoteUrl(rawRemoteUrl)
      if (!remoteUrl) {
        throw new Error('请输入 Git 仓库地址')
      }
      if (remoteUrl.length > 2048) {
        throw new Error('Git 仓库地址过长')
      }
      if (remoteUrl.includes('\n') || remoteUrl.includes('\r')) {
        throw new Error('Git 仓库地址无效')
      }
      const identity = remoteIdentity(remoteUrl)
      const duplicate = this.repositories.find(
        (repository) =>
          repository.workspaceId === workspaceId &&
          repository.remoteUrl &&
          remoteIdentity(repository.remoteUrl) === identity,
      )
      if (duplicate) {
        throw new Error(`「${duplicate.name}」已在当前工作空间`)
      }

      const pathName = remoteUrl
        .replace(/[?#].*$/, '')
        .replace(/\/+$/, '')
        .split(/[/:]/)
        .filter(Boolean)
        .pop()
        ?.replace(/\.git$/i, '')
      const now = new Date().toISOString()
      const repository: GitRepository = {
        id: newGitRepositoryId(),
        workspaceId,
        name: rawName.trim() || pathName || 'Git 仓库',
        path: '',
        remoteUrl,
        source: 'remote',
        category: category.trim() || DEFAULT_GIT_REPOSITORY_CATEGORY,
        createdAt: now,
        updatedAt: now,
      }
      this.repositories = [repository, ...this.repositories]
      this.ensureCategory(repository.category, workspaceId)
      this.persist()
      return repository
    },
    async refreshRepository(id: string) {
      const repository = this.repositories.find(
        (item) => item.id === id,
      )
      if (
        !repository ||
        repository.source === 'remote' ||
        !repository.path ||
        this.loadingIds.includes(id)
      ) {
        return
      }

      this.loadingIds = [...this.loadingIds, id]
      this.errors[id] = ''
      try {
        const status = await inspectGitRepository(repository.path)
        this.statuses[id] = status
        this.repositories = this.repositories.map((item) =>
          item.id === id
            ? {
                ...item,
                name: status.name,
                path: normalizePath(status.rootPath),
                remoteUrl: status.remoteUrl,
                source: 'local',
                updatedAt: new Date().toISOString(),
              }
            : item,
        )
        this.persist()
      } catch (error) {
        this.errors[id] =
          error instanceof Error
            ? error.message
            : '无法读取 Git 仓库状态'
      } finally {
        this.loadingIds = this.loadingIds.filter(
          (item) => item !== id,
        )
      }
    },
    async refreshWorkspace(workspaceId: string) {
      const ids = this.repositories
        .filter(
          (repository) =>
            repository.workspaceId === workspaceId &&
            repository.source === 'local' &&
            repository.path,
        )
        .map((repository) => repository.id)
      await Promise.all(ids.map((id) => this.refreshRepository(id)))
    },
    removeRepository(id: string) {
      this.repositories = this.repositories.filter(
        (repository) => repository.id !== id,
      )
      delete this.statuses[id]
      delete this.errors[id]
      this.loadingIds = this.loadingIds.filter((item) => item !== id)
      this.persist()
    },
    changeRepositoryCategory(id: string, category: string) {
      const repository = this.repositories.find((item) => item.id === id)
      const nextCategory = category.trim()
      if (
        !repository ||
        !nextCategory ||
        repository.category === nextCategory
      ) {
        return false
      }
      this.repositories = this.repositories.map((item) =>
        item.id === id
          ? {
              ...item,
              category: nextCategory,
              updatedAt: new Date().toISOString(),
            }
          : item,
      )
      this.ensureCategory(nextCategory, repository.workspaceId)
      this.persist()
      return true
    },
    addCategory(category: string, workspaceId: string) {
      const value = category.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [
          DEFAULT_GIT_REPOSITORY_CATEGORY,
        ]
      if (
        !value ||
        [...value].length > 24 ||
        value === '全部' ||
        categories.includes(value)
      ) {
        return false
      }
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: [...categories, value],
      }
      this.persist()
      return true
    },
    renameCategory(
      currentCategory: string,
      nextCategory: string,
      workspaceId: string,
    ) {
      const current = currentCategory.trim()
      const next = nextCategory.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [
          DEFAULT_GIT_REPOSITORY_CATEGORY,
        ]
      if (
        !next ||
        [...next].length > 24 ||
        next === '全部' ||
        !categories.includes(current) ||
        (current !== next && categories.includes(next))
      ) {
        return false
      }
      if (current === next) return true

      const now = new Date().toISOString()
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: categories.map((category) =>
          category === current ? next : category,
        ),
      }
      this.repositories = this.repositories.map((repository) =>
        repository.workspaceId === workspaceId &&
        repository.category === current
          ? { ...repository, category: next, updatedAt: now }
          : repository,
      )
      this.persist()
      return true
    },
    deleteCategory(
      category: string,
      reassignTo: string,
      workspaceId: string,
    ) {
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [
          DEFAULT_GIT_REPOSITORY_CATEGORY,
        ]
      if (categories.length <= 1 || !categories.includes(category)) {
        return false
      }
      const remaining = categories.filter((item) => item !== category)
      const fallback = remaining.includes(reassignTo)
        ? reassignTo
        : remaining[0]
      const now = new Date().toISOString()
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: remaining,
      }
      this.repositories = this.repositories.map((repository) =>
        repository.workspaceId === workspaceId &&
        repository.category === category
          ? { ...repository, category: fallback, updatedAt: now }
          : repository,
      )
      this.persist()
      return true
    },
    ensureCategory(category: string, workspaceId: string) {
      const value = category.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [
          DEFAULT_GIT_REPOSITORY_CATEGORY,
        ]
      if (!value || categories.includes(value)) return
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: [...categories, value],
      }
    },
    copyRepository(id: string, targetWorkspaceId: string) {
      const source = this.repositories.find(
        (repository) => repository.id === id,
      )
      if (
        !source ||
        source.workspaceId === targetWorkspaceId ||
        this.repositories.some(
          (repository) =>
            repository.workspaceId === targetWorkspaceId &&
            (source.source === 'remote'
              ? Boolean(
                  source.remoteUrl &&
                    repository.remoteUrl &&
                    remoteIdentity(repository.remoteUrl) ===
                      remoteIdentity(source.remoteUrl),
                )
              : repository.source === 'local' &&
                repository.path === source.path),
        )
      ) {
        return false
      }
      const now = new Date().toISOString()
      this.repositories = [
        {
          ...source,
          id: newGitRepositoryId(),
          workspaceId: targetWorkspaceId,
          createdAt: now,
          updatedAt: now,
        },
        ...this.repositories,
      ]
      this.ensureCategory(source.category, targetWorkspaceId)
      this.persist()
      return true
    },
    moveRepository(id: string, targetWorkspaceId: string) {
      const source = this.repositories.find(
        (repository) => repository.id === id,
      )
      if (
        !source ||
        source.workspaceId === targetWorkspaceId ||
        this.repositories.some(
          (repository) =>
            repository.workspaceId === targetWorkspaceId &&
            (source.source === 'remote'
              ? Boolean(
                  source.remoteUrl &&
                    repository.remoteUrl &&
                    remoteIdentity(repository.remoteUrl) ===
                      remoteIdentity(source.remoteUrl),
                )
              : repository.source === 'local' &&
                repository.path === source.path),
        )
      ) {
        return false
      }
      this.repositories = this.repositories.map((repository) =>
        repository.id === id
          ? {
              ...repository,
              workspaceId: targetWorkspaceId,
              updatedAt: new Date().toISOString(),
            }
          : repository,
      )
      this.ensureCategory(source.category, targetWorkspaceId)
      this.persist()
      return true
    },
  },
})
