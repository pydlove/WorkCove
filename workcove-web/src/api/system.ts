import { get } from '@/api/http'

export interface SystemHealth {
  status: 'UP' | 'DOWN'
  database: 'UP' | 'DOWN'
  checkedAt: string
}

export function getSystemHealth() {
  return get<SystemHealth>('/system/health')
}
