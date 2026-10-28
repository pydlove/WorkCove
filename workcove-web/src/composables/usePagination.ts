import { computed, ref, watch } from 'vue'

export const DEFAULT_PAGE_SIZE = 15

export function usePagination<T>(
  items: { readonly value: readonly T[] },
  resetKey?: () => unknown,
) {
  const currentPage = ref(1)
  const pageSize = DEFAULT_PAGE_SIZE
  const total = computed(() => items.value.length)
  const pageCount = computed(() =>
    Math.max(1, Math.ceil(total.value / pageSize)),
  )
  const pageItems = computed(() => {
    const start = (currentPage.value - 1) * pageSize
    return items.value.slice(start, start + pageSize)
  })
  const rangeStart = computed(() =>
    total.value === 0 ? 0 : (currentPage.value - 1) * pageSize + 1,
  )
  const rangeEnd = computed(() =>
    Math.min(currentPage.value * pageSize, total.value),
  )

  function setPage(page: number) {
    currentPage.value = Math.min(
      pageCount.value,
      Math.max(1, Math.trunc(page)),
    )
  }

  watch(pageCount, (count) => {
    if (currentPage.value > count) currentPage.value = count
  })

  if (resetKey) {
    watch(resetKey, () => {
      currentPage.value = 1
    })
  }

  return {
    currentPage,
    pageSize,
    total,
    pageCount,
    pageItems,
    rangeStart,
    rangeEnd,
    setPage,
  }
}
