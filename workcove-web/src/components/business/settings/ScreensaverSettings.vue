<script setup lang="ts">
import {
  Check,
  ImagePlus,
  Trash2,
  Upload,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import { useScreensaverStore } from '@/stores/useScreensaverStore'
import { SCREENSAVER_PRESETS } from '@/types/screensaver'
import { fileToScreensaverWebp } from '@/utils/image'

const emit = defineEmits<{
  notify: [message: string]
}>()

const screensaver = useScreensaverStore()
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const errorMessage = ref('')

const customPreviewStyle = computed(() =>
  screensaver.customImageDataUrl
    ? {
        backgroundImage: `url("${screensaver.customImageDataUrl}")`,
      }
    : undefined,
)

function chooseUpload() {
  errorMessage.value = ''
  fileInput.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploading.value) return
  uploading.value = true
  errorMessage.value = ''
  try {
    const dataUrl = await fileToScreensaverWebp(file)
    screensaver.saveCustom(dataUrl, file.name)
    emit('notify', `已使用「${file.name}」作为休息屏保`)
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '屏保图片上传失败'
  } finally {
    uploading.value = false
  }
}

function removeCustom() {
  screensaver.removeCustom()
  errorMessage.value = ''
  emit('notify', '已移除自定义屏保')
}
</script>

<template>
  <section class="settings-group screensaver-settings">
    <header>
      <h2>屏保</h2>
    </header>
    <div class="screensaver-settings__intro">
      <div>
        <strong>休息画面</strong>
        <span>点击缩略图选择屏保，上传的图片会按当前账号保存。</span>
      </div>
      <div class="screensaver-settings__actions">
        <UiButton
          size="sm"
          :disabled="uploading"
          @click="chooseUpload"
        >
          <UiFidgetSpinner v-if="uploading" :size="13" />
          <Upload v-else :size="14" />
          {{ uploading ? '正在处理' : '上传屏保' }}
        </UiButton>
        <UiButton
          v-if="screensaver.customImageDataUrl"
          size="sm"
          variant="ghost"
          @click="removeCustom"
        >
          <Trash2 :size="14" />
          移除自定义
        </UiButton>
      </div>
    </div>

    <div
      class="screensaver-settings__grid"
      role="radiogroup"
      aria-label="选择屏保"
    >
      <button
        v-for="preset in SCREENSAVER_PRESETS"
        :key="preset.id"
        type="button"
        role="radio"
        :aria-checked="screensaver.selectedId === preset.id"
        :class="{
          'screensaver-option--selected':
            screensaver.selectedId === preset.id,
        }"
        class="screensaver-option"
        @click="screensaver.select(preset.id)"
      >
        <span
          class="screensaver-option__preview"
          :class="`screensaver-option__preview--${preset.id}`"
        >
          <span class="screensaver-option__clock">00:18</span>
          <span class="screensaver-option__hint">休息中</span>
          <Check
            v-if="screensaver.selectedId === preset.id"
            class="screensaver-option__check"
            :size="13"
          />
        </span>
        <span class="screensaver-option__copy">
          <strong>{{ preset.name }}</strong>
          <small>{{ preset.description }}</small>
        </span>
      </button>

      <button
        type="button"
        role="radio"
        :aria-checked="screensaver.selectedId === 'custom'"
        :class="{
          'screensaver-option--selected':
            screensaver.selectedId === 'custom',
          'screensaver-option--empty':
            !screensaver.customImageDataUrl,
        }"
        class="screensaver-option screensaver-option--custom"
        @click="
          screensaver.customImageDataUrl
            ? screensaver.select('custom')
            : chooseUpload()
        "
      >
        <span
          class="screensaver-option__preview screensaver-option__preview--custom"
          :style="customPreviewStyle"
        >
          <template v-if="screensaver.customImageDataUrl">
            <span class="screensaver-option__custom-shade" />
            <span class="screensaver-option__clock">00:18</span>
            <span class="screensaver-option__hint">休息中</span>
            <Check
              v-if="screensaver.selectedId === 'custom'"
              class="screensaver-option__check"
              :size="13"
            />
          </template>
          <template v-else>
            <ImagePlus :size="22" />
            <span>上传图片</span>
          </template>
        </span>
        <span class="screensaver-option__copy">
          <strong>自定义</strong>
          <small :title="screensaver.customImageName">
            {{
              screensaver.customImageName ||
              'JPG、PNG 或 WebP，最大 12MB'
            }}
          </small>
        </span>
      </button>
    </div>

    <p
      v-if="errorMessage"
      class="screensaver-settings__error"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <input
      ref="fileInput"
      class="screensaver-settings__file"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="handleUpload"
    />
  </section>
</template>

<style scoped>
.screensaver-settings {
  overflow: hidden;
}

.screensaver-settings__intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.screensaver-settings__intro > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-1);
}

.screensaver-settings__intro strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.screensaver-settings__intro span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.screensaver-settings__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.screensaver-settings__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  padding: var(--space-4);
}

.screensaver-option {
  display: flex;
  min-width: 0;
  padding: 0;
  flex-direction: column;
  text-align: left;
  background: transparent;
  border-radius: var(--radius-panel);
}

.screensaver-option__preview {
  position: relative;
  display: flex;
  width: 100%;
  aspect-ratio: 16 / 10;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-1);
  overflow: hidden;
  color: var(--text-subtle);
  background: var(--surface-inset);
  border: 2px solid transparent;
  border-radius: var(--radius-panel);
  background-position: center;
  background-size: cover;
}

.screensaver-option:hover .screensaver-option__preview,
.screensaver-option:focus-visible .screensaver-option__preview {
  border-color: var(--line);
}

.screensaver-option--selected .screensaver-option__preview {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.screensaver-option__preview--deep-forest {
  color: var(--screensaver-deep-forest-text);
  background: var(--screensaver-deep-forest-bg);
}

.screensaver-option__preview--graphite {
  color: var(--screensaver-graphite-text);
  background: var(--screensaver-graphite-bg);
}

.screensaver-option__preview--morning-mist {
  color: var(--screensaver-morning-mist-text);
  background: var(--screensaver-morning-mist-bg);
}

.screensaver-option__preview--deep-ocean {
  color: var(--screensaver-deep-ocean-text);
  background: var(--screensaver-deep-ocean-bg);
}

.screensaver-option__preview--custom {
  background-color: var(--canvas-inverse);
}

.screensaver-option--empty .screensaver-option__preview {
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border: 1px dashed var(--line);
}

.screensaver-option__custom-shade {
  position: absolute;
  inset: 0;
  background: var(--screensaver-image-scrim);
}

.screensaver-option__clock,
.screensaver-option__hint,
.screensaver-option__check {
  position: relative;
  z-index: 1;
}

.screensaver-option__clock {
  font-family: var(--font-mono);
  font-size: var(--font-metric);
  font-variant-numeric: tabular-nums;
}

.screensaver-option__hint {
  font-size: var(--font-caption);
  opacity: 0.72;
}

.screensaver-option__check {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  width: var(--control-md);
  height: var(--control-md);
  color: var(--accent-contrast);
  background: var(--accent);
  border-radius: 50%;
  place-items: center;
}

.screensaver-option__copy {
  display: flex;
  min-width: 0;
  padding: var(--space-2) var(--space-1) 0;
  flex-direction: column;
  gap: 2px;
}

.screensaver-option__copy strong,
.screensaver-option__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.screensaver-option__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.screensaver-option__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.screensaver-settings__error {
  margin: 0;
  padding: 0 var(--space-4) var(--space-3);
  color: var(--system-red);
  font-size: var(--font-caption);
}

.screensaver-settings__file {
  display: none;
}

@media (max-width: 760px) {
  .screensaver-settings__intro {
    align-items: stretch;
    flex-direction: column;
  }

  .screensaver-settings__actions {
    flex-wrap: wrap;
  }

  .screensaver-settings__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: var(--space-3);
  }
}
</style>
