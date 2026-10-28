import { defineStore } from 'pinia'

import {
  DEFAULT_WEBSITE_CATEGORY,
  loadLocalShortcuts,
  newShortcutId,
  saveLocalShortcuts,
} from '@/services/localShortcuts'
import type {
  ApplicationShortcut,
  ShortcutInput,
  WebsiteShortcut,
} from '@/types/shortcut'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

export const useShortcutStore = defineStore('shortcuts', {
  state: () => ({
    ownerEmail: '',
    websites: [] as WebsiteShortcut[],
    applications: [] as ApplicationShortcut[],
    categoriesByWorkspace: {
      [DEFAULT_WORKSPACE_ID]: [DEFAULT_WEBSITE_CATEGORY],
    } as Record<string, string[]>,
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.websites = []
        this.applications = []
        this.categoriesByWorkspace = {
          [DEFAULT_WORKSPACE_ID]: [DEFAULT_WEBSITE_CATEGORY],
        }
        return
      }
      if (this.ownerEmail === normalizedEmail) {
        this.normalizeCategoryState()
        return
      }

      const shortcuts = loadLocalShortcuts(normalizedEmail)
      this.ownerEmail = normalizedEmail
      this.websites = shortcuts.websites
      this.applications = shortcuts.applications
      this.categoriesByWorkspace = shortcuts.categoriesByWorkspace
      this.normalizeCategoryState()
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalShortcuts(this.ownerEmail, {
        websites: this.websites,
        applications: this.applications,
        categoriesByWorkspace: this.categoriesByWorkspace,
      })
    },
    saveShortcut(input: ShortcutInput) {
      const existingWebsite = input.id
        ? this.websites.find((item) => item.id === input.id)
        : null
      const existingApplication = input.id
        ? this.applications.find((item) => item.id === input.id)
        : null
      const createdAt =
        existingWebsite?.createdAt ??
        existingApplication?.createdAt ??
        new Date().toISOString()
      const id = input.id ?? newShortcutId()

      if (input.mode === 'website') {
        const category = input.category?.trim() || DEFAULT_WEBSITE_CATEGORY
        const website: WebsiteShortcut = {
          id,
          workspaceId:
            input.workspaceId ??
            existingWebsite?.workspaceId ??
            DEFAULT_WORKSPACE_ID,
          name: input.name,
          url: input.target,
          category,
          clickCount: existingWebsite?.clickCount ?? 0,
          lastClickedAt: existingWebsite?.lastClickedAt ?? null,
          iconMode: input.iconMode,
          iconDataUrl: input.iconDataUrl,
          createdAt,
        }
        this.websites = existingWebsite
          ? this.websites.map((item) => (item.id === id ? website : item))
          : [...this.websites, website]
        this.applications = this.applications.filter(
          (item) => item.id !== id,
        )
        this.ensureCategory(category, website.workspaceId)
      } else {
        const application: ApplicationShortcut = {
          id,
          workspaceId:
            input.workspaceId ??
            existingApplication?.workspaceId ??
            DEFAULT_WORKSPACE_ID,
          name: input.name,
          path: input.target,
          clickCount: existingApplication?.clickCount ?? 0,
          lastOpenedAt: existingApplication?.lastOpenedAt ?? null,
          iconMode: input.iconMode,
          iconDataUrl: input.iconDataUrl,
          createdAt,
        }
        this.applications = existingApplication
          ? this.applications.map((item) =>
              item.id === id ? application : item,
            )
          : [...this.applications, application]
        this.websites = this.websites.filter((item) => item.id !== id)
      }

      this.persist()
    },
    addCategory(rawCategory: string, workspaceId: string) {
      this.normalizeCategoryState()
      const category = rawCategory.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [DEFAULT_WEBSITE_CATEGORY]
      if (
        !category ||
        [...category].length > 24 ||
        category === '全部' ||
        categories.includes(category)
      ) {
        return false
      }
      this.ensureCategory(category, workspaceId)
      this.persist()
      return true
    },
    renameCategory(
      currentCategory: string,
      nextCategory: string,
      workspaceId: string,
    ) {
      this.normalizeCategoryState()
      const current = currentCategory.trim()
      const next = nextCategory.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [DEFAULT_WEBSITE_CATEGORY]
      if (
        !current ||
        !next ||
        [...next].length > 24 ||
        next === '全部' ||
        current === DEFAULT_WEBSITE_CATEGORY ||
        !categories.includes(current) ||
        (current !== next && categories.includes(next))
      ) {
        return false
      }
      if (current === next) return true

      const previousCategories = this.categoriesByWorkspace
      const previousWebsites = this.websites
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: categories.map((category) =>
          category === current ? next : category,
        ),
      }
      this.websites = this.websites.map((website) =>
        website.workspaceId === workspaceId &&
        website.category === current
          ? { ...website, category: next }
          : website,
      )
      try {
        this.persist()
      } catch (error) {
        this.categoriesByWorkspace = previousCategories
        this.websites = previousWebsites
        throw error
      }
      return true
    },
    deleteCategory(
      category: string,
      reassignTo: string,
      workspaceId: string,
    ) {
      this.normalizeCategoryState()
      const next = category.trim()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [DEFAULT_WEBSITE_CATEGORY]
      if (
        !next ||
        next === DEFAULT_WEBSITE_CATEGORY ||
        !categories.includes(next)
      ) {
        return false
      }

      const remaining = categories.filter((item) => item !== next)
      const fallback = remaining.includes(reassignTo.trim())
        ? reassignTo.trim()
        : remaining[0]
      if (!fallback) return false

      const previousCategories = this.categoriesByWorkspace
      const previousWebsites = this.websites
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: remaining,
      }
      this.websites = this.websites.map((website) =>
        website.workspaceId === workspaceId &&
        website.category === next
          ? { ...website, category: fallback }
          : website,
      )
      try {
        this.persist()
      } catch (error) {
        this.categoriesByWorkspace = previousCategories
        this.websites = previousWebsites
        throw error
      }
      return true
    },
    ensureCategory(category: string, workspaceId: string) {
      this.normalizeCategoryState()
      const categories =
        this.categoriesByWorkspace[workspaceId] ?? [DEFAULT_WEBSITE_CATEGORY]
      if (!category || categories.includes(category)) return
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: [...categories, category],
      }
    },
    normalizeCategoryState() {
      const source =
        this.categoriesByWorkspace &&
        typeof this.categoriesByWorkspace === 'object'
          ? this.categoriesByWorkspace
          : {}
      const workspaceIds = new Set([
        DEFAULT_WORKSPACE_ID,
        ...Object.keys(source),
        ...this.websites.map((website) => website.workspaceId),
      ])
      const categoriesByWorkspace: Record<string, string[]> = {}
      for (const workspaceId of workspaceIds) {
        categoriesByWorkspace[workspaceId] = [
          ...new Set([
            ...(Array.isArray(source[workspaceId])
              ? source[workspaceId]
              : []),
            ...this.websites
              .filter((website) => website.workspaceId === workspaceId)
              .map((website) => website.category),
            DEFAULT_WEBSITE_CATEGORY,
          ]),
        ]
      }
      this.categoriesByWorkspace = categoriesByWorkspace
    },
    reorderCategories(categories: string[], workspaceId: string) {
      const current =
        this.categoriesByWorkspace[workspaceId] ?? [DEFAULT_WEBSITE_CATEGORY]
      const known = new Set(current)
      const ordered = [...new Set(categories)].filter((category) =>
        known.has(category),
      )
      this.categoriesByWorkspace = {
        ...this.categoriesByWorkspace,
        [workspaceId]: [
          ...ordered,
          ...current.filter((category) => !ordered.includes(category)),
        ],
      }
      this.persist()
    },
    moveWebsiteToCategory(id: string, category: string) {
      const nextCategory = category.trim()
      const website = this.websites.find((item) => item.id === id)
      if (!website || !nextCategory || website.category === nextCategory) {
        return false
      }

      this.websites = this.websites.map((item) =>
        item.id === id ? { ...item, category: nextCategory } : item,
      )
      this.ensureCategory(nextCategory, website.workspaceId)
      this.persist()
      return true
    },
    recordApplicationOpen(id: string) {
      let changed = false
      const lastOpenedAt = new Date().toISOString()
      this.applications = this.applications.map((application) => {
        if (application.id !== id) return application
        changed = true
        return {
          ...application,
          clickCount: application.clickCount + 1,
          lastOpenedAt,
        }
      })
      if (changed) this.persist()
    },
    recordWebsiteClick(id: string) {
      let changed = false
      this.websites = this.websites.map((website) => {
        if (website.id !== id) return website
        changed = true
        return {
          ...website,
          clickCount: website.clickCount + 1,
          lastClickedAt: new Date().toISOString(),
        }
      })
      if (changed) this.persist()
    },
    copyApplications(ids: string[], targetWorkspaceId: string) {
      const selectedIds = new Set(ids)
      const applications = this.applications.filter(
        (application) =>
          selectedIds.has(application.id) &&
          application.workspaceId !== targetWorkspaceId,
      )
      if (applications.length === 0) return 0

      const createdAt = new Date().toISOString()
      const copies = applications.map<ApplicationShortcut>(
        (application) => ({
          ...application,
          id: newShortcutId(),
          workspaceId: targetWorkspaceId,
          clickCount: 0,
          lastOpenedAt: null,
          createdAt,
        }),
      )
      const previousApplications = this.applications
      this.applications = [...copies, ...this.applications]
      try {
        this.persist()
      } catch (error) {
        this.applications = previousApplications
        throw error
      }
      return copies.length
    },
    moveApplications(ids: string[], targetWorkspaceId: string) {
      const selectedIds = new Set(ids)
      const applications = this.applications.filter(
        (application) =>
          selectedIds.has(application.id) &&
          application.workspaceId !== targetWorkspaceId,
      )
      if (applications.length === 0) return 0

      const previousApplications = this.applications
      this.applications = this.applications.map((application) =>
        selectedIds.has(application.id)
          ? { ...application, workspaceId: targetWorkspaceId }
          : application,
      )
      try {
        this.persist()
      } catch (error) {
        this.applications = previousApplications
        throw error
      }
      return applications.length
    },
    removeApplications(ids: string[]) {
      const selectedIds = new Set(ids)
      const nextApplications = this.applications.filter(
        (application) => !selectedIds.has(application.id),
      )
      const removed = this.applications.length - nextApplications.length
      if (removed === 0) return 0

      const previousApplications = this.applications
      this.applications = nextApplications
      try {
        this.persist()
      } catch (error) {
        this.applications = previousApplications
        throw error
      }
      return removed
    },
    copyShortcut(id: string, targetWorkspaceId: string) {
      const website = this.websites.find((item) => item.id === id)
      if (website) {
        if (website.workspaceId === targetWorkspaceId) return false
        const copy: WebsiteShortcut = {
          ...website,
          id: newShortcutId(),
          workspaceId: targetWorkspaceId,
          clickCount: 0,
          lastClickedAt: null,
          createdAt: new Date().toISOString(),
        }
        this.websites = [copy, ...this.websites]
        this.ensureCategory(copy.category, copy.workspaceId)
        this.persist()
        return true
      }

      const application = this.applications.find(
        (item) => item.id === id,
      )
      if (!application || application.workspaceId === targetWorkspaceId) {
        return false
      }
      const copy: ApplicationShortcut = {
        ...application,
        id: newShortcutId(),
        workspaceId: targetWorkspaceId,
        clickCount: 0,
        lastOpenedAt: null,
        createdAt: new Date().toISOString(),
      }
      this.applications = [copy, ...this.applications]
      this.persist()
      return true
    },
    moveShortcut(id: string, targetWorkspaceId: string) {
      const website = this.websites.find((item) => item.id === id)
      if (website) {
        if (website.workspaceId === targetWorkspaceId) return false
        this.websites = this.websites.map((item) =>
          item.id === id
            ? { ...item, workspaceId: targetWorkspaceId }
            : item,
        )
        this.persist()
        return true
      }

      const application = this.applications.find(
        (item) => item.id === id,
      )
      if (!application || application.workspaceId === targetWorkspaceId) {
        return false
      }
      this.applications = this.applications.map((item) =>
        item.id === id
          ? { ...item, workspaceId: targetWorkspaceId }
          : item,
      )
      this.persist()
      return true
    },
    removeShortcut(id: string) {
      this.websites = this.websites.filter((item) => item.id !== id)
      this.applications = this.applications.filter((item) => item.id !== id)
      this.persist()
    },
  },
})
