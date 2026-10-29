import assert from 'node:assert/strict'
import test from 'node:test'

import type { WellnessData } from '../src/types/wellness.ts'
import {
  wellnessClockMode,
  wellnessClockOutConfirmation,
  wellnessReminderBaseline,
} from '../src/utils/wellness.ts'

function wellnessData(): WellnessData {
  return {
    startedAtByDate: {},
    actions: [],
    restSessions: [],
    activeRest: null,
    workSessions: [],
    activeWork: null,
    reminderSettings: {
      clockInReminder: true,
      clockOutReminder: true,
      reminderMinutesBefore: 5,
      workdays: [],
      waterMinutes: 60,
      movementMinutes: 90,
      restroomMinutes: 120,
    },
  }
}

const previousWorkday = new Date(2026, 9, 28, 9, 0)
const today = new Date(2026, 9, 29, 9, 0)

test('starts a new clock-in on the next day instead of updating yesterday', () => {
  const data = wellnessData()
  data.workSessions = [
    {
      id: 'yesterday',
      startedAt: previousWorkday.toISOString(),
      endedAt: new Date(2026, 9, 28, 18, 0).toISOString(),
      durationMs: 9 * 60 * 60_000,
    },
  ]

  assert.equal(wellnessClockMode(data, today), 'clockIn')
})

test('uses clock-out only for an active session from today', () => {
  const data = wellnessData()
  data.activeWork = { startedAt: today.toISOString() }
  assert.equal(wellnessClockMode(data, today), 'clockOut')

  data.activeWork = { startedAt: previousWorkday.toISOString() }
  assert.equal(wellnessClockMode(data, today), 'clockIn')
})

test('allows updates after a completed session from today', () => {
  const data = wellnessData()
  data.workSessions = [
    {
      id: 'today',
      startedAt: today.toISOString(),
      endedAt: new Date(2026, 9, 29, 18, 0).toISOString(),
      durationMs: 9 * 60 * 60_000,
    },
  ]

  assert.equal(wellnessClockMode(data, today), 'update')
})

test('warns when clocking out before the scheduled end time', () => {
  const data = wellnessData()
  data.reminderSettings.workdays = [
    {
      day: 4,
      enabled: true,
      startTime: '09:00',
      endTime: '18:00',
    },
  ]
  const confirmation = wellnessClockOutConfirmation(
    data.reminderSettings,
    new Date(2026, 9, 29, 17, 30),
  )

  assert.equal(confirmation.early, true)
  assert.equal(confirmation.scheduledEndTime, '18:00')
  assert.match(confirmation.warning, /还未到计划下班时间 18:00/)
})

test('uses the normal clock-out warning after the scheduled end time', () => {
  const data = wellnessData()
  data.reminderSettings.workdays = [
    {
      day: 4,
      enabled: true,
      startTime: '09:00',
      endTime: '18:00',
    },
  ]
  const confirmation = wellnessClockOutConfirmation(
    data.reminderSettings,
    new Date(2026, 9, 29, 18, 30),
  )

  assert.equal(confirmation.early, false)
  assert.match(confirmation.warning, /计划下班时间 18:00 已到/)
})

test('still requires confirmation when no schedule is enabled', () => {
  const data = wellnessData()
  const confirmation = wellnessClockOutConfirmation(
    data.reminderSettings,
    new Date(2026, 9, 29, 15, 0),
  )

  assert.equal(confirmation.early, false)
  assert.equal(confirmation.scheduledEndTime, '')
  assert.match(confirmation.warning, /未设置有效的计划下班时间/)
})

test('starts wellness reminders from clock-in, not midnight', () => {
  const data = wellnessData()
  const workStartedAt = new Date(2026, 9, 29, 9, 30)
  data.activeWork = { startedAt: workStartedAt.toISOString() }
  data.actions = [
    {
      id: 'before-work',
      type: 'water',
      occurredAt: new Date(2026, 9, 29, 8, 50).toISOString(),
    },
  ]

  assert.equal(
    wellnessReminderBaseline(
      data,
      ['water'],
      new Date(2026, 9, 29, 10, 30),
    ),
    workStartedAt.getTime(),
  )
})

test('uses the latest action after clock-in as the next reminder baseline', () => {
  const data = wellnessData()
  data.activeWork = {
    startedAt: new Date(2026, 9, 29, 9, 30).toISOString(),
  }
  const action = new Date(2026, 9, 29, 10, 5)
  data.actions = [
    {
      id: 'after-work',
      type: 'water',
      occurredAt: action.toISOString(),
    },
  ]

  assert.equal(
    wellnessReminderBaseline(
      data,
      ['water'],
      new Date(2026, 9, 29, 10, 30),
    ),
    action.getTime(),
  )
})
