<script setup lang="ts">
import TrafficLights from '@/components/ui/TrafficLights.vue'

withDefaults(
  defineProps<{
    title?: string
    localStatus?: boolean
  }>(),
  {
    title: 'WorkCove 栖作',
    localStatus: false,
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
      <span v-if="localStatus" class="local-status">
        <span class="local-status__dot" />
        本地
      </span>
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.titlebar {
  position: relative;
  z-index: 20;
  display: grid;
  height: 46px;
  flex: 0 0 46px;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 16px;
  background: rgb(246 248 250 / 84%);
  border-bottom: 1px solid var(--line-soft);
  backdrop-filter: blur(24px) saturate(1.25);
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
  gap: 10px;
}

.titlebar__title {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.local-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: 550;
  letter-spacing: 0.02em;
}

.local-status__dot {
  width: 6px;
  height: 6px;
  background: var(--success);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(26 127 55 / 12%);
}

html[data-theme="dark"] {
  .titlebar {
    background: rgb(13 17 23 / 84%);
  }

  .local-status__dot {
    box-shadow: 0 0 0 3px rgb(63 185 80 / 12%);
  }
}

@media (max-width: 720px) {
  .titlebar {
    padding: 0 14px;
  }

  .titlebar__title {
    max-width: 42vw;
  }
}
</style>
