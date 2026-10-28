export type UsageRange = 'today' | '7d' | '30d' | 'all'
export type UsageSource = 'cc-switch' | 'workcove'

export interface TokenUsageSnapshot {
  available: boolean
  source: UsageSource
  range: UsageRange
  reason: string
  costUsd: number | null
  requestCount: number
  successCount: number
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheCreationTokens: number
  totalTokens: number
  cacheHitRate: number | null
  updatedAt: string
}
