<script setup lang="ts">
import { MoreHorizontal } from '@lucide/vue'

import ShortcutVisual from '@/components/business/workbench/ShortcutVisual.vue'
import type { ShortcutMode } from '@/types/shortcut'

defineProps<{
  name: string
  detail: string
  iconUrls: string[]
  mode: ShortcutMode
}>()

const emit = defineEmits<{
  open: []
  edit: []
}>()
</script>

<template>
  <article class="shortcut-tile">
    <button
      class="shortcut-tile__main"
      type="button"
      :aria-label="mode === 'website' ? `打开 ${name}` : `启动 ${name}`"
      @click="emit('open')"
    >
      <ShortcutVisual
        :sources="iconUrls"
        :mode="mode"
        :name="name"
        :size="54"
      />
      <span class="shortcut-copy">
        <strong>{{ name }}</strong>
        <small :title="detail">{{ detail }}</small>
      </span>
    </button>
    <button
      class="shortcut-edit"
      type="button"
      :aria-label="`编辑 ${name}`"
      @click="emit('edit')"
    >
      <MoreHorizontal :size="16" />
    </button>
  </article>
</template>

<style scoped>
.shortcut-tile {
  position: relative;
  min-width: 0;
  background: transparent;
  border-radius: 9px;
  transition:
    background 150ms ease,
    transform 150ms ease;
}

.shortcut-tile:hover {
  background: rgb(255 255 255 / 58%);
  transform: translateY(-1px);
}

.shortcut-tile__main {
  display: flex;
  width: 100%;
  min-height: 94px;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 7px 4px;
  flex-direction: column;
  text-align: center;
  background: transparent;
  border-radius: inherit;
}

.shortcut-copy {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 2px;
}

.shortcut-copy strong {
  display: block;
  width: 100%;
  overflow: hidden;
  color: var(--text);
  font-size: 10px;
  font-weight: 620;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-copy small {
  display: block;
  width: 100%;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 8px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-edit {
  position: absolute;
  top: 3px;
  right: 3px;
  display: grid;
  width: 25px;
  height: 25px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 5px;
  opacity: 0;
  place-items: center;
  transition:
    opacity 120ms ease,
    background 120ms ease,
    color 120ms ease;
}

.shortcut-tile:hover .shortcut-edit,
.shortcut-edit:focus-visible {
  opacity: 1;
}

.shortcut-edit:hover {
  color: var(--text);
  background: rgb(31 35 40 / 5%);
}

html[data-theme="dark"] {
  .shortcut-tile {
    background: transparent;
  }

  .shortcut-tile:hover {
    background: rgb(240 246 252 / 5%);
  }

  .shortcut-edit:hover {
    background: rgb(240 246 252 / 6%);
  }
}

@media (max-width: 560px) {
  .shortcut-edit {
    opacity: 1;
  }
}
</style>
