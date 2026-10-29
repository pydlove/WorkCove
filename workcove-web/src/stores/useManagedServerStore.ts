import { defineStore } from 'pinia'

import {
  copyManagedServer,
  createManagedServer,
  deleteManagedServer,
  listManagedServers,
  moveManagedServer,
  openManagedServer,
  updateManagedServer,
} from '@/api/managedServers'
import type {
  ManagedServer,
  ManagedServerInput,
  ServerTool,
} from '@/types/managedServer'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'

export const useManagedServerStore = defineStore('managedServers', {
  state: () => ({
    servers: [] as ManagedServer[],
    loading: false,
    loaded: false,
    loadedWorkspaceId: '',
    loadSequence: 0,
  }),
  actions: {
    async load(force = false) {
      const workspaces = useWorkspaceStore()
      const workspaceId = workspaces.activeWorkspaceId
      if (
        this.loaded &&
        !force &&
        this.loadedWorkspaceId === workspaceId
      ) {
        return
      }
      const sequence = ++this.loadSequence
      this.loading = true
      if (this.loadedWorkspaceId !== workspaceId) this.servers = []
      try {
        const servers = await listManagedServers(workspaceId)
        if (sequence !== this.loadSequence) return
        this.servers = servers
        this.loaded = true
        this.loadedWorkspaceId = workspaceId
      } finally {
        if (sequence === this.loadSequence) {
          this.loading = false
        }
      }
    },
    async save(input: ManagedServerInput) {
      const workspaces = useWorkspaceStore()
      const workspaceId = workspaces.activeWorkspaceId
      const nextInput = {
        ...input,
        workspaceId,
      }
      const saved = input.id
        ? await updateManagedServer(nextInput)
        : await createManagedServer(nextInput)
      if (workspaces.activeWorkspaceId !== workspaceId) return saved
      this.servers = [
        saved,
        ...this.servers.filter((server) => server.id !== saved.id),
      ]
      return saved
    },
    async remove(id: number) {
      const workspaces = useWorkspaceStore()
      const workspaceId = workspaces.activeWorkspaceId
      await deleteManagedServer(id, workspaceId)
      if (workspaces.activeWorkspaceId !== workspaceId) return
      this.servers = this.servers.filter((server) => server.id !== id)
    },
    async copyToWorkspace(id: number, targetWorkspaceId: string) {
      const workspaces = useWorkspaceStore()
      return copyManagedServer(
        id,
        workspaces.activeWorkspaceId,
        targetWorkspaceId,
      )
    },
    async moveToWorkspace(id: number, targetWorkspaceId: string) {
      const workspaces = useWorkspaceStore()
      const sourceWorkspaceId = workspaces.activeWorkspaceId
      await moveManagedServer(
        id,
        sourceWorkspaceId,
        targetWorkspaceId,
      )
      if (workspaces.activeWorkspaceId !== sourceWorkspaceId) return
      this.servers = this.servers.filter((server) => server.id !== id)
    },
    open(id: number, tool: ServerTool) {
      const workspaces = useWorkspaceStore()
      return openManagedServer(
        id,
        workspaces.activeWorkspaceId,
        tool,
      )
    },
  },
})
