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
    accountsSequence: 0,
    foldersSequence: 0,
    inboxSequence: 0,
    messageSequence: 0,
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

      const ownerChanged = this.ownerEmail !== normalizedEmail
      const workspaceChanged = this.workspaceId !== workspaceId
      this.ownerEmail = normalizedEmail
      this.workspaceId = workspaceId
      if (ownerChanged || workspaceChanged) {
        this.accounts = []
        this.activeAccountId = 0
        this.folders = []
        this.activeFolderPath = 'INBOX'
        this.messages = []
        this.messagePage = 1
        this.messageTotal = 0
        this.activeMessage = null
        this.accountsSequence += 1
        this.foldersSequence += 1
        this.inboxSequence += 1
        this.messageSequence += 1
        this.loadingAccounts = false
        this.loadingFolders = false
        this.loadingInbox = false
        this.loadingMessage = false
        this.syncing = false
        this.sending = false
        this.error = ''
        this.messageError = ''
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
      this.accountsSequence += 1
      this.foldersSequence += 1
      this.inboxSequence += 1
      this.messageSequence += 1
    },
    async refreshAccounts() {
      const workspaceId = this.workspaceId
      if (!workspaceId) return
      const sequence = ++this.accountsSequence
      this.loadingAccounts = true
      this.error = ''
      try {
        const accounts = await listMailAccounts(workspaceId)
        if (
          sequence !== this.accountsSequence ||
          this.workspaceId !== workspaceId
        ) {
          return
        }
        this.accounts = accounts
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
        if (
          sequence !== this.accountsSequence ||
          this.workspaceId !== workspaceId
        ) {
          return
        }
        this.error =
          error instanceof Error ? error.message : '无法读取邮箱账号'
      } finally {
        if (
          sequence === this.accountsSequence &&
          this.workspaceId === workspaceId
        ) {
          this.loadingAccounts = false
        }
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
      this.loadingInbox = false
      this.loadingMessage = false
      this.syncing = false
      this.sending = false
      await this.refreshFolders()
    },
    async refreshFolders() {
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      if (!accountId || !workspaceId) return
      const sequence = ++this.foldersSequence
      this.loadingFolders = true
      this.error = ''
      try {
        const folders = await listMailFolders({
          workspaceId,
          accountId,
        })
        if (
          sequence !== this.foldersSequence ||
          this.workspaceId !== workspaceId ||
          this.activeAccountId !== accountId
        ) {
          return
        }
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
        if (
          sequence === this.foldersSequence &&
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId
        ) {
          this.error =
            error instanceof Error ? error.message : '无法读取邮件目录'
        }
      } finally {
        if (
          sequence === this.foldersSequence &&
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId
        ) {
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
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      if (!accountId || !workspaceId) return
      const folderPath = this.activeFolderPath
      const flagged = this.activeFolder?.role === 'FLAGGED'
      const sequence = ++this.inboxSequence
      const targetPage = Math.max(
        1,
        Math.floor(page ?? this.messagePage),
      )
      const offset = (targetPage - 1) * this.messagePageSize
      this.loadingInbox = true
      this.error = ''
      try {
        const response = await listMailInbox({
          workspaceId,
          accountId,
          folder: folderPath,
          flagged,
          limit: this.messagePageSize,
          offset,
        })
        if (
          sequence !== this.inboxSequence ||
          this.workspaceId !== workspaceId ||
          this.activeAccountId !== accountId ||
          this.activeFolderPath !== folderPath
        ) {
          return
        }
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
        if (
          sequence === this.inboxSequence &&
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId &&
          this.activeFolderPath === folderPath
        ) {
          this.error =
            error instanceof Error ? error.message : '无法读取收件箱'
        }
      } finally {
        if (
          sequence === this.inboxSequence &&
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId &&
          this.activeFolderPath === folderPath
        ) {
          this.loadingInbox = false
        }
      }
    },
    async syncCurrentFolder() {
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      const folderPath = this.activeFolderPath || 'INBOX'
      const flagged = this.activeFolder?.role === 'FLAGGED'
      if (!accountId || !workspaceId || this.syncing) {
        return null
      }
      this.syncing = true
      this.error = ''
      try {
        const result = await syncMail({
          workspaceId,
          accountId,
          folder: folderPath,
          flagged,
        })
        if (
          this.workspaceId !== workspaceId ||
          this.activeAccountId !== accountId
        ) {
          return result
        }
        await this.refreshFolders()
        return result
      } catch (error) {
        const message =
          error instanceof Error ? error.message : '无法同步邮箱'
        if (
          this.workspaceId !== workspaceId ||
          this.activeAccountId !== accountId
        ) {
          return null
        }
        try {
          await this.refreshFolders()
        } catch {
          // Keep the synchronization error visible if cache refresh also fails.
        }
        if (
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId
        ) {
          this.error = message
        }
        return null
      } finally {
        if (
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId
        ) {
          this.syncing = false
        }
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
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      if (!accountId || !workspaceId) return null
      const sequence = ++this.messageSequence
      this.loadingMessage = true
      this.messageError = ''
      try {
        const message = await getMailMessage({
          workspaceId,
          accountId,
          folder: folderPath,
          uid,
        })
        if (
          sequence !== this.messageSequence ||
          this.workspaceId !== workspaceId ||
          this.activeAccountId !== accountId
        ) {
          return null
        }
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
        if (
          sequence === this.messageSequence &&
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId
        ) {
          this.messageError =
            error instanceof Error ? error.message : '无法读取邮件'
        }
        return null
      } finally {
        if (
          sequence === this.messageSequence &&
          this.workspaceId === workspaceId &&
          this.activeAccountId === accountId
        ) {
          this.loadingMessage = false
        }
      }
    },
    async sendReply(body: string) {
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      const message = this.activeMessage
      if (!accountId || !message || !workspaceId) {
        throw new Error('请选择需要回复的邮件')
      }
      this.sending = true
      this.messageError = ''
      try {
        await replyMail({
          workspaceId,
          accountId,
          folder: message.folderPath,
          uid: message.uid,
          body,
        })
      } catch (error) {
        if (this.workspaceId === workspaceId) {
          this.messageError =
            error instanceof Error ? error.message : '邮件回复失败'
        }
        throw error
      } finally {
        if (this.workspaceId === workspaceId) {
          this.sending = false
        }
      }
    },
    async saveAccount(input: MailAccountInput) {
      const workspaceId = this.workspaceId
      if (!workspaceId) throw new Error('工作空间尚未初始化')
      const isNewAccount = !input.id
      const account = await saveMailAccount({
        ...input,
        workspaceId,
      })
      if (this.workspaceId !== workspaceId) return account
      this.accounts = [
        account,
        ...this.accounts.filter((item) => item.id !== account.id),
      ]
      this.activeAccountId = account.id
      if (isNewAccount) {
        this.folders = []
        this.activeFolderPath = 'INBOX'
        this.messages = []
        this.messagePage = 1
        this.messageTotal = 0
        this.activeMessage = null
      }
      await this.refreshFolders()
      if (this.workspaceId !== workspaceId) return account
      const result = await this.syncCurrentFolder()
      if (this.workspaceId !== workspaceId) return account
      if (!result && this.error) {
        const syncError = this.error
        if (isNewAccount) {
          await deleteMailAccount(account.id, workspaceId)
          if (this.workspaceId !== workspaceId) {
            throw new Error(syncError)
          }
          this.accounts = this.accounts.filter(
            (item) => item.id !== account.id,
          )
          this.activeAccountId = this.accounts[0]?.id ?? 0
          this.folders = []
          this.messages = []
          this.messageTotal = 0
          if (this.activeAccountId) await this.refreshFolders()
        }
        throw new Error(syncError)
      }
      return account
    },
    async removeAccount(accountId: number) {
      const workspaceId = this.workspaceId
      if (!workspaceId) return
      await deleteMailAccount(accountId, workspaceId)
      if (this.workspaceId !== workspaceId) return
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
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      if (!accountId || !workspaceId) return
      await setMailStarred({
        workspaceId,
        accountId,
        folder: message.folderPath,
        uid: message.uid,
        starred,
      })
      if (
        this.workspaceId !== workspaceId ||
        this.activeAccountId !== accountId
      ) {
        return
      }
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
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      await setMailReadState({
        workspaceId,
        accountId,
        folder: folderPath,
        uid,
        seen,
      })
      if (
        this.workspaceId !== workspaceId ||
        this.activeAccountId !== accountId
      ) {
        return
      }
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
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      const folderPath = this.activeFolderPath
      await markAllMailRead({
        workspaceId,
        accountId,
        folder: folderPath,
      })
      if (
        this.workspaceId !== workspaceId ||
        this.activeAccountId !== accountId ||
        this.activeFolderPath !== folderPath
      ) {
        return
      }
      this.messages = this.messages.map((message) => ({
        ...message,
        unread: false,
      }))
    },
    async moveMessage(message: MailMessageSummary, targetFolder: string) {
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      if (!workspaceId || !accountId) return
      await moveMail({
        workspaceId,
        accountId,
        folder: message.folderPath,
        uid: message.uid,
        targetFolder,
      })
      if (
        this.workspaceId !== workspaceId ||
        this.activeAccountId !== accountId
      ) {
        return
      }
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
      const workspaceId = this.workspaceId
      const accountId = this.activeAccountId
      if (!workspaceId || !accountId) {
        throw new Error('请先选择邮箱账号')
      }
      const folder = await createMailFolder({
        workspaceId,
        accountId,
        name: name.trim(),
      })
      if (
        this.workspaceId !== workspaceId ||
        this.activeAccountId !== accountId
      ) {
        return folder
      }
      await this.syncCurrentFolder()
      if (this.folders.some((item) => item.path === folder.path)) {
        await this.selectFolder(folder.path)
      }
      return folder
    },
  },
})
