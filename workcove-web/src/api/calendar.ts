import {
  deleteNoContentWithHeaders,
  get,
  postJsonNoContentWithHeaders,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  CalendarEvent,
  CalendarEventInput,
  CalendarNotification,
  CalendarReminderSettings,
} from '@/types/calendar'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-calendar' }

function requestBody(input: CalendarEventInput) {
  return {
    workspaceId: input.workspaceId,
    title: input.title,
    date: input.date,
    startTime: input.startTime,
    endTime: input.endTime,
    notes: input.notes,
    color: input.color,
    reminderInApp: input.reminderInApp,
    reminderEmail: input.reminderEmail,
    reminderMinutesBefore: input.reminderMinutesBefore,
  }
}

export const listCalendarEvents = () =>
  get<CalendarEvent[]>('/calendar/events')

export const createCalendarEvent = (input: CalendarEventInput) =>
  postJsonWithHeaders<CalendarEvent>(
    '/calendar/events',
    requestBody(input),
    ACTION_HEADER,
  )

export const updateCalendarEvent = (input: CalendarEventInput) =>
  putJsonWithHeaders<CalendarEvent>(
    `/calendar/events/${encodeURIComponent(input.id ?? '')}`,
    requestBody(input),
    ACTION_HEADER,
  )

export const deleteCalendarEvent = (id: string) =>
  deleteNoContentWithHeaders(
    `/calendar/events/${encodeURIComponent(id)}`,
    ACTION_HEADER,
  )

export const getCalendarReminderSettings = () =>
  get<CalendarReminderSettings & { configured: boolean }>(
    '/calendar/reminder-settings',
  )

export const updateCalendarReminderSettings = (
  settings: CalendarReminderSettings,
) =>
  putJsonWithHeaders<
    CalendarReminderSettings & { configured: boolean }
  >('/calendar/reminder-settings', settings, ACTION_HEADER)

export const listCalendarNotifications = (limit = 20) =>
  get<CalendarNotification[]>(
    `/calendar/notifications?limit=${Math.max(1, Math.min(limit, 100))}`,
  )

export const getUnreadNotificationCount = () =>
  get<{ count: number }>('/calendar/notifications/unread-count')

export const markCalendarNotificationRead = (id: number) =>
  postJsonNoContentWithHeaders(
    `/calendar/notifications/${id}/read`,
    {},
    ACTION_HEADER,
  )

export const markAllCalendarNotificationsRead = () =>
  postJsonNoContentWithHeaders(
    '/calendar/notifications/read-all',
    {},
    ACTION_HEADER,
  )
