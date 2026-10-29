<script setup lang="ts">
import { ArrowUpRight } from '@lucide/vue'
import { computed } from 'vue'

import { splitTextUrls } from '@/utils/textUrls'

const props = defineProps<{
  text: string
}>()

const emit = defineEmits<{
  open: [url: string]
}>()

const segments = computed(() => splitTextUrls(props.text))
</script>

<template>
  <template
    v-for="(segment, index) in segments"
    :key="`${segment.type}:${index}`"
  >
    <button
      v-if="segment.type === 'url' && segment.href"
      class="text-with-links__url"
      type="button"
      :title="segment.href"
      :aria-label="`使用内置浏览器打开 ${segment.href}`"
      @click="emit('open', segment.href)"
    >
      <span>{{ segment.value }}</span>
      <ArrowUpRight :size="11" aria-hidden="true" />
    </button>
    <template v-else>{{ segment.value }}</template>
  </template>
</template>

<style scoped>
.text-with-links__url {
  display: inline;
  padding: 0 2px;
  color: var(--system-blue);
  font: inherit;
  line-height: inherit;
  text-align: inherit;
  text-decoration: underline;
  text-decoration-color: color-mix(
    in srgb,
    var(--system-blue) 42%,
    transparent
  );
  text-underline-offset: 2px;
  background: transparent;
  border-radius: 2px;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.text-with-links__url > span {
  white-space: inherit;
}

.text-with-links__url > svg {
  display: inline-block;
  margin-left: 2px;
  vertical-align: -1px;
}

.text-with-links__url:hover {
  color: var(--accent-strong);
  text-decoration-color: currentColor;
  background: var(--accent-soft);
}
</style>
