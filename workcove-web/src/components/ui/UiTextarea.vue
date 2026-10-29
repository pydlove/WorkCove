<script setup lang="ts">
import { computed, ref, useAttrs } from 'vue'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(
  defineProps<{
    invalid?: boolean
    disabled?: boolean
    readonly?: boolean
    rows?: number
  }>(),
  {
    invalid: false,
    disabled: false,
    readonly: false,
    rows: 4,
  },
)

const model = defineModel<string>({ default: '' })
const attrs = useAttrs()
const textarea = ref<HTMLTextAreaElement | null>(null)
const classes = computed(() => ({
  'ui-textarea--invalid': props.invalid,
}))

function focus() {
  textarea.value?.focus()
}

function blur() {
  textarea.value?.blur()
}

defineExpose({ focus, blur })
</script>

<template>
  <textarea
    ref="textarea"
    v-model="model"
    v-bind="attrs"
    class="ui-textarea"
    :class="classes"
    :rows="rows"
    :disabled="disabled"
    :readonly="readonly"
    :aria-invalid="invalid || undefined"
  />
</template>

<style scoped>
.ui-textarea {
  width: 100%;
  min-height: 88px;
  padding: 8px;
  resize: vertical;
  color: var(--text);
  font: inherit;
  font-size: var(--font-body);
  line-height: var(--line-body);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 4%);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.ui-textarea:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
  outline: none;
}

.ui-textarea--invalid {
  border-color: var(--system-red);
}

.ui-textarea:disabled {
  pointer-events: none;
  opacity: 0.4;
}
</style>
