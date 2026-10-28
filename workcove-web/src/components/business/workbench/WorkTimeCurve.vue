<script setup lang="ts">
import { Activity, Coffee } from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
} from 'vue'

import type { WellnessData } from '@/types/wellness'
import {
  formatWellnessDuration,
  workTimeCurve,
} from '@/utils/wellness'

const props = withDefaults(
  defineProps<{
    data: WellnessData
    date: string
    compact?: boolean
  }>(),
  {
    compact: false,
  },
)

const now = ref(new Date())
const gradientId = `work-curve-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
let timer: number | null = null

const width = 700
const height = 160
const plot = { left: 42, right: 14, top: 16, bottom: 32 }
const plotWidth = width - plot.left - plot.right
const plotHeight = height - plot.top - plot.bottom

const curve = computed(() =>
  workTimeCurve(props.data, props.date, now.value),
)
const hasWork = computed(() => curve.value.workIntervals.length > 0)
const maxMinutes = computed(() => {
  const workMinutes =
    Math.max(0, ...curve.value.points.map((point) => point.workMs)) /
    60_000
  return Math.max(15, Math.ceil(workMinutes / 15) * 15)
})
const linePoints = computed(() =>
  curve.value.points
    .map(
      (point) =>
        `${xFor(point.at).toFixed(1)},${yFor(point.workMs).toFixed(1)}`,
    )
    .join(' '),
)
const areaPath = computed(() => {
  if (curve.value.points.length === 0) return ''
  const first = curve.value.points[0]
  const last = curve.value.points.at(-1)!
  return [
    `M ${xFor(first.at).toFixed(1)} ${(plot.top + plotHeight).toFixed(1)}`,
    ...curve.value.points.map(
      (point) =>
        `L ${xFor(point.at).toFixed(1)} ${yFor(point.workMs).toFixed(1)}`,
    ),
    `L ${xFor(last.at).toFixed(1)} ${(plot.top + plotHeight).toFixed(1)}`,
    'Z',
  ].join(' ')
})
const timeTicks = computed(() => {
  const count = props.compact ? 2 : 4
  if (curve.value.endAt <= curve.value.startAt) return []
  return Array.from({ length: count + 1 }, (_, index) => {
    const at =
      curve.value.startAt +
      ((curve.value.endAt - curve.value.startAt) * index) / count
    return {
      at,
      label: formatAxisTime(at),
      x: xFor(at),
    }
  })
})
const workTicks = computed(() => {
  const count = 2
  return Array.from({ length: count + 1 }, (_, index) => {
    const minutes = (maxMinutes.value * index) / count
    return {
      label: formatAxisDuration(minutes),
      y: yFor(minutes * 60_000),
    }
  })
})
const lastPoint = computed(() => curve.value.points.at(-1) ?? null)
const lastPointLabel = computed(() =>
  lastPoint.value
    ? formatWellnessDuration(lastPoint.value.workMs)
    : '',
)
const lastPointLabelX = computed(() =>
  lastPoint.value ? xFor(lastPoint.value.at) : plot.left,
)
const lastPointLabelAnchor = computed(() =>
  lastPointLabelX.value > plot.left + plotWidth - 72 ? 'end' : 'start',
)

function xFor(at: number) {
  if (curve.value.endAt <= curve.value.startAt) return plot.left
  return (
    plot.left +
    ((at - curve.value.startAt) /
      (curve.value.endAt - curve.value.startAt)) *
      plotWidth
  )
}

function yFor(workMs: number) {
  return (
    plot.top +
    plotHeight -
    (Math.max(0, workMs) / (maxMinutes.value * 60_000)) * plotHeight
  )
}

function intervalX(interval: { startAt: number; endAt: number }) {
  return xFor(interval.startAt)
}

function intervalWidth(interval: { startAt: number; endAt: number }) {
  return Math.max(1, xFor(interval.endAt) - xFor(interval.startAt))
}

function formatAxisTime(at: number) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(at))
}

function formatAxisDuration(minutes: number) {
  if (minutes < 60) return `${Math.round(minutes)}m`
  return `${Number((minutes / 60).toFixed(1))}h`
}

function formatClock(value: string | null) {
  if (!value) return '--:--'
  return formatAxisTime(new Date(value).getTime())
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
  <section
    class="work-time-curve"
    :class="{ 'work-time-curve--compact': compact }"
  >
    <header>
      <span class="work-time-curve__heading">
        <span class="work-time-curve__title">
          <Activity :size="15" />
          <strong>工作时间曲线</strong>
        </span>
        <small>曲线随净工作时长累计上升，休息期间保持水平。</small>
      </span>
      <span
        class="work-time-curve__status"
        :class="{ 'is-active': curve.active }"
      >
        <i />
        {{ curve.active ? '工作中' : hasWork ? '已下班' : '未打卡' }}
      </span>
    </header>

    <div v-if="hasWork" class="work-time-curve__body">
      <div class="work-time-curve__metrics">
        <span>
          <small>净工作时长</small>
          <strong>{{ formatWellnessDuration(curve.workMs) }}</strong>
        </span>
        <span>
          <small>休息</small>
          <strong>{{ formatWellnessDuration(curve.restMs) }}</strong>
        </span>
        <span>
          <small>打卡</small>
          <strong>{{ formatClock(curve.clockInAt) }}</strong>
        </span>
        <span>
          <small>{{ curve.active ? '当前时间' : '下班' }}</small>
          <strong>
            {{ curve.active ? formatClock(new Date(now).toISOString()) : formatClock(curve.clockOutAt) }}
          </strong>
        </span>
      </div>

      <div class="work-time-curve__chart">
        <svg
          :viewBox="`0 0 ${width} ${height}`"
          role="img"
          aria-label="累计净工作时长曲线，休息时段以背景色标出"
        >
          <defs>
            <linearGradient
              :id="gradientId"
              x1="0"
              y1="0"
              x2="0"
              y2="1"
            >
              <stop offset="0" stop-color="var(--accent)" stop-opacity="0.28" />
              <stop offset="1" stop-color="var(--accent)" stop-opacity="0.02" />
            </linearGradient>
          </defs>

          <g class="work-time-curve__grid">
            <line
              v-for="tick in workTicks"
              :key="`work-${tick.label}`"
              :x1="plot.left"
              :x2="width - plot.right"
              :y1="tick.y"
              :y2="tick.y"
            />
            <text
              v-for="tick in workTicks"
              :key="`work-label-${tick.label}`"
              :x="plot.left - 8"
              :y="tick.y + 3"
              text-anchor="end"
            >
              {{ tick.label }}
            </text>
          </g>

          <rect
            v-for="(interval, index) in curve.restIntervals"
            :key="`rest-${index}`"
            class="work-time-curve__rest-band"
            :x="intervalX(interval)"
            :y="plot.top"
            :width="intervalWidth(interval)"
            :height="plotHeight"
          />

          <path
            v-if="areaPath"
            class="work-time-curve__area"
            :d="areaPath"
            :fill="`url(#${gradientId})`"
          />
          <polyline
            v-if="linePoints"
            class="work-time-curve__line"
            :points="linePoints"
          />
          <circle
            v-if="lastPoint"
            class="work-time-curve__point"
            :cx="xFor(lastPoint.at)"
            :cy="yFor(lastPoint.workMs)"
            r="4"
          />
          <text
            v-if="lastPoint"
            class="work-time-curve__current-value"
            :x="lastPointLabelX"
            :y="Math.max(14, yFor(lastPoint.workMs) - 8)"
            :text-anchor="lastPointLabelAnchor"
          >
            {{ lastPointLabel }}
          </text>

          <g class="work-time-curve__work-band">
            <rect
              v-for="(interval, index) in curve.workIntervals"
              :key="`work-band-${index}`"
              :x="intervalX(interval)"
              :y="height - 24"
              :width="intervalWidth(interval)"
              height="5"
              rx="2.5"
            />
          </g>

          <g class="work-time-curve__axis">
            <text
              v-for="tick in timeTicks"
              :key="`time-${tick.at}`"
              :x="tick.x"
              :y="height - 8"
              text-anchor="middle"
            >
              {{ tick.label }}
            </text>
          </g>
        </svg>
      </div>

      <footer class="work-time-curve__legend">
        <span><i class="is-work" />工作</span>
        <span><Coffee :size="12" /><i class="is-rest" />休息</span>
      </footer>
    </div>

    <div v-else class="work-time-curve__empty">
      打卡上班后开始记录，休息时间会从净工作时长中扣除。
    </div>
  </section>
</template>

<style scoped>
.work-time-curve {
  display: flex;
  padding: var(--space-3);
  background: var(--surface-secondary);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
  flex-direction: column;
  gap: var(--space-3);
}

.work-time-curve header,
.work-time-curve__heading,
.work-time-curve__title,
.work-time-curve__status,
.work-time-curve__legend,
.work-time-curve__legend span {
  display: flex;
  align-items: center;
}

.work-time-curve header {
  justify-content: space-between;
  gap: var(--space-3);
}

.work-time-curve__heading {
  min-width: 0;
  align-items: flex-start;
  flex-direction: column;
  gap: 3px;
}

.work-time-curve__title {
  gap: var(--space-2);
  color: var(--text);
}

.work-time-curve__title strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.work-time-curve__heading small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.work-time-curve__status {
  flex: 0 0 auto;
  gap: 5px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.work-time-curve__status i {
  width: 6px;
  height: 6px;
  background: var(--text-subtle);
  border-radius: 50%;
}

.work-time-curve__status.is-active {
  color: var(--system-green);
}

.work-time-curve__status.is-active i {
  background: var(--system-green);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--system-green) 14%, transparent);
}

.work-time-curve__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-2);
}

.work-time-curve__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.work-time-curve__metrics span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.work-time-curve__metrics small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-time-curve__metrics strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-time-curve__chart {
  width: 100%;
  overflow: hidden;
}

.work-time-curve__chart svg {
  display: block;
  width: 100%;
  height: auto;
}

.work-time-curve__grid line {
  stroke: var(--line-soft);
  stroke-width: 1;
}

.work-time-curve__grid text,
.work-time-curve__axis text {
  fill: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.work-time-curve__rest-band {
  fill: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.work-time-curve__area {
  pointer-events: none;
}

.work-time-curve__line {
  fill: none;
  stroke: var(--accent);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.work-time-curve__point {
  fill: var(--accent);
  stroke: var(--surface-secondary);
  stroke-width: 2;
}

.work-time-curve__current-value {
  fill: var(--accent);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
}

.work-time-curve__work-band rect {
  fill: color-mix(in srgb, var(--accent) 42%, transparent);
}

.work-time-curve__legend {
  justify-content: flex-end;
  gap: var(--space-3);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.work-time-curve__legend span {
  gap: 4px;
}

.work-time-curve__legend i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
}

.work-time-curve__legend .is-work {
  background: var(--accent);
}

.work-time-curve__legend .is-rest {
  background: var(--system-orange);
  opacity: 0.55;
}

.work-time-curve__legend svg {
  color: var(--system-orange);
}

.work-time-curve__empty {
  display: grid;
  min-height: 76px;
  padding: var(--space-3);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-align: center;
  background: var(--canvas);
  border-radius: var(--radius-control);
  place-items: center;
}

.work-time-curve--compact {
  padding: var(--space-2);
  background: var(--canvas);
  border: 0;
}

.work-time-curve--compact .work-time-curve__chart svg {
  max-height: 170px;
}

@media (max-width: 620px) {
  .work-time-curve__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
