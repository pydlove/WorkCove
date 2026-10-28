export type WellnessActionType = 'water' | 'stand' | 'restroom'

export interface WellnessAction {
  id: string
  type: WellnessActionType
  occurredAt: string
}

export interface WellnessRestSession {
  id: string
  startedAt: string
  endedAt: string
  durationMs: number
}

export interface WellnessActiveRest {
  startedAt: string
}

export interface WellnessWorkSession {
  id: string
  startedAt: string
  endedAt: string
  durationMs: number
}

export interface WellnessActiveWork {
  startedAt: string
}

export interface WellnessWorkdaySchedule {
  day: number
  enabled: boolean
  startTime: string
  endTime: string
}

export interface WellnessReminderSettings {
  clockInReminder: boolean
  clockOutReminder: boolean
  reminderMinutesBefore: number
  workdays: WellnessWorkdaySchedule[]
  waterMinutes: number
  movementMinutes: number
  restroomMinutes: number
}

export interface WellnessData {
  startedAtByDate: Record<string, string>
  actions: WellnessAction[]
  restSessions: WellnessRestSession[]
  activeRest: WellnessActiveRest | null
  workSessions: WellnessWorkSession[]
  activeWork: WellnessActiveWork | null
  reminderSettings: WellnessReminderSettings
}

export interface WellnessDaySummary {
  date: string
  water: number
  stand: number
  restroom: number
  movements: number
  restMs: number
  focusMs: number
  actions: WellnessAction[]
  rests: WellnessRestSession[]
  workSessions: WellnessWorkSession[]
  clockInAt: string | null
  clockOutAt: string | null
  activeWorkStartedAt: string | null
}
