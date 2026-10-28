import { defineStore } from 'pinia'

import {
  deleteMailAccount,
  createMailFolder,
  getMailMessage,
  listMailAccounts,
  listMailFolders,
  listMailInbox,
  markAllMailRead,
  moveMail,
  replyMail,
  saveMailAccount,
  sendMail,
  setMailStarred,
  setMailReadState,
  syncMail,
} from '@/api/mail'
import type {
  MailAccount,
  MailAccountInput,
  MailFolder,
  MailMessageDetail,
  MailMessageSummary,
  MailOutgoingInput,
  MailRecipientInput,
} from '@/types/mail'

function detailPreview(
  content: string,
  html: boolean,
  attachmentCount: number,
) {
  const value = (html ? content.replace(/<[^>]+>/g, ' ') : content)
    .replace(/\s+/g, ' ')
    .trim()
  if (value) return value.slice(0, 180)
  return attachmentCount > 0
    ? `包含 ${attachmentCount} 个附件`
    : '无正文'
}

export const useMailStore = defineStore('mail', {
  state: () => ({
    ownerEmail: '',
    workspaceId: '',
    accounts: [] as MailAccount[],
    activeAccountId: 0,
    folders: [] as MailFolder[],
    activeFolderPath: 'INBOX',
    messages: [] as MailMessageSummary[],
    messagePage: 1,
    messagePageSize: 40,
    messageTotal: 0,
    activeMessage: null as MailMessageDetail | null,
    loadingAccounts: false,
    loadingFolders: false,
    loadingInbox: false,
    loadingMessage: false,
    syncing: false,
    sending: false,
    error: '',
    messageError: '',
  }),
  getters: {
    activeAccount(state) {
      return (
        state.accounts.find(
          (account) => account.id === state.activeAccountId,
        ) ?? null
      )
    },
    unreadCount(state) {
      return state.messages.filter((message) => message.unread).length
    },
    activeFolder(state) {
      return (
        state.folders.find(
          (folder) => folder.path === state.activeFolderPath,
        ) ?? null
      )
    },
    messagePageCount(state) {
      return Math.max(
        1,
        Math.ceil(state.messageTotal / state.messagePageSize),
      )
    },
  },
  actions: {
    async load(email: string, workspaceId: string, force = false) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail || !workspaceId) {
        this.clear()
        return
      }
      if (
        !force &&
        this.ownerEmail === normalizedEmail &&
        this.workspaceId === workspaceId
      ) {
        return
      }

      const workspaceChanged = this.workspaceId !== workspaceId
      this.ownerEmail = normalizedEmail
      this.workspaceId = workspaceId
      if (workspaceChanged) {
        this.accounts = []
        this.activeAccountId = 0
        this.folders = []
        this.activeFolderPath = 'INBOX'
        this.messages = []
        this.messagePage = 1
        this.messageTotal = 0
        this.activeMessage = null
      }
      await this.refreshAccounts()
    },
    clear() {
      this.ownerEmail = ''
      this.workspaceId = ''
      this.accounts = []
      this.activeAccountId = 0
      this.folders = []
      this.activeFolderPath = 'INBOX'
      this.messages = []
      this.messagePage = 1
      this.messageTotal = 0
      this.activeMessage = null
      this.loadingAccounts = false
      this.loadingFolders = false
      this.loadingInbox = false
      this.loadingMessage = false
      this.syncing = false
      this.sending = false
      this.error = ''
      this.messageError = ''
    },
    async refreshAccounts() {
      if (!this.workspaceId) return
      this.loadingAccounts = true
      this.error = ''
      try {
        this.accounts = await listMailAccounts(this.workspaceId)
        if (
          !this.accounts.some(
            (account) => account.id === this.activeAccountId,
          )
        ) {
          this.activeAccountId = this.accounts[0]?.id ?? 0
          this.messages = []
          this.messagePage = 1
          this.messageTotal = 0
          this.activeMessage = null
        }
        if (this.activeAccountId) {
          await this.refreshFolders()
        }
      } catch (error) {
        this.error =
          error instanceof Error ? error.message : '无法读取邮箱账号'
      } finally {
        this.loadingAccounts = false
      }
    },
    async selectAccount(accountId: number) {
      if (accountId === this.activeAccountId) return
      this.activeAccountId = accountId
      this.folders = []
      this.activeFolderPath = 'INBOX'
      this.messages = []
      this.messagePage = 1
      this.messageTotal = 0
      this.activeMessage = null
      this.messageError = ''
      await this.refreshFolders()
    },
    async refreshFolders() {
      const accountId = this.activeAccountId
      if (!accountId || !this.workspaceId) return
      this.loadingFolders = true
      this.error = ''
      try {
        const folders = await listMailFolders({
          workspaceId: this.workspaceId,
          accountId,
        })
        if (this.activeAccountId !== accountId) return
        this.folders = folders
        if (
          !folders.some(
            (folder) => folder.path === this.activeFolderPath,
          )
        ) {
          this.activeFolderPath =
            folders.find((folder) => folder.role === 'INBOX')?.path ??
            folders[0]?.path ??
            'INBOX'
          this.messagePage = 1
          this.messageTotal = 0
        }
        await this.refreshInbox()
      } catch (error) {
        if (this.activeAccountId === accountId) {
          this.error =
            error instanceof Error ? error.message : '无法读取邮件目录'
        }
      } finally {
        if (this.activeAccountId === accountId) {
          this.loadingFolders = false
        }
      }
    },
    async selectFolder(path: string) {
      if (path === this.activeFolderPath) return
      this.activeFolderPath = path
      this.messages = []
      this.messagePage = 1
      this.messageTotal = 0
      this.activeMessage = null
      this.messageError = ''
      await this.refreshInbox()
      const folder = this.folders.find((item) => item.path === path)
      if (
        folder &&
        folder.totalCount > 0 &&
        this.messages.length === 0
      ) {
        await this.syncCurrentFolder()
      }
    },
    async refreshInbox(page?: number) {
      const accountId = this.activeAccountId
      if (!accountId || !this.workspaceId) return
      const targetPage = Math.max(
        1,
        Math.floor(page ?? this.messagePage),
      )
      const offset = (targetPage - 1) * this.messagePageSize
      this.loadingInbox = true
      this.error = ''
      try {
        const response = await listMailInbox({
          workspaceId: this.workspaceId,
          accountId,
          folder: this.activeFolderPath,
          flagged: this.activeFolder?.role === 'FLAGGED',
          limit: this.messagePageSize,
          offset,
        })
        if (this.activeAccountId !== accountId) return
        this.messagePage = targetPage
        this.messageTotal = response.total
        this.messages = response.messages
        if (
          this.activeMessage &&
          !response.messages.some(
            (message) =>
              message.uid === this.activeMessage?.uid &&
              message.folderPath === this.activeMessage?.folderPath,
          )
        ) {
          this.activeMessage = null
        }
      } catch (error) {
        if (this.activeAccountId === accountId) {
          this.error =
            error instanceof Error ? error.message : '无法读取收件箱'
        }
      } finally {
        if (this.activeAccountId === accountId) {
          this.loadingInbox = false
        }
      }
    },
    async syncCurrentFolder() {
      const accountId = this.activeAccountId
      if (!accountId || !this.workspaceId || this.syncing) {
        return null
      }
      this.syncing = true
      this.error = ''
      try {
        const result = await syncMail({
          workspaceId: this.workspaceId,
          accountId,
          folder: this.activeFolderPath || 'INBOX',
          flagged: this.activeFolder?.role === 'FLAGGED',
        })
        await this.refreshFolders()
        return result
      } catch (error) {
        const message =
          error instanceof Error ? error.message : '无法同步邮箱'
        try {
          await this.refreshFolders()
        } catch {
          // Keep the synchronization error visible if cache refresh also fails.
        }
        this.error = message
        return null
      } finally {
        this.syncing = false
      }
    },
    async goToMessagePage(page: number) {
      const target = Math.min(
        this.messagePageCount,
        Math.max(1, Math.floor(page)),
      )
      if (target === this.messagePage && this.messages.length > 0) {
        return
      }
      this.messages = []
      this.activeMessage = null
      await this.refreshInbox(target)
    },
    async openMessage(uid: string, folderPath: string) {
      const accountId = this.activeAccountId
      if (!accountId || !this.workspaceId) return null
      this.loadingMessage = true
      this.messageError = ''
      try {
        const message = await getMailMessage({
          workspaceId: this.workspaceId,
          accountId,
          folder: folderPath,
          uid,
        })
        if (this.activeAccountId !== accountId) return null
        this.activeMessage = message
        this.messages = this.messages.map((item) =>
          item.uid === uid && item.folderPath === folderPath
            ? {
                ...item,
                unread: false,
                preview:
                  item.preview ||
                  detailPreview(
                    message.content,
                    message.html,
                    message.attachments.length,
                  ),
              }
            : item,
        )
        return message
      } catch (error) {
        if (this.activeAccountId === accountId) {
          this.messageError =
            error instanceof Error ? error.message : '无法读取邮件'
        }
        return null
      } finally {
        this.loadingMessage = false
      }
    },
    async sendReply(body: string) {
      const accountId = this.activeAccountId
      const message = this.activeMessage
      if (!accountId || !message || !this.workspaceId) {
        throw new Error('请选择需要回复的邮件')
      }
      this.sending = true
      this.messageError = ''
      try {
        await replyMail({
          workspaceId: this.workspaceId,
          accountId,
          folder: message.folderPath,
          uid: message.uid,
          body,
        })
      } catch (error) {
        this.messageError =
          error instanceof Error ? error.message : '邮件回复失败'
        throw error
      } finally {
        this.sending = false
      }
    },
    async saveAccount(input: MailAccountInput) {
      const account = await saveMailAccount(input)
      this.accounts = [
        account,
        ...this.accounts.filter((item) => item.id !== account.id),
      ]
      this.activeAccountId = account.id
      this.folders = []
      this.activeFolderPath = 'INBOX'
      this.messages = []
      this.messagePage = 1
      this.messageTotal = 0
      this.activeMessage = null
      await this.syncCurrentFolder()
      return account
    },
    async removeAccount(accountId: number) {
      if (!this.workspaceId) return
      await deleteMailAccount(accountId, this.workspaceId)
      this.accounts = this.accounts.filter(
        (account) => account.id !== accountId,
      )
      if (this.activeAccountId === accountId) {
        this.activeAccountId = this.accounts[0]?.id ?? 0
        this.messages = []
        this.messagePage = 1
        this.messageTotal = 0
        this.activeMessage = null
        if (this.activeAccountId) await this.refreshFolders()
      }
    },
    async toggleStarred() {
      const message = this.activeMessage
      if (!message) return
      await this.setMessageStarred(message, !message.starred)
    },
    async setMessageStarred(
      message: Pick<MailMessageSummary, 'uid' | 'folderPath' | 'starred'>,
      starred: boolean,
    ) {
      const accountId = this.activeAccountId
      if (!accountId || !this.workspaceId) return
      await setMailStarred({
        workspaceId: this.workspaceId,
        accountId,
        folder: message.folderPath,
        uid: message.uid,
        starred,
      })
      if (
        this.activeMessage?.uid === message.uid &&
        this.activeMessage.folderPath === message.folderPath
      ) {
        this.activeMessage = {
          ...this.activeMessage,
          starred,
        }
      }
      this.messages = this.messages
        .map((item) =>
          item.uid === message.uid &&
          item.folderPath === message.folderPath
            ? { ...item, starred }
            : item,
        )
        .filter(
          (item) =>
            this.activeFolder?.role !== 'FLAGGED' || item.starred,
        )
      if (this.activeFolder?.role === 'FLAGGED' && !starred) {
        this.activeMessage = null
      }
    },
    async sendMessage(input: {
      to: MailRecipientInput[]
      cc: MailRecipientInput[]
      subject: string
      plainBody: string
      htmlBody: string
      inReplyTo: string
      readReceipt: boolean
    }) {
      if (!this.workspaceId || !this.activeAccountId) {
        throw new Error('请先选择邮箱账号')
      }
      const payload: MailOutgoingInput = {
        workspaceId: this.workspaceId,
        accountId: this.activeAccountId,
        ...input,
      }
      this.sending = true
      this.messageError = ''
      try {
        await sendMail(payload)
      } catch (error) {
        this.messageError =
          error instanceof Error ? error.message : '邮件发送失败'
        throw error
      } finally {
        this.sending = false
      }
    },
    async setReadState(
      uid: string,
      folderPath: string,
      seen: boolean,
    ) {
      if (!this.workspaceId || !this.activeAccountId) return
      await setMailReadState({
        workspaceId: this.workspaceId,
        accountId: this.activeAccountId,
        folder: folderPath,
        uid,
        seen,
      })
      this.messages = this.messages.map((message) =>
        message.uid === uid && message.folderPath === folderPath
          ? { ...message, unread: !seen }
          : message,
      )
    },
    async markAllRead() {
      if (
        !this.workspaceId ||
        !this.activeAccountId ||
        this.activeFolder?.role === 'FLAGGED'
      ) {
        return
      }
      await markAllMailRead({
        workspaceId: this.workspaceId,
        accountId: this.activeAccountId,
        folder: this.activeFolderPath,
      })
      this.messages = this.messages.map((message) => ({
        ...message,
        unread: false,
      }))
    },
    async moveMessage(message: MailMessageSummary, targetFolder: string) {
      if (!this.workspaceId || !this.activeAccountId) return
      await moveMail({
        workspaceId: this.workspaceId,
        accountId: this.activeAccountId,
        folder: message.folderPath,
        uid: message.uid,
        targetFolder,
      })
      this.messages = this.messages.filter(
        (item) =>
          item.uid !== message.uid ||
          item.folderPath !== message.folderPath,
      )
      if (
        this.activeMessage?.uid === message.uid &&
        this.activeMessage.folderPath === message.folderPath
      ) {
        this.activeMessage = null
      }
    },
    async createFolder(name: string) {
      if (!this.workspaceId || !this.activeAccountId) {
        throw new Error('请先选择邮箱账号')
      }
      const folder = await createMailFolder({
        workspaceId: this.workspaceId,
        accountId: this.activeAccountId,
        name: name.trim(),
      })
      await this.syncCurrentFolder()
      if (this.folders.some((item) => item.path === folder.path)) {
        await this.selectFolder(folder.path)
      }
      return folder
    },
  },
})
