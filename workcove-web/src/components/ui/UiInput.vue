<script setup lang="ts">
import { computed, ref, useAttrs } from 'vue'

defineOptions({
  inheritAttrs: false,
})

type InputSize = 'sm' | 'regular' | 'lg'

const props = withDefaults(
  defineProps<{
    size?: InputSize
    invalid?: boolean
    disabled?: boolean
    readonly?: boolean
    type?: string
  }>(),
  {
    size: 'regular',
    invalid: false,
    disabled: false,
    readonly: false,
    type: 'text',
  },
)

const model = defineModel<string>({ default: '' })
const attrs = useAttrs()
const input = ref<HTMLInputElement | null>(null)
const classes = computed(() => [
  `ui-input--${props.size}`,
  { 'ui-input--invalid': props.invalid },
])

function focus() {
  input.value?.focus()
}

function blur() {
  input.value?.blur()
}

defineExpose({ focus, blur })
</script>

<template>
  <input
    ref="input"
    v-model="model"
    v-bind="attrs"
    class="ui-input"
    :class="classes"
    :type="type"
    :disabled="disabled"
    :readonly="readonly"
    :aria-invalid="invalid || undefined"
  />
</template>

<style scoped>
.ui-input {
  width: 100%;
  height: var(--control-md);
  padding: 0 8px;
  color: var(--text);
  font-size: var(--font-body);
  line-height: 1;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 4%);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.ui-input--sm {
  height: var(--control-sm);
  font-size: var(--font-caption);
}

.ui-input--lg {
  height: var(--control-lg);
}

.ui-input:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.ui-input:focus-visible {
  outline: none;
}

.ui-input--invalid {
  border-color: var(--system-red);
}

.ui-input:disabled {
  pointer-events: none;
  opacity: 0.4;
}
</style>
