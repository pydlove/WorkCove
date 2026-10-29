function normalizeHttpUrl(rawValue) {
  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    throw new Error('网址不能为空')
  }
  let parsed
  try {
    parsed = new URL(rawValue.trim())
  } catch {
    throw new Error('网址格式无效')
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('仅支持 HTTP 或 HTTPS 网址')
  }
  return parsed.toString()
}

function normalizeBrowserStartUrl(rawValue) {
  if (
    typeof rawValue === 'string' &&
    rawValue.trim().toLocaleLowerCase('en-US') === 'about:blank'
  ) {
    return 'about:blank'
  }
  return normalizeHttpUrl(rawValue)
}

function normalizeBrowserBounds(rawBounds, contentBounds) {
  if (!rawBounds || typeof rawBounds !== 'object') {
    throw new Error('浏览器区域无效')
  }
  const values = [
    rawBounds.x,
    rawBounds.y,
    rawBounds.width,
    rawBounds.height,
  ].map(Number)
  if (values.some((value) => !Number.isFinite(value))) {
    throw new Error('浏览器区域无效')
  }

  const contentWidth = Math.max(0, Number(contentBounds?.width) || 0)
  const contentHeight = Math.max(0, Number(contentBounds?.height) || 0)
  const x = Math.max(0, Math.min(Math.round(values[0]), contentWidth))
  const y = Math.max(0, Math.min(Math.round(values[1]), contentHeight))
  const width = Math.max(
    0,
    Math.min(Math.round(values[2]), contentWidth - x),
  )
  const height = Math.max(
    0,
    Math.min(Math.round(values[3]), contentHeight - y),
  )
  if (width < 1 || height < 1) {
    throw new Error('浏览器区域过小')
  }
  return { x, y, width, height }
}

function nextBrowserTabId(tabIds, closingTabId) {
  const ids = Array.isArray(tabIds)
    ? tabIds.filter((id) => typeof id === 'string' && id)
    : []
  const closingIndex = ids.indexOf(closingTabId)
  if (closingIndex < 0) return ids[0] ?? ''
  return ids[closingIndex + 1] ?? ids[closingIndex - 1] ?? ''
}

module.exports = {
  nextBrowserTabId,
  normalizeBrowserBounds,
  normalizeBrowserStartUrl,
  normalizeHttpUrl,
}
