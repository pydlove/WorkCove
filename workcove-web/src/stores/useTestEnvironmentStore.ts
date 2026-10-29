import { defineStore } from 'pinia'

import {
  createTestEnvironment,
  deleteTestEnvironment,
  listTestEnvironments,
  readTestEnvironmentCredentials,
  updateTestEnvironment,
} from '@/api/testEnvironments'
import type {
  TestEnvironment,
  TestEnvironmentInput,
} from '@/types/testEnvironment'

export const useTestEnvironmentStore = defineStore(
  'test-environments',
  {
    state: () => ({
      workspaceId: '',
      environments: [] as TestEnvironment[],
      loading: false,
      loaded: false,
      loadSequence: 0,
    }),
    actions: {
      async load(workspaceId: string, force = false) {
        if (!workspaceId) {
          this.clear()
          return
        }
        if (
          !force &&
          this.loaded &&
          this.workspaceId === workspaceId
        ) {
          return
        }
        const sequence = ++this.loadSequence
        const workspaceChanged = this.workspaceId !== workspaceId
        this.workspaceId = workspaceId
        this.loading = true
        if (workspaceChanged) {
          this.environments = []
          this.loaded = false
        }
        try {
          const environments =
            await listTestEnvironments(workspaceId)
          if (
            sequence !== this.loadSequence ||
            this.workspaceId !== workspaceId
          ) {
            return
          }
          this.environments = environments
          this.loaded = true
        } finally {
          if (
            sequence === this.loadSequence &&
            this.workspaceId === workspaceId
          ) {
            this.loading = false
          }
        }
      },
      async save(input: TestEnvironmentInput) {
        const workspaceId = this.workspaceId
        if (!workspaceId || input.workspaceId !== workspaceId) {
          throw new Error('测试环境不属于当前工作空间')
        }
        const saved = input.id
          ? await updateTestEnvironment(input)
          : await createTestEnvironment(input)
        if (this.workspaceId !== workspaceId) return saved
        const exists = this.environments.some(
          (environment) => environment.id === saved.id,
        )
        this.environments = exists
          ? this.environments.map((environment) =>
              environment.id === saved.id ? saved : environment,
            )
          : [saved, ...this.environments]
        return saved
      },
      async remove(id: number, workspaceId: string) {
        if (!workspaceId || workspaceId !== this.workspaceId) {
          throw new Error('测试环境不属于当前工作空间')
        }
        await deleteTestEnvironment(id, workspaceId)
        if (this.workspaceId !== workspaceId) return
        this.environments = this.environments.filter(
          (environment) => environment.id !== id,
        )
      },
      credentials(id: number, workspaceId: string) {
        return readTestEnvironmentCredentials(id, workspaceId)
      },
      clear() {
        this.workspaceId = ''
        this.environments = []
        this.loading = false
        this.loaded = false
        this.loadSequence += 1
      },
    },
  },
)
