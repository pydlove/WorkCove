import { defineStore } from 'pinia'

import {
  loadLocalZentaoTestResults,
  saveLocalZentaoTestResults,
} from '@/services/localZentaoTestResults'
import type { ZentaoTestResult } from '@/types/zentaoTest'

export const useZentaoTestStore = defineStore('zentao-tests', {
  state: () => ({
    ownerEmail: '',
    results: [] as ZentaoTestResult[],
  }),
  actions: {
    load(email: string) {
      const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.results = []
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.results = loadLocalZentaoTestResults(normalizedEmail)
    },
    saveResult(result: ZentaoTestResult) {
      this.results = [
        result,
        ...this.results.filter((item) => item.id !== result.id),
      ]
        .sort((left, right) =>
          right.createdAt.localeCompare(left.createdAt),
        )
        .slice(0, 200)
      this.persist()
    },
    latestForBug(bugId: number) {
      return (
        this.results.find((result) => result.bugId === bugId) ?? null
      )
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalZentaoTestResults(this.ownerEmail, this.results)
    },
  },
})
