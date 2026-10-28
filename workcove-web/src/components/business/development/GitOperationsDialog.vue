<script setup lang="ts">
import {
  ArrowDownToLine,
  ArrowUpFromLine,
  Bot,
  Check,
  CircleAlert,
  Download,
  FileCode2,
  GitBranch,
  GitCommitHorizontal,
  GitMerge,
  History,
  LoaderCircle,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Trash2,
  Upload,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import {
  executeGitOperation,
  listGitBranches,
  listGitCommits,
} from '@/api/gitRepositories'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import type {
  GitBranchInfo,
  GitChangedFile,
  GitCommitInfo,
  GitOperationInput,
  GitOperationResult,
  GitOperationType,
  GitRepository,
  GitRepositoryStatus,
} from '@/types/gitRepository'

type OperationTab = 'operations' | 'branches' | 'history'
type FeedbackTone = 'success' | 'warning' | 'error'
type CommitTone = 'blue' | 'red' | 'orange' | 'green' | 'gray'

interface CommitPresentation {
  type: string
  label: string
  scope: string
  summary: string
  tone: CommitTone
}

const COMMIT_TYPE_META: Record<
  string,
  { label: string; tone: CommitTone }
> = {
  feat: { label: '功能', tone: 'blue' },
  fix: { label: '修复', tone: 'red' },
  refactor: { label: '重构', tone: 'orange' },
  perf: { label: '性能', tone: 'orange' },
  test: { label: '测试', tone: 'green' },
  docs: { label: '文档', tone: 'gray' },
  build: { label: '构建', tone: 'gray' },
  chore: { label: '维护', tone: 'gray' },
  ci: { label: '流水线', tone: 'gray' },
  style: { label: '样式', tone: 'gray' },
  revert: { label: '回退', tone: 'red' },
  merge: { label: '合并', tone: 'blue' },
}

const props = defineProps<{
  repository: GitRepository
  status: GitRepositoryStatus | null
}>()

const emit = defineEmits<{
  close: []
  changed: []
  notify: [message: string]
  solveConflicts: [
    payload: {
      repository: GitRepository
      conflictFiles: string[]
    },
  ]
}>()

const tabItems = [
  { value: 'operations', label: '操作' },
  { value: 'branches', label: '分支' },
  { value: 'history', label: '提交记录' },
]
const activeTab = ref<OperationTab>('operations')
const branches = ref<GitBranchInfo[]>([])
const commits = ref<GitCommitInfo[]>([])
const branchSearch = ref('')
const newBranchName = ref('')
const selectedMergeBranch = ref('')
const mergeDialogOpen = ref(false)
const commitMessage = ref('')
const operationBusy = ref<GitOperationType | ''>('')
const branchBusyName = ref('')
const loadingBranches = ref(false)
const loadingHistory = ref(false)
const dataError = ref('')
const feedback = ref('')
const feedbackTone = ref<FeedbackTone>('success')
const commandOutput = ref('')
const lastResult = ref<GitOperationResult | null>(null)
const pushSetUpstream = ref(false)
const pendingDeleteBranch = ref('')
const forceDeleteBranch = ref(false)
const deleteError = ref('')

const currentBranchName = computed(
  () =>
    branches.value.find((branch) => branch.current)?.name ||
    props.status?.branch ||
    '当前分支',
)
const localBranches = computed(() =>
  branches.value.filter((branch) => !branch.remote),
)
const remoteBranches = computed(() =>
  branches.value.filter((branch) => branch.remote),
)
const filteredBranches = computed(() => {
  const query = branchSearch.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return branches.value
  return branches.value.filter((branch) =>
    [
      branch.name,
      branch.upstream,
      branch.latestCommit,
      branch.remote ? '远端' : '本地',
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})
const mergeOptions = computed(() =>
  branches.value
    .filter((branch) => !branch.current)
    .map((branch) => ({
      value: branch.name,
      label: branch.name,
      description: branch.remote
        ? '远端分支'
        : `本地 · ${branch.latestCommit}`,
    })),
)
const commitSummary = computed(() => {
  const counts = new Map<
    string,
    { label: string; tone: CommitTone; count: number }
  >()
  for (const commit of commits.value) {
    const presentation = commitPresentation(commit.subject)
    const key = presentation.type || 'other'
    const current = counts.get(key)
    counts.set(key, {
      label: presentation.label,
      tone: presentation.tone,
      count: (current?.count ?? 0) + 1,
    })
  }
  return [...counts.entries()]
    .sort((left, right) => right[1].count - left[1].count)
    .slice(0, 5)
    .map(([type, value]) => ({ type, ...value }))
})
const presentedCommits = computed(() =>
  commits.value.map((commit) => ({
    commit,
    presentation: commitPresentation(commit.subject),
  })),
)
const conflictFiles = computed(() => {
  if (props.status?.conflictFiles.length) {
    return props.status.conflictFiles
  }
  return lastResult.value?.conflictFiles ?? []
})
const mergeInProgress = computed(
  () =>
    Boolean(props.status?.mergeInProgress) ||
    Boolean(lastResult.value?.mergeInProgress),
)
const busy = computed(() => Boolean(operationBusy.value))
const changedFiles = computed(() => {
  const status = props.status
  if (!status) return 0
  if (status.changedFiles.length > 0) {
    return status.changedFiles.length
  }
  return (
    status.staged +
    status.unstaged +
    status.untracked +
    status.conflicted
  )
})
const changedFileItems = computed(
  () => props.status?.changedFiles ?? [],
)

function changedFileLabel(file: GitChangedFile) {
  if (file.conflicted) return '冲突'
  if (file.untracked) return '未跟踪'
  if (file.staged && file.unstaged) return '部分暂存'
  if (file.staged) return '已暂存'
  return '已修改'
}

function changedFileTone(file: GitChangedFile) {
  if (file.conflicted) return 'conflict'
  if (file.untracked) return 'untracked'
  if (file.staged && file.unstaged) return 'mixed'
  if (file.staged) return 'staged'
  return 'modified'
}

function changedFileName(path: string) {
  return path.split('/').filter(Boolean).at(-1) ?? path
}

function changedFileDirectory(path: string) {
  const segments = path.split('/').filter(Boolean)
  return segments.length > 1
    ? segments.slice(0, -1).join('/')
    : '仓库根目录'
}

function changedFileCode(file: GitChangedFile) {
  return `${file.indexStatus}${file.workTreeStatus}`.trim() || '?'
}

watch(
  mergeOptions,
  (options) => {
    if (
      selectedMergeBranch.value &&
      options.some((option) => option.value === selectedMergeBranch.value)
    ) {
      return
    }
    selectedMergeBranch.value = options[0]?.value ?? ''
  },
  { immediate: true },
)

onMounted(() => {
  void refreshRepositoryData()
})

async function refreshRepositoryData() {
  dataError.value = ''
  await Promise.all([loadBranches(), loadHistory()])
}

async function loadBranches() {
  if (loadingBranches.value) return
  loadingBranches.value = true
  try {
    branches.value = await listGitBranches(props.repository.path)
  } catch (error) {
    dataError.value = errorMessage(error, '无法读取 Git 分支')
  } finally {
    loadingBranches.value = false
  }
}

async function loadHistory() {
  if (loadingHistory.value) return
  loadingHistory.value = true
  try {
    commits.value = await listGitCommits(props.repository.path, 100)
  } catch (error) {
    dataError.value = errorMessage(error, '无法读取 Git 提交历史')
  } finally {
    loadingHistory.value = false
  }
}

async function runOperation(
  type: GitOperationType,
  extra: Omit<Partial<GitOperationInput>, 'path' | 'type'> = {},
) {
  if (busy.value) return null
  operationBusy.value = type
  feedback.value = ''
  commandOutput.value = ''
  try {
    const result = await executeGitOperation({
      path: props.repository.path,
      type,
      ...extra,
    })
    lastResult.value = result
    commandOutput.value = result.output
    feedback.value = result.message
    if (result.success) {
      feedbackTone.value = 'success'
      emit('notify', result.message)
      if (type === 'COMMIT') commitMessage.value = ''
    } else {
      feedbackTone.value = result.conflictFiles.length
        ? 'warning'
        : 'error'
    }
    emit('changed')
    await Promise.all([loadBranches(), loadHistory()])
    return result
  } catch (error) {
    feedbackTone.value = 'error'
    feedback.value = errorMessage(error, 'Git 操作失败')
    return null
  } finally {
    operationBusy.value = ''
  }
}

function createBranch() {
  const branchName = newBranchName.value.trim()
  if (!branchName) {
    feedbackTone.value = 'error'
    feedback.value = '请输入分支名称'
    return
  }
  void runOperation('CREATE_BRANCH', { branchName }).then((result) => {
    if (result?.success) newBranchName.value = ''
  })
}

function switchBranch(branch: GitBranchInfo) {
  if (branch.current || branch.remote || busy.value) return
  branchBusyName.value = branch.name
  void runOperation('SWITCH_BRANCH', {
    branchName: branch.name,
  }).finally(() => {
    branchBusyName.value = ''
  })
}

function requestDeleteBranch(branch: GitBranchInfo) {
  if (branch.current || branch.remote || busy.value) return
  pendingDeleteBranch.value = branch.name
  forceDeleteBranch.value = false
  deleteError.value = ''
}

async function confirmDeleteBranch() {
  const branchName = pendingDeleteBranch.value
  if (!branchName) return
  deleteError.value = ''
  branchBusyName.value = branchName
  const result = await runOperation('DELETE_BRANCH', {
    branchName,
    force: forceDeleteBranch.value,
  })
  branchBusyName.value = ''
  if (result?.success) {
    pendingDeleteBranch.value = ''
    forceDeleteBranch.value = false
    return
  }
  deleteError.value =
    result?.message || '删除分支失败，请检查分支是否已合并'
}

function openMergeDialog() {
  if (busy.value || mergeInProgress.value || mergeOptions.value.length === 0) {
    return
  }
  mergeDialogOpen.value = true
}

function closeMergeDialog() {
  if (operationBusy.value === 'MERGE') return
  mergeDialogOpen.value = false
}

async function mergeSelectedBranch() {
  const sourceBranch = selectedMergeBranch.value
  if (!sourceBranch) {
    feedbackTone.value = 'error'
    feedback.value = '请选择要合并的分支'
    return
  }
  const result = await runOperation('MERGE', { sourceBranch })
  if (result) mergeDialogOpen.value = false
}

function stageAll() {
  void runOperation('STAGE_ALL')
}

function commitChanges() {
  const message = commitMessage.value.trim()
  if (!message) {
    feedbackTone.value = 'error'
    feedback.value = '请输入提交说明'
    return
  }
  void runOperation('COMMIT', { commitMessage: message })
}

function solveWithCodex() {
  if (conflictFiles.value.length === 0) {
    emit('notify', '当前没有检测到冲突文件')
    return
  }
  emit('solveConflicts', {
    repository: props.repository,
    conflictFiles: [...conflictFiles.value],
  })
  emit('close')
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function formatDate(value: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function branchTypeLabel(branch: GitBranchInfo) {
  if (branch.current) return '当前'
  return branch.remote ? '远端' : '本地'
}

function branchTone(branch: GitBranchInfo) {
  if (branch.current) return 'green'
  return branch.remote ? 'gray' : 'blue'
}

function commitPresentation(subject: string): CommitPresentation {
  const match = subject.match(
    /^([a-z]+)(?:\(([^)]*)\))?!?:\s*(.+)$/i,
  )
  if (!match) {
    return {
      type: '',
      label: '提交',
      scope: '',
      summary: subject,
      tone: 'gray',
    }
  }
  const type = match[1]?.toLocaleLowerCase('en-US') ?? ''
  const meta = COMMIT_TYPE_META[type] ?? {
    label: type || '提交',
    tone: 'gray' as CommitTone,
  }
  return {
    type,
    label: meta.label,
    scope: match[2]?.trim() ?? '',
    summary: match[3]?.trim() || subject,
    tone: meta.tone,
  }
}
</script>

<template>
  <UiDialog
    :title="repository.name"
    :description="`${repository.path} · ${currentBranchName}`"
    size="wide"
    height="tall"
    scroll-mode="body"
    :suspended="Boolean(pendingDeleteBranch)"
    @close="emit('close')"
  >
    <div class="git-operations">
      <section class="git-operations__summary">
        <span class="git-operations__summary-icon">
          <GitBranch :size="18" />
        </span>
        <div>
          <strong>{{ currentBranchName }}</strong>
          <small>
            {{ changedFiles }} 个变更
            <template v-if="status?.ahead">
              · 领先 {{ status.ahead }}
            </template>
            <template v-if="status?.behind">
              · 落后 {{ status.behind }}
            </template>
          </small>
        </div>
        <span
          class="git-operations__state"
          :class="{
            'git-operations__state--clean': status?.clean,
          }"
        >
          <Check v-if="status?.clean" :size="12" />
          <CircleAlert v-else :size="12" />
          {{
            mergeInProgress
              ? '合并待继续'
              : status?.clean
                ? '工作区干净'
                : '工作区有变更'
          }}
        </span>
      </section>

      <div class="git-operations__tabs">
        <UiSegmentedControl
          v-model="activeTab"
          :items="tabItems"
          aria-label="Git 操作视图"
        />
        <UiButton
          size="sm"
          :disabled="loadingBranches || loadingHistory || busy"
          title="刷新分支和提交记录"
          @click="refreshRepositoryData"
        >
          <RefreshCw
            :size="13"
            :class="{
              'git-operations__spin': loadingBranches || loadingHistory,
            }"
          />
          刷新
        </UiButton>
      </div>

      <div class="git-operations__body">
        <p v-if="dataError" class="git-operations__message is-error">
          <CircleAlert :size="14" />
          {{ dataError }}
        </p>
        <p
          v-if="feedback"
          class="git-operations__message"
          :class="`is-${feedbackTone}`"
        >
          <Check v-if="feedbackTone === 'success'" :size="14" />
          <CircleAlert v-else :size="14" />
          {{ feedback }}
        </p>

        <section
          v-if="mergeInProgress"
          class="git-operations__conflict"
        >
          <div class="git-operations__conflict-heading">
            <span><GitMerge :size="17" /></span>
            <div>
              <strong>
                {{
                  conflictFiles.length
                    ? `${conflictFiles.length} 个文件存在冲突`
                    : '合并尚未完成'
                }}
              </strong>
              <p>
                解决冲突标记后继续合并，或者中止本次合并。
              </p>
            </div>
          </div>
          <ul v-if="conflictFiles.length">
            <li
              v-for="file in conflictFiles"
              :key="file"
            >
              {{ file }}
            </li>
          </ul>
          <div class="git-operations__conflict-actions">
            <UiButton
              size="sm"
              :disabled="busy"
              @click="runOperation('ABORT_MERGE')"
            >
              <RotateCcw :size="13" />
              中止合并
            </UiButton>
            <UiButton
              size="sm"
              :disabled="busy || conflictFiles.length === 0"
              @click="solveWithCodex"
            >
              <Bot :size="14" />
              交给 Codex 解决
            </UiButton>
            <UiButton
              size="sm"
              variant="primary"
              :disabled="busy"
              @click="runOperation('CONTINUE_MERGE')"
            >
              <Check :size="13" />
              继续合并
            </UiButton>
          </div>
        </section>

        <template v-if="activeTab === 'operations'">
          <div class="git-operations__columns">
            <section class="git-operations__section">
              <header>
                <span><Download :size="16" /></span>
                <div>
                  <strong>远端同步</strong>
                  <small>获取、拉取或推送当前仓库</small>
                </div>
              </header>
              <div class="git-operations__button-row">
                <UiButton
                  :disabled="busy"
                  @click="runOperation('FETCH')"
                >
                  <LoaderCircle
                    v-if="operationBusy === 'FETCH'"
                    class="git-operations__spin"
                    :size="14"
                  />
                  <ArrowDownToLine v-else :size="14" />
                  Fetch
                </UiButton>
                <UiButton
                  :disabled="busy || mergeInProgress"
                  @click="runOperation('PULL')"
                >
                  <LoaderCircle
                    v-if="operationBusy === 'PULL'"
                    class="git-operations__spin"
                    :size="14"
                  />
                  <Download v-else :size="14" />
                  Pull
                </UiButton>
                <UiButton
                  :disabled="busy || mergeInProgress"
                  @click="
                    runOperation('PUSH', {
                      branchName: currentBranchName,
                      setUpstream: pushSetUpstream,
                    })
                  "
                >
                  <LoaderCircle
                    v-if="operationBusy === 'PUSH'"
                    class="git-operations__spin"
                    :size="14"
                  />
                  <Upload v-else :size="14" />
                  Push
                </UiButton>
              </div>
              <div class="git-operations__check">
                <UiCheckbox
                  v-model="pushSetUpstream"
                  label="首次推送并设置上游"
                />
                <button
                  type="button"
                  @click="pushSetUpstream = !pushSetUpstream"
                >
                  首次推送并设置上游
                </button>
              </div>
            </section>

            <section class="git-operations__section">
              <header>
                <span><GitMerge :size="16" /></span>
                <div>
                  <strong>合并分支</strong>
                  <small>将其他分支合并到 {{ currentBranchName }}</small>
                </div>
              </header>
              <div class="git-operations__merge-current">
                <span>当前分支</span>
                <code>{{ currentBranchName }}</code>
              </div>
              <UiButton
                :disabled="
                  busy ||
                  mergeInProgress ||
                  mergeOptions.length === 0
                "
                @click="openMergeDialog"
              >
                <GitMerge :size="14" />
                合并
              </UiButton>
            </section>

            <section class="git-operations__section">
              <header>
                <span><GitCommitHorizontal :size="16" /></span>
                <div>
                  <strong>提交变更</strong>
                  <small>
                    暂存 {{ status?.staged ?? 0 }} · 修改
                    {{ status?.unstaged ?? 0 }} · 未跟踪
                    {{ status?.untracked ?? 0 }}
                  </small>
                </div>
              </header>
              <div
                v-if="changedFileItems.length > 0"
                class="git-operations__changed-files"
                role="list"
                aria-label="未提交文件"
              >
                <div
                  v-for="file in changedFileItems"
                  :key="`${file.path}:${file.indexStatus}:${file.workTreeStatus}`"
                  class="git-operations__changed-file"
                  role="listitem"
                  :title="file.path"
                >
                  <span
                    class="git-operations__changed-file-status"
                    :class="`git-operations__changed-file-status--${changedFileTone(file)}`"
                  >
                    {{ changedFileLabel(file) }}
                  </span>
                  <FileCode2
                    class="git-operations__changed-file-icon"
                    :size="14"
                    aria-hidden="true"
                  />
                  <span class="git-operations__changed-file-copy">
                    <strong>{{ changedFileName(file.path) }}</strong>
                    <small>{{ changedFileDirectory(file.path) }}</small>
                  </span>
                  <code
                    :class="`git-operations__changed-file-code--${changedFileTone(file)}`"
                    :title="changedFileLabel(file)"
                  >
                    {{ changedFileCode(file) }}
                  </code>
                </div>
              </div>
              <UiButton
                :disabled="changedFiles === 0 || busy"
                @click="stageAll"
              >
                <LoaderCircle
                  v-if="operationBusy === 'STAGE_ALL'"
                  class="git-operations__spin"
                  :size="14"
                />
                <Plus v-else :size="14" />
                暂存全部变更
              </UiButton>
              <textarea
                v-model="commitMessage"
                class="git-operations__commit-message"
                rows="3"
                maxlength="4000"
                placeholder="输入提交说明"
                aria-label="提交说明"
              />
              <UiButton
                variant="primary"
                :disabled="
                  !commitMessage.trim() ||
                  !status?.staged ||
                  mergeInProgress ||
                  busy
                "
                @click="commitChanges"
              >
                <LoaderCircle
                  v-if="operationBusy === 'COMMIT'"
                  class="git-operations__spin"
                  :size="14"
                />
                <GitCommitHorizontal v-else :size="14" />
                创建提交
              </UiButton>
            </section>
          </div>
        </template>

        <template v-else-if="activeTab === 'branches'">
          <section class="git-operations__branch-create">
            <input
              v-model="newBranchName"
              type="text"
              maxlength="255"
              aria-label="新分支名称"
              placeholder="输入新分支名称"
              @keydown.enter.prevent="createBranch"
            />
            <UiButton
              variant="primary"
              :disabled="!newBranchName.trim() || busy"
              @click="createBranch"
            >
              <LoaderCircle
                v-if="operationBusy === 'CREATE_BRANCH'"
                class="git-operations__spin"
                :size="14"
              />
              <Plus v-else :size="14" />
              创建并切换
            </UiButton>
          </section>

          <div class="git-operations__branch-toolbar">
            <label>
              <Search :size="14" />
              <input
                v-model="branchSearch"
                type="search"
                aria-label="搜索分支"
                placeholder="搜索分支"
              />
            </label>
            <span>
              本地 {{ localBranches.length }} · 远端
              {{ remoteBranches.length }}
            </span>
          </div>

          <div v-if="filteredBranches.length" class="git-operations__branches">
            <article
              v-for="branch in filteredBranches"
              :key="branch.refName"
              :class="{ 'is-current': branch.current }"
            >
              <span class="git-operations__branch-icon">
                <GitBranch :size="15" />
              </span>
              <div class="git-operations__branch-main">
                <div>
                  <strong>{{ branch.name }}</strong>
                  <span
                    :class="`git-operations__branch-tag--${branchTone(branch)}`"
                  >
                    {{ branchTypeLabel(branch) }}
                  </span>
                </div>
                <small>
                  <template v-if="branch.upstream">
                    跟踪 {{ branch.upstream }} ·
                  </template>
                  {{ branch.latestCommit }}
                  <template v-if="branch.updatedAt">
                    · {{ formatDate(branch.updatedAt) }}
                  </template>
                </small>
              </div>
              <UiButton
                v-if="!branch.current && !branch.remote"
                size="sm"
                :disabled="busy"
                @click="switchBranch(branch)"
              >
                <LoaderCircle
                  v-if="branchBusyName === branch.name"
                  class="git-operations__spin"
                  :size="13"
                />
                <GitBranch v-else :size="13" />
                切换
              </UiButton>
              <span v-else-if="branch.current" class="is-current-label">
                当前分支
              </span>
              <button
                v-if="!branch.current && !branch.remote"
                class="git-operations__icon-danger"
                type="button"
                :disabled="busy"
                :aria-label="`删除分支 ${branch.name}`"
                title="删除分支"
                @click="requestDeleteBranch(branch)"
              >
                <Trash2 :size="14" />
              </button>
            </article>
          </div>
          <div v-else class="git-operations__empty">
            <GitBranch :size="24" />
            <p>{{ loadingBranches ? '正在读取分支…' : '没有匹配的分支' }}</p>
          </div>
        </template>

        <template v-else>
          <div v-if="commits.length" class="git-operations__commits">
            <div
              v-if="commitSummary.length"
              class="git-operations__commit-summary"
            >
              <span
                v-for="item in commitSummary"
                :key="item.type"
                :class="`git-operations__commit-summary-item--${item.tone}`"
              >
                <i />
                {{ item.label }}
                <strong>{{ item.count }}</strong>
              </span>
            </div>
            <article
              v-for="item in presentedCommits"
              :key="item.commit.hash"
              :class="
                `git-operations__commit--${item.presentation.tone}`
              "
            >
              <span class="git-operations__commit-dot" />
              <div>
                <header>
                  <span
                    class="git-operations__commit-type"
                    :class="
                      `git-operations__commit-type--${item.presentation.tone}`
                    "
                  >
                    {{ item.presentation.label }}
                  </span>
                  <strong>
                    {{ item.presentation.summary }}
                  </strong>
                  <code>{{ item.commit.shortHash }}</code>
                </header>
                <small>
                  {{ item.commit.authorName }}
                  <template
                    v-if="item.presentation.scope"
                  >
                    · {{ item.presentation.scope }}
                  </template>
                  · {{ formatDate(item.commit.authoredAt) }}
                </small>
              </div>
            </article>
          </div>
          <div v-else class="git-operations__empty">
            <History :size="24" />
            <p>
              {{ loadingHistory ? '正在读取提交记录…' : '暂无提交记录' }}
            </p>
          </div>
        </template>

        <details v-if="commandOutput" class="git-operations__output">
          <summary>查看 Git 命令输出</summary>
          <pre>{{ commandOutput }}</pre>
        </details>
      </div>
    </div>

    <template #footer>
      <UiButton size="lg" @click="emit('close')">
        关闭
      </UiButton>
    </template>
  </UiDialog>

  <UiDialog
    v-if="mergeDialogOpen"
    title="合并分支"
    :description="`选择要合并到 ${currentBranchName} 的来源分支。`"
    height="compact"
    initial-focus="#git-merge-source-branch"
    @close="closeMergeDialog"
  >
    <form
      id="git-merge-form"
      class="git-merge-form"
      @submit.prevent="mergeSelectedBranch"
    >
      <div class="git-merge-form__target">
        <span>目标分支</span>
        <code>{{ currentBranchName }}</code>
      </div>
      <label class="git-merge-form__source">
        <span>要合并的分支</span>
        <UiSelect
          id="git-merge-source-branch"
          v-model="selectedMergeBranch"
          :options="mergeOptions"
          placeholder="选择要合并的分支"
          aria-label="选择要合并的分支"
          :disabled="mergeOptions.length === 0 || busy"
        />
      </label>
      <p v-if="mergeOptions.length === 0" class="git-merge-form__empty">
        当前仓库没有可合并的其他分支。
      </p>
    </form>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="operationBusy === 'MERGE'"
        @click="closeMergeDialog"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="git-merge-form"
        :disabled="
          !selectedMergeBranch ||
          mergeOptions.length === 0 ||
          busy
        "
      >
        <LoaderCircle
          v-if="operationBusy === 'MERGE'"
          class="git-operations__spin"
          :size="14"
        />
        <GitMerge v-else :size="14" />
        {{ operationBusy === 'MERGE' ? '正在合并…' : '合并' }}
      </UiButton>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="pendingDeleteBranch"
    title="删除 Git 分支？"
    :description="`分支「${pendingDeleteBranch}」只会从本地 Git 仓库中删除。`"
    :warning="
      forceDeleteBranch
        ? '强制删除会丢弃该分支上尚未合并的提交。'
        : '默认只允许删除已经合并的分支。'
    "
    :confirm-label="forceDeleteBranch ? '强制删除' : '删除分支'"
    :busy="operationBusy === 'DELETE_BRANCH'"
    busy-label="正在删除…"
    @close="pendingDeleteBranch = ''"
    @confirm="confirmDeleteBranch"
  >
    <div class="git-operations__delete-confirm">
      <div class="git-operations__check">
        <UiCheckbox
          v-model="forceDeleteBranch"
          label="强制删除未合并分支"
        />
        <button
          type="button"
          @click="forceDeleteBranch = !forceDeleteBranch"
        >
          强制删除未合并分支
        </button>
      </div>
      <p v-if="deleteError" class="is-error">{{ deleteError }}</p>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.git-operations {
  min-width: 0;
}

.git-operations__summary {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--line-soft);
}

.git-operations__summary-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.git-operations__summary div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.git-operations__summary strong {
  overflow: hidden;
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-operations__summary small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-operations__state {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  color: var(--system-orange);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-orange) 9%, transparent);
  border-radius: var(--radius-badge);
}

.git-operations__state--clean {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 9%, transparent);
}

.git-operations__tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--line-soft);
}

.git-operations__body {
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.git-operations__message {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  margin: 0 0 var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  background: var(--category-chip-bg);
  border-radius: var(--radius-control);
}

.git-operations__message.is-success {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 8%, transparent);
}

.git-operations__message.is-warning {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 8%, transparent);
}

.git-operations__message.is-error {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.git-operations__conflict {
  padding: var(--space-4);
  margin-bottom: var(--space-4);
  background: color-mix(in srgb, var(--system-orange) 7%, transparent);
  border: 1px solid
    color-mix(in srgb, var(--system-orange) 20%, transparent);
  border-radius: var(--radius-control);
}

.git-operations__conflict-heading {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.git-operations__conflict-heading > span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 12%, transparent);
  border-radius: 50%;
  place-items: center;
}

.git-operations__conflict-heading strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.git-operations__conflict-heading p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.git-operations__conflict ul {
  padding: var(--space-2) var(--space-3);
  margin: var(--space-3) 0 0 42px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.git-operations__conflict-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-3);
  flex-wrap: wrap;
}

.git-operations__columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: stretch;
  gap: var(--space-4);
}

.git-operations__section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.git-operations__section:last-child:nth-child(odd) {
  grid-column: 1 / -1;
}

.git-operations__section header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.git-operations__section header > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.git-operations__section header div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.git-operations__section header strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.git-operations__section header small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.git-operations__changed-files {
  display: flex;
  max-height: 230px;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  overflow-y: auto;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: 10px;
}

.git-operations__changed-file {
  display: grid;
  min-height: 44px;
  grid-template-columns: auto 18px minmax(0, 1fr) 26px;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: var(--canvas-raised);
  border: 1px solid transparent;
  border-radius: 7px;
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.git-operations__changed-file:hover {
  background: var(--canvas-raised);
  border-color: var(--line-soft);
}

.git-operations__changed-file-status {
  --changed-file-color: var(--system-orange);

  flex: 0 0 auto;
  padding: 2px 7px;
  color: var(--changed-file-color);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  line-height: 16px;
  white-space: nowrap;
  background: color-mix(
    in srgb,
    var(--changed-file-color) 10%,
    transparent
  );
  border-radius: 6px;
}

.git-operations__changed-file-status--staged {
  --changed-file-color: var(--system-green);
}

.git-operations__changed-file-status--modified {
  --changed-file-color: var(--system-orange);
}

.git-operations__changed-file-status--untracked {
  --changed-file-color: var(--accent);
}

.git-operations__changed-file-status--conflict {
  --changed-file-color: var(--system-red);
}

.git-operations__changed-file-status--mixed {
  --changed-file-color: color-mix(
    in srgb,
    var(--accent) 55%,
    var(--system-red)
  );
}

.git-operations__changed-file-icon {
  color: var(--text-subtle);
}

.git-operations__changed-file-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.git-operations__changed-file-copy strong,
.git-operations__changed-file-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-operations__changed-file-copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: 17px;
}

.git-operations__changed-file-copy small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  line-height: 15px;
}

.git-operations__changed-file code {
  --changed-file-color: var(--system-orange);

  display: grid;
  width: 26px;
  height: 24px;
  color: var(--changed-file-color);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  line-height: 1;
  background: color-mix(
    in srgb,
    var(--changed-file-color) 9%,
    transparent
  );
  border: 1px solid color-mix(
    in srgb,
    var(--changed-file-color) 13%,
    transparent
  );
  border-radius: 6px;
  place-items: center;
}

.git-operations__changed-file-code--staged {
  --changed-file-color: var(--system-green);
}

.git-operations__changed-file-code--modified {
  --changed-file-color: var(--system-orange);
}

.git-operations__changed-file-code--untracked {
  --changed-file-color: var(--accent);
}

.git-operations__changed-file-code--conflict {
  --changed-file-color: var(--system-red);
}

.git-operations__changed-file-code--mixed {
  --changed-file-color: color-mix(
    in srgb,
    var(--accent) 55%,
    var(--system-red)
  );
}

.git-operations__button-row {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.git-operations__merge-current {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.git-operations__merge-current span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-operations__merge-current code {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-operations__check {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 18px;
}

.git-operations__check button {
  padding: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
}

.git-operations__check button:hover {
  color: var(--text);
}

.git-operations__commit-message {
  width: 100%;
  min-height: 76px;
  padding: var(--space-2) var(--space-3);
  color: var(--text);
  font: inherit;
  font-size: var(--font-body);
  line-height: var(--line-body);
  resize: vertical;
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
}

.git-operations__commit-message:focus {
  border-color: var(--system-blue);
  outline: none;
  box-shadow: var(--focus-ring);
}

.git-operations__branch-create {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.git-operations__branch-create input,
.git-operations__branch-toolbar label {
  height: var(--control-md);
  color: var(--text);
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
}

.git-operations__branch-create input {
  min-width: 0;
  flex: 1;
  padding: 0 var(--space-3);
  font-size: var(--font-body);
}

.git-operations__branch-create input:focus,
.git-operations__branch-toolbar label:focus-within {
  border-color: var(--system-blue);
  outline: none;
  box-shadow: var(--focus-ring);
}

.git-operations__branch-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.git-operations__branch-toolbar label {
  display: flex;
  min-width: 200px;
  align-items: center;
  gap: 6px;
  padding: 0 var(--space-2);
  color: var(--text-subtle);
}

.git-operations__branch-toolbar input {
  min-width: 0;
  flex: 1;
  color: var(--text);
  background: transparent;
  border: 0;
}

.git-operations__branch-toolbar input:focus {
  outline: none;
}

.git-operations__branch-toolbar > span {
  margin-left: auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-operations__branches {
  border-top: 1px solid var(--line-soft);
}

.git-operations__branches article {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-1);
  border-bottom: 1px solid var(--line-soft);
}

.git-operations__branches article.is-current {
  background: var(--hover-bg);
}

.git-operations__branch-icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--text-muted);
  background: var(--category-chip-bg);
  border-radius: var(--radius-control);
  place-items: center;
}

.git-operations__branch-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.git-operations__branch-main > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.git-operations__branch-main strong,
.git-operations__branch-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-operations__branch-main strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.git-operations__branch-main span {
  padding: 1px 5px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.git-operations__branch-main .git-operations__branch-tag--blue {
  color: var(--system-blue);
  background: color-mix(in srgb, var(--system-blue) 9%, transparent);
}

.git-operations__branch-main .git-operations__branch-tag--green {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 9%, transparent);
}

.git-operations__branch-main .git-operations__branch-tag--gray {
  color: var(--text-subtle);
  background: var(--category-chip-bg);
}

.git-operations__branch-main small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.is-current-label {
  color: var(--system-green);
  font-size: var(--font-caption);
}

.git-operations__icon-danger {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.git-operations__icon-danger:hover:not(:disabled) {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.git-operations__icon-danger:disabled {
  opacity: 0.4;
}

.git-operations__commits {
  position: relative;
}

.git-operations__commit-summary {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-1);
  border-bottom: 1px solid var(--line-soft);
  flex-wrap: wrap;
}

.git-operations__commit-summary > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
}

.git-operations__commit-summary i {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  background: var(--system-gray);
  border-radius: 50%;
}

.git-operations__commit-summary strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.git-operations__commit-summary-item--blue i {
  background: var(--system-blue);
}

.git-operations__commit-summary-item--red i {
  background: var(--system-red);
}

.git-operations__commit-summary-item--orange i {
  background: var(--system-orange);
}

.git-operations__commit-summary-item--green i {
  background: var(--system-green);
}

.git-operations__commit-summary-item--gray i {
  background: var(--system-gray);
}

.git-operations__commits::before {
  position: absolute;
  top: 11px;
  bottom: 11px;
  left: 5px;
  width: 1px;
  content: '';
  background: var(--line-soft);
}

.git-operations__commits article {
  position: relative;
  display: flex;
  gap: var(--space-3);
  padding: var(--space-2) 0 var(--space-3);
}

.git-operations__commit-dot {
  z-index: 1;
  width: 11px;
  height: 11px;
  flex: 0 0 auto;
  margin-top: 5px;
  background: var(--accent);
  border: 3px solid var(--canvas-raised);
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--accent);
}

.git-operations__commit--red .git-operations__commit-dot {
  background: var(--system-red);
  box-shadow: 0 0 0 1px var(--system-red);
}

.git-operations__commit--orange .git-operations__commit-dot {
  background: var(--system-orange);
  box-shadow: 0 0 0 1px var(--system-orange);
}

.git-operations__commit--green .git-operations__commit-dot {
  background: var(--system-green);
  box-shadow: 0 0 0 1px var(--system-green);
}

.git-operations__commit--gray .git-operations__commit-dot {
  background: var(--system-gray);
  box-shadow: 0 0 0 1px var(--system-gray);
}

.git-operations__commits article > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.git-operations__commits header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--space-2);
}

.git-operations__commit-type {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 18px;
  padding: 1px 6px;
  margin-top: 0;
  color: var(--system-blue);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: color-mix(in srgb, var(--system-blue) 9%, transparent);
  border-radius: var(--radius-badge);
}

.git-operations__commit-type--red {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 9%, transparent);
}

.git-operations__commit-type--orange {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.git-operations__commit-type--green {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 9%, transparent);
}

.git-operations__commit-type--gray {
  color: var(--text-subtle);
  background: var(--category-chip-bg);
}

.git-operations__commits article strong {
  min-width: 0;
  flex: 1;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  line-height: var(--line-body);
}

.git-operations__commits code {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.git-operations__commits small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-operations__empty {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  flex-direction: column;
  gap: var(--space-2);
}

.git-operations__empty p {
  margin: 0;
  font-size: var(--font-caption);
}

.git-operations__output {
  padding-top: var(--space-3);
  margin-top: var(--space-4);
  border-top: 1px solid var(--line-soft);
}

.git-operations__output summary {
  color: var(--text-muted);
  font-size: var(--font-caption);
  cursor: pointer;
}

.git-operations__output pre {
  padding: var(--space-3);
  margin: var(--space-2) 0 0;
  overflow: visible;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.git-operations__delete-confirm {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.git-operations__delete-confirm .is-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.git-merge-form {
  display: flex;
  padding: 0 var(--space-5);
  flex-direction: column;
  gap: var(--space-4);
}

.git-merge-form__target {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-3);
  background: var(--surface-secondary);
  border-radius: var(--radius-control);
}

.git-merge-form__target span,
.git-merge-form__source > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.git-merge-form__target code {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-merge-form__source {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.git-merge-form__source :deep(.ui-select) {
  width: 100%;
}

.git-merge-form__empty {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-operations__spin {
  animation: git-operations-spin 800ms linear infinite;
}

@keyframes git-operations-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .git-operations__summary,
  .git-operations__tabs,
  .git-operations__body {
    padding-right: var(--space-4);
    padding-left: var(--space-4);
  }

  .git-operations__summary {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .git-operations__state {
    margin-left: 46px;
  }

  .git-operations__columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .git-operations__section:last-child:nth-child(odd) {
    grid-column: auto;
  }

  .git-operations__branch-create,
  .git-operations__branch-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .git-operations__branch-toolbar label {
    width: 100%;
  }

  .git-operations__branch-toolbar > span {
    margin-left: 0;
  }

  .git-operations__conflict-actions {
    justify-content: stretch;
  }

  .git-operations__conflict-actions > * {
    flex: 1;
  }

  .git-merge-form {
    padding: 0 var(--space-4);
  }
}
</style>
