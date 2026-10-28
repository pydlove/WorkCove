<script setup lang="ts">
import {
  ChevronRight,
  Clock3,
  Coffee,
  DoorOpen,
  Droplets,
  Footprints,
  LogIn,
  LogOut,
  SunMedium,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue'

import WorkTimeCurve from '@/components/business/workbench/WorkTimeCurve.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { useWellnessStore } from '@/stores/useWellnessStore'
import type {
  WellnessActionType,
  WellnessRestSession,
} from '@/types/wellness'
import {
  formatWellnessDuration,
  localDateKey,
  wellnessDaySummary,
  wellnessHistoryDates,
  wellnessTimeline,
  type WellnessTimelineEntry,
} from '@/utils/wellness'

type WellnessHistoryView = 'today' | 'history'

const emit = defineEmits<{
  close: []
}>()

const wellness = useWellnessStore()
const view = ref<WellnessHistoryView>('today')
const viewItems = [
  { value: 'today', label: '今日' },
  { value: 'history', label: '历史' },
]
const now = ref(new Date())
const today = computed(() => localDateKey(now.value))
const todaySummary = computed(() =>
  wellnessDaySummary(wellness.data, today.value, now.value),
)
const todayTimeline = computed(() =>
  wellnessTimeline(todaySummary.value),
)
const historyItems = computed(() =>
  wellnessHistoryDates(wellness.data, 30).map((date) => ({
    date,
    summary: wellnessDaySummary(
      wellness.data,
      date,
      new Date(`${date}T23:59:59`),
    ),
  })),
)
let timer: number | null = null

function actionIcon(type: WellnessActionType) {
  if (type === 'water') return Droplets
  if (type === 'restroom') return DoorOpen
  return Footprints
}

function actionLabel(type: WellnessActionType) {
  if (type === 'water') return '喝了一杯水'
  if (type === 'restroom') return '去了洗手间'
  return '起身活动'
}

function timelineIcon(entry: WellnessTimelineEntry) {
  if (entry.kind === 'rest') return Coffee
  if (entry.kind === 'clockIn' || entry.kind === 'clockOut') {
    return entry.kind === 'clockIn' ? LogIn : LogOut
  }
  return actionIcon(entry.action.type)
}

function timelineLabel(entry: WellnessTimelineEntry) {
  if (entry.kind === 'rest') return '休息'
  if (entry.kind === 'clockIn' || entry.kind === 'clockOut') {
    return entry.kind === 'clockIn' ? '打卡上班' : '下班'
  }
  return actionLabel(entry.action.type)
}

function timelineDetail(entry: WellnessTimelineEntry) {
  return entry.kind === 'rest'
    ? formatWellnessDuration(entry.rest.durationMs)
    : ''
}

function timelineKey(entry: WellnessTimelineEntry) {
  if (entry.kind === 'rest') return `rest-${entry.rest.id}`
  if (entry.kind === 'clockIn' || entry.kind === 'clockOut') {
    return `${entry.kind}-${entry.workId}`
  }
  return `action-${entry.action.id}`
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${value}T12:00:00`))
}

function timelineForDate(date: string) {
  return wellnessTimeline(
    wellnessDaySummary(
      wellness.data,
      date,
      new Date(`${date}T23:59:59`),
    ),
  )
}

function restLabel(rest: WellnessRestSession) {
  return `${formatTime(rest.startedAt)} - ${formatTime(rest.endedAt)} · ${formatWellnessDuration(rest.durationMs)}`
}

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date()
  }, 30_000)
})

onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <UiDialog
    title="关怀记录"
    description="查看工作时间曲线，以及最近 30 天的喝水、活动和休息记录。"
    size="wide"
    scroll-mode="body"
    @close="emit('close')"
  >
    <div class="wellness-history">
      <UiSegmentedControl
        v-model="view"
        :items="viewItems"
        aria-label="关怀记录范围"
      />

      <template v-if="view === 'today'">
        <div class="wellness-history__summary">
          <span>
            <SunMedium :size="15" />
            <small>今日工作</small>
            <strong>{{ formatWellnessDuration(todaySummary.focusMs) }}</strong>
          </span>
          <span>
            <Droplets :size="15" />
            <small>喝水</small>
            <strong>{{ todaySummary.water }} 杯</strong>
          </span>
          <span>
            <Footprints :size="15" />
            <small>活动</small>
            <strong>{{ todaySummary.movements }} 次</strong>
          </span>
          <span>
            <Coffee :size="15" />
            <small>休息</small>
            <strong>{{ formatWellnessDuration(todaySummary.restMs) }}</strong>
          </span>
        </div>

        <WorkTimeCurve
          :data="wellness.data"
          :date="today"
        />

        <section class="wellness-history__section">
          <header>
            <span>今天的记录</span>
            <small>{{ todayTimeline.length }} 项</small>
          </header>
          <div v-if="todayTimeline.length" class="wellness-timeline">
            <article
              v-for="entry in todayTimeline"
              :key="timelineKey(entry)"
              class="wellness-timeline__row"
            >
              <span class="wellness-timeline__icon">
                <component :is="timelineIcon(entry)" :size="14" />
              </span>
              <span class="wellness-timeline__copy">
                <strong>{{ timelineLabel(entry) }}</strong>
                <small v-if="timelineDetail(entry)">
                  {{ timelineDetail(entry) }}
                </small>
              </span>
              <time :datetime="entry.at">{{ formatTime(entry.at) }}</time>
            </article>
          </div>
          <div v-else class="wellness-history__empty">
            今天还没有关怀记录，完成后点一下对应按钮即可。
          </div>
        </section>

        <section
          v-if="todaySummary.rests.length"
          class="wellness-history__section"
        >
          <header>
            <span>休息时段</span>
            <small>{{ todaySummary.rests.length }} 次</small>
          </header>
          <div class="wellness-rest-list">
            <p
              v-for="rest in todaySummary.rests"
              :key="rest.id"
            >
              <Coffee :size="13" />
              <span>{{ restLabel(rest) }}</span>
            </p>
          </div>
        </section>
      </template>

      <template v-else>
        <div v-if="historyItems.length" class="wellness-history__days">
          <details
            v-for="item in historyItems"
            :key="item.date"
            class="wellness-history-day"
          >
            <summary>
              <span class="wellness-history-day__date">
                <strong>{{ formatDate(item.date) }}</strong>
                <small>
                  工作 {{ formatWellnessDuration(item.summary.focusMs) }} ·
                  {{ item.summary.water }} 杯水 ·
                  {{ item.summary.movements }} 次活动 ·
                  休息 {{ formatWellnessDuration(item.summary.restMs) }}
                </small>
              </span>
              <ChevronRight
                class="wellness-history-day__chevron"
                :size="14"
                aria-hidden="true"
              />
            </summary>
            <WorkTimeCurve
              v-if="item.summary.workSessions.length"
              :data="wellness.data"
              :date="item.date"
              compact
            />
            <div
              v-if="timelineForDate(item.date).length"
              class="wellness-timeline"
            >
              <article
                v-for="entry in timelineForDate(item.date)"
                :key="timelineKey(entry)"
                class="wellness-timeline__row"
              >
                <span class="wellness-timeline__icon">
                  <component :is="timelineIcon(entry)" :size="14" />
                </span>
                <span class="wellness-timeline__copy">
                  <strong>{{ timelineLabel(entry) }}</strong>
                  <small v-if="timelineDetail(entry)">
                    {{ timelineDetail(entry) }}
                  </small>
                </span>
                <time :datetime="entry.at">{{ formatTime(entry.at) }}</time>
              </article>
            </div>
          </details>
        </div>
        <div v-else class="wellness-history__empty">
          还没有历史记录，今天的关怀动作会从明天开始出现在这里。
        </div>
      </template>
    </div>

    <template #footer>
      <div class="wellness-history__footer">
        <span>
          <Clock3 :size="13" />
          记录只对当前账号可见
        </span>
        <UiButton size="lg" @click="emit('close')">关闭</UiButton>
      </div>
    </template>
  </UiDialog>
</template>

<style scoped>
.wellness-history {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.wellness-history__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: var(--space-3) 0;
  background: var(--surface-secondary);
  border-radius: var(--radius-panel);
}

.wellness-history__summary > span {
  display: grid;
  min-width: 0;
  grid-template-columns: 18px minmax(0, 1fr);
  grid-template-rows: auto auto;
  align-items: center;
  gap: 1px var(--space-1);
  padding: 0 var(--space-3);
}

.wellness-history__summary > span + span {
  border-left: 1px solid var(--line-soft);
}

.wellness-history__summary svg {
  grid-row: 1 / 3;
  color: var(--icon);
}

.wellness-history__summary small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-history__summary strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-history__section {
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.wellness-history__section > header {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  border-bottom: 1px solid var(--line-soft);
}

.wellness-history__section > header small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.wellness-timeline {
  display: flex;
  flex-direction: column;
}

.wellness-timeline__row {
  display: grid;
  min-height: 46px;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  border-bottom: 1px solid var(--line-soft);
}

.wellness-timeline__row:last-child {
  border-bottom: 0;
}

.wellness-timeline__icon {
  display: grid;
  width: 26px;
  height: 26px;
  color: var(--icon);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-timeline__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.wellness-timeline__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.wellness-timeline__copy small,
.wellness-timeline__row time {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.wellness-timeline__row time {
  font-family: var(--font-mono);
}

.wellness-rest-list {
  display: flex;
  flex-direction: column;
}

.wellness-rest-list p {
  display: flex;
  min-height: 40px;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  border-bottom: 1px solid var(--line-soft);
}

.wellness-rest-list p:last-child {
  border-bottom: 0;
}

.wellness-history__days {
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.wellness-history-day {
  border-bottom: 1px solid var(--line-soft);
}

.wellness-history-day:last-child {
  border-bottom: 0;
}

.wellness-history-day > summary {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  color: var(--text-muted);
  cursor: pointer;
  list-style: none;
}

.wellness-history-day > summary::-webkit-details-marker {
  display: none;
}

.wellness-history-day > summary:hover {
  background: var(--hover-bg);
}

.wellness-history-day__date {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.wellness-history-day__date strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.wellness-history-day__date small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-history-day__chevron {
  flex: 0 0 auto;
  color: var(--text-subtle);
  transition: transform var(--duration-fast) var(--ease-apple);
}

.wellness-history-day[open] .wellness-history-day__chevron {
  transform: rotate(90deg);
}

.wellness-history__empty {
  display: grid;
  min-height: 140px;
  padding: var(--space-5);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  place-items: center;
}

.wellness-history__footer {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.wellness-history__footer > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .wellness-history {
    padding: 0 var(--space-4) var(--space-4);
  }

  .wellness-history__summary {
    grid-template-columns: 1fr 1fr;
    gap: var(--space-3) 0;
  }

  .wellness-history__summary > span:nth-child(3) {
    border-left: 0;
  }

  .wellness-history__footer > span {
    display: none;
  }

  .wellness-history__footer {
    justify-content: flex-end;
  }
}
</style>
