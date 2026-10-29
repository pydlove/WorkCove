<script setup lang="ts">
import {
  Clock3,
  DoorOpen,
  Droplets,
  Footprints,
  LogIn,
  LogOut,
} from '@lucide/vue'
import {
  reactive,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import { useWellnessStore } from '@/stores/useWellnessStore'
import type { WellnessWorkdaySchedule } from '@/types/wellness'

const emit = defineEmits<{
  close: []
  saved: []
}>()

const wellness = useWellnessStore()
const errorMessage = ref('')
const weekdayNames: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}
const form = reactive({
  clockInReminder:
    wellness.data.reminderSettings.clockInReminder,
  clockOutReminder:
    wellness.data.reminderSettings.clockOutReminder,
  reminderMinutesBefore: String(
    wellness.data.reminderSettings.reminderMinutesBefore,
  ),
  workdays: wellness.data.reminderSettings.workdays.map(
    (item): WellnessWorkdaySchedule => ({ ...item }),
  ),
  waterMinutes: String(
    wellness.data.reminderSettings.waterMinutes,
  ),
  movementMinutes: String(
    wellness.data.reminderSettings.movementMinutes,
  ),
  restroomMinutes: String(
    wellness.data.reminderSettings.restroomMinutes,
  ),
})

function validMinutes(value: string) {
  const minutes = Number(value)
  return (
    Number.isFinite(minutes) &&
    minutes >= 5 &&
    minutes <= 480
  )
}

function validTime(value: string) {
  return /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)
}

function timeMinutes(value: string) {
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

function save() {
  errorMessage.value = ''
  if (
    !validMinutes(form.waterMinutes) ||
    !validMinutes(form.movementMinutes) ||
    !validMinutes(form.restroomMinutes)
  ) {
    errorMessage.value = '提醒间隔需要设置为 5–480 分钟'
    return
  }
  const reminderMinutes = Number(form.reminderMinutesBefore)
  if (
    !Number.isFinite(reminderMinutes) ||
    reminderMinutes < 0 ||
    reminderMinutes > 120
  ) {
    errorMessage.value = '上下班提醒需要提前 0–120 分钟'
    return
  }
  for (const day of form.workdays) {
    if (
      !validTime(day.startTime) ||
      !validTime(day.endTime)
    ) {
      errorMessage.value = `${weekdayNames[day.day]}的时间格式不正确`
      return
    }
    if (
      day.enabled &&
      timeMinutes(day.endTime) <= timeMinutes(day.startTime)
    ) {
      errorMessage.value = `${weekdayNames[day.day]}的下班时间需要晚于上班时间`
      return
    }
  }

  wellness.updateReminderSettings({
    clockInReminder: form.clockInReminder,
    clockOutReminder: form.clockOutReminder,
    reminderMinutesBefore: Number(form.reminderMinutesBefore),
    workdays: form.workdays.map((item) => ({ ...item })),
    waterMinutes: Number(form.waterMinutes),
    movementMinutes: Number(form.movementMinutes),
    restroomMinutes: Number(form.restroomMinutes),
  })
  emit('saved')
  emit('close')
}
</script>

<template>
  <UiDialog
    title="提醒设置"
    description="按星期安排上下班时间，并设置打卡与健康提醒。"
    size="wide"
    height="tall"
    scroll-mode="body"
    max-height="min(820px, calc(100dvh - 32px))"
    initial-focus="#wellness-monday-start"
    @close="emit('close')"
  >
    <form
      id="wellness-reminder-settings"
      class="wellness-reminder-settings"
      @submit.prevent="save"
    >
      <section class="wellness-work-schedule">
        <header>
          <span class="wellness-work-schedule__heading">
            <Clock3 :size="16" />
            <span>
              <strong>上下班时间与打卡提醒</strong>
              <small>
                每天可单独设置；例如周二、周四 21:00 下班，周三、周五 09:30 上班。
              </small>
            </span>
          </span>
        </header>

        <div class="wellness-work-schedule__options">
          <label>
            <UiCheckbox
              v-model="form.clockInReminder"
              label="开启上班打卡提醒"
            />
            <LogIn :size="14" />
            上班打卡提醒
          </label>
          <label>
            <UiCheckbox
              v-model="form.clockOutReminder"
              label="开启下班打卡提醒"
            />
            <LogOut :size="14" />
            下班打卡提醒
          </label>
          <label class="wellness-work-schedule__lead">
            <span>提前</span>
            <UiInput
              v-model="form.reminderMinutesBefore"
              type="number"
              min="0"
              max="120"
              step="5"
              aria-label="上下班提醒提前分钟数"
            />
            <span>分钟</span>
          </label>
        </div>

        <div class="wellness-workdays">
          <div
            v-for="(day, index) in form.workdays"
            :key="day.day"
            class="wellness-workday"
            :class="{ 'is-disabled': !day.enabled }"
          >
            <UiCheckbox
              v-model="day.enabled"
              :label="`${weekdayNames[day.day]}设置为工作日`"
            />
            <strong>{{ weekdayNames[day.day] }}</strong>
            <label>
              <span>上班</span>
              <UiInput
                :id="`wellness-${day.day === 1 ? 'monday' : `day-${day.day}`}-start`"
                v-model="day.startTime"
                type="time"
                step="60"
                :disabled="!day.enabled"
                :aria-label="`${weekdayNames[day.day]}上班时间`"
              />
            </label>
            <span class="wellness-workday__separator">至</span>
            <label>
              <span>下班</span>
              <UiInput
                :id="`wellness-day-${day.day}-end`"
                v-model="day.endTime"
                type="time"
                step="60"
                :disabled="!day.enabled"
                :aria-label="`${weekdayNames[day.day]}下班时间`"
              />
            </label>
          </div>
        </div>
      </section>

      <p class="wellness-reminder-section-title">健康提醒</p>
      <p class="wellness-reminder-section-hint">
        喝水和工位活动提醒仅在对应工作日的上下班时间内触发。
      </p>

      <label class="wellness-reminder-rule">
        <span class="wellness-reminder-rule__icon">
          <Droplets :size="16" />
        </span>
        <span class="wellness-reminder-rule__copy">
          <strong>喝水提醒</strong>
          <small>建议少量多次补水，默认 60 分钟。</small>
        </span>
        <span class="wellness-reminder-rule__input">
          <UiInput
            id="wellness-water-interval"
            v-model="form.waterMinutes"
            type="number"
            min="5"
            max="480"
            step="5"
            aria-label="喝水提醒间隔分钟数"
          />
          <small>分钟</small>
        </span>
      </label>

      <label class="wellness-reminder-rule">
        <span class="wellness-reminder-rule__icon">
          <Footprints :size="16" />
        </span>
        <span class="wellness-reminder-rule__copy">
          <strong>工位活动提醒</strong>
          <small>建议扭腰、伸展或肩颈放松，默认 90 分钟。</small>
        </span>
        <span class="wellness-reminder-rule__input">
          <UiInput
            id="wellness-movement-interval"
            v-model="form.movementMinutes"
            type="number"
            min="5"
            max="480"
            step="5"
            aria-label="工位活动提醒间隔分钟数"
          />
          <small>分钟</small>
        </span>
      </label>

      <label class="wellness-reminder-rule">
        <span class="wellness-reminder-rule__icon">
          <DoorOpen :size="16" />
        </span>
        <span class="wellness-reminder-rule__copy">
          <strong>如厕提示</strong>
          <small>仅作长时间未如厕的温和提示，有尿意应及时去。</small>
        </span>
        <span class="wellness-reminder-rule__input">
          <UiInput
            id="wellness-restroom-interval"
            v-model="form.restroomMinutes"
            type="number"
            min="5"
            max="480"
            step="5"
            aria-label="如厕提醒间隔分钟数"
          />
          <small>分钟</small>
        </span>
      </label>

      <p
        v-if="errorMessage"
        class="wellness-reminder-settings__error"
        role="alert"
      >
        {{ errorMessage }}
      </p>
    </form>

    <template #footer>
      <UiButton size="lg" @click="emit('close')">
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="wellness-reminder-settings"
      >
        保存设置
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.wellness-reminder-settings {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-1) var(--space-5) var(--space-4);
}

.wellness-work-schedule {
  display: flex;
  padding: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  flex-direction: column;
  gap: var(--space-3);
}

.wellness-work-schedule > header,
.wellness-work-schedule__heading,
.wellness-work-schedule__options,
.wellness-work-schedule__options > label,
.wellness-work-schedule__lead,
.wellness-workday,
.wellness-workday > label {
  display: flex;
  align-items: center;
}

.wellness-work-schedule__heading {
  gap: var(--space-2);
  color: var(--accent);
}

.wellness-work-schedule__heading > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.wellness-work-schedule__heading strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.wellness-work-schedule__heading small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.wellness-work-schedule__options {
  gap: var(--space-4);
  flex-wrap: wrap;
}

.wellness-work-schedule__options > label {
  gap: 6px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.wellness-work-schedule__lead {
  gap: 5px;
  margin-left: auto;
}

.wellness-work-schedule__lead :deep(.ui-input) {
  width: 58px;
  text-align: center;
}

.wellness-workdays {
  display: flex;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  flex-direction: column;
}

.wellness-workday {
  min-height: 42px;
  gap: var(--space-2);
  padding: 4px var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.wellness-workday:last-child {
  border-bottom: 0;
}

.wellness-workday.is-disabled {
  background: var(--surface-inset);
}

.wellness-workday > strong {
  width: 36px;
  flex: 0 0 auto;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.wellness-workday > label {
  min-width: 0;
  flex: 1;
  gap: 5px;
}

.wellness-workday > label > span {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.wellness-workday :deep(.ui-input) {
  min-width: 92px;
  height: var(--control-sm);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.wellness-workday__separator {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.wellness-reminder-section-title {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.wellness-reminder-section-hint {
  margin: calc(-1 * var(--space-1)) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.wellness-reminder-rule {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 104px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.wellness-reminder-rule__icon {
  display: grid;
  width: 32px;
  height: 32px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-reminder-rule__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.wellness-reminder-rule__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.wellness-reminder-rule__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.wellness-reminder-rule__input {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.wellness-reminder-rule__input small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.wellness-reminder-settings__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .wellness-reminder-settings {
    padding: 0 var(--space-4) var(--space-4);
  }

  .wellness-reminder-rule {
    grid-template-columns: 32px minmax(0, 1fr);
  }

  .wellness-work-schedule__lead {
    width: 100%;
    margin-left: calc(18px + 6px);
  }

  .wellness-workday {
    display: grid;
    grid-template-columns: 18px 36px minmax(0, 1fr);
  }

  .wellness-workday > label {
    grid-column: 3;
  }

  .wellness-workday__separator {
    display: none;
  }

  .wellness-workday > label + label {
    padding-bottom: 4px;
  }

  .wellness-reminder-rule__input {
    grid-column: 1 / -1;
    padding-left: calc(32px + var(--space-3));
  }
}
</style>
