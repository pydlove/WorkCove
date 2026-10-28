export const UNCATEGORIZED_CATEGORY = '未分类'

export function visibleCategoryNames(
  categories: readonly string[],
  counts: ReadonlyMap<string, number>,
) {
  const regular = categories.filter(
    (category) => category !== UNCATEGORIZED_CATEGORY,
  )
  const showUncategorized =
    categories.includes(UNCATEGORIZED_CATEGORY) &&
    (counts.get(UNCATEGORIZED_CATEGORY) ?? 0) > 0
  return showUncategorized
    ? [...regular, UNCATEGORIZED_CATEGORY]
    : regular
}
