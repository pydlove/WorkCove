import { defineStore } from 'pinia'

import {
  confirmZentaoBug,
  createZentaoBuild,
  createZentaoTestTask,
  deleteZentaoBug,
  getZentaoConnection,
  listZentaoBuilds,
  listZentaoBuildOptions,
  listZentaoBugs,
  listZentaoProjects,
  listZentaoSystems,
  resolveZentaoBug,
  resolveZentaoBugLocally,
  listZentaoTestTaskOptions,
  saveZentaoConnection,
  syncZentaoBugs,
} from '@/api/zentao'
import type {
  ZentaoBug,
  ZentaoBuild,
  ZentaoBuildCreateInput,
  ZentaoBuildOptions,
  ZentaoConnection,
  ZentaoConnectionInput,
  ZentaoCreatedBuild,
  ZentaoCreatedTestTask,
  ZentaoLocalResolveInput,
  ZentaoProjectInput,
  ZentaoResolveInput,
  ZentaoSystem,
  ZentaoTestTaskCreateInput,
  ZentaoTestTaskOptions,
} from '@/types/zentao'
import type { TestEnvironment } from '@/types/testEnvironment'

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
    builds: [] as ZentaoBuild[],
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
      environment: TestEnvironment
      username: string
      password: string
      nonce: number
    } | null,
    loading: false,
    syncing: false,
    error: '',
    loadSequence: 0,
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
        this.builds = []
        this.selectedBugId = 0
        this.codexPrefillRequest = null
        this.codexTestPrefillRequest = null
        this.loading = false
        this.syncing = false
        this.error = ''
        this.loadSequence += 1
        return
      }
      if (
        !force &&
        this.ownerEmail === normalizedEmail &&
        this.workspaceId === workspaceId
      ) {
        return
      }

      const sequence = ++this.loadSequence
      const workspaceChanged = this.workspaceId !== workspaceId
      this.ownerEmail = normalizedEmail
      this.workspaceId = workspaceId
      this.loading = true
      this.error = ''
      if (workspaceChanged) {
        this.connection = { ...EMPTY_CONNECTION }
        this.bugs = []
        this.builds = []
        this.selectedBugId = 0
        this.codexPrefillRequest = null
        this.codexTestPrefillRequest = null
      }
      try {
        const [connection, bugs, builds] = await Promise.all([
          getZentaoConnection(workspaceId),
          listZentaoBugs(workspaceId),
          listZentaoBuilds(workspaceId),
        ])
        if (
          sequence !== this.loadSequence ||
          this.workspaceId !== workspaceId
        ) {
          return
        }
        this.connection = connection
        this.bugs = bugs
        this.builds = builds
        if (
          this.selectedBugId &&
          !bugs.some((bug) => bug.id === this.selectedBugId)
        ) {
          this.selectedBugId = 0
        }
      } catch (error) {
        if (
          sequence !== this.loadSequence ||
          this.workspaceId !== workspaceId
        ) {
          return
        }
        this.connection = { ...EMPTY_CONNECTION }
        this.bugs = []
        this.builds = []
        this.error =
          error instanceof Error ? error.message : '无法读取禅道数据'
      } finally {
        if (
          sequence === this.loadSequence &&
          this.workspaceId === workspaceId
        ) {
          this.loading = false
        }
      }
    },
    async saveConnection(input: ZentaoConnectionInput) {
      const workspaceId = this.workspaceId
      if (!workspaceId || input.workspaceId !== workspaceId) {
        throw new Error('禅道配置不属于当前工作空间')
      }
      const connection = await saveZentaoConnection(input)
      if (this.workspaceId === workspaceId) {
        this.connection = connection
      }
      return connection
    },
    async loadProjects(input: ZentaoProjectInput) {
      return listZentaoProjects(input)
    },
    async loadBuildOptions(): Promise<ZentaoBuildOptions> {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      return listZentaoBuildOptions(this.workspaceId)
    },
    async loadSystems(productId: string): Promise<ZentaoSystem[]> {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      return listZentaoSystems(this.workspaceId, productId)
    },
    async loadTestTaskOptions(
      productId: string,
    ): Promise<ZentaoTestTaskOptions> {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      return listZentaoTestTaskOptions(this.workspaceId, productId)
    },
    async createBuild(
      input: Omit<ZentaoBuildCreateInput, 'workspaceId'>,
    ): Promise<ZentaoCreatedBuild> {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      const created = await createZentaoBuild({
        ...input,
        workspaceId: this.workspaceId,
      })
      return created
    },
    async createTestTask(
      buildId: number,
      input: Omit<ZentaoTestTaskCreateInput, 'workspaceId'>,
    ): Promise<ZentaoCreatedTestTask> {
      if (!this.workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      return createZentaoTestTask(buildId, {
        ...input,
        workspaceId: this.workspaceId,
      })
    },
    async sync() {
      const workspaceId = this.workspaceId
      if (!workspaceId) return null
      this.syncing = true
      this.error = ''
      try {
        const result = await syncZentaoBugs(workspaceId)
        const [bugs, builds] = await Promise.all([
          listZentaoBugs(workspaceId),
          listZentaoBuilds(workspaceId),
        ])
        if (this.workspaceId !== workspaceId) return result
        this.bugs = bugs
        this.builds = builds
        this.connection = {
          ...this.connection,
          configured: true,
          lastSyncedAt: result.syncedAt,
        }
        return result
      } catch (error) {
        if (this.workspaceId === workspaceId) {
          this.error =
            error instanceof Error ? error.message : '同步禅道 Bug 失败'
        }
        throw error
      } finally {
        if (this.workspaceId === workspaceId) {
          this.syncing = false
        }
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
      environment: TestEnvironment,
      username: string,
      password: string,
    ) {
      this.selectedBugId = bugId
      this.codexTestPrefillRequest = {
        bugId,
        runId,
        evidenceDirectory,
        environment,
        username,
        password,
        nonce: Date.now() + Math.random(),
      }
    },
    clearCodexTestPrefillRequest() {
      this.codexTestPrefillRequest = null
    },
    async resolveBug(id: number, input: Omit<ZentaoResolveInput, 'workspaceId'>) {
      const workspaceId = this.workspaceId
      const updated = await resolveZentaoBug(id, {
        ...input,
        workspaceId,
      })
      if (this.workspaceId !== workspaceId) return updated
      this.bugs = this.bugs.map((bug) =>
        bug.id === updated.id ? updated : bug,
      )
      return updated
    },
    async confirmBug(id: number) {
      const workspaceId = this.workspaceId
      if (!workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      const updated = await confirmZentaoBug(id, workspaceId)
      if (this.workspaceId !== workspaceId) return updated
      this.bugs = this.bugs.map((bug) =>
        bug.id === updated.id ? updated : bug,
      )
      return updated
    },
    async resolveBugLocally(
      id: number,
      input: Omit<ZentaoLocalResolveInput, 'workspaceId'>,
    ) {
      const workspaceId = this.workspaceId
      if (!workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      const updated = await resolveZentaoBugLocally(
        id,
        {
          ...input,
          workspaceId,
        },
      )
      if (this.workspaceId !== workspaceId) return updated
      this.bugs = this.bugs.map((bug) =>
        bug.id === updated.id ? updated : bug,
      )
      return updated
    },
    async deleteBug(id: number) {
      const workspaceId = this.workspaceId
      if (!workspaceId) {
        throw new Error('工作空间尚未初始化')
      }
      await deleteZentaoBug(id, workspaceId)
      if (this.workspaceId !== workspaceId) return
      this.bugs = this.bugs.filter((bug) => bug.id !== id)
      if (this.selectedBugId === id) this.selectedBugId = 0
    },
  },
})
