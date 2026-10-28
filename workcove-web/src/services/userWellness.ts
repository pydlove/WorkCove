import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type {
  WellnessAction,
  WellnessActionType,
  WellnessActiveRest,
  WellnessActiveWork,
  WellnessData,
  WellnessReminderSettings,
  WellnessRestSession,
  WellnessWorkSession,
  WellnessWorkdaySchedule,
} from '@/types/wellness'

const STORAGE_KEY = 'workcove.wellness.v1'
const MAX_HISTORY_DAYS = 366
const ACTION_TYPES = new Set<WellnessActionType>([
  'water',
  'stand',
  'restroom',
])
const REMINDER_MIN_MINUTES = 5
const REMINDER_MAX_MINUTES = 480

export const DEFAULT_WELLNESS_REMINDER_SETTINGS: WellnessReminderSettings = {
  clockInReminder: true,
  clockOutReminder: true,
  reminderMinutesBefore: 5,
  workdays: [
    workday(1, true, '09:00', '18:00'),
    workday(2, true, '09:00', '18:00'),
    workday(3, true, '09:00', '18:00'),
    workday(4, true, '09:00', '18:00'),
    workday(5, true, '09:00', '18:00'),
    workday(6, false, '09:00', '18:00'),
    workday(7, false, '09:00', '18:00'),
  ],
  waterMinutes: 60,
  movementMinutes: 90,
  restroomMinutes: 120,
}

export function emptyWellnessData(): WellnessData {
  return {
    startedAtByDate: {},
    actions: [],
    restSessions: [],
    activeRest: null,
    workSessions: [],
    activeWork: null,
    reminderSettings: cloneReminderSettings(
      DEFAULT_WELLNESS_REMINDER_SETTINGS,
    ),
  }
}

export function newWellnessId() {
  return window.crypto.randomUUID()
}

export function loadUserWellness(email: string): WellnessData {
  return normalizeWellnessData(
    readUserScopedValue<unknown>(STORAGE_KEY, email),
  )
}

export function saveUserWellness(email: string, data: WellnessData) {
  writeUserScopedValue(STORAGE_KEY, email, data)
}

function normalizeWellnessData(value: unknown): WellnessData {
  if (!value || typeof value !== 'object') return emptyWellnessData()
  const source = value as Partial<WellnessData>
  const cutoff = Date.now() - MAX_HISTORY_DAYS * 86_400_000

  const startedAtByDate: Record<string, string> = {}
  if (source.startedAtByDate && typeof source.startedAtByDate === 'object') {
    for (const [date, startedAt] of Object.entries(
      source.startedAtByDate,
    )) {
      if (
        /^\d{4}-\d{2}-\d{2}$/.test(date) &&
        new Date(`${date}T00:00:00`).getTime() >= cutoff &&
        typeof startedAt === 'string' &&
        Number.isFinite(new Date(startedAt).getTime())
      ) {
        startedAtByDate[date] = startedAt
      }
    }
  }

  const actions = Array.isArray(source.actions)
    ? source.actions
        .filter(isWellnessAction)
        .filter((action) => new Date(action.occurredAt).getTime() >= cutoff)
    : []
  const restSessions = Array.isArray(source.restSessions)
    ? source.restSessions
        .filter(isWellnessRestSession)
        .filter((rest) => new Date(rest.startedAt).getTime() >= cutoff)
    : []
  const workSessions = Array.isArray(source.workSessions)
    ? source.workSessions
        .filter(isWellnessWorkSession)
        .filter((work) => new Date(work.startedAt).getTime() >= cutoff)
    : []

  return {
    startedAtByDate,
    actions,
    restSessions,
    activeRest: isActiveRest(source.activeRest)
      ? source.activeRest
      : null,
    workSessions,
    activeWork: isActiveWork(source.activeWork)
      ? source.activeWork
      : null,
    reminderSettings: normalizeWellnessReminderSettings(
      source.reminderSettings,
    ),
  }
}

export function normalizeWellnessReminderSettings(
  value: Partial<WellnessReminderSettings> | null | undefined,
): WellnessReminderSettings {
  return {
    clockInReminder:
      typeof value?.clockInReminder === 'boolean'
        ? value.clockInReminder
        : DEFAULT_WELLNESS_REMINDER_SETTINGS.clockInReminder,
    clockOutReminder:
      typeof value?.clockOutReminder === 'boolean'
        ? value.clockOutReminder
        : DEFAULT_WELLNESS_REMINDER_SETTINGS.clockOutReminder,
    reminderMinutesBefore: reminderLeadMinutes(
      value?.reminderMinutesBefore,
    ),
    workdays: normalizeWorkdays(value?.workdays),
    waterMinutes: reminderMinutes(
      value?.waterMinutes,
      DEFAULT_WELLNESS_REMINDER_SETTINGS.waterMinutes,
    ),
    movementMinutes: reminderMinutes(
      value?.movementMinutes,
      DEFAULT_WELLNESS_REMINDER_SETTINGS.movementMinutes,
    ),
    restroomMinutes: reminderMinutes(
      value?.restroomMinutes,
      DEFAULT_WELLNESS_REMINDER_SETTINGS.restroomMinutes,
    ),
  }
}

function cloneReminderSettings(
  value: WellnessReminderSettings,
): WellnessReminderSettings {
  return {
    ...value,
    workdays: value.workdays.map((item) => ({ ...item })),
  }
}

function workday(
  day: number,
  enabled: boolean,
  startTime: string,
  endTime: string,
): WellnessWorkdaySchedule {
  return { day, enabled, startTime, endTime }
}

function normalizeWorkdays(
  value: WellnessWorkdaySchedule[] | null | undefined,
) {
  const source = new Map(
    (Array.isArray(value) ? value : [])
      .filter(
        (item) =>
          item &&
          Number.isInteger(item.day) &&
          item.day >= 1 &&
          item.day <= 7,
      )
      .map((item) => [item.day, item]),
  )
  return DEFAULT_WELLNESS_REMINDER_SETTINGS.workdays.map((fallback) => {
    const candidate = source.get(fallback.day)
    return {
      day: fallback.day,
      enabled:
        typeof candidate?.enabled === 'boolean'
          ? candidate.enabled
          : fallback.enabled,
      startTime: timeValue(candidate?.startTime, fallback.startTime),
      endTime: timeValue(candidate?.endTime, fallback.endTime),
    }
  })
}

function timeValue(value: unknown, fallback: string) {
  return typeof value === 'string' &&
    /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)
    ? value
    : fallback
}

function reminderLeadMinutes(value: unknown) {
  if (
    typeof value !== 'number' ||
    !Number.isFinite(value)
  ) {
    return DEFAULT_WELLNESS_REMINDER_SETTINGS.reminderMinutesBefore
  }
  return Math.min(120, Math.max(0, Math.round(value)))
}

function reminderMinutes(value: unknown, fallback: number) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return fallback
  }
  return Math.min(
    REMINDER_MAX_MINUTES,
    Math.max(REMINDER_MIN_MINUTES, Math.round(value)),
  )
}

function isWellnessAction(value: unknown): value is WellnessAction {
  if (!value || typeof value !== 'object') return false
  const action = value as Partial<WellnessAction>
  return (
    typeof action.id === 'string' &&
    typeof action.type === 'string' &&
    ACTION_TYPES.has(action.type as WellnessActionType) &&
    typeof action.occurredAt === 'string' &&
    Number.isFinite(new Date(action.occurredAt).getTime())
  )
}

function isWellnessRestSession(
  value: unknown,
): value is WellnessRestSession {
  if (!value || typeof value !== 'object') return false
  const rest = value as Partial<WellnessRestSession>
  return (
    typeof rest.id === 'string' &&
    typeof rest.startedAt === 'string' &&
    typeof rest.endedAt === 'string' &&
    typeof rest.durationMs === 'number' &&
    rest.durationMs >= 0 &&
    Number.isFinite(new Date(rest.startedAt).getTime()) &&
    Number.isFinite(new Date(rest.endedAt).getTime())
  )
}

function isActiveRest(value: unknown): value is WellnessActiveRest {
  if (!value || typeof value !== 'object') return false
  const active = value as Partial<WellnessActiveRest>
  return (
    typeof active.startedAt === 'string' &&
    Number.isFinite(new Date(active.startedAt).getTime())
  )
}

function isWellnessWorkSession(
  value: unknown,
): value is WellnessWorkSession {
  if (!value || typeof value !== 'object') return false
  const work = value as Partial<WellnessWorkSession>
  return (
    typeof work.id === 'string' &&
    typeof work.startedAt === 'string' &&
    typeof work.endedAt === 'string' &&
    typeof work.durationMs === 'number' &&
    work.durationMs >= 0 &&
    Number.isFinite(new Date(work.startedAt).getTime()) &&
    Number.isFinite(new Date(work.endedAt).getTime())
  )
}

function isActiveWork(value: unknown): value is WellnessActiveWork {
  if (!value || typeof value !== 'object') return false
  const active = value as Partial<WellnessActiveWork>
  return (
    typeof active.startedAt === 'string' &&
    Number.isFinite(new Date(active.startedAt).getTime())
  )
}
