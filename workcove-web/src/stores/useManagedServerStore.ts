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
  }),
  actions: {
    async load(force = false) {
      const workspaces = useWorkspaceStore()
      const workspaceId = workspaces.activeWorkspaceId
      if (
        this.loading ||
        (this.loaded &&
          !force &&
          this.loadedWorkspaceId === workspaceId)
      ) {
        return
      }
      this.loading = true
      if (this.loadedWorkspaceId !== workspaceId) this.servers = []
      try {
        this.servers = await listManagedServers(workspaceId)
        this.loaded = true
        this.loadedWorkspaceId = workspaceId
      } finally {
        this.loading = false
      }
    },
    async save(input: ManagedServerInput) {
      const workspaces = useWorkspaceStore()
      const nextInput = {
        ...input,
        workspaceId: workspaces.activeWorkspaceId,
      }
      const saved = input.id
        ? await updateManagedServer(nextInput)
        : await createManagedServer(nextInput)
      this.servers = [
        saved,
        ...this.servers.filter((server) => server.id !== saved.id),
      ]
      return saved
    },
    async remove(id: number) {
      await deleteManagedServer(id)
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
      await moveManagedServer(
        id,
        workspaces.activeWorkspaceId,
        targetWorkspaceId,
      )
      this.servers = this.servers.filter((server) => server.id !== id)
    },
    open(id: number, tool: ServerTool) {
      return openManagedServer(id, tool)
    },
  },
})
