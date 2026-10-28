<script setup lang="ts">
import {
  CircleAlert,
  Clock3,
  Coffee,
  DoorOpen,
  Droplets,
  Footprints,
  History,
  Settings2,
  ShieldCheck,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import WellnessHistoryDialog from '@/components/business/workbench/WellnessHistoryDialog.vue'
import WellnessReminderSettingsDialog from '@/components/business/workbench/WellnessReminderSettingsDialog.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useWellnessStore } from '@/stores/useWellnessStore'
import type { WellnessActionType } from '@/types/wellness'
import {
  formatWellnessDuration,
  localDateKey,
  wellnessDaySummary,
} from '@/utils/wellness'

const emit = defineEmits<{
  notify: [message: string]
}>()

const WATER_GOAL = 8
const MOVEMENT_GOAL = 6
const wellnessHelp = computed(() => ({
  water: {
    title: '喝水提醒',
    risk:
      '长期饮水不足可能引起口干、注意力下降、便秘，并增加泌尿系统结石的风险。',
    advice:
      `建议每 ${wellness.data.reminderSettings.waterMinutes} 分钟少量补水；运动、出汗或天气炎热时适当增加。`,
  },
  stand: {
    title: '工位活动',
    risk:
      '久坐不动可能导致腰颈僵硬、肌肉紧张、血液循环变慢，并增加静脉血栓和代谢问题的风险。',
    advice:
      `建议每 ${wellness.data.reminderSettings.movementMinutes} 分钟做一次扭腰、伸展或肩颈放松等原地活动。`,
  },
  restroom: {
    title: '及时如厕',
    risk:
      '长期憋尿可能增加尿路感染、膀胱不适和肾脏负担的风险。',
    advice:
      `建议设置 ${wellness.data.reminderSettings.restroomMinutes} 分钟作为温和提示；有尿意就及时去，不要强忍。`,
  },
}))

const auth = useAuthStore()
const wellness = useWellnessStore()
const now = ref(new Date())
const historyOpen = ref(false)
const reminderSettingsOpen = ref(false)
let clockTimer: number | null = null

const today = computed(() => localDateKey(now.value))
const summary = computed(() =>
  wellnessDaySummary(wellness.data, today.value, now.value),
)
const focusLabel = computed(() => {
  if (!summary.value.clockInAt) return '0 分钟'
  return summary.value.focusMs < 60_000
    ? '刚刚开始'
    : formatWellnessDuration(summary.value.focusMs)
})
const restLabel = computed(() =>
  summary.value.restMs < 60_000
    ? '还没有休息'
    : formatWellnessDuration(summary.value.restMs),
)
const waterProgress = computed(() =>
  Math.min(100, (summary.value.water / WATER_GOAL) * 100),
)
const movementProgress = computed(() =>
  Math.min(100, (summary.value.movements / MOVEMENT_GOAL) * 100),
)

watch(
  () => auth.email,
  (email) => wellness.load(email),
  { immediate: true },
)

function recordAction(type: WellnessActionType) {
  wellness.recordAction(type)
  const message =
    type === 'water'
      ? '已记录一杯水'
      : type === 'restroom'
        ? '已记录一次洗手间'
        : '已记录一次工位活动'
  emit('notify', message)
}

function startRest() {
  if (!wellness.startRest()) return
  emit('notify', '休息计时开始，先离开屏幕一会儿')
}

onMounted(() => {
  clockTimer = window.setInterval(() => {
    now.value = new Date()
    wellness.ensureDay(now.value)
    wellness.closeStaleSessions(now.value)
  }, 30_000)
})

onBeforeUnmount(() => {
  if (clockTimer !== null) window.clearInterval(clockTimer)
})
</script>

<template>
  <section class="workbench-wellness" aria-label="今日关怀">
    <div class="workbench-wellness__body">
      <div class="wellness-actions">
        <button
          class="wellness-action"
          type="button"
          aria-label="记录喝了一杯水"
          aria-describedby="wellness-water-help"
          @click="recordAction('water')"
        >
          <span class="wellness-action__icon">
            <Droplets :size="14" />
          </span>
          <span class="wellness-action__copy">
            <span class="wellness-action__line">
              <small>喝水</small>
              <strong>{{ summary.water }} / {{ WATER_GOAL }}</strong>
            </span>
            <span class="wellness-progress is-water" aria-hidden="true">
              <i :style="{ width: `${waterProgress}%` }" />
            </span>
          </span>
          <span class="wellness-action__add">+1</span>
          <span
            id="wellness-water-help"
            class="wellness-action__tooltip"
            role="tooltip"
          >
            <span class="wellness-action__tooltip-heading">
              <span class="wellness-action__tooltip-icon">
                <Droplets :size="15" />
              </span>
              <span>
                <em>健康提醒</em>
                <strong>{{ wellnessHelp.water.title }}</strong>
              </span>
            </span>
            <p class="wellness-action__tooltip-risk">
              <CircleAlert :size="14" />
              <span>{{ wellnessHelp.water.risk }}</span>
            </p>
            <p class="wellness-action__tooltip-advice">
              <ShieldCheck :size="14" />
              <span>
                <strong>建议</strong>
                {{ wellnessHelp.water.advice }}
              </span>
            </p>
          </span>
        </button>
        <button
          class="wellness-action"
          type="button"
          aria-label="记录一次工位活动"
          aria-describedby="wellness-stand-help"
          @click="recordAction('stand')"
        >
          <span class="wellness-action__icon">
            <Footprints :size="14" />
          </span>
          <span class="wellness-action__copy">
            <span class="wellness-action__line">
              <small>活动</small>
              <strong>{{ summary.movements }} / {{ MOVEMENT_GOAL }}</strong>
            </span>
            <span class="wellness-progress is-movement" aria-hidden="true">
              <i :style="{ width: `${movementProgress}%` }" />
            </span>
          </span>
          <span class="wellness-action__add">+1</span>
          <span
            id="wellness-stand-help"
            class="wellness-action__tooltip"
            role="tooltip"
          >
            <span class="wellness-action__tooltip-heading">
              <span class="wellness-action__tooltip-icon">
                <Footprints :size="15" />
              </span>
              <span>
                <em>健康提醒</em>
                <strong>{{ wellnessHelp.stand.title }}</strong>
              </span>
            </span>
            <p class="wellness-action__tooltip-risk">
              <CircleAlert :size="14" />
              <span>{{ wellnessHelp.stand.risk }}</span>
            </p>
            <p class="wellness-action__tooltip-advice">
              <ShieldCheck :size="14" />
              <span>
                <strong>建议</strong>
                {{ wellnessHelp.stand.advice }}
              </span>
            </p>
          </span>
        </button>
        <button
          class="wellness-action"
          type="button"
          aria-label="记录去洗手间"
          aria-describedby="wellness-restroom-help"
          @click="recordAction('restroom')"
        >
          <span class="wellness-action__icon">
            <DoorOpen :size="14" />
          </span>
          <span class="wellness-action__copy">
            <span class="wellness-action__line">
              <small>洗手间</small>
              <strong>{{ summary.restroom }} 次</strong>
              <span class="wellness-action__frequency">按需</span>
            </span>
          </span>
          <span class="wellness-action__add">+1</span>
          <span
            id="wellness-restroom-help"
            class="wellness-action__tooltip"
            role="tooltip"
          >
            <span class="wellness-action__tooltip-heading">
              <span class="wellness-action__tooltip-icon">
                <DoorOpen :size="15" />
              </span>
              <span>
                <em>健康提醒</em>
                <strong>{{ wellnessHelp.restroom.title }}</strong>
              </span>
            </span>
            <p class="wellness-action__tooltip-risk">
              <CircleAlert :size="14" />
              <span>{{ wellnessHelp.restroom.risk }}</span>
            </p>
            <p class="wellness-action__tooltip-advice">
              <ShieldCheck :size="14" />
              <span>
                <strong>建议</strong>
                {{ wellnessHelp.restroom.advice }}
              </span>
            </p>
          </span>
        </button>
      </div>

      <footer class="workbench-wellness__footer">
        <div class="wellness-summary">
          <span>
            <Clock3 :size="13" />
            <small>今日工作</small>
            <strong>{{ focusLabel }}</strong>
          </span>
          <span>
            <Coffee :size="13" />
            <small>今日休息</small>
            <strong :class="{ 'is-alert': summary.restMs < 60_000 }">
              {{ restLabel }}
            </strong>
          </span>
        </div>
        <div class="workbench-wellness__actions">
          <span class="wellness-footer-action">
            <button
              class="workbench-wellness__reminder-settings"
              type="button"
              aria-label="提醒设置"
              aria-describedby="wellness-footer-settings-tip"
              @click="reminderSettingsOpen = true"
            >
              <Settings2 :size="14" />
            </button>
            <span
              id="wellness-footer-settings-tip"
              class="wellness-footer-action__tooltip"
              role="tooltip"
            >
              提醒设置
            </span>
          </span>
          <span class="wellness-footer-action">
            <UiButton
              class="wellness-footer-action__icon"
              aria-label="记录健康数据"
              aria-describedby="wellness-footer-history-tip"
              @click="historyOpen = true"
            >
              <History :size="14" />
            </UiButton>
            <span
              id="wellness-footer-history-tip"
              class="wellness-footer-action__tooltip"
              role="tooltip"
            >
              记录
            </span>
          </span>
          <span class="wellness-footer-action">
            <UiButton
              class="wellness-footer-action__icon"
              variant="primary"
              aria-label="休息一下"
              aria-describedby="wellness-footer-rest-tip"
              :disabled="Boolean(wellness.data.activeRest)"
              @click="startRest"
            >
              <Coffee :size="14" />
            </UiButton>
            <span
              id="wellness-footer-rest-tip"
              class="wellness-footer-action__tooltip"
              role="tooltip"
            >
              休息一下
            </span>
          </span>
        </div>
      </footer>
    </div>

    <WellnessHistoryDialog
      v-if="historyOpen"
      @close="historyOpen = false"
    />

    <WellnessReminderSettingsDialog
      v-if="reminderSettingsOpen"
      @close="reminderSettingsOpen = false"
      @saved="emit('notify', '健康提醒间隔已保存')"
    />

  </section>
</template>

<style scoped>
.workbench-wellness {
  display: flex;
  min-width: 0;
  max-width: 520px;
  align-items: flex-start;
  flex-direction: column;
}

.workbench-wellness__body {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
}

.wellness-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  background: transparent;
}

.wellness-action {
  --wellness-tone: var(--accent);

  position: relative;
  display: grid;
  min-width: 0;
  min-height: 44px;
  grid-template-columns: 16px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: var(--radius-badge);
  transition: background var(--duration-fast) var(--ease-apple);
}

.wellness-action + .wellness-action::before {
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: 0;
  width: 1px;
  content: "";
  background: var(--line-soft);
}

.wellness-action:hover {
  background: var(--hover-bg);
}

.wellness-action:hover,
.wellness-action:focus-visible {
  z-index: 50;
}

.wellness-action__icon {
  display: grid;
  width: 16px;
  height: 16px;
  color: var(--icon);
  background: transparent;
  place-items: center;
}

.wellness-action__copy {
  display: flex;
  min-width: 0;
  align-items: stretch;
  flex-direction: column;
  gap: 5px;
}

.wellness-action__line {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: flex-start;
  gap: 5px;
  overflow: hidden;
  text-align: left;
}

.wellness-action small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-action__line strong {
  flex: 0 0 auto;
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-progress {
  display: block;
  width: 100%;
  height: 3px;
  overflow: hidden;
  background: var(--line-soft);
  border-radius: var(--radius-badge);
}

.wellness-progress i {
  display: block;
  height: 100%;
  background: var(--accent);
  border-radius: inherit;
  transition: width var(--duration-fast) var(--ease-apple);
}

.wellness-action__frequency {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-action__add {
  display: grid;
  min-width: 18px;
  height: 18px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: transparent;
  place-items: center;
}

.wellness-action__tooltip {
  position: absolute;
  z-index: 40;
  top: calc(100% + 8px);
  left: 0;
  width: min(330px, calc(100vw - 40px));
  padding: var(--space-3) var(--space-4);
  color: var(--text-muted);
  text-align: left;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-left: 3px solid var(--wellness-tone);
  border-radius: var(--radius-control);
  box-shadow:
    0 12px 30px rgb(0 0 0 / 16%),
    var(--dropdown-shadow);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-4px);
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.wellness-action:last-child .wellness-action__tooltip {
  right: 0;
  left: auto;
}

.wellness-action:hover .wellness-action__tooltip,
.wellness-action:focus-visible .wellness-action__tooltip {
  opacity: 1;
  transform: translateY(0);
}

.wellness-action__tooltip-heading {
  display: flex;
  align-items: center;
  gap: 9px;
}

.wellness-action__tooltip-heading > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.wellness-action__tooltip-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  color: var(--wellness-tone);
  background: color-mix(
    in srgb,
    var(--wellness-tone) 12%,
    var(--canvas-raised)
  );
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-action__tooltip-heading em {
  color: var(--wellness-tone);
  font-size: var(--font-caption);
  font-style: normal;
  font-weight: var(--weight-emphasis);
  line-height: var(--line-caption);
}

.wellness-action__tooltip-heading strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-strong);
  line-height: var(--line-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-action__tooltip-risk,
.wellness-action__tooltip-advice {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  align-items: flex-start;
  gap: 7px;
  margin: var(--space-3) 0 0;
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.wellness-action__tooltip-risk {
  color: var(--text);
}

.wellness-action__tooltip-risk svg,
.wellness-action__tooltip-advice svg {
  margin-top: 1px;
  color: var(--wellness-tone);
}

.wellness-action__tooltip-advice {
  padding: var(--space-2);
  color: var(--text);
  background: color-mix(
    in srgb,
    var(--wellness-tone) 9%,
    var(--canvas-raised)
  );
  border: 1px solid color-mix(
    in srgb,
    var(--wellness-tone) 16%,
    transparent
  );
  border-radius: var(--radius-badge);
}

.wellness-action__tooltip-advice > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.wellness-action__tooltip-advice strong {
  color: var(--wellness-tone);
  font-family: var(--font-sans);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.workbench-wellness__footer {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.workbench-wellness__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.wellness-footer-action {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.wellness-footer-action:hover,
.wellness-footer-action:focus-within {
  z-index: 50;
}

.wellness-footer-action__icon {
  width: 28px;
  min-width: 28px;
  height: 28px;
  padding: 0;
  gap: 0;
}

.workbench-wellness__reminder-settings {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-muted);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  place-items: center;
}

.workbench-wellness__reminder-settings:hover {
  color: var(--accent);
  background: var(--accent-soft);
}

.wellness-footer-action__tooltip {
  position: absolute;
  z-index: 60;
  bottom: calc(100% + 7px);
  left: 50%;
  padding: 4px 8px;
  color: #f5f7f4;
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  white-space: nowrap;
  background: rgb(31 41 38 / 94%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: var(--radius-badge);
  box-shadow: var(--shadow-popover);
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 4px);
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.wellness-footer-action:hover .wellness-footer-action__tooltip,
.wellness-footer-action:focus-within .wellness-footer-action__tooltip {
  opacity: 1;
  transform: translate(-50%, 0);
}

.wellness-summary {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
  gap: var(--space-4);
  padding: 0 var(--space-2);
  flex-wrap: wrap;
}

.wellness-summary > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.wellness-summary svg {
  color: var(--icon);
}

.wellness-summary small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.wellness-summary strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-summary strong.is-alert {
  color: var(--system-red);
}

@media (max-width: 760px) {
  .wellness-summary {
    flex-direction: row;
    justify-content: flex-start;
    gap: var(--space-4);
    flex-wrap: wrap;
  }
}

@media (max-width: 620px) {
  .wellness-actions {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .wellness-action {
    min-height: 42px;
    grid-template-columns: 16px minmax(0, 1fr) 18px;
    gap: var(--space-2);
    padding: var(--space-2);
  }

  .wellness-action__icon {
    width: 16px;
    height: 16px;
  }

  .wellness-action + .wellness-action::before {
    top: 0;
    right: var(--space-2);
    bottom: auto;
    left: var(--space-2);
    width: auto;
    height: 1px;
  }

  .wellness-summary {
    gap: var(--space-3);
  }

  .workbench-wellness__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .workbench-wellness__actions {
    justify-content: flex-end;
  }
}
</style>
