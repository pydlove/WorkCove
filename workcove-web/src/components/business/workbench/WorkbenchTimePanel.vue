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

    <header class="time-widget__header">
      <span class="time-label">
        <Clock3 :size="13" />
        LOCAL TIME
        <i>UTC+8</i>
      </span>
      <span class="date-label">
        <CalendarDays :size="13" />
        {{ dateText }}
        <i>W{{ weekNumber }}</i>
      </span>
    </header>

    <div class="time-widget__main">
      <div class="clock-display">
        <strong>{{ timeText }}</strong>
        <span>{{ secondsText }}</span>
      </div>

      <div class="rhythm-copy">
        <p>WORKDAY RHYTHM</p>
        <strong>{{ workdayMessage }}</strong>
      </div>
    </div>

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
        <div
          v-for="day in weekDays"
          :key="day.key"
          :class="{ 'week-days__today': day.isToday }"
        >
          <span>{{ day.label }}</span>
          <strong>{{ day.day }}</strong>
        </div>
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.time-label,
.date-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.time-label i,
.date-label i {
  padding: 2px 5px;
  color: rgb(255 255 255 / 58%);
  font-size: 7px;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.rhythm-copy strong {
  color: rgb(255 255 255 / 72%);
  font-size: 10px;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 7px;
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
  gap: 3px;
}

.week-days > div {
  display: flex;
  width: 35px;
  height: 44px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 3px;
  color: rgb(255 255 255 / 42%);
  border-radius: 7px;
}

.week-days span {
  font-size: 7px;
}

.week-days strong {
  color: rgb(255 255 255 / 72%);
  font-size: 10px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
}

.week-days__today {
  background: #0969da;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 12%);
}

.week-days__today strong {
  color: #ffffff;
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
  .week-days > div {
    width: 31px;
  }
}
</style>
