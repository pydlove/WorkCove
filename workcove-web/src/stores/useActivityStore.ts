import { defineStore } from 'pinia'

import { getSogouInputStats } from '@/api/activity'
import {
  loadLocalActivity,
  localDateKey,
  saveLocalActivity,
} from '@/services/localActivity'
import type {
  DailyActivity,
  SogouDailyInput,
  SogouInputStats,
} from '@/types/activity'

interface SogouState {
  loading: boolean
  installed: boolean
  serviceAvailable: boolean
  appPath: string | null
  totalInput: number
  currentSpeed: number
  historySpeed: number
  historySpeedAt: number | null
  days: SogouDailyInput[]
  message: string
  checkedAt: string | null
}

interface ActivityState {
  ownerEmail: string
  days: DailyActivity[]
  sogou: SogouState
}

const EMPTY_SOGOU_STATE: SogouState = {
  loading: false,
  installed: false,
  serviceAvailable: false,
  appPath: null,
  totalInput: 0,
  currentSpeed: 0,
  historySpeed: 0,
  historySpeedAt: null,
  days: [],
  message: '',
  checkedAt: null,
}

function sogouIsActive(state: ActivityState) {
  return (
    state.sogou.serviceAvailable &&
    state.sogou.days.length > 0
  )
}

export const useActivityStore = defineStore('activity', {
  state: (): ActivityState => ({
    ownerEmail: '',
    days: [] as DailyActivity[],
    sogou: { ...EMPTY_SOGOU_STATE } as SogouState,
  }),
  getters: {
    localToday(state): DailyActivity | null {
      const key = localDateKey()
      return state.days.find((day) => day.date === key) ?? null
    },
    localTotalTypedCharacters: (state) =>
      state.days.reduce(
        (total, day) => total + day.typedCharacters,
        0,
      ),
    sogouActive(state) {
      return sogouIsActive(state)
    },
    today(state): DailyActivity | null {
      const key = localDateKey()
      const local =
        state.days.find((day) => day.date === key) ?? null
      if (!sogouIsActive(state)) return local
      const external = state.sogou.days.find(
        (day) => day.date === key,
      )
      return {
        date: key,
        typedCharacters: external?.count ?? 0,
        mouseClicks: local?.mouseClicks ?? 0,
        updatedAt:
          local?.updatedAt ?? state.sogou.checkedAt ?? '',
      }
    },
    totalTypedCharacters(state) {
      return sogouIsActive(state)
        ? state.sogou.totalInput
        : state.days.reduce(
            (total, day) => total + day.typedCharacters,
            0,
          )
    },
    totalMouseClicks: (state) =>
      state.days.reduce((total, day) => total + day.mouseClicks, 0),
    history(state): DailyActivity[] {
      if (!sogouIsActive(state)) {
        return [...state.days].sort((left, right) =>
          right.date.localeCompare(left.date),
        )
      }
      const localByDate = new Map(
        state.days.map((day) => [day.date, day]),
      )
      return state.sogou.days
        .map((day) => {
          const local = localByDate.get(day.date)
          return {
            date: day.date,
            typedCharacters: day.count,
            mouseClicks: local?.mouseClicks ?? 0,
            updatedAt: local?.updatedAt ?? '',
          }
        })
        .filter(
          (day) => day.typedCharacters > 0 || day.mouseClicks > 0,
        )
        .sort((left, right) => right.date.localeCompare(left.date))
    },
  },
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        this.days = []
        this.sogou = { ...EMPTY_SOGOU_STATE }
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      this.days = loadLocalActivity(normalizedEmail)
      this.sogou = { ...EMPTY_SOGOU_STATE }
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalActivity(this.ownerEmail, this.days)
    },
    updateToday(
      updater: (day: DailyActivity) => DailyActivity,
    ) {
      const date = localDateKey()
      const existing = this.days.find((day) => day.date === date)
      const next = updater(
        existing ?? {
          date,
          typedCharacters: 0,
          mouseClicks: 0,
          updatedAt: new Date().toISOString(),
        },
      )
      this.days = existing
        ? this.days.map((day) => (day.date === date ? next : day))
        : [next, ...this.days]
      this.persist()
    },
    recordTypedCharacters(count: number) {
      if (!this.ownerEmail || count <= 0) return
      this.updateToday((day) => ({
        ...day,
        typedCharacters: day.typedCharacters + Math.floor(count),
        updatedAt: new Date().toISOString(),
      }))
    },
    recordMouseClick() {
      if (!this.ownerEmail) return
      this.updateToday((day) => ({
        ...day,
        mouseClicks: day.mouseClicks + 1,
        updatedAt: new Date().toISOString(),
      }))
    },
    async refreshSogouStats() {
      if (this.sogou.loading) return
      this.sogou = { ...this.sogou, loading: true }
      try {
        const stats: SogouInputStats = await getSogouInputStats()
        this.sogou = {
          loading: false,
          installed: stats.installed,
          serviceAvailable: stats.serviceAvailable,
          appPath: stats.appPath,
          totalInput: stats.totalInput,
          currentSpeed: stats.currentSpeed,
          historySpeed: stats.historySpeed,
          historySpeedAt: stats.historySpeedAt,
          days: Array.isArray(stats.days) ? stats.days : [],
          message: stats.message,
          checkedAt: stats.checkedAt,
        }
      } catch (error) {
        this.sogou = {
          ...this.sogou,
          loading: false,
          serviceAvailable: false,
          message:
            error instanceof Error
              ? error.message
              : '无法读取搜狗输入法统计',
          checkedAt: new Date().toISOString(),
        }
      }
    },
  },
})
