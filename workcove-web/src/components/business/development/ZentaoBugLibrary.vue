<script setup lang="ts">
import {
  Bug,
  Check,
  CircleCheck,
  CloudUpload,
  Eye,
  EyeOff,
  ExternalLink,
  FileText,
  FlaskConical,
  FolderKanban,
  Images,
  ListChecks,
  RefreshCw,
  Search,
  Settings2,
  Trash2,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import DOMPurify from 'dompurify'

import ZentaoBugPlanDialog from '@/components/business/development/ZentaoBugPlanDialog.vue'
import ZentaoTestResultDialog from '@/components/business/development/ZentaoTestResultDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCodexStore } from '@/stores/useCodexStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { useZentaoStore } from '@/stores/useZentaoStore'
import { useZentaoTestStore } from '@/stores/useZentaoTestStore'
import type { ZentaoBug, ZentaoProject } from '@/types/zentao'
import type { ZentaoTestResult } from '@/types/zentaoTest'

type BugFilter = 'active' | 'resolved' | 'all'

const emit = defineEmits<{
  notify: [message: string]
  solveBug: [bug: ZentaoBug]
  solveBugs: [bugs: ZentaoBug[], plan: string]
  testBug: [bug: ZentaoBug]
}>()

const auth = useAuthStore()
const codex = useCodexStore()
const workspaces = useWorkspaceStore()
const zentao = useZentaoStore()
const zentaoTests = useZentaoTestStore()
const searchQuery = ref('')
const bugFilter = ref<BugFilter>('active')
const configurationOpen = ref(false)
const savingConnection = ref(false)
const connectionError = ref('')
const passwordVisible = ref(false)
const loadingProjects = ref(false)
const projectError = ref('')
const projectQuery = ref('')
const availableProjects = ref<ZentaoProject[]>([])
const selectedProjectIds = ref<string[]>([])
const bugPendingDelete = ref<ZentaoBug | null>(null)
const deletingBug = ref(false)
const deleteError = ref('')
const detailBug = ref<ZentaoBug | null>(null)
const bugStepsContent = ref<HTMLElement | null>(null)
const resolvingLocally = ref(false)
const pushingToZentao = ref(false)
const detailError = ref('')
const localSolution = ref('')
const localReason = ref('')
const selectionMode = ref(false)
const selectedBugIds = ref<number[]>([])
const batchPlanOpen = ref(false)
const testResultBug = ref<ZentaoBug | null>(null)
const testResultTarget = ref<ZentaoTestResult | null>(null)
const autoSyncedScopes = new Set<string>()
let autoSyncTimer: number | null = null
const connectionForm = reactive({
  baseUrl: '',
  username: '',
  password: '',
  projectIds: [] as string[],
})

const scopeKey = computed(
  () => `${auth.email}\n${workspaces.activeWorkspaceId}`,
)
const selectedProjectNames = computed(() => {
  const names = new Map(
    availableProjects.value.map((project) => [project.id, project.name]),
  )
  return selectedProjectIds.value.map(
    (projectId) => names.get(projectId) ?? `项目 ${projectId}`,
  )
})
const filteredAvailableProjects = computed(() => {
  const query = projectQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return availableProjects.value
  return availableProjects.value.filter((project) =>
    `${project.id} ${project.name}`
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})
const renderedBugSteps = computed(() =>
  renderBugSteps(
    detailBug.value?.steps ?? '',
    detailBug.value?.id,
  ),
)

const filteredBugs = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return zentao.bugs.filter((bug) => {
    if (bugFilter.value === 'active' && (!bug.active || bug.localStatus === 'resolved')) {
      return false
    }
    if (bugFilter.value === 'resolved' && bug.localStatus !== 'resolved') {
      return false
    }
    if (!query) return true
    return `${bug.remoteId} ${bug.title} ${bug.projectName} ${bug.productName} ${bug.openedBy} ${bug.assignedTo}`
      .toLocaleLowerCase('zh-CN')
      .includes(query)
  })
})
const codexSelectableBugs = computed(() =>
  zentao.bugs.filter(canSendBugToCodex),
)
const filteredSelectableBugs = computed(() =>
  filteredBugs.value.filter(canSendBugToCodex),
)
const selectedBugs = computed(() => {
  const selectedIds = new Set(selectedBugIds.value)
  return zentao.bugs.filter(
    (bug) =>
      selectedIds.has(bug.id) &&
      canSendBugToCodex(bug),
  )
})
const allFilteredBugsSelected = computed(
  () =>
    filteredSelectableBugs.value.length > 0 &&
    filteredSelectableBugs.value.every((bug) =>
      selectedBugIds.value.includes(bug.id),
    ),
)
const {
  currentPage,
  pageItems: pagedBugs,
  setPage,
} = usePagination(
  filteredBugs,
  () => `${searchQuery.value}\n${bugFilter.value}\n${scopeKey.value}`,
)

watch(
  scopeKey,
  async () => {
    selectionMode.value = false
    selectedBugIds.value = []
    batchPlanOpen.value = false
    zentaoTests.load(auth.email)
    testResultBug.value = null
    testResultTarget.value = null
    await loadBugs()
  },
  { immediate: true },
)

watch(codexSelectableBugs, (bugs) => {
  const selectableIds = new Set(bugs.map((bug) => bug.id))
  selectedBugIds.value = selectedBugIds.value.filter((id) =>
    selectableIds.has(id),
  )
})

onMounted(() => {
  autoSyncTimer = window.setInterval(() => {
    if (
      zentao.connection.configured &&
      zentao.connection.projectIds.length > 0 &&
      !zentao.syncing
    ) {
      void syncBugs(false)
    }
  }, 5 * 60_000)
})

onBeforeUnmount(() => {
  if (autoSyncTimer) window.clearInterval(autoSyncTimer)
})

watch(
  () => zentao.connection,
  (connection) => {
    connectionForm.baseUrl = connection.baseUrl
    connectionForm.username = connection.username
    connectionForm.password = connection.password
    connectionForm.projectIds = [...connection.projectIds]
    selectedProjectIds.value = [...connection.projectIds]
    availableProjects.value = []
    projectError.value = ''
    projectQuery.value = ''
    passwordVisible.value = false
    configurationOpen.value =
      !connection.configured ||
      connection.projectIds.length === 0
    if (configurationOpen.value && connection.configured) {
      void loadProjects()
    }
  },
  { immediate: true },
)

async function loadBugs() {
  if (!auth.email || !workspaces.activeWorkspaceId) return
  await zentao.load(auth.email, workspaces.activeWorkspaceId, true)
  if (!zentao.connection.configured) return
  if (zentao.connection.projectIds.length === 0) return
  const lastSyncedAt = zentao.connection.lastSyncedAt
  const stale =
    !lastSyncedAt ||
    Date.now() - new Date(lastSyncedAt).getTime() > 5 * 60_000
  if (stale && !autoSyncedScopes.has(scopeKey.value)) {
    autoSyncedScopes.add(scopeKey.value)
    await syncBugs(false)
  }
}

function toggleConfiguration() {
  configurationOpen.value = !configurationOpen.value
  if (
    configurationOpen.value &&
    availableProjects.value.length === 0
  ) {
    void loadProjects()
  }
}

async function loadProjects() {
  projectError.value = ''
  if (!connectionForm.baseUrl.trim()) {
    projectError.value = '请先填写禅道地址'
    return
  }
  if (!connectionForm.username.trim()) {
    projectError.value = '请先填写禅道用户名'
    return
  }
  if (!zentao.connection.configured && !connectionForm.password) {
    projectError.value = '请先填写禅道密码'
    return
  }

  loadingProjects.value = true
  try {
    const projects = await zentao.loadProjects({
      workspaceId: workspaces.activeWorkspaceId,
      baseUrl: connectionForm.baseUrl.trim(),
      username: connectionForm.username.trim(),
      password: connectionForm.password,
    })
    availableProjects.value = projects
    projectQuery.value = ''
    const availableIds = new Set(projects.map((project) => project.id))
    selectedProjectIds.value = selectedProjectIds.value.filter((id) =>
      availableIds.has(id),
    )
    connectionForm.projectIds = [...selectedProjectIds.value]
    if (projects.length === 0) {
      projectError.value = '当前账号没有可访问的禅道项目'
    }
  } catch (error) {
    availableProjects.value = []
    projectError.value =
      error instanceof Error ? error.message : '无法读取禅道项目'
  } finally {
    loadingProjects.value = false
  }
}

function toggleProject(projectId: string) {
  selectedProjectIds.value = selectedProjectIds.value.includes(projectId)
    ? selectedProjectIds.value.filter((id) => id !== projectId)
    : [...selectedProjectIds.value, projectId]
  connectionForm.projectIds = [...selectedProjectIds.value]
  projectError.value = ''
}

async function saveConnection() {
  connectionError.value = ''
  if (!connectionForm.baseUrl.trim()) {
    connectionError.value = '请输入禅道地址'
    return
  }
  if (!connectionForm.username.trim()) {
    connectionError.value = '请输入禅道用户名'
    return
  }
  if (!zentao.connection.configured && !connectionForm.password) {
    connectionError.value = '请输入禅道密码'
    return
  }
  if (selectedProjectIds.value.length === 0) {
    connectionError.value = '请至少选择一个需要同步的禅道项目'
    return
  }

  savingConnection.value = true
  try {
    await zentao.saveConnection({
      workspaceId: workspaces.activeWorkspaceId,
      baseUrl: connectionForm.baseUrl.trim(),
      username: connectionForm.username.trim(),
      password: connectionForm.password,
      projectIds: [...selectedProjectIds.value],
    })
    passwordVisible.value = false
    configurationOpen.value = false
    emit('notify', '禅道连接已保存')
    await syncBugs(true)
  } catch (error) {
    connectionError.value =
      error instanceof Error ? error.message : '无法连接禅道'
  } finally {
    savingConnection.value = false
  }
}

async function syncBugs(showNotification: boolean) {
  if (zentao.syncing) return
  try {
    const result = await zentao.sync()
    if (!result) return
    if (showNotification) {
      emit(
        'notify',
        `已同步 ${result.projects} 个项目、${result.bugs} 个指派给你的 Bug`,
      )
    }
  } catch (error) {
    const message =
      error instanceof Error ? error.message : '同步禅道 Bug 失败'
    if (showNotification) emit('notify', message)
  }
}

function formatSyncTime(value: string | null) {
  if (!value) return '尚未同步'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function bugStatusLabel(bug: ZentaoBug) {
  if (bug.pushedAt) return '已回写禅道'
  if (bug.localResolved) return '平台已解决'
  if (bug.localStatus === 'resolved') return '禅道已解决'
  return bug.status || '待处理'
}

function canSendBugToCodex(bug: ZentaoBug) {
  return (
    bug.active &&
    !bug.localResolved &&
    bug.localStatus !== 'resolved' &&
    !bug.pushedAt
  )
}

function canTestBug(bug: ZentaoBug) {
  return (
    !bug.localDeleted &&
    (bug.localResolved ||
      bug.pushedAt !== null ||
      bug.localStatus === 'resolved' ||
      bug.status === 'resolved' ||
      bug.status === 'closed')
  )
}

function testInProgress(bugId: number) {
  return codex.sessions.some(
    (session) =>
      session.workspaceId === workspaces.activeWorkspaceId &&
      session.tasks.some(
        (task) =>
          task.zentaoTestBugId === bugId &&
          (task.status === 'queued' || task.status === 'running'),
      ),
  )
}

function latestTestResult(bugId: number) {
  return zentaoTests.latestForBug(bugId)
}

function testResultLabel(bugId: number) {
  const status = latestTestResult(bugId)?.status
  if (status === 'passed') return '测试通过'
  if (status === 'failed') return '测试未通过'
  if (status === 'blocked') return '暂无法验证'
  return ''
}

function requestCodexTest(bug: ZentaoBug) {
  if (testInProgress(bug.id)) return
  emit('testBug', bug)
}

function openTestResult(bug: ZentaoBug) {
  const result = latestTestResult(bug.id)
  if (!result) return
  testResultBug.value = bug
  testResultTarget.value = result
}

function closeTestResult() {
  testResultBug.value = null
  testResultTarget.value = null
}

function projectStatusLabel(status: string) {
  const labels: Record<string, string> = {
    wait: '未开始',
    doing: '进行中',
    suspended: '已挂起',
    closed: '已关闭',
  }
  return labels[status] ?? status
}

function zentaoMediaSource(value: string) {
  const placeholder = value.match(/^\{(\d+)(\.[A-Za-z0-9]+)?}$/)
  return placeholder
    ? `file-read-${placeholder[1]}${placeholder[2] ?? ''}`
    : value
}

function renderBugSteps(value: string, bugId?: number) {
  const source = value.trim()
  if (!source) return '<p>禅道中未提供重现步骤</p>'

  const sanitized = DOMPurify.sanitize(source, {
    ALLOWED_TAGS: [
      'a',
      'b',
      'blockquote',
      'br',
      'code',
      'div',
      'em',
      'i',
      'img',
      'li',
      'ol',
      'p',
      'pre',
      'span',
      'strong',
      'table',
      'tbody',
      'td',
      'th',
      'thead',
      'tr',
      'u',
      'ul',
    ],
    ALLOWED_ATTR: [
      'alt',
      'colspan',
      'href',
      'rel',
      'rowspan',
      'src',
      'target',
      'title',
    ],
    FORBID_TAGS: [
      'button',
      'embed',
      'form',
      'iframe',
      'input',
      'object',
      'script',
      'style',
    ],
  })
  const template = document.createElement('template')
  template.innerHTML = sanitized
  template.content.querySelectorAll('a').forEach((link) => {
    link.target = '_blank'
    link.rel = 'noopener noreferrer nofollow'
  })
  template.content.querySelectorAll('img').forEach((image) => {
    const source = image.getAttribute('src')?.trim() ?? ''
    if (!source) {
      image.remove()
      return
    }
    if (!source.startsWith('data:image/')) {
      if (!bugId) {
        image.remove()
        return
      }
      const mediaSource = zentaoMediaSource(source)
      image.setAttribute(
        'src',
        `/api/development/zentao/bugs/${bugId}/media`
          + `?workspaceId=${encodeURIComponent(workspaces.activeWorkspaceId)}`
          + `&source=${encodeURIComponent(mediaSource)}`,
      )
    }
    image.setAttribute('loading', 'lazy')
    image.setAttribute('decoding', 'async')
    image.setAttribute('referrerpolicy', 'no-referrer')
    image.setAttribute('alt', image.getAttribute('alt') || 'Bug 图片附件')
    image.dataset.zentaoMedia = 'true'
  })
  return template.innerHTML
}

function enhanceBugStepMedia() {
  const root = bugStepsContent.value
  if (!root) return
  root.querySelectorAll<HTMLImageElement>('img[data-zentao-media]')
    .forEach((image) => {
      if (image.dataset.fallbackBound) return
      image.dataset.fallbackBound = 'true'
      image.title = '点击查看图片'
      image.addEventListener('click', () => {
        window.open(image.src, '_blank', 'noopener,noreferrer')
      })
      const showFallback = () => {
        if (!image.isConnected) return
        const fallback = document.createElement('span')
        fallback.className = 'zentao-bug-image-fallback'
        fallback.textContent =
          `${image.alt || '图片附件'}暂时无法预览`
        image.replaceWith(fallback)
      }
      if (image.complete && image.naturalWidth === 0) {
        showFallback()
        return
      }
      image.addEventListener('error', showFallback, { once: true })
    })
}

watch(
  renderedBugSteps,
  () => {
    void nextTick(enhanceBugStepMedia)
  },
  { flush: 'post' },
)

function requestDeleteBug(bug: ZentaoBug) {
  bugPendingDelete.value = bug
  deleteError.value = ''
}

function openBugDetails(bug: ZentaoBug) {
  detailBug.value = bug
  localSolution.value = bug.solutionText ?? ''
  localReason.value = bug.reasonText ?? ''
  detailError.value = ''
  void nextTick(enhanceBugStepMedia)
}

function prepareBugResolution(bug: ZentaoBug) {
  openBugDetails(bug)
  void nextTick(() => {
    document.getElementById('zentao-local-solution')?.focus()
  })
}

function sendBugToCodex(bug: ZentaoBug) {
  if (resolvingLocally.value || pushingToZentao.value) return
  detailBug.value = null
  emit('solveBug', bug)
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) {
    selectedBugIds.value = []
    batchPlanOpen.value = false
  }
}

function setBugSelected(id: number, selected: boolean) {
  selectedBugIds.value = selected
    ? [...new Set([...selectedBugIds.value, id])]
    : selectedBugIds.value.filter((item) => item !== id)
}

function handleBugRowClick(bug: ZentaoBug) {
  if (!selectionMode.value) {
    openBugDetails(bug)
    return
  }
  if (canSendBugToCodex(bug)) {
    setBugSelected(
      bug.id,
      !selectedBugIds.value.includes(bug.id),
    )
  }
}

function toggleSelectAllFilteredBugs() {
  const filteredIds = filteredSelectableBugs.value.map(
    (bug) => bug.id,
  )
  if (allFilteredBugsSelected.value) {
    const filteredSet = new Set(filteredIds)
    selectedBugIds.value = selectedBugIds.value.filter(
      (id) => !filteredSet.has(id),
    )
    return
  }
  selectedBugIds.value = [
    ...new Set([...selectedBugIds.value, ...filteredIds]),
  ]
}

function clearBugSelection() {
  selectedBugIds.value = []
}

function openBatchPlanDialog() {
  if (selectedBugs.value.length === 0) {
    emit('notify', '请先选择需要处理的 Bug')
    return
  }
  batchPlanOpen.value = true
}

function closeBatchPlanDialog() {
  batchPlanOpen.value = false
}

function submitBatchPlan(plan: string) {
  const bugs = [...selectedBugs.value]
  if (bugs.length === 0) {
    batchPlanOpen.value = false
    emit('notify', '没有可处理的 Bug，请重新选择')
    return
  }
  batchPlanOpen.value = false
  selectionMode.value = false
  selectedBugIds.value = []
  emit('solveBugs', bugs, plan)
}

function closeBugDetails() {
  if (resolvingLocally.value || pushingToZentao.value) return
  detailBug.value = null
  detailError.value = ''
  localSolution.value = ''
  localReason.value = ''
}

async function resolveBugLocally(bug: ZentaoBug) {
  if (resolvingLocally.value) return
  const solution = localSolution.value.trim()
  const reason = localReason.value.trim()
  if (!solution) {
    detailError.value = '请填写解决方案'
    return
  }
  if (!reason) {
    detailError.value = '请填写定位原因'
    return
  }

  resolvingLocally.value = true
  detailError.value = ''
  try {
    const updated = await zentao.resolveBugLocally(bug.id, {
      solution,
      reason,
    })
    if (detailBug.value?.id === updated.id) {
      detailBug.value = updated
      localSolution.value = updated.solutionText ?? solution
      localReason.value = updated.reasonText ?? reason
    }
    emit('notify', '解决方案和定位原因已保存到本地，尚未更新禅道')
  } catch (error) {
    const message =
      error instanceof Error ? error.message : '无法标记为解决'
    detailError.value = message
    if (!detailBug.value) emit('notify', message)
  } finally {
    resolvingLocally.value = false
  }
}

async function pushResolutionToZentao(bug: ZentaoBug) {
  if (pushingToZentao.value) return
  const solution = localSolution.value.trim()
  const reason = localReason.value.trim()
  if (!solution) {
    detailError.value = '请填写解决方案'
    return
  }
  if (!reason) {
    detailError.value = '请填写定位原因'
    return
  }

  pushingToZentao.value = true
  detailError.value = ''
  try {
    const updated = await zentao.resolveBug(bug.id, {
      resolution: 'fixed',
      solution,
      reason,
    })
    if (detailBug.value?.id === updated.id) {
      detailBug.value = updated
      localSolution.value = updated.solutionText ?? solution
      localReason.value = updated.reasonText ?? reason
    }
    emit('notify', `Bug #${bug.remoteId} 的备注已更新到禅道`)
  } catch (error) {
    const message =
      error instanceof Error ? error.message : '更新禅道备注失败'
    detailError.value = message
  } finally {
    pushingToZentao.value = false
  }
}

function closeDeleteDialog() {
  if (deletingBug.value) return
  bugPendingDelete.value = null
  deleteError.value = ''
}

async function confirmDeleteBug() {
  const bug = bugPendingDelete.value
  if (!bug || deletingBug.value) return

  deletingBug.value = true
  deleteError.value = ''
  try {
    await zentao.deleteBug(bug.id)
    bugPendingDelete.value = null
    emit('notify', '已从 8小时工作台移除，禅道中的 Bug 未删除')
  } catch (error) {
    deleteError.value =
      error instanceof Error ? error.message : '无法移除 Bug'
  } finally {
    deletingBug.value = false
  }
}
</script>

<template>
  <section class="zentao-library">
    <header class="zentao-toolbar">
      <div class="zentao-connection-summary">
        <span
          class="zentao-connection-dot"
          :class="{ 'zentao-connection-dot--ready': zentao.connection.configured }"
        />
        <div>
          <strong>
            {{
              zentao.connection.configured
                ? zentao.connection.baseUrl
                : '尚未连接禅道'
            }}
          </strong>
          <small>
            {{
              zentao.connection.configured
                ? zentao.connection.projectIds.length > 0
                  ? `${zentao.connection.username} · ${zentao.connection.projectIds.length} 个项目 · ${formatSyncTime(zentao.connection.lastSyncedAt)}`
                  : '请选择需要同步的禅道项目'
                : '配置地址和账号后即可同步 Bug'
            }}
          </small>
        </div>
      </div>
      <div class="zentao-toolbar__actions">
        <UiButton
          v-if="zentao.connection.configured"
          size="sm"
          :disabled="
            zentao.syncing ||
            zentao.connection.projectIds.length === 0
          "
          :title="
            zentao.connection.projectIds.length === 0
              ? '请先选择同步项目'
              : '同步所选项目的 Bug'
          "
          @click="syncBugs(true)"
        >
          <RefreshCw
            :size="13"
            :class="{ 'zentao-spin': zentao.syncing }"
          />
          {{ zentao.syncing ? '同步中…' : '同步' }}
        </UiButton>
        <UiButton size="sm" @click="toggleConfiguration">
          <Settings2 :size="13" />
          {{ configurationOpen ? '收起配置' : '连接配置' }}
        </UiButton>
      </div>
    </header>

    <form
      v-if="configurationOpen"
      class="zentao-connection-form"
      @submit.prevent="saveConnection"
    >
      <UiInput
        v-model="connectionForm.baseUrl"
        size="lg"
        aria-label="禅道地址"
        placeholder="例如：https://zentao.example.com"
        spellcheck="false"
      />
      <UiInput
        v-model="connectionForm.username"
        size="lg"
        aria-label="禅道用户名"
        placeholder="用户名"
        autocomplete="username"
        spellcheck="false"
      />
      <span class="zentao-password">
        <UiInput
          v-model="connectionForm.password"
          size="lg"
          :type="passwordVisible ? 'text' : 'password'"
          aria-label="禅道密码"
          :placeholder="
            zentao.connection.configured
              ? '已保存密码'
              : '密码'
          "
          autocomplete="new-password"
          autocapitalize="off"
          :spellcheck="false"
          data-lpignore="true"
          data-1p-ignore="true"
        />
        <button
          type="button"
          :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
          :title="passwordVisible ? '隐藏密码' : '显示密码'"
          @click="passwordVisible = !passwordVisible"
        >
          <EyeOff v-if="passwordVisible" :size="15" />
          <Eye v-else :size="15" />
        </button>
      </span>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        :disabled="savingConnection"
      >
        {{ savingConnection ? '连接中…' : '保存并同步' }}
      </UiButton>
      <div class="zentao-project-field">
        <div class="zentao-project-field__header">
          <div>
            <strong>
              <FolderKanban :size="14" />
              同步项目
            </strong>
            <small>只显示未关闭项目，并拉取其中的 Bug</small>
          </div>
          <UiButton
            size="sm"
            type="button"
            :disabled="loadingProjects"
            @click="loadProjects"
          >
            <RefreshCw
              :size="13"
              :class="{ 'zentao-spin': loadingProjects }"
            />
            {{ loadingProjects ? '读取中…' : '获取项目' }}
          </UiButton>
        </div>
        <label v-if="availableProjects.length > 0" class="zentao-project-search">
          <Search :size="13" />
          <input
            v-model="projectQuery"
            type="search"
            aria-label="搜索禅道项目"
            placeholder="搜索项目名称或 ID"
          />
        </label>
        <div
          v-if="filteredAvailableProjects.length > 0"
          class="zentao-project-options"
        >
          <label
            v-for="project in filteredAvailableProjects"
            :key="project.id"
            class="zentao-project-option"
            :class="{
              'zentao-project-option--selected':
                selectedProjectIds.includes(project.id),
            }"
          >
            <input
              type="checkbox"
              :checked="selectedProjectIds.includes(project.id)"
              :aria-label="`选择项目 ${project.name}`"
              @change="toggleProject(project.id)"
            />
            <span class="zentao-project-option__check" aria-hidden="true">
              <Check
                v-if="selectedProjectIds.includes(project.id)"
                :size="10"
                :stroke-width="2.6"
              />
            </span>
            <span class="zentao-project-option__copy">
              <strong>{{ project.name }}</strong>
              <small>
                #{{ project.id }}
                <template v-if="project.status">
                  · {{ projectStatusLabel(project.status) }}
                </template>
              </small>
            </span>
          </label>
        </div>
        <p
          v-else-if="availableProjects.length > 0"
          class="zentao-project-hint"
        >
          没有匹配的项目。
        </p>
        <p v-else-if="loadingProjects" class="zentao-project-hint">
          正在读取当前账号可访问的项目…
        </p>
        <p
          v-else-if="projectError"
          class="zentao-project-error"
          role="alert"
        >
          {{ projectError }}
        </p>
        <p v-else class="zentao-project-hint">
          填写连接信息后获取项目，可同时选择多个项目。
        </p>
        <p
          v-if="projectError && availableProjects.length > 0"
          class="zentao-project-error"
          role="alert"
        >
          {{ projectError }}
        </p>
        <div
          v-if="selectedProjectNames.length > 0"
          class="zentao-project-selection"
        >
          <span>已选择</span>
          <span
            v-for="(name, index) in selectedProjectNames"
            :key="`${name}-${index}`"
            class="zentao-project-tag"
          >
            {{ name }}
          </span>
        </div>
      </div>
      <p v-if="connectionError" role="alert">{{ connectionError }}</p>
    </form>

    <div class="zentao-list-toolbar">
      <div class="zentao-filters" role="group" aria-label="Bug 状态筛选">
        <button
          v-for="item in [
            { value: 'active', label: '待处理' },
            { value: 'resolved', label: '已解决' },
            { value: 'all', label: '全部' },
          ]"
          :key="item.value"
          type="button"
          :class="{ 'zentao-filter--active': bugFilter === item.value }"
          @click="bugFilter = item.value as BugFilter"
        >
          {{ item.label }}
        </button>
      </div>
      <label class="zentao-search">
        <Search :size="14" />
        <input
          v-model="searchQuery"
          type="search"
          aria-label="搜索 Bug"
          placeholder="搜索 Bug 编号、标题或产品"
        />
      </label>
      <span class="zentao-count">{{ filteredBugs.length }} 个 Bug</span>
      <UiButton size="sm" @click="toggleSelectionMode">
        <ListChecks :size="13" />
        {{ selectionMode ? '退出批量' : '批量选择' }}
      </UiButton>
    </div>

    <div
      v-if="selectionMode"
      class="zentao-batch-bar"
      role="toolbar"
      aria-label="Bug 批量操作"
    >
      <span>
        <ListChecks :size="13" />
        已选择 <strong>{{ selectedBugs.length }}</strong> 个 Bug
      </span>
      <UiButton
        size="sm"
        :disabled="filteredSelectableBugs.length === 0"
        @click="toggleSelectAllFilteredBugs"
      >
        {{
          allFilteredBugsSelected
            ? '取消全选当前筛选'
            : '全选当前筛选'
        }}
      </UiButton>
      <UiButton
        size="sm"
        :disabled="selectedBugs.length === 0"
        @click="clearBugSelection"
      >
        清除选择
      </UiButton>
      <UiButton
        variant="primary"
        size="sm"
        :disabled="selectedBugs.length === 0"
        @click="openBatchPlanDialog"
      >
        设置解决计划
      </UiButton>
      <button
        class="zentao-batch-bar__close"
        type="button"
        aria-label="退出批量选择"
        title="退出批量选择"
        @click="toggleSelectionMode"
      >
        <X :size="13" />
      </button>
    </div>

    <div v-if="pagedBugs.length > 0" class="zentao-bug-list">
      <article
        v-for="bug in pagedBugs"
        :key="bug.id"
        class="zentao-bug-row"
        :class="{
          'zentao-bug-row--selecting': selectionMode,
          'zentao-bug-row--selected':
            selectionMode && selectedBugIds.includes(bug.id),
        }"
        role="button"
        tabindex="0"
        :aria-label="
          selectionMode
            ? `选择 Bug #${bug.remoteId}`
            : `查看 Bug #${bug.remoteId} 详情`
        "
        @click="handleBugRowClick(bug)"
        @keydown.enter.prevent="handleBugRowClick(bug)"
        @keydown.space.prevent="handleBugRowClick(bug)"
      >
        <span
          v-if="selectionMode"
          class="zentao-bug-select"
          @click.stop
        >
          <UiCheckbox
            :model-value="selectedBugIds.includes(bug.id)"
            :label="`选择 Bug #${bug.remoteId}`"
            :disabled="!canSendBugToCodex(bug)"
            @update:model-value="setBugSelected(bug.id, $event)"
          />
        </span>
        <div class="zentao-bug-row__main">
          <div class="zentao-bug-row__top">
            <span class="zentao-bug-id">#{{ bug.remoteId }}</span>
            <span class="zentao-bug-priority">P{{ bug.priority || '-' }}</span>
            <span class="zentao-bug-severity">
              严重度 {{ bug.severity || '-' }}
            </span>
            <span
              class="zentao-bug-status"
              :class="{
                'zentao-bug-status--resolved':
                  bug.localStatus === 'resolved' || bug.pushedAt,
              }"
            >
              {{ bugStatusLabel(bug) }}
            </span>
            <span
              v-if="latestTestResult(bug.id)"
              class="zentao-test-status"
              :class="`zentao-test-status--${latestTestResult(bug.id)?.status}`"
            >
              {{ testResultLabel(bug.id) }}
            </span>
          </div>
          <h3>{{ bug.title }}</h3>
          <div class="zentao-bug-meta">
            <span v-if="bug.projectName">{{ bug.projectName }}</span>
            <span>{{ bug.productName }}</span>
            <span>{{ bug.openedBy || '未知创建人' }}</span>
            <span>{{ bug.openedDate || '未知日期' }}</span>
            <span v-if="bug.assignedTo">指派给 {{ bug.assignedTo }}</span>
          </div>
        </div>
        <div class="zentao-bug-row__actions">
          <UiButton
            v-if="canSendBugToCodex(bug)"
            variant="primary"
            size="sm"
            @click.stop="emit('solveBug', bug)"
          >
            <Bug :size="13" />
            交给 Codex
          </UiButton>
          <UiButton
            v-if="
              !bug.localResolved &&
              bug.localStatus !== 'resolved' &&
              !bug.pushedAt
            "
            size="sm"
            :disabled="resolvingLocally"
            title="填写解决方案和定位原因后保存到 8小时工作台"
            @click.stop="prepareBugResolution(bug)"
          >
            <CircleCheck :size="13" />
            解决
          </UiButton>
          <UiButton
            v-else-if="bug.localResolved && !bug.pushedAt"
            variant="primary"
            size="sm"
            @click.stop="openBugDetails(bug)"
          >
            <CloudUpload :size="13" />
            更新禅道
          </UiButton>
          <span v-else class="zentao-pushed-label">
            <ExternalLink v-if="bug.pushedAt" :size="13" />
            <CircleCheck v-else :size="13" />
            {{ bug.pushedAt ? '已回写禅道' : '已标记解决' }}
          </span>
          <UiButton
            v-if="canTestBug(bug)"
            size="sm"
            :disabled="testInProgress(bug.id)"
            :title="
              testInProgress(bug.id)
                ? 'Codex 正在测试这个 Bug'
                : '在研发环境验证修复结果并生成测试截图'
            "
            @click.stop="requestCodexTest(bug)"
          >
            <FlaskConical :size="13" />
            {{ testInProgress(bug.id) ? '测试中…' : 'Codex 测试' }}
          </UiButton>
          <UiButton
            v-if="latestTestResult(bug.id)"
            size="sm"
            @click.stop="openTestResult(bug)"
          >
            <Images :size="13" />
            查看测试结果
          </UiButton>
          <button
            class="zentao-bug-detail"
            type="button"
            :aria-label="`查看 Bug #${bug.remoteId} 详情`"
            title="查看详情"
            @click.stop="openBugDetails(bug)"
          >
            <FileText :size="14" />
          </button>
          <button
            class="zentao-bug-delete"
            type="button"
            :aria-label="`从 8小时工作台移除 Bug #${bug.remoteId}`"
            title="仅从 8小时工作台移除"
            @click.stop="requestDeleteBug(bug)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="zentao-empty">
      <span><Bug :size="28" /></span>
      <strong>
        {{
          !zentao.connection.configured
            ? '先配置禅道连接'
            : zentao.connection.projectIds.length === 0
              ? '先选择同步项目'
            : zentao.syncing
              ? '正在同步 Bug'
              : '没有符合条件的 Bug'
        }}
      </strong>
      <p>
        {{
          !zentao.connection.configured
            ? '保存禅道地址、用户名和密码后会自动拉取。'
            : zentao.connection.projectIds.length === 0
              ? '选择需要同步的项目后，只会拉取这些项目中的 Bug。'
            : zentao.error || '可以手动同步，或调整搜索条件后再试。'
        }}
      </p>
    </div>

    <UiPagination
      :page="currentPage"
      :total="filteredBugs.length"
      label="Bug 分页"
      @update:page="setPage"
    />
  </section>

  <ZentaoBugPlanDialog
    v-if="batchPlanOpen"
    :bugs="selectedBugs"
    @close="closeBatchPlanDialog"
    @submit="submitBatchPlan"
  />

  <ZentaoTestResultDialog
    v-if="testResultBug && testResultTarget"
    :bug="testResultBug"
    :result="testResultTarget"
    @close="closeTestResult"
  />

  <UiDialog
    v-if="detailBug"
    :title="`Bug #${detailBug.remoteId}`"
    :description="detailBug.title"
    size="extra-wide"
    height="tall"
    scroll-mode="body"
    max-height="min(860px, calc(100dvh - 32px))"
    @close="closeBugDetails"
  >
    <div class="zentao-bug-detail-dialog">
      <div class="zentao-bug-detail-layout">
        <main class="zentao-bug-detail-main">
          <section class="zentao-bug-steps">
            <header>
              <h3>重现步骤</h3>
            </header>
            <div
              ref="bugStepsContent"
              class="zentao-bug-steps__content"
              v-html="renderedBugSteps"
            ></div>
          </section>

          <section class="zentao-resolution-notes">
            <header>
              <div>
                <h3>解决备注</h3>
                <p>解决方案和定位原因先保存在本地，确认后再更新到禅道。</p>
              </div>
              <span v-if="detailBug.pushedAt">已更新禅道</span>
              <span v-else-if="detailBug.localResolved">已保存本地</span>
            </header>
            <div class="zentao-resolution-notes__grid">
              <label>
                <span>解决方案 <b>*</b></span>
                <textarea
                  id="zentao-local-solution"
                  v-model="localSolution"
                  rows="5"
                  maxlength="4000"
                  :disabled="Boolean(detailBug.pushedAt)"
                  placeholder="说明实际修复了什么"
                />
              </label>
              <label>
                <span>定位原因 <b>*</b></span>
                <textarea
                  v-model="localReason"
                  rows="5"
                  maxlength="4000"
                  :disabled="Boolean(detailBug.pushedAt)"
                  placeholder="说明问题根因和定位过程"
                />
              </label>
            </div>
          </section>

          <p v-if="detailError" class="zentao-detail-error" role="alert">
            {{ detailError }}
          </p>
        </main>

        <aside class="zentao-bug-detail-sidebar">
          <header>
            <h3>基本信息</h3>
          </header>
          <dl>
            <div>
              <dt>状态</dt>
              <dd>{{ bugStatusLabel(detailBug) }}</dd>
            </div>
            <div>
              <dt>优先级</dt>
              <dd>P{{ detailBug.priority || '-' }}</dd>
            </div>
            <div>
              <dt>严重程度</dt>
              <dd>{{ detailBug.severity || '未设置' }}</dd>
            </div>
            <div>
              <dt>所属项目</dt>
              <dd>{{ detailBug.projectName || '未关联项目' }}</dd>
            </div>
            <div>
              <dt>所属产品</dt>
              <dd>{{ detailBug.productName || '未知产品' }}</dd>
            </div>
            <div>
              <dt>创建</dt>
              <dd>
                <span>{{ detailBug.openedBy || '未知' }}</span>
                <small>{{ detailBug.openedDate || '未知日期' }}</small>
              </dd>
            </div>
            <div>
              <dt>指派给</dt>
              <dd>{{ detailBug.assignedTo || '未指派' }}</dd>
            </div>
          </dl>
        </aside>
      </div>
    </div>

    <template #footer>
      <footer class="zentao-bug-detail-actions">
        <div>
          <UiButton
            v-if="
              !detailBug.localResolved &&
              detailBug.localStatus !== 'resolved' &&
              !detailBug.pushedAt
            "
            variant="primary"
            size="lg"
            :disabled="resolvingLocally || pushingToZentao"
            @click="sendBugToCodex(detailBug)"
          >
            <Bug :size="14" />
            交给 Codex 解决
          </UiButton>
          <UiButton
            v-if="!detailBug.pushedAt"
            size="lg"
            :disabled="resolvingLocally"
            @click="resolveBugLocally(detailBug)"
          >
            <CircleCheck :size="14" />
            {{
              resolvingLocally
                ? '保存中…'
                : detailBug.localResolved
                  ? '保存本地备注'
                  : '保存并标记为已解决'
            }}
          </UiButton>
          <UiButton
            v-if="detailBug.localResolved && !detailBug.pushedAt"
            variant="primary"
            size="lg"
            :disabled="pushingToZentao"
            @click="pushResolutionToZentao(detailBug)"
          >
            <CloudUpload :size="14" />
            {{ pushingToZentao ? '更新中…' : '更新备注到禅道' }}
          </UiButton>
        </div>
      </footer>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="bugPendingDelete"
    title="从 8小时工作台移除这个 Bug？"
    description="只会隐藏本地同步记录，不会修改或删除禅道中的 Bug。"
    warning="移除后再次同步也不会重新出现在 8小时工作台列表，请确认后继续。"
    confirm-label="从 8小时工作台移除"
    busy-label="正在移除…"
    :busy="deletingBug"
    @close="closeDeleteDialog"
    @confirm="confirmDeleteBug"
  >
    <div class="zentao-delete-target">
      <strong>#{{ bugPendingDelete.remoteId }}</strong>
      <span>{{ bugPendingDelete.title }}</span>
      <p v-if="deleteError" role="alert">{{ deleteError }}</p>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.zentao-library {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: zentao-enter var(--duration-window) var(--ease-apple) both;
}

.zentao-toolbar {
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-connection-summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.zentao-connection-summary > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.zentao-connection-summary strong,
.zentao-connection-summary small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-connection-summary strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-connection-summary small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-connection-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  background: var(--text-subtle);
  border-radius: 50%;
}

.zentao-connection-dot--ready {
  background: var(--system-green);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--system-green) 14%, transparent);
}

.zentao-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--space-2);
}

.zentao-connection-form {
  display: grid;
  grid-template-columns: minmax(180px, 1.6fr) minmax(120px, 0.8fr) minmax(120px, 0.8fr) auto;
  align-items: start;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--canvas);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-connection-form > p {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.zentao-password {
  position: relative;
  display: block;
  min-width: 0;
}

.zentao-password :deep(.ui-input) {
  padding-right: 36px;
}

.zentao-password button {
  position: absolute;
  top: 1px;
  right: 1px;
  bottom: 1px;
  display: grid;
  width: 34px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: calc(var(--radius-control) - 1px);
  place-items: center;
}

.zentao-password button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.zentao-project-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  grid-column: 1 / -1;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.zentao-project-field__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.zentao-project-field__header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.zentao-project-field__header strong {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-project-field__header small,
.zentao-project-hint {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.zentao-project-options {
  display: grid;
  max-height: 180px;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: var(--space-2);
  overflow-y: auto;
}

.zentao-project-search {
  display: flex;
  width: min(100%, 280px);
  height: var(--control-md);
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  color: var(--text-subtle);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.zentao-project-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.zentao-project-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
}

.zentao-project-search input:focus,
.zentao-project-search input:focus-visible {
  outline: none;
}

.zentao-project-option {
  display: flex;
  min-width: 0;
  min-height: 44px;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 8px;
  cursor: pointer;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.zentao-project-option:hover {
  background: var(--hover-bg);
}

.zentao-project-option--selected {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--system-blue) 45%, var(--line));
}

.zentao-project-option input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.zentao-project-option__check {
  display: grid;
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  color: #ffffff;
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: 4px;
  place-items: center;
}

.zentao-project-option--selected .zentao-project-option__check {
  background: var(--system-blue);
  border-color: var(--system-blue);
}

.zentao-project-option input:focus-visible + .zentao-project-option__check {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.zentao-project-option__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.zentao-project-option__copy strong,
.zentao-project-option__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-project-option__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.zentao-project-option__copy small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.zentao-project-error,
.zentao-delete-target p {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.zentao-project-selection {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-project-tag {
  padding: 2px 6px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
}

.zentao-list-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-filters {
  display: flex;
  gap: 2px;
  padding: 2px;
  background: var(--segmented-track);
  border-radius: var(--radius-control);
}

.zentao-filters button {
  height: 22px;
  padding: 0 8px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: 4px;
}

.zentao-filters button:hover {
  color: var(--text);
}

.zentao-filters .zentao-filter--active {
  color: var(--text);
  font-weight: var(--weight-emphasis);
  background: var(--control-bg);
  box-shadow: var(--control-shadow);
}

.zentao-search {
  display: flex;
  width: min(100%, 260px);
  height: var(--control-md);
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  margin-left: auto;
  color: var(--text-subtle);
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
}

.zentao-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.zentao-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.zentao-search input:focus,
.zentao-search input:focus-visible {
  outline: none;
}

.zentao-count {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.zentao-batch-bar {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-4);
  background: var(--selection-bg);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-batch-bar > span {
  display: inline-flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.zentao-batch-bar > span strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-weight: var(--weight-emphasis);
}

.zentao-batch-bar__close {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.zentao-batch-bar__close:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.zentao-bug-list {
  display: flex;
  flex-direction: column;
}

.zentao-bug-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  border-bottom: 1px solid var(--line-soft);
  transition: background var(--duration-fast) var(--ease-apple);
}

.zentao-bug-row:hover {
  background: var(--hover-bg);
}

.zentao-bug-row--selecting {
  grid-template-columns: 18px minmax(0, 1fr);
}

.zentao-bug-row--selected,
.zentao-bug-row--selected:hover {
  background: var(--selection-bg);
}

.zentao-bug-row--selecting .zentao-bug-row__actions {
  display: none;
}

.zentao-bug-select {
  display: grid;
  align-self: start;
  margin-top: 2px;
  place-items: center;
}

.zentao-bug-row:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: -2px;
}

.zentao-bug-row__main {
  min-width: 0;
}

.zentao-bug-row__top,
.zentao-bug-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.zentao-bug-id,
.zentao-bug-priority,
.zentao-bug-severity,
.zentao-bug-status {
  padding: 2px 5px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--canvas);
  border-radius: var(--radius-badge);
}

.zentao-bug-priority {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.zentao-bug-status {
  color: var(--accent);
  background: var(--accent-soft);
}

.zentao-bug-status--resolved {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
}

.zentao-test-status {
  padding: 2px 5px;
  font-size: var(--font-caption);
  background: var(--canvas);
  border-radius: var(--radius-badge);
}

.zentao-test-status--passed {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
}

.zentao-test-status--failed {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 9%, transparent);
}

.zentao-test-status--blocked {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.zentao-bug-row h3 {
  overflow: hidden;
  margin: var(--space-2) 0 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-bug-row__main > p {
  display: -webkit-box;
  overflow: hidden;
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.zentao-bug-meta {
  margin-top: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-bug-meta span + span::before {
  margin-right: var(--space-2);
  content: "·";
}

.zentao-bug-row__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.zentao-bug-delete {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.zentao-bug-detail {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.zentao-bug-detail:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.zentao-bug-delete:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.zentao-pushed-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--system-green);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.zentao-delete-target {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.zentao-delete-target strong {
  color: var(--text);
  font-family: var(--font-mono);
}

.zentao-delete-target span {
  overflow: hidden;
  color: var(--text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-bug-detail-dialog {
  min-height: 0;
  padding: var(--space-3) var(--space-5) var(--space-6);
}

.zentao-bug-detail-layout {
  display: grid;
  min-height: 0;
  grid-template-columns: minmax(0, 1fr) 260px;
  align-items: start;
  gap: 28px;
}

.zentao-bug-detail-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-5);
}

.zentao-bug-detail-sidebar {
  position: sticky;
  top: 0;
  min-width: 0;
  padding-left: var(--space-5);
  border-left: 1px solid var(--line-soft);
}

.zentao-bug-detail-sidebar > header {
  padding-bottom: var(--space-2);
}

.zentao-bug-detail-sidebar h3,
.zentao-bug-steps h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-bug-detail-sidebar dl {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.zentao-bug-detail-sidebar dl > div {
  display: grid;
  min-width: 0;
  grid-template-columns: 70px minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  padding: 9px 0;
  border-bottom: 1px solid var(--line-soft);
}

.zentao-bug-detail-sidebar dl > div:last-child {
  border-bottom: 0;
}

.zentao-bug-detail-sidebar dt {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.zentao-bug-detail-sidebar dd {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  margin: 0;
  color: var(--text);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  overflow-wrap: anywhere;
}

.zentao-bug-detail-sidebar dd small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.zentao-bug-steps {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-2);
}

.zentao-bug-steps__content {
  padding: 0;
  margin: 0;
  overflow: visible;
  color: var(--text);
  font-size: var(--font-body);
  line-height: 1.72;
  overflow-wrap: anywhere;
}

.zentao-bug-steps__content :deep(p) {
  margin: 0;
}

.zentao-bug-steps__content :deep(p + p),
.zentao-bug-steps__content :deep(p + ul),
.zentao-bug-steps__content :deep(p + ol) {
  margin-top: var(--space-2);
}

.zentao-bug-steps__content :deep(ul),
.zentao-bug-steps__content :deep(ol) {
  padding-left: 20px;
  margin: var(--space-2) 0;
}

.zentao-bug-steps__content :deep(a) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.zentao-bug-steps__content :deep(img) {
  display: block;
  box-sizing: border-box;
  max-width: 100%;
  height: auto;
  padding: 3px;
  margin: var(--space-3) 0;
  cursor: zoom-in;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  box-shadow: 0 8px 22px rgb(0 0 0 / 7%);
}

.zentao-bug-image-fallback {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  padding: 6px 9px;
  margin: var(--space-2) 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  overflow-wrap: anywhere;
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.zentao-bug-steps__content :deep(pre),
.zentao-bug-steps__content :deep(code) {
  font-family: var(--font-mono);
  white-space: pre-wrap;
}

.zentao-bug-steps__content :deep(pre) {
  padding: var(--space-2);
  overflow-x: auto;
  overflow-y: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-control);
}

.zentao-bug-steps__content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.zentao-bug-steps__content :deep(th),
.zentao-bug-steps__content :deep(td) {
  padding: 4px 6px;
  text-align: left;
  border: 1px solid var(--line-soft);
}

.zentao-resolution-notes {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.zentao-resolution-notes > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.zentao-resolution-notes h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.zentao-resolution-notes header p {
  margin: 3px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-resolution-notes header > span {
  flex: 0 0 auto;
  padding: 2px 6px;
  color: var(--system-green);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
  border-radius: var(--radius-badge);
}

.zentao-resolution-notes__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

.zentao-resolution-notes__grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.zentao-resolution-notes__grid label > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.zentao-resolution-notes__grid b {
  color: var(--system-red);
}

.zentao-resolution-notes__grid textarea {
  height: 224px;
  min-height: 224px;
  padding: var(--space-2);
  resize: none;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: var(--line-body);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.zentao-resolution-notes {
  padding-bottom: var(--space-2);
}

.zentao-resolution-notes__grid textarea:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
  outline: none;
}

.zentao-resolution-notes__grid textarea:disabled {
  color: var(--text-muted);
  background: var(--surface-inset);
}

.zentao-detail-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.zentao-bug-detail-actions {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
}

.zentao-bug-detail-actions > div {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.zentao-empty {
  display: flex;
  min-height: 300px;
  align-items: center;
  justify-content: center;
  text-align: center;
  flex-direction: column;
}

.zentao-empty > span {
  display: grid;
  width: 48px;
  height: 48px;
  color: var(--text-subtle);
  background: var(--canvas);
  border-radius: var(--radius-panel);
  place-items: center;
}

.zentao-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-empty p {
  max-width: 440px;
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.zentao-spin {
  animation: zentao-spin 800ms linear infinite;
}

@media (max-width: 820px) {
  .zentao-connection-form {
    grid-template-columns: 1fr 1fr;
  }

  .zentao-bug-detail-layout {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  .zentao-bug-detail-sidebar {
    position: static;
    grid-row: 1;
    padding: 0 0 var(--space-3);
    border-bottom: 1px solid var(--line-soft);
    border-left: 0;
  }
}

@media (max-width: 620px) {
  .zentao-toolbar,
  .zentao-list-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .zentao-connection-summary {
    flex-basis: 100%;
  }

  .zentao-toolbar__actions {
    width: 100%;
  }

  .zentao-connection-form {
    grid-template-columns: 1fr;
  }

  .zentao-project-field__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .zentao-search {
    width: 100%;
    margin-left: 0;
  }

  .zentao-batch-bar {
    align-items: center;
    flex-wrap: wrap;
    padding: var(--space-2) var(--space-3);
  }

  .zentao-batch-bar > span {
    flex-basis: 100%;
  }

  .zentao-batch-bar__close {
    margin-left: auto;
  }

  .zentao-bug-row {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }

  .zentao-bug-row--selecting {
    grid-template-columns: 18px minmax(0, 1fr);
  }

  .zentao-bug-row__actions {
    justify-content: flex-end;
  }

  .zentao-resolution-notes__grid {
    grid-template-columns: 1fr;
  }

  .zentao-bug-detail-actions > div {
    justify-content: flex-end;
  }
}

@keyframes zentao-enter {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes zentao-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
