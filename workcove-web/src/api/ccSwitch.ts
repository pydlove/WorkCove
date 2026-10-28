import { get } from '@/api/http'
import type { TokenUsageSnapshot } from '@/types/usage'

export function getCcSwitchUsage(from?: number) {
  const query = from === undefined
    ? ''
    : `?from=${encodeURIComponent(String(from))}`
  return get<Omit<TokenUsageSnapshot, 'source' | 'range'>>(
    `/development/cc-switch/usage${query}`,
  )
}
