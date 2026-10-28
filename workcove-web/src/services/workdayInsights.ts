import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type { WorkdayInsightsSettings } from '@/types/workdayInsights'

const STORAGE_KEY = 'workcove.workday-insights.v1'

export const DEFAULT_WORKDAY_INSIGHTS: WorkdayInsightsSettings = {
  payday: 10,
  dailyIncome: 0,
}

function normalizeSettings(
  value: WorkdayInsightsSettings | null,
): WorkdayInsightsSettings {
  const payday = Number(value?.payday)
  const dailyIncome = Number(value?.dailyIncome)
  return {
    payday:
      Number.isInteger(payday) && payday >= 1 && payday <= 31
        ? payday
        : DEFAULT_WORKDAY_INSIGHTS.payday,
    dailyIncome:
      Number.isFinite(dailyIncome) &&
      dailyIncome >= 0 &&
      dailyIncome <= 1_000_000_000_000
        ? Math.round(dailyIncome * 100) / 100
        : DEFAULT_WORKDAY_INSIGHTS.dailyIncome,
  }
}

export function loadWorkdayInsights(email: string) {
  return normalizeSettings(
    readUserScopedValue<WorkdayInsightsSettings>(STORAGE_KEY, email),
  )
}

export function saveWorkdayInsights(
  email: string,
  settings: WorkdayInsightsSettings,
) {
  writeUserScopedValue(
    STORAGE_KEY,
    email,
    normalizeSettings(settings),
  )
}
