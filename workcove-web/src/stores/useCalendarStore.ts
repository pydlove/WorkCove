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
    workspaceId: '',
    events: [] as CalendarEvent[],
    reminderSettings: {
      defaultInApp: true,
      defaultEmail: false,
      defaultMinutesBefore: 10,
    } as CalendarReminderSettings,
    loading: false,
    loaded: false,
    loadSequence: 0,
  }),
  actions: {
    async load(email: string, workspaceId: string, force = false) {
      const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail || !workspaceId) {
        this.ownerEmail = ''
        this.workspaceId = ''
        this.events = []
        this.loaded = false
        this.loading = false
        this.loadSequence += 1
        return
      }
      if (
        !force &&
        this.loaded &&
        this.ownerEmail === normalizedEmail &&
        this.workspaceId === workspaceId
      ) {
        return
      }
      const sequence = ++this.loadSequence
      const ownerChanged = this.ownerEmail !== normalizedEmail
      this.ownerEmail = normalizedEmail
      const workspaceChanged = this.workspaceId !== workspaceId
      this.workspaceId = workspaceId
      if (ownerChanged || workspaceChanged) {
        this.events = []
        this.loaded = false
        this.reminderSettings = {
          defaultInApp: true,
          defaultEmail: false,
          defaultMinutesBefore: 10,
        }
      }
      this.loading = true
      this.loaded = false
      try {
        const [events, settings] = await Promise.all([
          loadUserCalendar(normalizedEmail, workspaceId),
          loadCalendarReminderSettings(normalizedEmail, workspaceId),
        ])
        if (
          sequence !== this.loadSequence ||
          this.workspaceId !== workspaceId
        ) {
          return
        }
        this.events = events
        this.reminderSettings = settings
        this.loaded = true
      } finally {
        if (
          sequence === this.loadSequence &&
          this.workspaceId === workspaceId
        ) {
          this.loading = false
        }
      }
    },
    async save(input: CalendarEventInput) {
      const workspaceId = this.workspaceId
      if (!workspaceId || input.workspaceId !== workspaceId) {
        throw new Error('日程不属于当前工作空间')
      }
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
      if (this.workspaceId !== workspaceId) return saved
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
      const workspaceId = this.workspaceId
      if (!workspaceId) return
      await deleteCalendarEvent(id, workspaceId)
      if (this.workspaceId !== workspaceId) return
      this.events = this.events.filter((event) => event.id !== id)
    },
    async saveReminderSettings(settings: CalendarReminderSettings) {
      const workspaceId = this.workspaceId
      if (!workspaceId) throw new Error('工作空间尚未初始化')
      const saved = await saveCalendarReminderSettings(
        workspaceId,
        settings,
      )
      if (this.workspaceId !== workspaceId) return
      this.reminderSettings = {
        defaultInApp: saved.defaultInApp,
        defaultEmail: saved.defaultEmail,
        defaultMinutesBefore: saved.defaultMinutesBefore,
      }
    },
  },
})
