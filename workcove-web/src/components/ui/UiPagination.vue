<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import { computed } from 'vue'

type PageItem = number | 'ellipsis-start' | 'ellipsis-end'

const props = withDefaults(
  defineProps<{
    page: number
    total: number
    pageSize?: number
    label?: string
    compact?: boolean
  }>(),
  {
    pageSize: 15,
    label: '分页',
    compact: false,
  },
)

const emit = defineEmits<{
  'update:page': [page: number]
}>()

const pageCount = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
)
const rangeStart = computed(() =>
  props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1,
)
const rangeEnd = computed(() =>
  Math.min(props.page * props.pageSize, props.total),
)
const pageItems = computed<PageItem[]>(() => {
  const count = pageCount.value
  if (count <= 7) {
    return Array.from({ length: count }, (_, index) => index + 1)
  }

  const candidates = new Set([
    1,
    count,
    props.page - 1,
    props.page,
    props.page + 1,
  ])
  if (props.page <= 3) {
    candidates.add(2)
    candidates.add(3)
    candidates.add(4)
  }
  if (props.page >= count - 2) {
    candidates.add(count - 1)
    candidates.add(count - 2)
    candidates.add(count - 3)
  }

  const pages = [...candidates]
    .filter((page) => page >= 1 && page <= count)
    .sort((left, right) => left - right)
  const items: PageItem[] = []
  pages.forEach((page, index) => {
    const previous = pages[index - 1]
    if (previous && page - previous > 1) {
      items.push(
        previous === 1 ? 'ellipsis-start' : 'ellipsis-end',
      )
    }
    items.push(page)
  })
  return items
})

function goTo(page: number) {
  const next = Math.min(pageCount.value, Math.max(1, page))
  if (next !== props.page) emit('update:page', next)
}
</script>

<template>
  <nav
    v-if="total > pageSize"
    class="ui-pagination"
    :class="{ 'ui-pagination--compact': compact }"
    :aria-label="label"
  >
    <span class="ui-pagination__range">
      {{ rangeStart }}–{{ rangeEnd }} / {{ total }}
    </span>
    <div class="ui-pagination__controls">
      <button
        type="button"
        aria-label="上一页"
        :disabled="page <= 1"
        @click="goTo(page - 1)"
      >
        <ChevronLeft :size="14" />
      </button>
      <template v-for="item in pageItems" :key="item">
        <span
          v-if="typeof item !== 'number'"
          class="ui-pagination__ellipsis"
          aria-hidden="true"
        >
          …
        </span>
        <button
          v-else
          type="button"
          :class="{ 'ui-pagination__page--active': item === page }"
          :aria-current="item === page ? 'page' : undefined"
          :aria-label="`第 ${item} 页`"
          @click="goTo(item)"
        >
          {{ item }}
        </button>
      </template>
      <button
        type="button"
        aria-label="下一页"
        :disabled="page >= pageCount"
        @click="goTo(page + 1)"
      >
        <ChevronRight :size="14" />
      </button>
    </div>
  </nav>
</template>

<style scoped>
.ui-pagination {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-2) var(--space-4);
  border-top: 1px solid var(--line-soft);
}

.ui-pagination__range {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.ui-pagination__controls {
  display: flex;
  align-items: center;
  gap: 2px;
}

.ui-pagination button {
  display: grid;
  min-width: 26px;
  height: 26px;
  padding: 0 5px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.ui-pagination button:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.ui-pagination button:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
}

.ui-pagination button:disabled {
  opacity: 0.35;
}

.ui-pagination__page--active {
  color: #ffffff !important;
  font-weight: var(--weight-emphasis);
  background: var(--system-blue) !important;
}

.ui-pagination__ellipsis {
  display: grid;
  width: 20px;
  height: 26px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  place-items: center;
}

.ui-pagination--compact {
  min-height: 42px;
  gap: var(--space-2);
  padding: var(--space-2);
}

.ui-pagination--compact
  .ui-pagination__controls
  > button:not(:first-child):not(:last-child),
.ui-pagination--compact .ui-pagination__ellipsis {
  display: none;
}

@media (max-width: 520px) {
  .ui-pagination {
    padding-inline: var(--space-3);
  }

  .ui-pagination__controls > button:not(:first-child):not(:last-child) {
    display: none;
  }

  .ui-pagination__ellipsis {
    display: none;
  }
}
</style>
