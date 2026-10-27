<script setup lang="ts">
import {
  AppWindow,
  Check,
  FolderOpen,
  Globe2,
  ImageUp,
  Trash2,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'

import {
  applicationDisplayName,
  normalizeWebsiteUrl,
  websiteFaviconUrls,
  websiteHostname,
} from '@/services/localShortcuts'
import ShortcutVisual from '@/components/business/workbench/ShortcutVisual.vue'
import type {
  ApplicationShortcut,
  ShortcutIconMode,
  ShortcutInput,
  ShortcutMode,
  WebsiteShortcut,
} from '@/types/shortcut'
import { fileToSquareWebp } from '@/utils/image'

const props = defineProps<{
  mode: ShortcutMode
  shortcut?: WebsiteShortcut | ApplicationShortcut | null
}>()

const emit = defineEmits<{
  close: []
  save: [input: ShortcutInput]
  remove: [id: string]
}>()

const isWebsite = computed(() => props.mode === 'website')
const isEditing = computed(() => Boolean(props.shortcut))
const hasDesktopBridge = Boolean(window.workCoveDesktop)
const iconInput = ref<HTMLInputElement | null>(null)
const nameInput = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const choosingApplication = ref(false)
const customIconDataUrl = ref<string | null>(null)
const nativeIconDataUrl = ref<string | null>(null)
const form = reactive({
  name: '',
  target: '',
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

function syncForm() {
  form.name = props.shortcut?.name ?? ''
  form.target =
    props.shortcut && 'url' in props.shortcut
      ? props.shortcut.url
      : props.shortcut?.path ?? ''
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

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  void nextTick(() => nameInput.value?.focus())
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

function setIconMode(mode: ShortcutIconMode) {
  form.iconMode = mode
  if (mode === 'custom' && !customIconDataUrl.value) {
    iconInput.value?.click()
  }
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
  if (!window.workCoveDesktop) return

  choosingApplication.value = true
  errorMessage.value = ''
  try {
    const application = await window.workCoveDesktop.chooseApplication()
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
    errorMessage.value = '请选择或填写本地软件路径'
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

  emit('save', {
    id: props.shortcut?.id,
    mode: props.mode,
    name,
    target,
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
  if (props.shortcut) emit('remove', props.shortcut.id)
}
</script>

<template>
  <Teleport to="body">
    <div class="shortcut-dialog-layer">
      <button
        class="shortcut-dialog-backdrop"
        type="button"
        aria-label="关闭"
        @click="emit('close')"
      />
      <section
        class="shortcut-dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`shortcut-dialog-title-${mode}`"
      >
        <header class="shortcut-dialog__header">
          <span class="shortcut-dialog__kind">
            <Globe2 v-if="isWebsite" :size="17" />
            <AppWindow v-else :size="17" />
          </span>
          <div>
            <p>{{ isWebsite ? 'WEBSITE' : 'APPLICATION' }}</p>
            <h2 :id="`shortcut-dialog-title-${mode}`">
              {{
                isEditing
                  ? isWebsite
                    ? '编辑常用网址'
                    : '编辑本地软件'
                  : isWebsite
                    ? '添加常用网址'
                    : '添加本地软件'
              }}
            </h2>
          </div>
          <button
            class="shortcut-dialog__close"
            type="button"
            aria-label="关闭"
            @click="emit('close')"
          >
            <X :size="16" />
          </button>
        </header>

        <form class="shortcut-dialog__form" @submit.prevent="submit">
          <div class="shortcut-icon-editor">
            <ShortcutVisual
              :sources="activeIconUrls"
              :mode="mode"
              :name="form.name || (isWebsite ? 'W' : '应用')"
              :size="54"
            />
            <div class="shortcut-icon-editor__copy">
              <strong>图标</strong>
              <span>
                {{
                  isWebsite
                    ? '自动读取网站图标，也可以上传自定义图片'
                    : '桌面版自动读取应用图标，也可以上传自定义图片'
                }}
              </span>
              <div class="icon-source-options">
                <button
                  type="button"
                  :class="{ active: form.iconMode === 'auto' }"
                  @click="setIconMode('auto')"
                >
                  自动获取
                </button>
                <button
                  type="button"
                  :class="{ active: form.iconMode === 'custom' }"
                  @click="setIconMode('custom')"
                >
                  自定义图标
                </button>
              </div>
            </div>
            <button
              class="icon-upload-button"
              type="button"
              aria-label="上传图标"
              @click="chooseCustomIcon"
            >
              <ImageUp :size="15" />
            </button>
            <input
              ref="iconInput"
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="handleIconFile"
            />
          </div>

          <label class="shortcut-field">
            <span>名称</span>
            <input
              ref="nameInput"
              v-model="form.name"
              type="text"
              maxlength="40"
              :placeholder="isWebsite ? '例如：GitHub' : '例如：Visual Studio Code'"
            />
          </label>

          <label class="shortcut-field">
            <span>{{ isWebsite ? '网址' : '应用路径' }}</span>
            <span class="shortcut-field__row">
              <input
                v-model="form.target"
                type="text"
                :placeholder="
                  isWebsite
                    ? 'https://example.com'
                    : hasDesktopBridge
                      ? '选择本地应用'
                      : '/Applications/Example.app'
                "
                spellcheck="false"
              />
              <button
                v-if="!isWebsite && hasDesktopBridge"
                class="choose-application"
                type="button"
                :disabled="choosingApplication"
                @click="chooseApplication"
              >
                <FolderOpen :size="14" />
                {{ choosingApplication ? '选择中…' : '选择应用' }}
              </button>
            </span>
          </label>

          <p class="shortcut-dialog__hint">
            {{
              isWebsite
                ? '点击后会在默认浏览器中打开。'
                : hasDesktopBridge
                  ? '点击后会在本机启动所选应用。'
                  : '浏览器预览无法启动本地软件，请在 WorkCove 桌面应用中使用。'
            }}
          </p>

          <p v-if="errorMessage" class="shortcut-dialog__error" role="alert">
            {{ errorMessage }}
          </p>

          <footer class="shortcut-dialog__actions">
            <button
              v-if="isEditing"
              class="shortcut-dialog__delete"
              type="button"
              @click="removeShortcut"
            >
              <Trash2 :size="14" />
              删除
            </button>
            <span v-else />
            <div>
              <button
                class="shortcut-dialog__cancel"
                type="button"
                @click="emit('close')"
              >
                取消
              </button>
              <button class="shortcut-dialog__save" type="submit">
                <Check :size="15" />
                保存
              </button>
            </div>
          </footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.shortcut-dialog-layer {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: grid;
  padding: 24px;
  place-items: center;
}

.shortcut-dialog-backdrop {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  background: rgb(13 17 23 / 28%);
  backdrop-filter: blur(4px);
}

.shortcut-dialog {
  position: relative;
  width: min(100%, 470px);
  overflow: hidden;
  color: var(--text);
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow:
    0 28px 80px rgb(13 17 23 / 24%),
    0 3px 12px rgb(13 17 23 / 12%);
  animation: shortcut-dialog-in 190ms cubic-bezier(0.32, 0.72, 0, 1) both;
}

.shortcut-dialog__header {
  display: grid;
  min-height: 76px;
  grid-template-columns: 38px minmax(0, 1fr) 30px;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  background: #f6f8fa;
  border-bottom: 1px solid var(--line-soft);
}

.shortcut-dialog__kind {
  display: grid;
  width: 36px;
  height: 36px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 8px;
  place-items: center;
}

.shortcut-dialog__header p {
  margin: 0 0 4px;
  color: var(--success);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.shortcut-dialog__header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 620;
}

.shortcut-dialog__close {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 6px;
  place-items: center;
}

.shortcut-dialog__close:hover {
  color: var(--text);
  background: rgb(31 35 40 / 6%);
}

.shortcut-dialog__form {
  display: flex;
  padding: 20px 20px 18px;
  flex-direction: column;
  gap: 17px;
}

.shortcut-icon-editor {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) 30px;
  align-items: center;
  gap: 12px;
}

.shortcut-icon-editor__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.shortcut-icon-editor__copy strong {
  font-size: 10px;
  font-weight: 620;
}

.shortcut-icon-editor__copy > span {
  margin-top: 3px;
  color: var(--text-subtle);
  font-size: 8px;
  line-height: 1.45;
}

.icon-source-options {
  display: flex;
  gap: 3px;
  margin-top: 7px;
}

.icon-source-options button {
  height: 22px;
  padding: 0 7px;
  color: var(--text-subtle);
  font-size: 8px;
  background: #f6f8fa;
  border-radius: 4px;
}

.icon-source-options button:hover {
  color: var(--text);
}

.icon-source-options button.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.icon-upload-button {
  display: grid;
  width: 29px;
  height: 29px;
  padding: 0;
  color: var(--text-muted);
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 6px;
  place-items: center;
}

.icon-upload-button:hover {
  color: var(--accent);
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

.shortcut-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.shortcut-field > span:first-child {
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 600;
}

.shortcut-field input {
  width: 100%;
  height: 39px;
  padding: 0 11px;
  color: var(--text);
  font-size: 11px;
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 7px;
}

.shortcut-field input:focus {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.shortcut-field input:focus-visible {
  outline: none;
}

.shortcut-field__row {
  display: flex;
  gap: 7px;
}

.choose-application {
  display: inline-flex;
  height: 39px;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  color: var(--text-muted);
  font-size: 9px;
  background: #f6f8fa;
  border: 1px solid var(--line);
  border-radius: 7px;
}

.choose-application:hover:not(:disabled) {
  color: var(--accent);
}

.shortcut-dialog__hint {
  margin: -5px 0 0;
  color: var(--text-subtle);
  font-size: 8px;
  line-height: 1.5;
}

.shortcut-dialog__error {
  padding: 9px 10px;
  margin: -4px 0 0;
  color: var(--coral);
  font-size: 9px;
  background: rgb(207 34 46 / 6%);
  border-radius: 6px;
}

.shortcut-dialog__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 15px;
  margin-top: 2px;
  border-top: 1px solid var(--line-soft);
}

.shortcut-dialog__actions > div {
  display: flex;
  gap: 7px;
}

.shortcut-dialog__actions button {
  height: 32px;
  padding: 0 10px;
  font-size: 9px;
  font-weight: 600;
  border-radius: 6px;
}

.shortcut-dialog__delete {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--coral);
  background: transparent;
}

.shortcut-dialog__delete:hover {
  background: rgb(207 34 46 / 6%);
}

.shortcut-dialog__cancel {
  color: var(--text-muted);
  background: transparent;
  border: 1px solid var(--line);
}

.shortcut-dialog__save {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #ffffff;
  background: var(--accent);
}

.shortcut-dialog__save:hover {
  background: var(--accent-strong);
}

html[data-theme="dark"] {
  .shortcut-dialog,
  .shortcut-field input {
    background: #161b22;
  }

  .shortcut-dialog__header,
  .icon-source-options button,
  .choose-application {
    background: #0d1117;
  }

  .icon-upload-button {
    background: #21262d;
  }

  .shortcut-dialog__close:hover {
    background: rgb(240 246 252 / 6%);
  }
}

@media (max-width: 560px) {
  .shortcut-dialog-layer {
    padding: 12px;
  }

  .shortcut-dialog__form {
    padding: 18px 16px 16px;
  }

  .shortcut-field__row {
    flex-direction: column;
  }

  .choose-application {
    width: 100%;
    justify-content: center;
  }
}

@keyframes shortcut-dialog-in {
  from {
    opacity: 0;
    transform: translateY(6px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
