<script setup lang="ts">
import { Settings2 } from '@lucide/vue'
import { computed, watch } from 'vue'

import { visibleCategoryNames } from '@/utils/categoryFilter'

const props = withDefaults(
  defineProps<{
    categories: string[]
    counts: ReadonlyMap<string, number>
    total: number
    label?: string
    groupLabel?: string
  }>(),
  {
    label: '分类',
    groupLabel: '分类筛选',
  },
)

const selected = defineModel<string>({ required: true })
const visibleCategories = computed(() =>
  visibleCategoryNames(props.categories, props.counts),
)

watch(
  visibleCategories,
  (categories) => {
    if (selected.value !== '全部' && !categories.includes(selected.value)) {
      selected.value = '全部'
    }
  },
  { immediate: true },
)

const emit = defineEmits<{
  manage: []
}>()
</script>

<template>
  <div
    class="ui-category-filter"
    role="group"
    :aria-label="props.groupLabel"
  >
    <span class="ui-category-filter__label">{{ props.label }}</span>
    <button
      class="ui-category-filter__item ui-category-filter__manage"
      type="button"
      @click="emit('manage')"
    >
      <Settings2 :size="12" />
      管理
    </button>
    <button
      class="ui-category-filter__item"
      :class="{ 'ui-category-filter__item--active': selected === '全部' }"
      type="button"
      :aria-pressed="selected === '全部'"
      @click="selected = '全部'"
    >
      全部
      <small>{{ props.total }}</small>
    </button>
    <button
      v-for="category in visibleCategories"
      :key="category"
      class="ui-category-filter__item"
      :class="{ 'ui-category-filter__item--active': selected === category }"
      type="button"
      :aria-pressed="selected === category"
      @click="selected = category"
    >
      {{ category }}
      <small>{{ props.counts.get(category) ?? 0 }}</small>
    </button>
  </div>
</template>

<style scoped>
.ui-category-filter {
  display: flex;
  min-height: calc(var(--control-md) + var(--space-4));
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-4);
  overflow-x: auto;
  border-bottom: 1px solid var(--line-soft);
}

.ui-category-filter__label {
  flex: 0 0 auto;
  margin-right: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.ui-category-filter__item {
  display: inline-flex;
  height: var(--control-md);
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  white-space: nowrap;
  background: transparent;
  border-radius: var(--radius-control);
}

.ui-category-filter__item small {
  display: inline-flex;
  align-items: center;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  transform: translateY(1px);
}

.ui-category-filter__item:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.ui-category-filter__item--active {
  color: var(--accent);
  font-weight: var(--weight-emphasis);
  background: var(--accent-soft);
}

.ui-category-filter__item--active:hover {
  color: var(--accent);
  background: var(--accent-soft);
}

.ui-category-filter__manage {
  color: var(--text-muted);
  background: var(--control-bg);
  box-shadow: var(--control-shadow);
}

.ui-category-filter__manage:hover {
  color: var(--text);
  background: var(--control-bg);
}

.ui-category-filter__item:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
}
</style>
