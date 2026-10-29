import {
  createCalendarEvent,
  getCalendarReminderSettings,
  listCalendarEvents,
  updateCalendarReminderSettings,
} from '@/api/calendar'
import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type {
  CalendarEvent,
  CalendarEventColor,
  CalendarReminderSettings,
} from '@/types/calendar'
import { DEFAULT_WORKSPACE_ID } from '@/types/workspace'

const LEGACY_EVENTS_KEY = 'workcove.calendar-events.v1'
const LEGACY_SETTINGS_KEY = 'workcove.calendar-reminder-settings.v1'
const REMINDER_MINUTES = new Set([0, 5, 10, 15, 30, 60, 1440])
const COLORS = new Set<CalendarEventColor>([
  'blue',
  'green',
  'orange',
  'red',
  'purple',
])

function normalizeLegacyEvent(value: unknown): CalendarEvent | null {
  if (!value || typeof value !== 'object') return null
  const event = value as Partial<CalendarEvent>
  if (
    typeof event.id !== 'string' ||
    typeof event.title !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(event.date ?? '') ||
    !/^\d{2}:\d{2}$/.test(event.startTime ?? '')
  ) {
    return null
  }
  return {
    id: event.id,
    workspaceId:
      typeof event.workspaceId === 'string' && event.workspaceId
        ? event.workspaceId
        : DEFAULT_WORKSPACE_ID,
    title: event.title.trim() || '未命名日程',
    date: event.date!,
    startTime: event.startTime!,
    endTime:
      typeof event.endTime === 'string' ? event.endTime : '',
    notes: typeof event.notes === 'string' ? event.notes : '',
    color: COLORS.has(event.color as CalendarEventColor)
      ? (event.color as CalendarEventColor)
      : 'blue',
    reminderInApp:
      typeof event.reminderInApp === 'boolean'
        ? event.reminderInApp
        : true,
    reminderEmail:
      typeof event.reminderEmail === 'boolean'
        ? event.reminderEmail
        : false,
    reminderMinutesBefore: REMINDER_MINUTES.has(
      Number(event.reminderMinutesBefore),
    )
      ? Number(event.reminderMinutesBefore)
      : 10,
    createdAt:
      typeof event.createdAt === 'string'
        ? event.createdAt
        : new Date().toISOString(),
    updatedAt:
      typeof event.updatedAt === 'string'
        ? event.updatedAt
        : new Date().toISOString(),
  }
}

export async function loadUserCalendar(
  email: string,
  workspaceId: string,
) {
  const remote = await listCalendarEvents(workspaceId)
  if (remote.length > 0) {
    const staleLegacy = readUserScopedValue<CalendarEvent[]>(
      LEGACY_EVENTS_KEY,
      email,
    )
    if (Array.isArray(staleLegacy) && staleLegacy.length > 0) {
      writeUserScopedValue(LEGACY_EVENTS_KEY, email, [])
    }
    return remote
  }

  const legacy = readUserScopedValue<CalendarEvent[]>(
    LEGACY_EVENTS_KEY,
    email,
  )
  const events = Array.isArray(legacy)
    ? legacy
        .map(normalizeLegacyEvent)
        .filter((event): event is CalendarEvent => Boolean(event))
        .filter((event) => event.workspaceId === workspaceId)
    : []
  if (events.length === 0) return []

  const migrated: CalendarEvent[] = []
  for (const event of events) {
    migrated.push(
      await createCalendarEvent({
        ...event,
        id: undefined,
      }),
    )
  }
  writeUserScopedValue(LEGACY_EVENTS_KEY, email, [])
  return migrated
}

export async function loadCalendarReminderSettings(
  email: string,
  workspaceId: string,
): Promise<CalendarReminderSettings> {
  const stored = await getCalendarReminderSettings(workspaceId)
  if (stored.configured) {
    return {
      defaultInApp: stored.defaultInApp,
      defaultEmail: stored.defaultEmail,
      defaultMinutesBefore: stored.defaultMinutesBefore,
    }
  }

  const legacy = readUserScopedValue<CalendarReminderSettings>(
    LEGACY_SETTINGS_KEY,
    email,
  )
  const settings: CalendarReminderSettings = {
    defaultInApp:
      typeof legacy?.defaultInApp === 'boolean'
        ? legacy.defaultInApp
        : true,
    defaultEmail:
      typeof legacy?.defaultEmail === 'boolean'
        ? legacy.defaultEmail
        : false,
    defaultMinutesBefore: REMINDER_MINUTES.has(
      Number(legacy?.defaultMinutesBefore),
    )
      ? Number(legacy?.defaultMinutesBefore)
      : 10,
  }
  const saved = await updateCalendarReminderSettings(
    workspaceId,
    settings,
  )
  return {
    defaultInApp: saved.defaultInApp,
    defaultEmail: saved.defaultEmail,
    defaultMinutesBefore: saved.defaultMinutesBefore,
  }
}

export function saveCalendarReminderSettings(
  workspaceId: string,
  settings: CalendarReminderSettings,
) {
  return updateCalendarReminderSettings(workspaceId, settings)
}
