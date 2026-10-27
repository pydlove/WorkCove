import { defineStore } from 'pinia'

import {
  loadLocalShortcuts,
  newShortcutId,
  saveLocalShortcuts,
} from '@/services/localShortcuts'
import type {
  ApplicationShortcut,
  ShortcutInput,
  WebsiteShortcut,
} from '@/types/shortcut'

export const useShortcutStore = defineStore('shortcuts', {
  state: () => ({
    ownerEmail: '',
    websites: [] as WebsiteShortcut[],
    applications: [] as ApplicationShortcut[],
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US') || 'preview@workcove.local'
      if (this.ownerEmail === normalizedEmail) return

      const shortcuts = loadLocalShortcuts(normalizedEmail)
      this.ownerEmail = normalizedEmail
      this.websites = shortcuts.websites
      this.applications = shortcuts.applications
    },
    persist() {
      saveLocalShortcuts(this.ownerEmail, {
        websites: this.websites,
        applications: this.applications,
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
        const website: WebsiteShortcut = {
          id,
          name: input.name,
          url: input.target,
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
      } else {
        const application: ApplicationShortcut = {
          id,
          name: input.name,
          path: input.target,
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
    removeShortcut(id: string) {
      this.websites = this.websites.filter((item) => item.id !== id)
      this.applications = this.applications.filter((item) => item.id !== id)
      this.persist()
    },
  },
})
