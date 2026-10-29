<script setup lang="ts">
import {
  ArrowRight,
  Bot,
  Check,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Clock3,
  EyeOff,
  FileCode2,
  GitBranch,
  GitCommitHorizontal,
  LoaderCircle,
  MessageSquareText,
  Plus,
  RefreshCw,
  ScanSearch,
  UserRoundCheck,
  Wrench,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import CodeReviewStartDialog, {
  type CodeReviewStartPayload,
} from '@/components/business/development/CodeReviewStartDialog.vue'
import CodeReviewFileDialog from '@/components/business/development/CodeReviewFileDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCodeReviewStore } from '@/stores/useCodeReviewStore'
import { useCodexStore } from '@/stores/useCodexStore'
import { useGitRepositoryStore } from '@/stores/useGitRepositoryStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  CodeReviewFinding,
  CodeReviewFindingStatus,
  CodeReviewRecord,
  CodeReviewStatus,
} from '@/types/codeReview'
import type { CodexTask } from '@/types/codex'
import {
  codeReviewPrompt,
  codeReviewRepairPrompt,
  parseCodeReviewResult,
} from '@/utils/codeReview'

type ReviewFilter = 'attention' | 'passed' | 'issues' | 'all'

const emit = defineEmits<{
  notify: [message: string]
  openCodex: [sessionId?: string]
}>()

const props = defineProps<{
  focusId?: string
}>()

const auth = useAuthStore()
const workspaces = useWorkspaceStore()
const repositories = useGitRepositoryStore()
const reviews = useCodeReviewStore()
const codex = useCodexStore()

const filter = ref<ReviewFilter>('attention')
const selectedId = ref('')
const startOpen = ref(false)
const previousForStart = ref<CodeReviewRecord | null>(null)
const manualNote = ref('')
const showAllFindings = ref(false)
const syncingTaskUpdates = ref(false)
const filePreview = ref<{
  repositoryPath: string
  revision: string
  filePath: string
  line: number | null
} | null>(null)

const workspaceReviews = computed(() =>
  reviews.records
    .filter(
      (record) =>
        record.workspaceId === workspaces.activeWorkspaceId,
    )
    .sort(
      (left, right) =>
        new Date(right.updatedAt).getTime() -
        new Date(left.updatedAt).getTime(),
    ),
)

const workspaceRepositories = computed(() =>
  repositories.repositories.filter(
    (repository) =>
      repository.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const counts = computed(() => ({
  attention: workspaceReviews.value.filter((record) =>
    ['reviewing', 'pending_manual', 'failed', 'repair_completed'].includes(
      record.status,
    ),
  ).length,
  passed: workspaceReviews.value.filter(
    (record) => record.status === 'passed',
  ).length,
  issues: workspaceReviews.value.filter((record) =>
    ['issues', 'repairing'].includes(record.status),
  ).length,
  all: workspaceReviews.value.length,
}))

const filteredReviews = computed(() => {
  if (filter.value === 'all') return workspaceReviews.value
  if (filter.value === 'passed') {
    return workspaceReviews.value.filter(
      (record) => record.status === 'passed',
    )
  }
  if (filter.value === 'issues') {
    return workspaceReviews.value.filter((record) =>
      ['issues', 'repairing'].includes(record.status),
    )
  }
  return workspaceReviews.value.filter((record) =>
    ['reviewing', 'pending_manual', 'failed', 'repair_completed'].includes(
      record.status,
    ),
  )
})

const selectedReview = computed(() => {
  const record = reviews.recordById(selectedId.value)
  return record?.workspaceId === workspaces.activeWorkspaceId
    ? record
    : null
})

watch(
  [() => props.focusId, workspaceReviews],
  ([focusId]) => {
    if (
      !focusId ||
      !workspaceReviews.value.some((record) => record.id === focusId)
    ) {
      return
    }
    filter.value = 'all'
    selectedId.value = focusId
  },
  { immediate: true },
)

const visibleFindings = computed(() => {
  const record = selectedReview.value
  if (!record) return []
  const active = record.findings.filter(
    (finding) =>
      finding.status !== 'dismissed' &&
      finding.status !== 'ignored',
  )
  if (
    record.status !== 'pending_manual' ||
    showAllFindings.value
  ) {
    return active
  }
  const manual = active.filter((finding) => finding.needsManual)
  return manual.length > 0 ? manual : active
})

const hiddenFindingCount = computed(() => {
  const record = selectedReview.value
  if (!record || record.status !== 'pending_manual') return 0
  return Math.max(0, record.findings.length - visibleFindings.value.length)
})

const actionableFindings = computed(
  () =>
    selectedReview.value?.findings.filter(
      (finding) =>
        finding.status === 'confirmed' ||
        (finding.status === 'pending' && finding.needsManual),
    ) ?? [],
)

watch(
  filteredReviews,
  () => {
    if (
      selectedReview.value &&
      filteredReviews.value.some(
        (record) => record.id === selectedReview.value?.id,
      )
    ) {
      return
    }
    selectedId.value =
      filteredReviews.value[0]?.id ?? workspaceReviews.value[0]?.id ?? ''
  },
  { immediate: true },
)

watch(
  () => selectedReview.value?.id,
  () => {
    manualNote.value = selectedReview.value?.manualNote ?? ''
    showAllFindings.value = false
  },
  { immediate: true },
)

onMounted(() => {
  codex.load(auth.email)
  void syncReviewTasks()
})

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    startOpen.value = false
    previousForStart.value = null
    manualNote.value = ''
  },
)

function openStart(previous: CodeReviewRecord | null = null) {
  previousForStart.value = previous
  startOpen.value = true
}

async function startReview(payload: CodeReviewStartPayload) {
  const previous = payload.previousReview
  const record = reviews.create({
    workspaceId: workspaces.activeWorkspaceId,
    repositoryId: payload.repository.id,
    repositoryName: payload.repository.name,
    repositoryPath: payload.repository.path,
    branch: payload.branch.refName,
    branchLabel: payload.branch.name,
    branchAuthor:
      payload.context.commits[0]?.authorName ?? '',
    branchAuthorEmail:
      payload.context.commits[0]?.authorEmail ?? '',
    reviewSince: payload.since,
    reviewUntil: payload.until,
    baseBranch: payload.baseBranch.name,
    baseCommit: payload.context.baseCommit,
    headCommit: payload.context.headCommit,
    fromCommit: payload.context.fromCommit,
    commitCount: payload.context.commitCount,
    fileCount: payload.context.fileCount,
    changedFiles: payload.context.changedFiles,
    additions: payload.context.additions,
    deletions: payload.context.deletions,
    focus: payload.focus,
    iteration: previous ? previous.iteration + 1 : payload.iteration,
    previousReviewId: previous?.id ?? null,
  })
  startOpen.value = false
  previousForStart.value = null
  selectedId.value = record.id
  codex.requestPrefill(
    record.workspaceId,
    record.repositoryPath,
    codeReviewPrompt(record),
    {
      codeReviewId: record.id,
      codeReviewMode: 'initial',
      codeReviewTitle: `代码初审：${record.repositoryName} · ${record.branchLabel}`,
      autoSend: true,
    },
  )
  emit(
    'notify',
    payload.incremental
      ? '已提交上次评审后的增量代码给 Codex'
      : 'Codex 正在初审分支代码',
  )
  emit('openCodex')
}

async function syncReviewTasks() {
  if (syncingTaskUpdates.value) return
  syncingTaskUpdates.value = true
  try {
    const tasks = codex.sessions.flatMap((session) =>
      session.tasks.map((task) => ({
        sessionId: session.id,
        task,
      })),
    )
    for (const record of workspaceReviews.value) {
      const initial = tasks.find(
        (entry) =>
          entry.task.codeReviewId === record.id &&
          entry.task.codeReviewMode !== 'repair',
      )
      const repair = tasks.find(
        (entry) =>
          entry.task.codeReviewId === record.id &&
          entry.task.codeReviewMode === 'repair',
      )
      const patch: Partial<CodeReviewRecord> = {}
      if (initial && initial.task.id !== record.codexTaskId) {
        patch.codexTaskId = initial.task.id
        patch.codexSessionId = initial.sessionId
      }
      if (repair && repair.task.id !== record.repairTaskId) {
        patch.repairTaskId = repair.task.id
        patch.repairSessionId = repair.sessionId
      }
      if (
        record.status === 'reviewing' &&
        initial &&
        ['completed', 'failed', 'interrupted'].includes(
          initial.task.status,
        )
      ) {
        Object.assign(
          patch,
          reviewPatchFromTask(record, initial.task),
        )
      }
      if (
        record.status === 'repairing' &&
        repair &&
        ['completed', 'failed', 'interrupted'].includes(
          repair.task.status,
        )
      ) {
        Object.assign(patch, {
          status:
            repair.task.status === 'completed'
              ? ('repair_completed' as const)
              : ('issues' as const),
          summary:
            repair.task.status === 'completed'
              ? 'Codex 已完成问题修复，改动尚未提交；请提交后再次评审。'
              : repair.task.error ||
                'Codex 修复任务未完成，请查看会话记录。',
          reviewedAt:
            repair.task.completedAt ?? new Date().toISOString(),
        })
      }
      if (Object.keys(patch).length > 0) {
        reviews.update(record.id, patch)
      }
    }
  } finally {
    syncingTaskUpdates.value = false
  }
}

function reviewPatchFromTask(
  record: CodeReviewRecord,
  task: CodexTask,
): Partial<CodeReviewRecord> {
  if (task.status !== 'completed') {
    return {
      status: 'failed',
      summary:
        task.error ||
        'Codex 初审未完成，请查看会话记录后重试。',
      reviewedAt:
        task.completedAt ?? new Date().toISOString(),
    }
  }
  const parsed = parseCodeReviewResult(
    task.response,
    record.changedFiles ?? [],
  )
  if (!parsed) {
    return {
      status: 'pending_manual',
      summary:
        'Codex 初审结果无法解析，已转人工处理，不会自动通过。',
      rawResult: task.response.slice(0, 120_000),
      reviewedAt:
        task.completedAt ?? new Date().toISOString(),
    }
  }
  return {
    status:
      parsed.verdict === 'manual'
        ? 'pending_manual'
        : 'passed',
    summary: parsed.summary,
    findings: parsed.findings,
    rawResult: task.response.slice(0, 120_000),
    reviewedAt:
      task.completedAt ?? new Date().toISOString(),
    iteration: record.iteration,
  }
}

function setFindingStatus(
  finding: CodeReviewFinding,
  status: CodeReviewFindingStatus,
) {
  const record = selectedReview.value
  if (!record) return
  reviews.update(record.id, {
    findings: record.findings.map((item) =>
      item.id === finding.id ? { ...item, status } : item,
    ),
  })
}

function openFindingFile(finding: CodeReviewFinding) {
  const record = selectedReview.value
  if (!record) return
  filePreview.value = {
    repositoryPath: record.repositoryPath,
    revision: record.headCommit,
    filePath: finding.file,
    line: finding.line,
  }
}

function saveManualNote() {
  const record = selectedReview.value
  if (!record) return
  reviews.update(record.id, {
    manualNote: manualNote.value.trim(),
  })
  emit('notify', '人工评审备注已保存')
}

function markPassed() {
  const record = selectedReview.value
  if (!record) return
  reviews.update(record.id, {
    status: 'passed',
    manualNote: manualNote.value.trim(),
    reviewedAt: new Date().toISOString(),
  })
  emit('notify', '本次代码评审已标记为通过')
}

function markIssues() {
  const record = selectedReview.value
  if (!record) return
  if (
    !manualNote.value.trim() &&
    actionableFindings.value.length === 0
  ) {
    emit('notify', '请填写人工备注或确认至少一个问题')
    return
  }
  reviews.update(record.id, {
    status: 'issues',
    manualNote: manualNote.value.trim(),
    reviewedAt: new Date().toISOString(),
  })
  emit('notify', '已标记本次评审存在问题')
}

function transferToCodex() {
  const record = selectedReview.value
  if (!record) return
  const note = manualNote.value.trim()
  const findings = actionableFindings.value
  if (!note && findings.length === 0) {
    emit('notify', '请填写备注或确认需要修复的问题')
    return
  }
  const next = reviews.update(record.id, {
    status: 'repairing',
    manualNote: note,
    reviewedAt: new Date().toISOString(),
  })
  if (!next) return
  codex.requestPrefill(
    record.workspaceId,
    record.repositoryPath,
    codeReviewRepairPrompt(next, findings),
    {
      codeReviewId: record.id,
      codeReviewMode: 'repair',
      codeReviewTitle: `代码评审修复：${record.repositoryName} · ${record.branchLabel}`,
      autoSend: true,
    },
  )
  emit('notify', '评审问题已交给 Codex 修改')
  emit('openCodex')
}

function openReviewSession(record: CodeReviewRecord) {
  const sessionId =
    record.codexSessionId || record.repairSessionId
  emit('openCodex', sessionId || undefined)
}

function statusLabel(status: CodeReviewStatus) {
  const labels: Record<CodeReviewStatus, string> = {
    reviewing: 'Codex 初审中',
    pending_manual: '待人工审核',
    passed: '初审通过',
    issues: '已标记问题',
    failed: '初审失败',
    repairing: 'Codex 修改中',
    repair_completed: '修改完成，待复审',
  }
  return labels[status]
}

function statusIcon(status: CodeReviewStatus) {
  if (status === 'reviewing' || status === 'repairing') {
    return LoaderCircle
  }
  if (status === 'passed') return CircleCheck
  if (status === 'pending_manual' || status === 'repair_completed') {
    return UserRoundCheck
  }
  if (status === 'failed') return CircleAlert
  return Wrench
}

function filterLabel(value: ReviewFilter) {
  const labels: Record<ReviewFilter, string> = {
    attention: '待处理',
    passed: '已通过',
    issues: '有问题',
    all: '全部记录',
  }
  return labels[value]
}

function severityLabel(finding: CodeReviewFinding) {
  if (finding.severity === 'blocking') return '阻断'
  if (finding.severity === 'suggestion') return '建议'
  return '风险'
}

function reviewRangeLabel(record: CodeReviewRecord) {
  if (!record.reviewSince && !record.reviewUntil) {
    return '全部已提交代码'
  }
  if (
    !record.reviewUntil ||
    record.reviewSince === record.reviewUntil
  ) {
    return `${record.reviewSince || record.reviewUntil} 提交`
  }
  return `${record.reviewSince} 至 ${record.reviewUntil} 提交`
}

function formatTime(value: string | null) {
  if (!value) return '未完成'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <section class="code-review-page">
    <header class="code-review-header">
      <div>
        <h2>评审记录</h2>
        <p>Codex 先检查已提交分支，只把需要判断的问题交给你。</p>
      </div>
      <UiButton
        variant="primary"
        :disabled="
          workspaceRepositories.filter(
            (repository) =>
              repository.source === 'local' && repository.path,
          ).length === 0
        "
        @click="openStart()"
      >
        <ScanSearch :size="14" />
        发起评审
      </UiButton>
    </header>

    <nav class="code-review-tabs" aria-label="代码评审筛选">
      <button
        v-for="item in (['attention', 'passed', 'issues', 'all'] as ReviewFilter[])"
        :key="item"
        type="button"
        :class="{ 'code-review-tab--active': filter === item }"
        @click="filter = item"
      >
        {{ filterLabel(item) }}
        <span>{{ counts[item] }}</span>
      </button>
      <UiButton
        size="sm"
        variant="ghost"
        title="刷新 Codex 初审和修复结果"
        @click="syncReviewTasks"
      >
        <RefreshCw :size="13" />
      </UiButton>
    </nav>

    <div v-if="workspaceReviews.length === 0" class="code-review-empty">
      <span><ScanSearch :size="26" /></span>
      <strong>还没有代码评审记录</strong>
      <p>选择一个组员分支，Codex 会先分析完整差异。</p>
      <UiButton
        variant="primary"
        :disabled="
          workspaceRepositories.filter(
            (repository) =>
              repository.source === 'local' && repository.path,
          ).length === 0
        "
        @click="openStart()"
      >
        <Plus :size="14" />
        发起第一次评审
      </UiButton>
    </div>

    <div v-else class="code-review-layout">
      <aside class="code-review-records" aria-label="代码评审记录">
        <button
          v-for="record in filteredReviews"
          :key="record.id"
          class="code-review-row"
          :class="{
            'code-review-row--active': selectedId === record.id,
          }"
          type="button"
          @click="selectedId = record.id"
        >
          <span
            class="code-review-row__status"
            :class="`code-review-row__status--${record.status}`"
          >
            <component :is="statusIcon(record.status)" :size="15" />
          </span>
          <span class="code-review-row__copy">
            <strong>{{ record.repositoryName }}</strong>
            <small>
              <GitBranch :size="11" />
              {{ record.branchLabel }}
              <template v-if="record.branchAuthor">
                · {{ record.branchAuthor }}
              </template>
              <span>· 第 {{ record.iteration }} 次</span>
            </small>
            <span>
              {{ statusLabel(record.status) }}
              · {{ formatTime(record.updatedAt) }}
            </span>
          </span>
          <ChevronRight :size="14" />
        </button>
        <div v-if="filteredReviews.length === 0" class="code-review-list-empty">
          当前分类没有评审记录
        </div>
      </aside>

      <main v-if="selectedReview" class="code-review-detail">
        <header class="code-review-detail__header">
          <div>
            <span
              class="code-review-status"
              :class="`code-review-status--${selectedReview.status}`"
            >
              <component
                :is="statusIcon(selectedReview.status)"
                :size="13"
              />
              {{ statusLabel(selectedReview.status) }}
            </span>
            <h3>
              {{ selectedReview.repositoryName }}
              <code>{{ selectedReview.branchLabel }}</code>
            </h3>
            <p>
              对比 {{ selectedReview.baseBranch }}
              <template v-if="selectedReview.branchAuthor">
                · 提交者 {{ selectedReview.branchAuthor }}
              </template>
              · 第 {{ selectedReview.iteration }} 次评审
              · {{ reviewRangeLabel(selectedReview) }}
            </p>
          </div>
          <div class="code-review-detail__actions">
            <UiButton
              v-if="
                selectedReview.codexTaskId ||
                selectedReview.repairTaskId
              "
              size="sm"
              @click="openReviewSession(selectedReview)"
            >
              <Bot :size="13" />
              打开 Codex
            </UiButton>
            <UiButton
              v-if="
                ['issues', 'passed', 'repair_completed'].includes(
                  selectedReview.status,
                )
              "
              size="sm"
              @click="openStart(selectedReview)"
            >
              <RefreshCw :size="13" />
              再次评审
            </UiButton>
          </div>
        </header>

        <section class="code-review-metrics">
          <div>
            <span><GitCommitHorizontal :size="13" /> 提交</span>
            <strong>{{ selectedReview.commitCount }}</strong>
          </div>
          <div>
            <span><FileCode2 :size="13" /> 文件</span>
            <strong>{{ selectedReview.fileCount }}</strong>
          </div>
          <div>
            <span>新增</span>
            <strong class="is-add">+{{ selectedReview.additions }}</strong>
          </div>
          <div>
            <span>删除</span>
            <strong class="is-delete">-{{ selectedReview.deletions }}</strong>
          </div>
        </section>

        <section class="code-review-summary">
          <header>
            <Bot :size="15" />
            <strong>Codex 初审结论</strong>
            <span>{{ formatTime(selectedReview.reviewedAt) }}</span>
          </header>
          <p>
            {{
              selectedReview.summary ||
                (selectedReview.status === 'reviewing'
                  ? 'Codex 正在阅读分支差异…'
                  : '暂无初审摘要')
            }}
          </p>
          <div
            v-if="selectedReview.status === 'reviewing'"
            class="code-review-running"
          >
            <UiFidgetSpinner :size="14" />
            初审会在 Codex 会话完成后自动生成记录
          </div>
        </section>

        <section
          v-if="visibleFindings.length > 0"
          class="code-review-findings"
        >
          <header>
            <div>
              <h3>需要人工判断</h3>
              <p>确认后会纳入转交 Codex 的修复范围。</p>
            </div>
            <button
              v-if="hiddenFindingCount > 0"
              type="button"
              @click="showAllFindings = !showAllFindings"
            >
              {{
                showAllFindings
                  ? '只看人工项'
                  : `查看全部 ${selectedReview.findings.length} 项`
              }}
            </button>
          </header>
          <article
            v-for="finding in visibleFindings"
            :key="finding.id"
            class="code-review-finding"
            :class="{
              'code-review-finding--confirmed':
                finding.status === 'confirmed',
              'code-review-finding--dismissed':
                finding.status === 'dismissed',
              'code-review-finding--ignored':
                finding.status === 'ignored',
            }"
          >
            <span class="code-review-finding__severity">
              {{ severityLabel(finding) }}
            </span>
            <div class="code-review-finding__copy">
              <h4>{{ finding.title }}</h4>
              <button
                class="code-review-finding__file"
                type="button"
                :aria-label="`查看文件 ${finding.file} 的内容`"
                title="查看文件内容并定位到问题行"
                @click="openFindingFile(finding)"
              >
                <FileCode2 :size="12" />
                <code>
                  {{ finding.file
                  }}{{ finding.line ? `:${finding.line}` : '' }}
                </code>
              </button>
              <p>{{ finding.reason || 'Codex 未提供详细原因' }}</p>
              <small v-if="finding.suggestion">
                建议：{{ finding.suggestion }}
              </small>
            </div>
            <div class="code-review-finding__actions">
              <button
                type="button"
                :class="{
                  'is-active': finding.status === 'confirmed',
                }"
                @click="setFindingStatus(finding, 'confirmed')"
              >
                <CircleAlert :size="12" />
                确认问题
              </button>
              <button
                type="button"
                :class="{
                  'is-active': finding.status === 'dismissed',
                }"
                @click="setFindingStatus(finding, 'dismissed')"
              >
                <Check :size="12" />
                误报
              </button>
              <button
                type="button"
                :class="{
                  'is-active': finding.status === 'ignored',
                }"
                @click="setFindingStatus(finding, 'ignored')"
              >
                <EyeOff :size="12" />
                忽略
              </button>
            </div>
          </article>
        </section>

        <section class="code-review-manual">
          <header>
            <MessageSquareText :size="15" />
            <div>
              <h3>人工审核备注</h3>
              <p>记录问题、验收要求或转交 Codex 的补充说明。</p>
            </div>
          </header>
          <UiTextarea
            v-model="manualNote"
            :rows="4"
            maxlength="6000"
            placeholder="填写人工审核发现的问题或修改要求"
          />
          <div class="code-review-manual__actions">
            <UiButton size="sm" @click="saveManualNote">
              保存备注
            </UiButton>
          </div>
        </section>

        <footer class="code-review-detail__footer">
          <UiButton
            size="lg"
            :disabled="
              selectedReview.status === 'reviewing' ||
              selectedReview.status === 'repairing'
            "
            @click="markPassed"
          >
            <CircleCheck :size="14" />
            标记通过
          </UiButton>
          <UiButton
            size="lg"
            :disabled="
              selectedReview.status === 'reviewing' ||
              selectedReview.status === 'repairing'
            "
            @click="markIssues"
          >
            <CircleAlert :size="14" />
            标记有问题
          </UiButton>
          <UiButton
            variant="primary"
            size="lg"
            :disabled="
              selectedReview.status === 'reviewing' ||
              selectedReview.status === 'repairing'
            "
            @click="transferToCodex"
          >
            <Wrench :size="14" />
            转交 Codex 修改
            <ArrowRight :size="13" />
          </UiButton>
        </footer>
      </main>
    </div>

    <CodeReviewFileDialog
      v-if="filePreview"
      :repository-path="filePreview.repositoryPath"
      :revision="filePreview.revision"
      :file-path="filePreview.filePath"
      :line="filePreview.line"
      @close="filePreview = null"
    />

    <CodeReviewStartDialog
      v-if="startOpen"
      :repositories="workspaceRepositories"
      :previous-review="previousForStart"
      :reviews="workspaceReviews"
      @close="startOpen = false"
      @submit="startReview"
    />
  </section>
</template>

<style scoped>
.code-review-page {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
}

.code-review-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.code-review-header h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.code-review-header p {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding-bottom: var(--space-1);
  border-bottom: 1px solid var(--line-soft);
}

.code-review-tabs > button:not(.ui-button) {
  display: inline-flex;
  height: 32px;
  align-items: center;
  gap: 6px;
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-body);
  background: transparent;
  border-radius: var(--radius-control);
}

.code-review-tabs > button:not(.ui-button):hover {
  color: var(--text);
  background: var(--hover-bg);
}

.code-review-tabs .code-review-tab--active {
  color: var(--text);
  font-weight: var(--weight-emphasis);
  background: var(--selection-bg);
}

.code-review-tabs button span {
  min-width: 18px;
  padding: 1px 5px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-align: center;
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.code-review-tabs > .ui-button {
  margin-left: auto;
}

.code-review-layout {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(230px, 0.7fr) minmax(0, 2fr);
  gap: var(--space-4);
}

.code-review-records {
  min-height: 0;
  overflow-y: auto;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.code-review-row {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: 28px minmax(0, 1fr) 14px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-3);
  color: var(--text-subtle);
  text-align: left;
  background: transparent;
  border-bottom: 1px solid var(--line-soft);
}

.code-review-row:last-child {
  border-bottom: 0;
}

.code-review-row:hover {
  background: var(--hover-bg);
}

.code-review-row--active {
  background: var(--selection-bg);
}

.code-review-row__status {
  display: grid;
  width: 28px;
  height: 28px;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
  place-items: center;
}

.code-review-row__status--passed {
  color: var(--system-green);
}

.code-review-row__status--pending_manual,
.code-review-row__status--repair_completed {
  color: var(--system-orange);
}

.code-review-row__status--issues,
.code-review-row__status--failed {
  color: var(--system-red);
}

.code-review-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.code-review-row__copy strong,
.code-review-row__copy small,
.code-review-row__copy > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-review-row__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.code-review-row__copy small {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.code-review-row__copy > span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-list-empty {
  padding: var(--space-5);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
}

.code-review-detail {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.code-review-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.code-review-detail__header h3 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-2) 0 0;
  color: var(--text);
  font-size: var(--font-title);
}

.code-review-detail__header h3 code {
  overflow: hidden;
  padding: 2px 6px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-review-detail__header p {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px 6px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.code-review-status--passed {
  color: var(--system-green);
}

.code-review-status--issues,
.code-review-status--failed {
  color: var(--system-red);
}

.code-review-status--pending_manual,
.code-review-status--repair_completed {
  color: var(--system-orange);
}

.code-review-detail__actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--space-2);
}

.code-review-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
}

.code-review-metrics > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.code-review-metrics span {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-metrics strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-body);
}

.code-review-metrics .is-add {
  color: var(--system-green);
}

.code-review-metrics .is-delete {
  color: var(--system-red);
}

.code-review-summary,
.code-review-findings,
.code-review-manual {
  padding: 0 var(--space-4) var(--space-4);
}

.code-review-summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.code-review-summary header {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--accent);
}

.code-review-summary header strong {
  color: var(--text);
  font-size: var(--font-body);
}

.code-review-summary header span {
  margin-left: auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-summary p {
  padding: var(--space-3);
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-body);
  line-height: var(--line-body);
  white-space: pre-wrap;
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.code-review-running {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-findings > header,
.code-review-manual > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.code-review-findings h3,
.code-review-manual h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.code-review-findings header p,
.code-review-manual header p {
  margin: 2px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-findings > header > button {
  color: var(--accent);
  font-size: var(--font-caption);
  background: transparent;
}

.code-review-finding {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: var(--space-2);
  padding: var(--space-3) 0;
  border-top: 1px solid var(--line-soft);
}

.code-review-finding--dismissed,
.code-review-finding--ignored {
  opacity: 0.45;
}

.code-review-finding--confirmed {
  background: color-mix(
    in srgb,
    var(--system-orange) 5%,
    transparent
  );
}

.code-review-finding__severity {
  align-self: start;
  padding: 2px 4px;
  color: var(--system-orange);
  font-size: var(--font-caption);
  text-align: center;
  background: color-mix(
    in srgb,
    var(--system-orange) 10%,
    transparent
  );
  border-radius: var(--radius-badge);
}

.code-review-finding__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.code-review-finding__copy h4 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.code-review-finding__file {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  align-items: center;
  gap: 4px;
  padding: 0;
  color: var(--accent);
  text-align: left;
  background: transparent;
  border-radius: var(--radius-badge);
}

.code-review-finding__file:hover {
  background: var(--accent-soft);
}

.code-review-finding__file code {
  overflow: hidden;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-review-finding__file:hover code {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.code-review-finding__copy p,
.code-review-finding__copy small {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.code-review-finding__copy small {
  color: var(--text-subtle);
}

.code-review-finding__actions {
  display: flex;
  align-items: flex-start;
  gap: var(--space-1);
}

.code-review-finding__actions button {
  display: inline-flex;
  height: 24px;
  align-items: center;
  gap: 3px;
  padding: 0 6px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
}

.code-review-finding__actions button:hover,
.code-review-finding__actions button.is-active {
  color: var(--text);
  background: var(--selection-bg);
}

.code-review-manual > header {
  justify-content: flex-start;
  color: var(--accent);
}

.code-review-manual__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.code-review-detail__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--line-soft);
}

.code-review-empty {
  display: flex;
  min-height: 380px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  text-align: center;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.code-review-empty > span {
  display: grid;
  width: 48px;
  height: 48px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.code-review-empty strong {
  color: var(--text);
  font-size: var(--font-body);
}

.code-review-empty p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

@media (max-width: 980px) {
  .code-review-page {
    padding: var(--space-4);
  }

  .code-review-layout {
    grid-template-columns: 1fr;
  }

  .code-review-records {
    max-height: 250px;
  }

  .code-review-detail__header {
    flex-direction: column;
  }

  .code-review-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .code-review-finding {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .code-review-finding__actions {
    grid-column: 2;
  }

  .code-review-detail__footer {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 560px) {
  .code-review-page {
    padding: var(--space-3);
  }

  .code-review-header {
    align-items: stretch;
    flex-direction: column;
  }

  .code-review-tabs {
    overflow-x: auto;
  }

  .code-review-tabs > button:not(.ui-button) {
    flex: 0 0 auto;
    padding: 0 var(--space-1);
    font-size: var(--font-caption);
  }

  .code-review-detail__actions {
    flex-wrap: wrap;
  }
}
</style>
