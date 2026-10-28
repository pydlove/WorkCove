<script setup lang="ts">
import { AppWindow, Globe2 } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import type { ShortcutMode } from '@/types/shortcut'

const props = withDefaults(
  defineProps<{
    sources: string[]
    mode: ShortcutMode
    size?: number
  }>(),
  {
    size: 34,
  },
)

const sourceIndex = ref(0)
const imageLoaded = ref(false)
const currentSource = computed(() => props.sources[sourceIndex.value] ?? null)
const fallbackIconSize = computed(() =>
  Math.max(16, Math.round(props.size * 0.38)),
)

watch(
  () => props.sources.join('\n'),
  () => {
    sourceIndex.value = 0
    imageLoaded.value = false
  },
)

function tryNextSource() {
  imageLoaded.value = false
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
    <span v-if="!imageLoaded" class="shortcut-visual__fallback">
      <Globe2 v-if="mode === 'website'" :size="fallbackIconSize" />
      <AppWindow v-else :size="fallbackIconSize" />
    </span>
    <img
      v-if="currentSource"
      :src="currentSource"
      alt=""
      referrerpolicy="no-referrer"
      @load="imageLoaded = true"
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
  color: var(--asset-tile-icon);
  background: var(--asset-tile-bg);
  box-shadow: inset 0 0 0 0.5px var(--asset-tile-border);
  border-radius: 22%;
  place-items: center;
  transition:
    box-shadow var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.shortcut-visual__fallback {
  display: grid;
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

</style>
