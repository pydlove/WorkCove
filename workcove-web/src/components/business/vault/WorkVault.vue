<script setup lang="ts">
import {
  ArrowUpRight,
  Copy,
  Eye,
  EyeOff,
  Fingerprint,
  KeyRound,
  Lock,
  LockKeyhole,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  Trash2,
  UserRound,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import {
  createVaultEntry,
  deleteVaultEntry,
  getVaultSecret,
  getVaultStatus,
  listVaultCategories,
  listVaultEntries,
  lockVault,
  unlockVaultWithPassword,
  updateVaultEntry,
} from '@/api/vault'
import { ApiError } from '@/api/http'
import VaultCategoryDialog from '@/components/business/vault/VaultCategoryDialog.vue'
import VaultEntryDialog from '@/components/business/vault/VaultEntryDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCategoryFilterBar from '@/components/ui/UiCategoryFilterBar.vue'
import UiCategorySelect from '@/components/ui/UiCategorySelect.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import {
  platformPasskeyAvailable,
  registerVaultPasskey,
  unlockVaultPasskey,
} from '@/services/vaultPasskeys'
import type {
  VaultCategory,
  VaultEntry,
  VaultEntryInput,
} from '@/types/vault'
import { DEFAULT_VAULT_CATEGORY } from '@/types/vault'
import { openWebsiteUrl } from '@/utils/openWebsite'

type VaultState = 'checking' | 'locked' | 'unlocked'

const emit = defineEmits<{
  notify: [message: string]
}>()

const auth = useAuthStore()
const state = ref<VaultState>('checking')
const expiresAt = ref<number | null>(null)
const now = ref(Date.now())
const passkeyEnabled = ref(false)
const platformPasskeyReady = ref(false)
const nativeTouchIdAvailable = ref(false)
const nativeTouchIdEnabled = ref(false)
const touchIdSetupOpen = ref(false)
const touchIdSetupPassword = ref('')
const touchIdSetupError = ref('')
const savingTouchId = ref(false)
const password = ref('')
const errorMessage = ref('')
const unlocking = ref(false)
const registeringPasskey = ref(false)
const entries = ref<VaultEntry[]>([])
const categories = ref<VaultCategory[]>([])
const loadingEntries = ref(false)
const searchQuery = ref('')
const selectedCategory = ref('全部')
const categoryManagerOpen = ref(false)
const revealedId = ref<number | null>(null)
const revealedPassword = ref('')
const dialogOpen = ref(false)
const editingEntry = ref<VaultEntry | null>(null)
const saving = ref(false)
const deleteTarget = ref<VaultEntry | null>(null)
const deleting = ref(false)
const copiedKey = ref('')
let clockTimer: number | null = null
let copiedTimer: number | null = null
let expiryNotified = false

const categoryNames = computed(() =>
  categories.value.map((category) => category.name),
)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const entry of entries.value) {
    counts.set(entry.category, (counts.get(entry.category) ?? 0) + 1)
  }
  return counts
})

const filteredEntries = computed(() => {
  const keyword = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return entries.value.filter((entry) => {
    if (
      selectedCategory.value !== '全部' &&
      entry.category !== selectedCategory.value
    ) {
      return false
    }
    if (!keyword) return true
    return `${entry.name} ${entry.username} ${entry.url} ${entry.category}`
      .toLocaleLowerCase('zh-CN')
      .includes(keyword)
  })
})

const defaultDialogCategory = computed(() =>
  selectedCategory.value === '全部'
    ? categories.value[0]?.name ?? DEFAULT_VAULT_CATEGORY
    : selectedCategory.value,
)

const {
  currentPage,
  pageItems: pagedEntries,
  setPage,
} = usePagination(
  filteredEntries,
  () => `${searchQuery.value}\n${selectedCategory.value}`,
)

const remainingSeconds = computed(() => {
  if (state.value !== 'unlocked' || !expiresAt.value) return 0
  return Math.max(0, Math.ceil((expiresAt.value - now.value) / 1000))
})

const countdownLabel = computed(() => {
  const minutes = Math.floor(remainingSeconds.value / 60)
  const seconds = remainingSeconds.value % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

const touchIdNeedsLocalhost = computed(
  () =>
    window.location.hostname === '127.0.0.1' ||
    window.location.hostname === '[::1]' ||
    window.location.hostname === '::1',
)
const touchIdEnabled = computed(
  () => passkeyEnabled.value || nativeTouchIdEnabled.value,
)
const touchIdSupported = computed(
  () => platformPasskeyReady.value || nativeTouchIdAvailable.value,
)

watch(remainingSeconds, (seconds) => {
  if (state.value !== 'unlocked' || seconds > 0) return
  expireVault()
})

async function loadStatus() {
  state.value = 'checking'
  errorMessage.value = ''
  try {
    const status = await getVaultStatus()
    passkeyEnabled.value = status.passkeyEnabled
    if (status.unlocked && status.expiresAt) {
      applyUnlock(status.expiresAt)
      await loadEntries()
    } else {
      state.value = 'locked'
      clearVaultContent()
    }
  } catch (error) {
    state.value = 'locked'
    errorMessage.value = errorText(error)
  }
}

function applyUnlock(value: string) {
  const timestamp = new Date(value).getTime()
  if (!Number.isFinite(timestamp) || timestamp <= Date.now()) {
    expireVault()
    return
  }
  expiresAt.value = timestamp
  now.value = Date.now()
  state.value = 'unlocked'
  expiryNotified = false
}

async function unlockWithPassword() {
  if (!password.value || unlocking.value) return
  unlocking.value = true
  errorMessage.value = ''
  try {
    const result = await unlockVaultWithPassword(password.value)
    password.value = ''
    applyUnlock(result.expiresAt)
    await loadEntries()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    unlocking.value = false
  }
}

async function unlockWithPasskey() {
  if (!passkeyEnabled.value || unlocking.value) return
  unlocking.value = true
  errorMessage.value = ''
  try {
    const result = await unlockVaultPasskey()
    applyUnlock(result.expiresAt)
    await loadEntries()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    unlocking.value = false
  }
}

async function unlockWithTouchId() {
  if (passkeyEnabled.value && platformPasskeyReady.value) {
    await unlockWithPasskey()
    return
  }
  const bridge = window.workCoveDesktop
  if (
    !nativeTouchIdEnabled.value ||
    !bridge?.unlockTouchIdSecret ||
    unlocking.value
  ) {
    return
  }
  unlocking.value = true
  errorMessage.value = ''
  try {
    const secret = await bridge.unlockTouchIdSecret(auth.email)
    const result = await unlockVaultWithPassword(secret)
    applyUnlock(result.expiresAt)
    await loadEntries()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    unlocking.value = false
  }
}

async function loadEntries() {
  loadingEntries.value = true
  errorMessage.value = ''
  try {
    const [nextEntries, nextCategories] = await Promise.all([
      listVaultEntries(),
      listVaultCategories(),
    ])
    entries.value = nextEntries
    categories.value = nextCategories
    if (
      selectedCategory.value !== '全部' &&
      !nextCategories.some(
        (category) => category.name === selectedCategory.value,
      )
    ) {
      selectedCategory.value = '全部'
    }
    setPage(1)
  } catch (error) {
    handleVaultError(error)
  } finally {
    loadingEntries.value = false
  }
}

async function lockNow() {
  try {
    await lockVault()
  } catch {
    // Local state must still be cleared if the server is unavailable.
  }
  state.value = 'locked'
  expiresAt.value = null
  password.value = ''
  clearVaultContent()
  emit('notify', '密码库已锁定')
}

function expireVault() {
  if (state.value !== 'unlocked') return
  state.value = 'locked'
  expiresAt.value = null
  clearVaultContent()
  if (!expiryNotified) {
    expiryNotified = true
    emit('notify', '密码库验证已超过 5 分钟，请重新解锁')
  }
}

function clearVaultContent() {
  entries.value = []
  categories.value = []
  revealedId.value = null
  revealedPassword.value = ''
  searchQuery.value = ''
  selectedCategory.value = '全部'
  categoryManagerOpen.value = false
}

function handleVaultError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    expireVault()
    return
  }
  errorMessage.value = errorText(error)
}

async function togglePassword(entry: VaultEntry) {
  if (revealedId.value === entry.id) {
    revealedId.value = null
    revealedPassword.value = ''
    return
  }
  try {
    const result = await getVaultSecret(entry.id)
    revealedId.value = entry.id
    revealedPassword.value = result.password
  } catch (error) {
    handleVaultError(error)
  }
}

async function copyPassword(entry: VaultEntry) {
  try {
    const result = revealedId.value === entry.id
      ? { password: revealedPassword.value }
      : await getVaultSecret(entry.id)
    await copyText(result.password, `password-${entry.id}`)
    emit('notify', `已复制「${entry.name}」的密码`)
  } catch (error) {
    handleVaultError(error)
  }
}

async function copyUsername(entry: VaultEntry) {
  if (!entry.username) return
  try {
    await copyText(entry.username, `username-${entry.id}`)
    emit('notify', `已复制「${entry.name}」的用户名`)
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function copyText(value: string, key: string) {
  if (!navigator.clipboard?.writeText) {
    throw new Error('当前环境无法访问剪贴板')
  }
  await navigator.clipboard.writeText(value)
  copiedKey.value = key
  if (copiedTimer !== null) window.clearTimeout(copiedTimer)
  copiedTimer = window.setTimeout(() => {
    copiedKey.value = ''
  }, 1600)
}

async function openEntryUrl(entry: VaultEntry) {
  if (!entry.url) return
  const error = await openWebsiteUrl(entry.url)
  if (error) {
    errorMessage.value = `无法打开网址：${error}`
  }
}

async function changeEntryCategory(entry: VaultEntry, category: string) {
  if (!category || category === entry.category) return
  try {
    const updated = await updateVaultEntry(entry.id, {
      name: entry.name,
      username: entry.username,
      password: '',
      url: entry.url,
      category,
      notes: entry.notes,
    })
    entries.value = entries.value.map((item) =>
      item.id === updated.id ? updated : item,
    )
    emit('notify', `${entry.name} 已移至「${category}」`)
  } catch (error) {
    handleVaultError(error)
  }
}

async function handleCategoriesChanged() {
  await loadEntries()
}

function addEntry() {
  editingEntry.value = null
  dialogOpen.value = true
}

function editEntry(entry: VaultEntry) {
  editingEntry.value = entry
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  editingEntry.value = null
}

async function saveEntry(input: VaultEntryInput) {
  if (saving.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    if (editingEntry.value) {
      await updateVaultEntry(editingEntry.value.id, input)
      emit('notify', '密码条目已更新')
    } else {
      await createVaultEntry(input)
      emit('notify', '密码条目已添加')
    }
    closeDialog()
    await loadEntries()
  } catch (error) {
    handleVaultError(error)
  } finally {
    saving.value = false
  }
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await deleteVaultEntry(target.id)
    if (revealedId.value === target.id) {
      revealedId.value = null
      revealedPassword.value = ''
    }
    deleteTarget.value = null
    emit('notify', `密码条目「${target.name}」已删除`)
    await loadEntries()
  } catch (error) {
    handleVaultError(error)
  } finally {
    deleting.value = false
  }
}

async function enablePasskey() {
  if (registeringPasskey.value) return
  registeringPasskey.value = true
  errorMessage.value = ''
  try {
    await registerVaultPasskey()
    passkeyEnabled.value = true
    emit('notify', '触控 ID 已启用，下次可以直接验证')
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    registeringPasskey.value = false
  }
}

function openTouchIdSetup() {
  touchIdSetupPassword.value = ''
  touchIdSetupError.value = ''
  touchIdSetupOpen.value = true
}

function closeTouchIdSetup() {
  if (savingTouchId.value) return
  touchIdSetupOpen.value = false
  touchIdSetupPassword.value = ''
  touchIdSetupError.value = ''
}

async function saveTouchIdSetup() {
  const bridge = window.workCoveDesktop
  const secret = touchIdSetupPassword.value
  if (!bridge?.storeTouchIdSecret || !secret || savingTouchId.value) {
    return
  }
  savingTouchId.value = true
  touchIdSetupError.value = ''
  try {
    const result = await unlockVaultWithPassword(secret)
    await bridge.storeTouchIdSecret(auth.email, secret)
    applyUnlock(result.expiresAt)
    nativeTouchIdEnabled.value = true
    touchIdSetupOpen.value = false
    touchIdSetupPassword.value = ''
    emit('notify', '触控 ID 已启用，下次可以直接验证')
  } catch (error) {
    touchIdSetupError.value = errorText(error)
  } finally {
    savingTouchId.value = false
  }
}

function errorText(error: unknown) {
  return error instanceof Error
    ? error.message
    : '密码库操作失败，请重试'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

onMounted(async () => {
  platformPasskeyReady.value = await platformPasskeyAvailable()
  const bridge = window.workCoveDesktop
  if (bridge?.touchIdAvailable) {
    try {
      nativeTouchIdAvailable.value = await bridge.touchIdAvailable()
      nativeTouchIdEnabled.value =
        nativeTouchIdAvailable.value &&
        (await bridge.touchIdStatus(auth.email))
    } catch {
      nativeTouchIdAvailable.value = false
      nativeTouchIdEnabled.value = false
    }
  }
  clockTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  await loadStatus()
})

onBeforeUnmount(() => {
  if (clockTimer !== null) window.clearInterval(clockTimer)
  if (copiedTimer !== null) window.clearTimeout(copiedTimer)
  revealedPassword.value = ''
})
</script>

<template>
  <section class="work-vault">
    <div v-if="state === 'checking'" class="vault-checking">
      <LockKeyhole :size="24" />
      正在检查密码库状态…
    </div>

    <div v-else-if="state === 'locked'" class="vault-locked">
      <div class="vault-locked__card">
        <div class="vault-locked__intro">
          <span class="vault-locked__icon">
            <LockKeyhole :size="26" />
          </span>
          <p class="vault-locked__kicker">本地加密保险库</p>
          <h2>验证后查看密码</h2>
          <p class="vault-locked__copy">
            验证通过后 5 分钟内可以查看和复制密码，超时后自动锁定。
          </p>
        </div>

        <button
          v-if="touchIdEnabled"
          class="vault-touch-id"
          type="button"
          :disabled="
            unlocking ||
            (passkeyEnabled &&
              !platformPasskeyReady &&
              !nativeTouchIdEnabled)
          "
          @click="unlockWithTouchId"
        >
          <Fingerprint :size="21" />
          <span>
            <strong>{{ unlocking ? '正在验证…' : '使用触控 ID' }}</strong>
            <small>
              {{
                nativeTouchIdEnabled &&
                (!passkeyEnabled || !platformPasskeyReady)
                  ? '使用当前 Mac 指纹解锁'
                  : platformPasskeyReady
                  ? '使用当前 Mac 指纹解锁'
                  : '当前浏览器不支持平台指纹验证'
              }}
            </small>
          </span>
        </button>

        <div v-if="touchIdEnabled" class="vault-locked__divider">
          <span>或使用登录密码</span>
        </div>

        <form class="vault-unlock-form" @submit.prevent="unlockWithPassword">
          <label>
            <span>8小时工作台登录密码</span>
            <UiInput
              v-model="password"
              type="password"
              size="lg"
              autocomplete="current-password"
              placeholder="输入登录密码"
              aria-label="密码库解锁密码"
            />
          </label>
          <UiButton
            variant="primary"
            size="lg"
            type="submit"
            :disabled="!password || unlocking"
          >
            <Lock :size="15" />
            {{ unlocking ? '正在验证…' : '解锁密码库' }}
          </UiButton>
        </form>

        <div
          v-if="
            errorMessage ||
            (!touchIdEnabled && touchIdSupported) ||
            (touchIdNeedsLocalhost && !nativeTouchIdAvailable)
          "
          class="vault-locked__notes"
        >
          <p v-if="errorMessage" class="vault-error" role="alert">
            {{ errorMessage }}
          </p>
          <p
            v-if="!touchIdEnabled && touchIdSupported"
            class="vault-locked__hint"
          >
            首次使用登录密码解锁后，可以启用触控 ID。
          </p>
          <p
            v-if="touchIdNeedsLocalhost && !nativeTouchIdAvailable"
            class="vault-locked__hint"
          >
            触控 ID 建议通过 http://localhost:32581 访问使用。
          </p>
        </div>
        <p class="vault-locked__security">
          <ShieldCheck :size="14" />
          密码使用 AES-GCM 加密保存，不会进入页面列表或日志。
        </p>
      </div>
    </div>

    <template v-else>
      <header class="vault-toolbar">
        <div class="vault-toolbar__count">
          <span>{{ filteredEntries.length }} 个密码</span>
          <span class="vault-countdown">
            <Lock :size="12" />
            {{ countdownLabel }} 后锁定
          </span>
        </div>
        <label class="vault-search">
          <Search :size="15" aria-hidden="true" />
          <input
            v-model="searchQuery"
            type="search"
            aria-label="搜索密码条目"
            placeholder="搜索名称、账号、网址或分类"
          />
        </label>
        <button
          v-if="
            !nativeTouchIdEnabled &&
            (!passkeyEnabled || !platformPasskeyReady) &&
            touchIdSupported
          "
          class="vault-passkey-button"
          type="button"
          :disabled="
            registeringPasskey ||
            (!nativeTouchIdAvailable && !platformPasskeyReady)
          "
          title="使用当前 Mac 的触控 ID 解锁"
          @click="
            nativeTouchIdAvailable
              ? openTouchIdSetup()
              : enablePasskey()
          "
        >
          <Fingerprint :size="16" />
          {{ registeringPasskey ? '正在启用…' : '启用触控 ID' }}
        </button>
        <UiButton variant="primary" @click="addEntry">
          <Plus :size="15" />
          添加密码
        </UiButton>
        <button
          class="vault-lock-button"
          type="button"
          aria-label="立即锁定密码库"
          title="立即锁定"
          @click="lockNow"
        >
          <LockKeyhole :size="16" />
        </button>
      </header>

      <UiCategoryFilterBar
        v-model="selectedCategory"
        :categories="categoryNames"
        :counts="categoryCounts"
        :total="entries.length"
        group-label="密码分类"
        @manage="categoryManagerOpen = true"
      />

      <p v-if="errorMessage" class="vault-error vault-error--toolbar" role="alert">
        {{ errorMessage }}
      </p>

      <div class="vault-list">
        <div v-if="loadingEntries" class="vault-empty">
          正在读取密码库…
        </div>
        <div v-else-if="filteredEntries.length === 0" class="vault-empty">
          <span><KeyRound :size="26" /></span>
          <strong>
            {{
              entries.length === 0
                ? '还没有密码条目'
                : '当前分类没有密码条目'
            }}
          </strong>
          <p>
            {{
              entries.length === 0
                ? '把容易忘记的账号、密码和备注集中保存到这里。'
                : '切换其他分类，或为当前分类添加一个密码。'
            }}
          </p>
          <UiButton variant="primary" size="lg" @click="addEntry">
            <Plus :size="15" />
            {{ entries.length === 0 ? '添加第一个密码' : '添加密码' }}
          </UiButton>
        </div>

        <template v-else>
          <article
            v-for="entry in pagedEntries"
            :key="entry.id"
            class="vault-row"
            :class="{ 'vault-row--revealed': revealedId === entry.id }"
          >
            <span class="vault-row__icon">
              <KeyRound :size="18" />
            </span>
            <div class="vault-row__copy">
              <strong>{{ entry.name }}</strong>
              <small>{{ entry.username || '未填写用户名' }}</small>
            </div>
            <UiCategorySelect
              class="vault-row__category"
              :value="entry.category"
              :options="categoryNames"
              :label="`切换 ${entry.name} 的分类`"
              title="快速切换密码分类"
              @change="changeEntryCategory(entry, $event)"
            />
            <button
              v-if="entry.url"
              class="vault-row__url"
              type="button"
              :title="entry.url"
              @click="openEntryUrl(entry)"
            >
              {{ entry.url.replace(/^https?:\/\//, '') }}
              <ArrowUpRight :size="11" />
            </button>
            <time :datetime="entry.updatedAt">
              {{ formatDate(entry.updatedAt) }}
            </time>
            <div class="vault-row__actions">
              <button
                v-if="entry.username"
                type="button"
                :aria-label="`复制 ${entry.name} 的用户名`"
                title="复制用户名"
                @click="copyUsername(entry)"
              >
                <UserRound :size="14" />
              </button>
              <button
                type="button"
                :aria-label="`复制 ${entry.name} 的密码`"
                title="复制密码"
                @click="copyPassword(entry)"
              >
                <Copy :size="14" />
              </button>
              <button
                type="button"
                :aria-label="
                  revealedId === entry.id
                    ? `隐藏 ${entry.name} 的密码`
                    : `查看 ${entry.name} 的密码`
                "
                :title="revealedId === entry.id ? '隐藏密码' : '查看密码'"
                @click="togglePassword(entry)"
              >
                <EyeOff v-if="revealedId === entry.id" :size="14" />
                <Eye v-else :size="14" />
              </button>
              <button
                type="button"
                :aria-label="`编辑 ${entry.name}`"
                title="编辑"
                @click="editEntry(entry)"
              >
                <Pencil :size="14" />
              </button>
              <button
                type="button"
                :aria-label="`删除 ${entry.name}`"
                title="删除"
                @click="deleteTarget = entry"
              >
                <Trash2 :size="14" />
              </button>
            </div>
            <div
              v-if="revealedId === entry.id"
              class="vault-row__secret"
            >
              <span>当前密码</span>
              <code>{{ revealedPassword }}</code>
              <span class="vault-row__copied">
                {{ copiedKey === `password-${entry.id}` ? '已复制' : '' }}
              </span>
            </div>
          </article>
        </template>
      </div>

      <UiPagination
        :page="currentPage"
        :total="filteredEntries.length"
        label="密码条目分页"
        @update:page="setPage"
      />
    </template>

    <UiDialog
      v-if="touchIdSetupOpen"
      title="启用触控 ID"
      description="输入登录密码完成验证。密码会由 macOS 安全存储加密保存，并只在触控 ID 验证通过后读取。"
      height="auto"
      @close="closeTouchIdSetup"
    >
      <form
        id="vault-touch-id-setup"
        class="vault-touch-setup"
        @submit.prevent="saveTouchIdSetup"
      >
        <label>
          <span>登录密码</span>
          <UiInput
            v-model="touchIdSetupPassword"
            type="password"
            autocomplete="current-password"
            placeholder="输入登录密码"
            aria-label="启用触控 ID 的登录密码"
          />
        </label>
        <p
          v-if="touchIdSetupError"
          class="vault-error"
          role="alert"
        >
          {{ touchIdSetupError }}
        </p>
      </form>

      <template #footer>
        <UiButton
          size="lg"
          :disabled="savingTouchId"
          @click="closeTouchIdSetup"
        >
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="vault-touch-id-setup"
          :disabled="!touchIdSetupPassword || savingTouchId"
        >
          <Fingerprint :size="14" />
          {{ savingTouchId ? '正在启用…' : '启用触控 ID' }}
        </UiButton>
      </template>
    </UiDialog>

    <VaultEntryDialog
      v-if="dialogOpen"
      :entry="editingEntry"
      :categories="categoryNames"
      :default-category="defaultDialogCategory"
      @close="closeDialog"
      @save="saveEntry"
    />

    <VaultCategoryDialog
      v-if="categoryManagerOpen"
      :categories="categories"
      :entries="entries"
      @close="categoryManagerOpen = false"
      @changed="handleCategoriesChanged"
      @notify="emit('notify', $event)"
    />

    <UiConfirmDialog
      v-if="deleteTarget"
      title="删除密码条目？"
      :description="`「${deleteTarget.name}」及其加密密码将从密码库移除。`"
      warning="删除后无法恢复，请确认该账号已有其他恢复方式。"
      confirm-label="删除密码"
      :busy="deleting"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.work-vault {
  overflow: hidden;
  background: var(--canvas-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: vault-enter var(--duration-window) var(--ease-apple) both;
}

.vault-checking {
  display: flex;
  min-height: 520px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-body);
}

.vault-locked {
  display: grid;
  min-height: 570px;
  padding: var(--space-8) var(--space-6);
  background: var(--surface-secondary);
  place-items: center;
}

.vault-locked__card {
  display: flex;
  width: min(100%, 380px);
  align-items: stretch;
  gap: var(--space-3);
  text-align: center;
  flex-direction: column;
}

.vault-locked__intro {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
  flex-direction: column;
}

.vault-locked__icon {
  display: grid;
  width: 56px;
  height: 56px;
  margin-bottom: var(--space-2);
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--system-blue) 10%, transparent);
  place-items: center;
}

.vault-locked__kicker {
  margin: 0;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.vault-locked h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-metric);
  font-weight: var(--weight-strong);
  line-height: 1.25;
}

.vault-locked__copy {
  max-width: 340px;
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: 18px;
}

.vault-touch-setup {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.vault-touch-setup label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.vault-touch-id {
  display: flex;
  width: 100%;
  min-height: 52px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  color: var(--system-blue);
  text-align: left;
  background: var(--canvas-raised);
  border: 1px solid color-mix(in srgb, var(--system-blue) 14%, transparent);
  border-radius: var(--radius-control);
}

.vault-touch-id:disabled {
  opacity: 0.5;
}

.vault-touch-id span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.vault-touch-id strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.vault-touch-id small {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.vault-locked__divider {
  display: flex;
  width: 100%;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-1) 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.vault-locked__divider::before,
.vault-locked__divider::after {
  height: 1px;
  flex: 1;
  content: "";
  background: var(--line-soft);
}

.vault-unlock-form {
  display: flex;
  width: 100%;
  gap: var(--space-3);
  flex-direction: column;
}

.vault-unlock-form label {
  display: flex;
  min-width: 0;
  text-align: left;
  flex-direction: column;
  gap: 6px;
}

.vault-unlock-form label > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.vault-unlock-form :deep(.ui-button) {
  width: 100%;
  align-self: stretch;
}

.vault-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.vault-error--toolbar {
  padding: var(--space-2) var(--space-4);
  margin: 0;
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
  border-bottom: 1px solid var(--line-soft);
}

.vault-locked__hint,
.vault-locked__security {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.vault-locked__notes {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-direction: column;
}

.vault-locked__security {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding-top: var(--space-3);
  margin-top: var(--space-1);
  color: var(--text-subtle);
  border-top: 1px solid var(--line-soft);
}

.vault-toolbar {
  display: flex;
  min-height: 58px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.vault-toolbar__count {
  display: flex;
  min-width: 155px;
  flex: 1;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.vault-countdown {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--system-green);
  font-family: var(--font-mono);
}

.vault-search {
  display: flex;
  width: min(100%, 240px);
  height: var(--control-md);
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  color: var(--text-subtle);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.vault-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.vault-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
}

.vault-passkey-button,
.vault-lock-button {
  display: inline-flex;
  height: 24px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 var(--space-2);
  color: var(--icon);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-control);
  white-space: nowrap;
}

.vault-passkey-button:hover,
.vault-lock-button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.vault-passkey-button:disabled {
  opacity: 0.4;
}

.vault-lock-button {
  width: 28px;
  padding: 0;
}

.vault-list {
  min-height: 390px;
}

.vault-row {
  --vault-url-column: clamp(110px, 18vw, 180px);

  display: grid;
  min-height: 62px;
  grid-template-columns:
    36px minmax(150px, 1fr) 112px var(--vault-url-column)
    80px auto;
  align-items: center;
  gap: var(--space-1) var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--line-soft);
  transition: background var(--duration-fast) var(--ease-apple);
}

.vault-row:hover {
  background: var(--hover-bg);
}

.vault-row__icon {
  display: grid;
  grid-column: 1;
  width: 34px;
  height: 34px;
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.vault-row__copy {
  display: flex;
  grid-column: 2;
  min-width: 0;
  flex-direction: column;
}

.vault-row__copy strong,
.vault-row__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vault-row__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.vault-row__copy small {
  margin-top: 2px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.vault-row__category {
  grid-column: 3;
  width: fit-content;
  max-width: 100%;
  justify-self: start;
}

.vault-row__url {
  display: inline-flex;
  grid-column: 4;
  min-width: 0;
  align-items: center;
  gap: 3px;
  padding: 0;
  overflow: hidden;
  color: var(--accent);
  font-size: var(--font-caption);
  text-align: left;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border: 0;
}

.vault-row__url:hover,
.vault-row__url:focus-visible {
  color: var(--accent-strong);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.vault-row time {
  grid-column: 5;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.vault-row__actions {
  display: flex;
  grid-column: 6;
  align-items: center;
  gap: var(--space-2);
}

.vault-row__actions button {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.vault-row__actions button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.vault-row__actions button:last-child:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.vault-row__secret {
  display: flex;
  grid-column: 2 / -1;
  align-items: center;
  justify-self: start;
  gap: var(--space-2);
  min-height: 32px;
  margin: 0 0 var(--space-2);
  padding: var(--space-1) var(--space-2);
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.vault-row__secret > span:first-child {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.vault-row__secret code {
  padding: 0;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: var(--line-body);
  background: transparent;
  overflow-wrap: anywhere;
}

.vault-row__copied {
  color: var(--system-green);
  font-size: var(--font-caption);
}

.vault-empty {
  display: flex;
  min-height: 390px;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  text-align: center;
  flex-direction: column;
}

.vault-empty > span {
  display: grid;
  width: 52px;
  height: 52px;
  color: var(--icon);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.vault-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.vault-empty p {
  max-width: 360px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 980px) {
  .vault-row {
    grid-template-columns: 36px minmax(130px, 1fr) 90px auto;
  }

  .vault-row__category,
  .vault-row__url,
  .vault-row time {
    display: none;
  }

  .vault-row__actions {
    grid-column: 4;
  }

  .vault-row__secret {
    grid-column: 2 / -1;
  }
}

@media (max-width: 720px) {
  .vault-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .vault-toolbar__count {
    flex-basis: 100%;
  }

  .vault-search {
    width: auto;
    flex: 1;
  }

  .vault-passkey-button {
    width: 28px;
    padding: 0;
    font-size: 0;
  }

  .vault-row {
    grid-template-columns: 34px minmax(0, 1fr) auto;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-3);
  }

  .vault-row__actions {
    grid-column: 2 / -1;
    justify-content: flex-end;
  }

  .vault-row__secret {
    grid-column: 2 / -1;
  }

  .vault-locked {
    min-height: 520px;
    padding: var(--space-5) var(--space-4);
  }
}

@keyframes vault-enter {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
