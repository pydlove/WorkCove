<script setup lang="ts">
import {
  BadgeCheck,
  Bot,
  Bug,
  Check,
  CircleCheck,
  CloudUpload,
  Eye,
  EyeOff,
  FileText,
  FlaskConical,
  FolderKanban,
  Images,
  ListChecks,
  MessageSquareText,
  PackageCheck,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  Trash2,
  Wrench,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  type Component,
  watch,
} from 'vue'
import DOMPurify from 'dompurify'

import ZentaoBugPlanDialog from '@/components/business/development/ZentaoBugPlanDialog.vue'
import ZentaoBuildCreateDialog from '@/components/business/development/ZentaoBuildCreateDialog.vue'
import ZentaoTestTaskCreateDialog from '@/components/business/development/ZentaoTestTaskCreateDialog.vue'
import ZentaoTestResultDialog from '@/components/business/development/ZentaoTestResultDialog.vue'
import EmbeddedBrowserDialog from '@/components/business/websites/EmbeddedBrowserDialog.vue'
import UiActionMenu from '@/components/ui/UiActionMenu.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import ZentaoMark from '@/components/ui/ZentaoMark.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCodexStore } from '@/stores/useCodexStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { useZentaoStore } from '@/stores/useZentaoStore'
import { useZentaoTestStore } from '@/stores/useZentaoTestStore'
import type {
  ZentaoBug,
  ZentaoBuild,
  ZentaoCreatedBuild,
  ZentaoCreatedTestTask,
  ZentaoProject,
} from '@/types/zentao'
import type { ZentaoTestResult } from '@/types/zentaoTest'
import {
  failedZentaoTestComment,
  publishZentaoTestResultComment,
} from '@/services/zentaoTestComment'
import { openWebsiteUrl } from '@/utils/openWebsite'
import { activeZentaoTestSessionId } from '@/utils/zentaoTestNavigation'

type BugFilter = 'active' | 'resolved' | 'all'
type ZentaoTab = 'bugs' | 'builds' | 'projects'
type BugQuickAction =
  | 'codex'
  | 'test'
  | 'test-result'
  | 'detail'
  | 'resolve'
  | 'update'
  | 'zentao'

interface BugMenuItem {
  id: string
  label: string
  icon: Component
  disabled?: boolean
  danger?: boolean
  dividerBefore?: boolean
}

const props = defineProps<{
  openBugRequest?: {
    bugId: number
    remoteId: string
    nonce: number
  } | null
}>()

const emit = defineEmits<{
  notify: [message: string]
  solveBug: [bug: ZentaoBug]
  solveBugs: [bugs: ZentaoBug[], plan: string]
  testBug: [bug: ZentaoBug]
  repairFailedTest: [
    payload: {
      bug: ZentaoBug
      result: ZentaoTestResult
    },
  ]
  openBugHandled: [nonce: number]
  openCodex: [sessionId: string]
}>()

const auth = useAuthStore()
const codex = useCodexStore()
const workspaces = useWorkspaceStore()
const zentao = useZentaoStore()
const zentaoTests = useZentaoTestStore()
const searchQuery = ref('')
const buildSearchQuery = ref('')
const bugFilter = ref<BugFilter>('active')
const activeTab = ref<ZentaoTab>('bugs')
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
const bugPendingPush = ref<ZentaoBug | null>(null)
const bugPendingConfirm = ref<ZentaoBug | null>(null)
const bugStepsContent = ref<HTMLElement | null>(null)
const resolvingLocally = ref(false)
const pushingToZentao = ref(false)
const confirmingBugId = ref<number | null>(null)
const confirmError = ref('')
const detailError = ref('')
const localSolution = ref('')
const localReason = ref('')
const selectionMode = ref(false)
const selectedBugIds = ref<number[]>([])
const batchPlanOpen = ref(false)
const testResultBug = ref<ZentaoBug | null>(null)
const testResultTarget = ref<ZentaoTestResult | null>(null)
const publishingTestNoteBugId = ref<number | null>(null)
const zentaoBrowser = ref<{
  url: string
  title: string
} | null>(null)
const buildCreateOpen = ref(false)
const testTaskBuild = ref<ZentaoBuild | null>(null)
const autoSyncedScopes = new Set<string>()
const handledExternalBugRequest = ref(0)
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
const syncedProjects = computed(() => {
  const projects = new Map(
    availableProjects.value.map((project) => [
      project.id,
      project,
    ]),
  )
  return zentao.connection.projectIds.map((projectId) => {
    const project = projects.get(projectId)
    return {
      id: projectId,
      name: project?.name ?? `项目 ${projectId}`,
      status: project?.status ?? '',
    }
  })
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
    return `${bug.remoteId} ${bug.title} ${bug.projectName} ${bug.productName} ${bug.openedBy} ${bug.assignedTo} ${bug.confirmed ? '已确认' : '未确认'}`
      .toLocaleLowerCase('zh-CN')
      .includes(query)
  })
})
const filteredBuilds = computed(() => {
  const query = buildSearchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return zentao.builds
  return zentao.builds.filter((build) =>
    [
      build.name,
      build.projectName,
      build.productName,
      build.builder,
      build.status,
      build.scmRevision,
      build.description,
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
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
const {
  currentPage: buildPage,
  pageItems: pagedBuilds,
  setPage: setBuildPage,
} = usePagination(
  filteredBuilds,
  () => `${buildSearchQuery.value}\n${scopeKey.value}`,
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

watch(
  [() => props.openBugRequest?.nonce, () => zentao.bugs],
  () => {
    const request = props.openBugRequest
    if (
      !request ||
      handledExternalBugRequest.value === request.nonce
    ) {
      return
    }
    const bug =
      zentao.bugs.find((item) => item.id === request.bugId) ??
      zentao.bugs.find((item) => item.remoteId === request.remoteId)
    if (!bug) return
    handledExternalBugRequest.value = request.nonce
    activeTab.value = 'bugs'
    openBugDetails(bug)
    emit('openBugHandled', request.nonce)
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
    restoreConnectionForm()
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

function openConfiguration() {
  restoreConnectionForm()
  configurationOpen.value = true
  if (
    zentao.connection.configured &&
    availableProjects.value.length === 0
  ) {
    void loadProjects()
  }
}

function closeConfiguration() {
  if (savingConnection.value) return
  configurationOpen.value = false
  restoreConnectionForm()
  connectionError.value = ''
  passwordVisible.value = false
}

function restoreConnectionForm() {
  const connection = zentao.connection
  connectionForm.baseUrl = connection.baseUrl
  connectionForm.username = connection.username
  connectionForm.password = connection.password
  connectionForm.projectIds = [...connection.projectIds]
  selectedProjectIds.value = [...connection.projectIds]
  projectQuery.value = ''
}

function selectTab(tab: ZentaoTab) {
  activeTab.value = tab
  if (
    tab === 'projects' &&
    zentao.connection.configured &&
    zentao.connection.projectIds.length > 0 &&
    availableProjects.value.length === 0 &&
    !loadingProjects.value
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
        `已同步 ${result.projects} 个项目、${result.bugs} 个 Bug、${result.builds} 个构建`,
      )
    }
  } catch (error) {
    const message =
      error instanceof Error ? error.message : '同步禅道数据失败'
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

function buildStatusLabel(status: string) {
  const value = status.trim().toLocaleLowerCase('zh-CN')
  const labels: Record<string, string> = {
    success: '成功',
    successful: '成功',
    passed: '成功',
    failed: '失败',
    failure: '失败',
    building: '构建中',
    running: '构建中',
    created: '已创建',
  }
  return labels[value] ?? (status.trim() || '已创建')
}

function buildDescription(value: string) {
  if (!value.trim()) return ''
  const container = document.createElement('div')
  container.innerHTML = DOMPurify.sanitize(value, {
    ALLOWED_ATTR: [],
    ALLOWED_TAGS: [],
  })
  return (container.textContent || '').replace(/\s+/g, ' ').trim()
}

function openBuildCreateDialog() {
  if (!zentao.connection.configured) return
  buildCreateOpen.value = true
}

function closeBuildCreateDialog() {
  buildCreateOpen.value = false
}

async function handleBuildCreated(created: ZentaoCreatedBuild) {
  buildCreateOpen.value = false
  emit(
    'notify',
    `构建「${created.name || created.remoteId}」已创建到禅道`,
  )
  await syncBugs(false)
}

function openTestTaskDialog(build: ZentaoBuild) {
  if (!zentao.connection.configured) return
  testTaskBuild.value = build
}

function closeTestTaskDialog() {
  testTaskBuild.value = null
}

function handleTestTaskCreated(created: ZentaoCreatedTestTask) {
  const name = created.name || `测试单 #${created.remoteId}`
  testTaskBuild.value = null
  emit('notify', `测试单「${name}」已提交到禅道`)
}

function bugStatusLabel(bug: ZentaoBug) {
  if (bug.pushedAt) return '已回写禅道'
  if (bug.localResolved) return '平台已解决'
  if (bug.localStatus === 'resolved') return '禅道已解决'
  const labels: Record<string, string> = {
    active: '激活',
    resolved: '已解决',
    closed: '已关闭',
  }
  return labels[bug.status.toLowerCase()] ?? (bug.status || '待处理')
}

function canSendBugToCodex(bug: ZentaoBug) {
  return (
    bug.active &&
    !bug.localResolved &&
    bug.localStatus !== 'resolved' &&
    !bug.pushedAt
  )
}

function canConfirmBug(bug: ZentaoBug) {
  return (
    !bug.localDeleted &&
    bug.status.toLowerCase() === 'active' &&
    !bug.confirmed
  )
}

function confirmBugHint(bug: ZentaoBug) {
  if (bug.confirmed) return 'Bug 已确认'
  if (bug.status.toLowerCase() !== 'active') {
    return '仅激活且未确认的 Bug 可以确认'
  }
  return '确认 Bug 并同步到禅道'
}

function requestConfirmBug(bug: ZentaoBug) {
  if (!canConfirmBug(bug) || confirmingBugId.value !== null) return
  confirmError.value = ''
  bugPendingConfirm.value = bug
}

function closeConfirmBugDialog() {
  if (confirmingBugId.value !== null) return
  bugPendingConfirm.value = null
  confirmError.value = ''
}

async function confirmBugOnZentao() {
  const bug = bugPendingConfirm.value
  if (!bug || confirmingBugId.value !== null) return
  confirmingBugId.value = bug.id
  confirmError.value = ''
  try {
    const updated = await zentao.confirmBug(bug.id)
    if (detailBug.value?.id === updated.id) {
      detailBug.value = updated
    }
    bugPendingConfirm.value = null
    emit('notify', `Bug #${updated.remoteId} 已确认并同步到禅道`)
  } catch (error) {
    confirmError.value =
      error instanceof Error
        ? error.message
        : '确认 Bug 失败'
  } finally {
    confirmingBugId.value = null
  }
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

function canResolveBug(bug: ZentaoBug) {
  return (
    !bug.localResolved &&
    bug.localStatus !== 'resolved' &&
    !bug.pushedAt
  )
}

function canUpdateBug(bug: ZentaoBug) {
  return bug.localResolved && !bug.pushedAt
}

function primaryBugAction(bug: ZentaoBug): BugQuickAction {
  if (canSendBugToCodex(bug)) return 'codex'
  if (latestTestResult(bug.id)?.status === 'passed') {
    return 'test-result'
  }
  if (canTestBug(bug)) return 'test'
  return 'detail'
}

function secondaryBugAction(bug: ZentaoBug): BugQuickAction {
  if (canResolveBug(bug)) return 'resolve'
  if (canUpdateBug(bug)) return 'update'
  if (zentao.connection.configured) return 'zentao'
  return 'detail'
}

function showSecondaryBugAction(bug: ZentaoBug) {
  return secondaryBugAction(bug) !== primaryBugAction(bug)
}

function bugMenuItems(bug: ZentaoBug): BugMenuItem[] {
  const primary = primaryBugAction(bug)
  const secondary = secondaryBugAction(bug)
  const isQuick = (action: BugQuickAction) =>
    action === primary || action === secondary
  const items: BugMenuItem[] = []
  if (
    zentao.connection.configured &&
    !isQuick('zentao')
  ) {
    items.push({
      id: 'zentao',
      label: '在禅道中打开',
      icon: ZentaoMark,
    })
  }
  if (canConfirmBug(bug)) {
    items.push({
      id: 'confirm',
      label:
        confirmingBugId.value === bug.id
          ? '确认中…'
          : '确认 Bug',
      icon: BadgeCheck,
      disabled: confirmingBugId.value !== null,
    })
  }
  if (canResolveBug(bug) && !isQuick('resolve')) {
    items.push({
      id: 'resolve',
      label: '填写解决方案',
      icon: CircleCheck,
      disabled: resolvingLocally.value,
    })
  }
  if (canUpdateBug(bug) && !isQuick('update')) {
    items.push({
      id: 'update',
      label: '更新禅道',
      icon: CloudUpload,
    })
  }
  if (canTestBug(bug) && !isQuick('test')) {
    items.push({
      id: 'test',
      label: testInProgress(bug.id)
        ? '打开 Codex 测试'
        : latestTestResult(bug.id)?.status === 'passed'
          ? '重新执行 Codex 测试'
          : 'Codex 测试',
      icon: FlaskConical,
    })
  }
  if (offerTestRepair(bug)) {
    items.push({
      id: 'repair',
      label: 'Codex 兜底修复',
      icon: Wrench,
    })
  }
  if (latestTestResult(bug.id) && !isQuick('test-result')) {
    items.push({
      id: 'test-result',
      label: '查看测试结果',
      icon: Images,
    })
  }
  if (latestTestResult(bug.id)?.status === 'passed') {
    items.push({
      id: 'test-comment',
      label: latestTestResult(bug.id)?.zentaoNoteError
        ? '重试同步测试说明'
        : latestTestResult(bug.id)?.zentaoNoteAt
          ? '重新同步测试说明'
          : '同步测试说明到禅道',
      icon: MessageSquareText,
      disabled: publishingTestNoteBugId.value !== null,
    })
  }
  if (!isQuick('detail')) {
    items.push({
      id: 'detail',
      label: '查看详情',
      icon: FileText,
    })
  }
  items.push({
    id: 'delete',
    label: '从工作台移除',
    icon: Trash2,
    danger: true,
    dividerBefore: true,
  })
  return items
}

function handlePrimaryBugAction(bug: ZentaoBug) {
  const action = primaryBugAction(bug)
  if (action === 'codex') {
    emit('solveBug', bug)
    return
  }
  if (action === 'test') {
    requestCodexTest(bug)
    return
  }
  if (action === 'test-result') {
    openTestResult(bug)
    return
  }
  openBugDetails(bug)
}

function handleSecondaryBugAction(bug: ZentaoBug) {
  const action = secondaryBugAction(bug)
  if (action === 'resolve') {
    prepareBugResolution(bug)
    return
  }
  if (action === 'update') {
    openBugDetails(bug)
    return
  }
  if (action === 'zentao') {
    void openZentaoBug(bug)
    return
  }
  openBugDetails(bug)
}

function handleBugMenuAction(bug: ZentaoBug, id: string) {
  switch (id) {
    case 'zentao':
      void openZentaoBug(bug)
      break
    case 'confirm':
      requestConfirmBug(bug)
      break
    case 'resolve':
      prepareBugResolution(bug)
      break
    case 'update':
      openBugDetails(bug)
      break
    case 'test':
      requestCodexTest(bug)
      break
    case 'repair':
      requestFailedTestRepair(bug)
      break
    case 'test-result':
      openTestResult(bug)
      break
    case 'test-comment':
      void publishTestResultComment(bug)
      break
    case 'detail':
      openBugDetails(bug)
      break
    case 'delete':
      requestDeleteBug(bug)
      break
  }
}

function testInProgress(bugId: number) {
  return Boolean(
    activeZentaoTestSessionId(
      codex.sessions,
      workspaces.activeWorkspaceId,
      bugId,
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
  const sessionId = activeZentaoTestSessionId(
    codex.sessions,
    workspaces.activeWorkspaceId,
    bug.id,
  )
  if (sessionId) {
    emit('openCodex', sessionId)
    return
  }
  emit('testBug', bug)
}

function openTestResult(bug: ZentaoBug) {
  const result = latestTestResult(bug.id)
  if (!result) return
  testResultBug.value = bug
  testResultTarget.value = result
}

async function publishTestResultComment(bug: ZentaoBug) {
  const result = latestTestResult(bug.id)
  if (!result) {
    emit('notify', '这个 Bug 还没有 Codex 测试结果')
    return
  }
  if (result.status !== 'passed') {
    emit('notify', '只有测试通过的 Codex 结果才能同步测试说明')
    return
  }
  if (publishingTestNoteBugId.value !== null) return
  publishingTestNoteBugId.value = bug.id
  try {
    const published = await publishZentaoTestResultComment(result, {
      force: true,
    })
    zentaoTests.saveResult(published.result)
    if (testResultBug.value?.id === bug.id) {
      testResultTarget.value = published.result
    }
    emit(
      'notify',
      published.alreadyExists
        ? `Bug #${bug.remoteId} 的测试说明已存在于禅道`
        : `Bug #${bug.remoteId} 的测试说明已同步到禅道`,
    )
  } catch (error) {
    const failed = failedZentaoTestComment(result, error)
    zentaoTests.saveResult(failed)
    if (testResultBug.value?.id === bug.id) {
      testResultTarget.value = failed
    }
    emit(
      'notify',
      `Bug #${bug.remoteId} 测试说明同步失败：${
        error instanceof Error ? error.message : '请稍后重试'
      }`,
    )
  } finally {
    publishingTestNoteBugId.value = null
  }
}

function publishOpenTestResultComment() {
  if (!testResultBug.value) return
  void publishTestResultComment(testResultBug.value)
}

function offerTestRepair(bug: ZentaoBug) {
  const result = latestTestResult(bug.id)
  return Boolean(result && result.status !== 'passed')
}

function requestFailedTestRepair(bug: ZentaoBug) {
  const result = latestTestResult(bug.id)
  if (!result || result.status === 'passed') return
  emit('repairFailedTest', { bug, result })
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

function zentaoBugDetailUrl(bug: ZentaoBug) {
  const baseUrl = zentao.connection.baseUrl.trim()
  if (!baseUrl) {
    throw new Error('请先在禅道配置中填写访问地址')
  }
  const base = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
  return new URL(
    `bug-view-${encodeURIComponent(bug.remoteId)}.html`,
    base,
  ).toString()
}

async function openZentaoBug(bug: ZentaoBug) {
  try {
    const url = zentaoBugDetailUrl(bug)
    await openZentaoBrowserUrl({
      url,
      title: `禅道 Bug #${bug.remoteId}`,
    })
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法打开禅道 Bug',
    )
  }
}

async function openZentaoBrowserUrl(payload: {
  url: string
  title: string
}) {
  if (window.workCoveDesktop?.openEmbeddedBrowser) {
    zentaoBrowser.value = payload
    return
  }
  const error = await openWebsiteUrl(payload.url)
  if (error) {
    emit('notify', `无法打开网址：${error}`)
  }
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

function requestPushResolutionToZentao(bug: ZentaoBug) {
  if (pushingToZentao.value) return
  if (!localSolution.value.trim()) {
    detailError.value = '请填写解决方案'
    return
  }
  if (!localReason.value.trim()) {
    detailError.value = '请填写定位原因'
    return
  }
  bugPendingPush.value = bug
}

async function confirmPushResolutionToZentao() {
  const bug = bugPendingPush.value
  if (!bug) return
  await pushResolutionToZentao(bug)
  bugPendingPush.value = null
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
        <span class="zentao-page-mark">
          <ZentaoMark :size="22" />
        </span>
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
                : '配置地址和账号后即可同步 Bug 与构建'
            }}
          </small>
        </div>
      </div>
      <div class="zentao-toolbar__actions">
        <UiButton
          size="sm"
          :disabled="
            !zentao.connection.configured ||
            zentao.connection.projectIds.length === 0
          "
          :loading="zentao.syncing"
          :title="
            zentao.connection.projectIds.length === 0
              ? '请先选择同步项目'
              : '同步所选项目的 Bug 与构建'
          "
          @click="syncBugs(true)"
        >
          <RefreshCw
            v-if="!zentao.syncing"
            :size="13"
          />
          {{ zentao.syncing ? '同步中…' : '同步' }}
        </UiButton>
        <UiButton size="sm" @click="openConfiguration">
          <Settings2 :size="13" />
          配置
        </UiButton>
      </div>
    </header>

    <div class="zentao-tabs" role="tablist" aria-label="禅道页面">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'bugs'"
        :class="{ 'zentao-tab--active': activeTab === 'bugs' }"
        @click="selectTab('bugs')"
      >
        <Bug :size="14" />
        Bug 清单
        <span>{{ zentao.bugs.length }}</span>
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'builds'"
        :class="{ 'zentao-tab--active': activeTab === 'builds' }"
        @click="selectTab('builds')"
      >
        <PackageCheck :size="14" />
        构建清单
        <span>{{ zentao.builds.length }}</span>
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'projects'"
        :class="{ 'zentao-tab--active': activeTab === 'projects' }"
        @click="selectTab('projects')"
      >
        <FolderKanban :size="14" />
        同步项目
        <span>{{ zentao.connection.projectIds.length }}</span>
      </button>
    </div>

    <UiDialog
      v-if="configurationOpen"
      title="禅道连接配置"
      description="配置连接账号和需要同步的项目，保存后会立即同步一次。"
      size="wide"
      height="standard"
      scroll-mode="body"
      initial-focus="#zentao-base-url"
      :suspended="savingConnection"
      @close="closeConfiguration"
    >
      <form
        id="zentao-connection-form"
        class="zentao-connection-form"
        @submit.prevent="saveConnection"
      >
        <UiFormField
          label="禅道地址"
          control-id="zentao-base-url"
          required
        >
          <UiInput
            id="zentao-base-url"
            v-model="connectionForm.baseUrl"
            size="lg"
            placeholder="例如：https://zentao.example.com"
            spellcheck="false"
          />
        </UiFormField>
        <div class="zentao-connection-form__account">
          <UiFormField
            label="用户名"
            control-id="zentao-username"
            required
          >
            <UiInput
              id="zentao-username"
              v-model="connectionForm.username"
              size="lg"
              placeholder="禅道登录用户名"
              autocomplete="username"
              spellcheck="false"
            />
          </UiFormField>
          <UiFormField
            label="密码"
            :hint="
              zentao.connection.configured
                ? '留空继续使用已保存密码'
                : '密码会加密保存'
            "
            :required="!zentao.connection.configured"
          >
            <span class="zentao-password">
              <UiInput
                v-model="connectionForm.password"
                size="lg"
                :type="passwordVisible ? 'text' : 'password'"
                :placeholder="
                  zentao.connection.configured
                    ? '已保存密码'
                    : '禅道登录密码'
                "
                autocomplete="new-password"
                autocapitalize="off"
                :spellcheck="false"
                data-lpignore="true"
                data-1p-ignore="true"
              />
              <button
                type="button"
                :aria-label="
                  passwordVisible ? '隐藏密码' : '显示密码'
                "
                :title="
                  passwordVisible ? '隐藏密码' : '显示密码'
                "
                @click="passwordVisible = !passwordVisible"
              >
                <EyeOff v-if="passwordVisible" :size="15" />
                <Eye v-else :size="15" />
              </button>
            </span>
          </UiFormField>
        </div>
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
            :loading="loadingProjects"
            @click="loadProjects"
          >
            <RefreshCw
              v-if="!loadingProjects"
              :size="13"
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
        <p v-if="connectionError" class="zentao-connection-error" role="alert">
          {{ connectionError }}
        </p>
      </form>

      <template #footer>
        <UiButton
          size="lg"
          :disabled="savingConnection"
          @click="closeConfiguration"
        >
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="zentao-connection-form"
          :loading="savingConnection"
        >
          {{ savingConnection ? '连接中…' : '保存并同步' }}
        </UiButton>
      </template>
    </UiDialog>

    <div
      v-show="activeTab === 'bugs'"
      class="zentao-bug-view"
    >
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
      <UiSearchInput
        v-model="searchQuery"
        class="zentao-list-search"
        label="搜索 Bug"
        placeholder="搜索 Bug 编号、标题或产品"
      />
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
              class="zentao-bug-confirmed"
              :class="{
                'zentao-bug-confirmed--yes': bug.confirmed,
              }"
            >
              {{ bug.confirmed ? '已确认' : '未确认' }}
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
            <span>创建者 {{ bug.openedBy || '未知' }}</span>
            <span>创建于 {{ bug.openedDate || '未知日期' }}</span>
            <span v-if="bug.assignedTo">指派给 {{ bug.assignedTo }}</span>
          </div>
        </div>
        <div class="zentao-bug-row__actions">
          <UiButton
            class="zentao-bug-quick-action"
            :variant="
              primaryBugAction(bug) === 'detail'
                ? 'default'
                : 'primary'
            "
            size="sm"
            @click.stop="handlePrimaryBugAction(bug)"
          >
            <Bot
              v-if="primaryBugAction(bug) === 'codex'"
              :size="13"
            />
            <FlaskConical
              v-else-if="primaryBugAction(bug) === 'test'"
              :size="13"
            />
            <Images
              v-else-if="primaryBugAction(bug) === 'test-result'"
              :size="13"
            />
            <FileText v-else :size="13" />
            {{
              primaryBugAction(bug) === 'codex'
                ? '交给 Codex'
                : primaryBugAction(bug) === 'test'
                  ? testInProgress(bug.id)
                    ? '测试中 · Codex'
                    : 'Codex 测试'
                  : primaryBugAction(bug) === 'test-result'
                    ? '查看测试结果'
                  : '查看详情'
            }}
          </UiButton>
          <UiButton
            v-if="showSecondaryBugAction(bug)"
            class="zentao-bug-quick-action"
            size="sm"
            @click.stop="handleSecondaryBugAction(bug)"
          >
            <CircleCheck
              v-if="secondaryBugAction(bug) === 'resolve'"
              :size="13"
            />
            <CloudUpload
              v-else-if="secondaryBugAction(bug) === 'update'"
              :size="13"
            />
            <ZentaoMark
              v-else-if="secondaryBugAction(bug) === 'zentao'"
              :size="13"
            />
            <FileText v-else :size="13" />
            {{
              secondaryBugAction(bug) === 'resolve'
                ? '解决 Bug'
                : secondaryBugAction(bug) === 'update'
                  ? '更新禅道'
                  : secondaryBugAction(bug) === 'zentao'
                    ? '打开禅道'
                    : '查看详情'
            }}
          </UiButton>
          <span
            class="zentao-bug-more"
            @click.stop
            @keydown.stop
          >
            <UiActionMenu
              :items="bugMenuItems(bug)"
              :label="`Bug #${bug.remoteId} 更多操作`"
              :title="`Bug #${bug.remoteId} 更多操作`"
              @select="handleBugMenuAction(bug, $event)"
            />
          </span>
        </div>
      </article>
    </div>

    <div v-else class="zentao-empty">
      <span>
        <UiFidgetSpinner v-if="zentao.syncing" :size="28" />
        <Bug v-else :size="28" />
      </span>
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
    </div>

    <section
      v-if="activeTab === 'builds'"
      class="zentao-builds-view"
    >
      <header class="zentao-projects-header">
        <div>
          <h2>构建清单</h2>
          <p>
            {{
              zentao.connection.configured
                ? `从 ${zentao.connection.projectIds.length} 个同步项目拉取构建记录`
                : '配置禅道连接后，在这里查看项目构建清单。'
            }}
          </p>
        </div>
        <div class="zentao-builds-header__actions">
          <UiButton
            v-if="!zentao.connection.configured"
            size="sm"
            @click="openConfiguration"
          >
            <Settings2 :size="13" />
            配置连接
          </UiButton>
          <UiButton
            v-else
            variant="primary"
            size="sm"
            @click="openBuildCreateDialog"
          >
            <Plus :size="13" />
            新建构建
          </UiButton>
        </div>
      </header>

      <div class="zentao-builds-toolbar">
        <UiSearchInput
          v-model="buildSearchQuery"
          label="搜索构建清单"
          placeholder="搜索构建、项目、产品、构建人或版本号"
        />
        <span>{{ filteredBuilds.length }} 个构建</span>
      </div>

      <div
        v-if="zentao.syncing && zentao.builds.length === 0"
        class="zentao-projects-state"
      >
        <UiFidgetSpinner :size="22" />
        <span>正在拉取构建清单</span>
      </div>

      <div
        v-else-if="pagedBuilds.length > 0"
        class="zentao-build-list"
      >
        <article
          v-for="build in pagedBuilds"
          :key="build.id"
          class="zentao-build-row"
        >
          <span class="zentao-project-row__icon">
            <PackageCheck :size="17" />
          </span>
          <div class="zentao-build-row__copy">
            <header>
              <strong>{{ build.name }}</strong>
              <span
                class="zentao-build-status"
                :class="`zentao-build-status--${build.status.toLowerCase()}`"
              >
                {{ buildStatusLabel(build.status) }}
              </span>
              <UiButton
                class="zentao-build-test-button"
                size="sm"
                @click.stop="openTestTaskDialog(build)"
              >
                <FlaskConical :size="13" />
                提交测试
              </UiButton>
            </header>
            <div class="zentao-build-row__meta">
              <span>{{ build.projectName || '未知项目' }}</span>
              <span v-if="build.productName">
                {{ build.productName }}
              </span>
              <span v-if="build.builder">{{ build.builder }}</span>
              <span v-if="build.buildDate">{{ build.buildDate }}</span>
            </div>
            <p
              v-if="buildDescription(build.description)"
              :title="buildDescription(build.description)"
            >
              {{ buildDescription(build.description) }}
            </p>
            <code v-if="build.scmRevision" :title="build.scmRevision">
              {{ build.scmRevision }}
            </code>
          </div>
        </article>
      </div>

      <div v-else class="zentao-empty">
        <span><PackageCheck :size="28" /></span>
        <strong>
          {{
            buildSearchQuery.trim()
              ? '没有匹配的构建'
              : '还没有构建记录'
          }}
        </strong>
        <p>
          {{
            buildSearchQuery.trim()
              ? '换一个关键词再试。'
              : zentao.connection.configured
                ? '点击右上角同步，从禅道拉取所选项目的构建清单。'
                : '先配置禅道连接和项目。'
          }}
        </p>
      </div>

      <UiPagination
        :page="buildPage"
        :total="filteredBuilds.length"
        label="构建清单分页"
        @update:page="setBuildPage"
      />
    </section>

    <section
      v-if="activeTab === 'projects'"
      class="zentao-projects-view"
    >
      <header class="zentao-projects-header">
        <div>
          <h2>同步项目</h2>
          <p>
            {{
              zentao.connection.configured
                ? `当前账号 ${zentao.connection.username} · 最近同步 ${formatSyncTime(zentao.connection.lastSyncedAt)}`
                : '配置禅道连接后，在这里查看正在同步的项目。'
            }}
          </p>
        </div>
        <UiButton
          v-if="!zentao.connection.configured"
          size="sm"
          @click="openConfiguration"
        >
          <Settings2 :size="13" />
          配置连接
        </UiButton>
      </header>

      <p
        v-if="projectError"
        class="zentao-projects-error"
        role="alert"
      >
        {{ projectError }}
      </p>

      <div
        v-if="
          loadingProjects &&
          syncedProjects.length === 0
        "
        class="zentao-projects-state"
      >
        <UiFidgetSpinner :size="22" />
        <span>正在读取同步项目</span>
      </div>

      <div
        v-else-if="syncedProjects.length > 0"
        class="zentao-project-list"
      >
        <article
          v-for="project in syncedProjects"
          :key="project.id"
          class="zentao-project-row"
        >
          <span class="zentao-project-row__icon">
            <FolderKanban :size="17" />
          </span>
          <div class="zentao-project-row__copy">
            <strong>{{ project.name }}</strong>
            <small>
              #{{ project.id }}
              <template v-if="project.status">
                · {{ projectStatusLabel(project.status) }}
              </template>
            </small>
          </div>
          <span class="zentao-project-row__mode">
            <RefreshCw :size="12" />
            自动同步
          </span>
        </article>
      </div>

      <div v-else class="zentao-projects-state">
        <FolderKanban :size="22" />
        <strong>还没有同步项目</strong>
        <span>打开配置选择需要同步的禅道项目。</span>
        <UiButton size="sm" @click="openConfiguration">
          <Settings2 :size="13" />
          配置项目
        </UiButton>
      </div>
    </section>
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
    :publishing-note="publishingTestNoteBugId === testResultBug.id"
    @close="closeTestResult"
    @open-url="openZentaoBrowserUrl"
    @sync-note="publishOpenTestResultComment"
  />

  <EmbeddedBrowserDialog
    v-if="zentaoBrowser"
    :url="zentaoBrowser.url"
    :title="zentaoBrowser.title"
    :workspace-id="workspaces.activeWorkspaceId"
    @close="zentaoBrowser = null"
    @notify="emit('notify', $event)"
  />

  <UiDialog
    v-if="detailBug"
    :title="detailBug.title"
    :description="`Bug #${detailBug.remoteId} · 禅道 Bug 详情`"
    size="extra-wide"
    height="tall"
    title-emphasis="prominent"
    scroll-mode="body"
    max-height="min(860px, calc(100dvh - 32px))"
    :suspended="Boolean(bugPendingPush)"
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
              <dt>是否确认</dt>
              <dd>{{ detailBug.confirmed ? '已确认' : '未确认' }}</dd>
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
            size="lg"
            :loading="confirmingBugId === detailBug.id"
            :disabled="
              !canConfirmBug(detailBug) ||
              (confirmingBugId !== null &&
                confirmingBugId !== detailBug.id)
            "
            :title="confirmBugHint(detailBug)"
            @click="requestConfirmBug(detailBug)"
          >
            <BadgeCheck
              v-if="confirmingBugId !== detailBug.id"
              :size="14"
            />
            确认 Bug
          </UiButton>
          <UiButton
            v-if="canTestBug(detailBug)"
            size="lg"
            :title="
              testInProgress(detailBug.id)
                ? '打开正在测试这个 Bug 的 Codex 会话'
                : '在研发环境验证修复结果并生成测试截图'
            "
            @click="requestCodexTest(detailBug)"
          >
            <UiFidgetSpinner
              v-if="testInProgress(detailBug.id)"
              :size="14"
            />
            <FlaskConical
              v-if="!testInProgress(detailBug.id)"
              :size="14"
            />
            {{
              testInProgress(detailBug.id)
                ? '测试中 · 打开 Codex'
                : 'Codex 测试'
            }}
          </UiButton>
          <UiButton
            v-if="offerTestRepair(detailBug)"
            size="lg"
            title="测试未通过时继续让 Codex 定位并修复"
            @click="requestFailedTestRepair(detailBug)"
          >
            <Wrench :size="14" />
            Codex 兜底修复
          </UiButton>
          <UiButton
            v-if="
              !detailBug.localResolved &&
              detailBug.localStatus !== 'resolved' &&
              !detailBug.pushedAt
            "
            variant="primary"
            size="lg"
            :disabled="pushingToZentao"
            @click="sendBugToCodex(detailBug)"
          >
            <Bug :size="14" />
            交给 Codex 解决
          </UiButton>
          <UiButton
            v-if="!detailBug.pushedAt"
            size="lg"
            :loading="resolvingLocally"
            @click="resolveBugLocally(detailBug)"
          >
            <CircleCheck v-if="!resolvingLocally" :size="14" />
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
            :loading="pushingToZentao"
            @click="requestPushResolutionToZentao(detailBug)"
          >
            <CloudUpload v-if="!pushingToZentao" :size="14" />
            {{ pushingToZentao ? '更新中…' : '更新备注到禅道' }}
          </UiButton>
        </div>
      </footer>
    </template>
  </UiDialog>

  <ZentaoBuildCreateDialog
    v-if="buildCreateOpen"
    :workspace-id="workspaces.activeWorkspaceId"
    @close="closeBuildCreateDialog"
    @created="handleBuildCreated"
  />

  <ZentaoTestTaskCreateDialog
    v-if="testTaskBuild"
    :workspace-id="workspaces.activeWorkspaceId"
    :build="testTaskBuild"
    @close="closeTestTaskDialog"
    @created="handleTestTaskCreated"
  />

  <UiConfirmDialog
    v-if="bugPendingPush"
    title="更新备注到禅道？"
    :description="`Bug #${bugPendingPush.remoteId} 的解决方案和定位原因将写入禅道。`"
    warning="这会直接修改禅道中的 Bug 记录，请确认内容无误后继续。"
    confirm-label="确认更新"
    busy-label="正在更新…"
    :busy="pushingToZentao"
    @close="bugPendingPush = null"
    @confirm="confirmPushResolutionToZentao"
  >
    <div class="zentao-delete-target">
      <strong>#{{ bugPendingPush.remoteId }}</strong>
      <span>{{ bugPendingPush.title }}</span>
    </div>
  </UiConfirmDialog>

  <UiConfirmDialog
    v-if="bugPendingConfirm"
    title="确认 Bug 并同步到禅道？"
    :description="`Bug #${bugPendingConfirm.remoteId} 将被标记为已确认。`"
    warning="这会直接修改禅道中的 Bug 状态，请确认当前 Bug 内容无误后继续。"
    confirm-label="确认并同步"
    busy-label="正在确认…"
    :busy="confirmingBugId !== null"
    @close="closeConfirmBugDialog"
    @confirm="confirmBugOnZentao"
  >
    <div class="zentao-delete-target">
      <strong>#{{ bugPendingConfirm.remoteId }}</strong>
      <span>{{ bugPendingConfirm.title }}</span>
      <p v-if="confirmError" role="alert">{{ confirmError }}</p>
    </div>
  </UiConfirmDialog>

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

.zentao-page-mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  place-items: center;
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

.zentao-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-tabs button {
  position: relative;
  display: inline-flex;
  height: 42px;
  align-items: center;
  gap: 6px;
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  background: transparent;
}

.zentao-tabs button::after {
  position: absolute;
  right: var(--space-2);
  bottom: -1px;
  left: var(--space-2);
  height: 2px;
  background: transparent;
  border-radius: 999px 999px 0 0;
  content: "";
}

.zentao-tabs button:hover {
  color: var(--text);
}

.zentao-tabs .zentao-tab--active {
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.zentao-tabs .zentao-tab--active::after {
  background: var(--system-blue);
}

.zentao-tabs button > span {
  min-width: 18px;
  padding: 1px 5px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-regular);
  text-align: center;
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.zentao-connection-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-5);
}

.zentao-connection-form__account {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-3);
}

.zentao-connection-error {
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
  max-height: 240px;
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

.zentao-bug-view {
  min-width: 0;
}

.zentao-builds-view {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  padding: var(--space-4);
}

.zentao-builds-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
}

.zentao-builds-toolbar > span {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.zentao-builds-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.zentao-build-list {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.zentao-build-row {
  display: grid;
  min-width: 0;
  grid-template-columns: var(--control-lg) minmax(0, 1fr);
  align-items: start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-build-row:last-child {
  border-bottom: 0;
}

.zentao-build-row:hover {
  background: var(--hover-bg);
}

.zentao-build-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-2);
}

.zentao-build-row__copy > header {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.zentao-build-row__copy > header strong {
  overflow: hidden;
  min-width: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-build-test-button {
  margin-left: auto;
}

.zentao-build-row__meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.zentao-build-row__meta span {
  overflow-wrap: anywhere;
}

.zentao-build-row__meta span + span::before {
  margin-right: var(--space-2);
  content: "·";
}

.zentao-build-row__copy > p,
.zentao-build-row__copy > code {
  overflow: hidden;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-build-row__copy > code {
  color: var(--text-subtle);
  font-family: var(--font-mono);
}

.zentao-build-status {
  flex: 0 0 auto;
  padding: 2px 6px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
}

.zentao-build-status--success,
.zentao-build-status--successful,
.zentao-build-status--passed {
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 10%,
    transparent
  );
}

.zentao-build-status--failed,
.zentao-build-status--failure {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 9%,
    transparent
  );
}

.zentao-build-status--building,
.zentao-build-status--running {
  color: var(--system-orange);
  background: color-mix(
    in srgb,
    var(--system-orange) 10%,
    transparent
  );
}

.zentao-projects-view {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  padding: var(--space-4);
}

.zentao-projects-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-3);
}

.zentao-projects-header h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.zentao-projects-header p {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.zentao-projects-error {
  padding: var(--space-2) var(--space-3);
  margin: 0 0 var(--space-3);
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-red) 7%,
    transparent
  );
  border-radius: var(--radius-control);
}

.zentao-project-list {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.zentao-project-row {
  display: grid;
  min-width: 0;
  grid-template-columns:
    var(--control-lg)
    minmax(0, 1fr)
    auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.zentao-project-row:last-child {
  border-bottom: 0;
}

.zentao-project-row:hover {
  background: var(--hover-bg);
}

.zentao-project-row__icon {
  display: grid;
  width: var(--control-lg);
  height: var(--control-lg);
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.zentao-project-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.zentao-project-row__copy strong,
.zentao-project-row__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-project-row__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-project-row__copy small,
.zentao-project-row__mode {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-project-row__mode {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  white-space: nowrap;
}

.zentao-projects-state {
  display: flex;
  min-height: 240px;
  flex: 1;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.zentao-projects-state strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
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

.zentao-list-search {
  margin-left: auto;
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
.zentao-bug-status,
.zentao-bug-confirmed {
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

.zentao-bug-confirmed {
  color: var(--text-subtle);
}

.zentao-bug-confirmed--yes {
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 10%,
    transparent
  );
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
  gap: var(--space-1);
}

.zentao-bug-quick-action {
  flex: 0 0 auto;
}

.zentao-bug-more {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
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

@media (max-width: 820px) {
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
    justify-content: flex-end;
  }

  .zentao-connection-form__account,
  .zentao-project-options {
    grid-template-columns: 1fr;
  }

  .zentao-connection-form {
    padding: 0 var(--space-4) var(--space-4);
  }

  .zentao-tabs {
    overflow-x: auto;
    padding: 0 var(--space-3);
  }

  .zentao-builds-view,
  .zentao-projects-view {
    padding: var(--space-3);
  }

  .zentao-builds-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .zentao-builds-toolbar > span {
    align-self: flex-end;
  }

  .zentao-build-row {
    padding: var(--space-3);
  }

  .zentao-projects-header {
    align-items: stretch;
    flex-direction: column;
  }

  .zentao-project-row {
    padding: var(--space-3);
  }

  .zentao-project-field__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .zentao-list-search {
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
    gap: var(--space-1);
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

</style>
