<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number
  }>(),
  {
    size: 15,
  },
)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      d="m3.1 4.35 3.1 3.1-3.1 3.1"
      stroke="currentColor"
      stroke-width="1.45"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M8.4 10.55h4.35"
      stroke="currentColor"
      stroke-width="1.45"
      stroke-linecap="round"
    />
  </svg>
</template>
