import { defineStore } from 'pinia'

import {
  loadLocalCodexSessions,
  newCodexSessionId,
  saveLocalCodexSessions,
} from '@/services/localCodexSessions'
import type {
  CodexSession,
  CodexTask,
  CodexTaskAttachment,
} from '@/types/codex'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

function newTask(
  prompt: string,
  attachments: CodexTaskAttachment[] = [],
  status: CodexTask['status'] = 'running',
): CodexTask {
  const now = new Date().toISOString()
  return {
    id: window.crypto.randomUUID(),
    turnId: '',
    prompt,
    response: '',
    responseItemId: '',
    responseStreaming: false,
    status,
    startedAt: now,
    lastActivityAt: now,
    completedAt: null,
    durationMs: null,
    itemCount: 0,
    commandCount: 0,
    fileChangeCount: 0,
    toolCount: 0,
    inputTokens: 0,
    outputTokens: 0,
    cachedInputTokens: 0,
    totalTokens: 0,
    error: '',
    gitBranchMode: 'current',
    attachments,
    activities: [],
    timeline: [],
    plan: [],
  }
}

export const useCodexStore = defineStore('codex', {
  state: () => ({
    ownerEmail: '',
    sessions: [] as CodexSession[],
    activeSessionId: '',
    prefillRequest: null as {
      workspaceId: string
      cwd: string
      prompt: string
      nonce: number
      userTaskId?: number
      userTaskIds?: number[]
      codeReviewId?: string
      codeReviewMode?: 'initial' | 'repair'
      codeReviewTitle?: string
      autoSend?: boolean
    } | null,
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.sessions = []
        this.activeSessionId = ''
        this.prefillRequest = null
        return
      }
      if (this.ownerEmail === normalizedEmail) return

      this.ownerEmail = normalizedEmail
      this.sessions = loadLocalCodexSessions(normalizedEmail)
      this.activeSessionId = this.sessions[0]?.id ?? ''
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalCodexSessions(this.ownerEmail, this.sessions)
    },
    createSession(
      workspaceId = DEFAULT_WORKSPACE_ID,
      cwd = '',
      title = '新会话',
    ) {
      const now = new Date().toISOString()
      const session: CodexSession = {
        id: newCodexSessionId(),
        workspaceId,
        threadId: '',
        title: title.trim() || '新会话',
        cwd,
        pinned: false,
        accessMode: 'workspace',
        model: '',
        reasoningEffort: '',
        status: 'idle',
        unread: false,
        createdAt: now,
        updatedAt: now,
        activeTaskId: '',
        tasks: [],
      }
      this.sessions = [session, ...this.sessions]
      this.activeSessionId = session.id
      this.persist()
      return session
    },
    selectSession(id: string) {
      this.activeSessionId = id
      const session = this.sessions.find((item) => item.id === id)
      if (session?.unread) {
        session.unread = false
        this.persist()
      }
    },
    togglePinned(id: string) {
      const session = this.sessions.find((item) => item.id === id)
      if (!session) return
      session.pinned = !session.pinned
      this.persist()
    },
    clearActiveSession() {
      this.activeSessionId = ''
    },
    requestPrefill(
      workspaceId: string,
      cwd: string,
      prompt: string,
      options: {
        userTaskId?: number
        userTaskIds?: number[]
        codeReviewId?: string
        codeReviewMode?: 'initial' | 'repair'
        codeReviewTitle?: string
        autoSend?: boolean
      } = {},
    ) {
      this.prefillRequest = {
        workspaceId,
        cwd,
        prompt,
        nonce: Date.now() + Math.random(),
        userTaskId: options.userTaskId,
        userTaskIds: options.userTaskIds,
        codeReviewId: options.codeReviewId,
        codeReviewMode: options.codeReviewMode,
        codeReviewTitle: options.codeReviewTitle,
        autoSend: options.autoSend,
      }
    },
    clearPrefillRequest() {
      this.prefillRequest = null
    },
    removeSession(id: string) {
      this.sessions = this.sessions.filter(
        (session) => session.id !== id,
      )
      if (this.activeSessionId === id) {
        this.activeSessionId = this.sessions[0]?.id ?? ''
      }
      this.persist()
    },
    findByThreadId(threadId: string) {
      return this.sessions.find(
        (session) => session.threadId === threadId,
      )
    },
    startTask(
      sessionId: string,
      prompt: string,
      titleSource = prompt,
      attachments: CodexTaskAttachment[] = [],
    ) {
      const session = this.sessions.find((item) => item.id === sessionId)
      if (!session) return null

      const task = newTask(prompt, attachments)
      session.tasks.push(task)
      session.activeTaskId = task.id
      session.status = 'running'
      session.unread = false
      session.updatedAt = new Date().toISOString()
      if (session.title === '新会话') {
        session.title =
          titleSource.replace(/\s+/g, ' ').trim().slice(0, 32) ||
          '新会话'
      }
      this.persist()
      return task
    },
    enqueueTask(
      sessionId: string,
      prompt: string,
      titleSource = prompt,
      attachments: CodexTaskAttachment[] = [],
    ) {
      const session = this.sessions.find((item) => item.id === sessionId)
      if (!session) return null

      const task = newTask(prompt, attachments, 'queued')
      session.tasks.push(task)
      session.status = 'running'
      session.unread = false
      session.updatedAt = new Date().toISOString()
      if (session.title === '新会话') {
        session.title =
          titleSource.replace(/\s+/g, ' ').trim().slice(0, 32) ||
          '新会话'
      }
      this.persist()
      return task
    },
    touchSession(session: CodexSession) {
      session.updatedAt = new Date().toISOString()
    },
  },
})
