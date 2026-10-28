<script setup lang="ts">
import logoMark from '@/assets/brand/workcove-mark.png'

withDefaults(
  defineProps<{
    size?: number
  }>(),
  {
    size: 34,
  },
)
</script>

<template>
  <span
    class="brand-mark"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <img :src="logoMark" alt="" draggable="false" />
  </span>
</template>

<style scoped>
.brand-mark {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: 22%;
  box-shadow: var(--shadow-control);
}

.brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  user-select: none;
}
</style>
