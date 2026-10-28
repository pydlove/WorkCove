import { defineStore } from 'pinia'

import {
  loadLocalQuickCommands,
  newQuickCommandId,
  saveLocalQuickCommands,
} from '@/services/localQuickCommands'
import type {
  TerminalQuickCommand,
  TerminalQuickCommandInput,
} from '@/types/quickCommand'

export const useQuickCommandStore = defineStore('quickCommands', {
  state: () => ({
    ownerEmail: '',
    commands: [] as TerminalQuickCommand[],
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.commands = []
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.commands = loadLocalQuickCommands(normalizedEmail)
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalQuickCommands(this.ownerEmail, this.commands)
    },
    saveCommand(input: TerminalQuickCommandInput) {
      const existing = input.id
        ? this.commands.find((command) => command.id === input.id)
        : null
      const now = new Date().toISOString()
      const command: TerminalQuickCommand = {
        id: input.id ?? newQuickCommandId(),
        workspaceId: input.workspaceId,
        scope: input.scope,
        serverId: input.scope === 'global' ? null : input.serverId,
        name: input.name.trim(),
        command: input.command,
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
      }
      this.commands = existing
        ? this.commands.map((item) =>
            item.id === command.id ? command : item,
          )
        : [...this.commands, command]
      this.persist()
      return command
    },
    removeCommand(id: string) {
      const next = this.commands.filter((command) => command.id !== id)
      if (next.length === this.commands.length) return false
      this.commands = next
      this.persist()
      return true
    },
  },
})
