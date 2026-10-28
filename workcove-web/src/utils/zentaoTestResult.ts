import type { ZentaoTestStatus } from '@/types/zentaoTest'

const CONCLUSION_LABELS = ['测试结论', '验证结论', '结论']
const ENVIRONMENT_LABELS = ['测试环境', '验证环境', '环境']
const PROBLEM_LABELS = ['问题描述', 'Bug 描述', '缺陷描述']
const PROOF_LABELS = [
  '修复证明',
  '验证结果',
  '测试结果',
  '验证证据',
  '证明',
]
const ALL_LABELS = [
  ...CONCLUSION_LABELS,
  ...ENVIRONMENT_LABELS,
  ...PROBLEM_LABELS,
  ...PROOF_LABELS,
]

export interface ZentaoTestOutput {
  status: ZentaoTestStatus
  environment: string
  problemDescription: string
  proof: string
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function labelPattern(labels: string[], flags = 'i') {
  const choices = labels.map(escapeRegExp).join('|')
  return new RegExp(
    `(?:^|[\\n\\r。；;!?！？\\t ]+)` +
      `\\s*(?:` +
      `#{1,6}\\s*(?:${choices})\\s*(?:[:：])?` +
      `|(?:[-*+>]\\s*)?(?:\\*\\*|__)?(?:${choices})` +
      `(?:\\*\\*|__)?\\s*[:：]` +
      `)\\s*(?:\\*\\*|__)?\\s*`,
    flags,
  )
}

function extractLabeledSection(response: string, labels: string[]) {
  const start = labelPattern(labels).exec(response)
  if (!start) return ''
  const contentStart = start.index + start[0].length
  const stopPattern = labelPattern(ALL_LABELS, 'gi')
  stopPattern.lastIndex = contentStart
  const stop = stopPattern.exec(response)
  const boundary =
    stop?.[0].match(/^[\n\r。!?！？]+/)?.[0] ?? ''
  const contentEnd = stop
    ? stop.index + boundary.length
    : response.length
  return response
    .slice(contentStart, contentEnd)
    .replace(/\r\n?/g, '\n')
    .replace(/\n\s*(?:---|___|\*\*\*)\s*$/, '')
    .trim()
}

function conclusionStatus(value: string): ZentaoTestStatus {
  const normalized = value.toLocaleLowerCase('zh-CN')
  if (
    /失败|不通过|未通过|failed|仍存在|未修复/.test(normalized)
  ) {
    return 'failed'
  }
  if (/通过|已修复|passed|success|正常/.test(normalized)) {
    return 'passed'
  }
  return 'blocked'
}

export function extractZentaoTestResult(
  response: string,
): ZentaoTestOutput {
  const conclusion = extractLabeledSection(
    response,
    CONCLUSION_LABELS,
  )
  const environment = extractLabeledSection(
    response,
    ENVIRONMENT_LABELS,
  )
  const problemDescription = extractLabeledSection(
    response,
    PROBLEM_LABELS,
  )
  const proof = extractLabeledSection(response, PROOF_LABELS)
  return {
    status: conclusionStatus(conclusion),
    environment,
    problemDescription,
    proof: proof || (ALL_LABELS.every((label) => !response.includes(label))
      ? response.trim()
      : ''),
  }
}
