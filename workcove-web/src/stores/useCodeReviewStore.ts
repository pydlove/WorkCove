import { defineStore } from 'pinia'

import {
  loadLocalCodeReviews,
  saveLocalCodeReviews,
} from '@/services/localCodeReviews'
import type {
  CodeReviewInput,
  CodeReviewRecord,
} from '@/types/codeReview'

export const useCodeReviewStore = defineStore('codeReviews', {
  state: () => ({
    ownerEmail: '',
    records: [] as CodeReviewRecord[],
  }),
  getters: {
    recordById: (state) => (id: string) =>
      state.records.find((record) => record.id === id) ?? null,
  },
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.records = []
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.records = loadLocalCodeReviews(normalizedEmail)
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalCodeReviews(this.ownerEmail, this.records)
    },
    create(input: CodeReviewInput) {
      const now = new Date().toISOString()
      const record: CodeReviewRecord = {
        id: window.crypto.randomUUID(),
        ...input,
        previousReviewId: input.previousReviewId ?? null,
        status: 'reviewing',
        summary: '',
        findings: [],
        manualNote: '',
        rawResult: '',
        codexSessionId: '',
        codexTaskId: '',
        repairSessionId: '',
        repairTaskId: '',
        createdAt: now,
        updatedAt: now,
        reviewedAt: null,
      }
      this.records = [record, ...this.records]
      this.persist()
      return record
    },
    update(
      id: string,
      patch: Partial<CodeReviewRecord>,
    ) {
      let updated: CodeReviewRecord | null = null
      this.records = this.records.map((record) => {
        if (record.id !== id) return record
        updated = {
          ...record,
          ...patch,
          updatedAt: new Date().toISOString(),
        }
        return updated
      })
      if (updated) this.persist()
      return updated
    },
    remove(id: string) {
      this.records = this.records.filter(
        (record) => record.id !== id,
      )
      this.persist()
    },
  },
})
