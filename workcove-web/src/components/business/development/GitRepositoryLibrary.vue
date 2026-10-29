<script setup lang="ts">
import {
  ArrowRightLeft,
  ChartColumnIncreasing,
  Cloud,
  Copy,
  FolderOpen,
  GitBranch,
  GitCommitHorizontal,
  GitMerge,
  ListChecks,
  Plus,
  RefreshCw,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  ref,
  watch,
} from 'vue'
import type { Component } from 'vue'

import {
  openLocalDirectory,
  openLocalProjectWithIdea,
} from '@/api/development'
import ideaIcon from '@/assets/development/intellij-idea.png'
import CategoryManagerDialog from '@/components/business/development/CategoryManagerDialog.vue'
import GitRepositoryAddDialog from '@/components/business/development/GitRepositoryAddDialog.vue'
import GitOperationsDialog from '@/components/business/development/GitOperationsDialog.vue'
import GitStatisticsDialog from '@/components/business/development/GitStatisticsDialog.vue'
import WorkspaceBatchBar from '@/components/business/workspaces/WorkspaceBatchBar.vue'
import WorkspaceTransferDialog, {
  type WorkspaceTransferMode,
} from '@/components/business/workspaces/WorkspaceTransferDialog.vue'
import UiActionMenu from '@/components/ui/UiActionMenu.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCategoryFilterBar from '@/components/ui/UiCategoryFilterBar.vue'
import UiCategorySelect from '@/components/ui/UiCategorySelect.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import { useGitRepositoryStore } from '@/stores/useGitRepositoryStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { DEFAULT_GIT_REPOSITORY_CATEGORY } from '@/types/gitRepository'
import type {
  GitRepository,
  GitRepositoryStatus,
} from '@/types/gitRepository'

const emit = defineEmits<{
  notify: [message: string]
  solveConflicts: [
    payload: {
      repository: GitRepository
      conflictFiles: string[]
    },
  ]
}>()

const auth = useAuthStore()
const repositories = useGitRepositoryStore()
const workspaces = useWorkspaceStore()
const searchQuery = ref('')
const selectedCategory = ref('全部')
const categoryManagerOpen = ref(false)
const addDialogOpen = ref(false)
const selectionMode = ref(false)
const selectedRepositoryIds = ref<string[]>([])
const selectedRepositoryId = ref('')
const ideaBusyId = ref('')
const transferRepository = ref<GitRepository | null>(null)
const batchTransferMode = ref<WorkspaceTransferMode | null>(null)
const deleteTargets = ref<GitRepository[]>([])
const operationRepository = ref<GitRepository | null>(null)
const statisticsRepository = ref<GitRepository | null>(null)

interface RepositoryMenuItem {
  id: string
  label: string
  icon?: Component
  iconSrc?: string
  disabled?: boolean
  dividerBefore?: boolean
}

const workspaceRepositories = computed(() =>
  repositories.repositories
    .filter(
      (repository) =>
        repository.workspaceId === workspaces.activeWorkspaceId,
    )
    .sort((left, right) =>
      right.createdAt.localeCompare(left.createdAt),
    ),
)

const categories = computed(
  () =>
    repositories.categoriesByWorkspace[
      workspaces.activeWorkspaceId
    ] ?? [DEFAULT_GIT_REPOSITORY_CATEGORY],
)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const repository of workspaceRepositories.value) {
    counts.set(
      repository.category,
      (counts.get(repository.category) ?? 0) + 1,
    )
  }
  return counts
})

const defaultRepositoryCategory = computed(() =>
  selectedCategory.value === '全部'
    ? categories.value[0] ?? DEFAULT_GIT_REPOSITORY_CATEGORY
    : selectedCategory.value,
)

const filteredRepositories = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return workspaceRepositories.value.filter((repository) => {
    if (
      selectedCategory.value !== '全部' &&
      repository.category !== selectedCategory.value
    ) {
      return false
    }
    if (!query) return true
    const status = repositories.statuses[repository.id]
    return [
      repository.name,
      repository.path,
      repository.category,
      status?.branch ?? '',
      status?.remoteName ?? '',
      status?.remoteUrl ?? '',
      status?.latestCommitSubject ?? '',
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query)
  })
})

const {
  currentPage,
  pageItems: pagedRepositories,
  setPage,
} = usePagination(
  filteredRepositories,
  () =>
    `${searchQuery.value}\n${selectedCategory.value}\n${workspaces.activeWorkspaceId}`,
)

const selectedRepositories = computed(() => {
  const selectedIds = new Set(selectedRepositoryIds.value)
  return workspaceRepositories.value.filter((repository) =>
    selectedIds.has(repository.id),
  )
})

const refreshing = computed(() =>
  workspaceRepositories.value.some((repository) =>
    repositories.loadingIds.includes(repository.id),
  ),
)

watch(
  () => auth.email,
  (email) => {
    repositories.load(email)
    workspaces.load(email)
    clearSelection()
    void refreshAll()
  },
  { immediate: true },
)

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    clearSelection()
    addDialogOpen.value = false
    deleteTargets.value = []
    operationRepository.value = null
    statisticsRepository.value = null
    transferRepository.value = null
    batchTransferMode.value = null
    void refreshAll()
  },
)

watch(categories, (items) => {
  if (
    selectedCategory.value !== '全部' &&
    !items.includes(selectedCategory.value)
  ) {
    selectedCategory.value = '全部'
  }
})

function openAddDialog() {
  addDialogOpen.value = true
}

function handleRepositoryAdded(repository: GitRepository) {
  selectedRepositoryId.value = repository.id
  addDialogOpen.value = false
}

function changeRepositoryCategory(
  repository: GitRepository,
  category: string,
) {
  if (
    !repositories.changeRepositoryCategory(repository.id, category)
  ) {
    return
  }
  selectedRepositoryId.value = repository.id
  emit('notify', `${repository.name} 已移至「${category}」`)
}

function addGitCategory(category: string) {
  return repositories.addCategory(
    category,
    workspaces.activeWorkspaceId,
  )
}

function renameGitCategory(
  currentCategory: string,
  nextCategory: string,
) {
  return repositories.renameCategory(
    currentCategory,
    nextCategory,
    workspaces.activeWorkspaceId,
  )
}

function deleteGitCategory(
  category: string,
  reassignTo: string,
) {
  return repositories.deleteCategory(
    category,
    reassignTo,
    workspaces.activeWorkspaceId,
  )
}

async function refreshAll(showNotification = false) {
  if (!auth.email || !workspaces.activeWorkspaceId) return
  await repositories.refreshWorkspace(workspaces.activeWorkspaceId)
  if (!showNotification) return

  const failed = workspaceRepositories.value.filter(
    (repository) => repositories.errors[repository.id],
  ).length
  emit(
    'notify',
    failed > 0
      ? `Git 状态已刷新，${failed} 个仓库读取失败`
      : 'Git 仓库状态已刷新',
  )
}

async function refreshRepository(repository: GitRepository) {
  selectedRepositoryId.value = repository.id
  await repositories.refreshRepository(repository.id)
  const error = repositories.errors[repository.id]
  if (error) emit('notify', `${repository.name}：${error}`)
}

async function handleOperationChanged() {
  const repository = operationRepository.value
  if (!repository) return
  await repositories.refreshRepository(repository.id)
  const error = repositories.errors[repository.id]
  if (error) emit('notify', `${repository.name}：${error}`)
}

function statusOf(id: string): GitRepositoryStatus | null {
  return repositories.statuses[id] ?? null
}

function loading(id: string) {
  return repositories.loadingIds.includes(id)
}

function repositoryMenuItems(
  repository: GitRepository,
): RepositoryMenuItem[] {
  if (repository.source !== 'local') {
    return [
      {
        id: 'transfer',
        label: '复制或移动到其他工作空间',
        icon: ArrowRightLeft,
      },
    ]
  }

  return [
    {
      id: 'statistics',
      label: '统计近一年提交和代码量',
      icon: ChartColumnIncreasing,
    },
    {
      id: 'refresh',
      label: loading(repository.id) ? '正在刷新 Git 状态…' : '刷新 Git 状态',
      icon: RefreshCw,
      disabled: loading(repository.id),
    },
    {
      id: 'open-directory',
      label: '在访达中打开目录',
      icon: FolderOpen,
    },
    {
      id: 'open-idea',
      label:
        ideaBusyId.value === repository.id
          ? '正在用 IDEA 打开…'
          : '用 IntelliJ IDEA 打开',
      iconSrc: ideaIcon,
      disabled: ideaBusyId.value === repository.id,
    },
    {
      id: 'transfer',
      label: '复制或移动到其他工作空间',
      icon: ArrowRightLeft,
      dividerBefore: true,
    },
  ]
}

function handleRepositoryMenuAction(
  repository: GitRepository,
  action: string,
) {
  if (action === 'statistics') {
    statisticsRepository.value = repository
    return
  }
  if (action === 'refresh') {
    void refreshRepository(repository)
    return
  }
  if (action === 'open-directory') {
    void openDirectory(repository)
    return
  }
  if (action === 'open-idea') {
    void openWithIdea(repository)
    return
  }
  if (action === 'transfer') {
    transferRepository.value = repository
  }
}

function statusLabel(repository: GitRepository) {
  if (repository.source === 'remote') return '仅远端'
  if (loading(repository.id)) return '读取中'
  if (repositories.errors[repository.id]) return '状态异常'
  const status = statusOf(repository.id)
  if (!status) return '等待刷新'
  if (status.mergeInProgress) return '合并待继续'
  if (!status.clean) return '有未提交变更'
  if (status.ahead > 0) return '有未推送提交'
  return '工作区干净'
}

function statusTone(repository: GitRepository) {
  if (repository.source === 'remote') return 'remote'
  if (loading(repository.id)) return 'loading'
  if (repositories.errors[repository.id]) return 'error'
  const status = statusOf(repository.id)
  if (!status) return 'clean'
  if (!status.clean) return 'dirty'
  return status.ahead > 0 ? 'ahead' : 'clean'
}

function branchLabel(repository: GitRepository) {
  if (repository.source === 'remote') return '尚未克隆'
  return statusOf(repository.id)?.branch || '读取分支中'
}

function remoteUrlOf(repository: GitRepository) {
  const status = statusOf(repository.id)
  return status?.remoteUrl || repository.remoteUrl
}

function remoteLabel(repository: GitRepository) {
  const remoteUrl = remoteUrlOf(repository)
  if (!remoteUrl) return '未配置远端'
  const value = remoteUrl.replace(/\.git$/, '')
  if (/^https?:\/\//i.test(value)) {
    try {
      const url = new URL(value)
      return `${url.host}${url.pathname}`
    } catch {
      return value
    }
  }
  const scpMatch = value.match(/^[^@]+@([^:]+):(.+)$/)
  return scpMatch
    ? `${scpMatch[1]}/${scpMatch[2]}`
    : value
}

async function copyRemoteUrl(repository: GitRepository) {
  const remoteUrl = remoteUrlOf(repository)
  if (!remoteUrl) {
    emit('notify', `${repository.name} 未配置远端地址`)
    return
  }
  if (!navigator.clipboard?.writeText) {
    emit('notify', '当前环境无法访问剪贴板')
    return
  }
  try {
    await navigator.clipboard.writeText(remoteUrl)
    emit('notify', `已复制「${repository.name}」的 Git 地址`)
  } catch {
    emit('notify', `复制「${repository.name}」的 Git 地址失败`)
  }
}

function changeSummary(repository: GitRepository) {
  if (repository.source === 'remote') return '尚未克隆到本地'
  if (repositories.errors[repository.id]) {
    return repositories.errors[repository.id]
  }
  const status = statusOf(repository.id)
  if (!status) return '正在读取仓库状态'
  if (status.mergeInProgress && status.conflicted === 0) {
    return '合并尚未完成'
  }
  if (status.clean) return '未提交变更：无'

  const items: string[] = []
  if (status.conflicted) items.push(`冲突 ${status.conflicted}`)
  if (status.staged) items.push(`暂存 ${status.staged}`)
  if (status.unstaged) items.push(`修改 ${status.unstaged}`)
  if (status.untracked) items.push(`未跟踪 ${status.untracked}`)
  return `未提交变更：${items.join(' · ')}`
}

function commitLabel(repository: GitRepository) {
  if (repository.source === 'remote') return '尚无本地提交'
  return statusOf(repository.id)?.latestCommitSubject || '暂无提交记录'
}

function formatCommitTime(value: string | undefined) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

async function openDirectory(repository: GitRepository) {
  selectedRepositoryId.value = repository.id
  const error = await openLocalDirectory(repository.path)
  if (error) {
    emit('notify', `无法在访达中打开目录：${error}`)
    return
  }
  emit('notify', `已在访达中打开「${repository.name}」`)
}

async function openWithIdea(repository: GitRepository) {
  selectedRepositoryId.value = repository.id
  ideaBusyId.value = repository.id
  try {
    const error = await openLocalProjectWithIdea(repository.path)
    if (error) {
      emit('notify', `无法用 IDEA 打开：${error}`)
      return
    }
    emit('notify', `正在用 IntelliJ IDEA 打开 ${repository.name}`)
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法启动 IntelliJ IDEA',
    )
  } finally {
    ideaBusyId.value = ''
  }
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) selectedRepositoryIds.value = []
}

function clearSelection() {
  selectionMode.value = false
  selectedRepositoryIds.value = []
}

function setRepositorySelected(id: string, selected: boolean) {
  selectedRepositoryIds.value = selected
    ? [...new Set([...selectedRepositoryIds.value, id])]
    : selectedRepositoryIds.value.filter((item) => item !== id)
}

function toggleSelectAll() {
  const visibleIds = filteredRepositories.value.map(
    (repository) => repository.id,
  )
  const allSelected =
    visibleIds.length > 0 &&
    visibleIds.every((id) => selectedRepositoryIds.value.includes(id))
  if (allSelected) {
    const visible = new Set(visibleIds)
    selectedRepositoryIds.value = selectedRepositoryIds.value.filter(
      (id) => !visible.has(id),
    )
    return
  }
  selectedRepositoryIds.value = [
    ...new Set([...selectedRepositoryIds.value, ...visibleIds]),
  ]
}

function requestDelete(repository: GitRepository) {
  deleteTargets.value = [repository]
}

function requestDeleteSelected() {
  if (selectedRepositories.value.length === 0) return
  deleteTargets.value = [...selectedRepositories.value]
}

function confirmDelete() {
  const targets = [...deleteTargets.value]
  for (const repository of targets) {
    repositories.removeRepository(repository.id)
  }
  deleteTargets.value = []
  selectedRepositoryIds.value = selectedRepositoryIds.value.filter(
    (id) => !targets.some((repository) => repository.id === id),
  )
  emit(
    'notify',
    targets.length === 1
      ? 'Git 仓库记录已从平台移除'
      : `已移除 ${targets.length} 个 Git 仓库记录`,
  )
}

function handleTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const repository = transferRepository.value
  if (!repository) return
  const changed =
    mode === 'copy'
      ? repositories.copyRepository(repository.id, targetWorkspaceId)
      : repositories.moveRepository(repository.id, targetWorkspaceId)
  if (!changed) {
    emit('notify', '目标工作空间已有同一仓库，或当前仓库不可处理')
    return
  }
  transferRepository.value = null
  if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
  emit(
    'notify',
    mode === 'copy'
      ? 'Git 仓库已复制到目标工作空间'
      : 'Git 仓库已移动到目标工作空间',
  )
}

function handleBatchTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const ids = [...selectedRepositoryIds.value]
  let changed = 0
  for (const id of ids) {
    const success =
      mode === 'copy'
        ? repositories.copyRepository(id, targetWorkspaceId)
        : repositories.moveRepository(id, targetWorkspaceId)
    if (success) changed += 1
  }
  batchTransferMode.value = null
  if (changed === 0) {
    emit('notify', '没有可处理的 Git 仓库')
    return
  }
  clearSelection()
  if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
  emit(
    'notify',
    `已将 ${changed} 个 Git 仓库${mode === 'copy' ? '复制' : '移动'}到目标工作空间`,
  )
}
</script>

<template>
  <section class="git-repository-library">
    <header class="git-repository-toolbar">
      <span class="git-repository-count">
        {{ filteredRepositories.length }} 个 Git 仓库
      </span>
      <UiSearchInput
        v-model="searchQuery"
        label="搜索 Git 仓库"
        placeholder="搜索名称、路径、分支、远端或提交"
      />
      <UiButton @click="toggleSelectionMode">
        <ListChecks :size="14" />
        {{ selectionMode ? '退出批量' : '批量' }}
      </UiButton>
      <UiButton
        :loading="refreshing"
        :disabled="refreshing || workspaceRepositories.length === 0"
        @click="refreshAll(true)"
      >
        <RefreshCw
          v-if="!refreshing"
          :size="14"
        />
        {{ refreshing ? '刷新中…' : '刷新状态' }}
      </UiButton>
      <UiButton
        variant="primary"
        @click="openAddDialog"
      >
        <Plus :size="15" />
        添加仓库
      </UiButton>
    </header>

    <WorkspaceBatchBar
      v-if="selectionMode"
      :count="selectedRepositories.length"
      :total="filteredRepositories.length"
      show-delete
      @toggle-select-all="toggleSelectAll"
      @copy="batchTransferMode = 'copy'"
      @move="batchTransferMode = 'move'"
      @delete="requestDeleteSelected"
      @cancel="toggleSelectionMode"
    />

    <UiCategoryFilterBar
      v-model="selectedCategory"
      :categories="categories"
      :counts="categoryCounts"
      :total="workspaceRepositories.length"
      label="分类"
      group-label="Git 仓库分类筛选"
      @manage="categoryManagerOpen = true"
    />

    <div v-if="pagedRepositories.length > 0" class="git-repository-list">
      <article
        v-for="repository in pagedRepositories"
        :key="repository.id"
        class="git-repository-row"
        :class="{
          'git-repository-row--selecting': selectionMode,
          'git-repository-row--focused':
            selectedRepositoryId === repository.id,
        }"
      >
        <span
          v-if="selectionMode"
          class="git-repository-select"
          @click.stop
        >
          <UiCheckbox
            :model-value="selectedRepositoryIds.includes(repository.id)"
            :label="`选择 Git 仓库 ${repository.name}`"
            @update:model-value="
              setRepositorySelected(repository.id, $event)
            "
          />
        </span>

        <span class="git-repository-icon">
          <GitBranch :size="18" />
        </span>

        <div class="git-repository-main">
          <div class="git-repository-title">
            <strong>{{ repository.name }}</strong>
            <span
              class="git-repository-state"
              :class="`git-repository-state--${statusTone(repository)}`"
            >
              <UiFidgetSpinner
                v-if="loading(repository.id)"
                :size="11"
              />
              {{ statusLabel(repository) }}
            </span>
          </div>
          <small
            class="git-repository-path"
            :title="repository.path || repository.remoteUrl"
          >
            {{ repository.path || repository.remoteUrl }}
          </small>
          <div class="git-repository-meta">
            <span
              v-if="repository.source === 'local'"
              :title="repository.path"
            >
              <GitBranch :size="12" />
              {{ branchLabel(repository) }}
            </span>
            <span v-else>
              <Cloud :size="12" />
              仅远端仓库
            </span>
            <span
              :title="
                remoteUrlOf(repository) ||
                '未配置远端'
              "
            >
              <Cloud :size="12" />
              {{ remoteLabel(repository) }}
            </span>
            <span :title="commitLabel(repository)">
              <GitCommitHorizontal :size="12" />
              {{ commitLabel(repository) }}
            </span>
          </div>
        </div>

        <UiCategorySelect
          class="git-repository-category"
          :value="repository.category"
          :options="categories"
          :label="`切换 ${repository.name} 的分类`"
          title="快速切换 Git 仓库分类"
          @focus="selectedRepositoryId = repository.id"
          @change="changeRepositoryCategory(repository, $event)"
        />

        <div class="git-repository-summary">
          <span
            class="git-repository-changes"
            :title="changeSummary(repository)"
          >
            {{ changeSummary(repository) }}
          </span>
          <div
            v-if="statusOf(repository.id)"
            class="git-repository-tracking"
          >
            <span
              v-if="statusOf(repository.id)?.ahead"
              class="git-repository-tracking__unpushed"
              :title="`${statusOf(repository.id)?.ahead} 个本地提交尚未 push 到远端`"
            >
              未推送 {{ statusOf(repository.id)?.ahead }}
            </span>
            <span
              v-if="statusOf(repository.id)?.behind"
              class="git-repository-tracking__unpulled"
              :title="`远端有 ${statusOf(repository.id)?.behind} 个提交尚未 pull 到本地`"
            >
              未拉取 {{ statusOf(repository.id)?.behind }}
            </span>
            <small>
              {{ formatCommitTime(statusOf(repository.id)?.latestCommitAt) }}
            </small>
          </div>
        </div>

        <div class="git-repository-actions">
          <button
            type="button"
            :disabled="!remoteUrlOf(repository)"
            :aria-label="`复制 ${repository.name} 的 Git 地址`"
            :title="
              remoteUrlOf(repository) || '未配置远端地址'
            "
            @click="copyRemoteUrl(repository)"
          >
            <Copy :size="14" />
          </button>
          <button
            v-if="repository.source === 'local'"
            type="button"
            :aria-label="`打开 ${repository.name} 的 Git 操作`"
            title="Git 操作、分支和提交记录"
            @click="operationRepository = repository"
          >
            <GitMerge :size="14" />
          </button>
          <UiActionMenu
            :items="repositoryMenuItems(repository)"
            :label="`更多 ${repository.name} 操作`"
            :title="`更多 ${repository.name} 操作`"
            @select="handleRepositoryMenuAction(repository, $event)"
          />
          <button
            class="git-repository-delete"
            type="button"
            :aria-label="`移除 Git 仓库 ${repository.name}`"
            title="仅从平台移除记录"
            @click="requestDelete(repository)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="git-repository-empty">
      <span><GitBranch :size="28" /></span>
      <strong>
        {{
          workspaceRepositories.length === 0
            ? '还没有 Git 仓库'
            : '没有符合条件的 Git 仓库'
        }}
      </strong>
      <p>
        {{
          workspaceRepositories.length === 0
            ? '添加本地 Git 仓库后，可以集中查看分支、远端和工作区状态。'
            : '调整搜索关键词后再试。'
        }}
      </p>
      <UiButton
        v-if="workspaceRepositories.length === 0"
        variant="primary"
        @click="openAddDialog"
      >
        <Plus :size="14" />
        添加第一个仓库
      </UiButton>
    </div>

    <UiPagination
      :page="currentPage"
      :total="filteredRepositories.length"
      label="Git 仓库分页"
      @update:page="setPage"
    />

    <GitRepositoryAddDialog
      v-if="addDialogOpen"
      :categories="categories"
      :default-category="defaultRepositoryCategory"
      @close="addDialogOpen = false"
      @added="handleRepositoryAdded"
      @notify="emit('notify', $event)"
    />

    <GitOperationsDialog
      v-if="operationRepository"
      :repository="operationRepository"
      :status="statusOf(operationRepository.id)"
      @close="operationRepository = null"
      @changed="handleOperationChanged"
      @notify="emit('notify', $event)"
      @solve-conflicts="emit('solveConflicts', $event)"
    />

    <GitStatisticsDialog
      v-if="statisticsRepository"
      :repository="statisticsRepository"
      @close="statisticsRepository = null"
      @notify="emit('notify', $event)"
    />

    <CategoryManagerDialog
      v-if="categoryManagerOpen"
      title="管理 Git 仓库分类"
      description="新增、重命名或删除 Git 仓库分类。"
      :categories="categories"
      :counts="categoryCounts"
      item-label="仓库"
      :default-category="DEFAULT_GIT_REPOSITORY_CATEGORY"
      :add-category="addGitCategory"
      :rename-category="renameGitCategory"
      :delete-category="deleteGitCategory"
      @close="categoryManagerOpen = false"
      @notify="emit('notify', $event)"
    />

    <WorkspaceTransferDialog
      v-if="transferRepository"
      :item-label="`Git 仓库「${transferRepository.name}」`"
      :source-workspace-id="transferRepository.workspaceId"
      @close="transferRepository = null"
      @transfer="handleTransfer"
    />

    <WorkspaceTransferDialog
      v-if="batchTransferMode"
      :item-label="`已选择 ${selectedRepositories.length} 个 Git 仓库`"
      :source-workspace-id="workspaces.activeWorkspaceId"
      :initial-mode="batchTransferMode"
      @close="batchTransferMode = null"
      @transfer="handleBatchTransfer"
    />

    <UiConfirmDialog
      v-if="deleteTargets.length > 0"
      :title="
        deleteTargets.length === 1
          ? '从平台移除 Git 仓库？'
          : `移除 ${deleteTargets.length} 个 Git 仓库？`
      "
      description="只会移除平台中的仓库记录，不会删除本地仓库或任何代码文件。"
      confirm-label="移除记录"
      @close="deleteTargets = []"
      @confirm="confirmDelete"
    >
      <div class="git-repository-delete-target">
        <strong>
          {{
            deleteTargets.length === 1
              ? deleteTargets[0]?.name
              : '已选择的 Git 仓库'
          }}
        </strong>
        <small v-if="deleteTargets.length === 1">
          {{ deleteTargets[0]?.path }}
        </small>
      </div>
    </UiConfirmDialog>
  </section>
</template>

<style scoped>
.git-repository-library {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.git-repository-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.git-repository-count {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-repository-list {
  display: flex;
  flex-direction: column;
}

.git-repository-row {
  display: grid;
  min-width: 0;
  grid-template-columns:
    34px minmax(0, 1fr) auto
    minmax(145px, 0.38fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
  transition: background var(--duration-fast) var(--ease-apple);
}

.git-repository-row:hover,
.git-repository-row--focused {
  background: var(--hover-bg);
}

.git-repository-row--selecting {
  grid-template-columns:
    18px 34px minmax(0, 1fr) auto
    minmax(145px, 0.38fr);
}

.git-repository-row--selecting .git-repository-actions {
  display: none;
}

.git-repository-select {
  display: grid;
  place-items: center;
}

.git-repository-icon {
  display: grid;
  width: 34px;
  height: 34px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.git-repository-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.git-repository-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.git-repository-title strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-repository-state {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.git-repository-state--clean {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
}

.git-repository-state--dirty {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.git-repository-state--ahead {
  color: var(--system-blue);
  background: color-mix(in srgb, var(--system-blue) 9%, transparent);
}

.git-repository-state--error {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 9%, transparent);
}

.git-repository-state--remote {
  color: var(--text-muted);
  background: var(--category-chip-bg);
}

.git-repository-path {
  overflow: hidden;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-repository-meta,
.git-repository-tracking {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.git-repository-meta {
  margin-top: 2px;
}

.git-repository-meta span,
.git-repository-tracking span {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.git-repository-meta span {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-repository-meta svg {
  flex: 0 0 auto;
  color: var(--text-subtle);
}

.git-repository-category {
  width: 104px;
  justify-self: start;
}

.git-repository-summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.git-repository-changes {
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-repository-tracking small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.git-repository-tracking span {
  padding: 1px 5px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
}

.git-repository-tracking .git-repository-tracking__unpushed {
  color: var(--system-blue);
  background: color-mix(in srgb, var(--system-blue) 9%, transparent);
}

.git-repository-tracking .git-repository-tracking__unpulled {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 9%, transparent);
}

.git-repository-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.git-repository-actions > button {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
}

.git-repository-actions > button:hover:not(:disabled) {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.git-repository-actions > button:disabled {
  opacity: 0.45;
}

.git-repository-actions > .git-repository-delete:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.git-repository-empty {
  display: flex;
  min-height: 320px;
  align-items: center;
  justify-content: center;
  text-align: center;
  flex-direction: column;
}

.git-repository-empty > span {
  display: grid;
  width: 52px;
  height: 52px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.git-repository-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.git-repository-empty p {
  max-width: 430px;
  margin: var(--space-1) 0 var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.git-repository-delete-target {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.git-repository-delete-target strong,
.git-repository-delete-target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-repository-delete-target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.git-repository-delete-target small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

@media (max-width: 1050px) {
  .git-repository-row,
  .git-repository-row--selecting {
    grid-template-columns: 34px minmax(0, 1fr) auto;
  }

  .git-repository-row--selecting {
    grid-template-columns: 18px 34px minmax(0, 1fr);
  }

  .git-repository-summary {
    grid-column: 2;
    grid-row: 3;
  }

  .git-repository-category {
    grid-column: 2;
    grid-row: 2;
  }

  .git-repository-row--selecting .git-repository-summary {
    grid-column: 3;
  }

  .git-repository-row--selecting .git-repository-category {
    grid-column: 3;
  }

  .git-repository-actions {
    grid-column: 3;
    grid-row: 1 / span 3;
  }

  .git-repository-row--selecting .git-repository-actions {
    display: none;
  }
}

@media (max-width: 700px) {
  .git-repository-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .git-repository-count {
    flex-basis: 100%;
  }

  .git-repository-row,
  .git-repository-row--selecting {
    grid-template-columns: 34px minmax(0, 1fr);
    gap: var(--space-2);
    padding: var(--space-3);
  }

  .git-repository-row--selecting {
    grid-template-columns: 18px 34px minmax(0, 1fr);
  }

  .git-repository-summary {
    grid-column: 2;
    grid-row: 3;
  }

  .git-repository-row--selecting .git-repository-summary {
    grid-column: 3;
  }

  .git-repository-category {
    grid-column: 2;
    grid-row: 2;
  }

  .git-repository-row--selecting .git-repository-category {
    grid-column: 3;
  }

  .git-repository-actions {
    grid-column: 2;
    grid-row: 4;
    justify-content: flex-end;
  }

  .git-repository-row--selecting .git-repository-actions {
    display: none;
  }
}
</style>
