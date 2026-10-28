<script setup lang="ts">
import { computed } from 'vue'

type SegmentSize = 'sm' | 'regular' | 'lg'

interface SegmentItem {
  value: string
  label: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    items: SegmentItem[]
    size?: SegmentSize
    disabled?: boolean
    ariaLabel?: string
  }>(),
  {
    size: 'regular',
    disabled: false,
    ariaLabel: '选项',
  },
)

const model = defineModel<string>({ required: true })
const activeIndex = computed(() => {
  const index = props.items.findIndex((item) => item.value === model.value)
  return index < 0 ? 0 : index
})
const thumbStyle = computed(() => ({
  width: `${100 / props.items.length}%`,
  transform: `translateX(${activeIndex.value * 100}%)`,
}))
</script>

<template>
  <div
    class="ui-segmented"
    :class="`ui-segmented--${size}`"
    role="group"
    :aria-label="ariaLabel"
  >
    <span class="ui-segmented__thumb-layer" aria-hidden="true">
      <span class="ui-segmented__thumb" :style="thumbStyle" />
    </span>
    <button
      v-for="item in items"
      :key="item.value"
      class="ui-segmented__item"
      type="button"
      :disabled="disabled || item.disabled"
      :aria-pressed="item.value === model"
      @click="model = item.value"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.ui-segmented {
  position: relative;
  display: inline-grid;
  height: var(--control-md);
  grid-auto-columns: 1fr;
  grid-auto-flow: column;
  padding: 2px;
  background: var(--segmented-track);
  border-radius: 7px;
}

.ui-segmented--sm {
  height: var(--control-sm);
}

.ui-segmented--lg {
  height: var(--control-lg);
}

.ui-segmented__thumb-layer {
  position: absolute;
  pointer-events: none;
  inset: 2px;
}

.ui-segmented__thumb {
  display: block;
  height: 100%;
  background: var(--control-bg);
  border-radius: 5px;
  box-shadow: var(--control-shadow);
  transition: transform var(--duration-popover) var(--ease-apple);
}

.ui-segmented__item {
  position: relative;
  z-index: 1;
  height: 100%;
  padding: 0 9px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-weight: var(--weight-regular);
  white-space: nowrap;
  background: transparent;
  border-radius: 5px;
  transition: color var(--duration-fast) var(--ease-apple);
}

.ui-segmented--regular .ui-segmented__item {
  font-size: var(--font-body);
}

.ui-segmented__item:hover {
  color: var(--text);
}

.ui-segmented__item[aria-pressed="true"] {
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.ui-segmented__item:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: -1px;
}

.ui-segmented__item:disabled {
  opacity: 0.4;
}
</style>
