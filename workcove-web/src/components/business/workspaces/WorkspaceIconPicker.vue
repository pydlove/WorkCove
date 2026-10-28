<script setup lang="ts">
import { ImagePlus, Trash2, Upload } from '@lucide/vue'
import { ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import WorkspaceIcon from '@/components/business/workspaces/WorkspaceIcon.vue'
import {
  DEFAULT_WORKSPACE_ICON,
  DEFAULT_WORKSPACE_ICON_TONE,
  MAX_WORKSPACE_ICON_DATA_URL_LENGTH,
  type WorkspaceIcon as WorkspaceIconName,
  type WorkspaceIconTone,
} from '@/types/workspace'

const props = withDefaults(
  defineProps<{
    fallbackIcon?: WorkspaceIconName
    fallbackTone?: WorkspaceIconTone
  }>(),
  {
    fallbackIcon: DEFAULT_WORKSPACE_ICON,
    fallbackTone: DEFAULT_WORKSPACE_ICON_TONE,
  },
)

const image = defineModel<string>('image', { required: true })
const processing = defineModel<boolean>('processing', { default: false })
const fileInput = ref<HTMLInputElement | null>(null)
const dragging = ref(false)
const errorMessage = ref('')

function chooseImage() {
  if (processing.value) return
  fileInput.value?.click()
}

async function handleFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) await importImage(file)
}

async function handleDrop(event: DragEvent) {
  dragging.value = false
  if (processing.value) return
  const file = event.dataTransfer?.files?.[0]
  if (file) await importImage(file)
}

async function importImage(file: File) {
  errorMessage.value = ''
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    errorMessage.value = '请选择 PNG、JPEG 或 WebP 图片'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    errorMessage.value = '图片不能超过 10 MB'
    return
  }

  processing.value = true
  try {
    const source = await readImage(file)
    image.value = renderSquareImage(source)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法读取这张图片'
  } finally {
    processing.value = false
  }
}

function removeImage() {
  image.value = ''
  errorMessage.value = ''
}

function readImage(file: File) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('无法读取这张图片'))
    reader.onload = () => {
      const source = new Image()
      source.onerror = () => reject(new Error('图片格式无法解析'))
      source.onload = () => resolve(source)
      source.src = String(reader.result ?? '')
    }
    reader.readAsDataURL(file)
  })
}

function renderSquareImage(source: HTMLImageElement) {
  const size = 192
  const sourceSize = Math.min(source.naturalWidth, source.naturalHeight)
  const sourceX = (source.naturalWidth - sourceSize) / 2
  const sourceY = (source.naturalHeight - sourceSize) / 2
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  if (!context) throw new Error('当前环境无法处理图片')
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(
    source,
    sourceX,
    sourceY,
    sourceSize,
    sourceSize,
    0,
    0,
    size,
    size,
  )

  for (const quality of [0.9, 0.8, 0.7, 0.6]) {
    const result = canvas.toDataURL('image/webp', quality)
    if (result.length <= MAX_WORKSPACE_ICON_DATA_URL_LENGTH) return result
  }
  throw new Error('图片处理后仍然过大，请换一张更简单的图片')
}

function handleDropzoneKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  chooseImage()
}
</script>

<template>
  <div class="workspace-icon-picker">
    <input
      ref="fileInput"
      class="workspace-icon-picker__file"
      type="file"
      accept="image/png,image/jpeg,image/webp"
      tabindex="-1"
      hidden
      aria-hidden="true"
      @change="handleFileInput"
    />

    <div
      class="workspace-icon-picker__dropzone"
      :class="{
        'workspace-icon-picker__dropzone--dragging': dragging,
        'workspace-icon-picker__dropzone--ready': image,
      }"
      role="button"
      tabindex="0"
      :aria-label="image ? '更换工作空间图片' : '上传工作空间图片'"
      @click="chooseImage"
      @keydown="handleDropzoneKeydown"
      @dragenter.prevent="dragging = true"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="handleDrop"
    >
      <WorkspaceIcon
        :icon="props.fallbackIcon"
        :tone="props.fallbackTone"
        :image-url="image"
        :size="24"
      />
      <span class="workspace-icon-picker__copy">
        <strong>
          {{
            processing
              ? '正在处理图片…'
              : image
                ? '已上传自定义图片'
                : '上传工作空间图片'
          }}
        </strong>
        <small>
          {{
            image
              ? '点击可更换，图片会居中裁切为方形'
              : '支持 PNG、JPEG、WebP，也可以拖放到这里'
          }}
        </small>
      </span>
      <Upload :size="16" aria-hidden="true" />
    </div>

    <div class="workspace-icon-picker__actions">
      <UiButton :disabled="processing" @click="chooseImage">
        <ImagePlus :size="14" />
        {{ image ? '更换图片' : '选择图片' }}
      </UiButton>
      <UiButton
        v-if="image"
        variant="danger-ghost"
        :disabled="processing"
        @click="removeImage"
      >
        <Trash2 :size="14" />
        移除图片
      </UiButton>
    </div>

    <p
      v-if="errorMessage"
      class="workspace-icon-picker__error"
      role="alert"
    >
      {{ errorMessage }}
    </p>
  </div>
</template>

<style scoped>
.workspace-icon-picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.workspace-icon-picker__file {
  display: none;
}

.workspace-icon-picker__dropzone {
  display: grid;
  min-height: 68px;
  grid-template-columns: 44px minmax(0, 1fr) 16px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2);
  color: var(--text-muted);
  cursor: pointer;
  background: var(--surface-secondary);
  border: 1px dashed var(--control-border);
  border-radius: var(--radius-panel);
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple),
    border-color var(--duration-fast) var(--ease-apple);
}

.workspace-icon-picker__dropzone:hover,
.workspace-icon-picker__dropzone--dragging {
  color: var(--text);
  background: var(--hover-bg);
  border-color: var(--system-blue);
}

.workspace-icon-picker__dropzone:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.workspace-icon-picker__dropzone :deep(.workspace-icon) {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-panel);
}

.workspace-icon-picker__dropzone > svg {
  color: var(--icon);
}

.workspace-icon-picker__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.workspace-icon-picker__copy strong,
.workspace-icon-picker__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-icon-picker__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.workspace-icon-picker__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.workspace-icon-picker__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.workspace-icon-picker__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}
</style>
