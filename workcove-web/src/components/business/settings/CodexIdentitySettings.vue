<script setup lang="ts">
import {
  Bot,
  ImageUp,
  RotateCcw,
} from '@lucide/vue'
import {
  computed,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiInput from '@/components/ui/UiInput.vue'
import { fileToSquareWebp } from '@/utils/image'

const props = defineProps<{
  name: string
  iconDataUrl: string | null
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:iconDataUrl': [value: string | null]
}>()

const iconInput = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const nameModel = computed({
  get: () => props.name,
  set: (value: string) => emit('update:name', value),
})

function chooseIcon() {
  errorMessage.value = ''
  iconInput.value?.click()
}

async function handleIconFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  errorMessage.value = ''
  try {
    const iconDataUrl = await fileToSquareWebp(file, {
      maxBytes: 2 * 1024 * 1024,
      dimension: 256,
      sizeLabel: '2MB',
    })
    emit('update:iconDataUrl', iconDataUrl)
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '图标处理失败，请重新选择'
  }
}

function resetIcon() {
  errorMessage.value = ''
  emit('update:iconDataUrl', null)
}
</script>

<template>
  <section
    class="codex-identity-settings"
    aria-labelledby="codex-identity-settings-title"
  >
    <header>
      <div>
        <h2 id="codex-identity-settings-title">Codex 助手</h2>
        <p>自定义 Codex 工作区中显示的名称与图标。</p>
      </div>
    </header>

    <div class="codex-identity-settings__row">
      <button
        class="codex-identity-settings__icon"
        type="button"
        aria-label="上传 Codex 助手图标"
        title="上传图标"
        @click="chooseIcon"
      >
        <img v-if="iconDataUrl" :src="iconDataUrl" alt="" />
        <Bot v-else :size="22" />
        <span aria-hidden="true">
          <ImageUp :size="12" />
        </span>
      </button>
      <input
        ref="iconInput"
        class="codex-identity-settings__input"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        @change="handleIconFile"
      />

      <label class="codex-identity-settings__name">
        <strong>显示名称</strong>
        <UiInput
          v-model="nameModel"
          maxlength="24"
          placeholder="Codex"
          aria-label="Codex 助手昵称"
        />
        <small>留空时使用默认名称 Codex。</small>
      </label>

      <div class="codex-identity-settings__actions">
        <UiButton size="sm" @click="chooseIcon">
          <ImageUp :size="13" />
          上传图标
        </UiButton>
        <UiButton
          v-if="iconDataUrl"
          size="sm"
          variant="ghost"
          @click="resetIcon"
        >
          <RotateCcw :size="13" />
          恢复默认
        </UiButton>
      </div>
    </div>

    <p
      v-if="errorMessage"
      class="codex-identity-settings__error"
      role="alert"
    >
      {{ errorMessage }}
    </p>
  </section>
</template>

<style scoped>
.codex-identity-settings {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.codex-identity-settings > header {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.codex-identity-settings h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.codex-identity-settings header p {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.codex-identity-settings__row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
}

.codex-identity-settings__icon {
  position: relative;
  display: grid;
  width: 46px;
  height: 46px;
  padding: 0;
  overflow: hidden;
  color: var(--accent);
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 18%, transparent);
  border-radius: var(--radius-control);
  place-items: center;
}

.codex-identity-settings__icon img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.codex-identity-settings__icon > span {
  position: absolute;
  right: 3px;
  bottom: 3px;
  display: grid;
  width: 16px;
  height: 16px;
  color: #ffffff;
  background: rgb(0 0 0 / 56%);
  border-radius: 5px;
  opacity: 0;
  place-items: center;
  transition: opacity var(--duration-fast) var(--ease-apple);
}

.codex-identity-settings__icon:hover > span,
.codex-identity-settings__icon:focus-visible > span {
  opacity: 1;
}

.codex-identity-settings__input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.codex-identity-settings__name {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.codex-identity-settings__name strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.codex-identity-settings__name small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-identity-settings__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.codex-identity-settings__error {
  padding: 0 var(--space-4) var(--space-3) 75px;
  margin: calc(-1 * var(--space-2)) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 620px) {
  .codex-identity-settings__row {
    grid-template-columns: 46px minmax(0, 1fr);
  }

  .codex-identity-settings__actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .codex-identity-settings__error {
    padding-left: var(--space-4);
  }
}
</style>
