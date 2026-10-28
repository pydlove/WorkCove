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
        :size="42"
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
}

.shortcut-tile__main {
  display: flex;
  width: 100%;
  min-height: 110px;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  padding: 10px 2px 9px;
  flex-direction: column;
  text-align: center;
  background: transparent;
  border-radius: inherit;
}

.shortcut-tile__main:focus-visible {
  outline: none;
}

.shortcut-tile__main:focus-visible .shortcut-visual {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.shortcut-tile__main:hover .shortcut-visual {
  box-shadow:
    inset 0 0 0 0.5px var(--asset-tile-border),
    var(--shadow-control);
  transform: translateY(-1px);
}

.shortcut-copy {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 6px;
}

.shortcut-copy strong {
  display: block;
  width: 100%;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-copy small {
  display: block;
  width: 100%;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: 1.5;
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
  background: var(--hover-bg);
}

@media (max-width: 560px) {
  .shortcut-edit {
    opacity: 1;
  }
}
</style>
