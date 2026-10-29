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

export const listCalendarEvents = (workspaceId: string) =>
  get<CalendarEvent[]>(
    `/calendar/events?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

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

export const deleteCalendarEvent = (
  id: string,
  workspaceId: string,
) =>
  deleteNoContentWithHeaders(
    `/calendar/events/${encodeURIComponent(id)}?workspaceId=${encodeURIComponent(workspaceId)}`,
    ACTION_HEADER,
  )

export const getCalendarReminderSettings = (workspaceId: string) =>
  get<CalendarReminderSettings & { configured: boolean }>(
    `/calendar/reminder-settings?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const updateCalendarReminderSettings = (
  workspaceId: string,
  settings: CalendarReminderSettings,
) =>
  putJsonWithHeaders<
    CalendarReminderSettings & { configured: boolean }
  >(
    '/calendar/reminder-settings',
    { workspaceId, ...settings },
    ACTION_HEADER,
  )

export const listCalendarNotifications = (
  workspaceId: string,
  limit = 20,
) =>
  get<CalendarNotification[]>(
    `/calendar/notifications?workspaceId=${encodeURIComponent(workspaceId)}&limit=${Math.max(1, Math.min(limit, 100))}`,
  )

export const getUnreadNotificationCount = (workspaceId: string) =>
  get<{ count: number }>(
    `/calendar/notifications/unread-count?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const markCalendarNotificationRead = (
  id: number,
  workspaceId: string,
) =>
  postJsonNoContentWithHeaders(
    `/calendar/notifications/${id}/read?workspaceId=${encodeURIComponent(workspaceId)}`,
    {},
    ACTION_HEADER,
  )

export const markAllCalendarNotificationsRead = (
  workspaceId: string,
) =>
  postJsonNoContentWithHeaders(
    `/calendar/notifications/read-all?workspaceId=${encodeURIComponent(workspaceId)}`,
    {},
    ACTION_HEADER,
  )
