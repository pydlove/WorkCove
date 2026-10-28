<script setup lang="ts">
import {
  KeyRound,
  LoaderCircle,
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
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import type {
  VaultEntry,
  VaultEntryInput,
} from '@/types/vault'

type VaultState = 'checking' | 'locked' | 'unlocked'

interface MailVaultCredentialSelection {
  entry: VaultEntry
  password: string
}

const props = withDefaults(
  defineProps<{
    selectedEntryId?: number | null
    hasExistingPassword?: boolean
  }>(),
  {
    selectedEntryId: null,
    hasExistingPassword: false,
  },
)

const emit = defineEmits<{
  select: [selection: MailVaultCredentialSelection | null]
  busy: [busy: boolean]
}>()

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
const entryOptions = computed(() =>
  entries.value.map((entry) => ({
    value: String(entry.id),
    label: entry.name,
    description: [entry.username, entry.category]
      .filter(Boolean)
      .join(' · '),
  })),
)
const description = computed(() =>
  props.hasExistingPassword
    ? '可选；不选择时继续使用当前已保存的密码。'
    : '必须从密码库选择，不能手动输入邮箱密码。',
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
    await unlockVaultWithPassword(unlockPassword.value)
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
      listVaultEntries(),
      listVaultCategories(),
    ])
    entries.value = nextEntries
    categories.value = nextCategories.map((category) => category.name)
  } catch (error) {
    handleVaultError(error)
  } finally {
    loadingEntries.value = false
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
    const entry = await createVaultEntry(input)
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
    const secret = await getVaultSecret(entry.id)
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
  <section class="mail-vault-picker">
    <header>
      <span><ShieldCheck :size="16" /></span>
      <div>
        <strong>
          邮箱密码
          <i v-if="!hasExistingPassword">*</i>
        </strong>
        <p>{{ description }}</p>
      </div>
    </header>

    <div v-if="state === 'checking'" class="mail-vault-picker__state">
      <LoaderCircle class="mail-vault-picker__spin" :size="16" />
      正在检查密码库…
    </div>

    <div v-else-if="state === 'locked'" class="mail-vault-picker__locked">
      <p>
        <LockKeyhole :size="14" />
        密码库已锁定，验证登录密码后即可选择邮箱密码。
      </p>
      <UiButton size="lg" @click="openUnlockDialog">
        <LockKeyhole :size="14" />
        解锁密码库
      </UiButton>
    </div>

    <div v-else class="mail-vault-picker__entries">
      <div class="mail-vault-picker__select">
        <UiSelect
          v-model="selectedValue"
          :options="entryOptions"
          size="lg"
          placeholder="请选择密码库条目"
          aria-label="从密码库选择邮箱密码"
          :disabled="loadingEntries || selecting"
        />
        <button
          class="mail-vault-picker__refresh"
          type="button"
          title="刷新密码库"
          aria-label="刷新密码库"
          :disabled="loadingEntries"
          @click="loadEntries"
        >
          <RefreshCw
            :size="14"
            :class="{ 'mail-vault-picker__spin': loadingEntries }"
          />
        </button>
        <UiButton size="lg" @click="openAddEntry">
          <Plus :size="13" />
          添加密码
        </UiButton>
      </div>

      <div v-if="selectedEntry" class="mail-vault-picker__selected">
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
        class="mail-vault-picker__empty"
      >
        <p>密码库中还没有条目，请先添加一个邮箱密码。</p>
        <UiButton size="lg" @click="openAddEntry">
          <Plus :size="13" />
          添加密码
        </UiButton>
      </div>
    </div>

    <p v-if="errorMessage" class="mail-vault-picker__error" role="alert">
      {{ errorMessage }}
    </p>

    <UiDialog
      v-if="unlockDialogOpen"
      title="解锁密码库"
      description="输入登录密码后，才能读取并选择密码库中的邮箱密码。"
      height="auto"
      @close="closeUnlockDialog"
    >
      <form
        id="mail-vault-unlock-form"
        class="mail-vault-picker__unlock-dialog"
        @submit.prevent="unlock"
      >
        <UiInput
          id="mail-vault-unlock-password"
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
          class="mail-vault-picker__error"
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
          form="mail-vault-unlock-form"
          :disabled="!unlockPassword || unlocking"
        >
          {{ unlocking ? '验证中…' : '解锁' }}
        </UiButton>
      </template>
    </UiDialog>

    <VaultEntryDialog
      v-if="addEntryOpen"
      :categories="categories"
      default-category="邮箱"
      @close="addEntryOpen = false"
      @save="saveEntry"
    />
  </section>
</template>

<style scoped>
.mail-vault-picker {
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

.mail-vault-picker > header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.mail-vault-picker > header > span {
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

.mail-vault-picker > header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-vault-picker > header i {
  color: var(--system-red);
  font-style: normal;
}

.mail-vault-picker > header p {
  margin: 2px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.mail-vault-picker__state,
.mail-vault-picker__locked > p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-vault-picker__locked {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.mail-vault-picker__locked > p svg {
  flex: 0 0 auto;
  color: var(--system-orange);
}

.mail-vault-picker__entries {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.mail-vault-picker__select {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px auto;
  align-items: center;
  gap: var(--space-2);
}

.mail-vault-picker__refresh {
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

.mail-vault-picker__refresh:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.mail-vault-picker__selected {
  display: grid;
  min-width: 0;
  grid-template-columns: 26px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  background: var(--canvas-raised);
  border-radius: var(--radius-control);
}

.mail-vault-picker__selected > span {
  display: grid;
  width: 26px;
  height: 26px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  place-items: center;
}

.mail-vault-picker__selected > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.mail-vault-picker__selected strong,
.mail-vault-picker__selected small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-vault-picker__selected strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.mail-vault-picker__selected small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-vault-picker__selected > button {
  padding: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
}

.mail-vault-picker__selected > button:hover {
  color: var(--system-red);
}

.mail-vault-picker__empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 2px 0;
}

.mail-vault-picker__empty > p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.mail-vault-picker__unlock-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: 0 var(--space-5) var(--space-4);
}

.mail-vault-picker__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.mail-vault-picker__spin {
  animation: mail-vault-spin 800ms linear infinite;
}

@keyframes mail-vault-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 520px) {
  .mail-vault-picker__select {
    grid-template-columns: minmax(0, 1fr) 34px;
  }

  .mail-vault-picker__select > :deep(.ui-button) {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .mail-vault-picker__empty {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
