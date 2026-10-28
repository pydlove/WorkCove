import { defineStore } from 'pinia'

import {
  emptyWellnessData,
  loadUserWellness,
  newWellnessId,
  normalizeWellnessReminderSettings,
  saveUserWellness,
} from '@/services/userWellness'
import type {
  WellnessActionType,
  WellnessData,
  WellnessReminderSettings,
} from '@/types/wellness'
import { localDateKey } from '@/utils/wellness'

export const useWellnessStore = defineStore('wellness', {
  state: () => ({
    ownerEmail: '',
    data: emptyWellnessData() as WellnessData,
  }),
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.data = emptyWellnessData()
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.data = loadUserWellness(normalizedEmail)
      this.ensureDay(new Date())
      this.closeStaleSessions(new Date())
    },
    persist() {
      if (!this.ownerEmail) return
      saveUserWellness(this.ownerEmail, this.data)
    },
    updateReminderSettings(settings: WellnessReminderSettings) {
      this.data.reminderSettings =
        normalizeWellnessReminderSettings(settings)
      this.persist()
    },
    ensureDay(value: Date | string = new Date()) {
      const date = localDateKey(value)
      if (this.data.startedAtByDate[date]) return
      const timestamp =
        value instanceof Date ? value.toISOString() : new Date(value).toISOString()
      this.data.startedAtByDate = {
        ...this.data.startedAtByDate,
        [date]: timestamp,
      }
      this.persist()
    },
    recordAction(
      type: WellnessActionType,
      occurredAt: Date | string = new Date(),
    ) {
      const timestamp =
        occurredAt instanceof Date
          ? occurredAt.toISOString()
          : new Date(occurredAt).toISOString()
      this.ensureDay(timestamp)
      this.data.actions = [
        ...this.data.actions,
        {
          id: newWellnessId(),
          type,
          occurredAt: timestamp,
        },
      ]
      this.persist()
    },
    clockIn(startedAt: Date | string = new Date()) {
      if (this.data.activeWork) return false
      const timestamp =
        startedAt instanceof Date
          ? startedAt.toISOString()
          : new Date(startedAt).toISOString()
      const date = localDateKey(timestamp)
      if (
        this.data.workSessions.some(
          (work) => localDateKey(work.startedAt) === date,
        )
      ) {
        return false
      }
      this.ensureDay(timestamp)
      this.data.activeWork = { startedAt: timestamp }
      this.persist()
      return true
    },
    clockOut(endedAt: Date | string = new Date()) {
      const active = this.data.activeWork
      if (!active) return 0
      const end = endedAt instanceof Date ? endedAt : new Date(endedAt)
      if (this.data.activeRest) this.endRest(end)
      const startedAtMs = new Date(active.startedAt).getTime()
      const durationMs = Math.max(0, end.getTime() - startedAtMs)
      this.data.workSessions = [
        ...this.data.workSessions,
        {
          id: newWellnessId(),
          startedAt: active.startedAt,
          endedAt: end.toISOString(),
          durationMs,
        },
      ]
      this.data.activeWork = null
      this.ensureDay(end)
      this.persist()
      return durationMs
    },
    updateClockOut(endedAt: Date | string = new Date()) {
      if (this.data.activeWork) return 0
      const end = endedAt instanceof Date ? endedAt : new Date(endedAt)
      const date = localDateKey(end)
      const todaySessions = this.data.workSessions
        .filter((work) => localDateKey(work.startedAt) === date)
        .sort(
          (left, right) =>
            new Date(right.startedAt).getTime() -
            new Date(left.startedAt).getTime(),
        )
      const current = todaySessions[0]
      if (!current) return 0
      const startedAtMs = new Date(current.startedAt).getTime()
      const effectiveEnd = new Date(
        Math.max(startedAtMs, end.getTime()),
      )
      const durationMs = effectiveEnd.getTime() - startedAtMs
      this.data.workSessions = this.data.workSessions.map((work) =>
        work.id === current.id
          ? {
              ...work,
              endedAt: effectiveEnd.toISOString(),
              durationMs,
            }
          : work,
      )
      this.ensureDay(effectiveEnd)
      this.persist()
      return durationMs
    },
    closeStaleSessions(now: Date = new Date()) {
      const currentDate = localDateKey(now)
      const rest = this.data.activeRest
      if (rest && localDateKey(rest.startedAt) !== currentDate) {
        this.endRest(endOfLocalDay(rest.startedAt))
      }
      const work = this.data.activeWork
      if (work && localDateKey(work.startedAt) !== currentDate) {
        this.clockOut(endOfLocalDay(work.startedAt))
      }
    },
    startRest(startedAt: Date | string = new Date()) {
      if (this.data.activeRest) return false
      const timestamp =
        startedAt instanceof Date
          ? startedAt.toISOString()
          : new Date(startedAt).toISOString()
      this.ensureDay(timestamp)
      this.data.activeRest = { startedAt: timestamp }
      this.persist()
      return true
    },
    endRest(endedAt: Date | string = new Date()) {
      const active = this.data.activeRest
      if (!active) return 0
      const end =
        endedAt instanceof Date ? endedAt : new Date(endedAt)
      const durationMs = Math.max(
        0,
        end.getTime() - new Date(active.startedAt).getTime(),
      )
      this.data.restSessions = [
        ...this.data.restSessions,
        {
          id: newWellnessId(),
          startedAt: active.startedAt,
          endedAt: end.toISOString(),
          durationMs,
        },
      ]
      this.data.activeRest = null
      this.ensureDay(end)
      this.persist()
      return durationMs
    },
  },
})

function endOfLocalDay(value: string) {
  const date = new Date(value)
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    23,
    59,
    59,
    999,
  )
}
