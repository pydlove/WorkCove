export function formatCount(value: number) {
  const normalized = Number.isFinite(value) ? value : 0
  return new Intl.NumberFormat('zh-CN').format(normalized)
}

export function formatCompactCount(value: number) {
  const normalized = Math.max(
    0,
    Number.isFinite(value) ? value : 0,
  )
  if (normalized >= 100_000_000) {
    return `${compactDecimal(normalized / 100_000_000)}亿`
  }
  if (normalized >= 10_000) {
    return `${compactDecimal(normalized / 10_000)}万`
  }
  return formatCount(normalized)
}

function compactDecimal(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: 1,
  }).format(value)
}
