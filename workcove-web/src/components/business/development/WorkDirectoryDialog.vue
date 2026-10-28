<script setup lang="ts">
import { FolderOpen } from '@lucide/vue'
import { reactive, ref, watch } from 'vue'

import { chooseLocalDirectory } from '@/api/development'
import UiButton from '@/components/ui/UiButton.vue'
import UiCombobox from '@/components/ui/UiCombobox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import {
  DEFAULT_WORK_DIRECTORY_CATEGORY,
  type WorkDirectory,
  type WorkDirectoryInput,
} from '@/types/workDirectory'

const props = withDefaults(
  defineProps<{
    directory?: WorkDirectory | null
    categories?: string[]
    defaultCategory?: string
  }>(),
  {
    directory: null,
    categories: () => [],
    defaultCategory: DEFAULT_WORK_DIRECTORY_CATEGORY,
  },
)

const emit = defineEmits<{
  close: []
  save: [input: WorkDirectoryInput]
  remove: [id: string]
}>()

const errorMessage = ref('')
const choosingDirectory = ref(false)
const confirmingRemove = ref(false)
const form = reactive({
  name: '',
  path: '',
  category: DEFAULT_WORK_DIRECTORY_CATEGORY,
  description: '',
})

watch(
  () => props.directory,
  (directory) => {
    form.name = directory?.name ?? ''
    form.path = directory?.path ?? ''
    form.category =
      directory?.category ??
      props.defaultCategory ??
      DEFAULT_WORK_DIRECTORY_CATEGORY
    form.description = directory?.description ?? ''
    errorMessage.value = ''
  },
  { immediate: true },
)

function submit() {
  const path = form.path.trim()
  const category = form.category.trim()
  const name =
    form.name.trim() ||
    path.split('/').filter(Boolean).pop() ||
    ''

  errorMessage.value = ''
  if (!name) {
    errorMessage.value = '请输入目录名称'
    return
  }
  if ([...name].length > 40) {
    errorMessage.value = '目录名称不能超过 40 个字符'
    return
  }
  if (!category) {
    errorMessage.value = '请输入分类名称'
    return
  }
  if ([...category].length > 24) {
    errorMessage.value = '分类不能超过 24 个字符'
    return
  }
  if (!path || !path.startsWith('/')) {
    errorMessage.value = '请输入以 / 开头的绝对路径'
    return
  }
  if ([...path].length > 512) {
    errorMessage.value = '目录路径过长'
    return
  }
  if ([...form.description].length > 160) {
    errorMessage.value = '备注不能超过 160 个字符'
    return
  }

  emit('save', {
    id: props.directory?.id,
    name,
    path,
    category,
    description: form.description.trim(),
  })
}

async function chooseDirectory() {
  choosingDirectory.value = true
  errorMessage.value = ''
  try {
    const selectedPath = window.workCoveDesktop
      ? await window.workCoveDesktop.chooseDirectory()
      : await chooseLocalDirectory()
    if (!selectedPath) return
    if (!selectedPath.startsWith('/')) {
      form.path = ''
      errorMessage.value = '目录选择器返回了无效路径，请手动填写'
      return
    }
    form.path = selectedPath
    if (!form.name.trim()) {
      form.name =
        selectedPath.split('/').filter(Boolean).pop() ?? '工作目录'
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法选择项目目录'
  } finally {
    choosingDirectory.value = false
  }
}

function removeDirectory() {
  if (props.directory) confirmingRemove.value = true
}

function confirmRemoveDirectory() {
  if (!props.directory) return
  confirmingRemove.value = false
  emit('remove', props.directory.id)
}
</script>

<template>
  <UiDialog
    :title="directory ? '编辑工作目录' : '添加工作目录'"
    description="管理常用项目路径，桌面版可以直接打开对应文件夹。"
    initial-focus="#work-directory-name"
    :suspended="confirmingRemove"
    @close="emit('close')"
  >
    <form
      id="work-directory-form"
      class="directory-form"
      @submit.prevent="submit"
    >
      <div class="directory-form__body">
        <UiFormField
          label="名称"
          control-id="work-directory-name"
          hint="留空时使用路径最后一级目录名"
          layout="dialog"
        >
          <UiInput
            id="work-directory-name"
            v-model="form.name"
            size="lg"
            maxlength="40"
            placeholder="例如：研发项目"
          />
        </UiFormField>

        <UiFormField
          label="分类"
          control-id="work-directory-category"
          hint="选择已有分类，或直接输入新分类"
          layout="dialog"
          required
        >
          <UiCombobox
            id="work-directory-category"
            v-model="form.category"
            :options="categories"
            aria-label="工作目录分类"
            placeholder="例如：AI 安全"
          />
        </UiFormField>

        <UiFormField
          label="路径"
          control-id="work-directory-path"
          hint="本机绝对路径，例如 /Users/you/Projects/your-project"
          layout="dialog"
          required
        >
          <span class="directory-form__path-row">
            <UiInput
              id="work-directory-path"
              v-model="form.path"
              size="lg"
              maxlength="512"
              placeholder="/Users/you/Projects/your-project"
              spellcheck="false"
            />
            <UiButton
              size="lg"
              :disabled="choosingDirectory"
              @click="chooseDirectory"
            >
              <FolderOpen :size="14" />
              {{ choosingDirectory ? '选择中…' : '选择目录' }}
            </UiButton>
          </span>
        </UiFormField>

        <UiFormField
          label="备注"
          control-id="work-directory-description"
          hint="可选，最多 160 个字符"
          layout="dialog"
        >
          <textarea
            id="work-directory-description"
            v-model="form.description"
            class="directory-form__textarea"
            rows="3"
            maxlength="160"
            placeholder="这个目录用于什么工作"
          />
        </UiFormField>

        <p v-if="errorMessage" class="directory-form__error" role="alert">
          {{ errorMessage }}
        </p>
      </div>
    </form>

    <template #footer>
      <div class="directory-form__actions">
        <UiButton
          v-if="directory"
          variant="danger-ghost"
          size="lg"
          @click="removeDirectory"
        >
          移除目录
        </UiButton>
        <span v-else />
        <div>
          <UiButton size="lg" @click="emit('close')">取消</UiButton>
          <UiButton
            variant="primary"
            size="lg"
            type="submit"
            form="work-directory-form"
          >
            保存
          </UiButton>
        </div>
      </div>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="confirmingRemove && directory"
    title="移除工作目录？"
    description="目录会从 8小时工作台中移除，本地文件不会被删除。"
    confirm-label="移除目录"
    @close="confirmingRemove = false"
    @confirm="confirmRemoveDirectory"
  >
    <div class="directory-remove-target">
      <strong>{{ directory.name }}</strong>
      <small>{{ directory.path }}</small>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.directory-form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 var(--space-5);
}

.directory-form__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 2px 0 var(--space-4);
  overflow-y: auto;
}

.directory-form__textarea {
  width: 100%;
  min-height: 72px;
  padding: 7px 8px;
  color: var(--text);
  font-size: var(--font-body);
  line-height: var(--line-body);
  resize: vertical;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 4%);
}

.directory-form__path-row {
  display: flex;
  gap: var(--space-2);
}

.directory-form__textarea:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.directory-form__textarea:focus-visible {
  outline: none;
}

.directory-form__error {
  margin: -2px 0 0 calc(64px + var(--space-3));
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.directory-form__actions {
  display: flex;
  width: 100%;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.directory-remove-target {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.directory-remove-target strong,
.directory-remove-target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-remove-target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-remove-target small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.directory-form__actions > div {
  display: flex;
  gap: var(--space-3);
}

@media (max-width: 520px) {
  .directory-form {
    padding: 0 var(--space-4);
  }

  .directory-form__error {
    margin-left: 0;
  }

  .directory-form__path-row {
    flex-direction: column;
  }

  .directory-form__path-row .ui-button {
    width: 100%;
  }
}
</style>
