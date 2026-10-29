<script setup lang="ts">
import {
  CircleAlert,
  ChartColumnIncreasing,
  Clock3,
  FolderOpen,
  LoaderCircle,
  RefreshCw,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue'

import { inspectLocalDirectory } from '@/api/development'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import {
  calculateWorkDirectoryStats,
  type WorkDirectoryStatsRange,
} from '@/services/workDirectoryInsights'
import type { CodexSession } from '@/types/codex'
import type {
  LocalDirectorySummary,
  WorkDirectory,
} from '@/types/workDirectory'
import {
  formatCompactCount,
  formatCount,
} from '@/utils/numberFormat'

const props = defineProps<{
  directory: WorkDirectory
  sessions: CodexSession[]
}>()

const emit = defineEmits<{
  close: []
}>()

const session = useAuthStore()
const LARGE_FILE_THRESHOLD_KEY =
  'workcove.work-directory-large-file-threshold.v1'
const DEFAULT_LARGE_FILE_THRESHOLD_MB = 100
const MAX_LARGE_FILE_THRESHOLD_MB = 1024 * 1024
const DIRECTORY_SUMMARY_CACHE_TTL_MS = 60_000
const directorySummaryCache = new Map<
  string,
  { summary: LocalDirectorySummary; storedAt: number }
>()
const range = ref('30d')
const largeFileThresholdMb = ref(readLargeFileThreshold())
const thresholdInput = ref(String(largeFileThresholdMb.value))
const thresholdError = ref('')
const directorySummary = ref<LocalDirectorySummary | null>(null)
const directorySummaryLoading = ref(false)
const directorySummaryError = ref('')
const rangeOptions = [
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: 'all', label: '全部' },
]
const statusItems = [
  { key: 'completed', label: '已完成' },
  { key: 'running', label: '进行中' },
  { key: 'failed', label: '失败' },
  { key: 'interrupted', label: '已中断' },
  { key: 'queued', label: '排队中' },
] as const

const stats = computed(() =>
  calculateWorkDirectoryStats(
    props.directory,
    props.sessions,
    range.value as WorkDirectoryStatsRange,
  ),
)
const rangeLabel = computed(
  () =>
    rangeOptions.find((option) => option.value === range.value)?.label ??
    '近 30 天',
)
const largeFileThresholdBytes = computed(
  () => largeFileThresholdMb.value * 1024 * 1024,
)
const visibleStatusItems = computed(() =>
  statusItems
    .map((item) => ({
      ...item,
      count: stats.value.statusCounts[item.key],
    }))
    .filter((item) => item.count > 0),
)
let summaryRequestVersion = 0
let unmounted = false

watch(
  () => props.directory.path,
  () => {
    directorySummary.value = null
    directorySummaryError.value = ''
    void loadDirectorySummary()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  unmounted = true
  summaryRequestVersion += 1
})

async function loadDirectorySummary(force = false) {
  const path = props.directory.path
  const thresholdBytes = largeFileThresholdBytes.value
  const cacheKey = summaryCacheKey(path, thresholdBytes)
  const cached = directorySummaryCache.get(cacheKey)
  if (
    !force &&
    cached &&
    Date.now() - cached.storedAt < DIRECTORY_SUMMARY_CACHE_TTL_MS
  ) {
    directorySummary.value = cached.summary
    directorySummaryLoading.value = false
    directorySummaryError.value = ''
    return
  }

  const requestVersion = ++summaryRequestVersion
  directorySummaryLoading.value = true
  directorySummaryError.value = ''
  try {
    const summary = await inspectLocalDirectory(path, thresholdBytes)
    if (unmounted || requestVersion !== summaryRequestVersion) return
    directorySummary.value = summary
    directorySummaryCache.set(cacheKey, {
      summary,
      storedAt: Date.now(),
    })
  } catch (error) {
    if (unmounted || requestVersion !== summaryRequestVersion) return
    directorySummaryError.value =
      error instanceof Error
        ? error.message
        : '无法读取目录内容'
  } finally {
    if (!unmounted && requestVersion === summaryRequestVersion) {
      directorySummaryLoading.value = false
    }
  }
}

function readLargeFileThreshold() {
  try {
    const stored = readUserScopedValue<number>(
      LARGE_FILE_THRESHOLD_KEY,
      session.email,
    )
    return normalizeLargeFileThreshold(stored)
  } catch {
    return DEFAULT_LARGE_FILE_THRESHOLD_MB
  }
}

function normalizeLargeFileThreshold(value: unknown) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return DEFAULT_LARGE_FILE_THRESHOLD_MB
  }
  return Math.min(
    MAX_LARGE_FILE_THRESHOLD_MB,
    Math.max(1, Math.round(parsed)),
  )
}

function summaryCacheKey(path: string, thresholdBytes: number) {
  return `${path}\n${thresholdBytes}`
}

function applyLargeFileThreshold() {
  const parsed = Number(thresholdInput.value)
  if (
    !Number.isFinite(parsed) ||
    parsed < 1 ||
    parsed > MAX_LARGE_FILE_THRESHOLD_MB
  ) {
    thresholdError.value =
      `请输入 1-${MAX_LARGE_FILE_THRESHOLD_MB} MB 之间的阈值`
    return
  }

  const normalized = normalizeLargeFileThreshold(parsed)
  thresholdError.value = ''
  thresholdInput.value = String(normalized)
  if (normalized === largeFileThresholdMb.value) return

  largeFileThresholdMb.value = normalized
  try {
    writeUserScopedValue(
      LARGE_FILE_THRESHOLD_KEY,
      session.email,
      normalized,
    )
  } catch {
    thresholdError.value = '阈值已用于本次统计，但保存设置失败'
  }
  void loadDirectorySummary(true)
}

function statusPercent(count: number) {
  return stats.value.taskCount > 0
    ? (count / stats.value.taskCount) * 100
    : 0
}

function formatDuration(value: number) {
  if (value <= 0) return '0 分钟'
  if (value < 60_000) return '<1 分钟'
  const minutes = Math.round(value / 60_000)
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  return remainingMinutes
    ? `${hours} 小时 ${remainingMinutes} 分`
    : `${hours} 小时`
}

function formatRate(value: number | null) {
  return value === null ? '--' : `${value.toFixed(1)}%`
}

function formatLatestActivity(value: string | null) {
  if (!value) return '暂无记录'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function formatBytes(value: number) {
  const bytes = Math.max(0, Number.isFinite(value) ? value : 0)
  if (bytes < 1024) return `${formatCount(bytes)} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = bytes / 1024
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: size >= 100 ? 0 : size >= 10 ? 1 : 2,
  }).format(size)} ${units[unitIndex]}`
}

function formatDirectoryModifiedAt(value: string | null) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <UiDialog
    :title="`${directory.name} 工作统计`"
    :description="directory.path"
    size="extra-wide"
    height="tall"
    scroll-mode="body"
    @close="emit('close')"
  >
    <section
      class="directory-insights"
      :aria-label="`${directory.name} 工作目录统计`"
    >
      <div class="directory-insights__toolbar">
        <span>
          <ChartColumnIncreasing :size="14" />
          {{ stats.taskCount > 0 ? '统计范围' : '目录基础统计' }}
        </span>
        <UiSegmentedControl
          v-if="stats.taskCount > 0"
          v-model="range"
          :items="rangeOptions"
          size="sm"
          aria-label="统计时间范围"
        />
      </div>

      <template v-if="stats.taskCount > 0">
      <div class="directory-insights__metrics">
        <article>
          <span>Codex 会话</span>
          <strong>{{ formatCount(stats.sessionCount) }}</strong>
          <small>产生任务的会话</small>
        </article>
        <article>
          <span>执行任务</span>
          <strong>{{ formatCount(stats.taskCount) }}</strong>
          <small>{{ stats.activeDayCount }} 个活跃日</small>
        </article>
        <article>
          <span>完成率</span>
          <strong>{{ formatRate(stats.completionRate) }}</strong>
          <small>
            {{ stats.completedTaskCount }} / {{ stats.endedTaskCount }}
            个已结束任务
          </small>
        </article>
        <article>
          <span>记录耗时</span>
          <strong>{{ formatDuration(stats.recordedDurationMs) }}</strong>
          <small>按任务记录时长汇总</small>
        </article>
        <article>
          <span>Token 消耗</span>
          <strong>{{ formatCompactCount(stats.totalTokens) }}</strong>
          <small>输入与输出合计</small>
        </article>
      </div>
      </template>

      <section
        class="directory-insights__storage"
        aria-label="目录内容统计"
      >
        <header>
          <div>
            <span><FolderOpen :size="15" /></span>
            <div>
              <strong>目录内容</strong>
              <small>本机文件系统实时汇总</small>
            </div>
          </div>
          <div class="directory-insights__storage-tools">
            <label class="directory-insights__threshold">
              <span>大文件阈值</span>
              <UiInput
                v-model="thresholdInput"
                type="number"
                size="sm"
                min="1"
                :max="MAX_LARGE_FILE_THRESHOLD_MB"
                step="1"
                inputmode="numeric"
                aria-label="大文件阈值，单位 MB"
                :invalid="Boolean(thresholdError)"
                @change="applyLargeFileThreshold"
                @keydown.enter.prevent="applyLargeFileThreshold"
              />
              <span>MB</span>
            </label>
            <button
              type="button"
              :disabled="directorySummaryLoading"
              aria-label="重新扫描目录内容"
              title="重新扫描"
              @click="loadDirectorySummary(true)"
            >
              <RefreshCw
                :size="13"
                :class="{ 'is-spinning': directorySummaryLoading }"
              />
            </button>
          </div>
        </header>
        <p
          v-if="thresholdError"
          class="directory-insights__threshold-error"
          role="alert"
        >
          {{ thresholdError }}
        </p>

        <div
          v-if="directorySummaryLoading && !directorySummary"
          class="directory-insights__storage-state"
        >
          <LoaderCircle class="is-spinning" :size="16" />
          正在扫描文件数量和目录大小…
        </div>
        <template v-else-if="directorySummary">
          <div class="directory-insights__storage-grid">
            <div>
              <span>文件数</span>
              <strong>{{ formatCount(directorySummary.fileCount) }}</strong>
              <small>普通文件</small>
            </div>
            <div>
              <span>子目录数</span>
              <strong>
                {{ formatCount(directorySummary.directoryCount) }}
              </strong>
              <small>不含当前目录</small>
            </div>
            <div>
              <span>大文件数</span>
              <strong>{{ formatCount(directorySummary.largeFileCount) }}</strong>
              <small>
                ≥ {{ formatBytes(directorySummary.largeFileThresholdBytes) }}
              </small>
            </div>
            <div>
              <span>目录大小</span>
              <strong>{{ formatBytes(directorySummary.totalBytes) }}</strong>
              <small>文件逻辑大小合计</small>
            </div>
            <div>
              <span>最近修改</span>
              <strong class="is-date">
                {{
                  formatDirectoryModifiedAt(
                    directorySummary.lastModifiedAt,
                  )
                }}
              </strong>
              <small>文件或子目录</small>
            </div>
          </div>
          <p
            v-if="
              directorySummary.truncated ||
              directorySummary.inaccessibleCount > 0
            "
            class="directory-insights__storage-warning"
          >
            <CircleAlert :size="13" />
            <span>
              <template v-if="directorySummary.truncated">
                条目过多，当前结果已达到扫描上限，统计不完整。
              </template>
              <template v-if="directorySummary.inaccessibleCount > 0">
                有 {{ directorySummary.inaccessibleCount }}
                个条目因权限不足未读取。
              </template>
            </span>
          </p>
          <p class="directory-insights__storage-meta">
            扫描耗时
            {{ Math.max(1, directorySummary.scanDurationMs) }} ms
          </p>
          <p
            v-if="directorySummaryError"
            class="directory-insights__storage-error-inline"
          >
            <CircleAlert :size="13" />
            {{ directorySummaryError }}
            <button type="button" @click="loadDirectorySummary(true)">
              重试
            </button>
          </p>
        </template>
        <div
          v-else-if="directorySummaryError"
          class="directory-insights__storage-state is-error"
        >
          <CircleAlert :size="15" />
          <span>{{ directorySummaryError }}</span>
          <button type="button" @click="loadDirectorySummary(true)">
            重试
          </button>
        </div>
      </section>

      <template v-if="stats.taskCount > 0">
      <div class="directory-insights__details">
        <section class="directory-insights__status">
          <header>
            <div>
              <strong>任务状态</strong>
              <small>完成率仅统计已结束任务</small>
            </div>
            <span>{{ formatCount(stats.taskCount) }} 个</span>
          </header>
          <div
            class="directory-insights__status-bar"
            role="img"
            :aria-label="
              visibleStatusItems
                .map((item) => `${item.label} ${item.count} 个`)
                .join('，')
            "
          >
            <span
              v-for="item in visibleStatusItems"
              :key="item.key"
              :class="`is-${item.key}`"
              :style="{ width: `${statusPercent(item.count)}%` }"
              :title="`${item.label} ${item.count} 个`"
            />
          </div>
          <ul>
            <li
              v-for="item in visibleStatusItems"
              :key="item.key"
            >
              <i :class="`is-${item.key}`" />
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </li>
          </ul>
        </section>

        <section class="directory-insights__engineering">
          <header>
            <div>
              <strong>工程活动</strong>
              <small>Codex 在该目录中的执行构成</small>
            </div>
          </header>
          <dl>
            <div>
              <dt>命令执行</dt>
              <dd>{{ formatCount(stats.commandCount) }}</dd>
            </div>
            <div>
              <dt>文件操作</dt>
              <dd>{{ formatCount(stats.fileChangeCount) }}</dd>
            </div>
            <div>
              <dt>工具调用</dt>
              <dd>{{ formatCount(stats.toolCount) }}</dd>
            </div>
          </dl>
        </section>
      </div>

      </template>

      <div v-else class="directory-insights__empty">
        <ChartColumnIncreasing :size="24" />
        <strong>暂无 Codex 工作记录，已展示目录基础统计</strong>
        <p>
          在这个目录发起 Codex 任务后，还会显示工作量、完成情况、
          Token 消耗和工程活动。
        </p>
      </div>
    </section>
    <template #footer>
      <footer class="directory-insights__footer">
        <span v-if="stats.taskCount > 0">
          <Clock3 :size="13" />
          最近活跃 {{ formatLatestActivity(stats.latestActivityAt) }}
        </span>
        <span>
          {{
            stats.taskCount > 0
              ? `${rangeLabel} · 数据来自本机 Codex 任务记录`
              : '基础统计 · 数据来自本机文件系统'
          }}
        </span>
      </footer>
    </template>
  </UiDialog>
</template>

<style scoped>
.directory-insights {
  display: flex;
  width: 100%;
  min-height: 100%;
  flex-direction: column;
}

.directory-insights__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-secondary);
  border-bottom: 1px solid var(--line-soft);
}

.directory-insights__toolbar > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.directory-insights__metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-bottom: 1px solid var(--line-soft);
}

.directory-insights__metrics article {
  display: flex;
  min-width: 0;
  min-height: 94px;
  padding: var(--space-3) var(--space-4);
  flex-direction: column;
  justify-content: center;
  border-right: 1px solid var(--line-soft);
}

.directory-insights__metrics article:last-child {
  border-right: 0;
}

.directory-insights__metrics span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.directory-insights__metrics strong {
  margin-top: 3px;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-metric);
  font-weight: var(--weight-emphasis);
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-insights__metrics small {
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-insights__storage {
  padding: var(--space-3) var(--space-4) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.directory-insights__storage > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.directory-insights__storage > header > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.directory-insights__storage > header > div > span {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.directory-insights__storage > header > div > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.directory-insights__storage header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-insights__storage header small,
.directory-insights__storage-meta {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.directory-insights__storage-tools > button,
.directory-insights__storage-state > button {
  display: grid;
  height: 26px;
  padding: 0 8px;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.directory-insights__storage-tools > button {
  width: 26px;
  padding: 0;
}

.directory-insights__storage-tools > button:hover,
.directory-insights__storage-state > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.directory-insights__storage-tools > button:disabled {
  opacity: 0.5;
}

.directory-insights__storage-tools {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.directory-insights__threshold {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.directory-insights__threshold :deep(.ui-input) {
  width: 72px;
  text-align: right;
}

.directory-insights__threshold-error {
  margin: var(--space-2) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.directory-insights__storage-grid {
  display: grid;
  margin-top: var(--space-3);
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-2);
}

.directory-insights__storage-grid > div {
  display: flex;
  min-width: 0;
  min-height: 74px;
  padding: var(--space-2) var(--space-3);
  flex-direction: column;
  justify-content: center;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.directory-insights__storage-grid span,
.directory-insights__storage-grid small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-insights__storage-grid strong {
  margin-top: 2px;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-insights__storage-grid strong.is-date {
  font-size: var(--font-body);
}

.directory-insights__storage-grid small {
  margin-top: 2px;
}

.directory-insights__storage-state {
  display: flex;
  min-height: 74px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-align: center;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.directory-insights__storage-state.is-error {
  color: var(--system-red);
}

.directory-insights__storage-state > span {
  min-width: 0;
}

.directory-insights__storage-warning {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin: var(--space-2) 0 0;
  color: var(--system-orange);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.directory-insights__storage-warning svg {
  flex: 0 0 auto;
  margin-top: 1px;
}

.directory-insights__storage-meta {
  margin: var(--space-2) 0 0;
  text-align: right;
}

.directory-insights__storage-error-inline {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  margin: var(--space-2) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.directory-insights__storage-error-inline button {
  padding: 0;
  color: var(--system-blue);
  font-size: var(--font-caption);
  background: transparent;
}

.is-spinning {
  animation: directory-insights-spin 0.8s linear infinite;
}

.directory-insights__details {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(260px, 0.8fr);
}

.directory-insights__status,
.directory-insights__engineering {
  min-width: 0;
  padding: var(--space-4);
}

.directory-insights__status {
  border-right: 1px solid var(--line-soft);
}

.directory-insights__status > header,
.directory-insights__engineering > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.directory-insights__status header > div,
.directory-insights__engineering header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.directory-insights__status header strong,
.directory-insights__engineering header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-insights__status header small,
.directory-insights__engineering header small,
.directory-insights__status > header > span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.directory-insights__status-bar {
  display: flex;
  height: 8px;
  margin-top: var(--space-3);
  overflow: hidden;
  background: var(--segmented-track);
  border-radius: var(--radius-badge);
}

.directory-insights__status-bar span {
  height: 100%;
}

.directory-insights__status-bar .is-completed,
.directory-insights__status li i.is-completed {
  background: var(--system-green);
}

.directory-insights__status-bar .is-running,
.directory-insights__status li i.is-running {
  background: var(--system-blue);
}

.directory-insights__status-bar .is-failed,
.directory-insights__status li i.is-failed {
  background: var(--system-red);
}

.directory-insights__status-bar .is-interrupted,
.directory-insights__status li i.is-interrupted {
  background: var(--system-orange);
}

.directory-insights__status-bar .is-queued,
.directory-insights__status li i.is-queued {
  background: var(--text-subtle);
}

.directory-insights__status ul {
  display: flex;
  margin: var(--space-3) 0 0;
  padding: 0;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  list-style: none;
}

.directory-insights__status li {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.directory-insights__status li i {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.directory-insights__status li strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-weight: var(--weight-emphasis);
}

.directory-insights__engineering dl {
  display: flex;
  margin: var(--space-3) 0 0;
  flex-direction: column;
  gap: var(--space-2);
}

.directory-insights__engineering dl > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.directory-insights__engineering dl > div:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.directory-insights__engineering dt {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.directory-insights__engineering dd {
  margin: 0;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-insights__footer {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  border-top: 1px solid var(--line-soft);
}

.directory-insights__footer span {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
}

.directory-insights__empty {
  display: flex;
  min-height: 0;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.directory-insights__empty strong {
  margin-top: var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-insights__empty p {
  max-width: 480px;
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 900px) {
  .directory-insights__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .directory-insights__metrics article {
    border-bottom: 1px solid var(--line-soft);
  }

  .directory-insights__metrics article:nth-child(3) {
    border-right: 0;
  }

  .directory-insights__metrics article:nth-child(n + 4) {
    border-bottom: 0;
  }

  .directory-insights__details {
    grid-template-columns: 1fr;
  }

  .directory-insights__storage-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .directory-insights__status {
    border-right: 0;
    border-bottom: 1px solid var(--line-soft);
  }
}

@media (max-width: 620px) {
  .directory-insights__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .directory-insights__toolbar :deep(.ui-segmented) {
    width: 100%;
  }

  .directory-insights__storage > header {
    align-items: stretch;
    flex-direction: column;
  }

  .directory-insights__storage-tools {
    justify-content: space-between;
  }

  .directory-insights__threshold {
    flex: 1;
  }

  .directory-insights__threshold :deep(.ui-input) {
    width: min(110px, 100%);
    margin-left: auto;
  }

  .directory-insights__storage-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .directory-insights__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .directory-insights__metrics article:nth-child(3) {
    border-right: 1px solid var(--line-soft);
  }

  .directory-insights__metrics article:nth-child(2n) {
    border-right: 0;
  }

  .directory-insights__metrics article:nth-child(n + 4) {
    border-bottom: 1px solid var(--line-soft);
  }

  .directory-insights__metrics article:last-child {
    grid-column: 1 / -1;
    border-bottom: 0;
  }

  .directory-insights__footer {
    align-items: flex-start;
    justify-content: center;
    padding: var(--space-2) var(--space-3);
    flex-direction: column;
  }
}

@keyframes directory-insights-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
