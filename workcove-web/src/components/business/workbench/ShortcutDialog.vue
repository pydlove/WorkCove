<script setup lang="ts">
import {
  FolderOpen,
  ImageUp,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  reactive,
  ref,
  watch,
} from 'vue'

import {
  applicationDisplayName,
  DEFAULT_WEBSITE_CATEGORY,
  normalizeWebsiteUrl,
  websiteFaviconUrls,
  websiteHostname,
} from '@/services/localShortcuts'
import { chooseHostApplication } from '@/api/hostApplications'
import ShortcutVisual from '@/components/business/workbench/ShortcutVisual.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCombobox from '@/components/ui/UiCombobox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import type {
  ApplicationShortcut,
  ShortcutIconMode,
  ShortcutInput,
  ShortcutMode,
  WebsiteShortcut,
} from '@/types/shortcut'
import { fileToSquareWebp } from '@/utils/image'

const props = withDefaults(
  defineProps<{
    mode: ShortcutMode
    shortcut?: WebsiteShortcut | ApplicationShortcut | null
    categories?: string[]
    defaultCategory?: string
  }>(),
  {
    categories: () => [],
    defaultCategory: DEFAULT_WEBSITE_CATEGORY,
  },
)

const emit = defineEmits<{
  close: []
  save: [input: ShortcutInput]
  remove: [id: string]
}>()

const isWebsite = computed(() => props.mode === 'website')
const isEditing = computed(() => Boolean(props.shortcut))
const dialogTitle = computed(() => {
  if (isEditing.value) {
    return isWebsite.value ? '编辑常用网址' : '编辑应用'
  }
  return isWebsite.value ? '添加常用网址' : '添加应用'
})
const dialogDescription = computed(() => {
  if (isWebsite.value) {
    return '添加后可从工作台直接打开；图标会优先从网站自动获取。'
  }
  return '添加后可从 App 页和工作台启动；选择应用后会自动读取名称和图标。'
})
const targetHint = computed(() => {
  if (isWebsite.value) return '点击后会在默认浏览器中打开'
  return '从系统中选择应用，自动读取名称和图标'
})
const iconInput = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const choosingApplication = ref(false)
const confirmingRemove = ref(false)
const customIconDataUrl = ref<string | null>(null)
const nativeIconDataUrl = ref<string | null>(null)
const iconModeItems: Array<{ value: ShortcutIconMode; label: string }> = [
  { value: 'auto', label: '自动获取' },
  { value: 'custom', label: '自定义' },
]
const form = reactive({
  name: '',
  target: '',
  category: DEFAULT_WEBSITE_CATEGORY,
  iconMode: 'auto' as ShortcutIconMode,
})

const autoIconUrls = computed(() => {
  if (isWebsite.value) return websiteFaviconUrls(form.target)
  return nativeIconDataUrl.value ? [nativeIconDataUrl.value] : []
})

const activeIconUrls = computed(() =>
  form.iconMode === 'custom'
    ? customIconDataUrl.value
      ? [customIconDataUrl.value]
      : []
    : autoIconUrls.value,
)
const removalTitle = computed(() =>
  isWebsite.value ? '删除网址？' : '删除 App 记录？',
)
const removalDescription = computed(() =>
  isWebsite.value
    ? `从 8小时工作台移除「${props.shortcut?.name ?? ''}」，不会影响网站本身。`
    : `只会从 8小时工作台移除「${props.shortcut?.name ?? ''}」的记录，不会删除或卸载本地 App。`,
)
const removalTarget = computed(() =>
  props.shortcut && 'url' in props.shortcut
    ? props.shortcut.url
    : props.shortcut?.path ?? '',
)

function syncForm() {
  form.name = props.shortcut?.name ?? ''
  form.target =
    props.shortcut && 'url' in props.shortcut
      ? props.shortcut.url
      : props.shortcut?.path ?? ''
  form.category =
    props.shortcut && 'category' in props.shortcut
      ? props.shortcut.category
      : props.defaultCategory || DEFAULT_WEBSITE_CATEGORY
  form.iconMode = props.shortcut?.iconMode ?? 'auto'
  customIconDataUrl.value =
    props.shortcut?.iconMode === 'custom'
      ? props.shortcut.iconDataUrl
      : null
  nativeIconDataUrl.value =
    props.mode === 'application' && props.shortcut?.iconMode === 'auto'
      ? props.shortcut.iconDataUrl
      : null
  errorMessage.value = ''
}

watch(
  () => [props.mode, props.shortcut] as const,
  syncForm,
  { immediate: true },
)

function setIconMode(mode: ShortcutIconMode) {
  form.iconMode = mode
  if (mode === 'custom' && !customIconDataUrl.value) {
    iconInput.value?.click()
  }
}

function setIconModeFromControl(value: string) {
  setIconMode(value as ShortcutIconMode)
}

async function chooseCustomIcon() {
  iconInput.value?.click()
}

async function handleIconFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  errorMessage.value = ''
  try {
    customIconDataUrl.value = await fileToSquareWebp(file, {
      maxBytes: 2 * 1024 * 1024,
      dimension: 256,
      sizeLabel: '2MB',
    })
    form.iconMode = 'custom'
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '图标处理失败，请重新选择'
  }
}

async function chooseApplication() {
  choosingApplication.value = true
  errorMessage.value = ''
  try {
    const application = await chooseHostApplication()
    if (!application) return
    form.target = application.path
    if (!form.name.trim()) form.name = application.name
    nativeIconDataUrl.value = application.iconDataUrl
    form.iconMode = 'auto'
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法读取所选应用'
  } finally {
    choosingApplication.value = false
  }
}

function submit() {
  errorMessage.value = ''
  if (!isWebsite.value && !form.target.trim()) {
    errorMessage.value = '请选择本地应用'
    return
  }

  let target = form.target.trim()
  let name = form.name.trim()
  try {
    if (isWebsite.value) {
      target = normalizeWebsiteUrl(target)
      name ||= websiteHostname(target)
    } else {
      name ||= applicationDisplayName(target)
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '请输入有效的信息'
    return
  }

  if (!name) {
    errorMessage.value = isWebsite.value ? '请输入网站名称' : '请输入软件名称'
    return
  }
  if ([...name].length > 40) {
    errorMessage.value = '名称不能超过 40 个字符'
    return
  }
  if (form.iconMode === 'custom' && !customIconDataUrl.value) {
    errorMessage.value = '请上传自定义图标'
    return
  }
  const category = form.category.trim() || DEFAULT_WEBSITE_CATEGORY
  if ([...category].length > 24) {
    errorMessage.value = '分类不能超过 24 个字符'
    return
  }

  emit('save', {
    id: props.shortcut?.id,
    mode: props.mode,
    name,
    target,
    category: isWebsite.value ? category : undefined,
    iconMode: form.iconMode,
    iconDataUrl:
      form.iconMode === 'custom'
        ? customIconDataUrl.value
        : isWebsite.value
          ? null
          : nativeIconDataUrl.value,
  })
}

function removeShortcut() {
  if (props.shortcut) confirmingRemove.value = true
}

function confirmRemoveShortcut() {
  if (!props.shortcut) return
  confirmingRemove.value = false
  emit('remove', props.shortcut.id)
}
</script>

<template>
  <UiDialog
    :title="dialogTitle"
    :description="dialogDescription"
    :initial-focus="`#shortcut-name-${mode}`"
    :suspended="confirmingRemove"
    height="auto"
    @close="emit('close')"
  >
    <form
      id="shortcut-dialog-form"
      class="shortcut-dialog__form"
      @submit.prevent="submit"
    >
      <div class="shortcut-dialog__body">
            <UiFormField label="图标" layout="dialog">
              <div class="shortcut-icon-editor">
                <ShortcutVisual
                  :sources="activeIconUrls"
                  :mode="mode"
                  :size="44"
                />
                <div class="shortcut-icon-editor__copy">
                  <p>
                    {{
                      isWebsite
                        ? '自动读取网站图标，或使用自定义图片'
                        : '自动读取应用图标，或使用自定义图片'
                    }}
                  </p>
                  <UiSegmentedControl
                    class="shortcut-icon-mode"
                    :model-value="form.iconMode"
                    :items="iconModeItems"
                    aria-label="图标来源"
                    @update:model-value="setIconModeFromControl"
                  />
                </div>
                <UiButton size="sm" @click="chooseCustomIcon">
                  <ImageUp :size="13" />
                  上传
                </UiButton>
                <input
                  ref="iconInput"
                  class="visually-hidden"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  @change="handleIconFile"
                />
              </div>
            </UiFormField>

            <UiFormField
              label="名称"
              :control-id="`shortcut-name-${mode}`"
              :hint="
                isWebsite
                  ? '留空时会根据网址自动生成'
                  : '选择应用后自动填写，也可以手动调整'
              "
              layout="dialog"
            >
              <UiInput
                :id="`shortcut-name-${mode}`"
                v-model="form.name"
                type="text"
                maxlength="40"
                size="lg"
                :placeholder="isWebsite ? '例如：GitHub' : '例如：Visual Studio Code'"
              />
            </UiFormField>

            <UiFormField
              v-if="isWebsite"
              label="网址"
              :control-id="`shortcut-target-${mode}`"
              :hint="targetHint"
              layout="dialog"
            >
              <UiInput
                :id="`shortcut-target-${mode}`"
                v-model="form.target"
                type="text"
                size="lg"
                placeholder="https://example.com"
                spellcheck="false"
              />
            </UiFormField>

            <UiFormField
              v-else
              label="应用"
              :hint="targetHint"
              layout="dialog"
            >
              <div class="shortcut-application-picker">
                <span class="shortcut-application-picker__copy">
                  <strong>
                    {{
                      form.name.trim() ||
                      (form.target
                        ? applicationDisplayName(form.target)
                        : '尚未选择应用')
                    }}
                  </strong>
                  <small>
                    {{
                      form.target ||
                      '选择 macOS 或 Windows 应用'
                    }}
                  </small>
                </span>
                <UiButton
                  size="lg"
                  :disabled="choosingApplication"
                  @click="chooseApplication"
                >
                  <FolderOpen :size="13" />
                  {{ choosingApplication ? '选择中…' : '选择应用' }}
                </UiButton>
              </div>
            </UiFormField>

            <UiFormField
              v-if="isWebsite"
              label="分类"
              :control-id="`shortcut-category-${mode}`"
              hint="用于网址页筛选；可以直接输入新分类"
              layout="dialog"
            >
              <UiCombobox
                :id="`shortcut-category-${mode}`"
                v-model="form.category"
                :options="categories"
                placeholder="例如：开发工具"
                aria-label="分类"
                placement="top"
              />
            </UiFormField>

            <p v-if="errorMessage" class="shortcut-dialog__error" role="alert">
              {{ errorMessage }}
            </p>
      </div>
    </form>

    <template #footer>
      <div class="shortcut-dialog__actions">
        <UiButton
          v-if="isEditing"
          variant="danger-ghost"
          size="lg"
          @click="removeShortcut"
        >
          <Trash2 :size="14" />
          {{ isWebsite ? '删除' : '删除记录' }}
        </UiButton>
        <span v-else />
        <div>
          <UiButton size="lg" @click="emit('close')">
            取消
          </UiButton>
          <UiButton
            variant="primary"
            size="lg"
            type="submit"
            form="shortcut-dialog-form"
          >
            保存
          </UiButton>
        </div>
      </div>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="confirmingRemove && shortcut"
    :title="removalTitle"
    :description="removalDescription"
    :confirm-label="isWebsite ? '删除' : '删除记录'"
    @close="confirmingRemove = false"
    @confirm="confirmRemoveShortcut"
  >
    <div class="shortcut-remove-target">
      <ShortcutVisual
        :sources="activeIconUrls"
        :mode="mode"
        :size="36"
      />
      <span>
        <strong>{{ shortcut.name }}</strong>
        <small>{{ removalTarget }}</small>
      </span>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.shortcut-dialog__form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 20px 18px;
}

.shortcut-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 2px 0 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.shortcut-icon-editor {
  display: grid;
  min-height: 44px;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.shortcut-icon-editor__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.shortcut-icon-editor__copy p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.shortcut-icon-mode {
  width: fit-content;
  margin-top: 7px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.shortcut-application-picker {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.shortcut-application-picker__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.shortcut-application-picker__copy strong,
.shortcut-application-picker__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-application-picker__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.shortcut-application-picker__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.shortcut-dialog__error {
  padding: 0;
  margin: -2px 0 0 calc(64px + var(--space-3));
  color: var(--coral);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.shortcut-dialog__actions {
  display: flex;
  width: 100%;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.shortcut-dialog__actions > div {
  display: flex;
  gap: var(--space-3);
}

.shortcut-remove-target {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
}

.shortcut-remove-target > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.shortcut-remove-target strong,
.shortcut-remove-target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-remove-target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.shortcut-remove-target small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .shortcut-dialog__form {
    padding: 0 16px 16px;
  }

  .shortcut-dialog__error {
    margin-left: 0;
  }

  .shortcut-application-picker {
    flex-direction: column;
    align-items: stretch;
  }

  .shortcut-application-picker .ui-button {
    width: 100%;
  }
}

</style>
