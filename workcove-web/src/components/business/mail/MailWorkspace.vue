<script setup lang="ts">
import {
  Ban,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Copy,
  FilePenLine,
  Folder,
  FolderPlus,
  Inbox,
  ListFilter,
  LoaderCircle,
  Mail,
  MailOpen,
  Paperclip,
  Plus,
  RefreshCw,
  Reply,
  Search,
  Send,
  Settings2,
  SquarePen,
  Star,
  Trash2,
} from '@lucide/vue'
import DOMPurify from 'dompurify'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import type { Component } from 'vue'

import MailAccountDialog from '@/components/business/mail/MailAccountDialog.vue'
import MailAccountIcon from '@/components/business/mail/MailAccountIcon.vue'
import MailComposeDialog from '@/components/business/mail/MailComposeDialog.vue'
import type { MailComposeMode } from '@/components/business/mail/MailComposeDialog.vue'
import MailFolderCreateDialog from '@/components/business/mail/MailFolderCreateDialog.vue'
import MailRulesDialog from '@/components/business/mail/MailRulesDialog.vue'
import { mailExportUrl } from '@/api/mail'
import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import {
  loadMailExpandedAccount,
  saveMailExpandedAccount,
} from '@/services/localMailUi'
import { useAuthStore } from '@/stores/useAuthStore'
import { useMailStore } from '@/stores/useMailStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  MailAccount,
  MailFolderRole,
  MailMessageDetail,
  MailMessageSummary,
} from '@/types/mail'

const emit = defineEmits<{
  notify: [message: string]
}>()

const auth = useAuthStore()
const mail = useMailStore()
const workspaces = useWorkspaceStore()
const searchQuery = ref('')
const accountDialogOpen = ref(false)
const editingAccount = ref<MailAccount | null>(null)
const deleteTarget = ref<MailAccount | null>(null)
const deleting = ref(false)
const recipientsExpanded = ref(false)
const composeMode = ref<MailComposeMode | null>(null)
const composeMessage = ref<MailMessageDetail | null>(null)
const contextMenu = ref<{
  message: MailMessageSummary
  x: number
  y: number
} | null>(null)
const contextMenuElement = ref<HTMLElement | null>(null)
const contextSubmenu = ref<'move' | null>(null)
const contextBusy = ref(false)
const accountContextMenu = ref<{
  account: MailAccount
  x: number
  y: number
} | null>(null)
const accountContextMenuElement = ref<HTMLElement | null>(null)
const folderCreateAccount = ref<MailAccount | null>(null)
const rulesAccount = ref<MailAccount | null>(null)
const expandedAccountId = ref<number | null>(null)
const copiedEmailAccountId = ref<number | null>(null)
let refreshTimer: number | null = null
let copiedEmailTimer: number | null = null

const accountOptions = computed(() =>
  mail.accounts.map((account) => ({
    value: String(account.id),
    label: account.displayName,
    description: account.emailAddress,
  })),
)
const folderOptions = computed(() =>
  mail.folders.map((folder) => ({
    value: folder.path,
    label: folder.name,
    description:
      folder.unreadCount > 0
        ? `${folder.unreadCount} 封未读`
        : undefined,
  })),
)
const activeAccountValue = computed({
  get: () => String(mail.activeAccountId || ''),
  set: (value: string) => {
    const id = Number(value)
    if (Number.isFinite(id) && id > 0) {
      void selectAccount(id)
    }
  },
})
const activeFolderValue = computed({
  get: () => mail.activeFolderPath,
  set: (value: string) => {
    if (value) void selectFolder(value)
  },
})
const filteredMessages = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return mail.messages
  return mail.messages.filter((message) =>
    [
      message.fromName,
      message.fromAddress,
      message.subject,
      message.preview,
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})
const renderedMessage = computed(() => {
  const message = mail.activeMessage
  if (!message || !message.html) return ''
  const sanitized = DOMPurify.sanitize(message.content, {
    ALLOWED_TAGS: [
      'a',
      'b',
      'blockquote',
      'br',
      'code',
      'div',
      'em',
      'h1',
      'h2',
      'h3',
      'h4',
      'hr',
      'i',
      'img',
      'li',
      'ol',
      'p',
      'pre',
      'span',
      'strong',
      'table',
      'tbody',
      'td',
      'th',
      'thead',
      'tr',
      'u',
      'ul',
    ],
    ALLOWED_ATTR: [
      'alt',
      'colspan',
      'href',
      'rowspan',
      'title',
    ],
    FORBID_TAGS: [
      'audio',
      'button',
      'embed',
      'form',
      'iframe',
      'input',
      'object',
      'script',
      'style',
      'video',
    ],
  })
  const template = document.createElement('template')
  template.innerHTML = sanitized
  template.content.querySelectorAll('a').forEach((link) => {
    link.target = '_blank'
    link.rel = 'noopener noreferrer nofollow'
  })
  template.content.querySelectorAll('img').forEach((image) => {
    const placeholder = document.createElement('span')
    placeholder.className = 'mail-image-placeholder'
    placeholder.textContent = image.alt
      ? `图片已隐藏：${image.alt}`
      : '邮件图片已隐藏'
    image.replaceWith(placeholder)
  })
  return template.innerHTML
})
const reading = computed(() => Boolean(mail.activeMessage))
const recipientCount = computed(
  () =>
    (mail.activeMessage?.to.length ?? 0) +
    (mail.activeMessage?.cc.length ?? 0),
)
const replyAllowed = computed(() => {
  const message = mail.activeMessage
  if (!message) return false
  return canReplyToFolder(message.folderPath)
})

watch(
  () => `${auth.email}\n${workspaces.activeWorkspaceId}`,
  async () => {
    searchQuery.value = ''
    const storedExpandedAccountId =
      auth.email && workspaces.activeWorkspaceId
        ? loadMailExpandedAccount(
            auth.email,
            workspaces.activeWorkspaceId,
          )
        : null
    await mail.load(
      auth.email,
      workspaces.activeWorkspaceId,
    )
    const nextExpandedAccountId =
      storedExpandedAccountId !== null &&
      (storedExpandedAccountId === 0 ||
        mail.accounts.some(
          (account) => account.id === storedExpandedAccountId,
        ))
        ? storedExpandedAccountId
        : (mail.activeAccountId || null)
    expandedAccountId.value = nextExpandedAccountId
    if (
      nextExpandedAccountId &&
      nextExpandedAccountId !== mail.activeAccountId
    ) {
      await mail.selectAccount(nextExpandedAccountId)
    }
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('pointerdown', handleContextPointerDown)
  window.addEventListener('keydown', handleContextKeydown)
  window.addEventListener('resize', closeContextMenu)
  document.addEventListener('scroll', closeContextMenu, true)
  refreshTimer = window.setInterval(() => {
    if (!mail.activeAccountId || mail.loadingInbox || mail.syncing) {
      return
    }
    void mail.syncCurrentFolder()
  }, 2 * 60_000)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleContextPointerDown)
  window.removeEventListener('keydown', handleContextKeydown)
  window.removeEventListener('resize', closeContextMenu)
  document.removeEventListener('scroll', closeContextMenu, true)
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
  }
  if (copiedEmailTimer !== null) {
    window.clearTimeout(copiedEmailTimer)
  }
})

watch(
  () =>
    mail.activeMessage
      ? `${mail.activeMessage.folderPath}:${mail.activeMessage.uid}`
      : '',
  () => {
    recipientsExpanded.value = false
  },
)

function openAddAccount() {
  editingAccount.value = null
  accountDialogOpen.value = true
}

function persistExpandedAccount() {
  if (auth.email && workspaces.activeWorkspaceId) {
    saveMailExpandedAccount(
      auth.email,
      workspaces.activeWorkspaceId,
      expandedAccountId.value,
    )
  }
}

function toggleAccountExpanded(account: MailAccount) {
  if (expandedAccountId.value === account.id) {
    expandedAccountId.value = null
    persistExpandedAccount()
    return
  }
  expandedAccountId.value = account.id
  persistExpandedAccount()
  if (account.id !== mail.activeAccountId) {
    void mail.selectAccount(account.id)
  }
}

async function selectAccount(accountId: number) {
  expandedAccountId.value = accountId
  persistExpandedAccount()
  await mail.selectAccount(accountId)
}

function accountUnreadCount(account: MailAccount) {
  if (account.id !== mail.activeAccountId) return 0
  return mail.folders
    .filter(
      (folder) =>
        folder.role === 'INBOX' || folder.role === 'CUSTOM',
    )
    .reduce((total, folder) => total + folder.unreadCount, 0)
}

async function copyAccountEmail(account: MailAccount) {
  const emailAddress = account.emailAddress.trim()
  if (!emailAddress) return
  if (!navigator.clipboard?.writeText) {
    emit('notify', '当前环境无法访问剪贴板')
    return
  }
  try {
    await navigator.clipboard.writeText(emailAddress)
    copiedEmailAccountId.value = account.id
    if (copiedEmailTimer !== null) {
      window.clearTimeout(copiedEmailTimer)
    }
    copiedEmailTimer = window.setTimeout(() => {
      copiedEmailAccountId.value = null
      copiedEmailTimer = null
    }, 1600)
    emit('notify', `已复制邮箱：${emailAddress}`)
  } catch {
    emit('notify', '复制邮箱失败，请检查剪贴板权限')
  }
}

function openEditAccount(account: MailAccount) {
  editingAccount.value = account
  accountDialogOpen.value = true
}

function closeAccountDialog() {
  accountDialogOpen.value = false
  editingAccount.value = null
}

function handleAccountSaved() {
  closeAccountDialog()
  expandedAccountId.value = mail.activeAccountId || null
  persistExpandedAccount()
}

function requestDeleteAccount(account: MailAccount) {
  deleteTarget.value = account
}

async function confirmDeleteAccount() {
  const account = deleteTarget.value
  if (!account || deleting.value) return
  deleting.value = true
  try {
    await mail.removeAccount(account.id)
    deleteTarget.value = null
    expandedAccountId.value = mail.activeAccountId || null
    persistExpandedAccount()
    emit('notify', `邮箱「${account.displayName}」已移除`)
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法移除邮箱账号',
    )
  } finally {
    deleting.value = false
  }
}

async function refreshInbox() {
  const result = await mail.syncCurrentFolder()
  if (mail.error) {
    emit('notify', mail.error)
    return
  }
  emit(
    'notify',
    result
      ? `已从服务器同步 ${result.messageCount} 封邮件`
      : '邮箱已同步',
  )
}

async function openMessage(message: MailMessageSummary) {
  const detail = await mail.openMessage(
    message.uid,
    message.folderPath,
  )
  if (!detail && mail.messageError) {
    emit('notify', mail.messageError)
  }
}

async function selectFolder(path: string) {
  searchQuery.value = ''
  await mail.selectFolder(path)
}

function backToList() {
  mail.activeMessage = null
  mail.messageError = ''
}

async function toggleStarred() {
  if (!mail.activeMessage) return
  try {
    await mail.toggleStarred()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法更新邮件星标',
    )
  }
}

function openNewMail() {
  composeMessage.value = null
  composeMode.value = 'new'
}

async function openCompose(
  mode: Exclude<MailComposeMode, 'new'>,
  message: MailMessageSummary | MailMessageDetail,
) {
  closeContextMenu()
  const current = mail.activeMessage
  const detail =
    current?.uid === message.uid &&
    current.folderPath === message.folderPath
      ? current
      : await mail.openMessage(message.uid, message.folderPath)
  if (!detail) {
    if (mail.messageError) emit('notify', mail.messageError)
    return
  }
  composeMessage.value = detail
  composeMode.value = mode
}

function openContextMenu(
  event: MouseEvent,
  message: MailMessageSummary,
) {
  contextSubmenu.value = null
  contextMenu.value = {
    message,
    x: Math.min(event.clientX, window.innerWidth - 190),
    y: Math.min(event.clientY, window.innerHeight - 360),
  }
}

function closeContextMenu() {
  contextMenu.value = null
  contextSubmenu.value = null
  accountContextMenu.value = null
}

function handleContextPointerDown(event: PointerEvent) {
  if (
    contextMenu.value &&
    event.target instanceof Node &&
    !contextMenuElement.value?.contains(event.target)
  ) {
    closeContextMenu()
  }
  if (
    accountContextMenu.value &&
    event.target instanceof Node &&
    !accountContextMenuElement.value?.contains(event.target)
  ) {
    accountContextMenu.value = null
  }
}

function handleContextKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeContextMenu()
    accountContextMenu.value = null
  }
}

function openAccountContextMenu(
  event: MouseEvent,
  account: MailAccount,
) {
  closeContextMenu()
  accountContextMenu.value = {
    account,
    x: Math.min(event.clientX, window.innerWidth - 190),
    y: Math.min(event.clientY, window.innerHeight - 100),
  }
}

async function createFolderForAccount() {
  const menu = accountContextMenu.value
  if (!menu) return
  accountContextMenu.value = null
  await mail.selectAccount(menu.account.id)
  folderCreateAccount.value = menu.account
}

async function manageRulesForAccount() {
  const menu = accountContextMenu.value
  if (!menu) return
  accountContextMenu.value = null
  await mail.selectAccount(menu.account.id)
  rulesAccount.value = menu.account
}

async function handleRulesChanged() {
  await mail.syncCurrentFolder()
}

function canReplyToFolder(folderPath: string) {
  const folder = mail.folders.find((item) => item.path === folderPath)
  return !['SENT', 'DRAFTS', 'TRASH', 'JUNK'].includes(
    folder?.role ?? 'CUSTOM',
  )
}

async function toggleMessageRead(message: MailMessageSummary) {
  closeContextMenu()
  if (contextBusy.value) return
  contextBusy.value = true
  try {
    await mail.setReadState(
      message.uid,
      message.folderPath,
      message.unread,
    )
    emit('notify', message.unread ? '已标记为已读' : '已标记为未读')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法更新已读状态',
    )
  } finally {
    contextBusy.value = false
  }
}

async function toggleMessageStar(message: MailMessageSummary) {
  closeContextMenu()
  if (contextBusy.value) return
  contextBusy.value = true
  try {
    await mail.setMessageStarred(message, !message.starred)
    emit('notify', message.starred ? '已取消星标' : '已添加星标')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法更新星标',
    )
  } finally {
    contextBusy.value = false
  }
}

async function moveMessage(
  message: MailMessageSummary,
  targetFolder: string,
) {
  closeContextMenu()
  if (contextBusy.value) return
  contextBusy.value = true
  try {
    await mail.moveMessage(message, targetFolder)
    emit('notify', '邮件已移动')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法移动邮件',
    )
  } finally {
    contextBusy.value = false
  }
}

async function deleteMessage(message: MailMessageSummary) {
  const trash = mail.folders.find((folder) => folder.role === 'TRASH')
  if (!trash) {
    closeContextMenu()
    emit('notify', '当前邮箱没有可用的“已删除”目录')
    return
  }
  if (trash.path === message.folderPath) {
    closeContextMenu()
    emit('notify', '邮件已经在已删除目录中')
    return
  }
  await moveMessage(message, trash.path)
}

function exportMessage(message: MailMessageSummary) {
  closeContextMenu()
  if (!mail.workspaceId || !mail.activeAccountId) return
  const anchor = document.createElement('a')
  anchor.href = mailExportUrl({
    workspaceId: mail.workspaceId,
    accountId: mail.activeAccountId,
    folder: message.folderPath,
    uid: message.uid,
  })
  anchor.download = ''
  anchor.click()
}

async function markAllRead() {
  closeContextMenu()
  if (contextBusy.value) return
  contextBusy.value = true
  try {
    await mail.markAllRead()
    emit('notify', '当前目录已全部标记为已读')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法标记全部已读',
    )
  } finally {
    contextBusy.value = false
  }
}

function moveTargets(message: MailMessageSummary) {
  return mail.folders.filter(
    (folder) =>
      folder.role !== 'FLAGGED' &&
      folder.path !== message.folderPath,
  )
}

function senderName(message: {
  fromName: string
  fromAddress: string
}) {
  return message.fromName || message.fromAddress || '未知发件人'
}

function messagePreview(message: MailMessageSummary) {
  const preview = message.preview.trim()
  if (preview) return preview
  if (message.attachmentCount > 0) {
    return `包含 ${message.attachmentCount} 个附件`
  }
  return '没有可预览的正文'
}

function formatMessageTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(date)
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function formatFullTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function recipientLine(addresses: Array<{ name: string; address: string }>) {
  return addresses
    .map((recipient) => recipient.name || recipient.address)
    .filter(Boolean)
    .join('、')
}

function formatAttachmentSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function folderIcon(role: MailFolderRole): Component {
  return {
    INBOX: Inbox,
    FLAGGED: Star,
    SENT: Send,
    DRAFTS: FilePenLine,
    TRASH: Trash2,
    JUNK: Ban,
    CUSTOM: Folder,
  }[role]
}
</script>

<template>
  <section
    class="mail-workspace"
    :class="{ 'mail-workspace--reading': reading }"
  >
    <header class="mail-toolbar">
      <div class="mail-toolbar__identity">
        <span v-if="mail.activeAccount" class="mail-toolbar__account-mark">
          <MailAccountIcon :account="mail.activeAccount" :size="24" />
        </span>
        <span v-else><Mail :size="17" /></span>
        <div>
          <strong>{{ mail.activeAccount?.displayName || '邮件' }}</strong>
          <div class="mail-toolbar__email">
            <small>
              {{ mail.activeAccount?.emailAddress || '选择或添加邮箱账号' }}
            </small>
            <button
              v-if="mail.activeAccount?.emailAddress"
              type="button"
              class="mail-toolbar__copy-email"
              :aria-label="`复制邮箱 ${mail.activeAccount.emailAddress}`"
              :title="
                copiedEmailAccountId === mail.activeAccount.id
                  ? '已复制'
                  : '复制邮箱'
              "
              @click="copyAccountEmail(mail.activeAccount)"
            >
              <Check
                v-if="copiedEmailAccountId === mail.activeAccount.id"
                :size="12"
              />
              <Copy v-else :size="12" />
            </button>
          </div>
        </div>
      </div>

      <UiSelect
        v-if="mail.accounts.length > 0"
        v-model="activeAccountValue"
        class="mail-toolbar__account-select"
        :options="accountOptions"
        size="sm"
        aria-label="切换邮箱账号"
        title="切换邮箱账号"
      />
      <UiSelect
        v-if="mail.activeAccountId && mail.folders.length > 0"
        v-model="activeFolderValue"
        class="mail-toolbar__folder-select"
        :options="folderOptions"
        size="sm"
        aria-label="切换邮件目录"
        title="切换邮件目录"
      />

      <div class="mail-toolbar__actions">
        <UiButton
          size="sm"
          :disabled="!mail.activeAccount"
          @click="openNewMail"
        >
          <SquarePen :size="13" />
          写邮件
        </UiButton>
        <UiButton
          size="sm"
          :disabled="
            !mail.activeAccountId ||
            mail.loadingInbox ||
            mail.syncing
          "
          @click="refreshInbox"
        >
          <RefreshCw
            :size="13"
            :class="{
              'mail-spin': mail.loadingInbox || mail.syncing,
            }"
          />
          {{ mail.syncing ? '同步中' : '刷新' }}
        </UiButton>
        <UiButton size="sm" @click="openAddAccount">
          <Plus :size="13" />
          添加邮箱
        </UiButton>
      </div>
    </header>

    <div v-if="mail.accounts.length === 0" class="mail-empty">
      <span><MailOpen :size="28" /></span>
      <strong>还没有邮箱账号</strong>
      <p>添加 IMAP / SMTP 邮箱后，就可以在这里收信和回复。</p>
      <UiButton variant="primary" @click="openAddAccount">
        <Plus :size="14" />
        添加第一个邮箱
      </UiButton>
    </div>

    <div v-else class="mail-layout">
      <aside class="mail-accounts" aria-label="邮箱账号">
        <div
          id="mail-account-list"
          class="mail-account-tree"
        >
          <section
            v-for="account in mail.accounts"
            :key="account.id"
            class="mail-account-node"
            :class="{
              'is-active': account.id === mail.activeAccountId,
              'is-expanded': expandedAccountId === account.id,
            }"
            @contextmenu.prevent.stop="
              openAccountContextMenu($event, account)
            "
          >
            <div class="mail-account-row">
              <button
                class="mail-account-toggle"
                type="button"
                :aria-expanded="expandedAccountId === account.id"
                :aria-controls="`mail-account-folders-${account.id}`"
                :aria-label="
                  expandedAccountId === account.id
                    ? `收起邮箱 ${account.displayName}`
                    : `展开邮箱 ${account.displayName}`
                "
                @click="toggleAccountExpanded(account)"
              >
                <ChevronDown
                  :size="13"
                  :class="{
                    'is-collapsed': expandedAccountId !== account.id,
                  }"
                />
              </button>
              <button
                class="mail-account-main"
                type="button"
                :title="account.emailAddress"
                @click="selectAccount(account.id)"
              >
                <span class="mail-account-avatar">
                  <MailAccountIcon :account="account" :size="21" />
                </span>
                <strong>{{ account.displayName }}</strong>
              </button>
              <em
                v-if="
                  expandedAccountId !== account.id &&
                  accountUnreadCount(account) > 0
                "
                class="mail-account-unread"
              >
                {{ accountUnreadCount(account) }}
              </em>
              <div class="mail-account-actions">
                <button
                  type="button"
                  :aria-label="`编辑邮箱 ${account.displayName}`"
                  title="编辑账号"
                  @click="openEditAccount(account)"
                >
                  <Settings2 :size="13" />
                </button>
                <button
                  type="button"
                  :aria-label="`移除邮箱 ${account.displayName}`"
                  title="移除账号"
                  @click="requestDeleteAccount(account)"
                >
                  <Trash2 :size="13" />
                </button>
              </div>
            </div>

            <div
              v-if="
                expandedAccountId === account.id &&
                account.id === mail.activeAccountId
              "
              :id="`mail-account-folders-${account.id}`"
              class="mail-account-folders"
              aria-label="邮件目录"
            >
              <div
                v-if="mail.loadingFolders && mail.folders.length === 0"
                class="mail-folders__loading"
              >
                <LoaderCircle class="mail-spin" :size="14" />
                正在读取目录…
              </div>
              <div
                v-else-if="mail.folders.length === 0"
                class="mail-folders__empty"
                title="尚未同步目录，请点击刷新"
              >
                尚未同步目录，请点击刷新
              </div>
              <template v-else>
                <button
                  v-for="folder in mail.folders"
                  :key="folder.path"
                  type="button"
                  :class="{
                    'is-active':
                      folder.path === mail.activeFolderPath,
                  }"
                  @click="selectFolder(folder.path)"
                >
                  <component
                    :is="folderIcon(folder.role)"
                    :size="15"
                  />
                  <span>{{ folder.name }}</span>
                  <em v-if="folder.unreadCount > 0">
                    {{ folder.unreadCount }}
                  </em>
                </button>
              </template>
            </div>
            <div
              v-else-if="expandedAccountId === account.id"
              :id="`mail-account-folders-${account.id}`"
              class="mail-account-folders"
            >
              <div class="mail-folders__loading">
                <LoaderCircle class="mail-spin" :size="14" />
                正在切换邮箱…
              </div>
            </div>
          </section>
        </div>
      </aside>

      <section
        class="mail-list-panel"
        :aria-label="mail.activeFolder?.name || '邮件列表'"
      >
        <header class="mail-list-toolbar">
          <div>
            <component
              :is="folderIcon(mail.activeFolder?.role || 'INBOX')"
              :size="15"
            />
            <strong>{{ mail.activeFolder?.name || '邮件' }}</strong>
            <span>
              {{
                searchQuery
                  ? filteredMessages.length
                  : mail.messageTotal
              }}
            </span>
          </div>
          <label>
            <Search :size="13" />
            <input
              v-model="searchQuery"
              type="search"
              aria-label="搜索邮件"
              placeholder="搜索邮件"
            />
          </label>
        </header>

        <p v-if="mail.error" class="mail-inline-error" role="alert">
          <CircleAlert :size="13" />
          {{ mail.error }}
        </p>

        <div
          v-if="filteredMessages.length > 0"
          class="mail-message-list"
        >
          <article
            v-for="message in filteredMessages"
            :key="message.uid"
            :class="{
              'is-unread': message.unread,
              'is-active': mail.activeMessage?.uid === message.uid,
            }"
            @click="openMessage(message)"
            @contextmenu.prevent.stop="
              openContextMenu($event, message)
            "
          >
            <span class="mail-message-list__dot" />
            <div class="mail-message-list__copy">
              <header>
                <strong>{{ senderName(message) }}</strong>
                <time>{{ formatMessageTime(message.receivedAt) }}</time>
              </header>
              <div class="mail-message-list__subject">
                <span>{{ message.subject }}</span>
                <Paperclip
                  v-if="message.attachmentCount"
                  :size="11"
                />
              </div>
              <p>{{ messagePreview(message) }}</p>
            </div>
          </article>
        </div>

        <div
          v-else-if="mail.loadingInbox || mail.syncing"
          class="mail-list-state"
        >
          <LoaderCircle class="mail-spin" :size="22" />
          <span>
            {{
              mail.syncing
                ? `正在同步${mail.activeFolder?.name || '邮件'}…`
                : `正在读取${mail.activeFolder?.name || '邮件'}…`
            }}
          </span>
        </div>
        <div v-else class="mail-list-state">
          <Inbox :size="24" />
          <span>
            {{
              searchQuery
                ? '没有匹配的邮件'
                : mail.activeFolder &&
                    mail.activeFolder.totalCount > 0
                  ? '尚未同步到本地，请点击刷新重试'
                : `${mail.activeFolder?.name || '这个目录'}是空的`
            }}
          </span>
        </div>

        <UiPagination
          v-if="!searchQuery"
          :page="mail.messagePage"
          :total="mail.messageTotal"
          :page-size="mail.messagePageSize"
          label="邮件分页"
          @update:page="mail.goToMessagePage"
        />
      </section>

      <section class="mail-reader" aria-label="邮件内容">
        <button
          class="mail-reader__back"
          type="button"
          aria-label="返回邮件列表"
          @click="backToList"
        >
          <ChevronLeft :size="16" />
          返回
        </button>

        <div v-if="mail.loadingMessage" class="mail-reader__state">
          <LoaderCircle class="mail-spin" :size="24" />
          <span>正在打开邮件…</span>
        </div>

        <template v-else-if="mail.activeMessage">
          <header class="mail-reader__header">
            <div>
              <h2>{{ mail.activeMessage.subject }}</h2>
              <p class="mail-reader__sender">
                <strong>{{ senderName(mail.activeMessage) }}</strong>
                <span>&lt;{{ mail.activeMessage.fromAddress }}&gt;</span>
              </p>
              <div class="mail-reader__meta">
                <span>{{ formatFullTime(mail.activeMessage.receivedAt) }}</span>
                <template v-if="recipientCount > 0">
                  <i aria-hidden="true">·</i>
                  <template v-if="recipientCount <= 3">
                    <span v-if="mail.activeMessage.to.length">
                      收件人 {{ recipientLine(mail.activeMessage.to) }}
                    </span>
                    <span v-if="mail.activeMessage.cc.length">
                      抄送 {{ recipientLine(mail.activeMessage.cc) }}
                    </span>
                  </template>
                  <button
                    v-else
                    type="button"
                    :aria-expanded="recipientsExpanded"
                    @click="recipientsExpanded = !recipientsExpanded"
                  >
                    <span>
                      收件人 {{ mail.activeMessage.to.length }} 人
                      <template v-if="mail.activeMessage.cc.length">
                        · 抄送 {{ mail.activeMessage.cc.length }} 人
                      </template>
                    </span>
                    <ChevronDown
                      :size="12"
                      :class="{
                        'is-expanded': recipientsExpanded,
                      }"
                    />
                  </button>
                </template>
              </div>
              <div
                v-if="recipientsExpanded && recipientCount > 3"
                class="mail-recipient-details"
              >
                <p v-if="mail.activeMessage.to.length">
                  <strong>收件人</strong>
                  <span>{{ recipientLine(mail.activeMessage.to) }}</span>
                </p>
                <p v-if="mail.activeMessage.cc.length">
                  <strong>抄送</strong>
                  <span>{{ recipientLine(mail.activeMessage.cc) }}</span>
                </p>
              </div>
            </div>
            <div class="mail-reader__actions">
              <button
                type="button"
                :class="{ 'is-active': mail.activeMessage.starred }"
                :aria-label="
                  mail.activeMessage.starred
                    ? '取消星标'
                    : '添加星标'
                "
                :title="
                  mail.activeMessage.starred
                    ? '取消星标'
                    : '添加星标'
                "
                @click="toggleStarred"
              >
                <Star
                  :size="15"
                  :fill="
                    mail.activeMessage.starred
                      ? 'currentColor'
                      : 'none'
                  "
                />
              </button>
              <UiButton
                v-if="replyAllowed"
                size="sm"
                @click="
                  openCompose('reply', mail.activeMessage)
                "
              >
                <Reply :size="13" />
                回复
              </UiButton>
            </div>
          </header>

          <div class="mail-reader__body">
            <div
              v-if="mail.activeMessage.html"
              class="mail-html"
              v-html="renderedMessage"
            ></div>
            <pre v-else class="mail-plain">{{
              mail.activeMessage.content
            }}</pre>
          </div>

          <section
            v-if="mail.activeMessage.attachments.length"
            class="mail-attachments"
          >
            <header>
              <Paperclip :size="13" />
              附件 {{ mail.activeMessage.attachments.length }}
            </header>
            <div>
              <span
                v-for="attachment in mail.activeMessage.attachments"
                :key="`${attachment.filename}-${attachment.size}`"
              >
                <Paperclip :size="12" />
                {{ attachment.filename || '未命名附件' }}
                <small>{{ formatAttachmentSize(attachment.size) }}</small>
              </span>
            </div>
          </section>

        </template>

        <div v-else class="mail-reader__state">
          <MailOpen :size="28" />
          <strong>选择一封邮件阅读</strong>
          <span>正文和回复会显示在这里。</span>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <div
        v-if="contextMenu"
        ref="contextMenuElement"
        class="mail-context-menu"
        :style="{
          left: `${contextMenu.x}px`,
          top: `${contextMenu.y}px`,
        }"
        role="menu"
        @contextmenu.prevent
      >
        <button
          v-if="canReplyToFolder(contextMenu.message.folderPath)"
          type="button"
          role="menuitem"
          @click="openCompose('reply', contextMenu.message)"
        >
          回复
        </button>
        <button
          v-if="canReplyToFolder(contextMenu.message.folderPath)"
          type="button"
          role="menuitem"
          @click="openCompose('replyAll', contextMenu.message)"
        >
          回复全部
        </button>
        <button
          type="button"
          role="menuitem"
          @click="openCompose('forward', contextMenu.message)"
        >
          转发
        </button>
        <span class="mail-context-menu__divider" />
        <button
          type="button"
          role="menuitem"
          :disabled="contextBusy"
          @click="toggleMessageRead(contextMenu.message)"
        >
          {{ contextMenu.message.unread ? '标记为已读' : '标记为未读' }}
        </button>
        <button
          type="button"
          role="menuitem"
          :disabled="contextBusy"
          @click="toggleMessageStar(contextMenu.message)"
        >
          {{
            contextMenu.message.starred
              ? '取消星标'
              : '标记星标'
          }}
        </button>
        <button
          class="mail-context-menu__submenu-trigger"
          type="button"
          role="menuitem"
          :aria-expanded="contextSubmenu === 'move'"
          @click="
            contextSubmenu =
              contextSubmenu === 'move' ? null : 'move'
          "
        >
          移动到
          <ChevronRight
            :size="13"
            :class="{
              'is-expanded': contextSubmenu === 'move',
            }"
          />
        </button>
        <div
          v-if="contextSubmenu === 'move'"
          class="mail-context-menu__submenu"
        >
          <button
            v-for="folder in moveTargets(contextMenu.message)"
            :key="folder.path"
            type="button"
            role="menuitem"
            :disabled="contextBusy"
            @click="moveMessage(contextMenu.message, folder.path)"
          >
            {{ folder.name }}
          </button>
          <span v-if="moveTargets(contextMenu.message).length === 0">
            没有其他目录
          </span>
        </div>
        <button
          type="button"
          role="menuitem"
          @click="exportMessage(contextMenu.message)"
        >
          导出邮件…
        </button>
        <span class="mail-context-menu__divider" />
        <button
          class="mail-context-menu__danger"
          type="button"
          role="menuitem"
          :disabled="contextBusy"
          @click="deleteMessage(contextMenu.message)"
        >
          删除
        </button>
        <button
          type="button"
          role="menuitem"
          :disabled="
            contextBusy ||
            mail.activeFolder?.role === 'FLAGGED' ||
            mail.messages.every((message) => !message.unread)
          "
          @click="markAllRead"
        >
          全部标记为已读
        </button>
      </div>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="accountContextMenu"
        ref="accountContextMenuElement"
        class="mail-context-menu"
        :style="{
          left: `${accountContextMenu.x}px`,
          top: `${accountContextMenu.y}px`,
        }"
        role="menu"
        @contextmenu.prevent
      >
        <button
          type="button"
          role="menuitem"
          @click="createFolderForAccount"
        >
          <FolderPlus :size="14" />
          新建文件夹
        </button>
        <button type="button" role="menuitem" @click="manageRulesForAccount">
          <ListFilter :size="14" />
          收信规则
        </button>
      </div>
    </Teleport>

    <MailComposeDialog
      v-if="composeMode && mail.activeAccount"
      :mode="composeMode"
      :account="mail.activeAccount"
      :message="composeMessage"
      @close="
        composeMode = null;
        composeMessage = null
      "
      @sent="
        composeMode = null;
        composeMessage = null
      "
      @notify="emit('notify', $event)"
    />

    <MailAccountDialog
      v-if="accountDialogOpen"
      :account="editingAccount"
      @close="closeAccountDialog"
      @saved="handleAccountSaved"
      @notify="emit('notify', $event)"
    />

    <MailFolderCreateDialog
      v-if="folderCreateAccount"
      :account="folderCreateAccount"
      @close="folderCreateAccount = null"
      @created="folderCreateAccount = null"
      @notify="emit('notify', $event)"
    />

    <MailRulesDialog
      v-if="rulesAccount"
      :account="rulesAccount"
      @close="rulesAccount = null"
      @changed="handleRulesChanged"
      @notify="emit('notify', $event)"
    />

    <UiConfirmDialog
      v-if="deleteTarget"
      title="移除邮箱账号？"
      description="只会移除平台中的账号配置，不会删除服务器上的邮件。"
      warning="已保存的邮箱密码也会一并删除。"
      confirm-label="移除邮箱"
      busy-label="正在移除…"
      :busy="deleting"
      @close="deleteTarget = null"
      @confirm="confirmDeleteAccount"
    >
      <div class="mail-delete-target">
        <strong>{{ deleteTarget.displayName }}</strong>
        <small>{{ deleteTarget.emailAddress }}</small>
      </div>
    </UiConfirmDialog>
  </section>
</template>

<style scoped>
.mail-workspace {
  display: flex;
  min-height: 600px;
  height: min(760px, calc(100dvh - 190px));
  overflow: hidden;
  flex-direction: column;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: workbench-enter var(--duration-window) var(--ease-apple) both;
}

.mail-toolbar {
  display: flex;
  min-height: 52px;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.mail-toolbar__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.mail-toolbar__identity > span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-toolbar__identity > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.mail-toolbar__account-mark .mail-account-icon {
  border-radius: 5px;
}

.mail-toolbar__identity strong,
.mail-toolbar__identity small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-toolbar__identity strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-toolbar__identity small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-toolbar__email {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 3px;
}

.mail-toolbar__email small {
  min-width: 0;
}

.mail-toolbar__copy-email {
  display: grid;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.mail-toolbar__copy-email:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.mail-toolbar__copy-email:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
}

.mail-toolbar__account-select {
  display: none;
  width: 180px;
}

.mail-toolbar__folder-select {
  display: none;
  width: 150px;
}

.mail-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}

.mail-layout {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: 190px 310px minmax(0, 1fr);
}

.mail-accounts {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: var(--space-2);
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--surface-secondary);
  border-right: 1px solid var(--line-soft);
}

.mail-account-tree {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.mail-account-node {
  min-width: 0;
  border-radius: var(--radius-control);
}

.mail-account-row {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 38px;
  align-items: center;
  border-radius: var(--radius-control);
}

.mail-account-node.is-active > .mail-account-row,
.mail-account-node.is-expanded > .mail-account-row,
.mail-account-row:hover,
.mail-account-row:has(.mail-account-actions button:focus-visible) {
  background: var(--hover-bg);
}

.mail-account-toggle {
  display: grid;
  width: 22px;
  height: 30px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.mail-account-toggle:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.mail-account-toggle svg {
  flex: 0 0 auto;
  transition: transform var(--duration-fast) var(--ease-apple);
}

.mail-account-toggle svg.is-collapsed {
  transform: rotate(-90deg);
}

.mail-account-main {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 7px;
  padding: 3px 2px;
  text-align: left;
  background: transparent;
}

.mail-account-avatar {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  color: var(--icon);
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
  place-items: center;
}

.mail-account-avatar .mail-account-icon {
  border-radius: 5px;
}

.mail-account-main strong {
  overflow: hidden;
  min-width: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-account-unread {
  min-width: 20px;
  flex: 0 0 auto;
  padding: 2px 6px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-style: normal;
  text-align: center;
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
  transition: opacity var(--duration-fast) var(--ease-apple);
}

.mail-account-row:hover .mail-account-unread,
.mail-account-row:has(.mail-account-actions button:focus-visible)
  .mail-account-unread {
  visibility: hidden;
  opacity: 0;
}

.mail-account-actions {
  position: absolute;
  right: 2px;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 1px;
  padding-right: 2px;
  padding-left: var(--space-1);
  background: var(--hover-bg);
  opacity: 0;
  pointer-events: none;
}

.mail-account-row:hover .mail-account-actions,
.mail-account-row:has(.mail-account-actions button:focus-visible)
  .mail-account-actions {
  opacity: 1;
  pointer-events: auto;
}

.mail-account-actions button {
  display: grid;
  width: 22px;
  height: 22px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-account-actions button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.mail-account-folders {
  display: flex;
  padding: 2px 2px var(--space-2) 25px;
  flex-direction: column;
  gap: 1px;
}

.mail-folders__loading {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-folders__empty {
  padding: var(--space-2) var(--space-1);
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-account-folders > button {
  display: grid;
  width: 100%;
  min-height: 31px;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: 4px var(--space-2);
  color: var(--text-muted);
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.mail-account-folders > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.mail-account-folders > button.is-active {
  color: var(--accent);
  background: var(--selection-bg);
}

.mail-account-folders > button svg {
  color: var(--accent);
  justify-self: center;
}

.mail-account-folders > button span {
  overflow: hidden;
  font-size: var(--font-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-account-folders > button em {
  min-width: 20px;
  padding: 1px 5px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-style: normal;
  text-align: center;
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.mail-list-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid var(--line-soft);
}

.mail-list-toolbar {
  display: flex;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  border-bottom: 1px solid var(--line-soft);
}

.mail-list-toolbar > div {
  display: flex;
  align-items: center;
  gap: 5px;
}

.mail-list-toolbar svg {
  color: var(--icon);
}

.mail-list-toolbar strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-list-toolbar > div span {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.mail-list-toolbar label {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 4px;
  padding: 0 6px;
  margin-left: auto;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.mail-list-toolbar input {
  width: 100%;
  min-width: 0;
  height: 24px;
  padding: 0;
  color: var(--text);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
}

.mail-list-toolbar input:focus {
  outline: none;
}

.mail-inline-error {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin: 0;
  padding: var(--space-2) var(--space-3);
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
}

.mail-message-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.mail-message-list article {
  display: grid;
  min-width: 0;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: var(--space-2);
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-2);
  cursor: pointer;
  border-bottom: 1px solid var(--line-soft);
  transition: background var(--duration-fast) var(--ease-apple);
}

.mail-message-list article:hover {
  background: var(--hover-bg);
}

.mail-message-list article.is-active {
  background: var(--selection-bg);
}

.mail-message-list__dot {
  width: 6px;
  height: 6px;
  margin-top: 6px;
  background: transparent;
  border-radius: 50%;
}

.mail-message-list article.is-unread .mail-message-list__dot {
  background: var(--accent);
}

.mail-message-list__copy {
  min-width: 0;
}

.mail-message-list__copy header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.mail-message-list__copy header strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-message-list article:not(.is-unread) .mail-message-list__copy header strong {
  font-weight: var(--weight-regular);
}

.mail-message-list__copy time {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.mail-message-list__subject {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-message-list__subject span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-message-list__subject svg {
  flex: 0 0 auto;
  color: var(--text-subtle);
}

.mail-message-list__copy p {
  display: -webkit-box;
  overflow: hidden;
  margin: 4px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mail-list-state,
.mail-reader__state {
  display: flex;
  min-height: 200px;
  flex: 1;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-caption);
}

.mail-reader {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: var(--canvas-raised);
}

.mail-reader__back {
  display: none;
  width: fit-content;
  height: 28px;
  align-items: center;
  gap: 3px;
  padding: 0 7px;
  margin: var(--space-2) 0 0 var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.mail-reader__header {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--line-soft);
}

.mail-reader__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.mail-reader__actions > button:first-child {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-reader__actions > button:first-child:hover,
.mail-reader__actions > button:first-child.is-active {
  color: var(--system-orange);
  background: var(--hover-bg);
}

.mail-reader__header > div {
  min-width: 0;
}

.mail-reader__header h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
  line-height: var(--line-title);
  overflow-wrap: anywhere;
}

.mail-reader__sender {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 5px;
  margin: var(--space-2) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-reader__sender strong {
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.mail-reader__sender span {
  overflow: hidden;
  color: var(--text-subtle);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-reader__meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  margin-top: 5px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  flex-wrap: wrap;
}

.mail-reader__meta > i {
  font-style: normal;
}

.mail-reader__meta > span {
  white-space: nowrap;
}

.mail-reader__meta > button {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 3px;
  padding: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: transparent;
}

.mail-reader__meta > button:hover {
  color: var(--text-muted);
}

.mail-reader__meta > button svg {
  flex: 0 0 auto;
  transition: transform var(--duration-fast) var(--ease-apple);
}

.mail-reader__meta > button svg.is-expanded {
  transform: rotate(180deg);
}

.mail-recipient-details {
  display: flex;
  padding: var(--space-2);
  margin-top: var(--space-2);
  flex-direction: column;
  gap: var(--space-1);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.mail-recipient-details p {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: var(--space-2);
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.mail-recipient-details strong {
  color: var(--text-subtle);
  font-weight: var(--weight-emphasis);
}

.mail-recipient-details span {
  overflow-wrap: anywhere;
}

.mail-reader__body {
  min-height: 160px;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-5);
}

.mail-plain {
  margin: 0;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.mail-html {
  color: var(--text);
  font-size: var(--font-body);
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.mail-html :deep(p) {
  margin: 0 0 var(--space-3);
}

.mail-html :deep(a) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.mail-html :deep(blockquote) {
  padding-left: var(--space-3);
  margin: var(--space-3) 0;
  color: var(--text-muted);
  border-left: 2px solid var(--line);
}

.mail-html :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.mail-html :deep(th),
.mail-html :deep(td) {
  padding: 4px 6px;
  border: 1px solid var(--line-soft);
}

.mail-html :deep(pre),
.mail-html :deep(code) {
  font-family: var(--font-mono);
  white-space: pre-wrap;
}

.mail-html :deep(.mail-image-placeholder) {
  display: inline-flex;
  padding: 2px 6px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
}

.mail-attachments {
  flex: 0 0 auto;
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--line-soft);
}

.mail-attachments > header {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-attachments > div {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
  flex-wrap: wrap;
}

.mail-attachments > div span {
  display: inline-flex;
  max-width: 260px;
  align-items: center;
  gap: 5px;
  padding: 4px 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.mail-attachments > div span small {
  color: var(--text-subtle);
}

.mail-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  text-align: center;
  flex-direction: column;
}

.mail-empty > span {
  display: grid;
  width: 54px;
  height: 54px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.mail-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.mail-empty p {
  margin: var(--space-1) 0 var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-delete-target {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.mail-delete-target strong,
.mail-delete-target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-delete-target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-delete-target small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-context-menu {
  position: fixed;
  z-index: var(--z-popover);
  width: 184px;
  max-height: min(420px, calc(100dvh - 16px));
  padding: 4px;
  overflow-y: auto;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-radius);
  box-shadow: var(--dropdown-shadow);
}

.mail-context-menu > button,
.mail-context-menu__submenu > button {
  display: flex;
  width: 100%;
  min-height: 30px;
  align-items: center;
  gap: var(--space-2);
  padding: 4px var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.mail-context-menu > button:hover:not(:disabled),
.mail-context-menu__submenu > button:hover:not(:disabled) {
  background: var(--hover-bg);
}

.mail-context-menu > button:disabled,
.mail-context-menu__submenu > button:disabled {
  color: var(--text-subtle);
  cursor: default;
}

.mail-context-menu__submenu-trigger svg {
  margin-left: auto;
  color: var(--text-subtle);
  transition: transform var(--duration-fast) var(--ease-apple);
}

.mail-context-menu__submenu-trigger svg.is-expanded {
  transform: rotate(90deg);
}

.mail-context-menu__divider {
  display: block;
  height: 1px;
  margin: 4px var(--space-2);
  background: var(--line-soft);
}

.mail-context-menu__submenu {
  padding: 3px;
  margin: 2px var(--space-2) 4px 22px;
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.mail-context-menu__submenu > button {
  min-height: 27px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-context-menu__submenu > span {
  display: block;
  padding: 6px var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-context-menu > .mail-context-menu__danger {
  color: var(--system-red);
}

.mail-spin {
  animation: mail-spin 800ms linear infinite;
}

@keyframes mail-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .mail-layout {
    grid-template-columns: 174px 280px minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .mail-workspace {
    height: auto;
    min-height: 560px;
  }

  .mail-toolbar__account-select {
    display: block;
    margin-left: auto;
  }

  .mail-toolbar__folder-select {
    display: block;
  }

  .mail-toolbar__actions {
    margin-left: 0;
  }

  .mail-layout {
    min-height: 500px;
    grid-template-columns: minmax(0, 1fr);
  }

  .mail-accounts {
    display: none;
  }

  .mail-list-panel {
    border-right: 0;
  }

  .mail-reader {
    display: none;
  }

  .mail-workspace--reading .mail-list-panel {
    display: none;
  }

  .mail-workspace--reading .mail-reader {
    display: flex;
    min-height: 540px;
  }

  .mail-reader__back {
    display: inline-flex;
  }
}

@media (max-width: 620px) {
  .mail-workspace {
    min-height: 520px;
    border-radius: var(--radius-panel);
  }

  .mail-toolbar {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .mail-toolbar__identity {
    flex: 1;
  }

  .mail-toolbar__account-select {
    order: 3;
    width: 100%;
    margin-left: 0;
  }

  .mail-toolbar__folder-select {
    order: 4;
    width: 100%;
  }

  .mail-toolbar__actions {
    margin-left: auto;
  }

  .mail-list-toolbar {
    height: auto;
    min-height: 42px;
    align-items: stretch;
    flex-direction: column;
    gap: var(--space-1);
    padding: var(--space-2) var(--space-3);
  }

  .mail-list-toolbar label {
    width: 100%;
    margin-left: 0;
  }

  .mail-reader__header,
  .mail-reader__body,
  .mail-attachments {
    padding-right: var(--space-4);
    padding-left: var(--space-4);
  }
}
</style>
