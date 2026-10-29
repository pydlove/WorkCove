<script setup lang="ts">
import {
  ChartColumnIncreasing,
  ChevronRight,
  FileCode2,
  GitCommitHorizontal,
  Minus,
  Plus,
  RefreshCw,
  UsersRound,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import { getGitStatistics } from '@/api/gitRepositories'
import GitAuthorCommitsDialog from '@/components/business/development/GitAuthorCommitsDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import type {
  GitAuthorStatistics,
  GitRepository,
  GitRepositoryStatistics,
} from '@/types/gitRepository'

const props = defineProps<{
  repository: GitRepository
}>()

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const loading = ref(false)
const error = ref('')
const statistics = ref<GitRepositoryStatistics | null>(null)
const selectedAuthor = ref<GitAuthorStatistics | null>(null)

const monthlyMaximum = computed(() =>
  Math.max(
    1,
    ...(statistics.value?.months.map((item) => item.commits) ?? [0]),
  ),
)
const hourlyMaximum = computed(() =>
  Math.max(
    1,
    ...(statistics.value?.hours.map((item) => item.commits) ?? [0]),
  ),
)
const totalChanges = computed(
  () =>
    (statistics.value?.additions ?? 0) +
    (statistics.value?.deletions ?? 0),
)
const additionRatio = computed(() =>
  totalChanges.value === 0
    ? 0
    : ((statistics.value?.additions ?? 0) / totalChanges.value) * 100,
)
const topAuthors = computed(
  () => statistics.value?.authors.slice(0, 10) ?? [],
)

onMounted(loadStatistics)

async function loadStatistics() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    statistics.value = await getGitStatistics(props.repository.path)
  } catch (cause) {
    error.value =
      cause instanceof Error ? cause.message : '无法统计 Git 仓库'
    emit('notify', error.value)
  } finally {
    loading.value = false
  }
}

function monthLabel(value: string) {
  const month = Number(value.slice(5))
  return Number.isFinite(month) ? `${month}月` : value
}

function barHeight(value: number, maximum: number) {
  if (value <= 0) return '2px'
  return `${Math.max(8, Math.round((value / maximum) * 100))}%`
}

function formatCount(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatCompact(value: number) {
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(1)}亿`
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(1)}万`
  }
  return formatCount(value)
}

function signedLines(value: number) {
  if (value === 0) return '0'
  return `${value > 0 ? '+' : '-'}${formatCompact(Math.abs(value))}`
}
</script>

<template>
  <UiDialog
    title="代码统计"
    :description="`${repository.name} · 近一年提交与代码量`"
    size="extra-wide"
    height="tall"
    scroll-mode="body"
    max-height="min(900px, calc(100dvh - 32px))"
    @close="emit('close')"
  >
    <div class="git-statistics">
      <div v-if="loading" class="git-statistics__state">
        <UiFidgetSpinner :size="24" />
        <strong>正在统计近一年 Git 历史…</strong>
        <span>仓库较大时可能需要几秒钟</span>
      </div>

      <div v-else-if="error" class="git-statistics__state is-error">
        <ChartColumnIncreasing :size="24" />
        <strong>统计失败</strong>
        <span>{{ error }}</span>
        <UiButton size="sm" :loading="loading" @click="loadStatistics">
          <RefreshCw v-if="!loading" :size="13" />
          重新统计
        </UiButton>
      </div>

      <template v-else-if="statistics">
        <header class="git-statistics__heading">
          <div>
            <strong>
              {{ statistics.since }} 至 {{ statistics.until }}
            </strong>
            <span>排除 merge commit，按作者提交时间统计</span>
          </div>
          <UiButton
            size="sm"
            :loading="loading"
            @click="loadStatistics"
          >
            <RefreshCw v-if="!loading" :size="13" />
            重新统计
          </UiButton>
        </header>

        <section class="git-statistics__metrics" aria-label="统计汇总">
          <article>
            <span><GitCommitHorizontal :size="15" /></span>
            <small>提交量</small>
            <strong>{{ formatCount(statistics.totalCommits) }}</strong>
          </article>
          <article>
            <span><UsersRound :size="15" /></span>
            <small>参与人数</small>
            <strong>{{ formatCount(statistics.activeAuthors) }}</strong>
          </article>
          <article>
            <span><FileCode2 :size="15" /></span>
            <small>当前文本行</small>
            <strong>{{ formatCompact(statistics.totalLines) }}</strong>
          </article>
          <article class="is-additions">
            <span><Plus :size="15" /></span>
            <small>近一年新增</small>
            <strong>+{{ formatCompact(statistics.additions) }}</strong>
          </article>
          <article class="is-deletions">
            <span><Minus :size="15" /></span>
            <small>近一年删除</small>
            <strong>-{{ formatCompact(statistics.deletions) }}</strong>
          </article>
        </section>

        <section class="git-statistics__section">
          <header>
            <div>
              <strong>月度提交趋势</strong>
              <span>最近 12 个月的提交分布</span>
            </div>
            <em>{{ formatCount(statistics.totalCommits) }} 次提交</em>
          </header>
          <div
            class="git-statistics__month-chart"
            role="img"
            :aria-label="`月度提交趋势，共 ${statistics.totalCommits} 次提交`"
          >
            <div
              v-for="month in statistics.months"
              :key="month.month"
              class="git-statistics__month"
              :title="`${month.month}：${month.commits} 次提交，+${month.additions} / -${month.deletions}`"
            >
              <span class="git-statistics__bar-track">
                <i
                  :style="{
                    height: barHeight(month.commits, monthlyMaximum),
                  }"
                />
              </span>
              <small>{{ monthLabel(month.month) }}</small>
              <b>{{ month.commits }}</b>
            </div>
          </div>
        </section>

        <div class="git-statistics__split">
          <section class="git-statistics__section">
            <header>
              <div>
                <strong>24 小时提交分布</strong>
                <span>按作者本地时间统计</span>
              </div>
            </header>
            <div
              class="git-statistics__hour-chart"
              role="img"
              aria-label="24 小时提交分布"
            >
              <div
                v-for="hour in statistics.hours"
                :key="hour.hour"
                :title="`${String(hour.hour).padStart(2, '0')}:00 · ${hour.commits} 次提交`"
              >
                <span>
                  <i
                    :style="{
                      height: barHeight(hour.commits, hourlyMaximum),
                    }"
                  />
                </span>
                <small v-if="hour.hour % 3 === 0">
                  {{ String(hour.hour).padStart(2, '0') }}
                </small>
              </div>
            </div>
          </section>

          <section class="git-statistics__section">
            <header>
              <div>
                <strong>代码增删</strong>
                <span>
                  {{ formatCount(statistics.changedFiles) }} 个文件发生过变更
                </span>
              </div>
              <em
                :class="{
                  'is-negative': statistics.netLines < 0,
                }"
              >
                净变化 {{ signedLines(statistics.netLines) }}
              </em>
            </header>
            <div class="git-statistics__change-summary">
              <div>
                <span>+{{ formatCompact(statistics.additions) }}</span>
                <span>-{{ formatCompact(statistics.deletions) }}</span>
              </div>
              <div class="git-statistics__change-track">
                <i
                  class="is-additions"
                  :style="{ width: `${additionRatio}%` }"
                />
                <i
                  class="is-deletions"
                  :style="{ width: `${100 - additionRatio}%` }"
                />
              </div>
              <p>
                跟踪 {{ formatCount(statistics.trackedFiles) }} 个文件，
                当前共 {{ formatCompact(statistics.totalLines) }} 行文本代码
              </p>
            </div>
          </section>
        </div>

        <section class="git-statistics__section">
          <header>
            <div>
              <strong>作者提交排名</strong>
              <span>按近一年有效提交量排序，最多显示前 10 名</span>
            </div>
          </header>
          <div v-if="topAuthors.length" class="git-statistics__ranking">
            <article
              v-for="(author, index) in topAuthors"
              :key="author.email || author.name"
            >
              <span class="git-statistics__rank">{{ index + 1 }}</span>
              <span class="git-statistics__author">
                <strong>{{ author.name }}</strong>
                <small>{{ author.email || '未配置邮箱' }}</small>
              </span>
              <span class="git-statistics__commits">
                {{ formatCount(author.commits) }}
                <small>提交</small>
              </span>
              <span class="git-statistics__author-change is-additions">
                +{{ formatCompact(author.additions) }}
              </span>
              <span class="git-statistics__author-change is-deletions">
                -{{ formatCompact(author.deletions) }}
              </span>
              <button
                class="git-statistics__author-view"
                type="button"
                :aria-label="`查看 ${author.name} 的提交记录`"
                title="查看提交记录"
                @click="selectedAuthor = author"
              >
                <ChevronRight :size="14" />
              </button>
            </article>
          </div>
          <div v-else class="git-statistics__empty">
            近一年没有可统计的提交记录
          </div>
        </section>
      </template>
    </div>

    <template #footer>
      <UiButton size="lg" @click="emit('close')">
        关闭
      </UiButton>
    </template>
  </UiDialog>

  <GitAuthorCommitsDialog
    v-if="selectedAuthor"
    :repository="repository"
    :author="selectedAuthor"
    @close="selectedAuthor = null"
  />
</template>

<style scoped>
.git-statistics {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.git-statistics__state {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  text-align: center;
  flex-direction: column;
  gap: var(--space-2);
}

.git-statistics__state > svg {
  color: var(--accent);
}

.git-statistics__state strong {
  color: var(--text);
  font-size: var(--font-title);
}

.git-statistics__state span {
  font-size: var(--font-caption);
}

.git-statistics__state.is-error > svg {
  color: var(--system-red);
}

.git-statistics__heading,
.git-statistics__section > header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.git-statistics__heading > div,
.git-statistics__section > header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.git-statistics__heading strong,
.git-statistics__section > header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.git-statistics__heading span,
.git-statistics__section > header span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-statistics__section > header em {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-style: normal;
}

.git-statistics__section > header em.is-negative {
  color: var(--system-red);
}

.git-statistics__metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-2);
}

.git-statistics__metrics article {
  display: grid;
  min-width: 0;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 2px var(--space-2);
  padding: var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.git-statistics__metrics article > span {
  display: grid;
  width: 24px;
  height: 24px;
  grid-row: span 2;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  place-items: center;
}

.git-statistics__metrics small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-statistics__metrics strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-title);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-statistics__metrics .is-additions strong,
.git-statistics__metrics .is-additions > span {
  color: var(--system-green);
}

.git-statistics__metrics .is-deletions strong,
.git-statistics__metrics .is-deletions > span {
  color: var(--system-red);
}

.git-statistics__section {
  display: flex;
  min-width: 0;
  padding: var(--space-4);
  flex-direction: column;
  gap: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.git-statistics__split {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: var(--space-3);
}

.git-statistics__month-chart {
  display: grid;
  min-height: 180px;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  align-items: end;
  gap: var(--space-2);
  padding-top: var(--space-2);
}

.git-statistics__month {
  display: grid;
  min-width: 0;
  grid-template-rows: 132px auto auto;
  gap: 4px;
  text-align: center;
}

.git-statistics__bar-track {
  display: flex;
  height: 132px;
  align-items: flex-end;
  overflow: hidden;
  background:
    repeating-linear-gradient(
      to top,
      var(--line-soft) 0,
      var(--line-soft) 1px,
      transparent 1px,
      transparent 33px
    );
  border-radius: var(--radius-badge);
}

.git-statistics__bar-track i {
  display: block;
  width: 100%;
  min-height: 2px;
  background: var(--accent);
  border-radius: 3px 3px 0 0;
  transition: height 220ms var(--ease-apple);
}

.git-statistics__month small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-statistics__month b {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
}

.git-statistics__hour-chart {
  display: grid;
  height: 132px;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  align-items: end;
  gap: 3px;
}

.git-statistics__hour-chart > div {
  display: grid;
  height: 100%;
  grid-template-rows: minmax(0, 1fr) 14px;
  align-items: end;
  gap: 3px;
}

.git-statistics__hour-chart > div > span {
  display: flex;
  height: 100%;
  align-items: flex-end;
}

.git-statistics__hour-chart i {
  display: block;
  width: 100%;
  min-height: 2px;
  background: var(--system-orange);
  border-radius: 2px 2px 0 0;
}

.git-statistics__hour-chart small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
  text-align: center;
}

.git-statistics__change-summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.git-statistics__change-summary > div:first-child {
  display: flex;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.git-statistics__change-summary > div:first-child span:first-child {
  color: var(--system-green);
}

.git-statistics__change-summary > div:first-child span:last-child {
  color: var(--system-red);
}

.git-statistics__change-track {
  display: flex;
  height: 8px;
  overflow: hidden;
  background: var(--surface-inset);
  border-radius: 999px;
}

.git-statistics__change-track .is-additions {
  background: var(--system-green);
}

.git-statistics__change-track .is-deletions {
  background: var(--system-red);
}

.git-statistics__change-summary p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.git-statistics__ranking {
  display: flex;
  flex-direction: column;
}

.git-statistics__ranking article {
  display: grid;
  min-height: 46px;
  grid-template-columns: 28px minmax(0, 1fr) 70px 72px 72px 24px;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.git-statistics__ranking article:last-child {
  border-bottom: 0;
}

.git-statistics__rank {
  display: grid;
  width: 24px;
  height: 24px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: 50%;
  place-items: center;
}

.git-statistics__ranking article:nth-child(-n + 3) .git-statistics__rank {
  color: var(--accent);
  background: var(--accent-soft);
}

.git-statistics__author {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.git-statistics__author strong,
.git-statistics__author small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-statistics__author strong {
  color: var(--text);
  font-size: var(--font-caption);
}

.git-statistics__author small {
  color: var(--text-subtle);
  font-size: 11px;
}

.git-statistics__commits,
.git-statistics__author-change {
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-align: right;
}

.git-statistics__commits {
  color: var(--text);
}

.git-statistics__commits small {
  margin-left: 2px;
  color: var(--text-subtle);
  font-size: 11px;
}

.git-statistics__author-change.is-additions {
  color: var(--system-green);
}

.git-statistics__author-change.is-deletions {
  color: var(--system-red);
}

.git-statistics__author-view {
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.git-statistics__author-view:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.git-statistics__empty {
  padding: var(--space-6);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
}

@media (max-width: 820px) {
  .git-statistics__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .git-statistics__split {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .git-statistics {
    padding: var(--space-3) var(--space-4) var(--space-4);
  }

  .git-statistics__heading,
  .git-statistics__section > header {
    align-items: flex-start;
    flex-direction: column;
  }

  .git-statistics__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .git-statistics__month-chart {
    gap: 4px;
  }

  .git-statistics__ranking article {
    grid-template-columns: 24px minmax(0, 1fr) 58px 58px 24px;
  }

  .git-statistics__author-change.is-deletions {
    display: none;
  }
}
</style>
