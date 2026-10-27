<script setup lang="ts">
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
    <span class="brand-mark__bar" />
    <span class="brand-mark__letter">W</span>
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
  color: #e6edf3;
  background: #161b22;
  border-radius: 9px;
  box-shadow:
    inset 0 0 0 1px rgb(240 246 252 / 10%),
    0 2px 7px rgb(0 0 0 / 30%);
}

.brand-mark__bar {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: #58a6ff;
}

.brand-mark__letter {
  padding-top: 2px;
  font-family: "SF Pro Display", "Helvetica Neue", sans-serif;
  font-size: 0.9em;
  font-weight: 700;
  letter-spacing: 0;
  transform: scale(0.84);
}
</style>
