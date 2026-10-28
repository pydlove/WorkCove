import { PRODUCT_NAME } from '@/brand'
import { getCcSwitchUsage } from '@/api/ccSwitch'
import type { CodexSession } from '@/types/codex'
import type {
  TokenUsageSnapshot,
  UsageRange,
} from '@/types/usage'

function rangeStart(range: UsageRange) {
  if (range === 'all') return null
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  if (range === '7d') start.setDate(start.getDate() - 6)
  if (range === '30d') start.setDate(start.getDate() - 29)
  return start.getTime()
}

function rangeStartSeconds(range: UsageRange) {
  const start = rangeStart(range)
  return start === null ? undefined : Math.floor(start / 1000)
}

function fallbackUsage(
  range: UsageRange,
  sessions: CodexSession[],
  reason: string,
): TokenUsageSnapshot {
  const start = rangeStart(range)
  const tasks = sessions
    .flatMap((session) => session.tasks)
    .filter((task) => {
      if (start === null) return true
      const startedAt = new Date(task.startedAt).getTime()
      return Number.isFinite(startedAt) && startedAt >= start
    })

  const summary = tasks.reduce(
    (result, task) => {
      const inputTokens = task.inputTokens ?? task.totalTokens ?? 0
      const outputTokens = task.outputTokens ?? 0
      const totalTokens =
        task.totalTokens ?? inputTokens + outputTokens
      result.inputTokens += inputTokens
      result.outputTokens += outputTokens
      result.cacheReadTokens += task.cachedInputTokens ?? 0
      result.totalTokens += totalTokens
      if (task.status === 'completed') result.successCount += 1
      return result
    },
    {
      inputTokens: 0,
      outputTokens: 0,
      cacheReadTokens: 0,
      totalTokens: 0,
      successCount: 0,
    },
  )

  return {
    available: true,
    source: 'workcove',
    range,
    reason,
    costUsd: null,
    requestCount: tasks.length,
    successCount: summary.successCount,
    inputTokens: summary.inputTokens,
    outputTokens: summary.outputTokens,
    cacheReadTokens: summary.cacheReadTokens,
    cacheCreationTokens: 0,
    totalTokens: summary.totalTokens,
    cacheHitRate:
      summary.inputTokens > 0
        ? Math.min(
            100,
            Math.max(
              0,
              (summary.cacheReadTokens / summary.inputTokens) * 100,
            ),
          )
        : null,
    updatedAt: new Date().toISOString(),
  }
}
export async function loadTokenUsage(
  range: UsageRange,
  sessions: CodexSession[],
): Promise<TokenUsageSnapshot> {
  const bridge = window.workCoveDesktop
  if (bridge?.ccSwitchUsage) {
    try {
      const usage = await bridge.ccSwitchUsage(range)
      if (
        usage.available &&
        (usage.requestCount > 0 || usage.totalTokens > 0)
      ) {
        return {
          ...usage,
          source: 'cc-switch',
          range,
        }
      }
    } catch {
      // Continue to the local backend bridge before using own statistics.
    }
  }

  try {
    const usage = await getCcSwitchUsage(rangeStartSeconds(range))
    if (
      usage.available &&
      (usage.requestCount > 0 || usage.totalTokens > 0)
    ) {
      return {
        ...usage,
        source: 'cc-switch',
        range,
      }
    }
    return fallbackUsage(
      range,
      sessions,
      usage.reason ||
        `CC Switch 暂无可用数据，已使用 ${PRODUCT_NAME}自有统计。`,
    )
  } catch (error) {
    return fallbackUsage(
      range,
      sessions,
      error instanceof Error
        ? `CC Switch 桥接不可用：${error.message}`
        : `CC Switch 桥接不可用，已使用 ${PRODUCT_NAME}自有统计。`,
    )
  }
}
