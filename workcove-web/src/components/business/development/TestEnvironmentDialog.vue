<script setup lang="ts">
import {
  Eye,
  EyeOff,
  Globe2,
  KeyRound,
  Save,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from 'vue'

import VaultPasswordPicker from '@/components/business/vault/VaultPasswordPicker.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useTestEnvironmentStore } from '@/stores/useTestEnvironmentStore'
import type { TestEnvironment } from '@/types/testEnvironment'
import type { VaultEntry } from '@/types/vault'
import { testEnvironmentValuesFromWebsite } from '@/utils/testEnvironment'

const props = defineProps<{
  workspaceId: string
  environment: TestEnvironment | null
}>()

const emit = defineEmits<{
  close: []
  saved: [environment: TestEnvironment]
}>()

const shortcuts = useShortcutStore()
const environments = useTestEnvironmentStore()
const saving = ref(false)
const passwordVisible = ref(false)
const vaultPickerOpen = ref(false)
const vaultCredentialBusy = ref(false)
const selectedVaultEntryId = ref<number | null>(null)
const selectedVaultEntryName = ref('')
const errorMessage = ref('')
const form = reactive({
  websiteId: '',
  name: props.environment?.name ?? '',
  url: props.environment?.url ?? '',
  username: props.environment?.username ?? '',
  password: '',
})

const websiteOptions = computed(() => [
  {
    value: '',
    label: '手动填写',
    description: '直接输入测试环境地址',
  },
  ...shortcuts.websites
    .filter(
      (website) => website.workspaceId === props.workspaceId,
    )
    .map((website) => ({
      value: website.id,
      label: website.name,
      description: website.url,
    })),
])

watch(
  () => props.environment,
  (environment) => {
    form.websiteId = ''
    form.name = environment?.name ?? ''
    form.url = environment?.url ?? ''
    form.username = environment?.username ?? ''
    form.password = ''
    passwordVisible.value = false
    clearVaultSelection()
    vaultPickerOpen.value = false
    errorMessage.value = ''
  },
)

onBeforeUnmount(() => {
  form.password = ''
  clearVaultSelection()
})

function selectWebsite(websiteId: string) {
  form.websiteId = websiteId
  if (!websiteId) return
  const website = shortcuts.websites.find(
    (item) =>
      item.id === websiteId &&
      item.workspaceId === props.workspaceId,
  )
  if (!website) return
  const values = testEnvironmentValuesFromWebsite(website)
  form.name = values.name
  form.url = values.url
  errorMessage.value = ''
}

function validateUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function openVaultPicker() {
  errorMessage.value = ''
  vaultPickerOpen.value = true
}

function closeVaultPicker() {
  if (vaultCredentialBusy.value) return
  vaultPickerOpen.value = false
}

function handleVaultSelection(
  selection: { entry: VaultEntry; password: string } | null,
) {
  if (!selection) {
    clearVaultSelection()
    form.password = ''
    return
  }
  selectedVaultEntryId.value = selection.entry.id
  selectedVaultEntryName.value = selection.entry.name
  form.password = selection.password
  passwordVisible.value = false
  errorMessage.value = ''
}

function clearVaultSelection() {
  selectedVaultEntryId.value = null
  selectedVaultEntryName.value = ''
}

function clearVaultSelectionAfterManualInput() {
  if (!selectedVaultEntryId.value) return
  clearVaultSelection()
}

async function save() {
  if (saving.value || vaultCredentialBusy.value) return
  errorMessage.value = ''
  const name = form.name.trim()
  const url = form.url.trim()
  const username = form.username.trim()
  if (!name) {
    errorMessage.value = '请输入环境名称'
    return
  }
  if (!validateUrl(url)) {
    errorMessage.value =
      '测试链接必须是有效的 HTTP 或 HTTPS 地址'
    return
  }
  if (!username) {
    errorMessage.value = '请输入登录用户名'
    return
  }
  if (!props.environment && !form.password.trim()) {
    errorMessage.value = '新增测试环境时必须填写密码'
    return
  }

  saving.value = true
  try {
    const saved = await environments.save({
      id: props.environment?.id,
      workspaceId: props.workspaceId,
      name,
      url,
      username,
      password: form.password,
    })
    emit('saved', saved)
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '测试环境保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UiDialog
    :title="environment ? '编辑测试环境' : '添加测试环境'"
    description="统一保存 Codex 验证所需的地址和登录信息，密码会加密保存。"
    size="wide"
    height="standard"
    scroll-mode="body"
    initial-focus="#test-environment-name"
    :suspended="saving"
    @close="emit('close')"
  >
    <form
      class="test-environment-form"
      @submit.prevent="save"
    >
      <UiFormField label="从网址选择">
        <UiSelect
          :model-value="form.websiteId"
          :options="websiteOptions"
          size="lg"
          searchable
          aria-label="从已保存的网址选择测试环境"
          placeholder="手动填写"
          @update:model-value="selectWebsite"
        />
        <p class="test-environment-form__selection-hint">
          <Globe2 :size="13" />
          选择已保存网址后会自动填入名称和链接。
        </p>
      </UiFormField>

      <UiFormField
        label="环境名称"
        control-id="test-environment-name"
        required
      >
        <UiInput
          id="test-environment-name"
          v-model="form.name"
          size="lg"
          maxlength="80"
          placeholder="例如：V3.0.2 研发环境"
        />
      </UiFormField>

      <UiFormField label="测试链接" required>
        <UiInput
          v-model="form.url"
          size="lg"
          maxlength="2048"
          inputmode="url"
          autocomplete="url"
          placeholder="https://test.example.com"
        />
      </UiFormField>

      <div class="test-environment-form__account">
        <UiFormField label="用户名" required>
          <UiInput
            v-model="form.username"
            size="lg"
            maxlength="256"
            autocomplete="username"
            placeholder="测试账号"
          />
        </UiFormField>

        <UiFormField
          label="密码"
          :hint="
            environment
              ? '留空则保持原密码不变'
              : '密码会使用应用主密钥加密保存'
          "
          :required="!environment"
        >
          <div class="test-environment-form__password">
            <UiInput
              v-model="form.password"
              size="lg"
              :type="passwordVisible ? 'text' : 'password'"
              maxlength="512"
              autocomplete="new-password"
              placeholder="测试密码"
              @input="clearVaultSelectionAfterManualInput"
            />
            <button
              type="button"
              :aria-label="
                passwordVisible ? '隐藏密码' : '显示密码'
              "
              :title="
                passwordVisible ? '隐藏密码' : '显示密码'
              "
              @click="passwordVisible = !passwordVisible"
            >
              <EyeOff v-if="passwordVisible" :size="15" />
              <Eye v-else :size="15" />
            </button>
          </div>
          <div class="test-environment-form__vault">
            <UiButton
              size="sm"
              :disabled="saving"
              @click="openVaultPicker"
            >
              <KeyRound :size="13" />
              从密码库选择
            </UiButton>
            <span
              v-if="selectedVaultEntryName"
              :title="selectedVaultEntryName"
            >
              已使用「{{ selectedVaultEntryName }}」
            </span>
          </div>
        </UiFormField>
      </div>

      <p
        v-if="errorMessage"
        class="test-environment-form__error"
        role="alert"
      >
        {{ errorMessage }}
      </p>
    </form>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="saving || vaultCredentialBusy"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        :loading="saving"
        :disabled="vaultCredentialBusy"
        @click="save"
      >
        <Save :size="14" />
        保存环境
      </UiButton>
    </template>
  </UiDialog>

  <UiDialog
    v-if="vaultPickerOpen"
    title="从密码库选择密码"
    description="密码只在当前表单中使用，保存测试环境后仍会加密写入后端。"
    size="wide"
    height="compact"
    scroll-mode="body"
    @close="closeVaultPicker"
  >
    <div class="test-environment-vault-picker">
      <VaultPasswordPicker
        :workspace-id="workspaceId"
        :selected-entry-id="selectedVaultEntryId"
        :has-existing-password="Boolean(environment)"
        label="测试环境密码"
        description="可以从密码库选择，也可以返回后手动输入。"
        :required="false"
        default-category="研发"
        @select="handleVaultSelection"
        @busy="vaultCredentialBusy = $event"
      />
    </div>

    <template #footer>
      <UiButton
        variant="primary"
        size="lg"
        :disabled="vaultCredentialBusy"
        @click="closeVaultPicker"
      >
        完成
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.test-environment-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-5);
}

.test-environment-form__selection-hint {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.test-environment-form__account {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-3);
}

.test-environment-form__password {
  position: relative;
}

.test-environment-form__password :deep(.ui-input) {
  padding-right: var(--control-lg);
}

.test-environment-form__password button {
  position: absolute;
  top: 50%;
  right: var(--space-1);
  display: grid;
  width: var(--control-sm);
  height: var(--control-sm);
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
  transform: translateY(-50%);
}

.test-environment-form__password button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.test-environment-form__vault {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.test-environment-form__vault span {
  min-width: 0;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-environment-vault-picker {
  padding: 0 var(--space-5) var(--space-4);
}

.test-environment-form__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 640px) {
  .test-environment-form {
    padding: 0 var(--space-4) var(--space-4);
  }

  .test-environment-form__account {
    grid-template-columns: 1fr;
  }

  .test-environment-vault-picker {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>
