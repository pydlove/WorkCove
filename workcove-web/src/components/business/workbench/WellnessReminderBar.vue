<script setup lang="ts">
import {
  BellRing,
  DoorOpen,
  Droplets,
  Footprints,
  HeartPulse,
  LogIn,
  LogOut,
  Settings2,
  X,
} from '@lucide/vue'
import type { Component } from 'vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import WellnessReminderSettingsDialog from '@/components/business/workbench/WellnessReminderSettingsDialog.vue'
import { useWellnessStore } from '@/stores/useWellnessStore'
import {
  localDateKey,
  wellnessReminderBaseline,
} from '@/utils/wellness'

interface ReminderItem {
  type:
    | 'clockIn'
    | 'clockOut'
    | 'water'
    | 'movement'
    | 'restroom'
  key: string
  label: string
  message: string
  icon: Component
}

const wellness = useWellnessStore()
const now = ref(new Date())
const settingsOpen = ref(false)
const dismissedKeys = ref(new Set<string>())
let clockTimer: number | null = null

const today = computed(() => localDateKey(now.value))
const currentWorkday = computed(
  () => ((now.value.getDay() + 6) % 7) + 1,
)
const todaySchedule = computed(() =>
  wellness.data.reminderSettings.workdays.find(
    (day) => day.day === currentWorkday.value,
  ),
)
const withinWorkSchedule = computed(() => {
  const schedule = todaySchedule.value
  if (!schedule?.enabled) return false
  const currentMinutes =
    now.value.getHours() * 60 + now.value.getMinutes()
  return (
    currentMinutes >= localMinutes(schedule.startTime) &&
    currentMinutes < localMinutes(schedule.endTime)
  )
})
const activeWorkStartedAt = computed(() => {
  const active = wellness.data.activeWork
  return active &&
    localDateKey(active.startedAt) === today.value
    ? active.startedAt
    : null
})
const workStartedToday = computed(
  () =>
    Boolean(activeWorkStartedAt.value) ||
    wellness.data.workSessions.some(
      (work) => localDateKey(work.startedAt) === today.value,
    ),
)
const healthRemindersActive = computed(
  () =>
    Boolean(activeWorkStartedAt.value) &&
    withinWorkSchedule.value,
)
const waterReminderBaseline = computed(() =>
  wellnessReminderBaseline(wellness.data, ['water'], now.value),
)
const movementReminderBaseline = computed(() =>
  wellnessReminderBaseline(wellness.data, ['stand'], now.value),
)
const restroomReminderBaseline = computed(() =>
  wellnessReminderBaseline(wellness.data, ['restroom'], now.value),
)
const reminderItems = computed<ReminderItem[]>(() => {
  if (wellness.data.activeRest) return []

  const settings = wellness.data.reminderSettings
  const items: Array<ReminderItem | null> = [
    workReminder({
      type: 'clockIn',
      label: '上班打卡',
      icon: LogIn,
      time: todaySchedule.value?.startTime,
      enabled: settings.clockInReminder,
    }),
    workReminder({
      type: 'clockOut',
      label: '下班打卡',
      icon: LogOut,
      time: todaySchedule.value?.endTime,
      enabled: settings.clockOutReminder,
    }),
    dueItem({
      type: 'water',
      label: '喝水',
      icon: Droplets,
      enabled: healthRemindersActive.value,
      baselineAt: waterReminderBaseline.value,
      intervalMinutes: settings.waterMinutes,
      message: (minutes) =>
        `距上次喝水已 ${minutes} 分钟，补充少量水分。`,
    }),
    dueItem({
      type: 'movement',
      label: '工位活动',
      icon: Footprints,
      enabled: healthRemindersActive.value,
      baselineAt: movementReminderBaseline.value,
      intervalMinutes: settings.movementMinutes,
      message: (minutes) =>
        `已连续专注 ${minutes} 分钟，做几下扭腰、伸展或肩颈放松。`,
    }),
    dueItem({
      type: 'restroom',
      label: '如厕提示',
      icon: DoorOpen,
      enabled: healthRemindersActive.value,
      baselineAt: restroomReminderBaseline.value,
      intervalMinutes: settings.restroomMinutes,
      message: (minutes) =>
        `距上次如厕已 ${minutes} 分钟，有尿意就及时去，无需强忍。`,
    }),
  ]
  return items.filter((item): item is ReminderItem => Boolean(item))
})
const hasWorkReminder = computed(() =>
  reminderItems.value.some(
    (item) => item.type === 'clockIn' || item.type === 'clockOut',
  ),
)
const reminderTitle = computed(() => {
  const hasHealthReminder = reminderItems.value.some(
    (item) => item.type !== 'clockIn' && item.type !== 'clockOut',
  )
  if (hasWorkReminder.value && hasHealthReminder) {
    return '工作与健康提醒'
  }
  return hasWorkReminder.value ? '工作提醒' : '健康提醒'
})

watch(
  () => wellness.data.reminderSettings,
  () => {
    dismissedKeys.value = new Set()
  },
  { deep: true },
)

function dueItem(options: {
  type: ReminderItem['type']
  label: string
  icon: Component
  enabled?: boolean
  baselineAt: number | null
  intervalMinutes: number
  message: (minutes: number) => string
}) {
  if (options.enabled === false) return null
  const baseline = options.baselineAt
  if (baseline === null) return null

  const elapsedMinutes = Math.max(
    0,
    Math.floor(
      (now.value.getTime() - baseline) / 60_000,
    ),
  )
  const key = `${options.type}:${baseline}`
  if (
    elapsedMinutes < options.intervalMinutes ||
    dismissedKeys.value.has(key)
  ) {
    return null
  }

  return {
    type: options.type,
    key,
    label: options.label,
    message: options.message(elapsedMinutes),
    icon: options.icon,
  } satisfies ReminderItem
}

function localMinutes(value: string) {
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

function workReminder(options: {
  type: 'clockIn' | 'clockOut'
  label: string
  icon: Component
  time: string | undefined
  enabled: boolean
}) {
  if (!options.enabled || !todaySchedule.value?.enabled || !options.time) {
    return null
  }
  if (options.type === 'clockIn' && workStartedToday.value) return null
  if (options.type === 'clockOut' && !activeWorkStartedAt.value) return null

  const target = localTime(now.value, options.time)
  const remindAt =
    target.getTime() -
    wellness.data.reminderSettings.reminderMinutesBefore * 60_000
  if (now.value.getTime() < remindAt) return null

  const key = `${options.type}:${today.value}:${options.time}`
  if (dismissedKeys.value.has(key)) return null
  const minutesUntil = Math.ceil(
    (target.getTime() - now.value.getTime()) / 60_000,
  )
  const message =
    options.type === 'clockIn'
      ? minutesUntil > 0
        ? `计划 ${options.time} 上班，还有 ${minutesUntil} 分钟，请准备打卡。`
        : `已到计划上班时间 ${options.time}，请完成上班打卡。`
      : minutesUntil > 0
        ? `计划 ${options.time} 下班，还有 ${minutesUntil} 分钟。`
        : `已到计划下班时间 ${options.time}，请完成下班打卡。`

  return {
    type: options.type,
    key,
    label: options.label,
    message,
    icon: options.icon,
  } satisfies ReminderItem
}

function localTime(date: Date, value: string) {
  const [hour, minute] = value.split(':').map(Number)
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    hour,
    minute,
    0,
    0,
  )
}

function dismissReminders() {
  const next = new Set(dismissedKeys.value)
  reminderItems.value.forEach((item) => next.add(item.key))
  dismissedKeys.value = next
}

function closeSettings() {
  settingsOpen.value = false
}

function handleSettingsSaved() {
  dismissedKeys.value = new Set()
}

onMounted(() => {
  if (wellness.ownerEmail) {
    wellness.ensureDay(now.value)
    wellness.closeStaleSessions(now.value)
  }
  clockTimer = window.setInterval(() => {
    now.value = new Date()
    if (wellness.ownerEmail) {
      wellness.ensureDay(now.value)
      wellness.closeStaleSessions(now.value)
    }
  }, 30_000)
})

onBeforeUnmount(() => {
  if (clockTimer !== null) window.clearInterval(clockTimer)
})
</script>

<template>
  <Transition name="wellness-reminder">
    <aside
      v-if="reminderItems.length > 0"
      class="wellness-reminder-bar"
      role="status"
      aria-live="polite"
      :aria-label="reminderTitle"
    >
      <span class="wellness-reminder-bar__icon" aria-hidden="true">
        <BellRing v-if="hasWorkReminder" :size="17" />
        <HeartPulse v-else :size="17" />
      </span>
      <div class="wellness-reminder-bar__copy">
        <span>
          <strong>{{ reminderTitle }}</strong>
        </span>
        <p>
          <span
            v-for="item in reminderItems"
            :key="item.key"
            class="wellness-reminder-bar__item"
          >
            <component :is="item.icon" :size="12" />
            <b>{{ item.label }}</b>
            {{ item.message }}
          </span>
        </p>
      </div>
      <div class="wellness-reminder-bar__actions">
        <button
          type="button"
          aria-label="设置提醒"
          title="提醒设置"
          @click="settingsOpen = true"
        >
          <Settings2 :size="14" />
        </button>
        <button
          type="button"
          aria-label="关闭本轮提醒"
          title="关闭本轮提醒"
          @click="dismissReminders"
        >
          <X :size="14" />
        </button>
      </div>
    </aside>
  </Transition>

  <WellnessReminderSettingsDialog
    v-if="settingsOpen"
    @close="closeSettings"
    @saved="handleSettingsSaved"
  />
</template>

<style scoped>
.wellness-reminder-bar {
  display: flex;
  min-height: 50px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  background: color-mix(
    in srgb,
    var(--accent) 7%,
    var(--canvas-raised)
  );
  border-top: 1px solid color-mix(
    in srgb,
    var(--accent) 14%,
    var(--line-soft)
  );
}

.wellness-reminder-bar__icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  animation: wellness-reminder-breathe 1.8s ease-in-out 2;
  place-items: center;
}

.wellness-reminder-bar__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.wellness-reminder-bar__copy > span {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.wellness-reminder-bar__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.wellness-reminder-bar__copy p {
  display: flex;
  min-width: 0;
  gap: 5px var(--space-4);
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  flex-wrap: wrap;
}

.wellness-reminder-bar__item {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
}

.wellness-reminder-bar__item svg {
  flex: 0 0 auto;
  color: var(--accent);
}

.wellness-reminder-bar__item b {
  flex: 0 0 auto;
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.wellness-reminder-bar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-1);
}

.wellness-reminder-bar__actions button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-reminder-bar__actions button:hover {
  color: var(--accent);
  background: var(--accent-soft);
}

.wellness-reminder-enter-active,
.wellness-reminder-leave-active {
  transition:
    opacity var(--duration-popover) var(--ease-apple),
    transform var(--duration-popover) var(--ease-apple);
}

.wellness-reminder-enter-from,
.wellness-reminder-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@keyframes wellness-reminder-breathe {
  0%,
  100% {
    box-shadow: 0 0 0 0 transparent;
  }

  50% {
    box-shadow: 0 0 0 5px color-mix(
      in srgb,
      var(--accent) 10%,
      transparent
    );
  }
}

@media (max-width: 620px) {
  .wellness-reminder-bar {
    align-items: flex-start;
    padding: var(--space-2) var(--space-3);
  }

  .wellness-reminder-bar__copy > span {
    align-items: flex-start;
    flex-direction: column;
    gap: 1px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wellness-reminder-bar__icon {
    animation: none;
  }
}
</style>
