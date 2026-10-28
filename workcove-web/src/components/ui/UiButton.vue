<script setup lang="ts">
import { computed } from 'vue'

type ButtonVariant =
  | 'default'
  | 'primary'
  | 'danger'
  | 'ghost'
  | 'danger-ghost'

type ButtonSize = 'sm' | 'regular' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariant
    size?: ButtonSize
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    block?: boolean
  }>(),
  {
    variant: 'default',
    size: 'regular',
    type: 'button',
    disabled: false,
    block: false,
  },
)

const classes = computed(() => [
  `ui-button--${props.variant}`,
  `ui-button--${props.size}`,
  { 'ui-button--block': props.block },
])
</script>

<template>
  <button
    class="ui-button"
    :class="classes"
    :type="type"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.ui-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  line-height: 1;
  white-space: nowrap;
  border: 0;
  border-radius: var(--radius-control);
  transition:
    background var(--duration-fast) var(--ease-apple),
    color var(--duration-fast) var(--ease-apple),
    filter var(--duration-fast) var(--ease-apple),
    transform 80ms var(--ease-apple);
}

.ui-button:active:not(:disabled) {
  transform: scale(0.98);
}

.ui-button:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.ui-button:disabled {
  pointer-events: none;
  opacity: 0.4;
}

.ui-button--sm {
  height: var(--control-sm);
  padding: 0 8px;
  font-size: var(--font-caption);
}

.ui-button--regular {
  height: var(--control-md);
}

.ui-button--lg {
  height: var(--control-lg);
  padding: 0 14px;
}

.ui-button--default {
  background: var(--control-bg);
  box-shadow: var(--control-shadow);
}

.ui-button--default:hover {
  filter: brightness(0.98);
}

.ui-button--primary {
  color: #ffffff;
  background: var(--system-blue);
  box-shadow: inset 0 0.5px 0 rgb(255 255 255 / 25%);
}

.ui-button--primary:hover {
  background: var(--system-blue-hover);
}

.ui-button--danger {
  color: #ffffff;
  background: var(--system-red);
  box-shadow: inset 0 0.5px 0 rgb(255 255 255 / 22%);
}

.ui-button--danger:hover {
  filter: brightness(1.06);
}

.ui-button--ghost,
.ui-button--danger-ghost {
  background: transparent;
}

.ui-button--ghost:hover {
  background: var(--hover-bg);
}

.ui-button--danger-ghost {
  color: var(--system-red);
}

.ui-button--danger-ghost:hover {
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.ui-button--block {
  width: 100%;
}
</style>
