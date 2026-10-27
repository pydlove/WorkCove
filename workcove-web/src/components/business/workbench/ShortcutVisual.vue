<script setup lang="ts">
import { AppWindow } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import type { ShortcutMode } from '@/types/shortcut'

const props = withDefaults(
  defineProps<{
    sources: string[]
    mode: ShortcutMode
    name: string
    size?: number
  }>(),
  {
    size: 34,
  },
)

const sourceIndex = ref(0)
const currentSource = computed(() => props.sources[sourceIndex.value] ?? null)
const initial = computed(
  () => props.name.trim().charAt(0).toLocaleUpperCase() || 'W',
)
const fallbackFontSize = computed(() =>
  Math.max(10, Math.round(props.size * 0.34)),
)

watch(
  () => props.sources.join('\n'),
  () => {
    sourceIndex.value = 0
  },
)

function tryNextSource() {
  sourceIndex.value += 1
}
</script>

<template>
  <span
    class="shortcut-visual"
    :class="`shortcut-visual--${mode}`"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <span
      class="shortcut-visual__fallback"
      :style="{ fontSize: `${fallbackFontSize}px` }"
    >
      <template v-if="mode === 'website'">{{ initial }}</template>
      <AppWindow v-else :size="Math.max(16, Math.round(size * 0.38))" />
    </span>
    <img
      v-if="currentSource"
      :src="currentSource"
      alt=""
      referrerpolicy="no-referrer"
      @error="tryNextSource"
    />
  </span>
</template>

<style scoped>
.shortcut-visual {
  position: relative;
  display: grid;
  overflow: hidden;
  flex: 0 0 auto;
  color: #4a545e;
  background: #f0f2f4;
  border: 1px solid rgb(31 35 40 / 6%);
  border-radius: 22%;
  place-items: center;
}

.shortcut-visual__fallback {
  display: grid;
  font-weight: 650;
  place-items: center;
}

.shortcut-visual img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  object-fit: contain;
}

.shortcut-visual--website img {
  padding: 15%;
}

.shortcut-visual--application img {
  padding: 3%;
  object-fit: cover;
}

html[data-theme="dark"] .shortcut-visual {
  color: #c9d1d9;
  background: #21262d;
  border-color: rgb(240 246 252 / 6%);
}
</style>
