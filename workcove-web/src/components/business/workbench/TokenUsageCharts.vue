<script setup lang="ts">
import { computed } from 'vue'

import type { TokenUsageSnapshot } from '@/types/usage'

const props = defineProps<{
  snapshot: TokenUsageSnapshot
}>()

const failureCount = computed(() =>
  Math.max(
    0,
    props.snapshot.requestCount - props.snapshot.successCount,
  ),
)
const requestSuccessRate = computed(() => {
  if (props.snapshot.requestCount <= 0) return null
  return clampPercent(
    (props.snapshot.successCount / props.snapshot.requestCount) * 100,
  )
})
const totalTokens = computed(() =>
  Math.max(
    0,
    props.snapshot.inputTokens + props.snapshot.outputTokens,
  ),
)
const inputPercent = computed(() =>
  totalTokens.value > 0
    ? clampPercent(
        (props.snapshot.inputTokens / totalTokens.value) * 100,
      )
    : 0,
)
const outputPercent = computed(() =>
  totalTokens.value > 0
    ? clampPercent(
        (props.snapshot.outputTokens / totalTokens.value) * 100,
      )
    : 0,
)
const cacheHitRate = computed(() =>
  props.snapshot.cacheHitRate === null
    ? null
    : clampPercent(props.snapshot.cacheHitRate),
)

function clampPercent(value: number) {
  return Math.min(100, Math.max(0, value))
}

function formatCount(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatTokens(value: number) {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(2)}B`
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(2)}M`
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}K`
  }
  return formatCount(value)
}

function formatRate(value: number | null) {
  return value === null ? '--' : `${value.toFixed(1)}%`
}
</script>

<template>
  <section class="token-usage-charts" aria-label="Token 用量图表">
    <article class="token-usage-chart">
      <header>
        <div>
          <h3>请求结果</h3>
          <p>成功与失败请求占比</p>
        </div>
        <span>{{ formatCount(snapshot.requestCount) }} 次</span>
      </header>

      <div class="request-chart">
        <div
          class="request-chart__donut"
          role="img"
          :aria-label="`请求成功率 ${formatRate(requestSuccessRate)}`"
        >
          <svg viewBox="0 0 120 120" aria-hidden="true">
            <circle
              class="request-chart__track"
              cx="60"
              cy="60"
              r="48"
              pathLength="100"
            />
            <circle
              class="request-chart__value"
              cx="60"
              cy="60"
              r="48"
              pathLength="100"
              :stroke-dasharray="`${requestSuccessRate ?? 0} ${100 - (requestSuccessRate ?? 0)}`"
            />
          </svg>
          <div>
            <strong>{{ formatRate(requestSuccessRate) }}</strong>
            <small>成功率</small>
          </div>
        </div>

        <div class="request-chart__legend">
          <span>
            <i class="is-success" />
            <small>成功</small>
            <strong>{{ formatCount(snapshot.successCount) }}</strong>
          </span>
          <span>
            <i class="is-failure" />
            <small>未成功</small>
            <strong>{{ formatCount(failureCount) }}</strong>
          </span>
        </div>
      </div>
    </article>

    <article class="token-usage-chart">
      <header>
        <div>
          <h3>Token 构成</h3>
          <p>输入与输出占当前范围总 Token 的比例</p>
        </div>
        <span>{{ formatTokens(totalTokens) }}</span>
      </header>

      <div class="token-composition">
        <div
          class="token-composition__bar"
          role="img"
          :aria-label="`输入 ${formatRate(inputPercent)}，输出 ${formatRate(outputPercent)}`"
        >
          <span
            v-if="inputPercent > 0"
            class="is-input"
            :style="{ width: `${inputPercent}%` }"
          />
          <span
            v-if="outputPercent > 0"
            class="is-output"
            :style="{ width: `${outputPercent}%` }"
          />
        </div>
        <div class="token-composition__legend">
          <span>
            <i class="is-input" />
            <small>输入</small>
            <strong>{{ formatTokens(snapshot.inputTokens) }}</strong>
            <em>{{ formatRate(inputPercent) }}</em>
          </span>
          <span>
            <i class="is-output" />
            <small>输出</small>
            <strong>{{ formatTokens(snapshot.outputTokens) }}</strong>
            <em>{{ formatRate(outputPercent) }}</em>
          </span>
        </div>
      </div>

      <div class="cache-chart">
        <div>
          <span>缓存命中率</span>
          <strong>{{ formatRate(cacheHitRate) }}</strong>
        </div>
        <div
          class="cache-chart__track"
          role="img"
          :aria-label="`缓存命中率 ${formatRate(cacheHitRate)}`"
        >
          <i :style="{ width: `${cacheHitRate ?? 0}%` }" />
        </div>
        <p>
          读取 {{ formatTokens(snapshot.cacheReadTokens) }}
          <span>·</span>
          写入 {{ formatTokens(snapshot.cacheCreationTokens) }}
        </p>
      </div>
    </article>
  </section>
</template>

<style scoped>
.token-usage-charts {
  display: grid;
  grid-template-columns: minmax(210px, 0.72fr) minmax(0, 1.28fr);
  gap: var(--space-2);
}

.token-usage-chart {
  display: flex;
  min-width: 0;
  min-height: 188px;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.token-usage-chart > header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.token-usage-chart > header > div {
  min-width: 0;
}

.token-usage-chart h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.token-usage-chart header p {
  margin: 3px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.token-usage-chart > header > span {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-variant-numeric: tabular-nums;
}

.request-chart {
  display: flex;
  min-height: 0;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
}

.request-chart__donut {
  position: relative;
  width: 108px;
  height: 108px;
  flex: 0 0 auto;
}

.request-chart__donut svg {
  display: block;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.request-chart__donut circle {
  fill: none;
  stroke-width: 10;
}

.request-chart__track {
  stroke: var(--line-soft);
}

.request-chart__value {
  stroke: var(--system-green);
  stroke-linecap: round;
}

.request-chart__donut > div {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  inset: 0;
}

.request-chart__donut strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: var(--weight-strong);
}

.request-chart__donut small {
  margin-top: 2px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.request-chart__legend {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-2);
}

.request-chart__legend > span,
.token-composition__legend > span {
  display: grid;
  min-width: 0;
  align-items: center;
  gap: 5px;
}

.request-chart__legend > span {
  grid-template-columns: 7px minmax(0, 1fr) auto;
}

.request-chart__legend i,
.token-composition__legend i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.request-chart__legend small,
.token-composition__legend small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.request-chart__legend strong,
.token-composition__legend strong {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.is-success {
  background: var(--system-green);
}

.is-failure {
  background: var(--system-red);
}

.token-composition .is-input {
  background: var(--system-blue);
}

.token-composition .is-output {
  background: var(--system-orange);
}

.token-composition {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.token-composition__bar {
  display: flex;
  width: 100%;
  height: 18px;
  overflow: hidden;
  background: var(--line-soft);
  border-radius: var(--radius-badge);
}

.token-composition__bar > span {
  height: 100%;
  min-width: 2px;
}

.token-composition__legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.token-composition__legend > span {
  grid-template-columns: 7px auto minmax(0, 1fr) auto;
}

.token-composition__legend em {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-style: normal;
}

.cache-chart {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding-top: var(--space-3);
  margin-top: auto;
  border-top: 1px solid var(--line-soft);
}

.cache-chart > div:first-child {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.cache-chart > div:first-child strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-weight: var(--weight-emphasis);
}

.cache-chart__track {
  height: 6px;
  overflow: hidden;
  background: var(--line-soft);
  border-radius: var(--radius-badge);
}

.cache-chart__track i {
  display: block;
  height: 100%;
  background: linear-gradient(
    90deg,
    var(--system-blue),
    var(--system-green)
  );
  border-radius: inherit;
}

.cache-chart p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.cache-chart p span {
  padding: 0 3px;
}

@media (max-width: 760px) {
  .token-usage-charts {
    grid-template-columns: 1fr;
  }

  .token-usage-chart {
    min-height: 0;
  }

  .request-chart {
    justify-content: flex-start;
  }
}
</style>
