<script setup lang="ts">
import { CalendarDays, Clock3 } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

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

const weekDays = computed(() => {
  const current = now.value
  const start = new Date(current)
  start.setDate(current.getDate() - ((current.getDay() + 6) % 7))
  start.setHours(12, 0, 0, 0)

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    return {
      key: date.toISOString(),
      label: new Intl.DateTimeFormat('zh-CN', {
        weekday: 'narrow',
      }).format(date),
      day: date.getDate(),
      isToday: date.toDateString() === current.toDateString(),
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
    const remaining = start - minutes
    return `距离 09:00 还有 ${formatDuration(remaining)}`
  }
  if (minutes >= end) return '今天的工作时段已结束'

  const remaining = end - minutes
  return `距离 18:00 还有 ${formatDuration(remaining)}`
})

function formatDuration(minutes: number) {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest} 分钟`
  if (rest === 0) return `${hours} 小时`
  return `${hours} 小时 ${rest} 分钟`
}

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
  <section class="time-panel" aria-label="当前时间">
    <div class="clock-zone">
      <div class="clock-zone__label">
        <span><Clock3 :size="13" /> LOCAL TIME</span>
        <span>UTC+8</span>
      </div>

      <div class="clock-zone__time">
        <strong>{{ timeText }}</strong>
        <span>{{ secondsText }}</span>
      </div>

      <div class="clock-zone__date">
        <CalendarDays :size="14" />
        <span>{{ dateText }}</span>
        <span class="week-number">W{{ weekNumber }}</span>
      </div>
    </div>

    <div class="rhythm-zone">
      <div class="rhythm-zone__heading">
        <div>
          <p>WORKDAY RHYTHM</p>
          <h2>今天的工作节奏</h2>
        </div>
        <span>{{ workdayMessage }}</span>
      </div>

      <div class="workday-track">
        <span :style="{ width: `${workdayProgress}%` }" />
      </div>
      <div class="workday-scale">
        <span>09:00</span>
        <span>12:00</span>
        <span>15:00</span>
        <span>18:00</span>
      </div>

      <div class="week-days">
        <div
          v-for="day in weekDays"
          :key="day.key"
          :class="{ 'week-days__today': day.isToday }"
        >
          <span>{{ day.label }}</span>
          <strong>{{ day.day }}</strong>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.time-panel {
  position: relative;
  display: grid;
  overflow: hidden;
  grid-template-columns: minmax(300px, 0.9fr) minmax(380px, 1.1fr);
  min-height: 260px;
  background: #f6f8fa;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.time-panel::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: "";
  background-image:
    linear-gradient(rgb(31 35 40 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(31 35 40 / 3%) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: linear-gradient(90deg, #000, transparent 78%);
}

.clock-zone,
.rhythm-zone {
  position: relative;
  z-index: 1;
}

.clock-zone {
  display: flex;
  justify-content: center;
  padding: 30px 34px 26px;
  flex-direction: column;
  border-right: 1px solid var(--line-soft);
}

.clock-zone__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-subtle);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.1em;
}

.clock-zone__label > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.clock-zone__time {
  display: flex;
  align-items: flex-end;
  gap: 9px;
  margin: 14px 0 11px;
  color: var(--text);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
}

.clock-zone__time strong {
  font-size: clamp(48px, 5.4vw, 68px);
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.clock-zone__time > span {
  padding-bottom: 8px;
  color: var(--accent);
  font-size: 15px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.clock-zone__date {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 10px;
}

.week-number {
  padding: 3px 6px;
  margin-left: 5px;
  color: var(--accent);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  background: var(--accent-soft);
  border-radius: 4px;
}

.rhythm-zone {
  display: flex;
  justify-content: center;
  padding: 28px 32px 24px;
  flex-direction: column;
}

.rhythm-zone__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.rhythm-zone__heading p {
  margin: 0 0 5px;
  color: var(--success);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.11em;
}

.rhythm-zone__heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 620;
}

.rhythm-zone__heading > span {
  color: var(--text-subtle);
  font-size: 9px;
  text-align: right;
}

.workday-track {
  height: 5px;
  overflow: hidden;
  margin-top: 22px;
  background: rgb(31 35 40 / 8%);
  border-radius: 999px;
}

.workday-track > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--accent), #55a6ff);
  border-radius: inherit;
  transition: width 300ms ease;
}

.workday-scale {
  display: flex;
  justify-content: space-between;
  margin-top: 7px;
  color: var(--text-subtle);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 7px;
  font-variant-numeric: tabular-nums;
}

.week-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 3px;
  padding-top: 22px;
  margin-top: 22px;
  border-top: 1px solid var(--line-soft);
}

.week-days > div {
  display: flex;
  height: 55px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  color: var(--text-subtle);
  border-radius: 7px;
}

.week-days span {
  font-size: 8px;
}

.week-days strong {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
}

.week-days__today {
  color: #ffffff;
  background: var(--accent);
}

.week-days__today strong {
  color: #ffffff;
}

html[data-theme="dark"] {
  .time-panel {
    background: #161b22;
  }

  .time-panel::before {
    background-image:
      linear-gradient(rgb(240 246 252 / 4%) 1px, transparent 1px),
      linear-gradient(90deg, rgb(240 246 252 / 4%) 1px, transparent 1px);
  }

  .workday-track {
    background: rgb(240 246 252 / 8%);
  }
}

@media (max-width: 900px) {
  .time-panel {
    grid-template-columns: 1fr;
  }

  .clock-zone {
    border-right: 0;
    border-bottom: 1px solid var(--line-soft);
  }
}

@media (max-width: 560px) {
  .clock-zone,
  .rhythm-zone {
    padding: 22px 20px;
  }

  .clock-zone__time strong {
    font-size: 50px;
  }

  .rhythm-zone__heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }

  .rhythm-zone__heading > span {
    text-align: left;
  }
}
</style>
