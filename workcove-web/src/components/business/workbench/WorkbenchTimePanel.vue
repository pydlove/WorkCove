<script setup lang="ts">
import { CalendarDays, Clock3 } from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import WorkbenchActivityPanel from '@/components/business/workbench/WorkbenchActivityPanel.vue'
import WorkbenchDayInsights from '@/components/business/workbench/WorkbenchDayInsights.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCalendarStore } from '@/stores/useCalendarStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { CalendarEvent } from '@/types/calendar'
import {
  lunarDateName,
  lunarDayName,
} from '@/utils/lunarCalendar'

const emit = defineEmits<{
  notify: [message: string]
  openCalendar: [date: string]
}>()

const auth = useAuthStore()
const calendar = useCalendarStore()
const workspaces = useWorkspaceStore()
const now = ref(new Date())
let clockTimer: number | undefined

const timeText = computed(() => {
  const hour = String(now.value.getHours()).padStart(2, '0')
  const minute = String(now.value.getMinutes()).padStart(2, '0')
  return `${hour}:${minute}`
})

const secondsText = computed(() =>
  String(now.value.getSeconds()).padStart(2, '0'),
)

const dateText = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(now.value),
)

const weekNumber = computed(() => {
  const date = new Date(
    Date.UTC(
      now.value.getFullYear(),
      now.value.getMonth(),
      now.value.getDate(),
    ),
  )
  const day = date.getUTCDay() || 7
  date.setUTCDate(date.getUTCDate() + 4 - day)
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1))
  return Math.ceil(
    ((date.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7,
  )
})

const eventsByDate = computed(() => {
  const result = new Map<string, CalendarEvent[]>()
  for (const event of calendar.events) {
    if (event.workspaceId !== workspaces.activeWorkspaceId) continue
    const events = result.get(event.date) ?? []
    events.push(event)
    result.set(event.date, events)
  }
  for (const events of result.values()) {
    events.sort((left, right) =>
      left.startTime.localeCompare(right.startTime),
    )
  }
  return result
})

const weekDays = computed(() => {
  const current = now.value
  const start = new Date(current)
  start.setDate(current.getDate() - ((current.getDay() + 6) % 7))
  start.setHours(12, 0, 0, 0)

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const key = dateKey(date)
    const events = eventsByDate.value.get(key) ?? []
    return {
      key,
      dateKey: key,
      label: new Intl.DateTimeFormat('zh-CN', {
        weekday: 'narrow',
      }).format(date),
      day: date.getDate(),
      lunarDay: lunarDayName(date),
      isToday: date.toDateString() === current.toDateString(),
      events,
      eventColors: events.slice(0, 3).map((event) => event.color),
    }
  })
})

const isWeekend = computed(() => {
  const day = now.value.getDay()
  return day === 0 || day === 6
})

const workdayProgress = computed(() => {
  const minutes = now.value.getHours() * 60 + now.value.getMinutes()
  const start = 9 * 60
  const end = 18 * 60
  return Math.min(100, Math.max(0, ((minutes - start) / (end - start)) * 100))
})

const workdayMessage = computed(() => {
  if (isWeekend.value) return '周末，按自己的节奏来'

  const minutes = now.value.getHours() * 60 + now.value.getMinutes()
  const start = 9 * 60
  const end = 18 * 60
  if (minutes < start) {
    return `距离 09:00 还有 ${formatDuration(start - minutes)}`
  }
  if (minutes >= end) return '今天的工作时段已结束'
  return `距离 18:00 还有 ${formatDuration(end - minutes)}`
})

function formatDuration(minutes: number) {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest} 分钟`
  if (rest === 0) return `${hours} 小时`
  return `${hours} 小时 ${rest} 分钟`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function dateKey(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

watch(
  () => auth.email,
  (email) => {
    void calendar.load(email).catch((error) => {
      emit(
        'notify',
        error instanceof Error
          ? `日程加载失败：${error.message}`
          : '日程加载失败',
      )
    })
  },
  { immediate: true },
)

onMounted(() => {
  clockTimer = window.setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
})
</script>

<template>
  <section class="time-widget" aria-label="当前时间">
    <div class="time-widget__glow" aria-hidden="true" />

    <WorkbenchDayInsights @notify="emit('notify', $event)" />

    <header class="time-widget__header">
      <span class="time-label">
        <Clock3 :size="13" />
        本地时间
        <i>UTC+8</i>
      </span>
      <span class="date-label">
        <CalendarDays :size="13" />
        {{ dateText }}
        <span class="lunar-date">农历{{ lunarDateName(now) }}</span>
        <i>W{{ weekNumber }}</i>
      </span>
    </header>

    <div class="time-widget__main">
      <div class="clock-display">
        <strong>{{ timeText }}</strong>
        <span>{{ secondsText }}</span>
      </div>

      <div class="rhythm-copy">
        <p>工作节奏</p>
        <strong>{{ workdayMessage }}</strong>
      </div>
    </div>

    <WorkbenchActivityPanel />

    <footer class="time-widget__footer">
      <div class="workday-meter">
        <div class="workday-meter__labels">
          <span>09:00</span>
          <span>今日工作节奏</span>
          <span>18:00</span>
        </div>
        <div class="workday-meter__track">
          <span :style="{ width: `${workdayProgress}%` }" />
        </div>
      </div>

      <div class="week-days">
        <button
          v-for="day in weekDays"
          :key="day.key"
          type="button"
          :class="{ 'week-days__today': day.isToday }"
          :aria-label="`${day.dateKey}，${day.events.length} 个日程，打开日历`"
          :title="
            day.events.length
              ? `${day.dateKey} · ${day.events.length} 个日程`
              : `${day.dateKey} · 打开日历`
          "
          @click="emit('openCalendar', day.dateKey)"
        >
          <span>{{ day.label }}</span>
          <strong>{{ day.day }}</strong>
          <em>{{ day.lunarDay }}</em>
          <span class="week-days__events" aria-hidden="true">
            <i
              v-for="(color, index) in day.eventColors"
              :key="`${color}-${index}`"
              :class="`week-days__event-dot--${color}`"
            />
          </span>
        </button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.time-widget {
  position: relative;
  display: flex;
  min-height: 276px;
  overflow: hidden;
  padding: 24px 28px 20px;
  color: #ffffff;
  flex-direction: column;
  background:
    linear-gradient(
      115deg,
      rgb(5 13 24 / 94%) 0%,
      rgb(8 20 34 / 84%) 48%,
      rgb(7 17 29 / 68%) 100%
    ),
    url("@/assets/auth/workcove-login-glass.webp") center / cover no-repeat,
    #07111c;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 10px;
  box-shadow:
    0 16px 38px rgb(13 26 40 / 17%),
    inset 0 1px 0 rgb(255 255 255 / 10%);
  isolation: isolate;
}

.time-widget::after {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: "";
  border-radius: inherit;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 4%);
}

.time-widget__glow {
  position: absolute;
  top: -54%;
  right: 2%;
  width: 54%;
  height: 142%;
  pointer-events: none;
  background: linear-gradient(
    110deg,
    transparent 8%,
    rgb(117 201 238 / 12%) 48%,
    transparent 82%
  );
  filter: blur(24px);
  transform: skewX(-16deg);
}

.time-widget__header,
.time-widget__main,
.time-widget__footer {
  position: relative;
  z-index: 1;
}

.time-widget__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  color: rgb(255 255 255 / 52%);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.time-label,
.date-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.lunar-date {
  color: rgb(255 255 255 / 48%);
}

.time-label i,
.date-label i {
  padding: 2px 5px;
  color: rgb(255 255 255 / 58%);
  font-size: 11px;
  font-style: normal;
  background: rgb(255 255 255 / 8%);
  border-radius: 4px;
}

.time-widget__main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-top: auto;
  padding: 20px 0 24px;
}

.clock-display {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  font-family: var(--font-mono);
}

.clock-display strong {
  font-size: clamp(60px, 7vw, 82px);
  font-weight: 470;
  letter-spacing: 0;
  line-height: 0.92;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 3px 28px rgb(0 0 0 / 22%);
}

.clock-display span {
  padding-bottom: 7px;
  color: #79c0ff;
  font-size: 16px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
}

.rhythm-copy {
  display: flex;
  align-items: flex-end;
  padding-bottom: 4px;
  flex-direction: column;
}

.rhythm-copy p {
  margin: 0 0 7px;
  color: #7ee787;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.rhythm-copy strong {
  color: rgb(255 255 255 / 72%);
  font-size: 11px;
  font-weight: 540;
}

.time-widget__footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 30px;
  padding-top: 17px;
  border-top: 1px solid rgb(255 255 255 / 12%);
}

.workday-meter__labels {
  display: flex;
  justify-content: space-between;
  color: rgb(255 255 255 / 38%);
  font-family: var(--font-mono);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.workday-meter__track {
  height: 4px;
  overflow: hidden;
  margin-top: 8px;
  background: rgb(255 255 255 / 11%);
  border-radius: 999px;
}

.workday-meter__track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #1f6feb, #7dcfff);
  border-radius: inherit;
  box-shadow: 0 0 14px rgb(88 166 255 / 36%);
  transition: width 300ms ease;
}

.week-days {
  display: flex;
  align-items: center;
  gap: 4px;
}

.week-days > button {
  display: flex;
  width: 38px;
  height: 64px;
  align-items: center;
  justify-content: center;
  padding: 5px 0;
  flex-direction: column;
  gap: 3px;
  color: rgb(255 255 255 / 42%);
  font-family: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition:
    background-color 160ms ease,
    transform 160ms ease;
}

.week-days > button:hover {
  background: rgb(255 255 255 / 7%);
}

.week-days > button:focus-visible {
  outline: 2px solid #79c0ff;
  outline-offset: -2px;
}

.week-days span,
.week-days strong,
.week-days em {
  display: block;
  font-size: 11px;
  line-height: var(--line-caption);
}

.week-days strong {
  color: rgb(255 255 255 / 72%);
  font-size: 11px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
}

.week-days em {
  color: rgb(255 255 255 / 46%);
  font-style: normal;
}

.week-days__events {
  display: flex;
  min-height: 4px;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.week-days__events i {
  display: block;
  width: 4px;
  height: 4px;
  background: rgb(255 255 255 / 32%);
  border-radius: 50%;
}

.week-days__events i.week-days__event-dot--blue {
  background: var(--system-blue);
}

.week-days__events i.week-days__event-dot--green {
  background: var(--system-green);
}

.week-days__events i.week-days__event-dot--orange {
  background: var(--system-orange);
}

.week-days__events i.week-days__event-dot--red {
  background: var(--system-red);
}

.week-days__events i.week-days__event-dot--purple {
  background: color-mix(
    in srgb,
    var(--system-blue) 52%,
    var(--system-red)
  );
}

.week-days > .week-days__today {
  width: 48px;
  height: 76px;
  padding: 8px 0;
  margin-inline: -5px;
  gap: 4px;
  background: #0969da;
  border-radius: 10px;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 12%);
}

.week-days > .week-days__today:hover {
  background: #0b72e8;
}

.week-days__today strong {
  color: #ffffff;
}

.week-days__today em {
  color: rgb(255 255 255 / 76%);
}

@media (max-width: 760px) {
  .time-widget {
    min-height: 300px;
    padding: 22px 20px 18px;
  }

  .time-widget__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .time-widget__main {
    align-items: flex-start;
    padding: 28px 0 24px;
    flex-direction: column;
    gap: 14px;
  }

  .clock-display strong {
    font-size: 62px;
  }

  .rhythm-copy {
    align-items: flex-start;
  }

  .time-widget__footer {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .week-days {
    justify-content: space-between;
  }
}

@media (max-width: 390px) {
  .week-days > button {
    width: 31px;
  }

  .week-days > .week-days__today {
    width: 43px;
  }
}
</style>
