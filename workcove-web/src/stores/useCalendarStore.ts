import { defineStore } from 'pinia'

import {
  createCalendarEvent,
  deleteCalendarEvent,
  updateCalendarEvent,
} from '@/api/calendar'
import {
  loadCalendarReminderSettings,
  loadUserCalendar,
  saveCalendarReminderSettings,
} from '@/services/userCalendar'
import type {
  CalendarEvent,
  CalendarEventInput,
  CalendarReminderSettings,
} from '@/types/calendar'

function normalizeTime(value: string) {
  return /^\d{2}:\d{2}$/.test(value) ? value : '09:00'
}

export const useCalendarStore = defineStore('calendar', {
  state: () => ({
    ownerEmail: '',
    events: [] as CalendarEvent[],
    reminderSettings: {
      defaultInApp: true,
      defaultEmail: false,
      defaultMinutesBefore: 10,
    } as CalendarReminderSettings,
    loading: false,
    loaded: false,
  }),
  actions: {
    async load(email: string, force = false) {
      const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.events = []
        this.loaded = false
        this.loading = false
        return
      }
      if (
        !force &&
        this.loaded &&
        this.ownerEmail === normalizedEmail
      ) {
        return
      }
      this.ownerEmail = normalizedEmail
      this.loading = true
      this.loaded = false
      try {
        const [events, settings] = await Promise.all([
          loadUserCalendar(normalizedEmail),
          loadCalendarReminderSettings(normalizedEmail),
        ])
        this.events = events
        this.reminderSettings = settings
        this.loaded = true
      } finally {
        this.loading = false
      }
    },
    async save(input: CalendarEventInput) {
      const title = input.title.trim()
      if (!title) throw new Error('请输入日程名称')
      if ([...title].length > 120) {
        throw new Error('日程名称不能超过 120 个字符')
      }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(input.date)) {
        throw new Error('请选择日程日期')
      }
      const startTime = normalizeTime(input.startTime)
      const endTime =
        /^\d{2}:\d{2}$/.test(input.endTime) ? input.endTime : ''
      if (endTime && endTime <= startTime) {
        throw new Error('结束时间必须晚于开始时间')
      }

      const payload: CalendarEventInput = {
        ...input,
        title,
        startTime,
        endTime,
        notes: input.notes.trim(),
      }
      const saved = input.id
        ? await updateCalendarEvent(payload)
        : await createCalendarEvent(payload)
      const existing = this.events.some((event) => event.id === saved.id)
      this.events = existing
        ? this.events.map((event) =>
            event.id === saved.id ? saved : event,
          )
        : [saved, ...this.events]
      this.events.sort((left, right) =>
        `${left.date} ${left.startTime}`.localeCompare(
          `${right.date} ${right.startTime}`,
        ),
      )
      return saved
    },
    async remove(id: string) {
      await deleteCalendarEvent(id)
      this.events = this.events.filter((event) => event.id !== id)
    },
    async saveReminderSettings(settings: CalendarReminderSettings) {
      const saved = await saveCalendarReminderSettings(settings)
      this.reminderSettings = {
        defaultInApp: saved.defaultInApp,
        defaultEmail: saved.defaultEmail,
        defaultMinutesBefore: saved.defaultMinutesBefore,
      }
    },
  },
})
