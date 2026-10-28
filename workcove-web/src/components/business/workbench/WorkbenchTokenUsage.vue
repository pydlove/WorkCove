<script setup lang="ts">
import {
  Activity,
  CircleAlert,
  LoaderCircle,
  RefreshCw,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import { PRODUCT_NAME } from '@/brand'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { loadTokenUsage } from '@/services/tokenUsage'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCodexStore } from '@/stores/useCodexStore'
import type {
  TokenUsageSnapshot,
  UsageRange,
} from '@/types/usage'

const auth = useAuthStore()
const codex = useCodexStore()
const usageRange = ref<UsageRange>('today')
const snapshot = ref<TokenUsageSnapshot | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let refreshTimer: number | null = null
let requestSequence = 0

const rangeItems = [
  { value: 'today', label: '今天' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
]

const rangeLabel = computed(
  () =>
    rangeItems.find((item) => item.value === usageRange.value)?.label ??
    '今天',
)
const sourceLabel = computed(() =>
  snapshot.value?.source === 'cc-switch'
    ? 'CC Switch 实时数据'
    : `${PRODUCT_NAME} 自有统计`,
)
const successRate = computed(() => {
  const current = snapshot.value
  if (!current || current.requestCount <= 0) return null
  return (current.successCount / current.requestCount) * 100
})

watch(
  () => auth.email,
  (email) => {
    codex.load(email)
    void refreshUsage()
  },
  { immediate: true },
)

watch(usageRange, () => {
  void refreshUsage()
})

onMounted(() => {
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void refreshUsage()
    }
  }, 60_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

async function refreshUsage() {
  if (!auth.email) return
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const next = await loadTokenUsage(
      usageRange.value,
      codex.sessions,
    )
    if (sequence === requestSequence) snapshot.value = next
  } catch (error) {
    if (sequence !== requestSequence) return
    errorMessage.value =
      error instanceof Error ? error.message : '无法读取 Token 用量'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
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
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatCount(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatCost(value: number | null) {
  if (value === null) return '--'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatRate(value: number | null) {
  return value === null ? '--' : `${value.toFixed(1)}%`
}

function formatUpdated(value: string | undefined) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <section class="token-usage-panel" aria-labelledby="token-usage-title">
    <header class="token-usage-panel__header">
      <div class="token-usage-panel__heading">
        <span class="token-usage-panel__icon">
          <Activity :size="17" />
        </span>
        <div>
          <h2 id="token-usage-title">AI 用量</h2>
          <p>CC Switch 优先，读取不到时使用自有统计</p>
        </div>
      </div>

      <div class="token-usage-panel__actions">
        <UiSegmentedControl
          :model-value="usageRange"
          :items="rangeItems"
          size="sm"
          aria-label="AI 用量时间范围"
          @update:model-value="usageRange = $event as UsageRange"
        />
        <button
          class="token-usage-panel__refresh"
          type="button"
          aria-label="刷新 AI 用量"
          title="刷新 AI 用量"
          :disabled="loading"
          @click="refreshUsage"
        >
          <RefreshCw
            :class="{ 'token-usage-panel__spin': loading }"
            :size="14"
          />
        </button>
      </div>
    </header>

    <div v-if="loading && !snapshot" class="token-usage-panel__loading">
      <LoaderCircle class="token-usage-panel__spin" :size="16" />
      正在读取 Token 用量…
    </div>

    <div
      v-else-if="errorMessage"
      class="token-usage-panel__error"
      role="alert"
    >
      <CircleAlert :size="16" />
      <span>{{ errorMessage }}</span>
      <button type="button" @click="refreshUsage">重试</button>
    </div>

    <template v-else-if="snapshot">
      <div class="token-usage-panel__metrics">
        <article>
          <span>消耗 Tokens</span>
          <strong>{{ formatTokens(snapshot.totalTokens) }}</strong>
          <small>
            输入 {{ formatTokens(snapshot.inputTokens) }}
            · 输出 {{ formatTokens(snapshot.outputTokens) }}
          </small>
        </article>
        <article>
          <span>请求数</span>
          <strong>{{ formatCount(snapshot.requestCount) }}</strong>
          <small>
            {{
              successRate === null
                ? '暂无请求记录'
                : `成功 ${formatCount(snapshot.successCount)} · ${successRate.toFixed(2)}%`
            }}
          </small>
        </article>
        <article>
          <span>估算成本</span>
          <strong>{{ formatCost(snapshot.costUsd) }}</strong>
          <small>
            {{
              snapshot.costUsd === null
                ? '自有统计暂不计费'
                : '按模型价格估算'
            }}
          </small>
        </article>
        <article>
          <span>缓存命中率</span>
          <strong>{{ formatRate(snapshot.cacheHitRate) }}</strong>
          <small>
            缓存读取 {{ formatTokens(snapshot.cacheReadTokens) }}
          </small>
        </article>
      </div>

      <footer class="token-usage-panel__footer">
        <span
          class="token-usage-panel__source"
          :class="{
            'token-usage-panel__source--fallback':
              snapshot.source === 'workcove',
          }"
        >
          <i />
          {{ sourceLabel }}
        </span>
        <span
          v-if="snapshot.reason"
          class="token-usage-panel__reason"
          :title="snapshot.reason"
        >
          {{ snapshot.reason }}
        </span>
        <span class="token-usage-panel__updated">
          {{ rangeLabel }}
          <template v-if="formatUpdated(snapshot.updatedAt)">
            · {{ formatUpdated(snapshot.updatedAt) }} 更新
          </template>
        </span>
      </footer>
    </template>

    <div v-else class="token-usage-panel__loading">
      暂无用量数据
    </div>
  </section>
</template>

<style scoped>
.token-usage-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.token-usage-panel__header {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 11px var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.token-usage-panel__heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.token-usage-panel__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--system-blue);
  background: color-mix(
    in srgb,
    var(--system-blue) 9%,
    var(--surface-inset)
  );
  border-radius: var(--radius-control);
  place-items: center;
}

.token-usage-panel__heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.token-usage-panel__heading h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.token-usage-panel__heading p {
  overflow: hidden;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-usage-panel__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.token-usage-panel__refresh {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.token-usage-panel__refresh:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.token-usage-panel__refresh:disabled {
  opacity: 0.5;
}

.token-usage-panel__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 0 var(--space-4);
}

.token-usage-panel__metrics article {
  display: flex;
  min-width: 0;
  min-height: 105px;
  flex-direction: column;
  justify-content: center;
  padding: 15px var(--space-4);
}

.token-usage-panel__metrics article + article {
  border-left: 1px solid var(--line-soft);
}

.token-usage-panel__metrics article > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.token-usage-panel__metrics article > strong {
  margin-top: 8px;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-metric);
  font-weight: var(--weight-strong);
  line-height: 1;
}

.token-usage-panel__metrics article > small {
  overflow: hidden;
  margin-top: 8px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-usage-panel__footer {
  display: flex;
  min-height: 36px;
  align-items: center;
  gap: var(--space-2);
  padding: 7px var(--space-4);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-top: 1px solid var(--line-soft);
}

.token-usage-panel__source {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  color: var(--system-green);
  font-weight: var(--weight-emphasis);
}

.token-usage-panel__source i {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
}

.token-usage-panel__source--fallback {
  color: var(--system-orange);
}

.token-usage-panel__reason {
  min-width: 0;
  overflow: hidden;
  color: var(--text-subtle);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-usage-panel__updated {
  margin-left: auto;
  flex: 0 0 auto;
  font-family: var(--font-mono);
}

.token-usage-panel__loading,
.token-usage-panel__error {
  display: flex;
  min-height: 105px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.token-usage-panel__error {
  color: var(--system-red);
}

.token-usage-panel__error button {
  color: var(--system-blue);
  font-size: var(--font-caption);
}

.token-usage-panel__spin {
  animation: token-usage-spin 900ms linear infinite;
}

@keyframes token-usage-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 760px) {
  .token-usage-panel__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .token-usage-panel__actions {
    width: 100%;
    justify-content: space-between;
  }

  .token-usage-panel__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .token-usage-panel__metrics article {
    min-height: 94px;
    padding: 13px var(--space-3);
  }

  .token-usage-panel__metrics article:nth-child(odd) {
    border-left: 0;
  }

  .token-usage-panel__metrics article:nth-child(n + 3) {
    border-top: 1px solid var(--line-soft);
  }

  .token-usage-panel__footer {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .token-usage-panel__updated {
    width: 100%;
    margin-left: 0;
  }
}
</style>
