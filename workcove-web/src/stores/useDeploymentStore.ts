import { defineStore } from 'pinia'

import { executeDeploymentScript } from '@/api/deployments'
import {
  loadLocalDeployments,
  newDeploymentId,
  saveLocalDeployments,
} from '@/services/localDeployments'
import type {
  DeploymentConfig,
  DeploymentInput,
  DeploymentRun,
} from '@/types/deployment'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '部署执行失败'
}

export const useDeploymentStore = defineStore('deployments', {
  state: () => ({
    ownerEmail: '',
    deployments: [] as DeploymentConfig[],
    runs: [] as DeploymentRun[],
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.deployments = []
        this.runs = []
        return
      }
      if (this.ownerEmail === normalizedEmail) return

      const state = loadLocalDeployments(normalizedEmail)
      this.ownerEmail = normalizedEmail
      this.deployments = state.deployments
      this.runs = state.runs
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalDeployments(this.ownerEmail, {
        deployments: this.deployments,
        runs: this.runs,
      })
    },
    saveDeployment(input: DeploymentInput) {
      const existing = input.id
        ? this.deployments.find((item) => item.id === input.id)
        : null
      const now = new Date().toISOString()
      const deployment: DeploymentConfig = {
        id: input.id ?? newDeploymentId(),
        workspaceId:
          input.workspaceId ??
          existing?.workspaceId ??
          DEFAULT_WORKSPACE_ID,
        name: input.name.trim(),
        serverId: input.serverId,
        serverName: input.serverName.trim(),
        remoteDirectory: input.remoteDirectory.trim(),
        scriptFilename: input.scriptFilename.trim(),
        scriptContent: input.scriptContent,
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
      }
      this.deployments = existing
        ? this.deployments.map((item) =>
            item.id === deployment.id ? deployment : item,
          )
        : [deployment, ...this.deployments]
      this.persist()
      return deployment
    },
    copyDeployments(ids: string[], targetWorkspaceId: string) {
      const selectedIds = new Set(ids)
      const sources = this.deployments.filter(
        (deployment) =>
          selectedIds.has(deployment.id) &&
          deployment.workspaceId !== targetWorkspaceId,
      )
      if (sources.length === 0) return 0

      const now = new Date().toISOString()
      const copies = sources.map<DeploymentConfig>((deployment) => ({
        ...deployment,
        id: newDeploymentId(),
        workspaceId: targetWorkspaceId,
        serverId: null,
        serverName: '',
        createdAt: now,
        updatedAt: now,
      }))
      this.deployments = [...copies, ...this.deployments]
      this.persist()
      return copies.length
    },
    moveDeployments(ids: string[], targetWorkspaceId: string) {
      const selectedIds = new Set(ids)
      const moved = this.deployments.filter(
        (deployment) =>
          selectedIds.has(deployment.id) &&
          deployment.workspaceId !== targetWorkspaceId,
      )
      if (moved.length === 0) return 0

      const now = new Date().toISOString()
      this.deployments = this.deployments.map((deployment) =>
        selectedIds.has(deployment.id)
          ? {
              ...deployment,
              workspaceId: targetWorkspaceId,
              serverId: null,
              serverName: '',
              updatedAt: now,
            }
          : deployment,
      )
      this.runs = this.runs.map((run) =>
        selectedIds.has(run.deploymentId)
          ? { ...run, workspaceId: targetWorkspaceId }
          : run,
      )
      this.persist()
      return moved.length
    },
    removeDeployments(ids: string[]) {
      const selectedIds = new Set(ids)
      const next = this.deployments.filter(
        (deployment) => !selectedIds.has(deployment.id),
      )
      const removed = this.deployments.length - next.length
      if (removed === 0) return 0

      this.deployments = next
      this.runs = this.runs.filter(
        (run) => !selectedIds.has(run.deploymentId),
      )
      this.persist()
      return removed
    },
    async executeDeployment(id: string) {
      const deployment = this.deployments.find((item) => item.id === id)
      if (!deployment) throw new Error('部署配置不存在')
      if (deployment.serverId === null) {
        throw new Error('请先为部署配置选择服务器')
      }

      const startedAt = new Date().toISOString()
      let run: DeploymentRun
      try {
        const result = await executeDeploymentScript(deployment)
        run = {
          id: newDeploymentId(),
          workspaceId: deployment.workspaceId,
          deploymentId: deployment.id,
          deploymentName: deployment.name,
          serverId: deployment.serverId,
          serverName: deployment.serverName,
          status: result.status,
          exitCode: result.exitCode,
          summary: result.summary,
          output: result.output,
          startedAt: result.startedAt,
          finishedAt: result.finishedAt,
        }
      } catch (error) {
        const finishedAt = new Date().toISOString()
        run = {
          id: newDeploymentId(),
          workspaceId: deployment.workspaceId,
          deploymentId: deployment.id,
          deploymentName: deployment.name,
          serverId: deployment.serverId,
          serverName: deployment.serverName,
          status: 'FAILED',
          exitCode: null,
          summary: errorMessage(error),
          output: `[平台] ${errorMessage(error)}\n`,
          startedAt,
          finishedAt,
        }
      }

      this.runs = [run, ...this.runs].slice(0, 200)
      this.persist()
      return run
    },
  },
})
