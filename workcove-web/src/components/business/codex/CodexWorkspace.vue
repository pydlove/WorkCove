<script setup lang="ts">
import {
  AppWindow,
  ArrowDown,
  ArrowUp,
  Bot,
  Bug,
  ChartNoAxesColumn,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  CircleX,
  Clock3,
  Database,
  FileImage,
  FileSearch,
  FileText,
  Folder,
  FolderOpen,
  FolderPlus,
  GitBranch,
  Globe2,
  Images,
  ListChecks,
  LoaderCircle,
  NotebookPen,
  PanelLeft,
  Paperclip,
  Pencil,
  Pin,
  Plus,
  Rocket,
  Search,
  Server,
  ShieldCheck,
  SquareTerminal,
  SquarePen,
  Square,
  Trash2,
  Wrench,
  X,
} from '@lucide/vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import {
  chooseCodexDirectory,
  getCodexStatus,
  interruptCodexTurn,
  listCodexModels,
  resumeCodexThread,
  setCodexThreadName,
  startCodexThread,
  startCodexTurn,
  subscribeCodexEvents,
  uploadCodexAttachment,
  type CodexModelInfo,
  type CodexSandboxMode,
  type CodexGatewayEvent,
} from '@/api/codex'
import { listZentaoTestEvidence } from '@/api/zentao'
import { PRODUCT_NAME } from '@/brand'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCodexStore } from '@/stores/useCodexStore'
import { useDatabaseStore } from '@/stores/useDatabaseStore'
import { useDeploymentStore } from '@/stores/useDeploymentStore'
import { useGitRepositoryStore } from '@/stores/useGitRepositoryStore'
import { useManagedServerStore } from '@/stores/useManagedServerStore'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useWorkDirectoryStore } from '@/stores/useWorkDirectoryStore'
import { useWorkNoteStore } from '@/stores/useWorkNoteStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { useZentaoStore } from '@/stores/useZentaoStore'
import { useZentaoTestStore } from '@/stores/useZentaoTestStore'
import TokenUsageCharts from '@/components/business/workbench/TokenUsageCharts.vue'
import CodexMark from '@/components/ui/CodexMark.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { usePagination } from '@/composables/usePagination'
import { loadTokenUsage } from '@/services/tokenUsage'
import {
  loadPreferredCodexDirectory,
  savePreferredCodexDirectory,
} from '@/services/localCodexSessions'
import { extractZentaoResolution } from '@/utils/zentaoResolution'
import { extractZentaoTestResult } from '@/utils/zentaoTestResult'
import type {
  CodexAccessMode,
  CodexActivity,
  CodexActivityType,
  CodexMessageTimelineEntry,
  CodexSession,
  CodexTask,
  CodexTaskAttachment,
  CodexTimelineEntry,
} from '@/types/codex'
import type { ZentaoBug } from '@/types/zentao'
import type {
  TokenUsageSnapshot,
  UsageRange,
} from '@/types/usage'
import type { WorkDirectory } from '@/types/workDirectory'
import type { WorkNote } from '@/types/workNote'

const emit = defineEmits<{
  notify: [message: string]
}>()

const props = withDefaults(
  defineProps<{
    assistantName?: string
    assistantIcon?: string | null
  }>(),
  {
    assistantName: 'Codex',
    assistantIcon: null,
  },
)

const assistantDisplayName = computed(
  () => props.assistantName.trim() || 'Codex',
)
const auth = useAuthStore()
const codex = useCodexStore()
const databaseStore = useDatabaseStore()
const deploymentStore = useDeploymentStore()
const gitRepositoryStore = useGitRepositoryStore()
const managedServerStore = useManagedServerStore()
const shortcutStore = useShortcutStore()
const workDirectories = useWorkDirectoryStore()
const noteStore = useWorkNoteStore()
const zentaoTestStore = useZentaoTestStore()
const workspaces = useWorkspaceStore()
const zentao = useZentaoStore()
const draft = ref('')
const composer = ref<HTMLTextAreaElement | null>(null)
const attachmentInput = ref<HTMLInputElement | null>(null)
const pendingAttachments = ref<PendingAttachment[]>([])
const attachmentDragActive = ref(false)
const codexModels = ref<CodexModelInfo[]>([])
const modelsLoading = ref(false)
const modelsError = ref('')
const transcript = ref<HTMLElement | null>(null)
const transcriptFollow = ref(true)
const transcriptHasNewContent = ref(false)
const bridgeChecked = ref(false)
const bridgeReady = ref(false)
const bridgeConnected = ref(false)
const bridgeExecutable = ref('')
const taskPanelOpen = ref(false)
const sessionPanelOpen = ref(false)
const sessionBrandMenuOpen = ref(false)
const sessionBrandMenu = ref<HTMLElement | null>(null)
const sessionSearchOpen = ref(false)
const sessionSearchQuery = ref('')
const pinnedSectionOpen = ref(true)
const projectsSectionOpen = ref(true)
const collapsedProjectKeys = ref<string[]>([])
const createProjectOpen = ref(false)
const projectName = ref('')
const projectSourceMode = ref<'folder' | 'workDirectory'>('folder')
const projectFolderPath = ref('')
const projectWorkDirectoryPath = ref('')
const choosingProjectFolder = ref(false)
const projectError = ref('')
const deleteSessionTarget = ref<CodexSession | null>(null)
const sessionContextMenuOpen = ref(false)
const sessionContextMenu = ref<HTMLElement | null>(null)
const contextMenuSession = ref<CodexSession | null>(null)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const renamingSessionId = ref('')
const renameDraft = ref('')
const bugPickerOpen = ref(false)
const bugPicker = ref<HTMLElement | null>(null)
const notePickerOpen = ref(false)
const notePicker = ref<HTMLElement | null>(null)
const noteSearchQuery = ref('')
const selectedNoteId = ref('')
const developmentPickerOpen = ref(false)
const developmentPicker = ref<HTMLElement | null>(null)
const developmentResourceKind = ref<DevelopmentResourceKind>('git')
const developmentSearchQuery = ref('')
const developmentResourcesLoading = ref(false)
const developmentResourcesError = ref('')
const selectedDevelopmentResources = ref<DevelopmentResourceItem[]>([])
const composerAddMenuOpen = ref(false)
const composerAddMenu = ref<HTMLElement | null>(null)
const runSettingsOpen = ref(false)
const runSettingsModelListOpen = ref(false)
const runSettings = ref<HTMLElement | null>(null)
const runSettingsPopover = ref<HTMLElement | null>(null)
const accessModeOpen = ref(false)
const accessMode = ref<HTMLElement | null>(null)
const accessModePopover = ref<HTMLElement | null>(null)
const bugSearchQuery = ref('')
const composerBugIds = ref<number[]>([])
const composerTestBugId = ref<number | null>(null)
const composerTestRunId = ref('')
const composerTestEvidenceDirectory = ref('')
const usageOpen = ref(false)
const usageLoading = ref(false)
const usageError = ref('')
const usageRange = ref<UsageRange>('today')
const usageSnapshot = ref<TokenUsageSnapshot | null>(null)
const clockNow = ref(Date.now())
const resumedThreads = new Set<string>()
const threadSandboxModes = new Map<string, CodexSandboxMode>()
const savingBugTaskIds = new Set<string>()
const scheduledZentaoSaveTimers = new Map<string, number>()
const savingZentaoTestTaskIds = new Set<string>()
const scheduledZentaoTestTimers = new Map<string, number>()
let stopCodexEvents: (() => void) | null = null
let persistTimer: number | null = null
let clockTimer: number | null = null
let lastScrollKey = ''
let transcriptProgrammaticScrollUntil = 0

const TRANSCRIPT_BOTTOM_THRESHOLD = 56
const MAX_ATTACHMENTS = 8
const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
const MAX_DEVELOPMENT_RESOURCES = 8
const ZENTAO_SAVE_DELAY_MS = 250
const NO_RESPONSE_UPDATE_ERROR = 'Codex 未返回修复说明，暂不更新 Bug 单'
const TASK_WAITING_THRESHOLD_MS = 30_000
const SESSION_CONTEXT_MENU_WIDTH = 188
const SESSION_CONTEXT_MENU_HEIGHT = 116

interface PendingAttachment extends CodexTaskAttachment {
  localId: string
  previewUrl: string
  uploading: boolean
  error: string
}

type DevelopmentResourceKind =
  | 'git'
  | 'database'
  | 'deployment'
  | 'server'
  | 'website'
  | 'application'

interface DevelopmentResourceItem {
  key: string
  kind: DevelopmentResourceKind
  name: string
  meta: string
  promptLines: string[]
  fullAccessPromptLines?: string[]
  searchText: string
}

type TaskTimelineRenderEntry =
  | {
      id: string
      kind: 'message'
      text: string
      streaming: boolean
    }
  | {
      id: string
      kind: 'activity'
      activity: CodexActivity
    }
  | {
      id: string
      kind: 'commandGroup'
      activities: CodexActivity[]
    }

const usageRangeItems: Array<{ value: UsageRange; label: string }> = [
  { value: 'today', label: '当天' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: 'all', label: '全部' },
]

const workspaceSessions = computed(() =>
  codex.sessions.filter(
    (session) =>
      session.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const workspaceDirectories = computed(() =>
  workDirectories.directories.filter(
    (directory) =>
      directory.workspaceId === workspaces.activeWorkspaceId,
  ),
)
const projectSourceItems: Array<{
  value: 'folder' | 'workDirectory'
  label: string
}> = [
  { value: 'folder', label: '文件夹' },
  { value: 'workDirectory', label: '工作目录' },
]
const selectedProjectPath = computed(() =>
  projectSourceMode.value === 'folder'
    ? projectFolderPath.value
    : projectWorkDirectoryPath.value,
)
const selectedWorkDirectory = computed(() =>
  workspaceDirectories.value.find(
    (directory) => directory.path === projectWorkDirectoryPath.value,
  ),
)

const activeSession = computed(() =>
  workspaceSessions.value.find(
    (session) => session.id === codex.activeSessionId,
  ),
)

const activeWorkingDirectory = computed(() =>
  workspaceDirectories.value.find(
    (directory) => directory.path === activeSession.value?.cwd,
  ),
)

const workingDirectoryLabel = computed(() => {
  if (activeWorkingDirectory.value) return activeWorkingDirectory.value.name
  return (
    activeSession.value?.cwd?.split('/').filter(Boolean).pop() ||
    '选择目录'
  )
})
const selectedBug = computed(() => zentao.selectedBug)
const linkedTaskBugs = computed(() => {
  const ids =
    composerBugIds.value.length > 0
      ? composerBugIds.value
      : selectedBug.value
        ? [selectedBug.value.id]
        : []
  const bugsById = new Map(
    zentao.bugs.map((bug) => [bug.id, bug]),
  )
  return ids
    .map((id) => bugsById.get(id))
    .filter((bug): bug is ZentaoBug => Boolean(bug))
})
const linkedBugLabel = computed(() => {
  if (linkedTaskBugs.value.length > 1) {
    return `${linkedTaskBugs.value.length} 个 Bug`
  }
  const bug = linkedTaskBugs.value[0]
  return bug
    ? `#${bug.remoteId} ${bug.title}`
    : '选择 Bug'
})

const filteredBugChoices = computed(() => {
  const query = bugSearchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return zentao.activeBugs
  return zentao.activeBugs.filter((bug) =>
    `#${bug.remoteId} ${bug.title} ${bug.productName}`
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})

const selectedNote = computed(
  () =>
    noteStore.notes.find(
      (note) =>
        note.id === selectedNoteId.value &&
        note.workspaceId === workspaces.activeWorkspaceId,
    ) ?? null,
)

const workspaceNoteChoices = computed(() =>
  noteStore.notes
    .filter(
      (note) =>
        note.workspaceId === workspaces.activeWorkspaceId &&
        !note.archived,
    )
    .sort(
      (left, right) =>
        Number(right.pinned) - Number(left.pinned) ||
        right.updatedAt.localeCompare(left.updatedAt),
    ),
)

const filteredNoteChoices = computed(() => {
  const query = noteSearchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return workspaceNoteChoices.value
  return workspaceNoteChoices.value.filter((note) =>
    [
      note.title,
      note.content,
      note.tags.join(' '),
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})

const developmentResourceKindItems: Array<{
  value: DevelopmentResourceKind
  label: string
}> = [
  { value: 'git', label: 'Git' },
  { value: 'database', label: '数据库' },
  { value: 'deployment', label: '部署' },
  { value: 'server', label: '服务器' },
  { value: 'website', label: '网址' },
  { value: 'application', label: 'App' },
]

const developmentResourceItems = computed<DevelopmentResourceItem[]>(() => {
  const workspaceId = workspaces.activeWorkspaceId
  const gitRepositories = gitRepositoryStore.repositories
    .filter((repository) => repository.workspaceId === workspaceId)
    .map<DevelopmentResourceItem>((repository) => {
      const status = gitRepositoryStore.statuses[repository.id]
      const location = repository.path || repository.remoteUrl || '未设置路径'
      const promptLines = [
        '类型：Git 仓库',
        `名称：${repository.name}`,
        `分类：${repository.category}`,
        `本地路径：${repository.path || '无'}`,
        `远程地址：${repository.remoteUrl || '无'}`,
      ]
      if (status) {
        promptLines.push(
          `当前分支：${status.branch || '未知'}`,
          `工作区状态：${status.clean ? '干净' : `已暂存 ${status.staged}，未暂存 ${status.unstaged}，未跟踪 ${status.untracked}，冲突 ${status.conflicted}`}`,
          `领先/落后：${status.ahead}/${status.behind}`,
          `最新提交：${status.latestCommitShortHash || '无'} ${status.latestCommitSubject || ''}`.trim(),
        )
      }
      return {
        key: `git:${repository.id}`,
        kind: 'git',
        name: repository.name,
        meta: `${repository.category} · ${location}`,
        promptLines,
        searchText: [
          repository.name,
          repository.path,
          repository.remoteUrl,
          repository.category,
          status?.branch ?? '',
        ].join(' '),
      }
    })

  const databases = databaseStore.connections
    .filter((connection) => connection.workspaceId === workspaceId)
    .map<DevelopmentResourceItem>((connection) => ({
      key: `database:${connection.id}`,
      kind: 'database',
      name: connection.name,
      meta: `${connection.type} · ${connection.host}:${connection.port}/${connection.databaseName}`,
      promptLines: [
        '类型：数据库',
        `名称：${connection.name}`,
        `引擎：${connection.type}`,
        `地址：${connection.host}:${connection.port}`,
        `数据库：${connection.databaseName}`,
        `用户名：${connection.username}`,
        `SSL：${connection.sslEnabled ? '启用' : '未启用'}`,
      ],
      searchText: [
        connection.name,
        connection.type,
        connection.host,
        connection.databaseName,
        connection.username,
      ].join(' '),
    }))

  const deployments = deploymentStore.deployments
    .filter((deployment) => deployment.workspaceId === workspaceId)
    .map<DevelopmentResourceItem>((deployment) => {
      const latestRun = deploymentStore.runs.find(
        (run) => run.deploymentId === deployment.id,
      )
      const promptLines = [
        '类型：部署配置',
        `名称：${deployment.name}`,
        `服务器：${deployment.serverName || '未关联'}`,
        `远程目录：${deployment.remoteDirectory || '未设置'}`,
        `脚本文件：${deployment.scriptFilename || '未设置'}`,
      ]
      if (latestRun) {
        promptLines.push(
          `最近执行：${latestRun.status} · exit ${latestRun.exitCode ?? '-'} · ${latestRun.summary || '无摘要'}`,
          `最近执行时间：${latestRun.finishedAt}`,
        )
      }
      return {
        key: `deployment:${deployment.id}`,
        kind: 'deployment',
        name: deployment.name,
        meta: [
          deployment.serverName || '未关联服务器',
          deployment.remoteDirectory,
          latestRun ? `最近 ${latestRun.status}` : '',
        ]
          .filter(Boolean)
          .join(' · '),
        promptLines,
        fullAccessPromptLines: deployment.scriptContent.trim()
          ? [
              `部署脚本 ${deployment.scriptFilename}：`,
              deployment.scriptContent.trim().slice(0, 12_000),
            ]
          : [],
        searchText: [
          deployment.name,
          deployment.serverName,
          deployment.remoteDirectory,
          deployment.scriptFilename,
        ].join(' '),
      }
    })

  const servers = managedServerStore.servers
    .filter((server) => server.workspaceId === workspaceId)
    .map<DevelopmentResourceItem>((server) => ({
      key: `server:${server.id}`,
      kind: 'server',
      name: server.name,
      meta: `${server.host}:${server.port} · ${server.username}`,
      promptLines: [
        '类型：服务器',
        `名称：${server.name}`,
        `地址：${server.host}:${server.port}`,
        `登录用户：${server.username}`,
        `主机指纹：${server.hostKeyFingerprint || '未记录'}`,
      ],
      searchText: [
        server.name,
        server.host,
        server.username,
      ].join(' '),
    }))

  const websites = shortcutStore.websites
    .filter((website) => website.workspaceId === workspaceId)
    .map<DevelopmentResourceItem>((website) => {
      const safeUrl = sanitizedWebsiteUrl(website.url)
      return {
        key: `website:${website.id}`,
        kind: 'website',
        name: website.name,
        meta: `${website.category} · ${safeUrl}`,
        promptLines: [
          '类型：网址',
          `名称：${website.name}`,
          `分类：${website.category}`,
          `地址：${safeUrl}`,
          `累计访问：${website.clickCount} 次`,
          `最近访问：${website.lastClickedAt || '无记录'}`,
        ],
        fullAccessPromptLines:
          safeUrl === website.url
            ? []
            : [`完整 URL：${website.url}`],
        searchText: [
          website.name,
          website.url,
          website.category,
        ].join(' '),
      }
    })

  const applications = shortcutStore.applications
    .filter((application) => application.workspaceId === workspaceId)
    .map<DevelopmentResourceItem>((application) => ({
      key: `application:${application.id}`,
      kind: 'application',
      name: application.name,
      meta: application.path,
      promptLines: [
        '类型：App',
        `名称：${application.name}`,
        `本机路径：${application.path}`,
        `累计打开：${application.clickCount} 次`,
        `最近打开：${application.lastOpenedAt || '无记录'}`,
      ],
      searchText: [
        application.name,
        application.path,
      ].join(' '),
    }))

  return [
    ...gitRepositories,
    ...databases,
    ...deployments,
    ...servers,
    ...websites,
    ...applications,
  ]
})

const activeDevelopmentResources = computed(() =>
  developmentResourceItems.value.filter(
    (resource) => resource.kind === developmentResourceKind.value,
  ),
)

const filteredDevelopmentResources = computed(() => {
  const query = developmentSearchQuery.value
    .trim()
    .toLocaleLowerCase('zh-CN')
  if (!query) return activeDevelopmentResources.value
  return activeDevelopmentResources.value.filter((resource) =>
    resource.searchText.toLocaleLowerCase('zh-CN').includes(query),
  )
})

const sortedSessions = computed(() => {
  const query = sessionSearchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return [...workspaceSessions.value]
    .filter((session) => {
      if (session.tasks.length === 0) return false
      if (!query) return true
      return [
        session.title,
        session.cwd,
        sessionPreview(session),
      ]
        .join(' ')
        .toLocaleLowerCase('zh-CN')
        .includes(query)
    })
    .sort(
      (a, b) =>
        new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    )
})
const pinnedSessions = computed(() =>
  sortedSessions.value.filter((session) => session.pinned),
)
const projectSessionGroups = computed(() => {
  const groups = new Map<
    string,
    {
      key: string
      label: string
      path: string
      sessions: CodexSession[]
      updatedAt: number
    }
  >()

  for (const session of sortedSessions.value) {
    if (session.pinned) continue
    const path = session.cwd.trim()
    const key = path || '__unlinked__'
    const label =
      path.split(/[\\/]/).filter(Boolean).at(-1) || '未关联项目'
    const existing = groups.get(key)
    if (existing) {
      existing.sessions.push(session)
      existing.updatedAt = Math.max(
        existing.updatedAt,
        new Date(session.updatedAt).getTime(),
      )
      continue
    }
    groups.set(key, {
      key,
      label,
      path,
      sessions: [session],
      updatedAt: new Date(session.updatedAt).getTime(),
    })
  }

  return [...groups.values()].sort(
    (a, b) => b.updatedAt - a.updatedAt,
  )
})

const activeTasks = computed(() =>
  [...(activeSession.value?.tasks ?? [])].reverse(),
)
const {
  currentPage: taskPage,
  pageItems: pagedTasks,
  setPage: setTaskPage,
} = usePagination(activeTasks, () => codex.activeSessionId)

const runningTask = computed(() =>
  activeSession.value?.tasks.find((task) => task.status === 'running'),
)
const queuedTasks = computed(() =>
  (activeSession.value?.tasks ?? []).filter(
    (task) => task.status === 'queued',
  ),
)
const runningTaskProgressLabel = computed(() => {
  const task = runningTask.value
  const queuedLabel =
    queuedTasks.value.length > 0
      ? ` · ${queuedTasks.value.length} 个任务排队`
      : ''
  if (!task) {
    return queuedTasks.value.length > 0
      ? `${queuedTasks.value.length} 个任务排队`
      : 'Codex 正在处理当前任务'
  }
  return `${assistantPendingLabel(task)} · 已用时 ${taskElapsedLabel(task)}${queuedLabel}`
})
const interruptingTaskId = ref('')

const sessionStats = computed(() => {
  const tasks = activeSession.value?.tasks ?? []
  return {
    tasks: tasks.length,
    commands: tasks.reduce((sum, task) => sum + task.commandCount, 0),
    files: tasks.reduce((sum, task) => sum + task.fileChangeCount, 0),
    tools: tasks.reduce((sum, task) => sum + task.toolCount, 0),
  }
})

const availableModels = computed(() =>
  codexModels.value.filter((model) => !model.hidden),
)
const selectedModelInfo = computed(() => {
  const sessionModel = activeSession.value?.model
  return (
    availableModels.value.find((model) => model.model === sessionModel) ??
    availableModels.value.find((model) => model.isDefault) ??
    availableModels.value[0] ??
    null
  )
})
const modelOptions = computed(() =>
  availableModels.value.map((model) => ({
    value: model.model,
    label: model.model,
    description:
      model.displayName && model.displayName !== model.model
        ? model.displayName
        : model.description,
  })),
)
const selectedModel = computed({
  get: () => selectedModelInfo.value?.model ?? '',
  set: (value: string) => selectModel(value),
})
const effortOptions = computed(() =>
  (selectedModelInfo.value?.supportedReasoningEfforts ?? []).map(
    (option) => ({
      value: option.reasoningEffort,
      label: effortLabel(option.reasoningEffort),
      description: option.description,
    }),
  ),
)
const selectedEffort = computed({
  get: () => {
    const sessionEffort = activeSession.value?.reasoningEffort
    const supported = effortOptions.value
    if (supported.some((option) => option.value === sessionEffort)) {
      return sessionEffort ?? ''
    }
    return selectedModelInfo.value?.defaultReasoningEffort ?? ''
  },
  set: (value: string) => selectReasoningEffort(value),
})
const selectedEffortIndex = computed(() =>
  Math.max(
    0,
    effortOptions.value.findIndex(
      (option) => option.value === selectedEffort.value,
    ),
  ),
)
const effortProgress = computed(() => {
  if (effortOptions.value.length <= 1) return '100%'
  return `${(selectedEffortIndex.value / (effortOptions.value.length - 1)) * 100}%`
})
const currentAccessMode = computed(
  () => activeSession.value?.accessMode ?? 'workspace',
)
const currentAccessModeLabel = computed(() =>
  currentAccessMode.value === 'full' ? '完全访问' : '工作区访问',
)
const accessModeOptions: Array<{
  value: CodexAccessMode
  label: string
  description: string
}> = [
  {
    value: 'workspace',
    label: '工作区访问',
    description: '仅在工作目录内读写文件和执行命令',
  },
  {
    value: 'full',
    label: '完全访问权限',
    description: '可不受限制地访问互联网和你电脑上的任何文件',
  },
]

const canSend = computed(
  () => {
    const attachmentsReady = pendingAttachments.value.every(
      (attachment) => !attachment.uploading && !attachment.error,
    )
    return (
      Boolean(activeSession.value) &&
      Boolean(
        draft.value.trim() ||
          pendingAttachments.value.length ||
          selectedNote.value ||
          selectedDevelopmentResources.value.length,
      ) &&
      attachmentsReady &&
      bridgeReady.value
    )
  },
)
const composerPlaceholder = computed(() => {
  if (activeSession.value?.status === 'running') {
    return '添加后续任务，发送后进入队列'
  }
  return activeSession.value?.tasks.length
    ? '和 Codex 一起工作'
    : '随心输入'
})

function effortLabel(value: string) {
  const labels: Record<string, string> = {
    minimal: '最少',
    low: '低',
    medium: '中',
    high: '高',
    xhigh: '极高',
    max: '最高',
    ultra: '超强',
  }
  return labels[value] ?? value
}

function selectModel(value: string) {
  const session = activeSession.value
  const model = availableModels.value.find((item) => item.model === value)
  if (!session || !model) return
  session.model = model.model
  session.reasoningEffort = model.defaultReasoningEffort
  codex.persist()
}

function selectReasoningEffort(value: string) {
  const session = activeSession.value
  if (
    !session ||
    !effortOptions.value.some((option) => option.value === value)
  ) {
    return
  }
  session.reasoningEffort = value
  codex.persist()
}

function ensureSessionModelSelection() {
  const session = activeSession.value
  const model = selectedModelInfo.value
  if (!session || !model) return
  let changed = false
  if (session.model !== model.model) {
    session.model = model.model
    changed = true
  }
  const supportedEfforts = new Set(
    model.supportedReasoningEfforts.map(
      (option) => option.reasoningEffort,
    ),
  )
  if (
    !session.reasoningEffort ||
    !supportedEfforts.has(session.reasoningEffort)
  ) {
    session.reasoningEffort = model.defaultReasoningEffort
    changed = true
  }
  if (changed) codex.persist()
}

async function loadModels() {
  if (modelsLoading.value) return
  modelsLoading.value = true
  modelsError.value = ''
  try {
    const response = await listCodexModels()
    codexModels.value = Array.isArray(response.data) ? response.data : []
    if (codexModels.value.length === 0) {
      modelsError.value = '没有可用的 Codex 模型'
    }
    ensureSessionModelSelection()
  } catch (error) {
    codexModels.value = []
    modelsError.value =
      error instanceof Error ? error.message : '无法读取 Codex 模型'
  } finally {
    modelsLoading.value = false
  }
}

function openAttachmentPicker() {
  if (!bridgeReady.value) return
  attachmentInput.value?.click()
}

function toggleComposerAddMenu() {
  if (!bridgeReady.value) return
  runSettingsOpen.value = false
  runSettingsModelListOpen.value = false
  accessModeOpen.value = false
  bugPickerOpen.value = false
  notePickerOpen.value = false
  developmentPickerOpen.value = false
  composerAddMenuOpen.value = !composerAddMenuOpen.value
}

function toggleRunSettings() {
  if (!bridgeReady.value || activeSession.value?.status === 'running') {
    return
  }
  composerAddMenuOpen.value = false
  bugPickerOpen.value = false
  notePickerOpen.value = false
  developmentPickerOpen.value = false
  accessModeOpen.value = false
  runSettingsOpen.value = !runSettingsOpen.value
  runSettingsModelListOpen.value = false
}

function selectRunModel(value: string) {
  selectModel(value)
  runSettingsModelListOpen.value = false
}

function updateEffortFromSlider(event: Event) {
  const index = Number((event.target as HTMLInputElement).value)
  const option = effortOptions.value[index]
  if (option) selectReasoningEffort(option.value)
}

function toggleAccessMode() {
  if (!bridgeReady.value || activeSession.value?.status === 'running') {
    return
  }
  composerAddMenuOpen.value = false
  runSettingsOpen.value = false
  runSettingsModelListOpen.value = false
  bugPickerOpen.value = false
  notePickerOpen.value = false
  developmentPickerOpen.value = false
  accessModeOpen.value = !accessModeOpen.value
}

function selectAccessMode(mode: CodexAccessMode) {
  const session = activeSession.value
  if (!session || session.status === 'running') {
    return
  }
  session.accessMode = mode
  codex.persist()
  accessModeOpen.value = false
}

function handleComposerAddAction(
  action:
    | 'files'
    | 'directory'
    | 'development'
    | 'bug'
    | 'note'
    | 'usage'
    | 'tasks',
) {
  composerAddMenuOpen.value = false
  if (action === 'files') {
    openAttachmentPicker()
  } else if (action === 'directory') {
    if (activeSession.value?.status === 'running') {
      emit('notify', '当前任务完成后才能切换工作目录')
      return
    }
    void chooseWorkingDirectory()
  } else if (action === 'development') {
    toggleDevelopmentPicker()
  } else if (action === 'bug') {
    toggleBugPicker()
  } else if (action === 'note') {
    toggleNotePicker()
  } else if (action === 'usage') {
    openTokenUsage()
  } else {
    taskPanelOpen.value = true
  }
}

function handleAttachmentInput(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  void addAttachments(files)
}

function handleComposerPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (files.length === 0) return
  event.preventDefault()
  void addAttachments(files)
}

function handleAttachmentDragEnter(event: DragEvent) {
  if (!bridgeReady.value) return
  event.preventDefault()
  attachmentDragActive.value = true
}

function handleAttachmentDragOver(event: DragEvent) {
  if (!bridgeReady.value) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}

function handleAttachmentDrop(event: DragEvent) {
  attachmentDragActive.value = false
  if (!bridgeReady.value) return
  event.preventDefault()
  void addAttachments(Array.from(event.dataTransfer?.files ?? []))
}

async function addAttachments(files: File[]) {
  if (files.length === 0) return
  const slots = MAX_ATTACHMENTS - pendingAttachments.value.length
  if (slots <= 0) {
    emit('notify', `一次最多发送 ${MAX_ATTACHMENTS} 个附件`)
    return
  }
  const accepted = files.slice(0, slots)
  if (files.length > slots) {
    emit('notify', `一次最多发送 ${MAX_ATTACHMENTS} 个附件`)
  }

  for (const file of accepted) {
    if (file.size <= 0) {
      emit('notify', `「${file.name}」是空文件，已跳过`)
      continue
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      emit('notify', `「${file.name}」超过 20MB，已跳过`)
      continue
    }
    const image = file.type.startsWith('image/')
    const inputModalities =
      selectedModelInfo.value?.inputModalities ?? ['text', 'image']
    if (
      image &&
      selectedModelInfo.value &&
      !inputModalities.includes('image')
    ) {
      emit('notify', `当前模型不支持图片附件：「${file.name}」`)
      continue
    }
    const pending: PendingAttachment = {
      localId: window.crypto.randomUUID(),
      id: '',
      name: file.name || (image ? '粘贴的图片.png' : '附件'),
      contentType: file.type || 'application/octet-stream',
      size: file.size,
      kind: image ? 'image' : 'file',
      previewUrl: image ? URL.createObjectURL(file) : '',
      uploading: true,
      error: '',
    }
    pendingAttachments.value = [
      ...pendingAttachments.value,
      pending,
    ]
    try {
      const uploaded = await uploadCodexAttachment(file)
      pendingAttachments.value = pendingAttachments.value.map(
        (attachment) =>
          attachment.localId === pending.localId
            ? {
                ...attachment,
                ...uploaded,
                uploading: false,
              }
            : attachment,
      )
    } catch (error) {
      pendingAttachments.value = pendingAttachments.value.map(
        (attachment) =>
          attachment.localId === pending.localId
            ? {
                ...attachment,
                uploading: false,
                error:
                  error instanceof Error
                    ? error.message
                    : '附件上传失败',
              }
            : attachment,
      )
      emit(
        'notify',
        `「${pending.name}」上传失败：${
          error instanceof Error ? error.message : '请重试'
        }`,
      )
    }
  }
}

function removeAttachment(localId: string) {
  const attachment = pendingAttachments.value.find(
    (item) => item.localId === localId,
  )
  if (attachment?.previewUrl) {
    URL.revokeObjectURL(attachment.previewUrl)
  }
  pendingAttachments.value = pendingAttachments.value.filter(
    (item) => item.localId !== localId,
  )
}

function clearPendingAttachments() {
  for (const attachment of pendingAttachments.value) {
    if (attachment.previewUrl) {
      URL.revokeObjectURL(attachment.previewUrl)
    }
  }
  pendingAttachments.value = []
  attachmentDragActive.value = false
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object'
    ? (value as Record<string, unknown>)
    : {}
}

function asText(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
}

function asNumber(value: unknown, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value)
    ? value
    : fallback
}

function eventTime(value: unknown) {
  return new Date(asNumber(value, Date.now())).toISOString()
}

function ensureTaskTimeline(task: CodexTask) {
  if (!Array.isArray(task.timeline)) task.timeline = []
  return task.timeline
}

function ensureTimelineMessage(
  task: CodexTask,
  itemId: string,
  startedAt: string,
) {
  const timeline = ensureTaskTimeline(task)
  const normalizedItemId =
    itemId || task.responseItemId || window.crypto.randomUUID()
  if (
    task.response.trim() &&
    !timeline.some((entry) => entry.kind === 'message')
  ) {
    const legacyItemId =
      task.responseItemId || `legacy-${task.id}`
    timeline.push({
      id: `message:${legacyItemId}`,
      kind: 'message',
      itemId: legacyItemId,
      text: task.response,
      streaming:
        task.responseStreaming && legacyItemId === normalizedItemId,
      startedAt: task.lastActivityAt || task.startedAt,
      completedAt:
        legacyItemId === normalizedItemId &&
        task.responseStreaming
          ? null
          : task.lastActivityAt || task.startedAt,
    })
  }
  let message = timeline.find(
    (entry): entry is CodexMessageTimelineEntry =>
      entry.kind === 'message' &&
      entry.itemId === normalizedItemId,
  )
  if (!message) {
    message = {
      id: `message:${normalizedItemId}`,
      kind: 'message',
      itemId: normalizedItemId,
      text: '',
      streaming: true,
      startedAt,
      completedAt: null,
    }
    timeline.push(message)
  }
  return message
}

function syncTaskResponseFromTimeline(task: CodexTask) {
  const messages = (task.timeline ?? []).filter(
    (entry): entry is CodexMessageTimelineEntry =>
      entry.kind === 'message' && Boolean(entry.text),
  )
  if (messages.length > 0) {
    task.response = messages.map((entry) => entry.text).join('\n\n')
  }
}

function stopTimelineStreaming(task: CodexTask, completedAt: string) {
  for (const entry of task.timeline ?? []) {
    if (entry.kind !== 'message' || !entry.streaming) continue
    entry.streaming = false
    entry.completedAt ??= completedAt
  }
}

function appendActivityTimeline(task: CodexTask, activity: CodexActivity) {
  const timeline = ensureTaskTimeline(task)
  if (
    timeline.some(
      (entry) =>
        entry.kind === 'activity' && entry.activityId === activity.id,
    )
  ) {
    return
  }
  const entry: CodexTimelineEntry = {
    id: `activity:${activity.id}`,
    kind: 'activity',
    activityId: activity.id,
  }
  timeline.push(entry)
}

function taskForEvent(
  session: CodexSession,
  turnId: string,
): CodexTask | undefined {
  const byTurn = turnId
    ? session.tasks.find((task) => task.turnId === turnId)
    : undefined
  return (
    byTurn ??
    session.tasks.find((task) => task.id === session.activeTaskId)
  )
}

function activityStatus(status: unknown) {
  if (status === 'failed' || status === 'declined') return status
  if (status === 'inProgress') return 'running' as const
  return 'completed' as const
}

function activityType(itemType: string): CodexActivityType {
  if (itemType === 'commandExecution') return 'command'
  if (itemType === 'fileChange') return 'fileChange'
  if (itemType === 'mcpToolCall' || itemType === 'dynamicToolCall') {
    return 'tool'
  }
  if (itemType === 'plan') return 'plan'
  if (itemType === 'webSearch') return 'webSearch'
  if (itemType === 'reasoning') return 'reasoning'
  return 'other'
}

function pathBasename(value: string) {
  return value.split(/[\\/]/).filter(Boolean).at(-1) || value
}

function sanitizedWebsiteUrl(value: string) {
  try {
    const url = new URL(value)
    url.username = ''
    url.password = ''
    url.search = ''
    url.hash = ''
    return url.toString()
  } catch {
    return value.replace(/[?#].*$/, '')
  }
}

function diffStats(diff: string) {
  let additions = 0
  let deletions = 0
  for (const line of diff.split(/\r?\n/)) {
    if (line.startsWith('+') && !line.startsWith('+++')) additions += 1
    if (line.startsWith('-') && !line.startsWith('---')) deletions += 1
  }
  return { additions, deletions }
}

function unwrapShellCommand(value: string) {
  let command = value.trim()
  const shellWrapper = command.match(
    /^(?:\/(?:usr\/)?bin\/)?(?:zsh|bash|sh)\s+-l?c\s+([\s\S]+)$/,
  )
  if (shellWrapper?.[1]) command = shellWrapper[1].trim()
  if (
    (command.startsWith("'") && command.endsWith("'")) ||
    (command.startsWith('"') && command.endsWith('"'))
  ) {
    command = command.slice(1, -1)
  }
  return command.replace(/^\/(?:usr\/)?bin\//, '').trim()
}

function displayCommand(value: string) {
  const command = unwrapShellCommand(value)
    .replace(/\s+/g, ' ')
    .trim()
  return command || '命令'
}

function activityTitle(
  type: CodexActivityType,
  item: Record<string, unknown>,
) {
  if (type === 'command') {
    return '已运行'
  }
  if (type === 'fileChange') {
    const changes = Array.isArray(item.changes) ? item.changes : []
    if (changes.length === 1) {
      const change = asRecord(changes[0])
      const path = asText(change.path)
      const stats = diffStats(asText(change.diff))
      const statText =
        stats.additions > 0 || stats.deletions > 0
          ? ` +${stats.additions} -${stats.deletions}`
          : ''
      return path
        ? `已编辑 ${pathBasename(path)}${statText}`
        : '已编辑 1 个文件'
    }
    return `已编辑 ${changes.length || 1} 个文件`
  }
  if (type === 'tool') {
    const tool = [asText(item.server), asText(item.tool)]
      .filter(Boolean)
      .join(' / ')
      .toLocaleLowerCase('en-US')
    if (
      /(read|view|open|list|search).*(file|folder|directory|path)|(?:cat|find|rg|grep)/.test(
        tool,
      )
    ) {
      return '已查看文件'
    }
    if (
      /(write|edit|patch|replace).*(file|folder|directory|path)/.test(
        tool,
      )
    ) {
      return '已编辑文件'
    }
    return '已运行工具'
  }
  if (type === 'webSearch') return '已搜索网页'
  if (type === 'reasoning') return '分析推理'
  if (type === 'plan') return '任务计划'
  return '执行记录'
}

function activityDetail(
  type: CodexActivityType,
  item: Record<string, unknown>,
) {
  if (type === 'command') {
    const command = asText(item.command, '执行命令')
    const cwd = asText(item.cwd)
    return cwd ? `${command}\n\n工作目录：${cwd}` : command
  }
  if (type === 'fileChange') {
    const changes = Array.isArray(item.changes) ? item.changes : []
    return changes
      .map((change) => asText(asRecord(change).path))
      .filter(Boolean)
      .join('\n')
  }
  if (type === 'reasoning') {
    const summary = Array.isArray(item.summary) ? item.summary : []
    return summary.map((entry) => asText(entry)).filter(Boolean).join('\n')
  }
  if (type === 'tool') {
    const toolName = [asText(item.server), asText(item.tool)]
      .filter(Boolean)
      .join(' / ')
    const input = item.arguments ?? item.input ?? item.parameters
    const inputText =
      typeof input === 'string'
        ? input
        : input && typeof input === 'object'
          ? JSON.stringify(input)
          : ''
    return [toolName, inputText].filter(Boolean).join('\n')
  }
  if (type === 'webSearch') return asText(item.query)
  return ''
}

function toolActivityInput(detail: string) {
  const [name = '', ...inputLines] = detail.split(/\r?\n/)
  const raw = inputLines.join('\n').trim()
  let input: Record<string, unknown> = {}
  if (raw) {
    try {
      input = asRecord(JSON.parse(raw))
    } catch {
      input = { query: raw }
    }
  }
  return { name, input }
}

function firstInputText(
  input: Record<string, unknown>,
  keys: string[],
) {
  for (const key of keys) {
    const value = input[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function activityCommandSource(activity: CodexActivity) {
  const detail = activity.detail.split(/\n{1,2}工作目录：/)[0]?.trim()
  return activity.title === '已运行' && detail
    ? detail
    : activity.title
}

function activityLabel(activity: CodexActivity) {
  if (activity.type === 'command') {
    return `已运行 ${displayCommand(activityCommandSource(activity))}`
  }
  if (activity.type === 'fileChange') {
    const firstPath = activity.detail.split(/\r?\n/)[0]?.trim()
    if (
      firstPath &&
      /个文件|文件变更/.test(activity.title)
    ) {
      return `已编辑 ${pathBasename(firstPath)}`
    }
    const count = activity.detail
      .split(/\r?\n/)
      .filter((path) => path.trim()).length
    return count > 1
      ? `已编辑 ${count} 个文件`
      : activity.title || '已编辑文件'
  }
  if (activity.type === 'tool') {
    const { name, input } = toolActivityInput(activity.detail)
    const query = firstInputText(input, [
      'query',
      'pattern',
      'searchTerm',
      'search',
      'regex',
    ])
    const path = firstInputText(input, [
      'path',
      'file',
      'filePath',
      'directory',
      'cwd',
    ])
    if (query) {
      return path
        ? `已在 ${pathBasename(path)} 中搜索“${query}”`
        : `已搜索“${query}”`
    }
    if (path && /查看|读取|read|view|open/i.test(activity.title)) {
      return `已查看 ${pathBasename(path)}`
    }
    if (path && /编辑|修改|write|edit|patch/i.test(activity.title)) {
      return `已编辑 ${pathBasename(path)}`
    }
    if (/查看|读取|read|view|open|list|search/i.test(activity.title)) {
      return '已查看文件'
    }
    if (/编辑|修改|write|edit|patch|replace/i.test(activity.title)) {
      return '已编辑文件'
    }
    return name ? `已运行 ${pathBasename(name)}` : '已运行工具'
  }
  if (activity.type === 'webSearch') {
    const query = activity.detail.trim()
    return query ? `已搜索“${query}”` : '已搜索网页'
  }
  return activity.title || '执行记录'
}

function activityPanelLabel(activity: CodexActivity) {
  if (activity.type === 'command') return 'Shell'
  if (activity.type === 'fileChange') return '文件'
  if (activity.type === 'tool') return '工具'
  if (activity.type === 'webSearch') return '搜索'
  return '详情'
}

function activityPanelBody(activity: CodexActivity) {
  const parts: string[] = []
  if (activity.type === 'command') {
    const command = unwrapShellCommand(activityCommandSource(activity))
    if (command && command !== '执行命令') {
      parts.push(`$ ${command}`)
    }
  } else if (activity.detail.trim()) {
    parts.push(activity.detail.trim())
  }
  if (activity.output.trim()) parts.push(activity.output.trim())
  return parts.join('\n\n')
}

function ensureActivity(
  task: CodexTask,
  item: Record<string, unknown>,
  itemType: string,
  startedAt: string,
) {
  const type = activityType(itemType)
  if (
    !['command', 'fileChange', 'tool', 'webSearch', 'plan', 'reasoning'].includes(
      type,
    )
  ) {
    return null
  }

  const id = asText(item.id)
  if (!id) return null
  let activity = task.activities.find((entry) => entry.id === id)
  if (!activity) {
    activity = {
      id,
      type,
      title: activityTitle(type, item),
      detail: activityDetail(type, item),
      status: activityStatus(item.status),
      startedAt,
      completedAt: null,
      output: '',
    }
    task.activities.push(activity)
    if (type === 'command') task.commandCount += 1
    if (type === 'fileChange') task.fileChangeCount += 1
    if (type === 'tool') task.toolCount += 1
  }
  appendActivityTimeline(task, activity)
  return activity
}

function schedulePersist() {
  if (persistTimer) window.clearTimeout(persistTimer)
  persistTimer = window.setTimeout(() => {
    codex.persist()
    persistTimer = null
  }, 350)
}

function markTaskActivity(task: CodexTask, emittedAtMs?: unknown) {
  task.lastActivityAt = new Date(
    asNumber(emittedAtMs, Date.now()),
  ).toISOString()
}

function markRunningTasksInterrupted(message: string) {
  let changed = false
  const interruptedTestTasks: CodexTask[] = []
  for (const session of codex.sessions) {
    let sessionChanged = false
    for (const task of session.tasks) {
      if (task.status !== 'running') continue
      const completedAt = new Date().toISOString()
      task.status = 'interrupted'
      task.responseStreaming = false
      stopTimelineStreaming(task, completedAt)
      task.completedAt = completedAt
      task.lastActivityAt = task.completedAt
      changed = true
      sessionChanged = true
      if (task.zentaoTestBugId) {
        interruptedTestTasks.push(task)
      }
    }
    if (sessionChanged) {
      session.status = 'idle'
      session.activeTaskId = ''
    }
  }
  if (changed) {
    codex.persist()
    interruptedTestTasks.forEach((task) => {
      scheduleZentaoTestResultUpdate(task)
    })
    emit('notify', message)
  }
  return changed
}

function handleCodexEvent(event: CodexGatewayEvent) {
  if (event.type === 'bridge') {
    if (event.method === 'appServer/exit') {
      const message = asText(
        asRecord(event.params).message,
        'Codex app-server 已断开',
      )
      bridgeConnected.value = false
      interruptingTaskId.value = ''
      markRunningTasksInterrupted(message)
    }
    return
  }

  if (event.type === 'serverRequest') {
    emit(
      'notify',
      `Codex 请求需要处理：${event.method}`,
    )
    return
  }

  const params = asRecord(event.params)
  const threadId = asText(params.threadId)
  if (!threadId) return
  const session = codex.findByThreadId(threadId)
  if (!session) return
  const eventTurnId =
    asText(params.turnId) || asText(asRecord(params.turn).id)
  const eventTask = taskForEvent(session, eventTurnId)
  if (eventTask?.status === 'running') {
    markTaskActivity(eventTask, event.emittedAtMs)
  }

  if (event.method === 'turn/started') {
    const turn = asRecord(params.turn)
    const task = taskForEvent(session, asText(turn.id))
    if (task) {
      task.turnId = asText(turn.id, task.turnId)
      task.status = 'running'
      markTaskActivity(task, event.emittedAtMs)
    }
    session.status = 'running'
    schedulePersist()
    return
  }

  if (event.method === 'item/started') {
    const turnId = asText(params.turnId)
    const task = taskForEvent(session, turnId)
    if (!task) return
    if (!task.turnId) task.turnId = turnId
    const item = asRecord(params.item)
    const itemType = asText(item.type)
    task.itemCount += 1
    markTaskActivity(task, event.emittedAtMs)

    if (itemType === 'agentMessage') {
      const itemId = asText(item.id)
      const startedAt = eventTime(
        asNumber(params.startedAtMs, Date.now()),
      )
      const message = ensureTimelineMessage(task, itemId, startedAt)
      if (!message.text) message.text = asText(item.text)
      message.streaming = true
      task.responseItemId = message.itemId
      task.responseStreaming = true
      syncTaskResponseFromTimeline(task)
    } else {
      ensureActivity(
        task,
        item,
        itemType,
        new Date(asNumber(params.startedAtMs, Date.now())).toISOString(),
      )
    }
    schedulePersist()
    return
  }

  if (event.method === 'item/agentMessage/delta') {
    const task = taskForEvent(session, asText(params.turnId))
    if (!task) return
    const message = ensureTimelineMessage(
      task,
      asText(params.itemId, task.responseItemId),
      eventTime(event.emittedAtMs),
    )
    message.text += asText(params.delta)
    message.streaming = true
    task.responseItemId = message.itemId
    task.responseStreaming = true
    syncTaskResponseFromTimeline(task)
    markTaskActivity(task, event.emittedAtMs)
    return
  }

  if (event.method === 'item/commandExecution/outputDelta') {
    const task = taskForEvent(session, asText(params.turnId))
    if (task) markTaskActivity(task, event.emittedAtMs)
    const activity = task?.activities.find(
      (entry) => entry.id === asText(params.itemId),
    )
    if (activity) {
      activity.output = `${activity.output}${asText(params.delta)}`.slice(
        -4000,
      )
    }
    return
  }

  if (event.method === 'item/completed') {
    const turnId = asText(params.turnId)
    const task = taskForEvent(session, turnId)
    if (!task) return
    if (!task.turnId) task.turnId = turnId
    const item = asRecord(params.item)
    const itemType = asText(item.type)
    const itemId = asText(item.id)
    markTaskActivity(task, event.emittedAtMs)

    if (itemType === 'agentMessage') {
      const completedText = asText(item.text)
      const completedAt = eventTime(
        asNumber(params.completedAtMs, Date.now()),
      )
      const message = ensureTimelineMessage(task, itemId, completedAt)
      if (completedText) message.text = completedText
      message.streaming = false
      message.completedAt = completedAt
      task.responseItemId = message.itemId
      task.responseStreaming = false
      syncTaskResponseFromTimeline(task)
      if (
        task.status === 'completed' &&
        task.zentaoPushError === NO_RESPONSE_UPDATE_ERROR
      ) {
        task.zentaoPushError = ''
      }
    } else {
      const activity = ensureActivity(
        task,
        item,
        itemType,
        new Date(Date.now()).toISOString(),
      )
      if (activity) {
        activity.status = activityStatus(item.status)
        activity.title = activityTitle(activity.type, item)
        activity.detail = activityDetail(activity.type, item) || activity.detail
        activity.output =
          asText(item.aggregatedOutput) || activity.output
        activity.completedAt = new Date(
          asNumber(params.completedAtMs, Date.now()),
        ).toISOString()
      }
    }
    schedulePersist()
    if (task.status === 'completed') {
      scheduleZentaoResolutionUpdate(task)
    }
    return
  }

  if (event.method === 'turn/plan/updated') {
    const task = taskForEvent(session, asText(params.turnId))
    if (!task) return
    markTaskActivity(task, event.emittedAtMs)
    const plan = Array.isArray(params.plan) ? params.plan : []
    task.plan = plan.map((entry) => {
      const step = asRecord(entry)
      const status = asText(step.status)
      return {
        step: asText(step.step),
        status:
          status === 'inProgress'
            ? 'inProgress'
            : status === 'completed'
              ? 'completed'
              : 'pending',
      }
    })
    schedulePersist()
    return
  }

  if (event.method === 'thread/tokenUsage/updated') {
    const task = taskForEvent(session, asText(params.turnId))
    if (!task) return
    markTaskActivity(task, event.emittedAtMs)
    const usage = asRecord(params.tokenUsage)
    const last = asRecord(usage.last)
    const inputTokens = asNumber(last.inputTokens)
    const outputTokens = asNumber(last.outputTokens)
    task.inputTokens = inputTokens
    task.outputTokens = outputTokens
    task.cachedInputTokens = asNumber(
      last.cachedInputTokens,
      asNumber(last.cacheReadTokens),
    )
    task.totalTokens = asNumber(
      last.totalTokens,
      inputTokens + outputTokens,
    )
    schedulePersist()
    return
  }

  if (event.method === 'turn/completed') {
    const turn = asRecord(params.turn)
    const task = taskForEvent(session, asText(turn.id))
    if (task) {
      task.turnId = asText(turn.id, task.turnId)
      const turnStatus = asText(turn.status)
      task.status =
        turnStatus === 'failed'
          ? 'failed'
          : turnStatus === 'interrupted'
            ? 'interrupted'
            : 'completed'
      task.completedAt =
        asNumber(turn.completedAt) > 0
          ? new Date(asNumber(turn.completedAt) * 1000).toISOString()
          : new Date().toISOString()
      task.durationMs =
        asNumber(turn.durationMs) || null
      task.responseStreaming = false
      stopTimelineStreaming(
        task,
        task.completedAt ?? new Date().toISOString(),
      )
      task.error = asText(asRecord(turn.error).message)
      markTaskActivity(task, event.emittedAtMs)
      if (task.status === 'failed' && task.error) {
        emit('notify', `Codex 任务失败：${task.error}`)
      }
    }
    advanceTaskQueue(
      session,
      task?.status === 'failed' ? 'error' : 'idle',
    )
    if (
      task?.status === 'completed' &&
      taskZentaoBugIds(task).length > 0
    ) {
      scheduleZentaoResolutionUpdate(task)
    }
    if (task?.zentaoTestBugId) {
      scheduleZentaoTestResultUpdate(task)
    }
    return
  }

  if (event.method === 'error') {
    const message = asText(
      asRecord(params.error).message,
      asText(params.message),
    )
    if (message) emit('notify', `Codex：${message}`)
  }
}

function renderMarkdown(value: string) {
  return DOMPurify.sanitize(marked.parse(value) as string)
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function formatDuration(value: number | null) {
  if (!value) return ''
  if (value < 1000) return `${value} ms`
  return `${(value / 1000).toFixed(value < 10_000 ? 1 : 0)} s`
}

function formatUsageCount(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatUsageTokens(value: number) {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(2)}B`
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(2)}M`
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}K`
  }
  return formatUsageCount(value)
}

function formatUsageCost(value: number | null) {
  if (value === null) return '--'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatUsageRate(value: number | null) {
  if (value === null) return '--'
  return `${value.toFixed(1)}%`
}

function formatUsageUpdated(value: string | undefined) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

async function refreshTokenUsage() {
  usageLoading.value = true
  usageError.value = ''
  try {
    usageSnapshot.value = await loadTokenUsage(
      usageRange.value,
      codex.sessions,
    )
  } catch (error) {
    usageError.value =
      error instanceof Error ? error.message : '无法读取 Token 用量'
  } finally {
    usageLoading.value = false
  }
}

function openTokenUsage() {
  usageOpen.value = true
  void refreshTokenUsage()
}

function activityIcon(activity: CodexActivity) {
  if (activity.type === 'command') return SquareTerminal
  if (activity.type === 'fileChange') return Pencil
  if (activity.type === 'webSearch') return Search
  if (activity.type === 'tool') {
    const signature = [
      activity.title,
      activity.detail,
      activityLabel(activity),
    ]
      .join(' ')
      .toLocaleLowerCase('en-US')
    if (
      /图像|图片|截图|image|screenshot|view_image/.test(signature)
    ) {
      return Images
    }
    if (/搜索|查找|search|query|regex|pattern/.test(signature)) {
      return FileSearch
    }
    if (
      /读取|查看|打开|read|open|view|\.(?:css|html|js|json|md|ts|txt|vue|ya?ml)\b/.test(
        signature,
      )
    ) {
      return FileText
    }
    return Wrench
  }
  if (activity.type === 'reasoning') return Bot
  return Wrench
}

function visibleActivities(task: CodexTask) {
  return task.activities.filter(
    (activity) =>
      activity.type === 'command' ||
      activity.type === 'fileChange' ||
      activity.type === 'tool' ||
      activity.type === 'webSearch',
  )
}

function taskTimeline(task: CodexTask): TaskTimelineRenderEntry[] {
  const activityById = new Map(
    visibleActivities(task).map((activity) => [activity.id, activity]),
  )
  const entries: TaskTimelineRenderEntry[] = []
  const appendActivity = (activity: CodexActivity) => {
    if (activity.type === 'command') {
      const previous = entries.at(-1)
      if (previous?.kind === 'commandGroup') {
        previous.activities.push(activity)
        return
      }
      entries.push({
        id: `commands:${activity.id}`,
        kind: 'commandGroup',
        activities: [activity],
      })
      return
    }
    entries.push({
      id: `activity:${activity.id}`,
      kind: 'activity',
      activity,
    })
  }

  for (const item of task.timeline ?? []) {
    if (item.kind === 'message') {
      if (!item.text && !item.streaming) continue
      entries.push({
        id: item.id,
        kind: 'message',
        text: item.text,
        streaming: item.streaming,
      })
      continue
    }
    const activity = activityById.get(item.activityId)
    if (activity) {
      appendActivity(activity)
    }
  }

  if (entries.length === 0) {
    for (const activity of activityById.values()) {
      appendActivity(activity)
    }
  }

  if (
    !entries.some((entry) => entry.kind === 'message') &&
    (task.response || task.responseStreaming)
  ) {
    entries.push({
      id: `legacy-response:${task.id}`,
      kind: 'message',
      text: task.response,
      streaming: task.responseStreaming,
    })
  }

  return entries
}

function statusIcon(task: CodexTask) {
  if (task.status === 'running') return LoaderCircle
  if (task.status === 'completed') return Check
  if (task.status === 'failed') return CircleAlert
  if (task.status === 'queued') return Clock3
  return Clock3
}

function timeValue(value: string | null | undefined) {
  const parsed = value ? new Date(value).getTime() : 0
  return Number.isFinite(parsed) ? parsed : 0
}

function taskLastActivityMs(task: CodexTask) {
  return Math.max(
    timeValue(task.lastActivityAt),
    timeValue(task.startedAt),
  )
}

function taskElapsedMs(task: CodexTask) {
  const end = task.completedAt
    ? timeValue(task.completedAt)
    : clockNow.value
  return Math.max(0, end - timeValue(task.startedAt))
}

function formatClockDuration(value: number) {
  const totalSeconds = Math.max(0, Math.floor(value / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) {
    return [
      hours,
      String(minutes).padStart(2, '0'),
      String(seconds).padStart(2, '0'),
    ].join(':')
  }
  return [
    String(minutes).padStart(2, '0'),
    String(seconds).padStart(2, '0'),
  ].join(':')
}

function taskElapsedLabel(task: CodexTask) {
  return formatClockDuration(taskElapsedMs(task))
}

function formatNaturalDuration(value: number) {
  const totalSeconds = Math.floor(Math.max(0, value) / 1000)
  if (totalSeconds < 1) return '不到 1 秒'
  if (totalSeconds < 60) return `${totalSeconds} 秒`

  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes < 60) {
    return seconds > 0 ? `${minutes} 分钟 ${seconds} 秒` : `${minutes} 分钟`
  }

  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  return remainingMinutes > 0
    ? `${hours} 小时 ${remainingMinutes} 分钟`
    : `${hours} 小时`
}

function taskTurnHeadline(task: CodexTask) {
  if (task.status === 'queued') return '排队中'
  const duration = formatNaturalDuration(
    task.durationMs || taskElapsedMs(task),
  )
  if (task.status === 'running') {
    return `${assistantPendingLabel(task)} ${duration}`
  }
  if (task.status === 'completed') return `已处理 ${duration}`
  if (task.status === 'failed') return `处理失败 ${duration}`
  return `已停止 ${duration}`
}

function activityElapsedLabel(activity: CodexActivity) {
  const end = activity.completedAt
    ? timeValue(activity.completedAt)
    : clockNow.value
  return formatClockDuration(
    Math.max(0, end - timeValue(activity.startedAt)),
  )
}

function taskActivityAgeMs(task: CodexTask) {
  return Math.max(0, clockNow.value - taskLastActivityMs(task))
}

function taskIsWaitingForResponse(task: CodexTask) {
  return (
    task.status === 'running' &&
    taskActivityAgeMs(task) >= TASK_WAITING_THRESHOLD_MS
  )
}

function taskActivityAgeLabel(task: CodexTask) {
  const seconds = Math.floor(taskActivityAgeMs(task) / 1000)
  if (seconds < 5) return '刚刚有活动'
  if (seconds < 60) return `${seconds} 秒前有活动`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前有活动`
  return `${Math.floor(minutes / 60)} 小时前有活动`
}

function statusLabel(task: CodexTask) {
  if (task.status === 'queued') return '排队中'
  if (task.status === 'running') {
    return taskIsWaitingForResponse(task) ? '暂无更新' : '执行中'
  }
  if (task.status === 'completed') return '已完成'
  if (task.status === 'failed') return '失败'
  return '已中断'
}

function hasRunningActivity(task: CodexTask) {
  return task.activities.some((activity) => activity.status === 'running')
}

function assistantPendingLabel(task: CodexTask) {
  if (taskIsWaitingForResponse(task)) return '等待 Codex 响应'
  if (task.responseStreaming) return '正在回复'
  return hasRunningActivity(task) ? '正在执行' : '正在思考'
}

function sessionPreview(session: CodexSession) {
  const task =
    [...session.tasks]
      .reverse()
      .find((item) => item.status === 'running') ??
    session.tasks.at(-1)
  const source =
    task?.displayPrompt ||
    task?.response ||
    task?.prompt ||
    ''
  const preview = source
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const title = session.title.replace(/\s+/g, ' ').trim()
  if (
    preview &&
    title &&
    (preview === title ||
      preview.startsWith(title) ||
      title.startsWith(preview))
  ) {
    return ''
  }
  return preview
}

function toggleSessionPinned(session: CodexSession) {
  codex.togglePinned(session.id)
}

function closeSessionContextMenu() {
  sessionContextMenuOpen.value = false
  contextMenuSession.value = null
}

function openSessionContextMenu(
  event: MouseEvent,
  session: CodexSession,
) {
  event.preventDefault()
  const margin = 8
  contextMenuSession.value = session
  contextMenuX.value = Math.max(
    margin,
    Math.min(
      event.clientX,
      window.innerWidth - SESSION_CONTEXT_MENU_WIDTH - margin,
    ),
  )
  contextMenuY.value = Math.max(
    margin,
    Math.min(
      event.clientY,
      window.innerHeight - SESSION_CONTEXT_MENU_HEIGHT - margin,
    ),
  )
  sessionContextMenuOpen.value = true
  void nextTick(() => {
    sessionContextMenu.value
      ?.querySelector<HTMLButtonElement>('[role="menuitem"]')
      ?.focus()
  })
}

function toggleContextMenuSessionPinned() {
  const session = contextMenuSession.value
  if (!session) return
  toggleSessionPinned(session)
  closeSessionContextMenu()
}

function renameContextMenuSession() {
  const session = contextMenuSession.value
  if (!session) return
  startSessionRename(session)
}

function startSessionRename(session: CodexSession) {
  closeSessionContextMenu()
  renamingSessionId.value = session.id
  renameDraft.value = session.title
  void nextTick(() => {
    const input = document.querySelector<HTMLInputElement>(
      '.codex-session-rename input',
    )
    input?.focus()
    input?.select()
  })
}

function cancelSessionRename() {
  renamingSessionId.value = ''
  renameDraft.value = ''
}

function commitSessionRename(session: CodexSession) {
  if (renamingSessionId.value !== session.id) return
  const title = renameDraft.value.replace(/\s+/g, ' ').trim().slice(0, 80)
  if (!title || title === session.title) {
    cancelSessionRename()
    return
  }
  session.title = title
  codex.touchSession(session)
  codex.persist()
  cancelSessionRename()
  if (session.threadId) {
    void setCodexThreadName(session.threadId, title).catch(() => {})
  }
  emit('notify', `会话已重命名为「${title}」`)
}

function deleteContextMenuSession() {
  const session = contextMenuSession.value
  if (!session) return
  closeSessionContextMenu()
  requestDeleteSession(session)
}

function toggleProjectGroup(key: string) {
  collapsedProjectKeys.value = collapsedProjectKeys.value.includes(key)
    ? collapsedProjectKeys.value.filter((item) => item !== key)
    : [...collapsedProjectKeys.value, key]
}

function defaultProjectNameFromPath(path: string) {
  return path.split(/[\\/]/).filter(Boolean).at(-1) || '新项目'
}

function resetProjectDraft() {
  projectName.value = ''
  projectSourceMode.value = 'folder'
  projectFolderPath.value = ''
  projectWorkDirectoryPath.value = ''
  projectError.value = ''
  choosingProjectFolder.value = false
}

function openCreateProject() {
  sessionBrandMenuOpen.value = false
  resetProjectDraft()
  createProjectOpen.value = true
}

function closeCreateProject() {
  if (choosingProjectFolder.value) return
  createProjectOpen.value = false
}

async function chooseProjectFolder() {
  if (choosingProjectFolder.value) return
  choosingProjectFolder.value = true
  projectError.value = ''
  try {
    const path = await chooseCodexDirectory()
    if (!path) return
    projectSourceMode.value = 'folder'
    projectFolderPath.value = path
    if (!projectName.value.trim()) {
      projectName.value = defaultProjectNameFromPath(path)
    }
  } catch (error) {
    projectError.value =
      error instanceof Error ? error.message : '无法选择项目文件夹'
  } finally {
    choosingProjectFolder.value = false
  }
}

function selectProjectWorkDirectory(directory: WorkDirectory) {
  projectSourceMode.value = 'workDirectory'
  projectWorkDirectoryPath.value = directory.path
  if (!projectName.value.trim()) {
    projectName.value = directory.name
  }
}

function submitCreateProject() {
  const name = projectName.value.trim()
  const path = selectedProjectPath.value.trim()
  projectError.value = ''
  if (!name) {
    projectError.value = '请输入项目名称'
    return
  }
  if ([...name].length > 40) {
    projectError.value = '项目名称不能超过 40 个字符'
    return
  }
  if (!path) {
    projectError.value =
      projectSourceMode.value === 'folder'
        ? '请选择项目文件夹'
        : '请选择工作目录'
    return
  }

  const session = codex.createSession(
    workspaces.activeWorkspaceId,
    path,
    name,
  )
  rememberPreferredWorkingDirectory(path)
  createProjectOpen.value = false
  sessionPanelOpen.value = false
  resetProjectDraft()
  void nextTick(() => composer.value?.focus())
  emit('notify', `已创建项目「${name}」`)
  codex.persist()
  return session
}

function createSession() {
  sessionBrandMenuOpen.value = false
  const existingDraft = workspaceSessions.value.find(
    (session) => session.tasks.length === 0,
  )
  if (existingDraft) {
    codex.selectSession(existingDraft.id)
    sessionPanelOpen.value = false
    sessionSearchOpen.value = false
    sessionSearchQuery.value = ''
    void nextTick(() => composer.value?.focus())
    return
  }
  const preferredDirectory = resolvePreferredWorkingDirectory()
  const cwd = activeSession.value?.cwd || preferredDirectory
  const session = codex.createSession(
    workspaces.activeWorkspaceId,
    cwd,
  )
  if (cwd) rememberPreferredWorkingDirectory(cwd)
  sessionPanelOpen.value = false
  sessionSearchOpen.value = false
  sessionSearchQuery.value = ''
  void nextTick(() => composer.value?.focus())
}

function rememberPreferredWorkingDirectory(path: string) {
  savePreferredCodexDirectory(
    auth.email,
    workspaces.activeWorkspaceId,
    path,
  )
}

function resolvePreferredWorkingDirectory() {
  const saved = loadPreferredCodexDirectory(
    auth.email,
    workspaces.activeWorkspaceId,
  )
  if (saved) return saved

  const recentSessionWithDirectory = [...workspaceSessions.value]
    .sort(
      (a, b) =>
        new Date(b.updatedAt).getTime() -
        new Date(a.updatedAt).getTime(),
    )
    .find((session) => session.cwd.trim())
  const directory = recentSessionWithDirectory?.cwd.trim() ?? ''
  if (directory) rememberPreferredWorkingDirectory(directory)
  return directory
}

function ensureWorkspaceSession() {
  if (workspaceSessions.value.length === 0) {
    if (codex.activeSessionId) codex.clearActiveSession()
    const preferredDirectory = resolvePreferredWorkingDirectory()
    codex.createSession(
      workspaces.activeWorkspaceId,
      preferredDirectory,
    )
    return
  }
  if (
    !workspaceSessions.value.some(
      (session) => session.id === codex.activeSessionId,
    )
  ) {
    codex.selectSession(workspaceSessions.value[0].id)
  }

}

function selectSession(id: string) {
  codex.selectSession(id)
  sessionPanelOpen.value = false
}

function requestDeleteSession(session: CodexSession) {
  if (session.status === 'running') {
    emit('notify', '会话正在执行任务，停止后才能删除')
    return
  }
  deleteSessionTarget.value = session
}

function confirmDeleteSession() {
  const session = deleteSessionTarget.value
  if (!session) return
  if (session.status === 'running') {
    emit('notify', '会话正在执行任务，停止后才能删除')
    deleteSessionTarget.value = null
    return
  }

  if (codex.activeSessionId === session.id) {
    const next = workspaceSessions.value.find(
      (item) => item.id !== session.id,
    )
    codex.selectSession(next?.id ?? '')
  }
  codex.removeSession(session.id)
  deleteSessionTarget.value = null
  emit('notify', `会话「${session.title}」已删除`)
}


async function chooseWorkingDirectory() {
  const session = activeSession.value
  if (!session || session.status === 'running') return
  try {
    const selected = await chooseCodexDirectory()
    if (!selected) return
    session.cwd = selected
    rememberPreferredWorkingDirectory(selected)
    codex.touchSession(session)
    codex.persist()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法选择项目目录',
    )
  }
}

function clearWorkingDirectory() {
  const session = activeSession.value
  if (!session || session.status === 'running') return
  session.cwd = ''
  rememberPreferredWorkingDirectory('')
  codex.touchSession(session)
  codex.persist()
  emit('notify', '已切换为不在项目中工作')
}

function handleDirectoryPointerDown(event: PointerEvent) {
  if (
    sessionContextMenuOpen.value &&
    event.target instanceof Node &&
    !sessionContextMenu.value?.contains(event.target)
  ) {
    closeSessionContextMenu()
  }
  if (
    accessModeOpen.value &&
    event.target instanceof Node &&
    !accessMode.value?.contains(event.target) &&
    !accessModePopover.value?.contains(event.target)
  ) {
    accessModeOpen.value = false
  }
  if (
    runSettingsOpen.value &&
    event.target instanceof Node &&
    !runSettings.value?.contains(event.target) &&
    !runSettingsPopover.value?.contains(event.target)
  ) {
    runSettingsOpen.value = false
    runSettingsModelListOpen.value = false
  }
  if (
    composerAddMenuOpen.value &&
    event.target instanceof Node &&
    !composerAddMenu.value?.contains(event.target)
  ) {
    composerAddMenuOpen.value = false
  }
  if (
    sessionBrandMenuOpen.value &&
    event.target instanceof Node &&
    !sessionBrandMenu.value?.contains(event.target)
  ) {
    sessionBrandMenuOpen.value = false
  }
  if (
    bugPickerOpen.value &&
    event.target instanceof Node &&
    !bugPicker.value?.contains(event.target)
  ) {
    bugPickerOpen.value = false
  }
  if (
    notePickerOpen.value &&
    event.target instanceof Node &&
    !notePicker.value?.contains(event.target)
  ) {
    notePickerOpen.value = false
  }
  if (
    developmentPickerOpen.value &&
    event.target instanceof Node &&
    !developmentPicker.value?.contains(event.target)
  ) {
    developmentPickerOpen.value = false
  }
}

function handleDirectoryKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  closeSessionContextMenu()
  bugPickerOpen.value = false
  notePickerOpen.value = false
  developmentPickerOpen.value = false
  sessionBrandMenuOpen.value = false
  composerAddMenuOpen.value = false
  runSettingsOpen.value = false
  runSettingsModelListOpen.value = false
  accessModeOpen.value = false
}

function toggleBugPicker() {
  if (bugPickerOpen.value) {
    bugPickerOpen.value = false
    return
  }
  if (composerBugIds.value.length > 1) {
    composerBugIds.value = []
  }
  composerAddMenuOpen.value = false
  runSettingsOpen.value = false
  runSettingsModelListOpen.value = false
  accessModeOpen.value = false
  notePickerOpen.value = false
  bugPickerOpen.value = true
  bugSearchQuery.value = ''
  void zentao.load(
    auth.email,
    workspaces.activeWorkspaceId,
    Boolean(zentao.error),
  )
  void nextTick(() => {
    bugPicker.value
      ?.querySelector<HTMLInputElement>('input[type="search"]')
      ?.focus()
  })
}

function notePreview(note: WorkNote) {
  return note.content.replace(/\s+/g, ' ').trim().slice(0, 140)
}

function taskNoteExists(task: CodexTask) {
  const marker = `任务 ID：${task.id}`
  return noteStore.notes.some((note) => note.content.includes(marker))
}

function taskNoteTitle(task: CodexTask) {
  const source = (task.displayPrompt || task.prompt)
    .split('\n')
    .map((line) => line.replace(/^#+\s*/, '').trim())
    .find(Boolean)
  return `待验证：${source || 'Codex 对话任务'}`.slice(0, 80)
}

function addTaskToNotes(task: CodexTask) {
  if (!auth.email || !activeSession.value) {
    emit('notify', '工作空间尚未初始化，暂时无法加入速记')
    return
  }
  if (taskNoteExists(task)) {
    emit('notify', '这个任务已经加入速记')
    return
  }

  const prompt = (task.displayPrompt || task.prompt).trim()
  const bugIds = taskZentaoBugIds(task)
  const remoteBugIds = bugIds.map(
    (bugId) =>
      zentao.bugs.find((bug) => bug.id === bugId)?.remoteId ??
      String(bugId),
  )
  const content = [
    '## 待验证任务',
    '验证状态：尚未验证此任务是否完成或解决。',
    '',
    '### 对话问题',
    prompt.slice(0, 12_000),
    '',
    '### 来源',
    `任务 ID：${task.id}`,
    `会话：${activeSession.value.title || '未命名会话'}`,
    `发起时间：${new Date(task.startedAt).toLocaleString('zh-CN')}`,
    `工作目录：${activeSession.value.cwd || '未设置'}`,
    remoteBugIds.length > 0
      ? `关联 Bug：${remoteBugIds.map((id) => `#${id}`).join('、')}`
      : '',
    task.attachments?.length
      ? `附件：${task.attachments.map((attachment) => attachment.name).join('、')}`
      : '',
  ]
    .filter(Boolean)
    .join('\n')

  try {
    const note = noteStore.save({
      workspaceId: workspaces.activeWorkspaceId,
      title: taskNoteTitle(task),
      content,
      important: true,
      resolved: false,
      pinned: false,
      archived: false,
      tags: ['待验证', 'Codex'],
    })
    emit(
      'notify',
      note
        ? `已加入速记：${note.title}`
        : '任务内容为空，无法加入速记',
    )
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '加入速记失败',
    )
  }
}

function noteUpdatedLabel(note: WorkNote) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(note.updatedAt))
}

function toggleNotePicker() {
  if (notePickerOpen.value) {
    notePickerOpen.value = false
    return
  }
  composerAddMenuOpen.value = false
  bugPickerOpen.value = false
  runSettingsOpen.value = false
  runSettingsModelListOpen.value = false
  accessModeOpen.value = false
  notePickerOpen.value = true
  noteSearchQuery.value = ''
  noteStore.load(auth.email)
  void nextTick(() => {
    notePicker.value
      ?.querySelector<HTMLInputElement>('input[type="search"]')
      ?.focus()
  })
}

function selectNoteForTask(note: WorkNote) {
  selectedNoteId.value = note.id
  notePickerOpen.value = false
  void nextTick(() => composer.value?.focus())
}

function clearSelectedNote() {
  selectedNoteId.value = ''
  notePickerOpen.value = false
  void nextTick(() => composer.value?.focus())
}

function developmentResourceKindLabel(kind: DevelopmentResourceKind) {
  return (
    developmentResourceKindItems.find((item) => item.value === kind)
      ?.label ?? '资源'
  )
}

function selectDevelopmentResourceKind(kind: DevelopmentResourceKind) {
  developmentResourceKind.value = kind
  developmentSearchQuery.value = ''
}

function developmentResourceIcon(kind: DevelopmentResourceKind) {
  if (kind === 'git') return GitBranch
  if (kind === 'database') return Database
  if (kind === 'deployment') return Rocket
  if (kind === 'server') return Server
  if (kind === 'website') return Globe2
  return AppWindow
}

function loadDevelopmentResources(force = false) {
  gitRepositoryStore.load(auth.email)
  deploymentStore.load(auth.email)
  shortcutStore.load(auth.email)
  developmentResourcesLoading.value = true
  developmentResourcesError.value = ''
  const tasks: Array<Promise<unknown>> = []
  if (workspaces.activeWorkspaceId) {
    tasks.push(
      databaseStore.load(
        workspaces.activeWorkspaceId,
        force,
      ),
    )
  }
  tasks.push(managedServerStore.load(force))
  void Promise.allSettled(tasks).then((results) => {
    developmentResourcesLoading.value = false
    const failed = results.find(
      (result) => result.status === 'rejected',
    )
    developmentResourcesError.value =
      failed?.status === 'rejected'
        ? failed.reason instanceof Error
          ? failed.reason.message
            : '部分资源读取失败'
        : ''
  })
}

function toggleDevelopmentPicker() {
  if (developmentPickerOpen.value) {
    developmentPickerOpen.value = false
    return
  }
  composerAddMenuOpen.value = false
  bugPickerOpen.value = false
  notePickerOpen.value = false
  runSettingsOpen.value = false
  runSettingsModelListOpen.value = false
  accessModeOpen.value = false
  developmentPickerOpen.value = true
  developmentSearchQuery.value = ''
  loadDevelopmentResources()
  void nextTick(() => {
    developmentPicker.value
      ?.querySelector<HTMLInputElement>('input[type="search"]')
      ?.focus()
  })
}

function developmentResourceSelected(resource: DevelopmentResourceItem) {
  return selectedDevelopmentResources.value.some(
    (item) => item.key === resource.key,
  )
}

function toggleDevelopmentResource(resource: DevelopmentResourceItem) {
  if (developmentResourceSelected(resource)) {
    selectedDevelopmentResources.value =
      selectedDevelopmentResources.value.filter(
        (item) => item.key !== resource.key,
      )
    return
  }
  if (
    selectedDevelopmentResources.value.length >=
    MAX_DEVELOPMENT_RESOURCES
  ) {
    emit(
      'notify',
      `一次最多关联 ${MAX_DEVELOPMENT_RESOURCES} 项资源`,
    )
    return
  }
  selectedDevelopmentResources.value = [
    ...selectedDevelopmentResources.value,
    resource,
  ]
  if (resource.kind === 'git') {
    const repositoryId = resource.key.replace(/^git:/, '')
    void gitRepositoryStore
      .refreshRepository(repositoryId)
      .then(() => {
        const refreshed = developmentResourceItems.value.find(
          (item) => item.key === resource.key,
        )
        if (!refreshed) return
        selectedDevelopmentResources.value =
          selectedDevelopmentResources.value.map((item) =>
            item.key === resource.key ? refreshed : item,
          )
      })
      .catch(() => {})
  }
}

function clearDevelopmentResources() {
  selectedDevelopmentResources.value = []
  developmentPickerOpen.value = false
  void nextTick(() => composer.value?.focus())
}

function selectBugForTask(bug: ZentaoBug) {
  composerBugIds.value = []
  composerTestBugId.value = null
  composerTestRunId.value = ''
  composerTestEvidenceDirectory.value = ''
  zentao.selectBug(bug.id)
  bugPickerOpen.value = false
  void nextTick(() => composer.value?.focus())
}

function clearSelectedBug() {
  composerBugIds.value = []
  composerTestBugId.value = null
  composerTestRunId.value = ''
  composerTestEvidenceDirectory.value = ''
  zentao.selectBug(0)
  bugPickerOpen.value = false
  void nextTick(() => composer.value?.focus())
}

function bugTaskPrompt(bugs: ZentaoBug[], userText: string) {
  if (bugs.length === 1) {
    const bug = bugs[0]
    return [
      '【关联禅道 Bug】',
      `Bug #${bug.remoteId}：${bug.title}`,
      `产品：${bug.productName}`,
      `严重程度：${bug.severity || '未设置'} · 优先级：P${bug.priority || '-'}`,
      `重现步骤：${bug.steps || '禅道未提供'}`,
      '',
      '【补充要求】',
      userText || '无',
      '',
      '【处理要求】',
      '1. 先阅读重现步骤和相关代码，确认问题触发条件，能复现时完成复现。',
      '2. 定位到具体代码、调用链或数据问题，说明根因，不要停留在表面现象。',
      '3. 完成代码修复，保持现有架构和代码风格，避免无关改动。',
      '4. 执行必要的测试、类型检查或构建验证，并检查相关回归风险。',
      '',
      '【Bug 单更新】',
      '不要只在回复中口头说明。任务成功后，平台会提取以下两段内容，直接更新对应禅道 Bug 单的解决方案和问题原因。',
      '请在最终回复末尾严格保留标题和顺序，否则 Bug 单将无法正确更新：',
      '解决方案：',
      '<具体修复内容、涉及模块和验证结果>',
      '定位原因：',
      '<可核验的根因，不要只写“已修复”或“逻辑问题”>',
      '',
    ].join('\n')
  }

  return [
    '【关联禅道 Bug 批次】',
    `本次一次性处理 ${bugs.length} 个 Bug：`,
    ...bugs.flatMap((bug, index) => [
      '',
      `--- Bug ${index + 1} ---`,
      `Bug #${bug.remoteId}：${bug.title}`,
      `项目：${bug.projectName || '未关联项目'}`,
      `产品：${bug.productName || '未知产品'}`,
      `严重程度：${bug.severity || '未设置'} · 优先级：P${bug.priority || '-'}`,
      `重现步骤：${bug.steps || '禅道未提供'}`,
    ]),
    '',
    '【补充要求】',
    userText || '无',
    '',
    '【处理要求】',
    '1. 按 Bug 的优先级、严重程度和关联关系逐项处理，能复现时完成复现。',
    '2. 每个 Bug 都要定位到具体代码、调用链或数据问题，明确根因。',
    '3. 完成代码修复和必要验证，同时检查相关回归风险。',
    '4. 保持现有架构与代码风格，避免与本次 Bug 无关的改动。',
    '5. 无法修复的 Bug 必须说明阻塞原因，不要虚构修复结果。',
    '',
    '【Bug 单更新】',
    '不要只在回复中口头说明。任务成功后，平台会把以下内容直接更新到对应禅道 Bug 单。',
    '请在最终回复中为每个 Bug 单独保留以下结构和标题，Bug 编号不可省略：',
    ...bugs.flatMap((bug) => [
      `【Bug #${bug.remoteId}】`,
      '解决方案：',
      '<该 Bug 的具体修复内容、涉及模块和验证结果>',
      '定位原因：',
      '<该 Bug 可核验的根因，不要只写“已修复”或“逻辑问题”>',
      '',
    ]),
  ].join('\n')
}

function bugTestPrompt(
  bug: ZentaoBug,
  evidenceDirectory: string,
  userText: string,
) {
  return [
    '【Codex 修复验证】',
    `Bug #${bug.remoteId}：${bug.title}`,
    `项目：${bug.projectName || '未关联项目'}`,
    `产品：${bug.productName || '未知产品'}`,
    `严重程度：${bug.severity || '未设置'} · 优先级：P${bug.priority || '-'}`,
    `重现步骤：${bug.steps || '禅道未提供'}`,
    '',
    '【测试目标】',
    '在研发环境验证该 Bug 是否已经修复。不要修改 Bug 状态，不要回写禅道，不要更新或发布本地客户端。',
    ...(userText ? [`补充要求：${userText}`] : []),
    '',
    '【测试要求】',
    '1. 阅读问题描述和相关实现，确认本次要验证的实际行为。',
    '2. 优先执行已有自动化测试；涉及页面时使用浏览器或 Playwright 验证真实交互。',
    '3. 至少生成一张能够证明验证结果的截图。页面问题必须截取修复后的实际页面状态；非页面问题可截取测试命令、测试报告或关键输出。',
    `4. 截图只能保存到以下受控目录：${evidenceDirectory}`,
    '5. 截图使用 png、jpg、jpeg 或 webp 格式，文件名直接写清楚场景，例如 bug-fixed.png。',
    '6. 不得伪造测试结果或截图。无法验证时必须明确写出阻塞原因。',
    '',
    '【输出格式】',
    '最终回复必须严格保留以下四段，标题不可修改：',
    '测试结论：',
    '<通过、失败或无法验证>',
    '测试环境：',
    '<必须说明研发环境、访问地址、操作系统、浏览器、分支或提交等实际测试环境>',
    '问题描述：',
    '<该 Bug 原问题的简要描述>',
    '修复证明：',
    '<可核验的测试步骤、实际结果和截图文件名，不要只写“已修复”>',
  ].join('\n')
}

function promptWithNoteContext(note: WorkNote, prompt: string) {
  return [
    '【关联速记】',
    `标题：${note.title || '未命名速记'}`,
    `标签：${note.tags.length > 0 ? note.tags.join('、') : '无'}`,
    '',
    '速记内容：',
    note.content.trim(),
    '',
    '【任务要求】',
    prompt,
  ].join('\n')
}

function promptWithDevelopmentResources(prompt: string) {
  if (selectedDevelopmentResources.value.length === 0) return prompt
  const sections = selectedDevelopmentResources.value.flatMap(
    (resource) => [
      '',
      `【${developmentResourceKindLabel(resource.kind)}】${resource.name}`,
      ...resource.promptLines,
      ...(currentAccessMode.value === 'full'
        ? resource.fullAccessPromptLines ?? []
        : []),
    ],
  )
  return [
    '【关联资源】',
    ...sections,
    '',
    '【任务要求】',
    prompt,
  ].join('\n')
}

function taskZentaoBugIds(task: CodexTask) {
  return [
    ...new Set([
      ...(task.zentaoBugIds ?? []),
      ...(task.zentaoBugId ? [task.zentaoBugId] : []),
    ]),
  ]
}

function scheduleZentaoTestResultUpdate(
  task: CodexTask,
  delay = 450,
) {
  if (
    !task.zentaoTestBugId ||
    !task.zentaoTestRunId ||
    task.zentaoTestResultSavedAt ||
    savingZentaoTestTaskIds.has(task.id) ||
    scheduledZentaoTestTimers.has(task.id)
  ) {
    return
  }
  const timer = window.setTimeout(() => {
    scheduledZentaoTestTimers.delete(task.id)
    void updateZentaoTestResult(task)
  }, delay)
  scheduledZentaoTestTimers.set(task.id, timer)
}

function restoreZentaoTestResults() {
  for (const session of codex.sessions) {
    for (const task of session.tasks) {
      if (
        task.zentaoTestBugId &&
        task.zentaoTestRunId &&
        !task.zentaoTestResultSavedAt &&
        task.status !== 'queued' &&
        task.status !== 'running'
      ) {
        scheduleZentaoTestResultUpdate(task, 0)
      }
    }
  }
}

async function updateZentaoTestResult(task: CodexTask) {
  const bugId = task.zentaoTestBugId
  const runId = task.zentaoTestRunId
  if (
    !bugId ||
    !runId ||
    task.zentaoTestResultSavedAt ||
    savingZentaoTestTaskIds.has(task.id)
  ) {
    return
  }
  savingZentaoTestTaskIds.add(task.id)
  try {
    const workspaceId =
      task.zentaoTestWorkspaceId || workspaces.activeWorkspaceId
    const sessionId =
      codex.sessions.find((session) =>
        session.tasks.some((item) => item.id === task.id),
      )?.id ??
      activeSession.value?.id ??
      ''
    let bug = zentao.bugs.find((item) => item.id === bugId)
    if (
      !bug &&
      auth.email &&
      workspaceId === workspaces.activeWorkspaceId
    ) {
      await zentao.load(
        auth.email,
        workspaceId,
        true,
      )
      bug = zentao.bugs.find((item) => item.id === bugId)
    }
    const evidence = await listZentaoTestEvidence(
      bugId,
      runId,
      workspaceId,
    ).catch(() => [])
    const parsed = extractZentaoTestResult(task.response)
    const completed = task.status === 'completed'
    const status =
      !completed
        ? ('failed' as const)
        : parsed.status === 'passed' && evidence.length === 0
          ? ('blocked' as const)
          : parsed.status
    const proofWithoutEvidence =
      parsed.proof.trim() ||
      task.response.trim() ||
      task.error.trim() ||
      'Codex 没有返回可用的验证结论'
    const proof =
      parsed.status === 'passed' && evidence.length === 0
        ? `${proofWithoutEvidence}\n\n本次测试没有生成截图，平台未将其判定为通过。`
        : proofWithoutEvidence
    const compactProof = proof.slice(0, 8_000)
    const result = {
      id: task.id,
      bugId,
      remoteId:
        task.zentaoTestRemoteId ||
        bug?.remoteId ||
        String(bugId),
      workspaceId,
      taskId: task.id,
      sessionId,
      runId,
      status,
      environment:
        (
          parsed.environment.trim() ||
          (completed
            ? 'Codex 未说明测试环境'
            : '研发环境（测试任务未完成）')
        ).slice(0, 2_000),
      problemDescription:
        (
          parsed.problemDescription.trim() ||
          bug?.title ||
          `Bug #${task.zentaoTestRemoteId || bugId}`
        ).slice(0, 4_000),
      proof: compactProof,
      screenshots: evidence.map((item) => item.filename),
      createdAt:
        task.completedAt ?? new Date().toISOString(),
    }
    zentaoTestStore.saveResult(result)
    task.zentaoTestResultSavedAt = new Date().toISOString()
    codex.persist()
    emit(
      'notify',
      `Bug #${result.remoteId} 的 Codex 测试结果已生成`,
    )
  } catch (error) {
    emit(
      'notify',
      error instanceof Error
        ? `保存 Codex 测试结果失败：${error.message}`
        : '保存 Codex 测试结果失败',
    )
  } finally {
    savingZentaoTestTaskIds.delete(task.id)
  }
}

function scheduleZentaoResolutionUpdate(
  task: CodexTask,
  delay = ZENTAO_SAVE_DELAY_MS,
) {
  if (
    task.status !== 'completed' ||
    taskZentaoBugIds(task).length === 0 ||
    task.zentaoPushedAt ||
    savingBugTaskIds.has(task.id) ||
    scheduledZentaoSaveTimers.has(task.id)
  ) {
    return
  }

  const timer = window.setTimeout(() => {
    scheduledZentaoSaveTimers.delete(task.id)
    if (task.status !== 'completed') return
    void updateZentaoResolution(task)
  }, delay)
  scheduledZentaoSaveTimers.set(task.id, timer)
}

async function updateZentaoResolution(task: CodexTask) {
  const bugIds = taskZentaoBugIds(task)
  if (bugIds.length === 0 || task.zentaoPushedAt) {
    return
  }
  if (savingBugTaskIds.has(task.id)) return
  if (!task.response.trim()) {
    task.zentaoPushError = NO_RESPONSE_UPDATE_ERROR
    codex.persist()
    return
  }

  savingBugTaskIds.add(task.id)
  const isBatch = bugIds.length > 1
  const failures: string[] = []
  const pushedBugIds = new Set(task.zentaoPushedBugIds ?? [])
  let updatedCount = 0

  try {
    task.zentaoPushError = ''
    if (
      auth.email &&
      workspaces.activeWorkspaceId &&
      bugIds.some(
        (bugId) => !zentao.bugs.some((bug) => bug.id === bugId),
      )
    ) {
      await zentao.load(
        auth.email,
        workspaces.activeWorkspaceId,
        true,
      )
    }

    for (const bugId of bugIds) {
      if (pushedBugIds.has(bugId)) continue
      const bug = zentao.bugs.find((item) => item.id === bugId)
      if (!bug) {
        failures.push(`未找到 Bug #${bugId}`)
        continue
      }

      const resolution = extractZentaoResolution(
        task.response,
        bug.remoteId,
        isBatch,
      )
      if (!resolution) {
        failures.push(`Bug #${bug.remoteId} 缺少独立回复`)
        continue
      }

      const solution = resolution.solution.trim().slice(0, 4000)
      const reason = resolution.reason.trim().slice(0, 4000)
      if (!solution) {
        failures.push(`Bug #${bug.remoteId} 缺少解决方案`)
        continue
      }
      if (!reason) {
        failures.push(`Bug #${bug.remoteId} 缺少问题原因`)
        continue
      }

      try {
        await zentao.resolveBug(bug.id, {
          resolution: 'fixed',
          solution,
          reason,
        })
        pushedBugIds.add(bug.id)
        task.zentaoPushedBugIds = [...pushedBugIds]
        updatedCount += 1
      } catch (error) {
        failures.push(
          `Bug #${bug.remoteId}：${
            error instanceof Error
              ? error.message
              : '更新 Bug 单失败'
          }`,
        )
      }
    }

    if (failures.length > 0) {
      task.zentaoPushError = failures.join('；').slice(0, 800)
      emit(
        'notify',
        `已更新 ${updatedCount} 个 Bug 单，${failures.length} 个需要重试`,
      )
    } else if (bugIds.every((bugId) => pushedBugIds.has(bugId))) {
      task.zentaoPushedAt = new Date().toISOString()
      task.zentaoPushError = ''
      emit(
        'notify',
        isBatch
          ? `${bugIds.length} 个 Bug 单的解决方案和问题原因已更新到禅道`
          : `Bug #${
              zentao.bugs.find((item) => item.id === bugIds[0])
                ?.remoteId ?? bugIds[0]
            } 的解决方案和问题原因已更新到禅道`,
      )
    }
  } catch (error) {
    task.zentaoPushError =
      error instanceof Error
        ? error.message
        : '更新禅道 Bug 单失败'
    emit(
      'notify',
      `更新禅道 Bug 单失败：${task.zentaoPushError}`,
    )
  } finally {
    savingBugTaskIds.delete(task.id)
    codex.persist()
  }
}

function failTask(task: CodexTask, session: CodexSession, error: unknown) {
  const message =
    error instanceof Error ? error.message : 'Codex 任务启动失败'
  task.status = 'failed'
  task.error = message
  task.completedAt = new Date().toISOString()
  stopTimelineStreaming(task, task.completedAt)
  task.lastActivityAt = task.completedAt
  task.responseStreaming = false
  if (task.zentaoTestBugId) {
    scheduleZentaoTestResultUpdate(task)
  }
  advanceTaskQueue(session, 'error')
  emit('notify', message)
}

function nextQueuedTask(session: CodexSession) {
  return session.tasks.find((task) => task.status === 'queued')
}

function advanceTaskQueue(
  session: CodexSession,
  fallbackStatus: 'idle' | 'error' = 'idle',
) {
  const next = nextQueuedTask(session)
  if (next && bridgeReady.value) {
    void executeTask(session, next)
    return true
  }
  session.status = fallbackStatus
  session.activeTaskId = ''
  codex.touchSession(session)
  codex.persist()
  return false
}

function resumeQueuedSessions() {
  for (const session of codex.sessions) {
    if (session.tasks.some((task) => task.status === 'running')) continue
    const next = nextQueuedTask(session)
    if (next) void executeTask(session, next)
  }
}

async function executeTask(
  session: CodexSession,
  task: CodexTask,
) {
  if (task.status !== 'queued' && task.status !== 'running') return

  const startedAt = new Date().toISOString()
  task.status = 'running'
  task.startedAt = startedAt
  task.lastActivityAt = startedAt
  task.completedAt = null
  task.durationMs = null
  task.error = ''
  session.status = 'running'
  session.activeTaskId = task.id
  codex.touchSession(session)
  codex.persist()
  if (session.id === activeSession.value?.id) {
    scrollToLatest(true)
  }

  try {
    let threadId = session.threadId
    const accessMode: CodexAccessMode =
      task.accessMode ??
      session.accessMode ??
      'workspace'
    const sandboxMode: CodexSandboxMode =
      accessMode === 'full' ? 'danger-full-access' : 'workspace-write'
    if (!threadId) {
      const started = await startCodexThread({
        cwd: session.cwd || undefined,
        sandbox: sandboxMode,
      })
      threadId = started.thread.id
      session.threadId = threadId
      resumedThreads.add(threadId)
      threadSandboxModes.set(threadId, sandboxMode)
      bridgeConnected.value = true
    } else {
      if (
        !resumedThreads.has(threadId) ||
        threadSandboxModes.get(threadId) !== sandboxMode
      ) {
        await resumeCodexThread(threadId, {
          cwd: session.cwd || undefined,
          sandbox: sandboxMode,
        })
        resumedThreads.add(threadId)
        threadSandboxModes.set(threadId, sandboxMode)
        bridgeConnected.value = true
      }
    }

    codex.touchSession(session)
    codex.persist()
    if (session.title !== '新会话') {
      void setCodexThreadName(threadId, session.title).catch(() => {})
    }

    const turn = await startCodexTurn({
      threadId,
      text: task.prompt,
      cwd: session.cwd || undefined,
      attachmentIds: (task.attachments ?? []).map(
        (attachment) => attachment.id,
      ),
      model: task.model || session.model || undefined,
      effort: task.reasoningEffort || session.reasoningEffort || undefined,
      writableRoots:
        accessMode === 'workspace' &&
        task.zentaoTestEvidenceDirectory
        ? [task.zentaoTestEvidenceDirectory]
        : undefined,
    })
    if (!task.turnId) task.turnId = turn.turn.id
    codex.persist()
  } catch (error) {
    failTask(task, session, error)
  }
}

function removeQueuedTask(task: CodexTask) {
  const session = activeSession.value
  if (!session || task.status !== 'queued') return
  session.tasks = session.tasks.filter((item) => item.id !== task.id)
  if (
    !session.tasks.some((item) => item.status === 'running') &&
    !session.tasks.some((item) => item.status === 'queued')
  ) {
    session.status = 'idle'
    session.activeTaskId = ''
  }
  codex.touchSession(session)
  codex.persist()
  emit('notify', '已取消排队任务')
}

async function sendMessage() {
  const session = activeSession.value
  const text = draft.value.trim()
  const attachments = pendingAttachments.value.filter(
    (attachment) =>
      attachment.id && !attachment.uploading && !attachment.error,
  )
  const note = selectedNote.value
  const developmentResources = [...selectedDevelopmentResources.value]
  if (
    !session ||
    (!text &&
      attachments.length === 0 &&
      !note &&
      developmentResources.length === 0) ||
    pendingAttachments.value.some(
      (attachment) => attachment.uploading || attachment.error,
    )
  ) {
    return
  }
  if (!bridgeReady.value) {
    emit('notify', 'Codex 后端尚未连接')
    return
  }

  const linkedBugs = [...linkedTaskBugs.value]
  const testBugId = composerTestBugId.value
  const testRunId = composerTestRunId.value
  const testEvidenceDirectory =
    composerTestEvidenceDirectory.value
  const testBug = testBugId
    ? linkedBugs.find((bug) => bug.id === testBugId) ?? null
    : null
  const isTestTask = Boolean(
    testBug && testRunId && testEvidenceDirectory,
  )
  if (testBugId && !testBug) {
    emit('notify', '测试 Bug 已不在当前列表中，请重新发起测试')
    clearSelectedBug()
    return
  }
  const attachmentMetadata: CodexTaskAttachment[] = attachments.map(
    ({ id, name, contentType, size, kind }) => ({
      id,
      name,
      contentType,
      size,
      kind,
    }),
  )
  draft.value = ''
  clearPendingAttachments()
  const baseTaskPrompt =
    isTestTask && testBug
      ? bugTestPrompt(
          testBug,
          testEvidenceDirectory,
          text,
        )
      : linkedBugs.length > 0
      ? bugTaskPrompt(linkedBugs, text)
      : text ||
        '请分析这条速记，提炼结论、风险和待办；如果包含需要修复的问题，请定位根因、完成修复和必要验证。'
  let taskPrompt = note
    ? promptWithNoteContext(note, baseTaskPrompt)
    : baseTaskPrompt
  if (developmentResources.length > 0) {
    taskPrompt = promptWithDevelopmentResources(taskPrompt)
  }
  const titleSource =
    isTestTask && testBug
      ? `Codex 测试 Bug #${testBug.remoteId}`
      : linkedBugs.length > 1
      ? `解决 ${linkedBugs.length} 个禅道 Bug`
      : text ||
        note?.title ||
        developmentResources.map((resource) => resource.name).join('、') ||
        attachmentMetadata.map((attachment) => attachment.name).join('、')
  const shouldQueue =
    Boolean(runningTask.value) || queuedTasks.value.length > 0
  const task = shouldQueue
    ? codex.enqueueTask(
        session.id,
        taskPrompt,
        titleSource,
        attachmentMetadata,
      )
    : codex.startTask(
        session.id,
        taskPrompt,
        titleSource,
        attachmentMetadata,
      )
  if (!task) return
  task.model = session.model || selectedModel.value || undefined
  task.reasoningEffort =
    session.reasoningEffort || selectedEffort.value || undefined
  task.accessMode = session.accessMode ?? 'workspace'
  if (isTestTask && testBug && testRunId) {
    task.zentaoTestBugId = testBug.id
    task.zentaoTestRemoteId = testBug.remoteId
    task.zentaoTestWorkspaceId = workspaces.activeWorkspaceId
    task.zentaoTestRunId = testRunId
    task.zentaoTestEvidenceDirectory = testEvidenceDirectory
    task.displayPrompt =
      `Codex 测试：Bug #${testBug.remoteId} · ${testBug.title}`
  } else if (linkedBugs.length > 0) {
    task.zentaoBugId = linkedBugs[0].id
    task.zentaoBugIds = linkedBugs.map((bug) => bug.id)
    task.displayPrompt =
      linkedBugs.length > 1
        ? [
            `一次性解决 ${linkedBugs.length} 个 Bug`,
            linkedBugs
              .map((bug) => `#${bug.remoteId}`)
              .join(' · '),
            '',
            text,
          ].join('\n')
        : text
  } else if (note && !text) {
    task.displayPrompt = `速记：${note.title}`
  } else if (developmentResources.length > 0 && !text) {
    task.displayPrompt = `资源：${developmentResources
      .map((resource) => resource.name)
      .join('、')}`
  }
  if (linkedBugs.length > 0) clearSelectedBug()
  scrollToLatest(true)
  void nextTick(() => {
    resizeComposer()
    composer.value?.focus()
  })

  if (shouldQueue) {
    codex.persist()
    emit(
      'notify',
      `任务已加入队列，前面还有 ${queuedTasks.value.length - 1} 个任务`,
    )
    if (!runningTask.value) {
      const firstQueued = nextQueuedTask(session)
      if (firstQueued) void executeTask(session, firstQueued)
    }
    return
  }

  await executeTask(session, task)
}

async function interruptTask() {
  const session = activeSession.value
  const task = runningTask.value
  if (!session || !task || interruptingTaskId.value) return

  const markInterrupted = () => {
    if (task.status !== 'running') return false
    const completedAt = new Date().toISOString()
    task.status = 'interrupted'
    task.responseStreaming = false
    stopTimelineStreaming(task, completedAt)
    task.completedAt = completedAt
    task.lastActivityAt = task.completedAt
    if (session.activeTaskId === task.id) {
      advanceTaskQueue(session)
    } else {
      codex.persist()
    }
    if (task.zentaoTestBugId) {
      scheduleZentaoTestResultUpdate(task)
    }
    return true
  }

  if (!session.threadId || !task.turnId) {
    if (markInterrupted()) {
      emit('notify', '已结束本地残留任务状态')
    }
    return
  }

  interruptingTaskId.value = task.id
  try {
    const result = await interruptCodexTurn(
      session.threadId,
      task.turnId,
    )
    if (result.forced) bridgeConnected.value = false
    if (result.alreadyStopped) {
      if (markInterrupted()) {
        emit(
          'notify',
          result.forced
            ? 'Codex 服务已重启，当前任务已结束'
            : '当前任务已停止',
        )
      }
      return
    }
    if (markInterrupted()) {
      emit('notify', '已停止当前 Codex 任务')
    }
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法中断 Codex 任务',
    )
  } finally {
    interruptingTaskId.value = ''
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (
    event.key === 'Enter' &&
    !event.shiftKey &&
    !event.isComposing
  ) {
    event.preventDefault()
    void sendMessage()
  }
}

function resizeComposer() {
  const element = composer.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(element.scrollHeight, 150)}px`
}

function handleTranscriptScroll() {
  const element = transcript.value
  if (!element) return
  const distance =
    element.scrollHeight - element.scrollTop - element.clientHeight
  const atBottom = distance <= TRANSCRIPT_BOTTOM_THRESHOLD
  if (!atBottom && performance.now() < transcriptProgrammaticScrollUntil) {
    return
  }
  transcriptFollow.value = atBottom
  if (atBottom) transcriptHasNewContent.value = false
}

function handleTranscriptWheel(event: WheelEvent) {
  if (event.deltaY >= 0) return
  const element = transcript.value
  if (
    !element ||
    element.scrollHeight - element.clientHeight <=
      TRANSCRIPT_BOTTOM_THRESHOLD
  ) {
    return
  }
  transcriptProgrammaticScrollUntil = 0
  transcriptFollow.value = false
}

function scrollToLatest(force = false) {
  if (!force && !transcriptFollow.value) {
    transcriptHasNewContent.value = true
    return
  }
  void nextTick(() => {
    const element = transcript.value
    if (!element) return
    if (!force && !transcriptFollow.value) {
      transcriptHasNewContent.value = true
      return
    }
    transcriptProgrammaticScrollUntil = performance.now() + 180
    element.scrollTop = element.scrollHeight
    transcriptFollow.value = true
    transcriptHasNewContent.value = false
  })
}

function jumpToLatest() {
  const element = transcript.value
  if (!element) return
  transcriptProgrammaticScrollUntil = performance.now() + 500
  transcriptFollow.value = true
  transcriptHasNewContent.value = false
  element.scrollTo({
    top: element.scrollHeight,
    behavior: 'smooth',
  })
}

watch(
  () => auth.email,
  (email) => {
    codex.load(email)
    deploymentStore.load(email)
    gitRepositoryStore.load(email)
    shortcutStore.load(email)
    workDirectories.load(email)
    noteStore.load(email)
    zentaoTestStore.load(email)
    workspaces.load(email)
    void zentao.load(email, workspaces.activeWorkspaceId)
    ensureWorkspaceSession()
  },
  { immediate: true },
)

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    bugPickerOpen.value = false
    notePickerOpen.value = false
    developmentPickerOpen.value = false
    selectedDevelopmentResources.value = []
    selectedNoteId.value = ''
    composerBugIds.value = []
    composerTestBugId.value = null
    composerTestRunId.value = ''
    composerTestEvidenceDirectory.value = ''
    void zentao.load(auth.email, workspaces.activeWorkspaceId)
    ensureWorkspaceSession()
  },
)

watch(
  () => {
    const tasks = activeSession.value?.tasks ?? []
    return tasks
      .map(
        (task) =>
          [
            task.id,
            task.status,
            task.response.length,
            task.plan
              .map((step) => `${step.step}:${step.status}`)
              .join(','),
            task.activities
              .map(
                (activity) =>
                  `${activity.id}:${activity.status}:${activity.detail.length}:${activity.output.length}`,
              )
              .join(','),
            (task.timeline ?? [])
              .map((entry) =>
                entry.kind === 'message'
                  ? `${entry.id}:${entry.text.length}:${entry.streaming}`
                  : entry.id,
              )
              .join(','),
          ].join(':'),
      )
      .join('|')
  },
  (key) => {
    if (key === lastScrollKey) return
    lastScrollKey = key
    scrollToLatest()
  },
)

watch(
  () => codex.activeSessionId,
  () => {
    closeSessionContextMenu()
    cancelSessionRename()
    taskPanelOpen.value = false
    sessionPanelOpen.value = false
    scrollToLatest(true)
  },
)

watch(
  () => zentao.selectedBugId,
  (bugId) => {
    if (bugId && workspaceSessions.value.length === 0) {
      codex.createSession(
        workspaces.activeWorkspaceId,
        resolvePreferredWorkingDirectory(),
      )
    }
    ensureWorkspaceSession()
  },
  { immediate: true },
)

watch(
  () => zentao.codexPrefillRequest,
  (request) => {
    if (!request) return
    ensureWorkspaceSession()
    composerBugIds.value = [...request.bugIds]
    composerTestBugId.value = null
    composerTestRunId.value = ''
    composerTestEvidenceDirectory.value = ''
    draft.value = request.prompt
    zentao.clearCodexPrefillRequest()
    void nextTick(() => {
      resizeComposer()
      composer.value?.focus()
      const length = draft.value.length
      composer.value?.setSelectionRange(length, length)
    })
  },
  { immediate: true },
)

watch(
  () => zentao.codexTestPrefillRequest,
  (request) => {
    if (!request) return
    ensureWorkspaceSession()
    composerBugIds.value = [request.bugId]
    composerTestBugId.value = request.bugId
    composerTestRunId.value = request.runId
    composerTestEvidenceDirectory.value =
      request.evidenceDirectory
    selectedNoteId.value = ''
    selectedDevelopmentResources.value = []
    draft.value =
      '请在研发环境验证这个 Bug 是否已经修复，并生成测试结果和截图。'
    zentao.clearCodexTestPrefillRequest()
    void nextTick(() => {
      resizeComposer()
      composer.value?.focus()
      const length = draft.value.length
      composer.value?.setSelectionRange(length, length)
    })
  },
  { immediate: true },
)

watch(
  () => codex.prefillRequest,
  (request) => {
    if (
      !request ||
      request.workspaceId !== workspaces.activeWorkspaceId
    ) {
      return
    }
    if (workspaceSessions.value.length === 0 && request.cwd) {
      codex.createSession(request.workspaceId, request.cwd)
    } else {
      ensureWorkspaceSession()
    }
    const session = activeSession.value
    if (session && request.cwd) {
      session.cwd = request.cwd
      codex.touchSession(session)
      codex.persist()
      rememberPreferredWorkingDirectory(request.cwd)
    }
    draft.value = request.prompt
    composerBugIds.value = []
    composerTestBugId.value = null
    composerTestRunId.value = ''
    composerTestEvidenceDirectory.value = ''
    codex.clearPrefillRequest()
    void nextTick(() => {
      resizeComposer()
      composer.value?.focus()
      const length = draft.value.length
      composer.value?.setSelectionRange(length, length)
    })
  },
  { immediate: true },
)

watch(usageRange, () => {
  if (usageOpen.value) void refreshTokenUsage()
})

watch(workspaceSessions, () => {
  ensureWorkspaceSession()
})

watch(
  () =>
    workspaceSessions.value
      .flatMap((session) =>
        session.tasks.map((task) =>
          [
            task.id,
            task.status,
            task.response.length,
            task.zentaoBugIds?.join(',') ?? task.zentaoBugId ?? '',
            task.zentaoLocalSavedAt ?? '',
            task.zentaoPushedAt ?? '',
            task.zentaoPushedBugIds?.join(',') ?? '',
            task.zentaoPushError ?? '',
          ].join(':'),
        ),
      )
      .join('|'),
  () => {
    for (const session of workspaceSessions.value) {
      for (const task of session.tasks) {
        if (
          task.zentaoPushError === NO_RESPONSE_UPDATE_ERROR &&
          task.response.trim()
        ) {
          task.zentaoPushError = ''
        }
        if (
          task.status === 'completed' &&
          !task.zentaoPushError &&
          taskZentaoBugIds(task).length > 0
        ) {
          scheduleZentaoResolutionUpdate(task)
        }
      }
    }
  },
  { immediate: true },
)

watch(
  () => activeSession.value?.id,
  () => {
    clearPendingAttachments()
    ensureSessionModelSelection()
  },
)

onMounted(async () => {
  document.addEventListener('pointerdown', handleDirectoryPointerDown)
  document.addEventListener('scroll', closeSessionContextMenu, true)
  window.addEventListener('keydown', handleDirectoryKeydown)
  window.addEventListener('resize', closeSessionContextMenu)
  window.addEventListener('blur', closeSessionContextMenu)
  stopCodexEvents = subscribeCodexEvents(handleCodexEvent)
  clockTimer = window.setInterval(() => {
    clockNow.value = Date.now()
  }, 1000)
  scrollToLatest(true)
  try {
    const status = await getCodexStatus()
    bridgeReady.value = status.available
    bridgeConnected.value = status.connected
    bridgeExecutable.value = status.executable
    if (!status.connected) {
      markRunningTasksInterrupted(
        'Codex 服务已重启，未完成的任务已标记为中断',
      )
    } else {
      resumeQueuedSessions()
    }
    if (status.available) {
      void loadModels()
    }
  } catch {
    bridgeReady.value = false
    bridgeConnected.value = false
  } finally {
    bridgeChecked.value = true
  }
  restoreZentaoTestResults()
  void nextTick(() => {
    resizeComposer()
    composer.value?.focus()
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDirectoryPointerDown)
  document.removeEventListener('scroll', closeSessionContextMenu, true)
  window.removeEventListener('keydown', handleDirectoryKeydown)
  window.removeEventListener('resize', closeSessionContextMenu)
  window.removeEventListener('blur', closeSessionContextMenu)
  stopCodexEvents?.()
  clearPendingAttachments()
  if (persistTimer) window.clearTimeout(persistTimer)
  if (clockTimer) window.clearInterval(clockTimer)
  for (const timer of scheduledZentaoSaveTimers.values()) {
    window.clearTimeout(timer)
  }
  scheduledZentaoSaveTimers.clear()
  for (const timer of scheduledZentaoTestTimers.values()) {
    window.clearTimeout(timer)
  }
  scheduledZentaoTestTimers.clear()
})
</script>

<template>
  <section class="codex-workspace">
    <button
      v-if="sessionPanelOpen"
      class="codex-mobile-scrim"
      type="button"
      aria-label="关闭会话列表"
      @click="sessionPanelOpen = false"
    />
    <button
      v-if="taskPanelOpen"
      class="codex-mobile-scrim"
      type="button"
      aria-label="关闭任务记录"
      @click="taskPanelOpen = false"
    />

    <aside
      class="codex-sessions"
      :class="{ 'codex-sessions--open': sessionPanelOpen }"
    >
      <header class="codex-sessions__header">
        <div ref="sessionBrandMenu" class="codex-sidebar-brand-wrap">
          <button
            class="codex-sidebar-brand"
            type="button"
            aria-haspopup="menu"
            :aria-expanded="sessionBrandMenuOpen"
            :title="
              bridgeConnected
                ? `Codex 已连接${bridgeExecutable ? ` · ${bridgeExecutable}` : ''}`
                : bridgeReady
                  ? 'Codex 已就绪，发送消息后连接'
                  : 'Codex CLI 不可用'
            "
            @click="sessionBrandMenuOpen = !sessionBrandMenuOpen"
          >
            <strong>Codex</strong>
            <ChevronDown :size="12" aria-hidden="true" />
          </button>
          <Transition name="codex-sidebar-menu">
            <div
              v-if="sessionBrandMenuOpen"
              class="codex-sidebar-menu"
              role="menu"
            >
              <button type="button" role="menuitem" @click="createSession">
                <SquarePen :size="14" />
                新对话
              </button>
              <button
                type="button"
                role="menuitem"
                @click="openCreateProject"
              >
                <FolderPlus :size="14" />
                创建项目
              </button>
            </div>
          </Transition>
        </div>
        <div class="codex-sessions__actions">
          <button
            type="button"
            :class="{ 'codex-sidebar-action--active': sessionSearchOpen }"
            aria-label="搜索会话"
            title="搜索会话"
            @click="sessionSearchOpen = !sessionSearchOpen"
          >
            <Search :size="15" />
          </button>
          <button
            class="codex-mobile-panel-button"
            type="button"
            aria-label="关闭会话列表"
            @click="sessionPanelOpen = false"
          >
            <X :size="15" />
          </button>
        </div>
      </header>

      <button
        class="codex-sidebar-new"
        type="button"
        @click="createSession"
      >
        <SquarePen :size="15" />
        <span>新对话</span>
      </button>

      <div v-if="sessionSearchOpen" class="codex-sidebar-search">
        <Search :size="14" aria-hidden="true" />
        <input
          v-model="sessionSearchQuery"
          type="search"
          aria-label="搜索 Codex 会话"
          placeholder="搜索会话"
          autofocus
        />
        <button
          type="button"
          aria-label="关闭搜索"
          @click="
            sessionSearchOpen = false;
            sessionSearchQuery = ''
          "
        >
          <X :size="13" />
        </button>
      </div>

      <div class="codex-session-list">
        <section
          v-if="pinnedSessions.length > 0"
          class="codex-sidebar-section"
        >
          <button
            class="codex-sidebar-section__heading"
            type="button"
            :aria-expanded="pinnedSectionOpen"
            @click="pinnedSectionOpen = !pinnedSectionOpen"
          >
            <span>置顶</span>
            <ChevronDown
              :size="12"
              :class="{
                'codex-sidebar-chevron--closed': !pinnedSectionOpen,
              }"
            />
          </button>
          <div v-show="pinnedSectionOpen" class="codex-session-group">
            <div
              v-for="session in pinnedSessions"
              :key="session.id"
              class="codex-session-row"
              :class="{
                'codex-session-item--active':
                  codex.activeSessionId === session.id,
              }"
              @contextmenu.prevent="
                openSessionContextMenu($event, session)
              "
            >
              <button
                v-if="renamingSessionId !== session.id"
                class="codex-session-item"
                type="button"
                :aria-current="
                  codex.activeSessionId === session.id
                    ? 'page'
                    : undefined
                "
                @click="selectSession(session.id)"
              >
                <span class="codex-session-item__copy">
                  <span class="codex-session-item__title">
                    <strong>{{ session.title }}</strong>
                    <span
                      v-if="session.status === 'running'"
                      class="codex-session-running-dot"
                    />
                  </span>
                  <small
                    v-if="
                      codex.activeSessionId !== session.id &&
                      sessionPreview(session)
                    "
                    :title="sessionPreview(session)"
                  >
                    {{ sessionPreview(session) }}
                  </small>
                </span>
              </button>
              <div v-else class="codex-session-rename">
                <SquarePen :size="13" aria-hidden="true" />
                <input
                  v-model="renameDraft"
                  type="text"
                  maxlength="80"
                  aria-label="重命名会话"
                  @click.stop
                  @keydown.enter.prevent="
                    commitSessionRename(session)
                  "
                  @keydown.esc.prevent.stop="cancelSessionRename"
                  @blur="commitSessionRename(session)"
                />
              </div>
              <div
                v-if="renamingSessionId !== session.id"
                class="codex-session-actions"
              >
                <button
                  type="button"
                  :aria-label="`取消置顶 ${session.title}`"
                  title="取消置顶"
                  @click.stop="toggleSessionPinned(session)"
                >
                  <Pin :size="12" fill="currentColor" />
                </button>
                <button
                  type="button"
                  :aria-label="`删除会话 ${session.title}`"
                  :disabled="session.status === 'running'"
                  :title="
                    session.status === 'running'
                      ? '任务运行中，停止后才能删除'
                      : `删除会话 ${session.title}`
                  "
                  @click.stop="requestDeleteSession(session)"
                >
                  <Trash2 :size="12" />
                </button>
              </div>
            </div>
          </div>
        </section>

        <section
          v-if="projectSessionGroups.length > 0"
          class="codex-sidebar-section"
        >
          <div class="codex-project-section__header">
            <button
              class="codex-sidebar-section__heading"
              type="button"
              :aria-expanded="projectsSectionOpen"
              @click="projectsSectionOpen = !projectsSectionOpen"
            >
              <span>项目</span>
              <ChevronDown
                :size="12"
                :class="{
                  'codex-sidebar-chevron--closed':
                    !projectsSectionOpen,
                }"
              />
            </button>
            <button
              class="codex-project-section__create"
              type="button"
              aria-label="新建项目"
              title="新建项目"
              @click="openCreateProject"
            >
              <Plus :size="14" />
            </button>
          </div>
          <div v-show="projectsSectionOpen">
            <section
              v-for="group in projectSessionGroups"
              :key="group.key"
              class="codex-project-group"
            >
              <button
                class="codex-project-row"
                type="button"
                :title="group.path || '未关联工作目录'"
                :aria-expanded="
                  !collapsedProjectKeys.includes(group.key)
                "
                @click="toggleProjectGroup(group.key)"
              >
                <Folder :size="14" />
                <span>{{ group.label }}</span>
                <ChevronDown
                  :size="12"
                  :class="{
                    'codex-sidebar-chevron--closed':
                      collapsedProjectKeys.includes(group.key),
                  }"
                />
              </button>
              <div
                v-show="!collapsedProjectKeys.includes(group.key)"
                class="codex-project-sessions"
              >
                <div
                  v-for="session in group.sessions"
                  :key="session.id"
                  class="codex-session-row"
                  :class="{
                    'codex-session-item--active':
                      codex.activeSessionId === session.id,
                  }"
                  @contextmenu.prevent="
                    openSessionContextMenu($event, session)
                  "
                >
                  <button
                    v-if="renamingSessionId !== session.id"
                    class="codex-session-item"
                    type="button"
                    :aria-current="
                      codex.activeSessionId === session.id
                        ? 'page'
                        : undefined
                    "
                    @click="selectSession(session.id)"
                  >
                    <span class="codex-session-item__copy">
                      <span class="codex-session-item__title">
                        <strong>{{ session.title }}</strong>
                        <span
                          v-if="session.status === 'running'"
                          class="codex-session-running-dot"
                        />
                      </span>
                    </span>
                  </button>
                  <div v-else class="codex-session-rename">
                    <SquarePen :size="13" aria-hidden="true" />
                    <input
                      v-model="renameDraft"
                      type="text"
                      maxlength="80"
                      aria-label="重命名会话"
                      @click.stop
                      @keydown.enter.prevent="
                        commitSessionRename(session)
                      "
                      @keydown.esc.prevent.stop="cancelSessionRename"
                      @blur="commitSessionRename(session)"
                    />
                  </div>
                  <div
                    v-if="renamingSessionId !== session.id"
                    class="codex-session-actions"
                  >
                    <button
                      type="button"
                      :aria-label="`置顶会话 ${session.title}`"
                      title="置顶"
                      @click.stop="toggleSessionPinned(session)"
                    >
                      <Pin :size="12" />
                    </button>
                    <button
                      type="button"
                      :aria-label="`删除会话 ${session.title}`"
                      :disabled="session.status === 'running'"
                      :title="
                        session.status === 'running'
                          ? '任务运行中，停止后才能删除'
                          : `删除会话 ${session.title}`
                      "
                      @click.stop="requestDeleteSession(session)"
                    >
                      <Trash2 :size="12" />
                    </button>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </section>

        <div
          v-if="
            sortedSessions.length === 0 &&
            sessionSearchQuery.trim()
          "
          class="codex-sessions__empty"
        >
          没有匹配的会话
        </div>
      </div>
    </aside>

    <section class="codex-main">
      <header
        class="codex-main__header"
        :class="{
          'codex-main__header--new': !activeSession?.tasks.length,
        }"
      >
        <div class="codex-main__heading">
          <button
            class="codex-mobile-panel-button"
            type="button"
            aria-label="打开会话列表"
            @click="sessionPanelOpen = true"
          >
            <PanelLeft :size="15" />
          </button>
          <div>
            <h2>{{ activeSession?.title || assistantDisplayName }}</h2>
            <p>
              {{ activeSession?.tasks.length ?? 0 }} 个任务
              <span v-if="activeSession?.status === 'running'">
                · {{ runningTaskProgressLabel }}
              </span>
            </p>
          </div>
        </div>
        <div class="codex-main__actions">
          <button
            class="codex-task-trigger"
            type="button"
            aria-label="打开任务记录"
            @click="taskPanelOpen = true"
          >
            <ListChecks :size="15" />
            <span>任务</span>
            <small>{{ activeSession?.tasks.length ?? 0 }}</small>
          </button>
        </div>
      </header>

      <div v-if="bridgeChecked && !bridgeReady" class="codex-unavailable">
        <CircleAlert :size="18" />
        <div>
          <strong>{{ assistantDisplayName }} 尚未连接</strong>
          <p>
            请确认本机已安装并登录 Codex CLI，然后重新打开此页面。
          </p>
          <small v-if="bridgeExecutable">{{ bridgeExecutable }}</small>
        </div>
      </div>

      <div class="codex-transcript-shell">
        <div
          ref="transcript"
          class="codex-transcript"
          @scroll.passive="handleTranscriptScroll"
          @wheel.passive="handleTranscriptWheel"
        >
          <div
            v-if="!activeSession?.tasks.length"
            class="codex-welcome"
          >
            <span class="codex-welcome__avatar">
              <img
                v-if="assistantIcon"
                :src="assistantIcon"
                :alt="assistantDisplayName"
              />
              <CodexMark v-else :size="38" />
            </span>
            <h3>
              {{ assistantDisplayName }} 能帮你做些什么？
            </h3>
          </div>

          <article
            v-for="task in activeSession?.tasks || []"
            :key="task.id"
            class="codex-turn"
          >
            <div class="codex-user-turn">
              <div class="codex-user-message">
                <p v-if="task.displayPrompt || task.prompt">
                  {{ task.displayPrompt || task.prompt }}
                </p>
                <div
                  v-if="task.attachments?.length"
                  class="codex-message-attachments"
                >
                  <span
                    v-for="attachment in task.attachments"
                    :key="attachment.id"
                  >
                    <FileImage
                      v-if="attachment.kind === 'image'"
                      :size="13"
                    />
                    <FileText v-else :size="13" />
                    <span :title="attachment.name">{{ attachment.name }}</span>
                    <small>{{ formatFileSize(attachment.size) }}</small>
                  </span>
                </div>
                <time>{{ formatTime(task.startedAt) }}</time>
              </div>
              <button
                class="codex-user-note"
                :class="{
                  'codex-user-note--saved': taskNoteExists(task),
                }"
                type="button"
                :disabled="taskNoteExists(task)"
                :title="
                  taskNoteExists(task)
                    ? '该任务已在速记中'
                    : '记录问题并标记为待验证'
                "
                @click="addTaskToNotes(task)"
              >
                <NotebookPen :size="12" />
                {{
                  taskNoteExists(task)
                    ? '已加入速记'
                    : '加入速记'
                }}
              </button>
            </div>

            <div v-if="task.plan.length" class="codex-plan">
              <span>任务计划</span>
              <ul>
                <li
                  v-for="step in task.plan"
                  :key="step.step"
                  :class="`codex-plan__step--${step.status}`"
                >
                  <span />
                  {{ step.step }}
                </li>
              </ul>
            </div>

            <div
              v-if="taskTimeline(task).length"
              class="codex-turn-timeline"
              aria-label="Codex 执行过程"
            >
              <template
                v-for="entry in taskTimeline(task)"
                :key="entry.id"
              >
                <div
                  v-if="entry.kind === 'message'"
                  class="codex-assistant-message"
                >
                  <div
                    v-if="entry.text"
                    class="codex-markdown"
                    v-html="renderMarkdown(entry.text)"
                  />
                  <span
                    v-if="entry.streaming"
                    class="codex-stream-cursor"
                  />
                </div>

                <details
                  v-else-if="entry.kind === 'commandGroup'"
                  class="codex-activity-group"
                >
                  <summary class="codex-activity-group__summary">
                    <SquareTerminal :size="13" aria-hidden="true" />
                    <span>
                      运行了 {{ entry.activities.length }} 个命令
                    </span>
                    <ChevronRight
                      class="codex-activity-group__chevron"
                      :size="13"
                      aria-hidden="true"
                    />
                  </summary>
                  <div class="codex-activity-group__items">
                    <details
                      v-for="activity in entry.activities"
                      :key="activity.id"
                      class="codex-activity"
                    >
                      <summary>
                        <component
                          :is="activityIcon(activity)"
                          :size="13"
                        />
                        <span>{{ activityLabel(activity) }}</span>
                        <small v-if="activity.status === 'running'">
                          <LoaderCircle
                            class="codex-spin"
                            :size="12"
                          />
                          {{ activityElapsedLabel(activity) }}
                        </small>
                        <small v-else-if="activity.status === 'failed'">
                          <CircleAlert :size="12" />
                        </small>
                        <small v-else>
                          <Check :size="12" />
                        </small>
                        <ChevronRight
                          class="codex-activity__chevron"
                          :size="13"
                          aria-hidden="true"
                        />
                      </summary>
                      <section
                        v-if="activity.detail || activity.output"
                        class="codex-activity__panel"
                      >
                        <header>{{ activityPanelLabel(activity) }}</header>
                        <pre>{{ activityPanelBody(activity) }}</pre>
                      </section>
                    </details>
                  </div>
                </details>

                <details
                  v-else
                  class="codex-activity"
                >
                  <summary>
                    <component
                      :is="activityIcon(entry.activity)"
                      :size="13"
                    />
                    <span>{{ activityLabel(entry.activity) }}</span>
                    <small v-if="entry.activity.status === 'running'">
                      <LoaderCircle class="codex-spin" :size="12" />
                      {{ activityElapsedLabel(entry.activity) }}
                    </small>
                    <small v-else-if="entry.activity.status === 'failed'">
                      <CircleAlert :size="12" />
                    </small>
                    <small v-else>
                      <Check :size="12" />
                    </small>
                    <ChevronRight
                      class="codex-activity__chevron"
                      :size="13"
                      aria-hidden="true"
                    />
                  </summary>
                  <section
                    v-if="
                      entry.activity.detail ||
                      entry.activity.output
                    "
                    class="codex-activity__panel"
                  >
                    <header>
                      {{ activityPanelLabel(entry.activity) }}
                    </header>
                    <pre>{{ activityPanelBody(entry.activity) }}</pre>
                  </section>
                </details>
              </template>
            </div>

            <div
              v-if="
                taskTimeline(task).length ||
                task.status === 'running' ||
                task.status === 'queued' ||
                visibleActivities(task).length
              "
              class="codex-turn-meta"
              :class="{
                'codex-turn-meta--waiting':
                  taskIsWaitingForResponse(task),
                'codex-turn-meta--queued':
                  task.status === 'queued',
              }"
            >
              <span
                v-if="task.status === 'running'"
                class="codex-turn-meta__dot"
                aria-hidden="true"
              />
              <span>{{ taskTurnHeadline(task) }}</span>
              <small v-if="taskIsWaitingForResponse(task)">
                · {{ taskActivityAgeLabel(task) }}
              </small>
            </div>

            <p v-if="task.error" class="codex-task-error">
              {{ task.error }}
            </p>
          </article>
        </div>

        <Transition name="codex-scroll-latest">
          <button
            v-if="!transcriptFollow"
            class="codex-scroll-latest"
            type="button"
            aria-label="回到最新内容"
            @click="jumpToLatest"
          >
            <ArrowDown :size="14" />
            {{
              transcriptHasNewContent
                ? '有新内容 · 回到最新'
                : '回到最新'
            }}
          </button>
        </Transition>
      </div>

      <footer class="codex-composer">
        <div
          v-if="!activeSession?.tasks.length && activeSession"
          class="codex-composer-project"
        >
          <button
            v-if="activeSession.cwd"
            class="codex-composer-project__clear"
            type="button"
            aria-label="不在项目中工作"
            data-tooltip="不在项目中工作"
            @click="clearWorkingDirectory"
          >
            <CircleX :size="14" />
          </button>
          <button
            class="codex-composer-project__select"
            type="button"
            :aria-label="
              activeSession.cwd ? '更换项目' : '选择项目'
            "
            :title="activeSession.cwd || '选择项目'"
            @click="chooseWorkingDirectory"
          >
            <Folder
              v-if="!activeSession.cwd"
              :size="14"
              aria-hidden="true"
            />
            <span>
              {{
                activeSession.cwd
                  ? workingDirectoryLabel
                  : '选择项目'
              }}
            </span>
          </button>
        </div>
        <section
          v-if="queuedTasks.length"
          class="codex-queue"
          aria-label="排队中的任务"
        >
          <header>
            <ListChecks :size="13" aria-hidden="true" />
            <span>排队中</span>
            <small>{{ queuedTasks.length }}</small>
          </header>
          <div
            v-for="(task, index) in queuedTasks"
            :key="task.id"
            class="codex-queue__item"
          >
            <span class="codex-queue__index">{{ index + 1 }}</span>
            <span
              class="codex-queue__prompt"
              :title="task.displayPrompt || task.prompt"
            >
              {{ task.displayPrompt || task.prompt }}
            </span>
            <button
              type="button"
              :aria-label="`取消排队任务 ${index + 1}`"
              title="取消排队"
              @click="removeQueuedTask(task)"
            >
              <X :size="13" />
            </button>
          </div>
        </section>
        <div class="codex-composer__dock">
          <Transition name="codex-composer-menu">
            <section
              v-if="composerAddMenuOpen"
              ref="composerAddMenu"
              class="codex-composer-add"
              aria-label="添加上下文"
            >
              <header>添加</header>
              <button
                type="button"
                @click="handleComposerAddAction('files')"
              >
                <Paperclip :size="15" />
                <span>
                  <strong>文件和文件夹</strong>
                  <small>附件、图片和文档</small>
                </span>
              </button>
              <button
                class="codex-composer-add__directory"
                type="button"
                @click="handleComposerAddAction('directory')"
              >
                <FolderOpen :size="15" />
                <span>
                  <strong>选择工作目录</strong>
                  <small>切换项目源目录</small>
                </span>
              </button>
              <button
                class="codex-composer-add__development"
                type="button"
                @click="handleComposerAddAction('development')"
              >
                <Wrench :size="15" />
                <span>
                  <strong>资源</strong>
                  <small>Git、数据库、部署、服务器、网址和 App</small>
                </span>
              </button>
              <button
                class="codex-composer-add__bug"
                type="button"
                @click="handleComposerAddAction('bug')"
              >
                <Bug :size="15" />
                <span>
                  <strong>关联 Bug</strong>
                  <small>附加禅道缺陷上下文</small>
                </span>
              </button>
              <button
                class="codex-composer-add__note"
                type="button"
                @click="handleComposerAddAction('note')"
              >
                <NotebookPen :size="15" />
                <span>
                  <strong>速记</strong>
                  <small>选择速记内容作为上下文</small>
                </span>
              </button>
              <p class="codex-composer-add__section">插件</p>
              <button
                class="codex-composer-add__usage"
                type="button"
                @click="handleComposerAddAction('usage')"
              >
                <ChartNoAxesColumn :size="15" />
                <span>
                  <strong>用量统计</strong>
                  <small>查看 Token 使用情况</small>
                </span>
              </button>
              <button
                class="codex-composer-add__tasks"
                type="button"
                @click="handleComposerAddAction('tasks')"
              >
                <ListChecks :size="15" />
                <span>
                  <strong>任务记录</strong>
                  <small>查看当前会话执行历史</small>
                </span>
              </button>
            </section>
          </Transition>
        <Transition name="codex-run-settings">
          <section
            v-if="runSettingsOpen"
            ref="runSettingsPopover"
            class="codex-run-settings"
            aria-label="Codex 模型和推理强度"
          >
            <strong>{{ effortLabel(selectedEffort) }}</strong>
            <button
              class="codex-run-settings__model"
              type="button"
              :aria-expanded="runSettingsModelListOpen"
              @click="
                runSettingsModelListOpen = !runSettingsModelListOpen
              "
            >
              <span>
                {{
                  selectedModelInfo?.displayName ||
                  selectedModelInfo?.model
                }}
              </span>
              <ChevronRight :size="12" />
            </button>
            <div
              v-if="runSettingsModelListOpen"
              class="codex-run-settings__models"
              role="listbox"
              aria-label="选择模型"
            >
              <button
                v-for="option in modelOptions"
                :key="option.value"
                type="button"
                role="option"
                :aria-selected="option.value === selectedModel"
                @click="selectRunModel(option.value)"
              >
                <span>{{ option.label }}</span>
                <Check
                  v-if="option.value === selectedModel"
                  :size="12"
                />
              </button>
            </div>
            <div
              v-if="effortOptions.length"
              class="codex-effort-slider"
            >
              <input
                type="range"
                min="0"
                :max="Math.max(effortOptions.length - 1, 0)"
                step="1"
                :value="selectedEffortIndex"
                :disabled="
                  effortOptions.length <= 1 ||
                  activeSession?.status === 'running'
                "
                :style="{
                  '--effort-progress': effortProgress,
                }"
                aria-label="选择推理强度"
                @input="updateEffortFromSlider"
              />
              <span
                class="codex-effort-slider__markers"
                aria-hidden="true"
              >
                <i
                  v-for="(_, index) in effortOptions"
                  :key="index"
                  :class="{ active: index <= selectedEffortIndex }"
                />
              </span>
            </div>
          </section>
        </Transition>
        <Transition name="codex-access-settings">
          <section
            v-if="accessModeOpen"
            ref="accessModePopover"
            class="codex-access-settings"
            aria-label="选择 Codex 访问权限"
          >
            <header>应如何批准 Codex 操作？</header>
            <div
              class="codex-access-options"
              role="listbox"
              aria-label="访问权限"
            >
              <button
                v-for="option in accessModeOptions"
                :key="option.value"
                type="button"
                role="option"
                :aria-selected="
                  option.value === currentAccessMode
                "
                :class="{
                  'codex-access-option--full':
                    option.value === 'full',
                  'codex-access-option--selected':
                    option.value === currentAccessMode,
                }"
                @click="selectAccessMode(option.value)"
              >
                <span class="codex-access-option__icon">
                  <ShieldCheck
                    v-if="option.value === 'workspace'"
                    :size="16"
                  />
                  <CircleAlert v-else :size="16" />
                </span>
                <span class="codex-access-option__copy">
                  <strong>{{ option.label }}</strong>
                  <small>{{ option.description }}</small>
                </span>
                <Check
                  v-if="option.value === currentAccessMode"
                  :size="15"
                  class="codex-access-option__check"
                />
              </button>
            </div>
          </section>
        </Transition>
        <Transition name="codex-composer-menu">
          <section
            v-if="bugPickerOpen"
            ref="bugPicker"
            class="codex-composer-bug-picker"
            aria-label="关联禅道 Bug"
          >
            <header>
              <span>
                <Bug :size="15" />
                <strong>关联 Bug</strong>
              </span>
              <small v-if="zentao.connection.configured">
                {{ filteredBugChoices.length }} 个待处理
              </small>
            </header>
            <label class="codex-composer-bug-picker__search">
              <Search :size="14" aria-hidden="true" />
              <input
                v-model="bugSearchQuery"
                type="search"
                placeholder="搜索 Bug 编号或标题"
                autocomplete="off"
              />
            </label>
            <div
              v-if="zentao.loading"
              class="codex-composer-bug-picker__state"
            >
              <LoaderCircle class="codex-spin" :size="14" />
              正在读取 Bug
            </div>
            <p
              v-else-if="zentao.error"
              class="codex-composer-bug-picker__error"
            >
              {{ zentao.error }}
            </p>
            <p
              v-else-if="!zentao.connection.configured"
              class="codex-composer-bug-picker__state"
            >
              请先在「研发」中的 Bug 清单连接禅道
            </p>
            <div
              v-else-if="filteredBugChoices.length"
              class="codex-composer-bug-picker__list"
              role="listbox"
              aria-label="待处理 Bug"
            >
              <button
                v-for="bug in filteredBugChoices"
                :key="bug.id"
                type="button"
                role="option"
                :aria-selected="
                  linkedTaskBugs.some(
                    (linkedBug) => linkedBug.id === bug.id,
                  )
                "
                @click="selectBugForTask(bug)"
              >
                <span class="codex-composer-bug-picker__id">
                  #{{ bug.remoteId }}
                </span>
                <span class="codex-composer-bug-picker__copy">
                  <strong>{{ bug.title }}</strong>
                  <small>
                    {{ bug.productName || bug.projectName || '未分类' }}
                    · P{{ bug.priority || '-' }}
                    · 严重度 {{ bug.severity || '-' }}
                  </small>
                </span>
                <Check
                  v-if="
                    linkedTaskBugs.some(
                      (linkedBug) => linkedBug.id === bug.id,
                    )
                  "
                  :size="14"
                />
              </button>
            </div>
            <p v-else class="codex-composer-bug-picker__state">
              {{
                bugSearchQuery.trim()
                  ? '没有匹配的 Bug'
                  : '暂无待处理 Bug'
              }}
            </p>
            <button
              v-if="linkedTaskBugs.length"
              class="codex-composer-bug-picker__clear"
              type="button"
              @click="clearSelectedBug"
            >
              取消关联当前 Bug
            </button>
          </section>
        </Transition>
        <Transition name="codex-composer-menu">
          <section
            v-if="notePickerOpen"
            ref="notePicker"
            class="codex-composer-bug-picker codex-composer-note-picker"
            aria-label="选择速记"
          >
            <header>
              <span>
                <NotebookPen :size="15" />
                <strong>选择速记</strong>
              </span>
              <small>{{ filteredNoteChoices.length }} 条记录</small>
            </header>
            <label class="codex-composer-bug-picker__search">
              <Search :size="14" aria-hidden="true" />
              <input
                v-model="noteSearchQuery"
                type="search"
                placeholder="搜索速记标题、内容或标签"
                autocomplete="off"
              />
            </label>
            <div
              v-if="filteredNoteChoices.length"
              class="codex-composer-bug-picker__list"
              role="listbox"
              aria-label="速记列表"
            >
              <button
                v-for="note in filteredNoteChoices"
                :key="note.id"
                class="codex-composer-note-option"
                type="button"
                role="option"
                :aria-selected="selectedNoteId === note.id"
                @click="selectNoteForTask(note)"
              >
                <span class="codex-composer-note-option__icon">
                  <NotebookPen :size="14" />
                </span>
                <span class="codex-composer-bug-picker__copy">
                  <strong>{{ note.title }}</strong>
                  <small>{{ notePreview(note) || '无内容预览' }}</small>
                  <em>
                    {{ noteUpdatedLabel(note) }}
                    <template v-if="note.tags.length">
                      · #{{ note.tags.slice(0, 2).join(' #') }}
                    </template>
                  </em>
                </span>
                <Check
                  v-if="selectedNoteId === note.id"
                  :size="14"
                />
              </button>
            </div>
            <p v-else class="codex-composer-bug-picker__state">
              {{
                noteSearchQuery.trim()
                  ? '没有匹配的速记'
                  : '暂无速记，请先在工具中的速记页面创建'
              }}
            </p>
            <button
              v-if="selectedNote"
              class="codex-composer-bug-picker__clear"
              type="button"
              @click="clearSelectedNote"
            >
              取消选择当前速记
            </button>
          </section>
        </Transition>
        <Transition name="codex-composer-menu">
          <section
            v-if="developmentPickerOpen"
            ref="developmentPicker"
            class="codex-composer-bug-picker codex-development-resource-picker"
            aria-label="选择资源"
          >
            <header>
              <span>
                <Wrench :size="15" />
                <strong>资源</strong>
              </span>
              <small>
                已选 {{ selectedDevelopmentResources.length }} 项
              </small>
            </header>
            <UiSegmentedControl
              :model-value="developmentResourceKind"
              :items="developmentResourceKindItems"
              aria-label="资源类型"
              @update:model-value="
                selectDevelopmentResourceKind(
                  $event as DevelopmentResourceKind,
                )
              "
            />
            <label class="codex-composer-bug-picker__search">
              <Search :size="14" aria-hidden="true" />
              <input
                v-model="developmentSearchQuery"
                type="search"
                placeholder="搜索名称、地址或路径"
                autocomplete="off"
              />
            </label>
            <p
              v-if="developmentResourcesError"
              class="codex-development-resource-picker__notice"
            >
              {{ developmentResourcesError }}
            </p>
            <div
              v-if="
                developmentResourcesLoading &&
                activeDevelopmentResources.length === 0
              "
              class="codex-composer-bug-picker__state"
            >
              <LoaderCircle class="codex-spin" :size="14" />
              正在读取资源
            </div>
            <div
              v-else-if="filteredDevelopmentResources.length"
              class="codex-composer-bug-picker__list"
              role="listbox"
              aria-label="资源列表"
              aria-multiselectable="true"
            >
              <button
                v-for="resource in filteredDevelopmentResources"
                :key="resource.key"
                class="codex-development-resource-option"
                type="button"
                role="option"
                :aria-selected="
                  developmentResourceSelected(resource)
                "
                @click="toggleDevelopmentResource(resource)"
              >
                <span class="codex-development-resource-option__icon">
                  <component
                    :is="developmentResourceIcon(resource.kind)"
                    :size="14"
                  />
                </span>
                <span class="codex-composer-bug-picker__copy">
                  <strong>{{ resource.name }}</strong>
                  <small>{{ resource.meta }}</small>
                </span>
                <Check
                  v-if="developmentResourceSelected(resource)"
                  :size="14"
                />
              </button>
            </div>
            <p v-else class="codex-composer-bug-picker__state">
              {{
                developmentSearchQuery.trim()
                  ? '没有匹配的资源'
                  : `暂无${developmentResourceKindLabel(
                      developmentResourceKind,
                    )}`
              }}
            </p>
            <footer
              v-if="selectedDevelopmentResources.length"
              class="codex-development-resource-picker__footer"
            >
              <span>
                {{ selectedDevelopmentResources.length }} 项已加入上下文
              </span>
              <button
                type="button"
                @click="developmentPickerOpen = false"
              >
                完成
              </button>
            </footer>
          </section>
        </Transition>
        <div
          class="codex-composer__box"
          :class="{
            'codex-composer__box--dragging': attachmentDragActive,
          }"
          @dragenter="handleAttachmentDragEnter"
          @dragover="handleAttachmentDragOver"
          @dragleave="attachmentDragActive = false"
          @drop="handleAttachmentDrop"
        >
          <input
            ref="attachmentInput"
            class="codex-composer__file-input"
            type="file"
            multiple
            tabindex="-1"
            aria-hidden="true"
            @change="handleAttachmentInput"
          />
          <div
            v-if="linkedTaskBugs.length"
            class="codex-composer-bug-context"
          >
            <Bug :size="14" aria-hidden="true" />
            <span
              :title="
                linkedTaskBugs
                  .map((bug) => `#${bug.remoteId} ${bug.title}`)
                  .join('\n')
              "
            >
              <strong>#{{ linkedTaskBugs[0].remoteId }}</strong>
              {{ linkedTaskBugs[0].title }}
              <small v-if="linkedTaskBugs.length > 1">
                +{{ linkedTaskBugs.length - 1 }}
              </small>
            </span>
            <button
              type="button"
              aria-label="取消关联 Bug"
              title="取消关联"
              @click="clearSelectedBug"
            >
              <X :size="13" />
            </button>
          </div>
          <div
            v-if="selectedNote"
            class="codex-composer-bug-context codex-composer-note-context"
          >
            <NotebookPen :size="14" aria-hidden="true" />
            <span :title="selectedNote.content">
              <strong>速记</strong>
              {{ selectedNote.title }}
            </span>
            <button
              type="button"
              aria-label="取消选择速记"
              title="取消选择"
              @click="clearSelectedNote"
            >
              <X :size="13" />
            </button>
          </div>
          <div
            v-if="selectedDevelopmentResources.length"
            class="codex-composer-bug-context codex-composer-resource-context"
          >
            <Wrench :size="14" aria-hidden="true" />
            <span
              :title="
                selectedDevelopmentResources
                  .map((resource) => resource.name)
                  .join('\n')
              "
            >
              <strong>资源</strong>
              {{ selectedDevelopmentResources[0].name }}
              <small v-if="selectedDevelopmentResources.length > 1">
                +{{ selectedDevelopmentResources.length - 1 }}
              </small>
            </span>
            <button
              type="button"
              aria-label="取消选择资源"
              title="取消选择"
              @click="clearDevelopmentResources"
            >
              <X :size="13" />
            </button>
          </div>
          <div
            v-if="pendingAttachments.length"
            class="codex-pending-attachments"
            aria-label="待发送附件"
          >
            <div
              v-for="attachment in pendingAttachments"
              :key="attachment.localId"
              class="codex-pending-attachment"
              :class="{
                'codex-pending-attachment--error': attachment.error,
              }"
            >
              <span class="codex-pending-attachment__preview">
                <img
                  v-if="
                    attachment.kind === 'image' &&
                    attachment.previewUrl
                  "
                  :src="attachment.previewUrl"
                  alt=""
                />
                <FileImage
                  v-else-if="attachment.kind === 'image'"
                  :size="17"
                />
                <FileText v-else :size="17" />
              </span>
              <span class="codex-pending-attachment__copy">
                <strong :title="attachment.name">
                  {{ attachment.name }}
                </strong>
                <small>
                  {{
                    attachment.uploading
                      ? '上传中…'
                      : attachment.error ||
                        formatFileSize(attachment.size)
                  }}
                </small>
              </span>
              <LoaderCircle
                v-if="attachment.uploading"
                class="codex-spin"
                :size="13"
              />
              <button
                v-else
                type="button"
                :aria-label="`移除附件 ${attachment.name}`"
                title="移除附件"
                @click="removeAttachment(attachment.localId)"
              >
                <X :size="13" />
              </button>
            </div>
          </div>
          <textarea
            ref="composer"
            v-model="draft"
            rows="2"
            :aria-label="composerPlaceholder"
            :placeholder="composerPlaceholder"
            :disabled="!bridgeReady"
            @keydown="handleComposerKeydown"
            @paste="handleComposerPaste"
            @input="resizeComposer"
          />
          <div class="codex-composer__footer">
            <div class="codex-composer__leading">
              <button
                class="codex-composer-add-trigger"
                type="button"
                aria-label="添加文件和上下文"
                title="添加"
                :aria-expanded="composerAddMenuOpen"
                :disabled="!bridgeReady"
                @click="toggleComposerAddMenu"
              >
                <Plus :size="15" />
              </button>
              <button
                ref="accessMode"
                class="codex-composer-access"
                :class="{
                  'codex-composer-access--full':
                    currentAccessMode === 'full',
                }"
                type="button"
                aria-haspopup="listbox"
                :aria-expanded="accessModeOpen"
                :disabled="activeSession?.status === 'running'"
                :title="
                  currentAccessMode === 'full'
                    ? 'Codex 可访问工作目录外的文件和互联网'
                    : 'Codex 仅在工作目录内读写文件和执行命令'
                "
                @click="toggleAccessMode"
              >
                <CircleAlert
                  v-if="currentAccessMode === 'full'"
                  :size="14"
                />
                <ShieldCheck v-else :size="14" />
                {{ currentAccessModeLabel }}
              </button>
            </div>
            <div class="codex-composer__actions">
              <div
                v-if="modelOptions.length"
                ref="runSettings"
                class="codex-run-config"
              >
                <button
                  class="codex-run-trigger"
                  type="button"
                  :aria-label="`模型和推理强度：${
                    selectedModelInfo?.displayName ||
                    selectedModelInfo?.model
                  } ${effortLabel(selectedEffort)}`"
                  :aria-expanded="runSettingsOpen"
                  :disabled="activeSession?.status === 'running'"
                  @click="toggleRunSettings"
                >
                  <span v-if="runSettingsOpen">选择推理强度</span>
                  <template v-else>
                    <span class="codex-run-trigger__model">
                      {{
                        selectedModelInfo?.displayName ||
                        selectedModelInfo?.model
                      }}
                    </span>
                    <span
                      v-if="effortOptions.length"
                      class="codex-run-trigger__effort"
                    >
                      {{ effortLabel(selectedEffort) }}
                    </span>
                  </template>
                  <ChevronDown :size="11" />
                </button>
              </div>
              <span
                v-else-if="modelsLoading"
                class="codex-model-loading"
              >
                读取模型…
              </span>
              <span
                v-else-if="modelsError"
                class="codex-model-loading"
                :title="modelsError"
              >
                默认模型
              </span>
              <button
                v-if="
                  activeSession?.status === 'running' && !canSend
                "
                type="button"
                :disabled="interruptingTaskId === runningTask?.id"
                :aria-label="
                  interruptingTaskId === runningTask?.id
                    ? '正在停止 Codex 任务'
                    : '停止 Codex 任务'
                "
                :title="
                  interruptingTaskId === runningTask?.id
                    ? '正在停止'
                    : '停止'
                "
                @click="interruptTask"
              >
                <LoaderCircle
                  v-if="interruptingTaskId === runningTask?.id"
                  class="codex-spin"
                  :size="14"
                />
                <Square v-else :size="14" />
              </button>
              <button
                v-else
                type="button"
                :aria-label="
                  activeSession?.status === 'running'
                    ? '加入任务队列'
                    : '发送消息'
                "
                :title="
                  activeSession?.status === 'running'
                    ? '加入队列'
                    : '发送'
                "
                :disabled="!canSend"
                @click="sendMessage"
              >
                <ArrowUp :size="16" />
              </button>
            </div>
          </div>
        </div>
        </div>
      </footer>
    </section>

    <aside
      class="codex-tasks"
      :class="{ 'codex-tasks--open': taskPanelOpen }"
    >
      <header class="codex-tasks__header">
        <div>
          <small>任务日志</small>
          <strong>任务记录</strong>
        </div>
        <button
          class="codex-panel-close"
          type="button"
          aria-label="关闭任务记录"
          @click="taskPanelOpen = false"
        >
          <X :size="15" />
        </button>
      </header>

      <div class="codex-task-stats">
        <span><strong>{{ sessionStats.tasks }}</strong>任务</span>
        <span><strong>{{ sessionStats.commands }}</strong>命令</span>
        <span><strong>{{ sessionStats.files }}</strong>文件</span>
      </div>

      <div v-if="activeTasks.length" class="codex-task-list">
        <article
          v-for="task in pagedTasks"
          :key="task.id"
          class="codex-task"
        >
          <header :class="`codex-task__header--${task.status}`">
            <component
              :is="statusIcon(task)"
              :class="{
                'codex-spin': task.status === 'running',
              }"
              :size="14"
            />
            <strong>{{ statusLabel(task) }}</strong>
            <time>
              {{
                task.status === 'running'
                  ? taskElapsedLabel(task)
                  : formatTime(task.startedAt)
              }}
            </time>
          </header>
          <p>{{ task.displayPrompt || task.prompt }}</p>
          <div>
            <span class="codex-task-stat codex-task-stat--items">
              {{ task.itemCount }} 项
            </span>
            <span
              v-if="task.commandCount"
              class="codex-task-stat codex-task-stat--commands"
            >
              {{ task.commandCount }} 命令
            </span>
            <span
              v-if="task.fileChangeCount"
              class="codex-task-stat codex-task-stat--files"
            >
              {{ task.fileChangeCount }} 文件
            </span>
            <span
              v-if="task.durationMs || task.status === 'running'"
              class="codex-task-stat codex-task-stat--duration"
            >
              {{
                task.status === 'running'
                  ? `已用时 ${taskElapsedLabel(task)}`
                  : formatDuration(task.durationMs)
              }}
            </span>
            <span
              v-if="task.totalTokens"
              class="codex-task-stat codex-task-stat--tokens"
            >
              {{ task.totalTokens }} tokens
            </span>
            <span
              v-if="taskZentaoBugIds(task).length"
              class="codex-task-stat codex-task-stat--bugs"
            >
              {{ taskZentaoBugIds(task).length }} 个 Bug
            </span>
            <button
              v-if="task.zentaoPushError"
              class="codex-zentao-status codex-zentao-status--error codex-zentao-retry"
              type="button"
              @click="updateZentaoResolution(task)"
            >
              更新失败 · 重试
            </button>
            <span
              v-else-if="task.zentaoPushedAt"
              class="codex-zentao-status codex-zentao-status--success"
            >
              已更新禅道 Bug 单
            </span>
            <span
              v-else-if="task.zentaoLocalSavedAt"
              class="codex-zentao-status"
            >
              备注已保存 · 待更新禅道
            </span>
            <span
              v-else-if="taskZentaoBugIds(task).length > 0"
              class="codex-zentao-status"
            >
              待更新 Bug 单
            </span>
          </div>
        </article>
      </div>

      <div v-else class="codex-tasks__empty">
        当前会话还没有任务记录
      </div>

      <UiPagination
        :page="taskPage"
        :total="activeTasks.length"
        label="Codex 任务分页"
        compact
        @update:page="setTaskPage"
      />
    </aside>

    <Teleport to="body">
      <Transition name="codex-session-context-menu">
        <div
          v-if="sessionContextMenuOpen && contextMenuSession"
          ref="sessionContextMenu"
          class="codex-session-context-menu"
          role="menu"
          aria-label="会话操作"
          :style="{
            left: `${contextMenuX}px`,
            top: `${contextMenuY}px`,
          }"
          @contextmenu.prevent
        >
          <button
            type="button"
            role="menuitem"
            @click="renameContextMenuSession"
          >
            <SquarePen :size="15" />
            <span>重命名</span>
          </button>
          <button
            type="button"
            role="menuitem"
            @click="toggleContextMenuSessionPinned"
          >
            <Pin
              :size="15"
              :fill="contextMenuSession.pinned ? 'currentColor' : 'none'"
            />
            <span>
              {{ contextMenuSession.pinned ? '取消置顶' : '置顶' }}
            </span>
          </button>
          <button
            class="codex-session-context-menu__danger"
            type="button"
            role="menuitem"
            :disabled="contextMenuSession.status === 'running'"
            :title="
              contextMenuSession.status === 'running'
                ? '任务运行中，停止后才能删除'
                : '删除会话'
            "
            @click="deleteContextMenuSession"
          >
            <Trash2 :size="15" />
            <span>删除</span>
          </button>
        </div>
      </Transition>
    </Teleport>

    <UiDialog
      v-if="createProjectOpen"
      title="创建项目"
      description="选择本机文件夹或工作空间中的工作目录，新建一个可继续对话的 Codex 项目。"
      size="wide"
      height="standard"
      max-height="min(680px, calc(100dvh - 32px))"
      initial-focus="#codex-project-name"
      @close="closeCreateProject"
    >
      <form
        id="codex-create-project-form"
        class="codex-project-form"
        @submit.prevent="submitCreateProject"
      >
        <label class="codex-project-name" for="codex-project-name">
          <Folder :size="17" aria-hidden="true" />
          <input
            id="codex-project-name"
            v-model="projectName"
            type="text"
            maxlength="40"
            autocomplete="off"
            placeholder="项目名称"
          />
        </label>

        <section class="codex-project-source">
          <span class="codex-project-source__label">项目来源</span>
          <div
            class="codex-project-source__card"
            :class="{
              'codex-project-source__card--compact':
                projectSourceMode === 'folder',
            }"
          >
            <UiSegmentedControl
              :model-value="projectSourceMode"
              :items="projectSourceItems"
              aria-label="选择项目源位置方式"
              @update:model-value="
                projectSourceMode = $event as
                  | 'folder'
                  | 'workDirectory'
              "
            />

            <button
              v-if="projectSourceMode === 'folder'"
              class="codex-project-folder"
              :class="{
                'codex-project-folder--selected':
                  Boolean(projectFolderPath),
              }"
              type="button"
              :disabled="choosingProjectFolder"
              @click="chooseProjectFolder"
            >
              <span class="codex-project-folder__icon">
                <LoaderCircle
                  v-if="choosingProjectFolder"
                  class="codex-spin"
                  :size="17"
                />
                <FolderOpen v-else :size="17" />
              </span>
              <span class="codex-project-folder__copy">
                <strong>
                  {{
                    projectFolderPath
                      ? '已选择文件夹'
                      : '在此电脑上添加文件夹'
                  }}
                </strong>
                <small :title="projectFolderPath || undefined">
                  {{
                    projectFolderPath ||
                    '选择本机任意文件夹作为项目源目录'
                  }}
                </small>
              </span>
              <span class="codex-project-folder__action">
                {{ projectFolderPath ? '更换' : '添加' }}
              </span>
            </button>

            <div v-else class="codex-project-work-directories">
              <button
                v-for="directory in workspaceDirectories"
                :key="directory.id"
                type="button"
                :class="{
                  'codex-project-work-directory--selected':
                    directory.path === projectWorkDirectoryPath,
                }"
                @click="selectProjectWorkDirectory(directory)"
              >
                <Folder :size="16" />
                <span>
                  <strong>{{ directory.name }}</strong>
                  <small>{{ directory.path }}</small>
                </span>
                <Check
                  v-if="directory.path === projectWorkDirectoryPath"
                  :size="14"
                />
              </button>
              <p v-if="workspaceDirectories.length === 0">
                当前工作空间还没有工作目录，请切换到“文件夹”选择本机目录。
              </p>
            </div>
          </div>
        </section>

        <p v-if="projectError" class="codex-project-error" role="alert">
          {{ projectError }}
        </p>
      </form>

      <template #footer>
        <UiButton size="lg" @click="closeCreateProject">
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="codex-create-project-form"
          :disabled="choosingProjectFolder"
        >
          创建项目
        </UiButton>
      </template>
    </UiDialog>

    <UiDialog
      v-if="deleteSessionTarget"
      title="删除会话？"
      :description="`「${deleteSessionTarget.title}」及 ${deleteSessionTarget.tasks.length} 条任务记录将从本机移除，此操作不可撤销。`"
      height="auto"
      @close="deleteSessionTarget = null"
    >
      <div class="codex-session-delete-confirm">
        <p>如果该会话仍在后台运行，请先在会话中停止任务。</p>
      </div>

      <template #footer>
        <UiButton size="lg" @click="deleteSessionTarget = null">
          取消
        </UiButton>
        <UiButton
          variant="danger"
          size="lg"
          @click="confirmDeleteSession"
        >
          <Trash2 :size="14" />
          删除会话
        </UiButton>
      </template>
    </UiDialog>

    <UiDialog
      v-if="usageOpen"
      title="Token 用量"
      :description="`优先读取本机 CC Switch 数据，读取失败时使用 ${PRODUCT_NAME}自有统计。`"
      size="wide"
      scroll-mode="body"
      max-height="min(560px, calc(100dvh - 32px))"
      @close="usageOpen = false"
    >
      <div class="codex-usage-dialog">
        <div class="codex-usage-toolbar">
          <UiSegmentedControl
            :model-value="usageRange"
            :items="usageRangeItems"
            aria-label="用量时间范围"
            @update:model-value="
              usageRange = $event as UsageRange
            "
          />
          <span
            v-if="usageSnapshot"
            class="codex-usage-source"
            :class="{
              'codex-usage-source--fallback':
                usageSnapshot.source === 'workcove',
            }"
          >
            {{
              usageSnapshot.source === 'cc-switch'
                ? 'CC Switch 本地数据'
                : `${PRODUCT_NAME}自有统计`
            }}
          </span>
        </div>

        <div v-if="usageLoading" class="codex-usage-loading">
          <LoaderCircle class="codex-spin" :size="16" />
          正在读取用量…
        </div>

        <template v-else-if="usageSnapshot">
          <div class="codex-usage-grid">
            <article>
              <span>总成本</span>
              <strong>{{ formatUsageCost(usageSnapshot.costUsd) }}</strong>
              <small>
                {{
                  usageSnapshot.costUsd === null
                    ? '自有统计暂不计算费用'
                    : '按 CC Switch 模型价格计算'
                }}
              </small>
            </article>
            <article>
              <span>总请求数</span>
              <strong>
                {{ formatUsageCount(usageSnapshot.requestCount) }}
              </strong>
              <small>
                成功 {{ formatUsageCount(usageSnapshot.successCount) }}
              </small>
            </article>
            <article>
              <span>真实消耗 Tokens</span>
              <strong>
                {{ formatUsageTokens(usageSnapshot.totalTokens) }}
              </strong>
              <small>输入 + 输出</small>
            </article>
            <article>
              <span>缓存命中率</span>
              <strong>
                {{ formatUsageRate(usageSnapshot.cacheHitRate) }}
              </strong>
              <small>缓存读取 / 输入 Tokens</small>
            </article>
          </div>

          <div class="codex-usage-breakdown">
            <span>
              输入
              <strong>
                {{ formatUsageTokens(usageSnapshot.inputTokens) }}
              </strong>
            </span>
            <span>
              输出
              <strong>
                {{ formatUsageTokens(usageSnapshot.outputTokens) }}
              </strong>
            </span>
            <span>
              缓存读取
              <strong>
                {{ formatUsageTokens(usageSnapshot.cacheReadTokens) }}
              </strong>
            </span>
            <span>
              缓存写入
              <strong>
                {{ formatUsageTokens(usageSnapshot.cacheCreationTokens) }}
              </strong>
            </span>
          </div>

          <TokenUsageCharts :snapshot="usageSnapshot" />

          <p
            v-if="usageSnapshot.reason"
            class="codex-usage-note"
          >
            {{ usageSnapshot.reason }}
          </p>
          <p class="codex-usage-updated">
            更新于 {{ formatUsageUpdated(usageSnapshot.updatedAt) }}
          </p>
        </template>

        <p v-else class="codex-usage-error">
          {{ usageError || '暂无用量数据' }}
        </p>
      </div>
    </UiDialog>

  </section>
</template>

<style scoped>
.codex-workspace {
  position: relative;
  display: grid;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  grid-template-columns: 296px minmax(0, 1fr);
  background: var(--canvas-raised);
  border-top: 1px solid var(--line-soft);
}

.codex-sessions,
.codex-tasks {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.codex-sessions {
  background: var(--canvas-raised);
  border-right: 1px solid var(--line-soft);
}

.codex-tasks {
  position: absolute;
  z-index: 12;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(92%, 340px);
  background: var(--canvas);
  border-left: 1px solid var(--line-soft);
  box-shadow: var(--shadow-popover);
  transform: translateX(102%);
  transition: transform var(--duration-window) var(--ease-apple);
}

.codex-tasks--open {
  transform: translateX(0);
}

.codex-tasks__header {
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
}

.codex-tasks__header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.codex-tasks__header small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-tasks__header strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-sessions__header {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 10px 4px;
}

.codex-sidebar-brand {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 4px;
  flex: 1;
  padding: 0;
  color: var(--text);
  text-align: left;
  background: transparent;
  border: 0;
}

.codex-sidebar-brand strong {
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  line-height: var(--line-title);
}

.codex-sidebar-brand > svg {
  color: var(--text-muted);
}

.codex-sidebar-brand-wrap {
  position: relative;
  min-width: 0;
  flex: 1;
}

.codex-sidebar-menu {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  left: -6px;
  width: 184px;
  padding: 4px;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: 10px;
  box-shadow: var(--dropdown-shadow);
  backdrop-filter: blur(24px) saturate(1.35);
}

.codex-sidebar-menu > button {
  display: flex;
  width: 100%;
  min-height: 32px;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  color: var(--text);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: 7px;
}

.codex-sidebar-menu > button:hover {
  background: var(--hover-bg);
}

.codex-sidebar-menu > button > svg {
  color: var(--text-muted);
}

.codex-sidebar-menu-enter-active,
.codex-sidebar-menu-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
  transform-origin: top left;
}

.codex-sidebar-menu-enter-from,
.codex-sidebar-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

.codex-session-context-menu {
  position: fixed;
  z-index: var(--z-popover);
  width: 188px;
  padding: 4px;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: 10px;
  box-shadow: var(--dropdown-shadow);
  backdrop-filter: blur(24px) saturate(1.35);
}

.codex-session-context-menu > button {
  display: flex;
  width: 100%;
  min-height: 32px;
  align-items: center;
  gap: 9px;
  padding: 0 9px;
  color: var(--text);
  font-size: 13px;
  text-align: left;
  background: transparent;
  border-radius: 7px;
}

.codex-session-context-menu > button:hover:not(:disabled),
.codex-session-context-menu > button:focus-visible {
  background: var(--hover-bg);
  outline: none;
}

.codex-session-context-menu > button > svg {
  flex: 0 0 auto;
  color: var(--text-muted);
}

.codex-session-context-menu > button > span {
  min-width: 0;
  flex: 1;
}

.codex-session-context-menu > .codex-session-context-menu__danger {
  color: var(--system-red);
}

.codex-session-context-menu
  > .codex-session-context-menu__danger
  > svg {
  color: var(--system-red);
}

.codex-session-context-menu > button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.codex-session-context-menu-enter-active,
.codex-session-context-menu-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
  transform-origin: top left;
}

.codex-session-context-menu-enter-from,
.codex-session-context-menu-leave-to {
  opacity: 0;
  transform: translateY(-3px) scale(0.98);
}

.codex-sessions__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
}

.codex-sessions__actions > button {
  position: relative;
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: 8px;
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.codex-sessions__actions > button:hover,
.codex-sessions__actions > .codex-sidebar-action--active {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-sessions__actions > .codex-mobile-panel-button {
  display: none;
}

.codex-sidebar-new {
  display: flex;
  width: calc(100% - 8px);
  min-height: 32px;
  align-items: center;
  gap: 8px;
  margin: 2px 4px 4px;
  padding: 0 6px;
  color: var(--text);
  font-size: 14px;
  font-weight: var(--weight-emphasis);
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition: background var(--duration-fast) var(--ease-apple);
}

.codex-sidebar-new:hover {
  background: var(--hover-bg);
}

.codex-sidebar-new > svg {
  color: var(--text-muted);
}

.codex-sidebar-search {
  display: flex;
  min-height: 30px;
  align-items: center;
  gap: 6px;
  margin: 2px 8px 6px;
  padding: 0 7px;
  color: var(--text-subtle);
  background: var(--surface-inset);
  border-radius: 8px;
}

.codex-sidebar-search input {
  min-width: 0;
  flex: 1;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.codex-sidebar-search input::-webkit-search-cancel-button {
  display: none;
}

.codex-sidebar-search > button {
  display: grid;
  width: 20px;
  height: 20px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 5px;
  place-items: center;
}

.codex-sidebar-search > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-session-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  padding: 2px 4px 12px;
  overflow-y: auto;
}

.codex-sidebar-section {
  display: flex;
  flex-direction: column;
}

.codex-sidebar-section + .codex-sidebar-section {
  margin-top: 10px;
}

.codex-project-section__header {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 2px;
}

.codex-project-section__header > .codex-sidebar-section__heading {
  width: auto;
  min-width: 0;
  flex: 1;
}

.codex-project-section__create {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-muted);
  pointer-events: none;
  background: transparent;
  border-radius: 6px;
  opacity: 0;
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple),
    opacity var(--duration-fast) var(--ease-apple);
}

.codex-project-section__header:hover
  > .codex-project-section__create,
.codex-project-section__header:focus-within
  > .codex-project-section__create {
  pointer-events: auto;
  opacity: 1;
}

.codex-project-section__create:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-sidebar-section__heading {
  display: flex;
  width: 100%;
  min-height: 28px;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  padding: 0 6px;
  color: var(--text-subtle);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: 6px;
}

.codex-sidebar-section__heading:hover {
  color: var(--text-muted);
  background: var(--hover-bg);
}

.codex-sidebar-chevron--closed {
  transform: rotate(-90deg);
}

.codex-sidebar-section__heading > svg,
.codex-project-row > svg:last-child {
  transition: transform var(--duration-fast) var(--ease-apple);
}

.codex-session-group,
.codex-project-sessions {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.codex-project-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.codex-project-row {
  display: flex;
  width: 100%;
  min-height: 30px;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  color: var(--text);
  font-size: 14px;
  text-align: left;
  background: transparent;
  border-radius: 7px;
}

.codex-project-row:hover {
  background: var(--hover-bg);
}

.codex-project-row > svg {
  flex: 0 0 auto;
  color: var(--text-muted);
}

.codex-project-row > span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-project-row > svg:last-child {
  opacity: 0;
}

.codex-project-row:hover > svg:last-child,
.codex-project-row:focus-visible > svg:last-child,
.codex-project-row[aria-expanded="false"] > svg:last-child {
  opacity: 1;
}

.codex-project-sessions {
  padding-left: 24px;
}

.codex-session-row {
  position: relative;
  width: 100%;
  min-height: 30px;
  border-radius: 6px;
}

.codex-session-item {
  display: flex;
  width: 100%;
  min-height: 30px;
  align-items: center;
  padding: 4px 50px 4px 6px;
  text-align: left;
  background: transparent;
  border-radius: 6px;
}

.codex-session-rename {
  display: flex;
  width: 100%;
  min-height: 30px;
  align-items: center;
  gap: 6px;
  padding: 3px 7px;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, var(--system-blue) 48%, transparent);
}

.codex-session-rename > svg {
  flex: 0 0 auto;
}

.codex-session-rename > input {
  min-width: 0;
  flex: 1;
  padding: 0;
  color: var(--text);
  font-size: 14px;
  line-height: 20px;
  background: transparent;
  border: 0;
  outline: none;
}

.codex-session-row:hover {
  background: var(--hover-bg);
}

.codex-session-item--active {
  background: var(--codex-session-active);
  border-radius: 8px;
  box-shadow: none;
}

.codex-session-item--active:hover {
  background: var(--codex-session-active);
}

.codex-session-actions {
  position: absolute;
  top: 50%;
  right: 4px;
  display: flex;
  align-items: center;
  gap: 1px;
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity var(--duration-fast) var(--ease-apple);
}

.codex-session-actions > button {
  display: grid;
  width: 20px;
  height: 20px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 5px;
  place-items: center;
}

.codex-session-row:hover .codex-session-actions,
.codex-session-row:focus-within .codex-session-actions,
.codex-session-item--active .codex-session-actions {
  opacity: 1;
}

.codex-session-actions > button:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-session-actions > button:disabled {
  opacity: 0.36;
}

.codex-session-delete-confirm {
  padding: 0 var(--space-5) var(--space-4);
}

.codex-session-delete-confirm p {
  margin: 0 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.codex-project-form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  padding: 0 var(--space-5) var(--space-3);
}

.codex-project-name {
  display: flex;
  width: 100%;
  height: 42px;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  color: var(--text-muted);
  background: var(--canvas-raised);
  border: 1.5px solid var(--line-soft);
  border-radius: 10px;
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.codex-project-name:focus-within {
  border-color: var(--system-blue);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--system-blue) 10%, transparent);
}

.codex-project-name > svg {
  flex: 0 0 auto;
}

.codex-project-name > input {
  min-width: 0;
  flex: 1;
  padding: 0;
  color: var(--text);
  font-size: 14px;
  background: transparent;
  border: 0;
}

.codex-project-name > input:focus,
.codex-project-name > input:focus-visible {
  outline: none;
}

.codex-project-source {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.codex-project-source__label {
  color: var(--text-muted);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.codex-project-source__card {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 12px;
  background: color-mix(
    in srgb,
    var(--canvas) 74%,
    var(--canvas-raised)
  );
  border: 1px solid var(--line-soft);
  border-radius: 12px;
}

.codex-project-source__card--compact {
  min-height: 0;
  flex: 0 0 auto;
}

.codex-project-source__card :deep(.ui-segmented) {
  display: grid;
  width: 100%;
  flex: 0 0 auto;
  margin-bottom: 10px;
}

.codex-project-folder {
  display: flex;
  width: 100%;
  height: 104px;
  min-height: 104px;
  flex: 0 0 104px;
  align-items: center;
  gap: 10px;
  padding: 14px;
  color: var(--text);
  text-align: left;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.codex-project-folder:hover:not(:disabled) {
  border-color: var(--line);
  background: var(--surface-secondary);
}

.codex-project-folder--selected {
  border-color: var(--system-blue);
}

.codex-project-folder__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: 8px;
  place-items: center;
}

.codex-project-folder__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.codex-project-folder__copy strong,
.codex-project-folder__copy small,
.codex-project-work-directories strong,
.codex-project-work-directories small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-project-folder__copy strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.codex-project-folder__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-project-folder__action {
  flex: 0 0 auto;
  padding: 4px 8px;
  color: var(--text);
  font-size: var(--font-caption);
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  box-shadow: var(--shadow-control);
}

.codex-project-work-directories {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.codex-project-work-directories > button {
  display: grid;
  width: 100%;
  min-height: 44px;
  grid-template-columns: 20px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  color: var(--text);
  text-align: left;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
}

.codex-project-work-directories > button:hover {
  background: var(--surface-secondary);
}

.codex-project-work-directories
  > .codex-project-work-directory--selected {
  border-color: var(--system-blue);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--system-blue) 9%, transparent);
}

.codex-project-work-directories > button > svg {
  color: var(--text-muted);
}

.codex-project-work-directories > button > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.codex-project-work-directories strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.codex-project-work-directories small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-project-work-directories > p {
  margin: 0;
  padding: 24px 12px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-body);
  text-align: center;
}

.codex-project-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.codex-usage-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-5);
}

.codex-usage-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.codex-usage-source {
  flex: 0 0 auto;
  padding: 3px 7px;
  color: var(--system-green);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
  border-radius: var(--radius-badge);
}

.codex-usage-source--fallback {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.codex-usage-loading {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.codex-usage-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.codex-usage-grid article {
  display: flex;
  min-width: 0;
  min-height: 104px;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.codex-usage-grid article > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.codex-usage-grid article > strong {
  margin-top: var(--space-2);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-metric);
  font-weight: var(--weight-strong);
  line-height: 1;
}

.codex-usage-grid article > small {
  margin-top: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-usage-breakdown {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.codex-usage-breakdown span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-usage-breakdown strong {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-weight: var(--weight-emphasis);
}

.codex-usage-note,
.codex-usage-updated,
.codex-usage-error {
  margin: 0;
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.codex-usage-note {
  color: var(--system-orange);
}

.codex-usage-updated {
  color: var(--text-subtle);
}

.codex-usage-error {
  min-height: 160px;
  color: var(--system-red);
}

.codex-session-item__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 1px;
}

.codex-session-item__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
}

.codex-session-item__copy strong {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--text);
  font-size: 14px;
  font-weight: var(--weight-regular);
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-session-item--active .codex-session-item__copy strong {
  font-weight: var(--weight-emphasis);
}

.codex-session-running-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  background: var(--system-blue);
  border-radius: 50%;
}

.codex-session-item__copy small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-sessions__empty,
.codex-tasks__empty {
  display: grid;
  flex: 1;
  padding: var(--space-4);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  place-items: center;
}

.codex-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: var(--canvas-raised);
}

.codex-main__header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.codex-main__header--new {
  display: none;
}

.codex-main__heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.codex-main__heading > div {
  min-width: 0;
}

.codex-main__heading h2 {
  overflow: hidden;
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: var(--line-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-main__heading p {
  margin: 1px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-main__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.codex-task-trigger,
.codex-panel-close {
  display: inline-flex;
  height: 26px;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  color: var(--text);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
}

.codex-task-trigger:hover,
.codex-panel-close:hover {
  color: var(--text);
}

.codex-task-trigger small {
  min-width: 14px;
  padding: 0 3px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  line-height: 14px;
  text-align: center;
  background: var(--canvas);
  border-radius: var(--radius-badge);
}

.codex-panel-close {
  width: 26px;
  padding: 0;
}

.codex-unavailable {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  color: var(--text-muted);
  background: var(--canvas);
  border-bottom: 1px solid var(--line-soft);
}

.codex-unavailable > svg {
  flex: 0 0 auto;
  color: var(--system-orange);
}

.codex-unavailable div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--space-2);
}

.codex-unavailable strong {
  flex: 0 0 auto;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.codex-unavailable p {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.codex-unavailable small {
  display: none;
  margin-left: auto;
  overflow: hidden;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-transcript-shell {
  position: relative;
  display: flex;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.codex-transcript {
  display: flex;
  width: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-8);
  padding: var(--space-5) clamp(var(--space-4), 4vw, 48px);
  overflow-y: auto;
  scroll-behavior: auto;
}

.codex-scroll-latest {
  position: absolute;
  right: clamp(var(--space-4), 4vw, 48px);
  bottom: var(--space-4);
  z-index: 4;
  display: inline-flex;
  height: 30px;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-3);
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  line-height: 1;
  white-space: nowrap;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--radius-control);
  box-shadow: var(--shadow-popover);
  backdrop-filter: blur(20px) saturate(1.35);
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.codex-scroll-latest:hover {
  color: var(--accent);
  background: var(--canvas-raised);
  transform: translateY(-1px);
}

.codex-scroll-latest:active {
  transform: translateY(0);
}

.codex-scroll-latest-enter-active,
.codex-scroll-latest-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.codex-scroll-latest-enter-from,
.codex-scroll-latest-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.codex-welcome {
  display: flex;
  max-width: 760px;
  margin: auto;
  align-items: center;
  text-align: center;
  flex-direction: column;
}

.codex-welcome__avatar {
  display: grid;
  width: 46px;
  height: 46px;
  overflow: hidden;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  place-items: center;
}

.codex-welcome__avatar > img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.codex-welcome h3 {
  margin: 18px 0 0;
  color: var(--text);
  font-size: 24px;
  font-weight: var(--weight-regular);
  line-height: 32px;
}

.codex-turn {
  display: flex;
  width: 100%;
  max-width: 840px;
  margin-inline: auto;
  flex-direction: column;
  gap: 18px;
}

.codex-user-turn {
  display: flex;
  align-self: flex-end;
  max-width: min(72%, 580px);
  align-items: flex-end;
  flex-direction: column;
  gap: 5px;
}

.codex-user-message {
  align-self: stretch;
  padding: 10px 14px;
  color: var(--text);
  background: var(--surface-secondary);
  border-radius: 18px;
}

.codex-user-message p {
  margin: 0;
  font-size: var(--font-body);
  line-height: var(--line-body);
  white-space: pre-wrap;
}

.codex-user-message time {
  display: block;
  margin-top: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: right;
}

.codex-user-note {
  display: inline-flex;
  height: 24px;
  align-items: center;
  gap: 4px;
  padding: 0 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-badge);
  opacity: 0;
  pointer-events: none;
  transform: translateY(2px);
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.codex-user-turn:hover .codex-user-note,
.codex-user-turn:focus-within .codex-user-note,
.codex-user-note:focus-visible {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.codex-user-note:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-user-note:disabled {
  cursor: default;
}

.codex-user-note--saved {
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 9%,
    transparent
  );
  border-color: color-mix(
    in srgb,
    var(--system-green) 16%,
    var(--line-soft)
  );
}

@media (hover: none) {
  .codex-user-note {
    opacity: 1;
    pointer-events: auto;
    transform: none;
  }
}

.codex-message-attachments {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-1);
  margin-top: var(--space-2);
  flex-wrap: wrap;
}

.codex-message-attachments > span {
  display: inline-flex;
  max-width: 220px;
  height: 24px;
  align-items: center;
  gap: 4px;
  padding: 0 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-badge);
}

.codex-message-attachments > span > svg {
  flex: 0 0 auto;
  color: var(--icon);
}

.codex-message-attachments > span > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-message-attachments small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-turn-meta {
  display: flex;
  min-height: 20px;
  align-items: center;
  gap: 6px;
  color: var(--codex-message-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.codex-turn-meta small {
  overflow: hidden;
  color: var(--codex-message-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-turn-meta__dot {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  background: var(--accent);
  border-radius: 50%;
  animation: codex-live-pulse 1.2s ease-in-out infinite;
}

.codex-turn-meta--waiting {
  color: var(--system-orange);
}

.codex-turn-meta--queued {
  color: var(--system-orange);
}

.codex-turn-meta--waiting .codex-turn-meta__dot {
  background: var(--system-orange);
  animation: none;
}

.codex-turn-meta--waiting small {
  color: color-mix(
    in srgb,
    var(--system-orange) 72%,
    var(--text-subtle)
  );
}

.codex-plan {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.codex-plan > span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-plan ul {
  display: flex;
  margin: 0;
  padding: 0;
  flex-direction: column;
  gap: var(--space-1);
  list-style: none;
}

.codex-plan li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.codex-plan li > span {
  width: 6px;
  height: 6px;
  background: var(--text-subtle);
  border-radius: 50%;
}

.codex-plan__step--inProgress > span {
  background: var(--system-blue);
}

.codex-plan__step--completed > span {
  background: var(--system-green);
}

.codex-turn-timeline {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-3);
}

.codex-activity-group {
  width: 100%;
}

.codex-activity-group__summary {
  display: grid;
  min-height: 25px;
  align-items: center;
  grid-template-columns: 16px minmax(0, 1fr) 13px;
  column-gap: var(--space-2);
  padding: 0 var(--space-2);
  color: var(--codex-message-muted);
  font-size: var(--font-body);
  line-height: var(--codex-message-line);
  cursor: pointer;
  list-style: none;
  border-radius: var(--radius-control);
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.codex-activity-group__summary:hover {
  color: var(--codex-message-text);
  background: var(--hover-bg);
}

.codex-activity-group__summary::-webkit-details-marker {
  display: none;
}

.codex-activity-group__summary > svg {
  width: 15px;
  height: 15px;
  color: var(--codex-message-subtle);
  stroke-width: 1.8;
}

.codex-activity-group__summary > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-activity-group__chevron {
  transition: transform var(--duration-fast) var(--ease-apple);
}

.codex-activity-group[open] > .codex-activity-group__summary {
  color: var(--codex-message-text);
}

.codex-activity-group[open] > .codex-activity-group__summary > svg {
  color: var(--codex-message-muted);
}

.codex-activity-group[open] > .codex-activity-group__summary
  .codex-activity-group__chevron {
  transform: rotate(90deg);
}

.codex-activity-group__items {
  display: flex;
  width: calc(100% - var(--space-6));
  margin: var(--space-2) 0 0 var(--space-6);
  flex-direction: column;
  gap: var(--space-3);
}

.codex-activity {
  width: 100%;
  background: transparent;
}

.codex-activity + .codex-activity {
  border-top: 0;
}

.codex-activity summary {
  display: grid;
  min-height: 25px;
  align-items: center;
  grid-template-columns: 16px minmax(0, 1fr) auto 13px;
  column-gap: var(--space-2);
  padding: 0 var(--space-2);
  color: var(--codex-message-muted);
  font-size: var(--font-body);
  cursor: pointer;
  list-style: none;
  border-radius: var(--radius-control);
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.codex-activity summary:hover {
  color: var(--codex-message-text);
  background: var(--hover-bg);
}

.codex-activity summary::-webkit-details-marker {
  display: none;
}

.codex-activity summary > svg {
  width: 15px;
  height: 15px;
  flex: 0 0 auto;
  stroke-width: 1.8;
  color: var(--codex-message-subtle);
}

.codex-activity summary > span {
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-body);
  line-height: var(--codex-message-line);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-activity summary small {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 3px;
  color: var(--codex-message-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--line-caption);
}

.codex-activity__chevron {
  width: 13px;
  height: 13px;
  flex: 0 0 auto;
  color: var(--codex-message-subtle);
  opacity: 0;
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.codex-activity summary:hover .codex-activity__chevron,
.codex-activity summary:focus-visible .codex-activity__chevron,
.codex-activity[open] .codex-activity__chevron {
  opacity: 1;
}

.codex-activity[open] .codex-activity__chevron {
  transform: rotate(90deg);
}

.codex-activity__panel {
  margin: 3px 0 8px 22px;
  overflow: hidden;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: 7px;
}

.codex-activity__panel > header {
  padding: 7px 10px 6px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 18px;
  background: color-mix(
    in srgb,
    var(--canvas-raised) 72%,
    transparent
  );
  border-bottom: 1px solid var(--line-soft);
}

.codex-activity__panel > pre {
  max-height: 260px;
  margin: 0;
  padding: var(--space-2) var(--space-3);
  overflow: auto;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 19px;
  tab-size: 2;
  white-space: pre-wrap;
  background: transparent;
  border: 0;
}

.codex-assistant-message {
  width: 100%;
}

.codex-markdown {
  color: var(--codex-message-text);
  font-size: var(--font-body);
  line-height: var(--codex-message-line);
}

.codex-markdown :deep(p) {
  margin: 0 0 16px;
}

.codex-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.codex-markdown :deep(h1),
.codex-markdown :deep(h2),
.codex-markdown :deep(h3) {
  margin: var(--space-4) 0 var(--space-2);
  font-weight: var(--weight-emphasis);
  line-height: 1.35;
}

.codex-markdown :deep(h1) {
  font-size: var(--font-title);
}

.codex-markdown :deep(h2),
.codex-markdown :deep(h3) {
  font-size: var(--font-body);
}

.codex-markdown :deep(ul),
.codex-markdown :deep(ol) {
  margin: var(--space-2) 0 var(--space-3);
  padding-left: var(--space-5);
}

.codex-markdown :deep(code) {
  padding: 2px 5px;
  color: var(--codex-message-text);
  font-family: var(--font-mono);
  font-size: var(--codex-inline-font);
  line-height: 18px;
  background: var(--codex-inline-code-bg);
  border-radius: var(--radius-badge);
}

.codex-markdown :deep(pre) {
  margin: var(--space-3) 0;
  padding: var(--space-3);
  overflow-x: auto;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.codex-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
}

.codex-markdown :deep(a) {
  color: var(--accent);
}

.codex-markdown :deep(blockquote) {
  margin: var(--space-3) 0;
  padding-left: var(--space-3);
  color: var(--text-muted);
  border-left: 2px solid var(--line);
}

.codex-stream-cursor {
  display: inline-block;
  width: 7px;
  height: 15px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--accent);
  animation: codex-cursor 0.9s steps(2, start) infinite;
}

.codex-task-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.codex-composer {
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  padding: var(--space-4) var(--space-5) var(--space-5);
  background: var(--canvas-raised);
}

.codex-queue {
  display: flex;
  width: 100%;
  max-width: 840px;
  max-height: 148px;
  margin: 0 auto var(--space-2);
  overflow-y: auto;
  flex-direction: column;
  gap: var(--space-1);
}

.codex-queue > header {
  display: flex;
  min-height: 20px;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-queue > header > svg {
  flex: 0 0 auto;
}

.codex-queue > header small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.codex-queue__item {
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-1) var(--space-1)
    var(--space-2);
  color: var(--text-muted);
  background: var(--surface-secondary);
  border-radius: var(--radius-panel);
}

.codex-queue__index {
  display: grid;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: 50%;
  place-items: center;
}

.codex-queue__prompt {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: var(--font-body);
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-queue__item > button {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border: 0;
  border-radius: var(--radius-control);
  place-items: center;
}

.codex-queue__item > button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.codex-composer__dock {
  position: relative;
  width: 100%;
  max-width: 840px;
  margin: 0 auto;
}

.codex-composer-project {
  position: relative;
  z-index: 0;
  display: flex;
  width: min(calc(100% - 40px), 840px);
  min-height: 30px;
  align-items: center;
  margin: 0 auto -8px;
  padding: 5px 14px 13px 8px;
  color: var(--text-muted);
  font-size: var(--font-body);
  background: var(--surface-secondary);
  border-radius: 12px 12px 0 0;
}

.codex-composer-project__clear {
  position: relative;
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: 50%;
  place-items: center;
}

.codex-composer-project__clear:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-composer-project__clear::after {
  position: absolute;
  z-index: 30;
  bottom: calc(100% + 7px);
  left: 50%;
  padding: 5px 9px;
  color: var(--tooltip-text);
  font-size: var(--font-caption);
  line-height: 1.3;
  white-space: nowrap;
  pointer-events: none;
  content: attr(data-tooltip);
  background: var(--tooltip-bg);
  border-radius: 7px;
  opacity: 0;
  transform: translate(-50%, 3px);
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.codex-composer-project__clear:hover::after,
.codex-composer-project__clear:focus-visible::after {
  opacity: 1;
  transform: translate(-50%, 0);
}

.codex-composer-project__select {
  display: flex;
  min-width: 0;
  height: 22px;
  flex: 1;
  align-items: center;
  gap: 6px;
  padding: 0 6px;
  color: inherit;
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.codex-composer-project__select:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-composer-project__select:focus-visible,
.codex-composer-project__clear:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
}

.codex-composer-project__select > svg {
  flex: 0 0 auto;
  color: var(--text-muted);
}

.codex-composer-project__select > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-composer-bug-picker {
  position: absolute;
  z-index: 21;
  bottom: calc(100% + 8px);
  left: 50%;
  display: flex;
  width: min(100%, 420px);
  max-height: min(56vh, 440px);
  padding: 10px;
  flex-direction: column;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  box-shadow:
    0 0 0 1px rgb(0 0 0 / 3%),
    0 16px 48px rgb(0 0 0 / 12%),
    0 3px 12px rgb(0 0 0 / 6%);
  transform: translateX(-50%);
}

.codex-composer-bug-picker > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 3px 4px 8px;
}

.codex-composer-bug-picker > header > span {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
}

.codex-composer-bug-picker > header svg {
  flex: 0 0 auto;
  color: var(--system-orange);
}

.codex-composer-bug-picker > header strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-composer-bug-picker > header small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-composer-bug-picker__search {
  display: flex;
  height: 34px;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  padding: 0 9px;
  color: var(--text-subtle);
  background: var(--surface-inset);
  border: 1px solid transparent;
  border-radius: 9px;
}

.codex-composer-bug-picker__search:focus-within {
  border-color: color-mix(
    in srgb,
    var(--system-blue) 42%,
    transparent
  );
  box-shadow: var(--focus-ring);
}

.codex-composer-bug-picker__search > svg {
  flex: 0 0 auto;
}

.codex-composer-bug-picker__search > input {
  min-width: 0;
  flex: 1;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
  outline: none;
}

.codex-composer-bug-picker__search > input::placeholder {
  color: var(--text-subtle);
}

.codex-composer-bug-picker__list {
  display: flex;
  min-height: 0;
  margin-top: 6px;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
}

.codex-composer-bug-picker__list > button {
  display: grid;
  width: 100%;
  min-height: 48px;
  grid-template-columns: auto minmax(0, 1fr) 14px;
  align-items: center;
  gap: 9px;
  padding: 6px 8px;
  color: var(--text-muted);
  text-align: left;
  background: transparent;
  border-radius: 8px;
}

.codex-composer-bug-picker__list > button:hover,
.codex-composer-bug-picker__list > button[aria-selected="true"] {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-composer-bug-picker__list > button > svg {
  color: var(--system-blue);
}

.codex-composer-bug-picker__id {
  color: var(--system-blue);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.codex-composer-bug-picker__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.codex-composer-bug-picker__copy strong,
.codex-composer-bug-picker__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-composer-bug-picker__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.codex-composer-bug-picker__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-composer-bug-picker__state,
.codex-composer-bug-picker__error {
  display: flex;
  min-height: 96px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0;
  padding: 16px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-align: center;
}

.codex-composer-bug-picker__error {
  color: var(--system-red);
}

.codex-composer-bug-picker__clear {
  min-height: 32px;
  margin-top: 6px;
  padding: 6px 8px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-align: left;
  background: transparent;
  border-top: 1px solid var(--line-soft);
  border-radius: 0 0 7px 7px;
}

.codex-composer-bug-picker__clear:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-composer-note-picker > header svg,
.codex-composer-note-picker
  .codex-composer-bug-picker__list
  > button
  > svg {
  color: var(--system-blue);
}

.codex-composer-bug-picker__list
  > .codex-composer-note-option {
  min-height: 64px;
}

.codex-composer-note-option__icon {
  display: grid;
  width: 24px;
  height: 24px;
  color: var(--system-blue);
  background: color-mix(
    in srgb,
    var(--system-blue) 8%,
    transparent
  );
  border-radius: 6px;
  place-items: center;
}

.codex-composer-note-option__icon > svg {
  color: var(--system-blue);
}

.codex-composer-note-option
  .codex-composer-bug-picker__copy
  em {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 11px;
  font-style: normal;
  line-height: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-development-resource-picker > header svg {
  color: #14a38b;
}

.codex-development-resource-picker :deep(.ui-segmented) {
  display: grid;
  width: 100%;
  margin: 0 0 8px;
}

.codex-development-resource-picker__notice {
  margin: 6px 4px 0;
  color: var(--system-orange);
  font-size: 11px;
  line-height: 16px;
}

.codex-composer-bug-picker__list
  > .codex-development-resource-option {
  min-height: 54px;
}

.codex-development-resource-option__icon {
  display: grid;
  width: 24px;
  height: 24px;
  color: #14a38b;
  background: color-mix(in srgb, #14a38b 9%, transparent);
  border-radius: 6px;
  place-items: center;
}

.codex-development-resource-option__icon > svg {
  color: #14a38b;
}

.codex-development-resource-picker__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
  padding: 8px 4px 2px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  border-top: 1px solid var(--line-soft);
}

.codex-development-resource-picker__footer > button {
  min-width: 58px;
  height: 28px;
  padding: 0 10px;
  color: var(--canvas-raised);
  font-size: var(--font-caption);
  background: var(--label);
  border-radius: 7px;
}

.codex-composer-add {
  --codex-add-icon-color: var(--text-muted);

  position: absolute;
  z-index: 18;
  bottom: calc(100% + 8px);
  left: 50%;
  width: 100%;
  max-height: 430px;
  padding: 8px;
  overflow-y: auto;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  box-shadow:
    0 0 0 1px rgb(0 0 0 / 3%),
    0 16px 48px rgb(0 0 0 / 12%),
    0 3px 12px rgb(0 0 0 / 6%);
  transform: translateX(-50%);
}

.codex-composer-add > header {
  padding: 5px 8px 7px;
  color: var(--text-subtle);
  font-size: var(--font-body);
}

.codex-composer-add > button {
  display: grid;
  width: 100%;
  min-height: 34px;
  grid-template-columns: 20px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  color: var(--text);
  text-align: left;
  background: transparent;
  border-radius: 8px;
}

.codex-composer-add > button:hover,
.codex-composer-add > button:focus-visible {
  background: var(--hover-bg);
}

.codex-composer-add > button > svg {
  color: var(--codex-add-icon-color);
}

.codex-composer-add__directory {
  --codex-add-icon-color: #4c8bf5;
}

.codex-composer-add__development {
  --codex-add-icon-color: #14a38b;
}

.codex-composer-add__bug {
  --codex-add-icon-color: var(--system-orange);
}

.codex-composer-add__note {
  --codex-add-icon-color: var(--system-blue);
}

.codex-composer-add__usage {
  --codex-add-icon-color: var(--system-green);
}

.codex-composer-add__tasks {
  --codex-add-icon-color: #8b5cf6;
}

.codex-composer-add > button > span {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 8px;
}

.codex-composer-add strong {
  flex: 0 0 auto;
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
}

.codex-composer-add small {
  min-width: 0;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-composer-add__section {
  margin: 7px 0 3px;
  padding: 0 8px;
  color: var(--text-subtle);
  font-size: var(--font-body);
}

.codex-composer-menu-enter-active,
.codex-composer-menu-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.codex-composer-menu-enter-from,
.codex-composer-menu-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(6px) scale(0.99);
}

.codex-composer__box {
  position: relative;
  z-index: 1;
  width: 100%;
  overflow: hidden;
  background: var(--canvas-raised);
  border: 0;
  border-radius: 22px;
  box-shadow:
    0 0 0 1px rgb(0 0 0 / 4%),
    0 2px 8px rgb(0 0 0 / 4%),
    0 4px 40px 8px rgb(0 0 0 / 2.4%);
  corner-shape: superellipse(1.1);
  transition:
    background var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.codex-composer__box:focus-within {
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--label) 9%, transparent),
    0 2px 8px rgb(0 0 0 / 5%),
    0 4px 40px 8px rgb(0 0 0 / 3%);
}

.codex-composer__box--dragging {
  background: color-mix(
    in srgb,
    var(--system-blue) 5%,
    var(--canvas-raised)
  );
  box-shadow:
    0 0 0 1px var(--system-blue),
    0 4px 40px 8px rgb(0 0 0 / 4%);
}

.codex-composer__file-input {
  display: none;
}

.codex-composer-bug-context {
  display: flex;
  min-height: 32px;
  align-items: center;
  gap: 7px;
  margin: 10px 10px 0;
  padding: 5px 5px 5px 9px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: 18px;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: 9px;
}

.codex-composer-bug-context > svg {
  flex: 0 0 auto;
  color: var(--system-orange);
}

.codex-composer-bug-context > span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-composer-bug-context strong {
  margin-right: 4px;
  color: var(--system-blue);
  font-family: var(--font-mono);
  font-weight: var(--weight-emphasis);
}

.codex-composer-note-context {
  background: color-mix(
    in srgb,
    var(--system-blue) 4%,
    var(--canvas-raised)
  );
  border-color: color-mix(
    in srgb,
    var(--system-blue) 12%,
    var(--line-soft)
  );
}

.codex-composer-note-context > svg {
  color: var(--system-blue);
}

.codex-composer-note-context strong {
  font-family: var(--font-sans);
}

.codex-composer-resource-context {
  background: color-mix(in srgb, #14a38b 4%, var(--canvas-raised));
  border-color: color-mix(
    in srgb,
    #14a38b 13%,
    var(--line-soft)
  );
}

.codex-composer-resource-context > svg {
  color: #14a38b;
}

.codex-composer-resource-context strong {
  color: #14a38b;
  font-family: var(--font-sans);
}

.codex-composer-bug-context small {
  margin-left: 4px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-composer-bug-context > button {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 6px;
  place-items: center;
}

.codex-composer-bug-context > button:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-composer-bug-context > button:disabled {
  opacity: 0.4;
}

.codex-pending-attachments {
  display: flex;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-3) 0;
  flex-wrap: wrap;
}

.codex-pending-attachment {
  display: grid;
  width: min(240px, 100%);
  min-width: 0;
  min-height: 38px;
  grid-template-columns: 28px minmax(0, 1fr) 20px;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 5px;
  color: var(--text-muted);
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.codex-pending-attachment--error {
  border-color: color-mix(
    in srgb,
    var(--system-red) 34%,
    var(--line-soft)
  );
}

.codex-pending-attachment__preview {
  display: grid;
  width: 28px;
  height: 28px;
  overflow: hidden;
  color: var(--icon);
  background: var(--canvas-raised);
  border-radius: var(--radius-badge);
  place-items: center;
}

.codex-pending-attachment__preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.codex-pending-attachment__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.codex-pending-attachment__copy strong,
.codex-pending-attachment__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-pending-attachment__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.codex-pending-attachment__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-pending-attachment--error
  .codex-pending-attachment__copy
  small {
  color: var(--system-red);
}

.codex-pending-attachment > svg {
  color: var(--text-subtle);
}

.codex-pending-attachment > button {
  display: grid;
  width: 20px;
  height: 20px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.codex-pending-attachment > button:hover {
  color: var(--system-red);
  background: var(--hover-bg);
}

.codex-composer textarea {
  display: block;
  width: 100%;
  min-height: 46px;
  max-height: 192px;
  padding: 14px 16px 2px;
  color: var(--text);
  font-size: 14px;
  line-height: 20px;
  resize: none;
  background: transparent;
  border: 0;
}

.codex-composer textarea::placeholder {
  color: var(--text-subtle);
}

.codex-composer textarea:focus-visible {
  outline: none;
}

.codex-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 10px 10px;
  flex-wrap: wrap;
}

.codex-composer__leading {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;
}

.codex-composer-add-trigger {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: 50%;
  place-items: center;
}

.codex-composer-add-trigger:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-composer-add-trigger:disabled {
  opacity: 0.35;
}

.codex-composer-access {
  display: inline-flex;
  min-width: 0;
  height: 28px;
  align-items: center;
  gap: 5px;
  padding: 0 6px;
  color: var(--system-orange);
  font-size: var(--font-body);
  white-space: nowrap;
  background: transparent;
  border: 0;
  border-radius: 8px;
}

.codex-composer-access > svg {
  flex: 0 0 auto;
}

.codex-composer-access--full {
  color: var(--codex-full-access);
}

.codex-composer-access:hover:not(:disabled),
.codex-composer-access[aria-expanded="true"] {
  background: color-mix(
    in srgb,
    var(--system-orange) 7%,
    transparent
  );
}

.codex-composer-access--full:hover:not(:disabled),
.codex-composer-access--full[aria-expanded="true"] {
  background: color-mix(
    in srgb,
    var(--codex-full-access) 8%,
    transparent
  );
}

.codex-composer-access:disabled {
  opacity: 0.5;
}

.codex-composer__actions {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}

.codex-run-config {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 2px;
}

.codex-run-trigger {
  display: inline-flex;
  width: min(220px, 60vw);
  height: 28px;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 0 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
  border-radius: 8px;
  box-shadow: none;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.codex-run-trigger:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-run-trigger[aria-expanded="true"],
.codex-run-trigger:focus-visible {
  color: var(--text);
  background: var(--hover-bg);
  outline: none;
  box-shadow: none;
}

.codex-run-trigger:disabled {
  opacity: 0.45;
}

.codex-run-trigger__model {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-run-trigger__effort {
  flex: 0 0 auto;
  color: var(--text-subtle);
  white-space: nowrap;
}

.codex-run-trigger > svg {
  flex: 0 0 auto;
  color: var(--text-subtle);
}

.codex-run-settings {
  position: absolute;
  z-index: 20;
  right: clamp(18px, calc(100% - 348px), 46px);
  bottom: 48px;
  width: min(330px, calc(100% - 36px));
  padding: 15px 16px 16px;
  background: var(--canvas-raised);
  border: 0;
  border-radius: 16px;
  box-shadow:
    0 0 0 1px rgb(0 0 0 / 3%),
    0 16px 44px rgb(0 0 0 / 13%),
    0 3px 12px rgb(0 0 0 / 6%);
  corner-shape: superellipse(1.1);
}

.codex-run-settings > strong {
  display: block;
  color: var(--codex-reasoning-blue);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: 20px;
  text-align: center;
}

.codex-run-settings__model {
  display: flex;
  width: 100%;
  height: 28px;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0 8px;
  color: var(--text-muted);
  font-size: var(--font-body);
  background: transparent;
  border-radius: 7px;
}

.codex-run-settings__model:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.codex-run-settings__model > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-run-settings__models {
  display: flex;
  max-height: 140px;
  margin: 4px 0 8px;
  padding: 4px;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
  background: var(--surface-secondary);
  border-radius: 8px;
}

.codex-run-settings__models > button {
  display: flex;
  min-height: 30px;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-align: left;
  background: transparent;
  border-radius: 6px;
}

.codex-run-settings__models > button:hover,
.codex-run-settings__models > button[aria-selected="true"] {
  color: var(--text);
  background: var(--canvas-raised);
}

.codex-run-settings__models > button > span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.codex-run-settings__models > button > svg {
  flex: 0 0 auto;
  color: var(--codex-reasoning-blue);
}

.codex-effort-slider {
  position: relative;
  height: 28px;
  margin-top: 10px;
}

.codex-effort-slider > input {
  width: 100%;
  height: 28px;
  margin: 0;
  padding: 0;
  appearance: none;
  background: linear-gradient(
    to right,
    var(--codex-reasoning-blue) 0 var(--effort-progress),
    color-mix(in srgb, var(--codex-reasoning-blue) 24%, transparent)
      var(--effort-progress) 100%
  );
  border: 0;
  border-radius: 999px;
  outline: none;
}

.codex-effort-slider > input::-webkit-slider-runnable-track {
  height: 28px;
  background: transparent;
}

.codex-effort-slider > input::-webkit-slider-thumb {
  width: 28px;
  height: 28px;
  appearance: none;
  background: var(--canvas-raised);
  border: 1px solid rgb(0 0 0 / 12%);
  border-radius: 50%;
  box-shadow: 0 1px 3px rgb(0 0 0 / 14%);
}

.codex-effort-slider > input:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--system-blue) 18%, transparent);
}

.codex-effort-slider > input:disabled {
  opacity: 0.6;
}

.codex-effort-slider__markers {
  position: absolute;
  top: 0;
  right: 14px;
  bottom: 0;
  left: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: none;
}

.codex-effort-slider__markers > i {
  width: 4px;
  height: 4px;
  background: rgb(255 255 255 / 36%);
  border-radius: 50%;
}

.codex-effort-slider__markers > i.active {
  background: rgb(255 255 255 / 52%);
}

.codex-run-settings-enter-active,
.codex-run-settings-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
  transform-origin: right bottom;
}

.codex-run-settings-enter-from,
.codex-run-settings-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.98);
}

.codex-access-settings {
  position: absolute;
  z-index: 20;
  bottom: calc(100% + 8px);
  left: clamp(18px, calc(100% - 388px), 44px);
  width: min(370px, calc(100% - 36px));
  padding: 9px;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  box-shadow:
    0 0 0 1px rgb(0 0 0 / 3%),
    0 16px 44px rgb(0 0 0 / 13%),
    0 3px 12px rgb(0 0 0 / 6%);
  corner-shape: superellipse(1.1);
}

.codex-access-settings > header {
  padding: 6px 8px 9px;
  color: var(--text-muted);
  font-size: var(--font-body);
}

.codex-access-options {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.codex-access-options > button {
  display: grid;
  width: 100%;
  min-height: 54px;
  grid-template-columns: 24px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 9px;
  padding: 7px 9px;
  color: var(--text);
  text-align: left;
  background: transparent;
  border-radius: 10px;
}

.codex-access-options > button:hover:not(:disabled) {
  background: var(--hover-bg);
}

.codex-access-options > button:disabled {
  opacity: 0.45;
}

.codex-access-option__icon {
  display: grid;
  color: var(--text-muted);
  place-items: center;
}

.codex-access-option__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.codex-access-option__copy strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: 18px;
}

.codex-access-option__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: 16px;
}

.codex-access-option__check {
  color: var(--system-blue);
}

.codex-access-options
  > .codex-access-option--full
  .codex-access-option__icon {
  color: var(--codex-full-access);
}

.codex-access-options
  > .codex-access-option--full.codex-access-option--selected {
  color: var(--codex-full-access);
  background: var(--surface-inset);
}

.codex-access-options
  > .codex-access-option--full.codex-access-option--selected
  .codex-access-option__copy
  strong,
.codex-access-options
  > .codex-access-option--full.codex-access-option--selected
  .codex-access-option__check {
  color: var(--codex-full-access);
}

.codex-access-settings-enter-active,
.codex-access-settings-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
  transform-origin: left bottom;
}

.codex-access-settings-enter-from,
.codex-access-settings-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.98);
}

.codex-model-loading {
  display: inline-flex;
  height: 28px;
  align-items: center;
  padding: 0 7px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.codex-composer__actions > button {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--codex-send-foreground);
  background: var(--codex-send-surface);
  border-radius: 50%;
  place-items: center;
  transition:
    filter var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.codex-composer__actions > button:hover:not(:disabled) {
  filter: brightness(0.9);
}

.codex-composer__actions > button:disabled {
  color: var(--text-subtle);
  background: var(--segmented-track);
}

.codex-task-stats {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: 0 var(--space-4) var(--space-4);
}

.codex-task-stats span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  flex-direction: row-reverse;
}

.codex-task-stats strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.codex-task-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding: var(--space-1) var(--space-3) var(--space-3);
  overflow-y: auto;
}

.codex-task {
  padding: var(--space-3);
  background: var(--canvas-raised);
  border-radius: 8px;
  box-shadow: var(--shadow-panel);
}

.codex-task header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
}

.codex-task header > svg {
  color: var(--codex-task-status-color, var(--text-muted));
}

.codex-task__header--running {
  --codex-task-status-color: var(--system-blue);
}

.codex-task__header--queued {
  --codex-task-status-color: var(--system-orange);
}

.codex-task__header--completed {
  --codex-task-status-color: var(--system-green);
}

.codex-task__header--failed {
  --codex-task-status-color: var(--system-red);
}

.codex-task__header--interrupted {
  --codex-task-status-color: var(--system-orange);
}

.codex-task header strong {
  flex: 1;
  color: var(
    --codex-task-status-color,
    var(--text-muted)
  );
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.codex-task header time {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.codex-task > p {
  display: -webkit-box;
  margin: 10px 0 12px;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-caption);
  line-height: var(--line-body);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.codex-task > div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.codex-task > div .codex-task-stat,
.codex-task > div .codex-zentao-status {
  --codex-task-stat-color: var(--system-blue);

  display: inline-flex;
  align-items: center;
  padding: 3px 6px;
  color: var(--codex-task-stat-color);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: color-mix(
    in srgb,
    var(--codex-task-stat-color) 10%,
    transparent
  );
  border-radius: var(--radius-badge);
}

.codex-task > div .codex-task-stat--items {
  --codex-task-stat-color: var(--system-blue);
}

.codex-task > div .codex-task-stat--commands {
  --codex-task-stat-color: var(--system-green);
}

.codex-task > div .codex-task-stat--files {
  --codex-task-stat-color: var(--system-orange);
}

.codex-task > div .codex-task-stat--duration {
  --codex-task-stat-color: color-mix(
    in srgb,
    var(--system-blue) 55%,
    var(--system-red)
  );
}

.codex-task > div .codex-task-stat--tokens {
  --codex-task-stat-color: color-mix(
    in srgb,
    var(--system-blue) 70%,
    var(--system-green)
  );
}

.codex-task > div .codex-task-stat--bugs {
  --codex-task-stat-color: var(--system-red);
}

.codex-task > div .codex-zentao-status {
  --codex-task-stat-color: var(--system-orange);
}

.codex-task > div .codex-zentao-status--success {
  --codex-task-stat-color: var(--system-green);
}

.codex-task > div .codex-zentao-status--error {
  --codex-task-stat-color: var(--system-red);
}

.codex-task > div .codex-zentao-retry {
  cursor: pointer;
}

.codex-task > div .codex-zentao-retry:hover {
  background: color-mix(in srgb, var(--system-red) 14%, transparent);
}

.codex-mobile-panel-button,
.codex-mobile-scrim {
  display: none;
}

.codex-spin {
  animation: codex-spin 1s linear infinite;
}

@keyframes codex-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes codex-cursor {
  50% {
    opacity: 0;
  }
}

@keyframes codex-live-pulse {
  0%,
  100% {
    opacity: 0.35;
  }

  50% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .codex-turn-meta__dot {
    animation: none;
  }
}

@media (hover: none) {
  .codex-project-section__create {
    pointer-events: auto;
    opacity: 0.72;
  }
}

@media (max-width: 1100px) {
  .codex-workspace {
    grid-template-columns: 296px minmax(0, 1fr);
  }

  .codex-tasks {
    position: absolute;
    z-index: 12;
    top: 0;
    right: 0;
    bottom: 0;
    width: min(88vw, 310px);
    border-left: 1px solid var(--line);
    box-shadow: var(--shadow-popover);
    transform: translateX(102%);
    transition: transform var(--duration-window) var(--ease-apple);
  }

  .codex-tasks--open {
    transform: translateX(0);
  }

  .codex-mobile-scrim {
    position: absolute;
    z-index: 11;
    inset: 0;
    display: block;
    padding: 0;
    background: rgb(0 0 0 / 18%);
    border-radius: inherit;
  }
}

@media (max-width: 760px) {
  .codex-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .codex-composer-project__clear::after {
    left: -4px;
    transform: translateY(3px);
  }

  .codex-composer-project__clear:hover::after,
  .codex-composer-project__clear:focus-visible::after {
    transform: translateY(0);
  }

  .codex-sessions {
    position: absolute;
    z-index: 12;
    top: 0;
    bottom: 0;
    left: 0;
    width: min(82vw, 260px);
    border-right: 1px solid var(--line);
    box-shadow: var(--shadow-popover);
    transform: translateX(-102%);
    transition: transform var(--duration-window) var(--ease-apple);
  }

  .codex-main__header--new {
    display: flex;
    min-height: 44px;
    padding: 8px 12px;
    border-bottom: 0;
  }

  .codex-main__header--new .codex-main__heading > div,
  .codex-main__header--new .codex-main__actions {
    display: none;
  }

  .codex-mobile-panel-button,
  .codex-sessions__actions > .codex-mobile-panel-button {
    display: grid;
    width: 26px;
    height: 26px;
    padding: 0;
    color: var(--text-muted);
    background: var(--control-bg);
    border-radius: var(--radius-control);
    box-shadow: var(--control-shadow);
    place-items: center;
  }

  .codex-sessions--open {
    transform: translateX(0);
  }

  .codex-session-actions {
    opacity: 0.72;
  }

  .codex-main__header {
    padding: var(--space-2) var(--space-3);
  }

  .codex-task-trigger span {
    display: none;
  }

  .codex-task-trigger {
    width: auto;
    padding: 0 var(--space-1);
  }

  .codex-usage-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .codex-usage-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .codex-usage-grid article {
    min-height: 88px;
  }

  .codex-usage-breakdown {
    grid-template-columns: 1fr 1fr;
  }

  .codex-transcript {
    padding: var(--space-4) var(--space-3);
  }

  .codex-user-turn {
    max-width: 88%;
  }

  .codex-composer {
    padding: var(--space-3);
  }

  .codex-composer-bug-picker {
    width: 100%;
  }

  .codex-composer__footer {
    align-items: center;
  }

  .codex-run-config :deep(.ui-select) {
    max-width: min(190px, 54vw);
  }

  .codex-run-config :deep(.ui-select:last-child) {
    max-width: 86px;
  }

  .codex-pending-attachment {
    width: 100%;
  }

  .codex-unavailable {
    align-items: flex-start;
  }

  .codex-unavailable div {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }
}
</style>
