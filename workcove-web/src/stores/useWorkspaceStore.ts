import { defineStore } from 'pinia'

import {
  loadLocalWorkspaceState,
  newWorkspaceId,
  normalizeWorkspaceIconImage,
  saveLocalWorkspaceState,
} from '@/services/localWorkspaces'
import {
  DEFAULT_WORKSPACE_ID,
  DEFAULT_WORKSPACE_ICON,
  DEFAULT_WORKSPACE_ICON_TONE,
  DEFAULT_WORKSPACE_NAME,
  type Workspace,
} from '@/types/workspace'

export const useWorkspaceStore = defineStore('workspaces', {
  state: () => ({
    ownerEmail: '',
    workspaces: [] as Workspace[],
    activeWorkspaceId: DEFAULT_WORKSPACE_ID,
  }),
  getters: {
    activeWorkspace(state) {
      return (
        state.workspaces.find(
          (workspace) => workspace.id === state.activeWorkspaceId,
        ) ?? state.workspaces[0]
      )
    },
  },
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        const now = new Date().toISOString()
        this.ownerEmail = ''
        this.workspaces = [
          {
            id: DEFAULT_WORKSPACE_ID,
            name: DEFAULT_WORKSPACE_NAME,
            icon: DEFAULT_WORKSPACE_ICON,
            iconTone: DEFAULT_WORKSPACE_ICON_TONE,
            iconImage: '',
            createdAt: now,
            updatedAt: now,
          },
        ]
        this.activeWorkspaceId = DEFAULT_WORKSPACE_ID
        return
      }
      if (this.ownerEmail === normalizedEmail) return

      const state = loadLocalWorkspaceState(normalizedEmail)
      this.ownerEmail = normalizedEmail
      this.workspaces = state.workspaces
      this.activeWorkspaceId = state.activeWorkspaceId
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalWorkspaceState(this.ownerEmail, {
        workspaces: this.workspaces,
        activeWorkspaceId: this.activeWorkspaceId,
      })
    },
    selectWorkspace(id: string) {
      if (!this.workspaces.some((workspace) => workspace.id === id)) return
      this.activeWorkspaceId = id
      this.persist()
    },
    createWorkspace(
      rawName: string,
      iconImage = '',
    ) {
      const name = rawName.trim()
      if (
        !name ||
        [...name].length > 40 ||
        this.workspaces.some((workspace) => workspace.name === name)
      ) {
        return null
      }

      const now = new Date().toISOString()
      const workspace: Workspace = {
        id: newWorkspaceId(),
        name,
        icon: DEFAULT_WORKSPACE_ICON,
        iconTone: DEFAULT_WORKSPACE_ICON_TONE,
        iconImage: normalizeWorkspaceIconImage(iconImage),
        createdAt: now,
        updatedAt: now,
      }
      this.workspaces = [...this.workspaces, workspace]
      this.activeWorkspaceId = workspace.id
      this.persist()
      return workspace
    },
    updateWorkspaceIcon(
      id: string,
      iconImage: string,
    ) {
      const workspace = this.workspaces.find((item) => item.id === id)
      if (!workspace) return false
      const now = new Date().toISOString()
      this.workspaces = this.workspaces.map((item) =>
        item.id === id
          ? {
              ...item,
              iconImage: normalizeWorkspaceIconImage(iconImage),
              updatedAt: now,
            }
          : item,
      )
      this.persist()
      return true
    },
  },
})
