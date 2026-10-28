<script setup lang="ts">
import {
  Keyboard,
  MousePointerClick,
  ShieldCheck,
} from '@lucide/vue'
import { computed } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import { localDateKey } from '@/services/localActivity'
import { useActivityStore } from '@/stores/useActivityStore'
import type { DailyActivity } from '@/types/activity'
import {
  formatCompactCount,
  formatCount,
} from '@/utils/numberFormat'

const emit = defineEmits<{
  close: []
}>()

const activity = useActivityStore()
const historyItems = computed(() => activity.history)
const {
  currentPage,
  pageItems: historyPage,
  setPage,
} = usePagination(historyItems)

const recentDays = computed(() => {
  const records = new Map(
    activity.days.map((day) => [day.date, day]),
  )
  const days: DailyActivity[] = []
  const today = new Date()
  for (let offset = 13; offset >= 0; offset -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - offset)
    const key = localDateKey(date)
    days.push(
      records.get(key) ?? {
        date: key,
        typedCharacters: 0,
        mouseClicks: 0,
        updatedAt: '',
      },
    )
  }
  return days
})

const maxTypedCharacters = computed(() =>
  Math.max(1, ...recentDays.value.map((day) => day.typedCharacters)),
)

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${value}T12:00:00`))
}

function barHeight(day: DailyActivity) {
  if (day.typedCharacters === 0) return '3px'
  return `${Math.max(
    8,
    (day.typedCharacters / maxTypedCharacters.value) * 100,
  )}%`
}
</script>

<template>
  <UiDialog
    title="输入轨迹"
    description="看看自己每天敲下了多少字、点击了多少次鼠标。"
    size="wide"
    scroll-mode="body"
    @close="emit('close')"
  >
    <div class="activity-history">
      <div class="activity-history__summary">
        <div>
          <span>累计输入</span>
          <strong>
            {{ formatCompactCount(activity.totalTypedCharacters) }}
            <small>字</small>
          </strong>
        </div>
        <div>
          <span>累计点击</span>
          <strong>
            {{ formatCompactCount(activity.totalMouseClicks) }}
            <small>次</small>
          </strong>
        </div>
        <div>
          <span>当前速度</span>
          <strong>
            {{ formatCompactCount(activity.sogou.currentSpeed) }}
            <small>字/分</small>
          </strong>
        </div>
        <div>
          <span>历史均速</span>
          <strong>
            {{ formatCompactCount(activity.sogou.historySpeed) }}
            <small>字/分</small>
          </strong>
        </div>
      </div>

      <section class="activity-history__chart">
        <header>
          <div>
            <h3>最近 14 天输入</h3>
            <p>每一根柱子代表当天实际输入的字符数。</p>
          </div>
          <span>
            <Keyboard :size="14" />
            今日
            {{ formatCompactCount(activity.today?.typedCharacters ?? 0) }}
            字
          </span>
        </header>
        <div class="activity-chart">
          <div
            v-for="day in recentDays"
            :key="day.date"
            class="activity-chart__day"
            :title="`${formatDate(day.date)} · ${formatCount(day.typedCharacters)} 字 · ${formatCount(day.mouseClicks)} 次点击`"
          >
            <span class="activity-chart__bar-track">
              <i :style="{ height: barHeight(day) }" />
            </span>
            <small>{{ day.date.slice(8) }}</small>
          </div>
        </div>
      </section>

      <section class="activity-history__records">
        <header>
          <div>
            <h3>每日记录</h3>
            <p>按日期倒序保存，只记录数量，不记录输入内容。</p>
          </div>
        </header>

        <div v-if="historyPage.length > 0" class="activity-history__table">
          <div class="activity-history__table-head">
            <span>日期</span>
            <span>输入字数</span>
            <span>鼠标点击</span>
          </div>
          <div
            v-for="day in historyPage"
            :key="day.date"
            class="activity-history__row"
          >
            <strong>{{ formatDate(day.date) }}</strong>
            <span class="activity-history__metric-count">
              <Keyboard :size="13" />
              <strong>{{ formatCompactCount(day.typedCharacters) }}</strong>
              <small>字</small>
            </span>
            <span class="activity-history__metric-count">
              <MousePointerClick :size="13" />
              <strong>{{ formatCompactCount(day.mouseClicks) }}</strong>
              <small>次</small>
            </span>
          </div>
        </div>
        <div v-else class="activity-history__empty">
          还没有输入记录，开始使用 8小时工作台后会自动统计。
        </div>

        <UiPagination
          :page="currentPage"
          :total="activity.history.length"
          label="输入历史分页"
          @update:page="setPage"
        />
      </section>

      <p class="activity-history__privacy">
        <ShieldCheck :size="14" />
        {{
          activity.sogouActive
            ? '字数和输入速度来自本机搜狗输入法；鼠标点击仅统计 8小时工作台页面内。'
            : '搜狗统计不可用时，仅统计 8小时工作台页面内的非空白字符和鼠标左键点击。'
        }}
        不记录输入内容。
      </p>

    </div>

    <template #footer>
      <UiButton size="lg" @click="emit('close')">关闭</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.activity-history {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: 0 var(--space-5) var(--space-5);
}

.activity-history__summary {
  display: grid;
  margin-top: var(--space-3);
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-3);
}

.activity-history__summary > div {
  display: flex;
  min-width: 0;
  padding: var(--space-3);
  flex-direction: column;
  background: var(--canvas);
  border-radius: var(--radius-panel);
}

.activity-history__summary span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.activity-history__summary strong {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 4px;
  margin-top: 3px;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 560;
  line-height: 1.2;
  white-space: nowrap;
}

.activity-history__summary small {
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: var(--font-caption);
  font-weight: 500;
}

.activity-history__chart,
.activity-history__records {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.activity-history__chart header,
.activity-history__records header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.activity-history h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.activity-history header p {
  margin: 3px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.activity-history__chart header > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.activity-chart {
  display: grid;
  height: 150px;
  grid-template-columns: repeat(14, 1fr);
  align-items: end;
  gap: 7px;
  padding: var(--space-3);
  background:
    linear-gradient(
      to top,
      transparent 24%,
      var(--line-soft) 25%,
      transparent 25.5%,
      transparent 49%,
      var(--line-soft) 50%,
      transparent 50.5%,
      transparent 74%,
      var(--line-soft) 75%,
      transparent 75.5%
    ),
    var(--canvas);
  border-radius: var(--radius-panel);
}

.activity-chart__day {
  display: flex;
  height: 100%;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-direction: column;
}

.activity-chart__bar-track {
  display: flex;
  width: 100%;
  min-height: 0;
  flex: 1;
  align-items: flex-end;
  justify-content: center;
}

.activity-chart__bar-track i {
  width: min(22px, 72%);
  min-height: 3px;
  background: linear-gradient(
    to top,
    var(--system-blue),
    color-mix(in srgb, var(--system-blue) 40%, var(--system-green))
  );
  border-radius: 4px 4px 2px 2px;
}

.activity-chart__day small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.activity-history__table {
  overflow: hidden;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.activity-history__table-head,
.activity-history__row {
  display: grid;
  min-height: 40px;
  grid-template-columns: minmax(0, 1fr) 150px 140px;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-3);
  border-bottom: 1px solid var(--line-soft);
}

.activity-history__table-head {
  min-height: 32px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.activity-history__row:last-child {
  border-bottom: 0;
}

.activity-history__row > strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.activity-history__row span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.activity-history__row .activity-history__metric-count strong {
  color: var(--accent);
  font-size: var(--font-body);
  font-weight: var(--weight-strong);
  font-variant-numeric: tabular-nums;
}

.activity-history__row .activity-history__metric-count small {
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.activity-history__empty {
  display: grid;
  min-height: 150px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--canvas);
  border-radius: var(--radius-panel);
  place-items: center;
}

.activity-history__privacy {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.activity-history__privacy svg {
  flex: 0 0 auto;
  color: var(--system-green);
}

@media (max-width: 620px) {
  .activity-history {
    padding: 0 var(--space-4) var(--space-4);
  }

  .activity-history__summary {
    grid-template-columns: repeat(2, 1fr);
  }

  .activity-chart {
    gap: 3px;
  }

  .activity-history__table-head,
  .activity-history__row {
    grid-template-columns: minmax(0, 1fr) 100px;
  }

  .activity-history__table-head span:last-child,
  .activity-history__row > span:last-child {
    display: none;
  }
}
</style>
