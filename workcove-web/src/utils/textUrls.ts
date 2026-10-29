export interface TextUrlSegment {
  type: 'text' | 'url'
  value: string
  href?: string
}

const URL_PATTERN =
  /(?:\bhttps?:\/\/|\bwww\.)[^\s<>"'`，。；：！？、）】]+/gi
const TRAILING_PUNCTUATION =
  /[\s.,;:!?，。；：！？、)\]}>》】）]+$/u

export function splitTextUrls(text: string): TextUrlSegment[] {
  const segments: TextUrlSegment[] = []
  let cursor = 0

  for (const match of text.matchAll(URL_PATTERN)) {
    const rawValue = match[0]
    const value = rawValue.replace(TRAILING_PUNCTUATION, '')
    const start = match.index ?? 0
    if (!value) continue
    const href = normalizeTextUrl(value)
    if (!href) continue

    if (start > cursor) {
      segments.push({
        type: 'text',
        value: text.slice(cursor, start),
      })
    }
    segments.push({
      type: 'url',
      value,
      href,
    })
    cursor = start + value.length
  }

  if (cursor < text.length) {
    segments.push({
      type: 'text',
      value: text.slice(cursor),
    })
  }
  return segments.length > 0
    ? segments
    : [{ type: 'text', value: text }]
}

export function normalizeTextUrl(value: string) {
  const normalized = value.startsWith('www.')
    ? `https://${value}`
    : value
  try {
    const url = new URL(normalized)
    return url.protocol === 'http:' || url.protocol === 'https:'
      ? url.toString()
      : ''
  } catch {
    return ''
  }
}
