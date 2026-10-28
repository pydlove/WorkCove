<script setup lang="ts">
import { Check } from '@lucide/vue'

withDefaults(
  defineProps<{
    modelValue: boolean
    label: string
    disabled?: boolean
  }>(),
  {
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <label class="ui-checkbox" :class="{ 'ui-checkbox--checked': modelValue }">
    <input
      type="checkbox"
      :checked="modelValue"
      :disabled="disabled"
      :aria-label="label"
      @change="
        emit(
          'update:modelValue',
          ($event.target as HTMLInputElement).checked,
        )
      "
    />
    <span class="ui-checkbox__box">
      <Check v-if="modelValue" :size="10" :stroke-width="2.6" />
    </span>
  </label>
</template>

<style scoped>
.ui-checkbox {
  display: grid;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  cursor: pointer;
  place-items: center;
}

.ui-checkbox input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.ui-checkbox__box {
  display: grid;
  width: 14px;
  height: 14px;
  color: #ffffff;
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-badge);
  box-shadow: inset 0 1px 1px rgb(0 0 0 / 5%);
  place-items: center;
  transition:
    background var(--duration-fast) var(--ease-apple),
    border-color var(--duration-fast) var(--ease-apple);
}

.ui-checkbox--checked .ui-checkbox__box {
  background: var(--system-blue);
  border-color: var(--system-blue);
}

.ui-checkbox input:focus-visible + .ui-checkbox__box {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.ui-checkbox input:disabled + .ui-checkbox__box {
  opacity: 0.4;
}
</style>
