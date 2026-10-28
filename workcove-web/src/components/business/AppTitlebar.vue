<script setup lang="ts">
import TrafficLights from '@/components/ui/TrafficLights.vue'
import { PRODUCT_NAME } from '@/brand'

withDefaults(
  defineProps<{
    title?: string
  }>(),
  {
    title: PRODUCT_NAME,
  },
)
</script>

<template>
  <header class="titlebar" data-tauri-drag-region>
    <div class="titlebar__leading" data-tauri-drag-region>
      <TrafficLights />
    </div>

    <span class="titlebar__title" data-tauri-drag-region>{{ title }}</span>

    <div class="titlebar__trailing" data-tauri-drag-region>
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.titlebar {
  position: relative;
  z-index: var(--z-titlebar);
  display: grid;
  height: var(--titlebar-height);
  flex: 0 0 var(--titlebar-height);
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 var(--space-4);
  background: var(--material);
  border-bottom: 1px solid var(--separator-soft);
  backdrop-filter: blur(30px) saturate(1.5);
  -webkit-app-region: drag;
}

.titlebar__leading,
.titlebar__trailing {
  display: flex;
  min-width: 0;
  align-items: center;
}

.titlebar__trailing {
  justify-content: flex-end;
  gap: var(--space-2);
}

.titlebar__title {
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .titlebar {
    padding: 0 var(--space-3);
  }

  .titlebar__title {
    max-width: 42vw;
  }
}
</style>
