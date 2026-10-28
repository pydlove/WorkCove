export type CalendarEventColor =
  | 'blue'
  | 'green'
  | 'orange'
  | 'red'
  | 'purple'

export interface CalendarEvent {
  id: string
  workspaceId: string
  title: string
  date: string
  startTime: string
  endTime: string
  notes: string
  color: CalendarEventColor
  reminderInApp: boolean
  reminderEmail: boolean
  reminderMinutesBefore: number
  createdAt: string
  updatedAt: string
}

export interface CalendarEventInput {
  id?: string
  workspaceId: string
  title: string
  date: string
  startTime: string
  endTime: string
  notes: string
  color: CalendarEventColor
  reminderInApp: boolean
  reminderEmail: boolean
  reminderMinutesBefore: number
}

export interface CalendarReminderSettings {
  defaultInApp: boolean
  defaultEmail: boolean
  defaultMinutesBefore: number
}

export interface CalendarNotification {
  id: number
  title: string
  body: string
  linkView: string
  readAt: string | null
  createdAt: string
}
