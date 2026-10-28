<script setup lang="ts">
import { Eye, EyeOff, KeyRound } from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCombobox from '@/components/ui/UiCombobox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import {
  DEFAULT_VAULT_CATEGORY,
  type VaultEntry,
  type VaultEntryInput,
} from '@/types/vault'

const props = withDefaults(
  defineProps<{
    entry?: VaultEntry | null
    categories?: string[]
    defaultCategory?: string
  }>(),
  {
    entry: null,
    categories: () => [],
    defaultCategory: DEFAULT_VAULT_CATEGORY,
  },
)

const emit = defineEmits<{
  close: []
  save: [input: VaultEntryInput]
}>()

const errorMessage = ref('')
const passwordVisible = ref(false)
const form = reactive({
  name: '',
  username: '',
  password: '',
  url: '',
  category: '',
  notes: '',
})

const title = computed(() =>
  props.entry ? '编辑密码' : '添加密码',
)

watch(
  () => props.entry,
  (entry) => {
    form.name = entry?.name ?? ''
    form.username = entry?.username ?? ''
    form.password = ''
    form.url = entry?.url ?? ''
    form.category =
      entry?.category ??
      props.defaultCategory ??
      DEFAULT_VAULT_CATEGORY
    form.notes = entry?.notes ?? ''
    passwordVisible.value = false
    errorMessage.value = ''
  },
  { immediate: true },
)

function submit() {
  errorMessage.value = ''
  if (!form.name.trim()) {
    errorMessage.value = '请输入条目名称'
    return
  }
  if (!props.entry && !form.password) {
    errorMessage.value = '请输入要保存的密码'
    return
  }
  if (!form.category.trim()) {
    errorMessage.value = '请输入分类名称'
    return
  }
  if ([...form.category.trim()].length > 24) {
    errorMessage.value = '分类不能超过 24 个字符'
    return
  }
  if (
    form.url.trim() &&
    !/^https?:\/\//i.test(form.url.trim())
  ) {
    errorMessage.value = '网址需要以 http:// 或 https:// 开头'
    return
  }
  emit('save', {
    name: form.name,
    username: form.username,
    password: form.password,
    url: form.url,
    category: form.category,
    notes: form.notes,
  })
}
</script>

<template>
  <UiDialog
    :title="title"
    description="密码会加密保存，列表只展示账号信息，查看密码需要先验证身份。"
    initial-focus="#vault-entry-name"
    @close="emit('close')"
  >
    <form
      id="vault-entry-form"
      class="vault-entry-form"
      @submit.prevent="submit"
    >
      <div class="vault-entry-form__body">
        <UiFormField
          label="名称"
          control-id="vault-entry-name"
          layout="dialog"
          required
        >
          <UiInput
            id="vault-entry-name"
            v-model="form.name"
            size="lg"
            maxlength="100"
            placeholder="例如：生产数据库"
          />
        </UiFormField>

        <UiFormField
          label="用户名"
          control-id="vault-entry-username"
          layout="dialog"
        >
          <UiInput
            id="vault-entry-username"
            v-model="form.username"
            size="lg"
            maxlength="255"
            autocomplete="off"
            spellcheck="false"
            placeholder="账号或用户名"
          />
        </UiFormField>

        <UiFormField
          label="密码"
          control-id="vault-entry-password"
          :hint="entry ? '留空则保持原密码不变' : '密码只会加密保存在本机数据库'"
          layout="dialog"
          :required="!entry"
        >
          <span class="vault-entry-form__password">
            <UiInput
              id="vault-entry-password"
              v-model="form.password"
              :type="passwordVisible ? 'text' : 'password'"
              size="lg"
              maxlength="1024"
              autocomplete="new-password"
              :placeholder="entry ? '不修改密码' : '输入密码'"
            />
            <button
              type="button"
              :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
              @click="passwordVisible = !passwordVisible"
            >
              <EyeOff v-if="passwordVisible" :size="15" />
              <Eye v-else :size="15" />
            </button>
          </span>
        </UiFormField>

        <UiFormField
          label="分类"
          control-id="vault-entry-category"
          hint="选择已有分类，或直接输入新分类"
          layout="dialog"
        >
          <UiCombobox
            id="vault-entry-category"
            v-model="form.category"
            :options="categories"
            aria-label="密码分类"
            placeholder="例如：服务器、数据库、开发工具"
          />
        </UiFormField>

        <UiFormField
          label="网址"
          control-id="vault-entry-url"
          layout="dialog"
        >
          <UiInput
            id="vault-entry-url"
            v-model="form.url"
            size="lg"
            maxlength="2048"
            spellcheck="false"
            placeholder="https://example.com"
          />
        </UiFormField>

        <UiFormField
          label="备注"
          control-id="vault-entry-notes"
          layout="dialog"
        >
          <textarea
            id="vault-entry-notes"
            v-model="form.notes"
            rows="4"
            maxlength="4000"
            placeholder="附加说明、恢复方式或注意事项"
          />
        </UiFormField>

        <p v-if="errorMessage" class="vault-entry-form__error" role="alert">
          {{ errorMessage }}
        </p>
      </div>
    </form>

    <template #footer>
      <div class="vault-entry-form__actions">
        <span>
          <KeyRound :size="13" />
          仅在你解锁后可用
        </span>
        <div>
          <UiButton size="lg" @click="emit('close')">取消</UiButton>
          <UiButton
            variant="primary"
            size="lg"
            type="submit"
            form="vault-entry-form"
          >
            保存
          </UiButton>
        </div>
      </div>
    </template>
  </UiDialog>
</template>

<style scoped>
.vault-entry-form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 var(--space-5);
}

.vault-entry-form__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 2px 0 var(--space-4);
  overflow-y: auto;
}

.vault-entry-form__password {
  position: relative;
  display: block;
}

.vault-entry-form__password :deep(.ui-input) {
  padding-right: 34px;
}

.vault-entry-form__password button {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  width: 28px;
  height: var(--control-md);
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.vault-entry-form__password button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.vault-entry-form textarea {
  width: 100%;
  min-height: 82px;
  padding: 8px;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.55;
  resize: vertical;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.vault-entry-form textarea:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.vault-entry-form__error {
  margin: -2px 0 0 calc(64px + var(--space-3));
  color: var(--system-red);
  font-size: var(--font-caption);
}

.vault-entry-form__actions {
  display: flex;
  width: 100%;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.vault-entry-form__actions > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.vault-entry-form__actions > div {
  display: flex;
  gap: var(--space-3);
}

@media (max-width: 520px) {
  .vault-entry-form {
    padding: 0 var(--space-4);
  }

  .vault-entry-form__error {
    margin-left: 0;
  }

  .vault-entry-form__actions > span {
    display: none;
  }

  .vault-entry-form__actions {
    justify-content: flex-end;
  }
}
</style>
