<script setup lang="ts">
import {
  KeyRound,
  LockKeyhole,
  Plus,
  RefreshCw,
  ShieldCheck,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue'

import { ApiError } from '@/api/http'
import {
  createVaultEntry,
  getVaultSecret,
  getVaultStatus,
  listVaultCategories,
  listVaultEntries,
  unlockVaultWithPassword,
} from '@/api/vault'
import VaultEntryDialog from '@/components/business/vault/VaultEntryDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { loadUserWorkbenchSettings } from '@/services/userWorkbench'
import { DEFAULT_VAULT_UNLOCK_MINUTES } from '@/services/vaultPreferences'
import type {
  VaultEntry,
  VaultEntryInput,
} from '@/types/vault'

type VaultState = 'checking' | 'locked' | 'unlocked'

interface VaultCredentialSelection {
  entry: VaultEntry
  password: string
}

const props = withDefaults(
  defineProps<{
    workspaceId: string
    selectedEntryId?: number | null
    hasExistingPassword?: boolean
    label?: string
    description?: string
    required?: boolean
    defaultCategory?: string
    preferredUrl?: string
    preferredName?: string
  }>(),
  {
    selectedEntryId: null,
    hasExistingPassword: false,
    label: '密码',
    description: '',
    required: undefined,
    defaultCategory: '邮箱',
    preferredUrl: '',
    preferredName: '',
  },
)

const emit = defineEmits<{
  select: [selection: VaultCredentialSelection | null]
  busy: [busy: boolean]
}>()

const auth = useAuthStore()
const state = ref<VaultState>('checking')
const entries = ref<VaultEntry[]>([])
const categories = ref<string[]>([])
const unlockPassword = ref('')
const unlockDialogOpen = ref(false)
const unlockError = ref('')
const unlocking = ref(false)
const loadingEntries = ref(false)
const selecting = ref(false)
const addEntryOpen = ref(false)
const savingEntry = ref(false)
const errorMessage = ref('')
const vaultUnlockMinutes = computed(
  () =>
    loadUserWorkbenchSettings(auth.email)?.vaultUnlockMinutes ??
    DEFAULT_VAULT_UNLOCK_MINUTES,
)

const selectedEntry = computed(
  () =>
    entries.value.find((entry) => entry.id === props.selectedEntryId) ??
    null,
)
const selectedValue = computed({
  get: () => String(props.selectedEntryId ?? ''),
  set: (value: string) => {
    void selectEntry(value)
  },
})
const preferredOrigin = computed(() => {
  try {
    return new URL(props.preferredUrl).origin
  } catch {
    return ''
  }
})
const orderedEntries = computed(() =>
  [...entries.value].sort((left, right) => {
    const leftMatches =
      preferredOrigin.value &&
      entryOrigin(left.url) === preferredOrigin.value
    const rightMatches =
      preferredOrigin.value &&
      entryOrigin(right.url) === preferredOrigin.value
    return Number(rightMatches) - Number(leftMatches)
  }),
)
const entryOptions = computed(() =>
  orderedEntries.value.map((entry) => ({
    value: String(entry.id),
    label: entry.name,
    description: [entry.username, entry.category]
      .concat(
        preferredOrigin.value &&
          entryOrigin(entry.url) === preferredOrigin.value
          ? ['当前网站']
          : [],
      )
      .filter(Boolean)
      .join(' · '),
  })),
)
const resolvedDescription = computed(() =>
  props.description ||
  (props.hasExistingPassword
    ? '可选；不选择时继续使用当前已保存的密码。'
    : '必须从密码库选择，不能手动输入密码。'),
)
const resolvedRequired = computed(
  () => props.required ?? !props.hasExistingPassword,
)

onMounted(() => {
  void loadStatus()
})

onBeforeUnmount(() => {
  unlockPassword.value = ''
  unlockError.value = ''
})

async function loadStatus() {
  state.value = 'checking'
  errorMessage.value = ''
  try {
    const status = await getVaultStatus()
    if (!status.unlocked) {
      state.value = 'locked'
      entries.value = []
      return
    }
    state.value = 'unlocked'
    await loadEntries()
  } catch (error) {
    state.value = 'locked'
    errorMessage.value = errorText(error)
  }
}

async function unlock() {
  if (!unlockPassword.value || unlocking.value) return
  unlocking.value = true
  unlockError.value = ''
  try {
    await unlockVaultWithPassword(
      unlockPassword.value,
      vaultUnlockMinutes.value,
    )
    unlockPassword.value = ''
    state.value = 'unlocked'
    await loadEntries()
    unlockDialogOpen.value = false
  } catch (error) {
    unlockError.value = errorText(error)
  } finally {
    unlocking.value = false
  }
}

async function loadEntries() {
  loadingEntries.value = true
  errorMessage.value = ''
  try {
    const [nextEntries, nextCategories] = await Promise.all([
      listVaultEntries(props.workspaceId),
      listVaultCategories(props.workspaceId),
    ])
    entries.value = nextEntries
    categories.value = nextCategories.map((category) => category.name)
    await autoSelectPreferredEntry(nextEntries)
  } catch (error) {
    handleVaultError(error)
  } finally {
    loadingEntries.value = false
  }
}

async function autoSelectPreferredEntry(nextEntries: VaultEntry[]) {
  const preferredName = props.preferredName.trim()
  let entry = preferredName
    ? nextEntries.find((item) => item.name === preferredName)
    : undefined
  if (!entry && preferredOrigin.value) {
    const originMatches = nextEntries.filter(
      (item) => entryOrigin(item.url) === preferredOrigin.value,
    )
    if (originMatches.length === 1) entry = originMatches[0]
  }
  if (entry && entry.id !== props.selectedEntryId) {
    await selectEntry(String(entry.id))
  }
}

function openUnlockDialog() {
  unlockPassword.value = ''
  unlockError.value = ''
  unlockDialogOpen.value = true
}

function closeUnlockDialog() {
  if (unlocking.value) return
  unlockDialogOpen.value = false
  unlockPassword.value = ''
  unlockError.value = ''
}

function openAddEntry() {
  if (state.value !== 'unlocked') return
  addEntryOpen.value = true
}

async function saveEntry(input: VaultEntryInput) {
  if (savingEntry.value) return
  savingEntry.value = true
  errorMessage.value = ''
  try {
    const entry = await createVaultEntry(props.workspaceId, input)
    addEntryOpen.value = false
    await loadEntries()
    await selectEntry(String(entry.id))
  } catch (error) {
    handleVaultError(error)
  } finally {
    savingEntry.value = false
  }
}

async function selectEntry(value: string) {
  if (!value) {
    clearSelection()
    return
  }
  const entry = entries.value.find((item) => item.id === Number(value))
  if (!entry || selecting.value) return
  selecting.value = true
  emit('busy', true)
  errorMessage.value = ''
  try {
    const secret = await getVaultSecret(entry.id, props.workspaceId)
    emit('select', {
      entry,
      password: secret.password,
    })
  } catch (error) {
    handleVaultError(error)
  } finally {
    selecting.value = false
    emit('busy', false)
  }
}

function clearSelection() {
  emit('select', null)
}

function entryOrigin(value: string) {
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
      ? parsed.origin
      : ''
  } catch {
    return ''
  }
}

function handleVaultError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    state.value = 'locked'
    entries.value = []
    emit('select', null)
    errorMessage.value = '密码库验证已过期，请重新解锁'
    return
  }
  errorMessage.value = errorText(error)
}

function errorText(error: unknown) {
  return error instanceof Error
    ? error.message
    : '密码库操作失败，请重试'
}
</script>

<template>
  <section class="vault-password-picker">
    <header>
      <span><ShieldCheck :size="16" /></span>
      <div>
        <strong>
          {{ label }}
          <i v-if="resolvedRequired">*</i>
        </strong>
        <p>{{ resolvedDescription }}</p>
      </div>
    </header>

    <div v-if="state === 'checking'" class="vault-password-picker__state">
      <UiFidgetSpinner :size="16" />
      正在检查密码库…
    </div>

    <div v-else-if="state === 'locked'" class="vault-password-picker__locked">
      <p>
        <LockKeyhole :size="14" />
        密码库已锁定，验证登录密码后即可选择{{ label }}。
      </p>
      <UiButton size="lg" @click="openUnlockDialog">
        <LockKeyhole :size="14" />
        解锁密码库
      </UiButton>
    </div>

    <div v-else class="vault-password-picker__entries">
      <div class="vault-password-picker__select">
        <UiSelect
          v-model="selectedValue"
          :options="entryOptions"
          size="lg"
          placeholder="请选择密码库条目"
          :aria-label="`从密码库选择${label}`"
          :disabled="loadingEntries || selecting"
          searchable
        />
        <button
          class="vault-password-picker__refresh"
          type="button"
          title="刷新密码库"
          aria-label="刷新密码库"
          :disabled="loadingEntries"
          @click="loadEntries"
        >
          <UiFidgetSpinner v-if="loadingEntries" :size="14" />
          <RefreshCw v-else :size="14" />
        </button>
        <UiButton size="lg" @click="openAddEntry">
          <Plus :size="13" />
          添加密码
        </UiButton>
      </div>

      <div v-if="selecting" class="vault-password-picker__state">
        <UiFidgetSpinner :size="14" />
        正在读取密码…
      </div>

      <div v-else-if="selectedEntry" class="vault-password-picker__selected">
        <span><KeyRound :size="14" /></span>
        <div>
          <strong>{{ selectedEntry.name }}</strong>
          <small>
            {{
              [selectedEntry.username, selectedEntry.category]
                .filter(Boolean)
                .join(' · ')
            }}
          </small>
        </div>
        <button type="button" @click="clearSelection">
          移除选择
        </button>
      </div>

      <div
        v-else-if="!loadingEntries && entries.length === 0"
        class="vault-password-picker__empty"
      >
        <p>密码库中还没有条目，请先添加一个密码。</p>
        <UiButton size="lg" @click="openAddEntry">
          <Plus :size="13" />
          添加密码
        </UiButton>
      </div>
    </div>

    <p v-if="errorMessage" class="vault-password-picker__error" role="alert">
      {{ errorMessage }}
    </p>

    <UiDialog
      v-if="unlockDialogOpen"
      title="解锁密码库"
      :description="`输入登录密码后，才能读取并选择密码库中的${label}。`"
      height="auto"
      @close="closeUnlockDialog"
    >
      <form
        id="vault-password-unlock-form"
        class="vault-password-picker__unlock-dialog"
        @submit.prevent="unlock"
      >
        <UiInput
          id="vault-password-unlock-password"
          v-model="unlockPassword"
          type="password"
          size="lg"
          maxlength="1024"
          autocomplete="current-password"
          placeholder="输入登录密码"
          aria-label="密码库解锁密码"
        />
        <p
          v-if="unlockError"
          class="vault-password-picker__error"
          role="alert"
        >
          {{ unlockError }}
        </p>
      </form>

      <template #footer>
        <UiButton
          size="lg"
          :disabled="unlocking"
          @click="closeUnlockDialog"
        >
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="vault-password-unlock-form"
          :loading="unlocking"
          :disabled="!unlockPassword"
        >
          {{ unlocking ? '验证中…' : '解锁' }}
        </UiButton>
      </template>
    </UiDialog>

    <VaultEntryDialog
      v-if="addEntryOpen"
      :categories="categories"
      :default-category="defaultCategory"
      :saving="savingEntry"
      @close="addEntryOpen = false"
      @save="saveEntry"
    />
  </section>
</template>

<style scoped>
.vault-password-picker {
  display: flex;
  min-width: 0;
  grid-column: 1 / -1;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--surface-secondary);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.vault-password-picker > header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.vault-password-picker > header > span {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 11%,
    transparent
  );
  border-radius: var(--radius-control);
  place-items: center;
}

.vault-password-picker > header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.vault-password-picker > header i {
  color: var(--system-red);
  font-style: normal;
}

.vault-password-picker > header p {
  margin: 2px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.vault-password-picker__state,
.vault-password-picker__locked > p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.vault-password-picker__locked {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.vault-password-picker__locked > p svg {
  flex: 0 0 auto;
  color: var(--system-orange);
}

.vault-password-picker__entries {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.vault-password-picker__select {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px auto;
  align-items: center;
  gap: var(--space-2);
}

.vault-password-picker__refresh {
  display: grid;
  width: 34px;
  height: var(--control-lg);
  padding: 0;
  color: var(--icon);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  place-items: center;
}

.vault-password-picker__refresh:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.vault-password-picker__selected {
  display: grid;
  min-width: 0;
  grid-template-columns: 26px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  background: var(--canvas-raised);
  border-radius: var(--radius-control);
}

.vault-password-picker__selected > span {
  display: grid;
  width: 26px;
  height: 26px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  place-items: center;
}

.vault-password-picker__selected > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.vault-password-picker__selected strong,
.vault-password-picker__selected small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vault-password-picker__selected strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.vault-password-picker__selected small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.vault-password-picker__selected > button {
  padding: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
}

.vault-password-picker__selected > button:hover {
  color: var(--system-red);
}

.vault-password-picker__empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 2px 0;
}

.vault-password-picker__empty > p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.vault-password-picker__unlock-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: 0 var(--space-5) var(--space-4);
}

.vault-password-picker__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .vault-password-picker__select {
    grid-template-columns: minmax(0, 1fr) 34px;
  }

  .vault-password-picker__select > :deep(.ui-button) {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .vault-password-picker__empty {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
