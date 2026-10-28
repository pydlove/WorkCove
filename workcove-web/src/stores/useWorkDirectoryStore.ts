import { defineStore } from 'pinia'

import {
  loadLocalWorkDirectories,
  loadLocalWorkDirectoryCategories,
  newWorkDirectoryId,
  saveLocalWorkDirectories,
  saveLocalWorkDirectoryCategories,
} from '@/services/localWorkDirectories'
import type { WorkDirectoryCategoriesByWorkspace } from '@/services/localWorkDirectories'
import {
  DEFAULT_WORK_DIRECTORY_CATEGORY,
  type WorkDirectory,
  type WorkDirectoryInput,
} from '@/types/workDirectory'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

function normalizePath(value: string) {
  const path = value.trim().replace(/\/+$/, '')
  return path || '/'
}

function normalizeCategories(
  categoriesByWorkspace: WorkDirectoryCategoriesByWorkspace,
  directories: WorkDirectory[],
) {
  const workspaceIds = new Set([
    DEFAULT_WORKSPACE_ID,
    ...Object.keys(categoriesByWorkspace),
    ...directories.map((directory) => directory.workspaceId),
  ])
  const normalized: WorkDirectoryCategoriesByWorkspace = {}

  for (const workspaceId of workspaceIds) {
    const categories = [
      ...new Set([
        ...(categoriesByWorkspace[workspaceId] ?? []),
        ...directories
          .filter((directory) => directory.workspaceId === workspaceId)
          .map((directory) => directory.category),
        DEFAULT_WORK_DIRECTORY_CATEGORY,
      ]),
    ]
      .map((category) => category.trim())
      .filter((category) => category && category !== '全部')

    normalized[workspaceId] =
      categories.length > 0
        ? categories
        : [DEFAULT_WORK_DIRECTORY_CATEGORY]
  }

  return normalized
}

export const useWorkDirectoryStore = defineStore('workDirectories', {
  state: () => ({
    ownerEmail: '',
    directories: [] as WorkDirectory[],
    categoriesByWorkspace: {
      [DEFAULT_WORKSPACE_ID]: [DEFAULT_WORK_DIRECTORY_CATEGORY],
    } as WorkDirectoryCategoriesByWorkspace,
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.directories = []
        this.categoriesByWorkspace = {
          [DEFAULT_WORKSPACE_ID]: [DEFAULT_WORK_DIRECTORY_CATEGORY],
        }
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.directories = loadLocalWorkDirectories(normalizedEmail)
      this.categoriesByWorkspace = normalizeCategories(
        loadLocalWorkDirectoryCategories(normalizedEmail),
        this.directories,
      )
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalWorkDirectories(this.ownerEmail, this.directories)
      saveLocalWorkDirectoryCategories(
        this.ownerEmail,
        this.categoriesByWorkspace,
      )
    },
    saveDirectory(input: WorkDirectoryInput) {
      const existing = input.id
        ? this.directories.find((directory) => directory.id === input.id)
        : null
      const now = new Date().toISOString()
      const directory: WorkDirectory = {
        id: input.id ?? newWorkDirectoryId(),
        workspaceId:
          input.workspaceId ??
          existing?.workspaceId ??
          DEFAULT_WORKSPACE_ID,
        name: input.name.trim(),
        path: normalizePath(input.path),
        category:
          input.category.trim() || DEFAULT_WORK_DIRECTORY_CATEGORY,
        description: input.description.trim(),
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
      }

      this.directories = existing
        ? this.directories.map((item) =>
            item.id === directory.id ? directory : item,
          )
        : [directory, ...this.directories]
      this.ensureCategory(directory.category, directory.workspaceId)
      this.persist()
    },
    addCategory(rawCategory: string, workspaceId: string) {
      const category = rawCategory.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [
          DEFAULT_WORK_DIRECTORY_CATEGORY,
        ]
      if (
        !category ||
        [...category].length > 24 ||
        category === '全部' ||
        categories.includes(category)
      ) {
        return false
      }
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: [...categories, category],
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
          DEFAULT_WORK_DIRECTORY_CATEGORY,
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
      this.directories = this.directories.map((directory) =>
        directory.workspaceId === workspaceId &&
        directory.category === current
          ? { ...directory, category: next, updatedAt: now }
          : directory,
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
          DEFAULT_WORK_DIRECTORY_CATEGORY,
        ]
      if (
        categories.length <= 1 ||
        !categories.includes(category)
      ) {
        return false
      }

      const remaining = categories.filter(
        (item) => item !== category,
      )
      const fallback = remaining.includes(reassignTo)
        ? reassignTo
        : remaining[0]
      const now = new Date().toISOString()

      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: remaining,
      }
      this.directories = this.directories.map((directory) =>
        directory.workspaceId === workspaceId &&
        directory.category === category
          ? { ...directory, category: fallback, updatedAt: now }
          : directory,
      )
      this.persist()
      return true
    },
    ensureCategory(category: string, workspaceId: string) {
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [
          DEFAULT_WORK_DIRECTORY_CATEGORY,
        ]
      if (!category || categories.includes(category)) return
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: [...categories, category],
      }
    },
    copyDirectory(id: string, targetWorkspaceId: string) {
      const source = this.directories.find(
        (directory) => directory.id === id,
      )
      if (!source || source.workspaceId === targetWorkspaceId) return false
      const now = new Date().toISOString()
      const copy: WorkDirectory = {
        ...source,
        id: newWorkDirectoryId(),
        workspaceId: targetWorkspaceId,
        createdAt: now,
        updatedAt: now,
      }
      this.directories = [copy, ...this.directories]
      this.ensureCategory(copy.category, copy.workspaceId)
      this.persist()
      return true
    },
    moveDirectory(id: string, targetWorkspaceId: string) {
      const source = this.directories.find(
        (directory) => directory.id === id,
      )
      if (!source || source.workspaceId === targetWorkspaceId) return false
      this.directories = this.directories.map((directory) =>
        directory.id === id
          ? {
              ...directory,
              workspaceId: targetWorkspaceId,
              updatedAt: new Date().toISOString(),
            }
          : directory,
      )
      this.ensureCategory(source.category, targetWorkspaceId)
      this.persist()
      return true
    },
    removeDirectory(id: string) {
      this.directories = this.directories.filter(
        (directory) => directory.id !== id,
      )
      this.persist()
    },
  },
})
