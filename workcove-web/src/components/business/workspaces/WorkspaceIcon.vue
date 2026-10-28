<script setup lang="ts">
import {
  BookOpen,
  Briefcase,
  CalendarDays,
  Code2,
  Home,
  Layers3,
  Rocket,
  Star,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import type {
  WorkspaceIcon,
  WorkspaceIconTone,
} from '@/types/workspace'

const props = withDefaults(
  defineProps<{
    icon: WorkspaceIcon
    tone: WorkspaceIconTone
    imageUrl?: string
    size?: number
  }>(),
  {
    size: 15,
    imageUrl: '',
  },
)

const icons = {
  layers: Layers3,
  briefcase: Briefcase,
  code: Code2,
  calendar: CalendarDays,
  rocket: Rocket,
  book: BookOpen,
  home: Home,
  star: Star,
}

const iconComponent = computed(
  () => icons[props.icon] ?? Layers3,
)
const failedImageUrl = ref('')
const visibleImageUrl = computed(() =>
  props.imageUrl && props.imageUrl !== failedImageUrl.value
    ? props.imageUrl
    : '',
)

watch(
  () => props.imageUrl,
  () => {
    failedImageUrl.value = ''
  },
)

function handleImageError() {
  failedImageUrl.value = props.imageUrl
}
</script>

<template>
  <span
    class="workspace-icon"
    :class="[
      `workspace-icon--${tone}`,
      { 'workspace-icon--image': visibleImageUrl },
    ]"
    aria-hidden="true"
  >
    <img
      v-if="visibleImageUrl"
      :key="visibleImageUrl"
      :src="visibleImageUrl"
      alt=""
      draggable="false"
      @error="handleImageError"
    />
    <component
      :is="iconComponent"
      v-else
      :size="size"
      :stroke-width="1.9"
    />
  </span>
</template>

<style scoped>
.workspace-icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  border: 1px solid color-mix(in srgb, currentColor 16%, transparent);
  border-radius: 8px;
  place-items: center;
}

.workspace-icon--blue {
  color: var(--system-blue);
  background: color-mix(
    in srgb,
    var(--system-blue) 12%,
    var(--canvas-raised)
  );
}

.workspace-icon--green {
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 12%,
    var(--canvas-raised)
  );
}

.workspace-icon--orange {
  color: var(--system-orange);
  background: color-mix(
    in srgb,
    var(--system-orange) 12%,
    var(--canvas-raised)
  );
}

.workspace-icon--red {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 12%,
    var(--canvas-raised)
  );
}

.workspace-icon--purple {
  color: color-mix(in srgb, var(--system-blue) 50%, var(--system-red));
  background: color-mix(
    in srgb,
    color-mix(in srgb, var(--system-blue) 50%, var(--system-red)) 12%,
    var(--canvas-raised)
  );
}

.workspace-icon--graphite {
  color: var(--text-muted);
  background: var(--surface-inset);
}

.workspace-icon--image {
  overflow: hidden;
  padding: 0;
  background: var(--asset-tile-bg);
  border-color: var(--asset-tile-border);
}

.workspace-icon--image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
