import { defineStore } from 'pinia'

import {
  loadLocalWorkNotes,
  normalizeWorkNoteTags,
  saveLocalWorkNotes,
} from '@/services/localWorkNotes'
import type { WorkNote, WorkNoteInput } from '@/types/workNote'

function derivedTitle(content: string) {
  const firstLine =
    content
      .split('\n')
      .map((line) => line.trim())
      .find(Boolean) ?? ''
  return (firstLine.replace(/^#+\s*/, '').slice(0, 64) || '未命名记录')
}

export const useWorkNoteStore = defineStore('work-notes', {
  state: () => ({
    ownerEmail: '',
    notes: [] as WorkNote[],
    requestedNoteId: '',
  }),
  actions: {
    load(email: string) {
      const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.notes = []
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.notes = loadLocalWorkNotes(normalizedEmail)
    },
    save(input: WorkNoteInput) {
      const content = input.content.trim()
      if (!content) return null
      if (content.length > 20_000) {
        throw new Error('单条随笔不能超过 20000 个字符')
      }

      const existing = input.id
        ? this.notes.find((note) => note.id === input.id)
        : null
      const now = new Date().toISOString()
      const titleAuto = !input.title.trim()
      const note: WorkNote = {
        id: existing?.id ?? window.crypto.randomUUID(),
        workspaceId:
          existing?.workspaceId ??
          input.workspaceId,
        title:
          input.title.trim().slice(0, 80) ||
          derivedTitle(content),
        titleAuto,
        content: input.content,
        important: input.important,
        resolved: input.resolved,
        pinned: input.pinned,
        archived: input.archived,
        tags: normalizeWorkNoteTags(input.tags),
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
      }
      this.notes = existing
        ? this.notes.map((item) =>
            item.id === existing.id ? note : item,
          )
        : [note, ...this.notes]
      this.persist()
      return note
    },
    remove(id: string) {
      this.notes = this.notes.filter((note) => note.id !== id)
      this.persist()
    },
    requestOpen(id: string) {
      this.requestedNoteId = id
    },
    clearOpenRequest() {
      this.requestedNoteId = ''
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalWorkNotes(this.ownerEmail, this.notes)
    },
  },
})
