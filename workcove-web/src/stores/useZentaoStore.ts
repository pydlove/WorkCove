import { defineStore } from 'pinia'

import {
  deleteZentaoBug,
  getZentaoConnection,
  listZentaoBugs,
  listZentaoProjects,
  resolveZentaoBug,
  resolveZentaoBugLocally,
  saveZentaoConnection,
  syncZentaoBugs,
} from '@/api/zentao'
import type {
  ZentaoBug,
  ZentaoConnection,
  ZentaoConnectionInput,
  ZentaoLocalResolveInput,
  ZentaoProjectInput,
  ZentaoResolveInput,
} from '@/types/zentao'

const EMPTY_CONNECTION: ZentaoConnection = {
  configured: false,
  baseUrl: '',
  username: '',
  password: '',
  projectIds: [],
  lastSyncedAt: null,
}

export const useZentaoStore = defineStore('zentao', {
  state: () => ({
    ownerEmail: '',
    workspaceId: '',
    connection: { ...EMPTY_CONNECTION } as ZentaoConnection,
    bugs: [] as ZentaoBug[],
    selectedBugId: 0,
    codexPrefillRequest: null as {
      bugIds: number[]
      prompt: string
      nonce: number
    } | null,
    codexTestPrefillRequest: null as {
      bugId: number
      runId: string
      evidenceDirectory: string
      nonce: number
    } | null,
    loading: false,
    syncing: false,
    error: '',
  }),
  getters: {
    activeBugs: (state) =>
      state.bugs.filter(
        (bug) => bug.active && bug.localStatus !== 'resolved',
      ),
    selectedBug(state): ZentaoBug | null {
      return (
        state.bugs.find((bug) => bug.id === state.selectedBugId) ?? null
      )
    },
  },
  actions: {
    async load(email: string, workspaceId: string, force = false) {
      const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail || !workspaceId) {
        this.ownerEmail = ''
        this.workspaceId = ''
        this.connection = { ...EMPTY_CONNECTION }
        this.bugs = []
        this.selectedBugId = 0
        this.codexPrefillRequest = null
        this.codexTestPrefillRequest = null
        return
      }
      if (
        !force &&
        this.ownerEmail === normalizedEmail &&
        this.workspaceId === workspaceId
      ) {
        return
      }

      this.ownerEmail = normalizedEmail
      this.workspaceId = workspaceId
      this.loading = true
      this.error = ''
      try {
        const [connection, bugs] = await Promise.all([
          getZentaoConnection(workspaceId),
          listZentaoBugs(workspaceId),
        ])
        this.connection = connection
        this.bugs = bugs
        if (
          this.selectedBugId &&
          !bugs.some((bug) => bug.id === this.selectedBugId)
        ) {
          this.selectedBugId = 0
        }
      } catch (error) {
        this.connection = { ...EMPTY_CONNECTION }
        this.bugs = []
        this.error =
          error instanceof Error ? error.message : '无法读取禅道数据'
      } finally {
        this.loading = false
      }
    },
    async saveConnection(input: ZentaoConnectionInput) {
      this.connection = await saveZentaoConnection(input)
      this.workspaceId = input.workspaceId
      return this.connection
    },
    async loadProjects(input: ZentaoProjectInput) {
      return listZentaoProjects(input)
    },
    async sync() {
      if (!this.workspaceId) return null
      this.syncing = true
      this.error = ''
      try {
        const result = await syncZentaoBugs(this.workspaceId)
        this.bugs = await listZentaoBugs(this.workspaceId)
        this.connection = {
          ...this.connection,
          configured: true,
          lastSyncedAt: result.syncedAt,
        }
        return result
      } catch (error) {
        this.error =
          error instanceof Error ? error.message : '同步禅道 Bug 失败'
        throw error
      } finally {
        this.syncing = false
      }
    },
    selectBug(id: number) {
      this.selectedBugId = id
    },
    requestCodexPrefill(
      bugIds: number | number[],
      prompt: string,
    ) {
      const normalizedBugIds = [
        ...new Set(
          (Array.isArray(bugIds) ? bugIds : [bugIds]).filter(
            (bugId) => bugId > 0,
          ),
        ),
      ]
      this.selectedBugId = normalizedBugIds[0] ?? 0
      this.codexPrefillRequest = {
        bugIds: normalizedBugIds,
        prompt,
        nonce: Date.now() + Math.random(),
      }
    },
    clearCodexPrefillRequest() {
      this.codexPrefillRequest = null
    },
    requestCodexTest(
      bugId: number,
      runId: string,
      evidenceDirectory: string,
    ) {
      this.selectedBugId = bugId
      this.codexTestPrefillRequest = {
        bugId,
        runId,
        evidenceDirectory,
        nonce: Date.now() + Math.random(),
      }
    },
    clearCodexTestPrefillRequest() {
      this.codexTestPrefillRequest = null
    },
    async resolveBug(id: number, input: Omit<ZentaoResolveInput, 'workspaceId'>) {
      const updated = await resolveZentaoBug(id, {
        ...input,
        workspaceId: this.workspaceId,
      })
      this.bugs = this.bugs.map((bug) =>
        bug.id === updated.id ? updated : bug,
      )
      return updated
    },
    async resolveBugLocally(
      id: number,
      input: Omit<ZentaoLocalResolveInput, 'workspaceId'>,
    ) {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      const updated = await resolveZentaoBugLocally(
        id,
        {
          ...input,
          workspaceId: this.workspaceId,
        },
      )
      this.bugs = this.bugs.map((bug) =>
        bug.id === updated.id ? updated : bug,
      )
      return updated
    },
    async deleteBug(id: number) {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      await deleteZentaoBug(id, this.workspaceId)
      this.bugs = this.bugs.filter((bug) => bug.id !== id)
      if (this.selectedBugId === id) this.selectedBugId = 0
    },
  },
})
