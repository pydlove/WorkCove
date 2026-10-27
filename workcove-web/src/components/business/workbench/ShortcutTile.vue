<script setup lang="ts">
import { ArrowUpRight, MoreHorizontal } from '@lucide/vue'

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
      <ShortcutVisual :sources="iconUrls" :mode="mode" :name="name" />
      <span class="shortcut-copy">
        <strong>{{ name }}</strong>
        <small :title="detail">{{ detail }}</small>
      </span>
      <ArrowUpRight class="shortcut-arrow" :size="14" />
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
  border-radius: 7px;
  transition:
    background 150ms ease,
    box-shadow 150ms ease;
}

.shortcut-tile:hover {
  background: rgb(255 255 255 / 74%);
  box-shadow: 0 2px 8px rgb(31 35 40 / 5%);
}

.shortcut-tile__main {
  display: grid;
  width: 100%;
  min-height: 51px;
  grid-template-columns: 34px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 10px;
  padding: 7px 32px 7px 7px;
  text-align: left;
  background: transparent;
  border-radius: inherit;
}

.shortcut-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.shortcut-copy strong {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text);
  font-size: 10px;
  font-weight: 620;
  line-height: 1.35;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.shortcut-copy small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 8px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-arrow {
  color: var(--text-subtle);
  opacity: 0;
  transform: translate(-2px, 2px);
  transition:
    opacity 140ms ease,
    transform 140ms ease;
}

.shortcut-tile:hover .shortcut-arrow,
.shortcut-tile__main:focus-visible .shortcut-arrow {
  opacity: 1;
  transform: translate(0, 0);
}

.shortcut-edit {
  position: absolute;
  top: 8px;
  right: 8px;
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
    box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
  }

  .shortcut-edit:hover {
    background: rgb(240 246 252 / 6%);
  }
}

@media (max-width: 560px) {
  .shortcut-tile__main {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .shortcut-arrow {
    display: none;
  }

  .shortcut-edit {
    opacity: 1;
  }
}
</style>
