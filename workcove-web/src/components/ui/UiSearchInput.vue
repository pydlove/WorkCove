<script setup lang="ts">
import { Search, X } from '@lucide/vue'
import { ref } from 'vue'

withDefaults(
  defineProps<{
    label: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
  }>(),
  {
    placeholder: '搜索',
    clearable: true,
    disabled: false,
  },
)

const model = defineModel<string>({ default: '' })
const emit = defineEmits<{
  clear: []
}>()
const input = ref<HTMLInputElement | null>(null)

function clear() {
  if (!model.value) return
  model.value = ''
  emit('clear')
  input.value?.focus()
}

function focus() {
  input.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div
    class="ui-search-input"
    :class="{ 'ui-search-input--disabled': disabled }"
  >
    <Search class="ui-search-input__icon" :size="14" aria-hidden="true" />
    <input
      ref="input"
      v-model="model"
      type="search"
      :aria-label="label"
      :placeholder="placeholder"
      :disabled="disabled"
      autocomplete="off"
      spellcheck="false"
    />
    <button
      v-if="clearable && model"
      class="ui-search-input__clear"
      type="button"
      aria-label="清除搜索"
      title="清除搜索"
      :disabled="disabled"
      @click="clear"
    >
      <X :size="12" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.ui-search-input {
  display: flex;
  width: min(var(--search-field-width), 100%);
  height: var(--control-md);
  min-width: 0;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  color: var(--text-subtle);
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
  box-shadow: var(--control-inset-shadow);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.ui-search-input:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.ui-search-input__icon {
  flex: 0 0 auto;
  color: currentColor;
}

.ui-search-input input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.ui-search-input input::-webkit-search-cancel-button {
  display: none;
}

.ui-search-input input:focus-visible {
  outline: none;
}

.ui-search-input__clear {
  display: grid;
  width: var(--control-sm);
  height: var(--control-sm);
  flex: 0 0 auto;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-round);
  place-items: center;
}

.ui-search-input__clear:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.ui-search-input--disabled {
  pointer-events: none;
  opacity: 0.5;
}

@media (max-width: 760px) {
  .ui-search-input {
    width: 100%;
  }
}
</style>
