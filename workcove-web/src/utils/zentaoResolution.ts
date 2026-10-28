const SOLUTION_LABELS = ['解决方案', '修复方案']
const REASON_LABELS = [
  '定位原因',
  '问题原因',
  '失败原因',
  '根因',
  '原因',
]

export interface ZentaoResolutionFields {
  solution: string
  reason: string
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function labelPattern(labels: string[], flags = 'i') {
  const choices = labels.map(escapeRegExp).join('|')
  return new RegExp(
    `(?:^|[\\n\\r。；;!?！？\\t ]+)` +
      `\\s*(?:#{1,6}\\s*)?(?:[-*+>]\\s*)?` +
      `(?:\\*\\*|__)?(?:${choices})(?:\\*\\*|__)?\\s*[:：]` +
      `\\s*(?:\\*\\*|__)?\\s*`,
    flags,
  )
}

function extractLabeledSection(response: string, labels: string[]) {
  const startPattern = labelPattern(labels)
  const start = startPattern.exec(response)
  if (!start) return ''

  const contentStart = start.index + start[0].length
  const stopPattern = labelPattern(
    [...SOLUTION_LABELS, ...REASON_LABELS],
    'gi',
  )
  stopPattern.lastIndex = contentStart
  const stop = stopPattern.exec(response)
  const stopBoundary =
    stop?.[0].match(/^[\n\r。!?！？]+/)?.[0] ?? ''
  const contentEnd = stop
    ? stop.index + stopBoundary.length
    : response.length

  return response
    .slice(contentStart, contentEnd)
    .replace(/\r\n?/g, '\n')
    .replace(/\n\s*(?:---|___|\*\*\*)\s*$/, '')
    .trim()
}

export function extractZentaoBatchBugSection(
  response: string,
  remoteId: string,
) {
  const escapedId = escapeRegExp(remoteId)
  const pattern = new RegExp(
    `(?:^|\\n)\\s*(?:` +
      `【\\s*Bug\\s*#${escapedId}\\s*】|` +
      `#{1,6}\\s*Bug\\s*#${escapedId}(?:\\s|：|:)|` +
      `Bug\\s*#${escapedId}\\s*(?:：|:)` +
      `)\\s*`,
    'i',
  )
  const marker = pattern.exec(response)
  if (!marker) return ''

  const contentStart = marker.index + marker[0].length
  const remaining = response.slice(contentStart)
  const nextBugPattern = new RegExp(
    `\\n\\s*(?:` +
      `【\\s*Bug\\s*#\\d+\\s*】|` +
      `#{1,6}\\s*Bug\\s*#\\d+(?:\\s|：|:)|` +
      `Bug\\s*#\\d+\\s*(?:：|:)` +
      `)`,
    'i',
  )
  const nextBug = nextBugPattern.exec(remaining)
  return remaining
    .slice(0, nextBug?.index ?? remaining.length)
    .trim()
}

export function extractZentaoResolution(
  response: string,
  remoteId: string,
  batch = false,
): ZentaoResolutionFields | null {
  const section = batch
    ? extractZentaoBatchBugSection(response, remoteId)
    : response.trim()
  if (!section) return null

  return {
    solution:
      extractLabeledSection(section, SOLUTION_LABELS) || section.trim(),
    reason: extractLabeledSection(section, REASON_LABELS),
  }
}
