<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    size?: number
  }>(),
  {
    size: 14,
  },
)

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}))
</script>

<template>
  <span
    class="ui-fidget-spinner"
    :style="style"
    aria-hidden="true"
  >
    <span class="ui-fidget-spinner__rotor">
      <span class="ui-fidget-spinner__lobe" />
      <span class="ui-fidget-spinner__lobe" />
      <span class="ui-fidget-spinner__lobe" />
      <span class="ui-fidget-spinner__hub" />
    </span>
  </span>
</template>

<style scoped>
.ui-fidget-spinner {
  position: relative;
  display: inline-grid;
  flex: 0 0 auto;
  color: currentColor;
  place-items: center;
}

.ui-fidget-spinner__rotor {
  position: absolute;
  inset: 0;
  animation: ui-fidget-spin var(--fidget-spinner-duration)
    var(--fidget-spinner-easing) infinite;
  transform-origin: center;
  will-change: transform;
}

.ui-fidget-spinner__lobe {
  position: absolute;
  inset: 0;
}

.ui-fidget-spinner__lobe:nth-child(2) {
  transform: rotate(120deg);
}

.ui-fidget-spinner__lobe:nth-child(3) {
  transform: rotate(240deg);
}

.ui-fidget-spinner__lobe::before {
  position: absolute;
  top: var(--fidget-spinner-lobe-top);
  left: var(--fidget-spinner-lobe-offset);
  width: var(--fidget-spinner-lobe-size);
  height: var(--fidget-spinner-lobe-size);
  content: "";
  background: color-mix(
    in srgb,
    currentColor var(--fidget-spinner-tint),
    transparent
  );
  border: var(--fidget-spinner-stroke) solid currentColor;
  border-radius: var(--radius-round);
}

.ui-fidget-spinner__lobe::after {
  position: absolute;
  top: var(--fidget-spinner-arm-top);
  left: 50%;
  width: var(--fidget-spinner-arm-width);
  height: var(--fidget-spinner-arm-height);
  content: "";
  background: currentColor;
  border-radius: var(--radius-control);
  opacity: var(--fidget-spinner-arm-opacity);
  transform: translateX(-50%);
}

.ui-fidget-spinner__hub {
  position: absolute;
  top: var(--fidget-spinner-hub-offset);
  left: var(--fidget-spinner-hub-offset);
  width: var(--fidget-spinner-hub-size);
  height: var(--fidget-spinner-hub-size);
  background: var(--canvas-raised);
  border: var(--fidget-spinner-stroke) solid currentColor;
  border-radius: var(--radius-round);
}

@keyframes ui-fidget-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ui-fidget-spinner__rotor {
    animation-duration: var(--fidget-spinner-reduced-duration);
  }
}
</style>
