<script setup lang="ts">
import {
  AppWindow,
  ArrowLeftRight,
  ArrowUpRight,
  Bell,
  Boxes,
  Bug,
  CalendarDays,
  Check,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  Clock3,
  Coffee,
  Code2,
  Database,
  FolderKanban,
  FolderCode,
  GitBranch,
  Globe2,
  Home,
  Inbox,
  KeyRound,
  LogIn,
  LogOut,
  Mail,
  Menu,
  NotebookPen,
  PanelLeft,
  Pause,
  Play,
  Plus,
  Palette,
  RefreshCw,
  Rocket,
  RotateCcw,
  Search,
  Server,
  Settings,
  UserRound,
  X,
} from '@lucide/vue'
import type { Component } from 'vue'
import {
  computed,
  defineAsyncComponent,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCodexStatus } from '@/api/codex'
import { getCcSwitchUsage } from '@/api/ccSwitch'
import {
  getUnreadNotificationCount,
  listCalendarNotifications,
  markAllCalendarNotificationsRead,
  markCalendarNotificationRead,
} from '@/api/calendar'
import { getSystemHealth } from '@/api/system'
import { prepareZentaoTestRun } from '@/api/zentao'
import ccSwitchIcon from '@/assets/integrations/cc-switch.png'
import codexIcon from '@/assets/integrations/codex.png'
import mysqlIcon from '@/assets/integrations/mysql.svg'
import sogouInputIcon from '@/assets/integrations/sogou-input.png'
import {
  PRODUCT_NAME,
  PRODUCT_TAGLINE,
  PRODUCT_VERSION_LABEL,
} from '@/brand'
import AppTitlebar from '@/components/business/AppTitlebar.vue'
import ApplicationLibrary from '@/components/business/applications/ApplicationLibrary.vue'
import DeploymentLibrary from '@/components/business/development/DeploymentLibrary.vue'
import GitRepositoryLibrary from '@/components/business/development/GitRepositoryLibrary.vue'
import ServerManagement from '@/components/business/development/ServerManagement.vue'
import WorkDirectoryLibrary from '@/components/business/development/WorkDirectoryLibrary.vue'
import ZentaoBugLibrary from '@/components/business/development/ZentaoBugLibrary.vue'
import MailWorkspace from '@/components/business/mail/MailWorkspace.vue'
import ProfileEditor from '@/components/business/ProfileEditor.vue'
import CodexIdentitySettings from '@/components/business/settings/CodexIdentitySettings.vue'
import StyleGuidePanel from '@/components/business/settings/StyleGuidePanel.vue'
import TaskRow from '@/components/business/TaskRow.vue'
import WebsiteLibrary from '@/components/business/websites/WebsiteLibrary.vue'
import WorkbenchHome from '@/components/business/workbench/WorkbenchHome.vue'
import WellnessReminderBar from '@/components/business/workbench/WellnessReminderBar.vue'
import WorkbenchWellnessPanel from '@/components/business/workbench/WorkbenchWellnessPanel.vue'
import WellnessRestOverlay from '@/components/business/workbench/WellnessRestOverlay.vue'
import WorkspaceSwitcher from '@/components/business/workspaces/WorkspaceSwitcher.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import CodexMark from '@/components/ui/CodexMark.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { useActivityTracker } from '@/composables/useActivityTracker'
import { usePagination } from '@/composables/usePagination'
import {
  DEFAULT_CODEX_ASSISTANT_NAME,
  loadUserWorkbenchData,
  loadUserWorkbenchSettings,
  saveUserWorkbenchData,
  saveUserWorkbenchSettings,
} from '@/services/userWorkbench'
import { useAuthStore } from '@/stores/useAuthStore'
import { useActivityStore } from '@/stores/useActivityStore'
import { useCodexStore } from '@/stores/useCodexStore'
import { useDatabaseStore } from '@/stores/useDatabaseStore'
import { useDeploymentStore } from '@/stores/useDeploymentStore'
import { useGitRepositoryStore } from '@/stores/useGitRepositoryStore'
import { useManagedServerStore } from '@/stores/useManagedServerStore'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useWorkDirectoryStore } from '@/stores/useWorkDirectoryStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { useWorkNoteStore } from '@/stores/useWorkNoteStore'
import { useWellnessStore } from '@/stores/useWellnessStore'
import { useZentaoStore } from '@/stores/useZentaoStore'
import {
  useThemeStore,
  type FontPreference,
  type ThemePreference,
} from '@/stores/theme'
import type {
  WorkNote,
  WorkProject,
  WorkTask,
} from '@/types/workbench'
import type { WorkNote as StoredWorkNote } from '@/types/workNote'
import type { CalendarNotification } from '@/types/calendar'
import type { ZentaoBug } from '@/types/zentao'
import { openWebsiteUrl } from '@/utils/openWebsite'
import {
  formatWellnessDuration,
  localDateKey,
  wellnessDaySummary,
} from '@/utils/wellness'

type ViewId =
  | 'overview'
  | 'websites'
  | 'codex'
  | 'apps'
  | 'workDirectories'
  | 'gitRepositories'
  | 'databases'
  | 'deployments'
  | 'zentaoBugs'
  | 'servers'
  | 'tasks'
  | 'notes'
  | 'vault'
  | 'mail'
  | 'calendar'
  | 'projects'
  | 'profile'
  | 'settings'

type TaskFilter = 'all' | 'open' | 'done'
type DatabaseStatus =
  | 'checking'
  | 'connected'
  | 'disconnected'
  | 'offline'
type LocalIntegrationStatus =
  | 'checking'
  | 'ready'
  | 'missing'
  | 'unavailable'
  | 'offline'

interface NavigationItem {
  id: ViewId
  label: string
  icon: Component
}

type GlobalSearchKind =
  | 'view'
  | 'website'
  | 'application'
  | 'task'
  | 'workbenchNote'
  | 'project'
  | 'note'
  | 'codexSession'
  | 'workDirectory'
  | 'git'
  | 'database'
  | 'deployment'
  | 'bug'
  | 'server'

interface GlobalSearchResult {
  key: string
  kind: GlobalSearchKind
  targetId: string
  targetView: ViewId
  group: string
  title: string
  subtitle: string
  icon: Component
}

const viewIds: ViewId[] = [
  'overview',
  'websites',
  'codex',
  'apps',
  'workDirectories',
  'gitRepositories',
  'databases',
  'deployments',
  'zentaoBugs',
  'servers',
  'tasks',
  'notes',
  'vault',
  'mail',
  'calendar',
  'projects',
  'profile',
  'settings',
]

const route = useRoute()
const router = useRouter()
const CodexWorkspace = defineAsyncComponent(
  () => import('@/components/business/codex/CodexWorkspace.vue'),
)
const WorkNotes = defineAsyncComponent(
  () => import('@/components/business/notes/WorkNotes.vue'),
)
const CalendarPage = defineAsyncComponent(
  () => import('@/components/business/calendar/CalendarPage.vue'),
)
const DatabaseWorkspace = defineAsyncComponent(
  () => import('@/components/business/development/DatabaseWorkspace.vue'),
)
const WorkVault = defineAsyncComponent(
  () => import('@/components/business/vault/WorkVault.vue'),
)
const session = useAuthStore()
const activityStore = useActivityStore()
const shortcutStore = useShortcutStore()
const workspaceStore = useWorkspaceStore()
const workDirectoryStore = useWorkDirectoryStore()
const gitRepositoryStore = useGitRepositoryStore()
const databaseStore = useDatabaseStore()
const deploymentStore = useDeploymentStore()
const managedServerStore = useManagedServerStore()
const zentaoStore = useZentaoStore()
const codexStore = useCodexStore()
const noteLibraryStore = useWorkNoteStore()
const wellnessStore = useWellnessStore()
const themeStore = useThemeStore()
const now = new Date()
useActivityTracker()

function routeView(): ViewId {
  const value = route.query.view
  return typeof value === 'string' &&
    viewIds.includes(value as ViewId)
    ? (value as ViewId)
    : 'overview'
}

const activeView = ref<ViewId>(routeView())
const mobileNavOpen = ref(false)
const sidebarCollapsed = ref(false)
const profileMenuOpen = ref(false)
const notificationCenterOpen = ref(false)
const notifications = ref<CalendarNotification[]>([])
const unreadNotificationCount = ref(0)
const notificationsLoading = ref(false)
const searchQuery = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const taskComposer = ref<HTMLInputElement | null>(null)
const newTaskTitle = ref('')
const notesWorkspace = ref<{ startComposer: () => void } | null>(null)
const notesQuickCapturePending = ref(false)
const taskFilter = ref<TaskFilter>('all')
const launchOnLogin = ref(true)
const compactSidebar = ref(false)
const codexDisplayName = ref(DEFAULT_CODEX_ASSISTANT_NAME)
const codexIconDataUrl = ref<string | null>(null)
const databaseStatus = ref<DatabaseStatus>('checking')
const codexStatus = ref<LocalIntegrationStatus>('checking')
const codexDetail = ref('正在检测本地 Codex CLI')
const ccSwitchStatus = ref<LocalIntegrationStatus>('checking')
const ccSwitchDetail = ref('正在检测本地 CC Switch')
const styleGuideOpen = ref(false)
const codexMounted = ref(activeView.value === 'codex')
const serverManagementMounted = ref(activeView.value === 'servers')
const developmentViews = new Set<ViewId>([
  'workDirectories',
  'gitRepositories',
  'databases',
  'deployments',
  'zentaoBugs',
  'servers',
])
const dailyViews = new Set<ViewId>(['codex', 'notes', 'calendar', 'mail'])
const resourceViews = new Set<ViewId>(['websites', 'apps', 'vault'])
const dailyMenuOpen = ref(true)
const developmentMenuOpen = ref(false)
const resourceMenuOpen = ref(true)
const toastMessage = ref('')
const defaultTasks: WorkTask[] = [
  {
    id: 1,
    title: '梳理本周产品优先级',
    project: PRODUCT_NAME,
    time: '09:30',
    done: false,
    priority: 'high',
  },
  {
    id: 2,
    title: '回复团队评审意见',
    project: '工作沟通',
    time: '11:20',
    done: false,
    priority: 'medium',
  },
  {
    id: 3,
    title: '整理客户需求记录',
    project: '需求池',
    time: '14:00',
    done: false,
    priority: 'high',
  },
  {
    id: 4,
    title: '完成首页交互细节',
    project: PRODUCT_NAME,
    time: '16:30',
    done: true,
    priority: 'medium',
  },
  {
    id: 5,
    title: '准备明日周会材料',
    project: '工作沟通',
    time: '17:30',
    done: false,
    priority: 'low',
  },
]
const defaultNotes: WorkNote[] = [
  {
    id: 1,
    title: '设计评审随手记',
    excerpt: '主区域保持安静，减少容器层级；导航和窗口控件沿用 macOS 的空间关系。',
    updatedAt: '12 分钟前',
    tone: 'sage',
  },
  {
    id: 2,
    title: '下一步实现顺序',
    excerpt: '登录校验 → 数据库连接 → 任务模块 → 今日聚合 → 全局搜索。',
    updatedAt: '今天 09:18',
    tone: 'sand',
  },
  {
    id: 3,
    title: '产品想法',
    excerpt: '快速记录不需要切换到笔记页面，写完即可回到当前任务。',
    updatedAt: '昨天',
    tone: 'rose',
  },
]
const defaultProjects: WorkProject[] = [
  {
    id: 1,
    name: `${PRODUCT_NAME} 1.0`,
    description: '个人工作台',
    progress: 62,
    color: 'green',
  },
  {
    id: 2,
    name: '知识库整理',
    description: '归纳与归档',
    progress: 28,
    color: 'coral',
  },
  {
    id: 3,
    name: '个人自动化',
    description: '快捷工作流',
    progress: 14,
    color: 'amber',
  },
]
const tasks = ref<WorkTask[]>(
  defaultTasks.map((task) => ({ ...task })),
)
const notes = ref<WorkNote[]>(
  defaultNotes.map((note) => ({ ...note })),
)
const projects = ref<WorkProject[]>(
  defaultProjects.map((project) => ({ ...project })),
)
const {
  currentPage: projectPage,
  pageItems: pagedProjects,
  setPage: setProjectPage,
} = usePagination(projects)
const navigationPrimary: NavigationItem[] = [
  { id: 'overview', label: '工作台', icon: Home },
]
const dailyNavigation: NavigationItem[] = [
  { id: 'codex', label: 'Codex', icon: CodexMark },
  { id: 'notes', label: '速记', icon: NotebookPen },
  { id: 'calendar', label: '日历', icon: CalendarDays },
  { id: 'mail', label: '邮件', icon: Mail },
]
const developmentNavigation: NavigationItem[] = [
  { id: 'workDirectories', label: '工作目录', icon: FolderCode },
  { id: 'gitRepositories', label: 'Git 仓库', icon: GitBranch },
  { id: 'databases', label: '数据库', icon: Database },
  { id: 'servers', label: '服务器管理', icon: Server },
  { id: 'deployments', label: '部署', icon: Rocket },
  { id: 'zentaoBugs', label: 'Bug 清单', icon: Bug },
]
const resourceNavigation: NavigationItem[] = [
  { id: 'websites', label: '网址', icon: Globe2 },
  { id: 'apps', label: 'App', icon: AppWindow },
  { id: 'vault', label: '密码库', icon: KeyRound },
]

const themeOptions: Array<{ value: ThemePreference; label: string }> = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' },
]
const fontOptions: Array<{ value: FontPreference; label: string }> = [
  { value: 'system', label: '系统' },
  { value: 'codex', label: 'Codex' },
  { value: 'avenir', label: 'Avenir' },
]
const taskFilterOptions: Array<{ value: TaskFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'open', label: '待完成' },
  { value: 'done', label: '已完成' },
]
const databaseStatusText = computed(() => {
  if (databaseStatus.value === 'connected') return '已连接'
  if (databaseStatus.value === 'disconnected') return '数据库连接失败'
  if (databaseStatus.value === 'offline') return '服务未启动'
  return '检测中'
})

const sogouStatus = computed<LocalIntegrationStatus>(() => {
  if (activityStore.sogou.loading) return 'checking'
  if (activityStore.sogou.serviceAvailable) return 'ready'
  if (activityStore.sogou.installed) return 'missing'
  return 'missing'
})

const sogouStatusText = computed(() => {
  if (activityStore.sogou.loading) return '检测中'
  if (activityStore.sogou.serviceAvailable) return '统计可用'
  if (activityStore.sogou.installed) return '服务未运行'
  return activityStore.sogou.checkedAt ? '未安装' : '未检测'
})

const sogouDetail = computed(() => {
  if (activityStore.sogou.loading) {
    return '正在检查本机搜狗输入法统计服务'
  }
  if (activityStore.sogou.serviceAvailable) {
    return `全系统字数与速度 · 当前 ${activityStore.sogou.currentSpeed} 字/分`
  }
  if (activityStore.sogou.message) {
    return activityStore.sogou.message
  }
  return '全系统字数与速度来源；不可用时自动回退到页面内计数'
})
const databaseStatusClass = computed(() => ({
  'setting-status--success': databaseStatus.value === 'connected',
  'setting-status--neutral':
    databaseStatus.value === 'checking' ||
    databaseStatus.value === 'offline',
}))
const codexStatusText = computed(() =>
  localIntegrationStatusText(codexStatus.value),
)
const ccSwitchStatusText = computed(() =>
  localIntegrationStatusText(ccSwitchStatus.value),
)

function localIntegrationStatusText(status: LocalIntegrationStatus) {
  if (status === 'ready') return '已就绪'
  if (status === 'missing') return '未检测到'
  if (status === 'unavailable') return '桌面版可用'
  if (status === 'offline') return '服务未启动'
  return '检测中'
}

function localIntegrationStatusClass(status: LocalIntegrationStatus) {
  return {
    'setting-status--success': status === 'ready',
    'setting-status--neutral':
      status === 'checking' ||
      status === 'unavailable' ||
      status === 'offline',
  }
}

const viewMeta: Record<
  ViewId,
  { label: string; description: string }
> = {
  overview: {
    label: '工作台',
    description: '时间、常用网址和本地软件集中在这里。',
  },
  websites: {
    label: '网址',
    description: '按分类整理网址，工作台会展示点击量最高的 Top 10。',
  },
  codex: {
    label: 'Codex',
    description: '与本地 Codex 对话，并记录每个会话执行的任务。',
  },
  apps: {
    label: 'App',
    description: '集中管理本地应用，工作台会展示使用频率最高的 Top 5。',
  },
  workDirectories: {
    label: '工作目录',
    description: '集中管理日常项目路径，快速回到正在处理的工作。',
  },
  gitRepositories: {
    label: 'Git 仓库',
    description: '集中查看本地仓库的分支、远端、提交和工作区状态。',
  },
  databases: {
    label: '数据库',
    description: '集中管理 MySQL 和 ClickHouse 连接，执行 SQL 并查看结果。',
  },
  deployments: {
    label: '部署',
    description: '保存部署脚本，在研发服务器上执行并查看运行结果。',
  },
  zentaoBugs: {
    label: 'Bug 清单',
    description:
      '从禅道同步指定项目的 Bug，可查看详情、在平台内标记解决或交给 Codex。',
  },
  servers: {
    label: '服务器管理',
    description: `集中保存研发服务器，在${PRODUCT_NAME}内连接 SSH 终端和管理 SFTP 文件。`,
  },
  tasks: {
    label: '任务',
    description: '整理、推进并完成今天的工作。',
  },
  notes: {
    label: '速记',
    description: '快速记录工作中的重要事情、决定和结论。',
  },
  vault: {
    label: '密码库',
    description: '集中管理账号密码，验证后 5 分钟内可以查看。',
  },
  mail: {
    label: '邮件',
    description: '在一个安静的工作区里收取、阅读并回复邮件。',
  },
  calendar: {
    label: '日历',
    description: '按月查看速记和日程，快速安排每天的工作。',
  },
  projects: {
    label: '项目',
    description: '从更长的周期观察工作进展。',
  },
  profile: {
    label: '个人资料',
    description: '管理头像、昵称和只属于你的工作台信息。',
  },
  settings: {
    label: '设置',
    description: '配置你的本地工作台。',
  },
}

const codexAssistantName = computed(
  () =>
    codexDisplayName.value.trim() ||
    DEFAULT_CODEX_ASSISTANT_NAME,
)
const displayName = computed(() => {
  const name = session.userName.trim() || 'Yong'
  return name.charAt(0).toUpperCase() + name.slice(1)
})
const displayInitial = computed(() => displayName.value.charAt(0).toUpperCase())
const accountLabel = computed(() => session.email || '本地账号')
const greeting = computed(() => {
  const hour = now.getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const dateLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(now),
)
const currentView = computed(() => viewMeta[activeView.value])
const pageTitle = computed(() =>
  activeView.value === 'overview'
    ? `${greeting.value}，${displayName.value}`
    : currentView.value.label,
)
const pageDescription = computed(() =>
  activeView.value === 'overview'
    ? `今天是 ${dateLabel.value}，从这里开始今天的工作。`
    : currentView.value.description,
)
const calendarDateQuery = computed(() => {
  const value = route.query.date
  return typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? value
    : ''
})

function setTaskFilter(value: string) {
  taskFilter.value = value as TaskFilter
}

function setThemePreference(value: string) {
  themeStore.setPreference(value as ThemePreference)
}
function setFontPreference(value: string) {
  themeStore.setFontPreference(value as FontPreference)
}
const filteredTasks = computed(() => {
  if (taskFilter.value === 'open') {
    return tasks.value.filter((task) => !task.done)
  }

  if (taskFilter.value === 'done') {
    return tasks.value.filter((task) => task.done)
  }

  return tasks.value
})
const {
  currentPage: taskPage,
  pageItems: pagedTasks,
  setPage: setTaskPage,
} = usePagination(filteredTasks, () => taskFilter.value)

const viewSearchIcons: Record<ViewId, Component> = {
  overview: Home,
  codex: CodexMark,
  websites: Globe2,
  apps: AppWindow,
  workDirectories: FolderCode,
  gitRepositories: GitBranch,
  databases: Database,
  deployments: Rocket,
  zentaoBugs: Bug,
  servers: Server,
  tasks: CheckCheck,
  mail: Mail,
  notes: NotebookPen,
  vault: KeyRound,
  calendar: CalendarDays,
  projects: FolderKanban,
  profile: UserRound,
  settings: Settings,
}

const searchResults = computed<GlobalSearchResult[]>(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return []
  const matches = (...values: Array<string | number | null | undefined>) =>
    values
      .filter((value) => value !== null && value !== undefined)
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query)
  const workspaceId = workspaceStore.activeWorkspaceId

  const pages = (
    Object.entries(viewMeta) as Array<
      [ViewId, (typeof viewMeta)[ViewId]]
    >
  )
    .filter(([id, meta]) => matches(id, meta.label, meta.description))
    .slice(0, 5)
    .map<GlobalSearchResult>(([id, meta]) => ({
      key: `view:${id}`,
      kind: 'view',
      targetId: id,
      targetView: id,
      group: '页面',
      title: meta.label,
      subtitle: meta.description,
      icon: viewSearchIcons[id],
    }))

  const websites = shortcutStore.websites
    .filter((website) => website.workspaceId === workspaceId)
    .filter((website) =>
      matches(website.name, website.url, website.category),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((website) => ({
      key: `website:${website.id}`,
      kind: 'website',
      targetId: website.id,
      targetView: 'websites',
      group: '网址',
      title: website.name,
      subtitle: `${website.category} · ${website.url}`,
      icon: Globe2,
    }))

  const applications = shortcutStore.applications
    .filter((application) => application.workspaceId === workspaceId)
    .filter((application) => matches(application.name, application.path))
    .slice(0, 3)
    .map<GlobalSearchResult>((application) => ({
      key: `application:${application.id}`,
      kind: 'application',
      targetId: application.id,
      targetView: 'apps',
      group: 'App',
      title: application.name,
      subtitle: application.path,
      icon: AppWindow,
    }))

  const workTasks = tasks.value
    .filter((task) => matches(task.title, task.project))
    .slice(0, 3)
    .map<GlobalSearchResult>((task) => ({
      key: `task:${task.id}`,
      kind: 'task',
      targetId: String(task.id),
      targetView: 'tasks',
      group: '任务',
      title: task.title,
      subtitle: task.project,
      icon: Check,
    }))

  const workbenchNotes = notes.value
    .filter((note) => matches(note.title, note.excerpt))
    .slice(0, 3)
    .map<GlobalSearchResult>((note) => ({
      key: `workbench-note:${note.id}`,
      kind: 'workbenchNote',
      targetId: String(note.id),
      targetView: 'notes',
      group: '速记',
      title: note.title,
      subtitle: note.excerpt,
      icon: NotebookPen,
    }))

  const workProjects = projects.value
    .filter((project) => matches(project.name, project.description))
    .slice(0, 3)
    .map<GlobalSearchResult>((project) => ({
      key: `project:${project.id}`,
      kind: 'project',
      targetId: String(project.id),
      targetView: 'projects',
      group: '项目',
      title: project.name,
      subtitle: project.description,
      icon: FolderKanban,
    }))

  const libraryNotes = noteLibraryStore.notes
    .filter((note) => note.workspaceId === workspaceId)
    .filter((note) =>
      matches(note.title, note.content, note.tags.join(' ')),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((note) => ({
      key: `note:${note.id}`,
      kind: 'note',
      targetId: note.id,
      targetView: 'notes',
      group: '速记',
      title: note.title,
      subtitle: note.content.replace(/\s+/g, ' ').trim().slice(0, 120),
      icon: NotebookPen,
    }))

  const codexSessions = codexStore.sessions
    .filter((item) => item.workspaceId === workspaceId)
    .filter((item) =>
      matches(
        item.title,
        item.cwd,
        item.tasks.map((task) => task.displayPrompt || task.prompt).join(' '),
      ),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((item) => ({
      key: `codex:${item.id}`,
      kind: 'codexSession',
      targetId: item.id,
      targetView: 'codex',
      group: 'Codex',
      title: item.title,
      subtitle: `${item.tasks.length} 个任务 · ${item.cwd || '未关联目录'}`,
      icon: CodexMark,
    }))

  const workDirectories = workDirectoryStore.directories
    .filter((directory) => directory.workspaceId === workspaceId)
    .filter((directory) =>
      matches(
        directory.name,
        directory.path,
        directory.category,
        directory.description,
      ),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((directory) => ({
      key: `directory:${directory.id}`,
      kind: 'workDirectory',
      targetId: directory.id,
      targetView: 'workDirectories',
      group: '工作目录',
      title: directory.name,
      subtitle: `${directory.category} · ${directory.path}`,
      icon: FolderCode,
    }))

  const gitRepositories = gitRepositoryStore.repositories
    .filter((repository) => repository.workspaceId === workspaceId)
    .filter((repository) =>
      matches(
        repository.name,
        repository.path,
        repository.remoteUrl,
        repository.category,
      ),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((repository) => ({
      key: `git:${repository.id}`,
      kind: 'git',
      targetId: repository.id,
      targetView: 'gitRepositories',
      group: 'Git 仓库',
      title: repository.name,
      subtitle: repository.path || repository.remoteUrl,
      icon: GitBranch,
    }))

  const databases = databaseStore.connections
    .filter((connection) => connection.workspaceId === workspaceId)
    .filter((connection) =>
      matches(
        connection.name,
        connection.type,
        connection.host,
        connection.databaseName,
        connection.username,
      ),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((connection) => ({
      key: `database:${connection.id}`,
      kind: 'database',
      targetId: String(connection.id),
      targetView: 'databases',
      group: '数据库',
      title: connection.name,
      subtitle: `${connection.type} · ${connection.host}:${connection.port}/${connection.databaseName}`,
      icon: Database,
    }))

  const deployments = deploymentStore.deployments
    .filter((deployment) => deployment.workspaceId === workspaceId)
    .filter((deployment) =>
      matches(
        deployment.name,
        deployment.serverName,
        deployment.remoteDirectory,
        deployment.scriptFilename,
      ),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((deployment) => ({
      key: `deployment:${deployment.id}`,
      kind: 'deployment',
      targetId: deployment.id,
      targetView: 'deployments',
      group: '部署',
      title: deployment.name,
      subtitle: `${deployment.serverName || '未关联服务器'} · ${deployment.remoteDirectory}`,
      icon: Rocket,
    }))

  const bugs = zentaoStore.bugs
    .filter((bug) => !bug.localDeleted)
    .filter((bug) =>
      matches(
        bug.remoteId,
        bug.title,
        bug.productName,
        bug.projectName,
        bug.assignedTo,
      ),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((bug) => ({
      key: `bug:${bug.id}`,
      kind: 'bug',
      targetId: String(bug.id),
      targetView: 'zentaoBugs',
      group: 'Bug',
      title: `#${bug.remoteId} ${bug.title}`,
      subtitle: `${bug.productName || bug.projectName || '未分类'} · ${bug.assignedTo || '未指派'}`,
      icon: Bug,
    }))

  const servers = managedServerStore.servers
    .filter((server) => server.workspaceId === workspaceId)
    .filter((server) =>
      matches(server.name, server.host, server.username),
    )
    .slice(0, 3)
    .map<GlobalSearchResult>((server) => ({
      key: `server:${server.id}`,
      kind: 'server',
      targetId: String(server.id),
      targetView: 'servers',
      group: '服务器',
      title: server.name,
      subtitle: `${server.host}:${server.port} · ${server.username}`,
      icon: Server,
    }))

  return [
    ...pages,
    ...websites,
    ...applications,
    ...workTasks,
    ...workbenchNotes,
    ...workProjects,
    ...libraryNotes,
    ...codexSessions,
    ...workDirectories,
    ...gitRepositories,
    ...databases,
    ...deployments,
    ...bugs,
    ...servers,
  ]
})

const todayWorkSession = computed(() => {
  const date = localDateKey(new Date())
  return (
    wellnessStore.data.workSessions
      .filter((work) => localDateKey(work.startedAt) === date)
      .sort(
        (left, right) =>
          new Date(right.startedAt).getTime() -
          new Date(left.startedAt).getTime(),
      )[0] ?? null
  )
})
const clockActionMode = computed(() => {
  if (wellnessStore.data.activeWork) return 'clockOut'
  return todayWorkSession.value ? 'update' : 'clockIn'
})

let toastTimer: number | null = null
let notificationPollTimer: number | null = null
let notificationRequestInFlight = false
let workbenchOwnerEmail = ''

function notify(message: string) {
  toastMessage.value = message
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMessage.value = ''
  }, 2200)
}

function refreshCurrentPage() {
  window.location.reload()
}

function startRestFromFooter() {
  if (!wellnessStore.startRest()) return
  notify('休息计时开始，先离开屏幕一会儿')
}

function clockInFromFooter() {
  if (!wellnessStore.clockIn()) {
    notify('今天已经完成上班打卡，一天只能打卡一次')
    return
  }
  notify('打卡成功，已开始记录工作时间')
}

function clockOutFromFooter() {
  if (!wellnessStore.data.activeWork) return
  wellnessStore.clockOut()
  const summary = wellnessDaySummary(
    wellnessStore.data,
    localDateKey(new Date()),
  )
  notify(
    `已下班，今日净工作 ${formatWellnessDuration(summary.focusMs)}`,
  )
}

function updateClockOutFromFooter() {
  if (!todayWorkSession.value) return
  wellnessStore.updateClockOut()
  const summary = wellnessDaySummary(
    wellnessStore.data,
    localDateKey(new Date()),
  )
  const clockOut = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date())
  notify(
    `下班时间已更新为 ${clockOut}，今日净工作 ${formatWellnessDuration(summary.focusMs)}`,
  )
}

function continueRestFromFooter() {
  const duration = wellnessStore.endRest()
  notify(
    duration < 60_000
      ? '已继续工作'
      : `已继续工作，本次休息 ${formatWellnessDuration(duration)}`,
  )
}

async function refreshUnreadNotifications() {
  try {
    const result = await getUnreadNotificationCount()
    unreadNotificationCount.value = result.count
  } catch {
    // Calendar data stays usable while the notification service is offline.
  }
}

async function refreshNotificationCenter() {
  if (notificationRequestInFlight) return
  notificationRequestInFlight = true
  notificationsLoading.value = true
  try {
    const [items, unread] = await Promise.all([
      listCalendarNotifications(30),
      getUnreadNotificationCount(),
    ])
    notifications.value = items
    unreadNotificationCount.value = unread.count
  } catch (error) {
    if (notificationCenterOpen.value) {
      notify(
        error instanceof Error
          ? `通知加载失败：${error.message}`
          : '通知加载失败',
      )
    }
  } finally {
    notificationsLoading.value = false
    notificationRequestInFlight = false
  }
}

async function toggleNotificationCenter() {
  notificationCenterOpen.value = !notificationCenterOpen.value
  profileMenuOpen.value = false
  if (notificationCenterOpen.value) {
    await refreshNotificationCenter()
  }
}

async function openNotification(notification: CalendarNotification) {
  if (!notification.readAt) {
    try {
      await markCalendarNotificationRead(notification.id)
      notification.readAt = new Date().toISOString()
      unreadNotificationCount.value = Math.max(
        0,
        unreadNotificationCount.value - 1,
      )
    } catch {
      // Navigation remains useful if marking a notification read fails.
    }
  }
  notificationCenterOpen.value = false
  if (
    notification.linkView === 'calendar' ||
    notification.linkView === ''
  ) {
    selectView('calendar')
  }
}

async function markAllNotificationsRead() {
  if (unreadNotificationCount.value === 0) return
  try {
    await markAllCalendarNotificationsRead()
    const readAt = new Date().toISOString()
    notifications.value = notifications.value.map((item) => ({
      ...item,
      readAt: item.readAt ?? readAt,
    }))
    unreadNotificationCount.value = 0
  } catch (error) {
    notify(
      error instanceof Error
        ? `通知标记失败：${error.message}`
        : '通知标记失败',
    )
  }
}

function notificationTime(value: string) {
  const timestamp = new Date(value)
  if (Number.isNaN(timestamp.getTime())) return ''
  const now = new Date()
  const sameDay =
    timestamp.getFullYear() === now.getFullYear() &&
    timestamp.getMonth() === now.getMonth() &&
    timestamp.getDate() === now.getDate()
  if (sameDay) {
    return new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(timestamp)
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  }).format(timestamp)
}

function restoreUserWorkbench(email: string) {
  const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
  workbenchOwnerEmail = normalizedEmail
  if (!normalizedEmail) return

  const data = loadUserWorkbenchData(normalizedEmail)
  tasks.value = (
    data
      ? data.tasks
      : defaultTasks
  ).map((task) => ({ ...task }))
  notes.value = (
    data
      ? data.notes
      : defaultNotes
  ).map((note) => ({ ...note }))
  projects.value = (
    data
      ? data.projects
      : defaultProjects
  ).map((project) => ({ ...project }))

  const settings = loadUserWorkbenchSettings(normalizedEmail)
  launchOnLogin.value = settings?.launchOnLogin ?? true
  compactSidebar.value = settings?.compactSidebar ?? false
  codexDisplayName.value =
    settings?.codexName ?? DEFAULT_CODEX_ASSISTANT_NAME
  codexIconDataUrl.value = settings?.codexIconDataUrl ?? null

  if (!data) {
    saveUserWorkbenchData(normalizedEmail, {
      tasks: tasks.value,
      notes: notes.value,
      projects: projects.value,
    })
  }
  if (!settings) {
    saveUserWorkbenchSettings(normalizedEmail, {
      launchOnLogin: launchOnLogin.value,
      compactSidebar: compactSidebar.value,
      codexName: codexDisplayName.value,
      codexIconDataUrl: codexIconDataUrl.value,
    })
  }
}

function persistUserWorkbench() {
  if (!workbenchOwnerEmail) return
  saveUserWorkbenchData(workbenchOwnerEmail, {
    tasks: tasks.value,
    notes: notes.value,
    projects: projects.value,
  })
}

function persistUserWorkbenchSettings() {
  if (!workbenchOwnerEmail) return
  saveUserWorkbenchSettings(workbenchOwnerEmail, {
    launchOnLogin: launchOnLogin.value,
    compactSidebar: compactSidebar.value,
    codexName: codexDisplayName.value,
    codexIconDataUrl: codexIconDataUrl.value,
  })
}

function handleStorageError(event: Event) {
  const detail = (event as CustomEvent<{ message?: string }>).detail
  notify(
    detail?.message
      ? `MySQL 保存失败：${detail.message}`
      : 'MySQL 保存失败，请稍后重试',
  )
}

function toggleTask(id: number) {
  const task = tasks.value.find((item) => item.id === id)
  if (!task) return

  task.done = !task.done
}

async function addTask() {
  const title = newTaskTitle.value.trim()
  if (!title) return

  tasks.value.unshift({
    id: Date.now(),
    title,
    project: '收件箱',
    time: new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date()),
    done: false,
    priority: 'medium',
  })
  newTaskTitle.value = ''
  notify('任务已加入今天')
  await nextTick()
  taskComposer.value?.focus()
}

async function focusTaskComposer() {
  if (activeView.value !== 'overview' && activeView.value !== 'tasks') {
    activeView.value = 'tasks'
  }
  mobileNavOpen.value = false
  await nextTick()
  taskComposer.value?.focus()
}

function toggleSidebar() {
  profileMenuOpen.value = false
  notificationCenterOpen.value = false
  if (window.matchMedia('(max-width: 900px)').matches) {
    sidebarCollapsed.value = false
    mobileNavOpen.value = !mobileNavOpen.value
    return
  }
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function selectView(
  view: ViewId,
  query: Record<string, string> = {},
) {
  if (view === 'codex') codexMounted.value = true
  if (view === 'servers') serverManagementMounted.value = true
  activeView.value = view
  syncNavigationMenus(view)
  mobileNavOpen.value = false
  profileMenuOpen.value = false
  notificationCenterOpen.value = false
  const routeNeedsUpdate =
    route.query.view !== view ||
    Object.entries(query).some(
      ([key, value]) => route.query[key] !== value,
    )
  if (routeNeedsUpdate) {
    void router.replace({
      name: 'dashboard',
      query: {
        ...route.query,
        view,
        ...query,
      },
    })
  }
}

function openCalendarDate(date: string) {
  selectView('calendar', { date })
}

function syncNavigationMenus(view: ViewId) {
  if (dailyViews.has(view)) dailyMenuOpen.value = true
  if (developmentViews.has(view)) developmentMenuOpen.value = true
  if (resourceViews.has(view)) resourceMenuOpen.value = true
}

function toggleDailyMenu() {
  dailyMenuOpen.value = !dailyMenuOpen.value
}

function toggleDevelopmentMenu() {
  developmentMenuOpen.value = !developmentMenuOpen.value
}

function toggleResourceMenu() {
  resourceMenuOpen.value = !resourceMenuOpen.value
}

function solveZentaoBug(bug: ZentaoBug) {
  solveZentaoBugs(
    [bug],
    `请分析并解决当前关联的 Bug #${bug.remoteId}：${bug.title}。请先定位根因，完成代码修复和必要验证；任务完成后更新该 Bug 单的解决方案与问题原因。`,
  )
}

async function testZentaoBug(bug: ZentaoBug) {
  if (!workspaceStore.activeWorkspaceId) {
    notify('工作空间尚未初始化')
    return
  }
  try {
    const run = await prepareZentaoTestRun(
      bug.id,
      workspaceStore.activeWorkspaceId,
    )
    zentaoStore.requestCodexTest(
      bug.id,
      run.runId,
      run.evidenceDirectory,
    )
    selectView('codex')
  } catch (error) {
    notify(
      error instanceof Error
        ? error.message
        : '无法创建 Codex 测试任务',
    )
  }
}

function solveZentaoBugs(bugs: ZentaoBug[], plan: string) {
  if (bugs.length === 0) return
  zentaoStore.requestCodexPrefill(
    bugs.map((bug) => bug.id),
    plan,
  )
  selectView('codex')
}

function solveGitConflicts(payload: {
  repository: {
    name: string
    path: string
    workspaceId: string
  }
  conflictFiles: string[]
}) {
  const { repository, conflictFiles } = payload
  if (conflictFiles.length === 0) return
  const fileList = conflictFiles
    .map((file) => `- ${file}`)
    .join('\n')
  const prompt = [
    `请解决 Git 仓库「${repository.name}」当前的合并冲突。`,
    '',
    `仓库路径：${repository.path}`,
    '冲突文件：',
    fileList,
    '',
    '要求：',
    '1. 查看冲突文件及相关的提交差异，定位双方改动意图。',
    '2. 保留双方正确逻辑，删除所有冲突标记，并检查是否还有语法或引用问题。',
    '3. 运行与改动相关的测试或构建验证。',
    '4. 不要自动提交，也不要执行 git merge --continue；完成后说明你的解决思路。',
  ].join('\n')
  codexStore.load(session.email)
  codexStore.createSession(
    repository.workspaceId,
    repository.path,
  )
  codexStore.requestPrefill(
    repository.workspaceId,
    repository.path,
    prompt,
  )
  selectView('codex')
}

function solveNoteWithCodex(note: StoredWorkNote) {
  const content = note.content.trim()
  if (!content) return

  const prompt = [
    '请分析并解决下面这条速记中记录的问题。',
    '',
    `标题：${note.title || '未命名速记'}`,
    note.tags.length > 0
      ? `标签：${note.tags.join('、')}`
      : '标签：无',
    '',
    '速记内容：',
    content,
    '',
    '执行要求：',
    '1. 先判断这条速记是否描述了明确的问题、故障、待办或决策。',
    '2. 如果存在需要修复的问题，请定位根因，完成必要的代码修改和验证。',
    '3. 如果内容只是信息记录，请整理结论、风险和后续行动，不要做无关改动。',
    '4. 最后说明分析结论、实际修改和验证结果。',
  ].join('\n')

  codexStore.load(session.email)
  codexStore.createSession(workspaceStore.activeWorkspaceId, '')
  codexStore.requestPrefill(
    workspaceStore.activeWorkspaceId,
    '',
    prompt,
  )
  selectView('codex')
}

function openCalendarNote(noteId: string) {
  noteLibraryStore.requestOpen(noteId)
  selectView('notes')
}

async function handleSearchSelection(result: GlobalSearchResult) {
  if (result.kind === 'website') {
    const website = shortcutStore.websites.find(
      (item) => item.id === result.targetId,
    )
    if (!website) return
    const error = await openWebsiteUrl(website.url)
    if (error) {
      notify(`无法打开网址：${error}`)
      return
    }
    shortcutStore.recordWebsiteClick(website.id)
  } else if (result.kind === 'note') {
    noteLibraryStore.requestOpen(result.targetId)
    selectView('notes')
  } else if (result.kind === 'codexSession') {
    codexStore.selectSession(result.targetId)
    selectView('codex')
  } else {
    selectView(result.targetView)
  }
  searchQuery.value = ''
}

function handleGlobalShortcut(event: KeyboardEvent) {
  if (
    (event.metaKey || event.ctrlKey) &&
    event.shiftKey &&
    event.key.toLowerCase() === 'n'
  ) {
    event.preventDefault()
    selectView('notes')
    notesQuickCapturePending.value = true
    void nextTick(() => {
      if (!notesWorkspace.value) return
      notesQuickCapturePending.value = false
      notesWorkspace.value.startComposer()
    })
    return
  }

  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    searchInput.value?.focus()
  }

  if (event.key === 'Escape') {
    searchQuery.value = ''
    mobileNavOpen.value = false
    profileMenuOpen.value = false
    notificationCenterOpen.value = false
  }
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (!(event.target instanceof Element)) return
  if (
    profileMenuOpen.value &&
    !event.target.closest('.profile-area')
  ) {
    profileMenuOpen.value = false
  }
  if (
    notificationCenterOpen.value &&
    !event.target.closest('.notification-area')
  ) {
    notificationCenterOpen.value = false
  }
}

async function handleSignOut() {
  try {
    await session.logout()
  } finally {
    await router.push({ name: 'login' })
  }
}

async function handleSwitchAccount() {
  profileMenuOpen.value = false
  await handleSignOut()
}

async function refreshDatabaseStatus() {
  databaseStatus.value = 'checking'
  try {
    const health = await getSystemHealth()
    databaseStatus.value =
      health.database === 'UP' ? 'connected' : 'disconnected'
  } catch {
    databaseStatus.value = 'offline'
  }
}

async function refreshCodexStatus() {
  codexStatus.value = 'checking'
  codexDetail.value = '正在检测本地 Codex CLI'
  try {
    const status = await getCodexStatus()
    codexStatus.value = status.available ? 'ready' : 'missing'
    codexDetail.value = status.available
      ? status.executable
      : '未找到可用的 Codex CLI'
  } catch {
    codexStatus.value = 'offline'
    codexDetail.value = `${PRODUCT_NAME}后端服务未启动`
  }
}

async function refreshCcSwitchStatus() {
  ccSwitchStatus.value = 'checking'
  ccSwitchDetail.value = '正在检测本地 CC Switch'
  if (window.workCoveDesktop?.ccSwitchStatus) {
    try {
      const status = await window.workCoveDesktop.ccSwitchStatus()
      ccSwitchStatus.value = status.available ? 'ready' : 'missing'
      if (!status.available) {
        ccSwitchDetail.value = '未检测到 CC Switch 或本地数据'
        return
      }
      ccSwitchDetail.value = status.dataPath
        ? `${status.appPath ? 'CC Switch · ' : ''}${status.dataPath.replace(/^.*\.cc-switch$/, '~/.cc-switch')}`
        : status.appPath ?? 'CC Switch 已安装'
      return
    } catch {
      // Fall through to the local backend bridge.
    }
  }

  try {
    const start = new Date()
    start.setHours(0, 0, 0, 0)
    const usage = await getCcSwitchUsage(
      Math.floor(start.getTime() / 1000),
    )
    ccSwitchStatus.value = usage.available ? 'ready' : 'missing'
    ccSwitchDetail.value = usage.available
      ? 'CC Switch · 本机数据库桥接'
      : usage.reason || '未检测到 CC Switch 本地数据'
  } catch {
    ccSwitchStatus.value = 'unavailable'
    ccSwitchDetail.value = 'CC Switch 本机桥接不可用'
  }
}

async function refreshSogouStatus() {
  await activityStore.refreshSogouStats()
}

watch(
  () => session.email,
  (email) => {
    workspaceStore.load(email)
    shortcutStore.load(email)
    workDirectoryStore.load(email)
    gitRepositoryStore.load(email)
    deploymentStore.load(email)
    noteLibraryStore.load(email)
    codexStore.load(email)
    wellnessStore.load(email)
    restoreUserWorkbench(email)
    const workspaceId = workspaceStore.activeWorkspaceId
    if (workspaceId) {
      void databaseStore.load(workspaceId).catch(() => {})
      void managedServerStore.load().catch(() => {})
      void zentaoStore.load(email, workspaceId).catch(() => {})
    }
    notifications.value = []
    unreadNotificationCount.value = 0
    notificationCenterOpen.value = false
    if (email) void refreshUnreadNotifications()
  },
  { immediate: true },
)

watch(
  () => workspaceStore.activeWorkspaceId,
  (workspaceId) => {
    if (!workspaceId) {
      databaseStore.clear()
      return
    }
    void databaseStore.load(workspaceId).catch(() => {})
    void managedServerStore.load().catch(() => {})
    void zentaoStore.load(session.email, workspaceId).catch(() => {})
  },
)

watch(notesWorkspace, (component) => {
  if (!component || !notesQuickCapturePending.value) return
  notesQuickCapturePending.value = false
  component.startComposer()
})

watch(
  [tasks, notes, projects],
  persistUserWorkbench,
  { deep: true },
)

watch(
  [
    launchOnLogin,
    compactSidebar,
    codexDisplayName,
    codexIconDataUrl,
  ],
  persistUserWorkbenchSettings,
)

watch(activeView, (view) => {
  if (view !== 'settings') return
  void refreshDatabaseStatus()
  void refreshCodexStatus()
  void refreshCcSwitchStatus()
  void refreshSogouStatus()
})

watch(
  () => route.query.view,
  (value) => {
    const next =
      typeof value === 'string' &&
      viewIds.includes(value as ViewId)
        ? (value as ViewId)
        : 'overview'
    if (next === activeView.value) return
    if (next === 'codex') codexMounted.value = true
    if (next === 'servers') serverManagementMounted.value = true
    activeView.value = next
    syncNavigationMenus(next)
    mobileNavOpen.value = false
    profileMenuOpen.value = false
    notificationCenterOpen.value = false
  },
)

onMounted(() => {
  syncNavigationMenus(activeView.value)
  window.addEventListener('keydown', handleGlobalShortcut)
  window.addEventListener('workcove:storage-error', handleStorageError)
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  void refreshDatabaseStatus()
  void refreshCodexStatus()
  void refreshCcSwitchStatus()
  void refreshSogouStatus()
  void refreshUnreadNotifications()
  notificationPollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void refreshUnreadNotifications()
    }
  }, 45_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalShortcut)
  window.removeEventListener(
    'workcove:storage-error',
    handleStorageError,
  )
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  if (toastTimer) window.clearTimeout(toastTimer)
  if (notificationPollTimer) {
    window.clearInterval(notificationPollTimer)
  }
})
</script>

<template>
  <main class="desktop-stage">
    <section class="workbench-window">
      <AppTitlebar :title="currentView.label">
        <template #actions>
          <button
            class="mobile-menu-trigger"
            type="button"
            aria-label="打开导航"
            @click="mobileNavOpen = !mobileNavOpen"
          >
            <Menu :size="16" />
          </button>
        </template>
      </AppTitlebar>

      <div class="workspace">
        <button
          v-if="mobileNavOpen"
          class="sidebar-scrim"
          type="button"
          aria-label="关闭导航"
          @click="mobileNavOpen = false"
        />

        <aside
          class="workbench-sidebar"
          :class="{
            'workbench-sidebar--open': mobileNavOpen,
            'workbench-sidebar--collapsed': sidebarCollapsed,
          }"
        >
          <div class="sidebar-brand">
            <BrandMark :size="32" />
            <div class="sidebar-brand__copy">
              <span>{{ PRODUCT_NAME }}</span>
              <small>{{ PRODUCT_TAGLINE }}</small>
            </div>
          </div>

          <nav class="sidebar-navigation" aria-label="主导航">
            <button
              v-for="item in navigationPrimary"
              :key="item.id"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === item.id }"
              type="button"
              :aria-current="activeView === item.id ? 'page' : undefined"
              @click="selectView(item.id)"
            >
              <component
                :is="item.icon"
                :size="17"
                :stroke-width="1.85"
              />
              <span>{{ item.label }}</span>
            </button>

            <button
              class="nav-item nav-item--group nav-item--group-start"
              :class="{
                'nav-item--group-active':
                  activeView === 'codex' ||
                  activeView === 'notes' ||
                  activeView === 'calendar' ||
                  activeView === 'mail',
              }"
              type="button"
              :aria-expanded="dailyMenuOpen"
              aria-controls="daily-submenu"
              @click="toggleDailyMenu"
            >
              <CalendarDays :size="17" :stroke-width="1.85" />
              <span>日常</span>
              <ChevronDown
                class="nav-item__chevron"
                :class="{ 'nav-item__chevron--closed': !dailyMenuOpen }"
                :size="15"
              />
            </button>
            <div
              v-show="dailyMenuOpen"
              id="daily-submenu"
              class="nav-submenu"
            >
              <button
                v-for="item in dailyNavigation"
                :key="item.id"
                class="nav-item nav-item--sub"
                :class="{ 'nav-item--active': activeView === item.id }"
                type="button"
                :aria-current="activeView === item.id ? 'page' : undefined"
                @click="selectView(item.id)"
              >
                <component
                  :is="item.icon"
                  :size="16"
                  :stroke-width="1.85"
                />
                <span>{{ item.label }}</span>
              </button>
            </div>

            <button
              class="nav-item nav-item--group nav-item--group-start"
              :class="{
                'nav-item--group-active':
                  activeView === 'workDirectories' ||
                  activeView === 'gitRepositories' ||
                  activeView === 'databases' ||
                  activeView === 'servers' ||
                  activeView === 'deployments' ||
                  activeView === 'zentaoBugs',
              }"
              type="button"
              :aria-expanded="developmentMenuOpen"
              aria-controls="development-submenu"
              @click="toggleDevelopmentMenu"
            >
              <Code2 :size="17" :stroke-width="1.85" />
              <span>研发</span>
              <ChevronDown
                class="nav-item__chevron"
                :class="{ 'nav-item__chevron--closed': !developmentMenuOpen }"
                :size="15"
              />
            </button>
            <div
              v-show="developmentMenuOpen"
              id="development-submenu"
              class="nav-submenu"
            >
              <button
                v-for="item in developmentNavigation"
                :key="item.id"
                class="nav-item nav-item--sub"
                :class="{ 'nav-item--active': activeView === item.id }"
                type="button"
                :aria-current="activeView === item.id ? 'page' : undefined"
                @click="selectView(item.id)"
              >
                <component
                  :is="item.icon"
                  :size="16"
                  :stroke-width="1.85"
                />
                <span>{{ item.label }}</span>
              </button>
            </div>

            <button
              class="nav-item nav-item--group nav-item--group-start"
              :class="{
                'nav-item--group-active':
                  activeView === 'websites' ||
                  activeView === 'apps' ||
                  activeView === 'vault',
              }"
              type="button"
              :aria-expanded="resourceMenuOpen"
              aria-controls="resource-submenu"
              @click="toggleResourceMenu"
            >
              <Boxes :size="17" :stroke-width="1.85" />
              <span>资源</span>
              <ChevronDown
                class="nav-item__chevron"
                :class="{ 'nav-item__chevron--closed': !resourceMenuOpen }"
                :size="15"
              />
            </button>
            <div
              v-show="resourceMenuOpen"
              id="resource-submenu"
              class="nav-submenu"
            >
              <button
                v-for="item in resourceNavigation"
                :key="item.id"
                class="nav-item nav-item--sub"
                :class="{ 'nav-item--active': activeView === item.id }"
                type="button"
                :aria-current="activeView === item.id ? 'page' : undefined"
                @click="selectView(item.id)"
              >
                <component
                  :is="item.icon"
                  :size="16"
                  :stroke-width="1.85"
                />
                <span>{{ item.label }}</span>
              </button>
            </div>
          </nav>

          <div class="sidebar-account">
            <div class="profile-area sidebar-profile-area">
              <button
                class="profile-trigger sidebar-profile-trigger"
                type="button"
                aria-label="账户菜单"
                aria-haspopup="menu"
                :aria-expanded="profileMenuOpen"
                :title="`${displayName} · ${accountLabel}`"
                @click="profileMenuOpen = !profileMenuOpen"
              >
                <span class="profile-avatar sidebar-profile-avatar">
                  <img
                    v-if="session.profile?.avatarDataUrl"
                    :src="session.profile.avatarDataUrl"
                    alt=""
                  />
                  <template v-else>{{ displayInitial }}</template>
                </span>
                <span class="sidebar-profile-copy">
                  <strong>{{ displayName }}</strong>
                  <small>{{ accountLabel }}</small>
                </span>
                <ChevronDown
                  class="sidebar-profile-chevron"
                  :size="14"
                  aria-hidden="true"
                />
              </button>

              <div
                v-if="profileMenuOpen"
                class="profile-popover sidebar-profile-popover"
                role="menu"
              >
                <div class="profile-popover__heading">
                  <span class="profile-avatar profile-avatar--large">
                    <img
                      v-if="session.profile?.avatarDataUrl"
                      :src="session.profile.avatarDataUrl"
                      alt=""
                    />
                    <template v-else>{{ displayInitial }}</template>
                  </span>
                  <div>
                    <strong>{{ displayName }}</strong>
                    <small>{{ session.email || 'macOS 本地工作台' }}</small>
                  </div>
                </div>
                <button
                  type="button"
                  role="menuitem"
                  @click="selectView('profile')"
                >
                  <UserRound :size="15" />
                  个人资料
                </button>
                <button
                  type="button"
                  role="menuitem"
                  @click="selectView('settings')"
                >
                  <Settings :size="15" />
                  设置
                </button>
                <button
                  type="button"
                  role="menuitem"
                  @click="handleSwitchAccount"
                >
                  <ArrowLeftRight :size="15" />
                  切换账号
                </button>
                <button
                  type="button"
                  role="menuitem"
                  @click="handleSignOut"
                >
                  <LogOut :size="15" />
                  退出登录
                </button>
              </div>
            </div>

            <button
              class="sidebar-settings-button"
              type="button"
              aria-label="打开设置"
              title="设置"
              @click="selectView('settings')"
            >
              <Settings :size="17" />
            </button>
          </div>
        </aside>

        <section class="workspace-main">
          <header
            class="workspace-toolbar"
          >
            <div class="toolbar-leading">
              <WorkspaceSwitcher @notify="notify" />

              <div class="global-search">
                <Search :size="15" aria-hidden="true" />
                <input
                  ref="searchInput"
                  v-model="searchQuery"
                  type="search"
                  aria-label="全局搜索"
                  placeholder="搜索页面、任务、资源…"
                />
                <kbd aria-label="快捷键 Command K">
                  <span aria-hidden="true">⌘</span>
                  <span>K</span>
                </kbd>

                <div v-if="searchQuery.trim()" class="search-results">
                  <p v-if="searchResults.length === 0" class="search-empty">
                    没有找到相关内容
                  </p>
                  <button
                    v-for="result in searchResults"
                    :key="result.key"
                    type="button"
                    @click="handleSearchSelection(result)"
                  >
                    <component :is="result.icon" :size="15" />
                    <span class="search-result__copy">
                      <strong>{{ result.title }}</strong>
                      <small>{{ result.subtitle }}</small>
                    </span>
                    <small class="search-result__group">
                      {{ result.group }}
                    </small>
                  </button>
                </div>
              </div>
            </div>

            <div class="toolbar-actions">
              <button
                class="toolbar-icon page-refresh-button"
                type="button"
                aria-label="刷新页面"
                title="刷新页面"
                @click="refreshCurrentPage"
              >
                <RefreshCw :size="16" />
              </button>

              <div class="notification-area">
                <button
                  class="toolbar-icon"
                  type="button"
                  aria-label="通知"
                  title="通知"
                  aria-haspopup="dialog"
                  :aria-expanded="notificationCenterOpen"
                  @click="toggleNotificationCenter"
                >
                  <Bell :size="16" />
                  <span
                    v-if="unreadNotificationCount > 0"
                    class="notification-badge"
                  >
                    {{
                      unreadNotificationCount > 99
                        ? '99+'
                        : unreadNotificationCount
                    }}
                  </span>
                </button>

                <section
                  v-if="notificationCenterOpen"
                  class="notification-center"
                  aria-label="通知中心"
                >
                  <header class="notification-center__header">
                    <div>
                      <strong>通知</strong>
                      <small>
                        {{
                          unreadNotificationCount > 0
                            ? `${unreadNotificationCount} 条未读`
                            : '暂无未读通知'
                        }}
                      </small>
                    </div>
                    <button
                      v-if="unreadNotificationCount > 0"
                      type="button"
                      @click="markAllNotificationsRead"
                    >
                      <CheckCheck :size="13" />
                      全部已读
                    </button>
                  </header>

                  <div class="notification-center__list">
                    <p
                      v-if="notificationsLoading"
                      class="notification-center__empty"
                    >
                      正在加载通知…
                    </p>
                    <p
                      v-else-if="notifications.length === 0"
                      class="notification-center__empty"
                    >
                      <span>
                        <Inbox :size="18" />
                      </span>
                      日程提醒会出现在这里
                    </p>
                    <template v-else>
                      <button
                        v-for="notification in notifications"
                        :key="notification.id"
                        type="button"
                        class="notification-item"
                        :class="{
                          'notification-item--unread':
                            !notification.readAt,
                        }"
                        @click="openNotification(notification)"
                      >
                        <span class="notification-item__icon">
                          <CalendarDays :size="15" />
                        </span>
                        <span class="notification-item__copy">
                          <span>
                            <strong>{{ notification.title }}</strong>
                            <time>
                              {{
                                notificationTime(
                                  notification.createdAt,
                                )
                              }}
                            </time>
                          </span>
                          <small>{{ notification.body }}</small>
                        </span>
                        <i
                          v-if="!notification.readAt"
                          aria-label="未读"
                        />
                      </button>
                    </template>
                  </div>
                </section>
              </div>

              <button
                class="toolbar-icon sidebar-toggle-button"
                type="button"
                :aria-label="
                  sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'
                "
                :aria-expanded="!sidebarCollapsed"
                :title="
                  sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'
                "
                @click="toggleSidebar"
              >
                <PanelLeft :size="16" />
              </button>
            </div>
          </header>

          <div
            class="content-scroll"
            :class="{ 'content-scroll--codex': activeView === 'codex' }"
          >
            <div
              class="content-frame"
              :class="{
                'content-frame--codex': activeView === 'codex',
                'content-frame--database': activeView === 'databases',
                'content-frame--mail': activeView === 'mail',
              }"
            >
              <header
                v-if="activeView !== 'codex'"
                class="page-heading"
                :class="{ 'page-heading--workbench': activeView === 'overview' }"
              >
                <div class="page-heading__copy">
                  <h1>{{ pageTitle }}</h1>
                  <p>{{ pageDescription }}</p>
                </div>
                <WorkbenchWellnessPanel
                  v-if="activeView === 'overview'"
                  class="page-heading__wellness"
                  @notify="notify"
                />
              </header>

              <CodexWorkspace
                v-if="codexMounted"
                v-show="activeView === 'codex'"
                :assistant-name="codexAssistantName"
                :assistant-icon="codexIconDataUrl"
                @notify="notify"
              />

              <ServerManagement
                v-if="serverManagementMounted"
                v-show="activeView === 'servers'"
                @notify="notify"
              />

              <WorkbenchHome
                v-if="activeView === 'overview'"
                @notify="notify"
                @open-calendar="openCalendarDate"
                @open-git-repositories="selectView('gitRepositories')"
              />

              <WebsiteLibrary
                v-else-if="activeView === 'websites'"
                @notify="notify"
              />

              <ApplicationLibrary
                v-else-if="activeView === 'apps'"
                @notify="notify"
              />

              <WorkDirectoryLibrary
                v-else-if="activeView === 'workDirectories'"
                @notify="notify"
              />

              <GitRepositoryLibrary
                v-else-if="activeView === 'gitRepositories'"
                @notify="notify"
                @solve-conflicts="solveGitConflicts"
              />

              <DatabaseWorkspace
                v-else-if="activeView === 'databases'"
                @notify="notify"
              />

              <DeploymentLibrary
                v-else-if="activeView === 'deployments'"
                @notify="notify"
              />

              <ZentaoBugLibrary
                v-else-if="activeView === 'zentaoBugs'"
                @notify="notify"
                @solve-bug="solveZentaoBug"
                @solve-bugs="solveZentaoBugs"
                @test-bug="testZentaoBug"
              />

              <template v-else-if="activeView === 'servers'" />

              <section v-else-if="activeView === 'tasks'" class="content-section">
                <header class="section-heading section-heading--bordered">
                  <div>
                    <h2>全部任务</h2>
                  </div>
                  <UiSegmentedControl
                    :model-value="taskFilter"
                    :items="taskFilterOptions"
                    aria-label="任务筛选"
                    @update:model-value="setTaskFilter"
                  />
                </header>

                <div class="task-list task-list--roomy">
                  <TaskRow
                    v-for="task in pagedTasks"
                    :key="task.id"
                    :task="task"
                    @toggle="toggleTask"
                  />
                </div>

                <div v-if="filteredTasks.length === 0" class="empty-state">
                  <Check :size="22" />
                  <p>这里暂时没有任务</p>
                </div>

                <UiPagination
                  :page="taskPage"
                  :total="filteredTasks.length"
                  label="任务分页"
                  @update:page="setTaskPage"
                />

                <form class="task-composer task-composer--roomy" @submit.prevent="addTask">
                  <Plus :size="16" />
                  <input
                    ref="taskComposer"
                    v-model="newTaskTitle"
                    type="text"
                    aria-label="新任务名称"
                    placeholder="写下下一项任务"
                  />
                  <button type="submit" :disabled="!newTaskTitle.trim()">
                    添加任务
                  </button>
                </form>
              </section>

              <WorkNotes
                v-else-if="activeView === 'notes'"
                ref="notesWorkspace"
                @notify="notify"
                @send-to-codex="solveNoteWithCodex"
              />

              <WorkVault
                v-else-if="activeView === 'vault'"
                @notify="notify"
              />

              <MailWorkspace
                v-else-if="activeView === 'mail'"
                @notify="notify"
              />

              <CalendarPage
                v-else-if="activeView === 'calendar'"
                :date="calendarDateQuery"
                @notify="notify"
                @open-note="openCalendarNote"
              />

              <section v-else-if="activeView === 'projects'" class="projects-page">
                <div class="projects-header">
                  <div>
                    <h2>正在进行</h2>
                  </div>
                  <span>{{ projects.length }} 个项目</span>
                </div>

                <div class="project-list">
                  <article
                    v-for="project in pagedProjects"
                    :key="project.id"
                    class="project-row"
                  >
                    <span
                      class="project-row__mark"
                      :class="`project-row__mark--${project.color}`"
                    >
                      {{ project.name.charAt(0) }}
                    </span>
                    <div class="project-row__copy">
                      <h3>{{ project.name }}</h3>
                      <p>{{ project.description }}</p>
                    </div>
                    <div class="project-row__progress">
                      <div>
                        <span
                          :style="{ width: `${project.progress}%` }"
                        />
                      </div>
                      <strong>{{ project.progress }}%</strong>
                    </div>
                    <button type="button" aria-label="打开项目">
                      <ArrowUpRight :size="15" />
                    </button>
                  </article>
                </div>

                <UiPagination
                  :page="projectPage"
                  :total="projects.length"
                  label="项目分页"
                  @update:page="setProjectPage"
                />
              </section>

              <ProfileEditor
                v-else-if="activeView === 'profile'"
                @saved="notify('个人资料已保存')"
              />

              <section v-else-if="activeView === 'settings'" class="settings-page">
                <div class="settings-group">
                  <header>
                    <h2>外观</h2>
                  </header>
                  <div class="appearance-row">
                    <div>
                      <strong>颜色模式</strong>
                      <span>选择工作台使用浅色或深色外观。</span>
                    </div>
                    <UiSegmentedControl
                      :model-value="themeStore.preference"
                      :items="themeOptions"
                      aria-label="颜色模式"
                      @update:model-value="setThemePreference"
                    />
                  </div>
                  <div class="appearance-row">
                    <div>
                      <strong>字体风格</strong>
                      <span>
                        为界面选择系统字体、Codex 风格或 Avenir。
                      </span>
                    </div>
                    <UiSegmentedControl
                      :model-value="themeStore.fontPreference"
                      :items="fontOptions"
                      aria-label="字体风格"
                      @update:model-value="setFontPreference"
                    />
                  </div>
                </div>

                <div class="settings-group">
                  <header>
                    <h2>通用</h2>
                  </header>
                  <label class="setting-row">
                    <div>
                      <strong>登录后打开工作台</strong>
                      <span>每次进入工作台时先显示今天的工作。</span>
                    </div>
                    <input v-model="launchOnLogin" type="checkbox" />
                  </label>
                  <label class="setting-row">
                    <div>
                      <strong>紧凑侧边栏</strong>
                      <span>在较窄的窗口中减少导航占用空间。</span>
                    </div>
                    <input v-model="compactSidebar" type="checkbox" />
                  </label>
                </div>

                <CodexIdentitySettings
                  v-model:name="codexDisplayName"
                  v-model:icon-data-url="codexIconDataUrl"
                />

                <div class="settings-group">
                  <header>
                    <h2>数据与集成</h2>
                  </header>
                  <div class="setting-row">
                    <span
                      class="setting-integration-icon setting-integration-icon--asset setting-integration-icon--glyph"
                      aria-hidden="true"
                    >
                      <img :src="mysqlIcon" alt="" />
                    </span>
                    <div class="setting-row__copy">
                      <strong>数据库</strong>
                      <span>MySQL · 127.0.0.1:3306</span>
                    </div>
                    <span
                      class="setting-status"
                      :class="databaseStatusClass"
                    >
                      {{ databaseStatusText }}
                    </span>
                  </div>
                  <div class="setting-row">
                    <span
                      class="setting-integration-icon setting-integration-icon--asset"
                      aria-hidden="true"
                    >
                      <img :src="codexIcon" alt="" />
                    </span>
                    <div class="setting-row__copy">
                      <strong>Codex</strong>
                      <span :title="codexDetail">{{ codexDetail }}</span>
                    </div>
                    <span
                      class="setting-status"
                      :class="localIntegrationStatusClass(codexStatus)"
                    >
                      {{ codexStatusText }}
                    </span>
                  </div>
                  <div class="setting-row">
                    <span
                      class="setting-integration-icon setting-integration-icon--asset"
                      aria-hidden="true"
                    >
                      <img :src="ccSwitchIcon" alt="" />
                    </span>
                    <div class="setting-row__copy">
                      <strong>CC Switch</strong>
                      <span :title="ccSwitchDetail">
                        {{ ccSwitchDetail }}
                      </span>
                    </div>
                    <span
                      class="setting-status"
                      :class="localIntegrationStatusClass(ccSwitchStatus)"
                    >
                      {{ ccSwitchStatusText }}
                    </span>
                  </div>
                  <div class="setting-row">
                    <span
                      class="setting-integration-icon setting-integration-icon--asset"
                      aria-hidden="true"
                    >
                      <img :src="sogouInputIcon" alt="" />
                    </span>
                    <div class="setting-row__copy">
                      <strong>搜狗输入法</strong>
                      <span :title="sogouDetail">{{ sogouDetail }}</span>
                    </div>
                    <div class="setting-integration-actions">
                      <UiButton
                        size="sm"
                        :disabled="activityStore.sogou.loading"
                        @click="refreshSogouStatus"
                      >
                        <RotateCcw :size="13" />
                        重新检测
                      </UiButton>
                      <span
                        class="setting-status"
                        :class="localIntegrationStatusClass(sogouStatus)"
                      >
                        {{ sogouStatusText }}
                      </span>
                    </div>
                  </div>
                </div>

                <div class="settings-group">
                  <header>
                    <h2>版本信息</h2>
                  </header>
                  <div class="setting-row">
                    <div>
                      <strong>{{ PRODUCT_NAME }}</strong>
                      <span>本地优先的个人工作平台</span>
                    </div>
                    <span class="setting-version">
                      {{ PRODUCT_VERSION_LABEL }}
                    </span>
                  </div>
                </div>

                <div class="settings-group">
                  <header>
                    <h2>设计系统</h2>
                  </header>
                  <button
                    class="settings-navigation"
                    type="button"
                    :aria-expanded="styleGuideOpen"
                    aria-controls="style-guide-panel"
                    @click="styleGuideOpen = !styleGuideOpen"
                  >
                    <span class="settings-navigation__icon">
                      <Palette :size="17" />
                    </span>
                    <span class="settings-navigation__copy">
                      <strong>样式规范</strong>
                      <small>颜色、字体、控件、弹窗和快捷图标规则</small>
                    </span>
                    <ChevronRight
                      class="settings-navigation__chevron"
                      :class="{ 'settings-navigation__chevron--open': styleGuideOpen }"
                      :size="16"
                    />
                  </button>
                  <Transition name="style-guide">
                    <StyleGuidePanel
                      v-if="styleGuideOpen"
                      id="style-guide-panel"
                    />
                  </Transition>
                </div>
              </section>
            </div>
          </div>
        </section>
      </div>

      <WellnessReminderBar />

      <footer class="app-footer">
        <span
          class="app-footer__copyright"
          title="Copyright © 2026 panyong"
        >
          Copyright © 2026 panyong
        </span>
        <div class="app-footer__actions">
          <button
            v-if="clockActionMode === 'clockIn'"
            class="app-footer__clock-action app-footer__clock-action--in"
            type="button"
            aria-label="打卡上班"
            title="打卡上班"
            @click="clockInFromFooter"
          >
            <LogIn :size="14" />
            打卡
          </button>
          <button
            v-else-if="clockActionMode === 'clockOut'"
            class="app-footer__clock-action app-footer__clock-action--out"
            type="button"
            aria-label="下班"
            title="结束今天的工作"
            @click="clockOutFromFooter"
          >
            <LogOut :size="14" />
            下班
          </button>
          <button
            v-else
            class="app-footer__clock-action app-footer__clock-action--update"
            type="button"
            aria-label="更新下班打卡"
            title="更新今天下班时间"
            @click="updateClockOutFromFooter"
          >
            <Clock3 :size="14" />
            更新打卡
          </button>
          <button
            class="app-footer__rest"
            type="button"
            aria-label="休息一下"
            title="休息一下"
            :disabled="Boolean(wellnessStore.data.activeRest)"
            @click="startRestFromFooter"
          >
            <Coffee :size="15" />
          </button>
        </div>
      </footer>

      <WellnessRestOverlay
        v-if="wellnessStore.data.activeRest"
        :started-at="wellnessStore.data.activeRest.startedAt"
        @continue="continueRestFromFooter"
      />

      <Transition name="toast">
        <div v-if="toastMessage" class="app-toast" role="status">
          <Check :size="14" />
          {{ toastMessage }}
        </div>
      </Transition>
    </section>
  </main>
</template>

<style scoped>
/* Main workbench layout */
.desktop-stage {
  display: grid;
  width: 100%;
  height: 100%;
  padding: 0;
  overflow: hidden;
  background: var(--canvas);
  place-items: center;
}

.workbench-window {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  flex-direction: column;
  background: var(--canvas);
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.workspace {
  position: relative;
  display: flex;
  min-height: 0;
  flex: 1;
}

.workbench-sidebar {
  z-index: 10;
  display: flex;
  width: 248px;
  min-height: 0;
  min-width: 248px;
  flex-direction: column;
  overflow: hidden;
  padding: 22px 14px 16px;
  background: var(--sidebar);
  border-right: 1px solid var(--line);
  backdrop-filter: blur(26px) saturate(1.15);
  transition:
    width var(--duration-window) var(--ease-apple),
    min-width var(--duration-window) var(--ease-apple),
    padding var(--duration-window) var(--ease-apple),
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-window) var(--ease-apple);
}

.workbench-sidebar.workbench-sidebar--collapsed {
  width: 0;
  min-width: 0;
  padding-right: 0;
  padding-left: 0;
  pointer-events: none;
  border-right-width: 0;
  border-right-color: transparent;
  opacity: 0;
  transform: translateX(-12px);
}

.sidebar-brand {
  display: flex;
  height: 38px;
  align-items: center;
  gap: 12px;
  padding: 0 var(--space-2);
  margin-bottom: 26px;
}

.sidebar-brand__copy {
  display: flex;
  min-width: 0;
  height: 100%;
  flex-direction: column;
  justify-content: center;
}

.sidebar-brand__copy span {
  overflow: hidden;
  color: var(--text);
  font-size: 17px;
  font-weight: var(--weight-strong);
  line-height: 23px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-brand__copy small {
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
  line-height: var(--line-caption);
  letter-spacing: normal;
  white-space: nowrap;
}

.sidebar-navigation {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  padding-right: 8px;
  padding-bottom: 8px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-color: var(--scrollbar-thumb) transparent;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scroll-behavior: smooth;
}

.sidebar-navigation::-webkit-scrollbar {
  width: 8px;
}

.sidebar-navigation::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-navigation::-webkit-scrollbar-thumb {
  min-height: var(--space-8);
  background: color-mix(
    in srgb,
    var(--scrollbar-thumb) 68%,
    transparent
  );
  background-clip: padding-box;
  border: 2px solid transparent;
  border-radius: 999px;
}

.sidebar-navigation::-webkit-scrollbar-thumb:hover {
  background: var(--scrollbar-thumb);
  background-clip: padding-box;
  border: 2px solid transparent;
}

.nav-item {
  display: flex;
  width: 100%;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  gap: 12px;
  padding: 0 11px;
  color: #626963;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  text-align: left;
  background: transparent;
  border-radius: 8px;
  transition:
    color 120ms ease,
    background 120ms ease;
}

.nav-item > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item:hover {
  color: #343a36;
  background: rgb(31 41 38 / 4%);
}

.nav-item--active {
  color: var(--text);
  font-weight: var(--weight-emphasis);
  background: color-mix(in srgb, var(--text) 6%, transparent);
  box-shadow: none;
}

.nav-item--active:hover {
  color: var(--text);
  background: color-mix(in srgb, var(--text) 6%, transparent);
}

.nav-item--group-active {
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.nav-item--group {
  font-weight: var(--weight-emphasis);
}

.nav-item--group-start {
  margin-top: 12px;
}

.nav-item--group-active > svg:first-child {
  color: var(--text-muted);
}

.nav-item__chevron {
  margin-left: auto;
  color: var(--text-subtle);
  transition: transform var(--duration-fast) var(--ease-apple);
}

.nav-item__chevron--closed {
  transform: rotate(-90deg);
}

.nav-submenu {
  display: flex;
  flex: 0 0 auto;
  padding-left: var(--space-3);
  margin: 1px 0 0 12px;
  flex-direction: column;
  gap: 2px;
  border-left: 1px solid var(--line-soft);
}

.nav-item--sub {
  height: 34px;
  flex: 0 0 34px;
  padding-left: 10px;
  font-size: 13px;
}

.nav-badge {
  min-width: 18px;
  margin-left: auto;
  padding: 1px 5px;
  color: #7e857f;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: center;
  background: rgb(31 41 38 / 6%);
  border-radius: 4px;
}

.app-footer {
  position: relative;
  display: flex;
  min-height: var(--app-footer-height);
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  padding: 0 var(--space-4);
  background: var(--material);
  border-top: 1px solid var(--line-soft);
  backdrop-filter: blur(24px) saturate(1.25);
}

.app-footer__copyright {
  max-width: calc(100% - 190px);
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-footer__actions {
  position: absolute;
  top: 50%;
  right: var(--space-4);
  display: flex;
  align-items: center;
  gap: 6px;
  transform: translateY(-50%);
}

.app-footer__clock-action {
  display: inline-flex;
  height: 28px;
  align-items: center;
  gap: 5px;
  padding: 0 9px;
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  border-radius: var(--radius-control);
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.app-footer__clock-action--in {
  color: var(--accent);
  background: color-mix(
    in srgb,
    var(--accent) 13%,
    transparent
  );
  box-shadow: inset 0 0 0 0.5px
    color-mix(in srgb, var(--accent) 30%, transparent);
}

.app-footer__clock-action--in:hover {
  color: var(--accent-strong);
  background: color-mix(
    in srgb,
    var(--accent) 20%,
    transparent
  );
}

.app-footer__clock-action--out {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 8%,
    transparent
  );
  box-shadow: inset 0 0 0 0.5px
    color-mix(in srgb, var(--system-red) 20%, transparent);
}

.app-footer__clock-action--out:hover {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 14%,
    transparent
  );
}

.app-footer__clock-action--update {
  color: var(--text-muted);
  background: var(--control-bg);
  box-shadow: var(--control-shadow);
}

.app-footer__clock-action--update:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.app-footer__rest {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--accent);
  background: color-mix(
    in srgb,
    var(--accent) 13%,
    transparent
  );
  border-radius: var(--radius-control);
  box-shadow: inset 0 0 0 0.5px
    color-mix(in srgb, var(--accent) 30%, transparent);
  place-items: center;
}

.app-footer__rest:hover:not(:disabled) {
  color: var(--accent-strong);
  background: color-mix(
    in srgb,
    var(--accent) 20%,
    transparent
  );
}

.app-footer__rest:disabled {
  opacity: 0.4;
}

.workspace-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  background: var(--canvas-raised);
}

.workspace-toolbar {
  position: relative;
  z-index: 9;
  display: flex;
  height: 54px;
  flex: 0 0 54px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 22px 0 24px;
  background: rgb(251 250 248 / 83%);
  border-bottom: 1px solid var(--line-soft);
  backdrop-filter: blur(22px);
}

.toolbar-leading {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 12px;
}

.global-search {
  position: relative;
  display: flex;
  width: min(100%, 340px);
  height: calc(var(--control-md) + var(--space-2));
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2) 0 var(--space-3);
  color: var(--text-subtle);
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  transition:
    width 180ms ease,
    background 150ms ease,
    border-color 150ms ease,
    box-shadow 150ms ease;
}

.global-search:focus-within {
  width: min(100%, 390px);
  background: var(--canvas-raised);
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.global-search input:focus-visible,
.task-composer input:focus-visible,
.note-composer textarea:focus-visible {
  outline: none;
}

.global-search input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.global-search input::-webkit-search-cancel-button {
  display: none;
}

.global-search kbd {
  display: inline-flex;
  height: var(--control-sm);
  min-width: calc(var(--space-8) + var(--space-1));
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding: 0 var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  line-height: 1;
  background: var(--canvas-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius-badge);
  box-shadow: var(--shadow-control);
}

.search-results {
  position: absolute;
  top: calc(100% + 7px);
  left: 0;
  display: flex;
  width: 100%;
  max-height: min(62vh, 430px);
  overflow-x: hidden;
  overflow-y: auto;
  flex-direction: column;
  padding: 5px;
  background: rgb(253 252 250 / 97%);
  border: 1px solid rgb(31 41 38 / 10%);
  border-radius: 8px;
  box-shadow: 0 18px 40px rgb(31 41 38 / 14%);
  backdrop-filter: blur(20px);
}

.search-results button {
  display: grid;
  min-height: 46px;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  color: #535a55;
  font-size: 11px;
  text-align: left;
  background: transparent;
  border-radius: 5px;
}

.search-results button:hover {
  color: #24322b;
  background: rgb(47 111 94 / 7%);
}

.search-result__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.search-result__copy strong,
.search-result__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-result__copy strong {
  color: var(--text);
  font-size: 12px;
  font-weight: var(--weight-emphasis);
}

.search-result__copy small {
  color: #9ba19c;
  font-size: 11px;
}

.search-results button .search-result__group {
  color: var(--text-subtle);
  font-size: 11px;
  white-space: nowrap;
}

.search-empty {
  margin: 0;
  padding: 15px 10px;
  color: #999f9a;
  font-size: 11px;
  text-align: center;
}

.toolbar-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.toolbar-icon {
  position: relative;
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  color: #737a74;
  background: transparent;
  border-radius: 7px;
  place-items: center;
}

.toolbar-icon:hover {
  color: #3e4540;
  background: rgb(31 41 38 / 5%);
}

.sidebar-toggle-button {
  color: var(--text);
}

.notification-dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 4px;
  height: 4px;
  background: var(--coral);
  border: 1px solid var(--canvas-raised);
  border-radius: 50%;
}

.notification-area {
  position: relative;
}

.notification-badge {
  position: absolute;
  top: 1px;
  right: -2px;
  display: grid;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  color: #ffffff;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
  line-height: 1;
  background: var(--system-red);
  border: 1.5px solid var(--canvas-raised);
  border-radius: 999px;
  place-items: center;
}

.notification-center {
  position: absolute;
  z-index: 90;
  top: calc(100% + 9px);
  right: -42px;
  display: flex;
  width: min(370px, calc(100vw - 24px));
  max-height: min(480px, calc(100vh - 120px));
  flex-direction: column;
  overflow: hidden;
  color: var(--text);
  background: color-mix(
    in srgb,
    var(--canvas-raised) 97%,
    transparent
  );
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow:
    0 22px 56px rgb(0 0 0 / 18%),
    0 2px 8px rgb(0 0 0 / 6%);
  backdrop-filter: blur(24px) saturate(1.15);
}

.notification-center__header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px 14px 9px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.notification-center__header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.notification-center__header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.notification-center__header small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.notification-center__header button {
  display: inline-flex;
  height: 26px;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  color: var(--system-blue);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: 6px;
}

.notification-center__header button:hover {
  background: color-mix(
    in srgb,
    var(--system-blue) 8%,
    transparent
  );
}

.notification-center__list {
  min-height: 0;
  padding: 6px;
  overflow-y: auto;
}

.notification-center__empty {
  display: flex;
  min-height: 150px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.notification-center__empty > span {
  display: grid;
  width: 38px;
  height: 38px;
  color: var(--text-subtle);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  place-items: center;
}

.notification-item {
  position: relative;
  display: grid;
  width: 100%;
  grid-template-columns: 34px minmax(0, 1fr) 8px;
  align-items: start;
  gap: 10px;
  padding: 10px;
  color: var(--text);
  text-align: left;
  background: transparent;
  border-radius: 8px;
}

.notification-item:hover {
  background: var(--hover-bg);
}

.notification-item--unread {
  background: color-mix(
    in srgb,
    var(--system-blue) 5%,
    transparent
  );
}

.notification-item__icon {
  display: grid;
  width: 34px;
  height: 34px;
  color: var(--system-blue);
  background: color-mix(
    in srgb,
    var(--system-blue) 10%,
    var(--canvas-raised)
  );
  border-radius: 9px;
  place-items: center;
}

.notification-item__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.notification-item__copy > span {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.notification-item__copy strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item__copy time {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.notification-item__copy small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.notification-item > i {
  width: 6px;
  height: 6px;
  margin-top: 5px;
  background: var(--system-blue);
  border-radius: 50%;
}

.profile-area {
  position: relative;
}

.profile-trigger {
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  color: #667069;
  background: transparent;
  border-radius: 50%;
  place-items: center;
}

.profile-trigger:hover {
  background: rgb(31 41 38 / 4%);
}

.profile-avatar {
  display: grid;
  width: 28px;
  height: 28px;
  overflow: hidden;
  flex: 0 0 auto;
  color: #eaf0ec;
  font-size: 11px;
  font-weight: 700;
  background:
    linear-gradient(145deg, #477564, #2c5143);
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 12%);
  place-items: center;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar--large {
  width: 34px;
  height: 34px;
  font-size: 12px;
}

.profile-popover {
  position: absolute;
  top: calc(100% + 7px);
  right: 0;
  display: flex;
  width: 190px;
  flex-direction: column;
  padding: 5px;
  background: rgb(253 252 250 / 98%);
  border: 1px solid rgb(31 41 38 / 10%);
  border-radius: 8px;
  box-shadow: 0 18px 42px rgb(31 41 38 / 15%);
  backdrop-filter: blur(20px);
}

.profile-popover__heading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 12px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--line-soft);
}

.profile-popover__heading div {
  display: flex;
  max-width: 128px;
  min-width: 0;
  flex-direction: column;
}

.profile-popover__heading strong {
  color: #393f3b;
  font-size: 11px;
}

.profile-popover__heading small {
  overflow: hidden;
  color: #979d98;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-popover > button {
  display: flex;
  height: 32px;
  align-items: center;
  gap: 9px;
  padding: 0 8px;
  color: #5d655f;
  font-size: 11px;
  text-align: left;
  background: transparent;
  border-radius: 5px;
}

.profile-popover > button:hover {
  color: #27312b;
  background: rgb(31 41 38 / 5%);
}

.profile-popover > button:last-child {
  color: #9c5040;
}

.sidebar-account {
  display: grid;
  min-width: 0;
  flex: 0 0 auto;
  grid-template-columns: minmax(0, 1fr) 34px;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  padding: 6px;
  background: color-mix(
    in srgb,
    var(--text) 4%,
    transparent
  );
  border: 1px solid var(--line-soft);
  border-radius: 12px;
}

.sidebar-profile-area {
  min-width: 0;
}

.profile-trigger.sidebar-profile-trigger {
  display: flex;
  width: 100%;
  height: 42px;
  align-items: center;
  justify-content: flex-start;
  gap: 9px;
  padding: 4px 6px;
  text-align: left;
  border-radius: 9px;
}

.profile-trigger.sidebar-profile-trigger:hover {
  background: color-mix(
    in srgb,
    var(--text) 4%,
    transparent
  );
}

.sidebar-profile-avatar {
  width: 34px;
  height: 34px;
  font-size: 12px;
}

.sidebar-profile-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 1px;
}

.sidebar-profile-copy strong,
.sidebar-profile-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-profile-copy strong {
  color: var(--text);
  font-size: 13px;
  font-weight: var(--weight-emphasis);
  line-height: 18px;
}

.sidebar-profile-copy small {
  color: var(--text-subtle);
  font-size: 11px;
  line-height: 15px;
}

.sidebar-profile-chevron {
  flex: 0 0 auto;
  color: var(--text-subtle);
}

.profile-popover.sidebar-profile-popover {
  top: auto;
  right: auto;
  bottom: calc(100% + 8px);
  left: 0;
  width: 220px;
}

.sidebar-settings-button {
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: 8px;
  place-items: center;
}

.sidebar-settings-button:hover {
  color: var(--text);
  background: color-mix(
    in srgb,
    var(--text) 5%,
    transparent
  );
}

.mobile-menu-trigger {
  display: none;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: 6px;
  place-items: center;
  -webkit-app-region: no-drag;
}

.content-scroll {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.content-scroll--codex {
  overflow: hidden;
}

.content-frame {
  width: min(100%, 1130px);
  padding: 34px 38px 48px;
  margin: 0 auto;
}

.content-frame.content-frame--codex {
  width: 100%;
  height: 100%;
  padding: 0;
}

.content-frame.content-frame--database {
  width: 100%;
  max-width: none;
  padding: 26px 24px 40px;
}

.content-frame.content-frame--mail {
  display: flex;
  height: 100%;
  flex-direction: column;
  padding: 20px 38px;
  overflow: hidden;
}

.content-frame.content-frame--mail .page-heading {
  display: none;
}

.content-frame.content-frame--mail .mail-workspace {
  min-height: 0;
  flex: 1;
  height: auto;
}

/* Page heading */
.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: 24px;
}

.page-heading--workbench {
  align-items: center;
  gap: var(--space-6);
  padding-bottom: 19px;
}

.page-heading__wellness {
  width: 100%;
  min-width: 0;
  margin-left: auto;
}

.page-heading--workbench h1 {
  font-size: clamp(27px, 2.5vw, 32px);
}

.page-heading--workbench .page-heading__copy > p:last-child {
  margin-top: 8px;
}

.page-heading__copy {
  min-width: 0;
}

.page-eyebrow {
  margin: 0 0 9px;
  color: #829188;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.page-heading h1 {
  margin: 0;
  color: #1f2722;
  font-family: var(--font-sans);
  font-size: clamp(27px, 2.8vw, 36px);
  font-weight: 620;
  letter-spacing: 0;
  line-height: 1.18;
}

.page-heading__copy > p:last-child {
  margin: 10px 0 0;
  color: #777e78;
  font-size: 12px;
}

.page-heading__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

@media (max-width: 1180px) {
  .page-heading--workbench {
    align-items: stretch;
    flex-direction: column;
    gap: var(--space-3);
  }

  .page-heading__wellness {
    margin-left: 0;
  }
}

.ghost-action,
.primary-action {
  display: inline-flex;
  height: 34px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 12px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 7px;
  transition:
    background 140ms ease,
    transform 140ms ease,
    box-shadow 140ms ease;
}

.ghost-action {
  color: #6f7771;
  background: transparent;
  border: 1px solid rgb(31 41 38 / 10%);
}

.ghost-action:hover {
  color: #424944;
  background: rgb(31 41 38 / 3%);
}

.primary-action {
  padding: 0 14px;
  color: #f6f9f7;
  background: #2e5345;
  box-shadow:
    0 1px 2px rgb(22 44 35 / 15%),
    inset 0 1px 0 rgb(255 255 255 / 9%);
}

.primary-action:hover {
  background: #24473a;
  box-shadow:
    0 4px 12px rgb(22 44 35 / 12%),
    inset 0 1px 0 rgb(255 255 255 / 9%);
  transform: translateY(-1px);
}

/* Overview */
.overview {
  animation: content-arrive 260ms ease both;
}

.day-summary {
  display: grid;
  min-height: 68px;
  grid-template-columns: repeat(3, auto) minmax(180px, 1fr);
  align-items: center;
  gap: 30px;
  padding: 13px 18px;
  background: #f2f1ed;
  border: 1px solid rgb(31 41 38 / 6%);
  border-radius: 8px;
}

.summary-metric {
  display: flex;
  align-items: baseline;
  gap: 7px;
  white-space: nowrap;
}

.summary-metric strong {
  color: #303934;
  font-family: var(--font-sans);
  font-size: 22px;
  font-weight: 580;
  font-variant-numeric: tabular-nums;
}

.summary-metric span {
  color: #818882;
  font-size: 11px;
}

.task-progress {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
  padding-left: 24px;
  border-left: 1px solid rgb(31 41 38 / 9%);
}

.task-progress__copy {
  display: flex;
  align-items: baseline;
  gap: 6px;
  white-space: nowrap;
}

.task-progress__copy span {
  color: #7e8580;
  font-size: 11px;
}

.task-progress__copy strong {
  color: #4d5a53;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.task-progress__track {
  width: 100%;
  height: 4px;
  overflow: hidden;
  background: rgb(31 41 38 / 8%);
  border-radius: 999px;
}

.task-progress__track span {
  display: block;
  height: 100%;
  background: var(--accent);
  border-radius: inherit;
  transition: width 300ms ease;
}

.overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 306px;
  align-items: start;
  gap: 38px;
  margin-top: 29px;
}

.overview-primary {
  min-width: 0;
}

.dashboard-section {
  padding-bottom: 29px;
  border-bottom: 1px solid var(--line);
}

.dashboard-section + .dashboard-section {
  padding-top: 27px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;
}

.section-eyebrow {
  margin: 0 0 6px;
  color: #97a098;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.section-heading h2,
.rail-heading h2,
.notes-editor h2,
.calendar-board__header h2,
.projects-header h2,
.favorites-heading h2,
.settings-group h2 {
  margin: 0;
  color: #29302c;
  font-family: var(--font-sans);
  font-size: 16px;
  font-weight: 620;
  letter-spacing: 0;
  line-height: 1.3;
}

.section-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 0;
  color: #89908a;
  font-size: 11px;
  background: transparent;
}

.section-link:hover {
  color: var(--accent);
}

.task-list {
  display: flex;
  flex-direction: column;
}

.task-composer {
  display: flex;
  height: 36px;
  align-items: center;
  gap: 9px;
  padding: 0 10px;
  margin-top: 9px;
  color: #9aa09b;
  border: 1px dashed rgb(31 41 38 / 14%);
  border-radius: 7px;
  transition:
    background 140ms ease,
    border-color 140ms ease;
}

.task-composer:focus-within {
  background: rgb(255 255 255 / 50%);
  border-color: rgb(47 111 94 / 38%);
}

.task-composer input {
  width: 100%;
  height: 100%;
  padding: 0;
  font-size: 11px;
  background: transparent;
  border: 0;
}

.task-composer kbd {
  color: #a8ada9;
  font-family: inherit;
  font-size: 11px;
}

.recent-notes {
  display: flex;
  flex-direction: column;
}

.recent-note {
  display: grid;
  min-height: 65px;
  grid-template-columns: 3px minmax(0, 1fr) auto;
  align-items: center;
  gap: 13px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--line-soft);
}

.recent-note:last-child {
  border-bottom: 0;
}

.note-tone {
  width: 3px;
  height: 28px;
  border-radius: 999px;
}

.note-tone--sage {
  background: #86a792;
}

.note-tone--sand {
  background: #c7a66b;
}

.note-tone--rose {
  background: #cf8a77;
}

.recent-note div {
  min-width: 0;
}

.recent-note h3 {
  margin: 0;
  color: #3c433e;
  font-size: 11px;
  font-weight: 620;
}

.recent-note p {
  overflow: hidden;
  margin: 4px 0 0;
  color: #8d948e;
  font-size: 11px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-note time {
  align-self: start;
  padding-top: 3px;
  color: #a2a8a3;
  font-size: 11px;
  white-space: nowrap;
}

.note-composer {
  position: relative;
  margin-top: 11px;
}

.note-composer textarea {
  display: block;
  width: 100%;
  min-height: 66px;
  resize: vertical;
  padding: 11px 82px 11px 12px;
  color: #4b534d;
  font-size: 11px;
  line-height: 1.6;
  background: #f5f4f1;
  border: 1px solid transparent;
  border-radius: 7px;
  transition:
    background 140ms ease,
    border-color 140ms ease;
}

.note-composer textarea:focus {
  background: #fff;
  border-color: rgb(47 111 94 / 25%);
  box-shadow: var(--focus-ring);
}

.note-composer button {
  position: absolute;
  right: 9px;
  bottom: 9px;
  height: 27px;
  padding: 0 10px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  background: var(--accent);
  border-radius: 5px;
}

.note-composer button:disabled {
  background: #b5bbb6;
}

.overview-rail {
  min-width: 0;
}

.focus-card {
  padding: 19px 20px 17px;
  color: #edf3ef;
  background:
    linear-gradient(145deg, rgb(255 255 255 / 5%), transparent 58%),
    #263f35;
  border-radius: 8px;
  box-shadow:
    0 8px 24px rgb(34 58 48 / 12%),
    inset 0 1px 0 rgb(255 255 255 / 7%);
}

.focus-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #c6d4cc;
  font-size: 11px;
  font-weight: 600;
}

.focus-card__state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #9db8a9;
  font-size: 11px;
  font-weight: 500;
}

.focus-card__state span {
  width: 5px;
  height: 5px;
  background: #8bb69f;
  border-radius: 50%;
}

.focus-clock {
  display: grid;
  padding: 19px 0 15px;
  place-items: center;
}

.focus-dial {
  display: grid;
  width: 122px;
  height: 122px;
  padding: 5px;
  border-radius: 50%;
  place-items: center;
}

.focus-dial__inner {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  background: #263f35;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 3%);
}

.focus-dial__inner strong {
  font-family: var(--font-sans);
  font-size: 27px;
  font-weight: 480;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.focus-dial__inner span {
  margin-top: 4px;
  color: #91a79b;
  font-size: 11px;
}

.focus-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
}

.focus-controls button {
  display: grid;
  width: 31px;
  height: 31px;
  padding: 0;
  color: #bdcec4;
  background: rgb(255 255 255 / 7%);
  border-radius: 50%;
  place-items: center;
}

.focus-controls button:hover {
  color: #fff;
  background: rgb(255 255 255 / 12%);
}

.focus-controls .focus-controls__primary {
  width: 39px;
  height: 39px;
  color: #263f35;
  background: #e1ece5;
}

.focus-controls .focus-controls__primary:hover {
  color: #1c3128;
  background: #f2f7f4;
}

.focus-card > p {
  overflow: hidden;
  margin: 13px 0 0;
  color: #99ad9f;
  font-size: 11px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-section {
  padding: 25px 1px 0;
  margin-top: 23px;
  border-top: 1px solid var(--line);
}

.rail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 15px;
}

.rail-heading p {
  margin: 0 0 5px;
  color: #9aa19b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.rail-heading h2 {
  font-size: 13px;
}

.rail-heading > svg {
  color: #8d948e;
}

.rail-icon {
  display: grid;
  width: 25px;
  height: 25px;
  padding: 0;
  color: #929993;
  background: transparent;
  border-radius: 6px;
  place-items: center;
}

.rail-icon:hover {
  color: #464e48;
  background: rgb(31 41 38 / 5%);
}

.week-strip {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.week-strip button {
  display: flex;
  min-width: 0;
  height: 55px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  color: #8d948f;
  background: transparent;
  border-radius: 7px;
}

.week-strip button:hover {
  background: rgb(31 41 38 / 4%);
}

.week-strip span {
  font-size: 11px;
}

.week-strip strong {
  color: #555d57;
  font-size: 11px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
}

.week-strip .week-day--today {
  color: #f2f7f4;
  background: var(--accent);
}

.week-strip .week-day--today:hover {
  background: var(--accent-strong);
}

.week-strip .week-day--today strong {
  color: #fff;
}

.agenda-list {
  display: flex;
  flex-direction: column;
}

.agenda-item {
  display: grid;
  min-height: 54px;
  grid-template-columns: 36px 2px minmax(0, 1fr);
  align-items: start;
  gap: 11px;
  padding: 8px 0;
}

.agenda-item time {
  padding-top: 2px;
  color: #8b928c;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.agenda-item__line {
  width: 2px;
  height: 30px;
  border-radius: 999px;
}

.agenda-item__line--green {
  background: #80a58f;
}

.agenda-item__line--coral {
  background: #d38b77;
}

.agenda-item__line--amber {
  background: #c7a266;
}

.agenda-item h3 {
  margin: 0;
  color: #414944;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}

.agenda-item p {
  margin: 3px 0 0;
  color: #949b95;
  font-size: 11px;
  line-height: 1.4;
}

.project-mini-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.project-mini-list > div {
  display: grid;
  grid-template-columns: 7px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
}

.project-color {
  width: 7px;
  height: 7px;
  border-radius: 2px;
}

.project-color--green {
  background: #729884;
}

.project-color--coral {
  background: #ce816c;
}

.project-color--amber {
  background: #c4a05f;
}

.project-mini-list > div > div {
  min-width: 0;
}

.project-mini-list span:not(.project-color) {
  display: block;
  overflow: hidden;
  color: #5b635d;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-mini-list strong {
  color: #888f89;
  font-size: 11px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
}

.mini-progress {
  height: 2px;
  margin-top: 6px;
  overflow: hidden;
  background: rgb(31 41 38 / 8%);
  border-radius: 999px;
}

.mini-progress span {
  height: 100%;
  background: #718e7f;
  border-radius: inherit;
}

/* Tasks */
.content-section {
  min-height: 390px;
  padding-top: 4px;
  animation: content-arrive 260ms ease both;
}

.section-heading--bordered {
  padding-bottom: 17px;
  border-bottom: 1px solid var(--line);
}

.segmented-control {
  display: flex;
  gap: 2px;
  padding: 2px;
  background: #efefeb;
  border-radius: 7px;
}

.segmented-control button {
  height: 25px;
  padding: 0 10px;
  color: #8a918b;
  font-size: 11px;
  background: transparent;
  border-radius: 5px;
}

.segmented-control button.active {
  color: #38413b;
  font-weight: 600;
  background: #fff;
  box-shadow: 0 1px 2px rgb(31 41 38 / 8%);
}

.task-list--roomy {
  padding-top: 3px;
}

.task-composer--roomy {
  height: 42px;
  margin-top: 18px;
  padding: 0 8px 0 12px;
  border-style: solid;
}

.task-composer--roomy button {
  height: 27px;
  padding: 0 11px;
  margin-left: auto;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  background: var(--accent);
  border-radius: 5px;
}

.task-composer--roomy button:disabled {
  background: #b5bbb6;
}

.empty-state {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: #a0a7a1;
}

.empty-state p {
  margin: 0;
  font-size: 11px;
}

/* Notes */
.notes-page {
  animation: content-arrive 260ms ease both;
}

.notes-editor {
  overflow: hidden;
  background: #f2f1ed;
  border: 1px solid rgb(31 41 38 / 6%);
  border-radius: 8px;
}

.notes-editor__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 3px;
  color: #89928b;
}

.notes-editor h2 {
  font-size: 14px;
}

.notes-editor textarea {
  display: block;
  width: 100%;
  min-height: 92px;
  resize: vertical;
  padding: 13px 18px;
  color: #424a44;
  font-size: 11px;
  line-height: 1.7;
  background: transparent;
  border: 0;
}

.notes-editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid rgb(31 41 38 / 6%);
}

.notes-editor__footer > span {
  color: #9ca29d;
  font-size: 11px;
}

.notes-editor__footer button {
  display: inline-flex;
  height: 29px;
  align-items: center;
  gap: 5px;
  padding: 0 11px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  background: var(--accent);
  border-radius: 6px;
}

.notes-editor__footer button:disabled {
  background: #b6bcb7;
}

.notes-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.note-card {
  position: relative;
  min-height: 154px;
  padding: 17px;
  overflow: hidden;
  background: #f4f3ef;
  border: 1px solid transparent;
  border-radius: 8px;
  transition:
    border-color 150ms ease,
    transform 150ms ease,
    box-shadow 150ms ease;
}

.note-card::after {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  content: "";
}

.note-card--sage::after {
  background: #86a792;
}

.note-card--sand::after {
  background: #c7a66b;
}

.note-card--rose::after {
  background: #cf8a77;
}

.note-card:hover {
  background: #f8f7f4;
  border-color: rgb(31 41 38 / 8%);
  box-shadow: 0 6px 18px rgb(31 41 38 / 6%);
  transform: translateY(-1px);
}

.note-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.note-card__type {
  color: #99a09a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.note-card time {
  color: #a1a7a2;
  font-size: 11px;
}

.note-card h3 {
  margin: 15px 0 7px;
  color: #3d463f;
  font-size: 12px;
  font-weight: 620;
}

.note-card p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #79817a;
  font-size: 11px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.note-card > button {
  position: absolute;
  right: 13px;
  bottom: 12px;
  display: grid;
  width: 25px;
  height: 25px;
  padding: 0;
  color: #909791;
  background: transparent;
  border-radius: 5px;
  place-items: center;
}

.note-card > button:hover {
  color: var(--accent);
  background: rgb(47 111 94 / 7%);
}

/* Calendar */
.calendar-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 28px;
  animation: content-arrive 260ms ease both;
}

.calendar-board {
  overflow: hidden;
  background: #f2f1ed;
  border: 1px solid rgb(31 41 38 / 6%);
  border-radius: 8px;
}

.calendar-board__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 18px 18px 14px;
}

.calendar-board__header span {
  color: #929993;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.calendar-week-head {
  display: grid;
  grid-template-columns: repeat(7, minmax(40px, 1fr));
  padding: 0 8px;
}

.calendar-week-head > div {
  display: flex;
  height: 51px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  color: #979e98;
  border-bottom: 1px solid rgb(31 41 38 / 7%);
}

.calendar-week-head span {
  font-size: 11px;
}

.calendar-week-head strong {
  display: grid;
  width: 23px;
  height: 23px;
  color: #5e665f;
  font-size: 11px;
  font-weight: 560;
  border-radius: 50%;
  place-items: center;
}

.calendar-week-head .calendar-day--today {
  color: var(--accent);
}

.calendar-week-head .calendar-day--today strong {
  color: white;
  background: var(--accent);
}

.calendar-grid {
  display: grid;
  min-height: 260px;
  grid-template-columns: repeat(7, minmax(40px, 1fr));
  padding: 0 8px 8px;
}

.calendar-grid > div {
  min-width: 0;
  padding: 7px 4px;
  border-right: 1px solid rgb(31 41 38 / 5%);
}

.calendar-grid > div:last-child {
  border-right: 0;
}

.calendar-grid > div.calendar-day--today {
  background: rgb(255 255 255 / 36%);
}

.calendar-event {
  display: block;
  overflow: hidden;
  padding: 5px 6px;
  margin-bottom: 5px;
  font-size: 11px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-left: 2px solid;
  border-radius: 3px;
}

.calendar-event--green {
  color: #476c59;
  background: #dfe9e2;
  border-color: #71977f;
}

.calendar-event--coral {
  color: #915846;
  background: #f0e1dc;
  border-color: #cc7d66;
}

.calendar-event--amber {
  color: #826430;
  background: #ede4d3;
  border-color: #b8924e;
}

.calendar-agenda {
  padding-top: 4px;
}

.calendar-agenda .section-heading {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.calendar-agenda__count {
  display: grid;
  width: 23px;
  height: 23px;
  color: #788079;
  font-size: 11px;
  background: #efefeb;
  border-radius: 5px;
  place-items: center;
}

.agenda-list--large .agenda-item {
  min-height: 69px;
  padding-top: 14px;
}

.agenda-list--large .agenda-item__line {
  height: 37px;
}

/* Projects */
.projects-page {
  animation: content-arrive 260ms ease both;
}

.projects-header,
.favorites-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 5px 0 17px;
  border-bottom: 1px solid var(--line);
}

.projects-header > span {
  color: #989f99;
  font-size: 11px;
}

.project-list {
  display: flex;
  flex-direction: column;
}

.project-row {
  display: grid;
  min-height: 80px;
  grid-template-columns: 38px minmax(0, 1fr) minmax(120px, 210px) 28px;
  align-items: center;
  gap: 13px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--line-soft);
}

.project-row__mark {
  display: grid;
  width: 36px;
  height: 36px;
  font-size: 12px;
  font-weight: 650;
  border-radius: 7px;
  place-items: center;
}

.project-row__mark--green {
  color: #3d6552;
  background: #dfeae3;
}

.project-row__mark--coral {
  color: #935a48;
  background: #f0e1dc;
}

.project-row__mark--amber {
  color: #80632e;
  background: #ede4d3;
}

.project-row__copy {
  min-width: 0;
}

.project-row__copy h3 {
  margin: 0;
  color: #3b433d;
  font-size: 11px;
  font-weight: 600;
}

.project-row__copy p {
  margin: 4px 0 0;
  color: #959c96;
  font-size: 11px;
}

.project-row__progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-row__progress > div {
  width: 100%;
  height: 4px;
  overflow: hidden;
  background: rgb(31 41 38 / 8%);
  border-radius: 999px;
}

.project-row__progress > div span {
  display: block;
  height: 100%;
  background: #73907f;
  border-radius: inherit;
}

.project-row__progress strong {
  color: #7f8781;
  font-size: 11px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.project-row > button {
  display: grid;
  width: 27px;
  height: 27px;
  padding: 0;
  color: #979e98;
  background: transparent;
  border-radius: 5px;
  place-items: center;
}

.project-row > button:hover {
  color: var(--accent);
  background: rgb(47 111 94 / 7%);
}

/* Favorites */
.favorites-page {
  animation: content-arrive 260ms ease both;
}

.favorites-heading button {
  display: inline-flex;
  height: 30px;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  color: #657068;
  font-size: 11px;
  background: #efefeb;
  border-radius: 6px;
}

.bookmark-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.bookmark-card {
  position: relative;
  display: flex;
  min-height: 120px;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  color: inherit;
  text-decoration: none;
  background: #f2f1ed;
  border: 1px solid transparent;
  border-radius: 8px;
  transition:
    border-color 150ms ease,
    transform 150ms ease,
    background 150ms ease;
}

.bookmark-card:hover {
  background: #f7f6f3;
  border-color: rgb(31 41 38 / 8%);
  transform: translateY(-1px);
}

.bookmark-card__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: #557264;
  background: #e3ebe6;
  border-radius: 7px;
  place-items: center;
}

.bookmark-card h3 {
  margin: 1px 0 5px;
  color: #3d453f;
  font-size: 11px;
  font-weight: 600;
}

.bookmark-card p {
  margin: 0;
  color: #929993;
  font-size: 11px;
}

.bookmark-card__category {
  position: absolute;
  bottom: 14px;
  left: 16px;
  color: #9ca29d;
  font-size: 11px;
}

/* Settings */
.settings-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  animation: content-arrive var(--duration-window) var(--ease-apple) both;
}

.settings-group {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.settings-group header {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.settings-group h2 {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.setting-row {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.setting-row:last-child {
  border-bottom: 0;
}

.setting-row > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.setting-row > .setting-row__copy {
  flex: 1;
}

.setting-row > .setting-integration-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  margin-top: 0;
  overflow: hidden;
  color: var(--asset-tile-icon);
  background: var(--asset-tile-bg);
  border: 1px solid var(--asset-tile-border);
  border-radius: var(--radius-control);
  place-items: center;
}

.setting-row > .setting-integration-icon--asset {
  background: transparent;
  border-color: transparent;
}

.setting-row > .setting-integration-icon--glyph {
  padding: 1px;
}

.setting-integration-icon img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: contain;
}

.setting-row > div span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setting-row strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.setting-row span {
  margin-top: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.setting-row > .setting-integration-actions {
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  flex-direction: row;
}

.setting-integration-actions .setting-status {
  margin-top: 0;
  overflow: visible;
  text-overflow: clip;
}

.setting-row input[type="checkbox"] {
  width: 32px;
  height: 18px;
  margin: 0;
  appearance: none;
  background: var(--segmented-track);
  border-radius: 999px;
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 10%);
  transition: background var(--duration-fast) var(--ease-apple);
}

.setting-row input[type="checkbox"]::after {
  display: block;
  width: 14px;
  height: 14px;
  margin: 2px;
  content: "";
  background: var(--control-bg);
  border-radius: 50%;
  box-shadow: var(--shadow-control);
  transition: transform var(--duration-fast) var(--ease-apple);
}

.setting-row input[type="checkbox"]:checked {
  background: var(--accent);
}

.setting-row input[type="checkbox"]:checked::after {
  transform: translateX(14px);
}

.setting-version {
  flex: 0 0 auto;
  color: var(--text-muted) !important;
  font-family: var(--font-mono);
  font-size: var(--font-caption) !important;
  font-variant-numeric: tabular-nums;
}

.setting-status {
  flex: 0 0 auto;
  padding: 3px 7px;
  margin-top: 0 !important;
  color: var(--amber) !important;
  font-size: 11px !important;
  background: color-mix(in srgb, var(--system-orange) 12%, transparent);
  border-radius: 5px;
}

.setting-status--neutral {
  color: #69726b !important;
  background: #eceeeb;
}

.setting-status--success {
  color: var(--system-green) !important;
  background: color-mix(in srgb, var(--system-green) 11%, transparent);
}

/* Global feedback */
.app-toast {
  position: absolute;
  z-index: 50;
  bottom: calc(var(--app-footer-height) + var(--space-4));
  left: 50%;
  display: flex;
  height: 34px;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  color: #edf4ef;
  font-size: 11px;
  font-weight: 520;
  background: rgb(37 54 46 / 94%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 8px;
  box-shadow: 0 10px 30px rgb(31 41 38 / 20%);
  backdrop-filter: blur(16px);
  transform: translateX(-50%);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 170ms ease,
    transform 170ms ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 6px);
}

.sidebar-scrim {
  display: none;
}

@keyframes content-arrive {
  from {
    opacity: 0;
    transform: translateY(5px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1100px) {
  .content-frame {
    padding-right: 28px;
    padding-left: 28px;
  }

  .overview-layout {
    grid-template-columns: minmax(0, 1fr) 270px;
    gap: 27px;
  }

  .day-summary {
    gap: 20px;
  }

  .calendar-page {
    grid-template-columns: minmax(0, 1fr) 250px;
    gap: 20px;
  }
}

@media (max-width: 900px) {
  .desktop-stage {
    padding: 0;
  }

  .workbench-window {
    height: 100%;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .workbench-sidebar {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    width: 248px;
    transform: translateX(-102%);
    transition: transform 210ms cubic-bezier(0.2, 0.75, 0.3, 1);
  }

  .workbench-sidebar--open {
    transform: translateX(0);
    box-shadow: 18px 0 42px rgb(31 41 38 / 16%);
  }

  .sidebar-scrim {
    position: absolute;
    z-index: 14;
    inset: 0;
    display: block;
    padding: 0;
    background: rgb(28 35 31 / 16%);
    border-radius: 0;
    backdrop-filter: blur(1px);
  }

  .workbench-sidebar {
    z-index: 15;
  }

  .mobile-menu-trigger {
    display: grid;
  }

  .overview-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
  }

  .page-heading__actions .ghost-action {
    display: none;
  }
}

@media (max-width: 760px) {
  .workspace-toolbar {
    padding: 0 16px;
  }

  .content-frame {
    padding: 25px 20px 40px;
  }

  .content-frame.content-frame--mail {
    height: auto;
    padding: 16px 20px 24px;
    overflow: visible;
  }

  .content-frame.content-frame--mail .mail-workspace {
    min-height: 560px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 17px;
    padding-bottom: 21px;
  }

  .page-heading__actions {
    width: 100%;
  }

  .page-heading__actions .primary-action {
    margin-left: 0;
  }

  .day-summary {
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }

  .summary-metric {
    justify-content: center;
    flex-direction: column;
    gap: 1px;
  }

  .summary-metric strong {
    font-size: 19px;
  }

  .task-progress {
    grid-column: 1 / -1;
    padding: 11px 0 0;
    border-top: 1px solid rgb(31 41 38 / 8%);
    border-left: 0;
  }

  .overview-layout {
    grid-template-columns: 1fr;
    gap: 25px;
  }

  .overview-rail {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
    padding-top: 25px;
    border-top: 1px solid var(--line);
  }

  .focus-card {
    grid-row: span 2;
    align-self: stretch;
  }

  .rail-section {
    padding-top: 0;
    margin-top: 0;
    border-top: 0;
  }

  .rail-section--projects {
    display: none;
  }

  .calendar-page {
    grid-template-columns: 1fr;
  }

  .calendar-grid {
    min-height: 210px;
  }

  .notes-grid {
    grid-template-columns: 1fr;
  }

  .bookmark-grid {
    grid-template-columns: 1fr;
  }

  .project-row {
    grid-template-columns: 38px minmax(0, 1fr) 28px;
  }

  .project-row__progress {
    display: none;
  }
}

@media (max-width: 560px) {
  .global-search {
    width: min(100%, 210px);
  }

  .global-search:focus-within {
    width: min(100%, 250px);
  }

  .global-search kbd {
    display: none;
  }

  .toolbar-icon {
    display: none;
  }

  .page-refresh-button,
  .notification-area .toolbar-icon {
    display: grid;
  }

  .workspace-toolbar {
    gap: 12px;
    padding: 0 13px;
  }

  .page-heading__actions {
    justify-content: flex-start;
  }

  .section-heading {
    align-items: flex-start;
  }

  .section-heading--bordered {
    flex-direction: column;
  }

  .overview-rail {
    display: flex;
    flex-direction: column;
  }

  .focus-card {
    order: 2;
  }

  .rail-section {
    padding-top: 20px;
    margin-top: 0;
    border-top: 1px solid var(--line);
  }

  .week-strip {
    gap: 2px;
  }

  .week-strip button {
    height: 51px;
  }

  .notes-editor__footer > span {
    display: none;
  }

  .notes-editor__footer {
    justify-content: flex-end;
  }

  .calendar-board {
    overflow-x: auto;
  }

  .calendar-board__header,
  .calendar-week-head,
  .calendar-grid {
    min-width: 520px;
  }

  .bookmark-card {
    min-height: 102px;
  }

  .app-toast {
    right: 18px;
    bottom: calc(var(--app-footer-height) + var(--space-2));
    left: 18px;
    justify-content: center;
    transform: none;
  }

  .toast-enter-from,
  .toast-leave-to {
    transform: translateY(6px);
  }
}

/* Theme switch */
.appearance-row {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-3) var(--space-4);
}

.appearance-row > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.appearance-row strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.appearance-row span {
  margin-top: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.settings-navigation {
  display: flex;
  width: 100%;
  min-height: 64px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  color: var(--text);
  text-align: left;
  background: transparent;
  transition: background var(--duration-fast) var(--ease-apple);
}

.settings-navigation:hover {
  background: var(--hover-bg);
}

.settings-navigation:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: -2px;
}

.settings-navigation__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--text-muted);
  background: var(--canvas);
  border-radius: var(--radius-control);
  box-shadow: inset 0 0 0 0.5px var(--separator);
  place-items: center;
}

.settings-navigation__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.settings-navigation__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.settings-navigation__copy small {
  margin-top: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.settings-navigation__chevron {
  flex: 0 0 auto;
  color: var(--text-subtle);
  transition: transform var(--duration-popover) var(--ease-apple);
}

.settings-navigation__chevron--open {
  transform: rotate(90deg);
}

.style-guide-enter-active,
.style-guide-leave-active {
  transition:
    opacity var(--duration-popover) var(--ease-apple),
    transform var(--duration-popover) var(--ease-apple);
}

.style-guide-enter-from,
.style-guide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.theme-segmented {
  display: grid;
  width: 264px;
  flex: 0 0 264px;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  padding: 3px;
  background: #eaeef2;
  border: 1px solid rgb(31 35 40 / 8%);
  border-radius: 8px;
}

.theme-segmented button {
  height: 28px;
  padding: 0 8px;
  color: #59636e;
  font-size: 11px;
  font-weight: 550;
  background: transparent;
  border-radius: 5px;
}

.theme-segmented button:hover {
  color: #1f2328;
}

.theme-segmented .theme-segmented__option--active {
  color: #1f2328;
  font-weight: 620;
  background: #ffffff;
  box-shadow:
    0 1px 3px rgb(31 35 40 / 12%),
    inset 0 0 0 1px rgb(31 35 40 / 5%);
}

/* Developer light theme */
html[data-theme="light"] {
  .desktop-stage {
    background: var(--canvas);
  }

  .workbench-window {
    border: 0;
    box-shadow: none;
  }

  .workbench-sidebar {
    background: var(--sidebar);
  }

  .page-eyebrow,
  .section-eyebrow,
  .rail-heading p,
  .note-card__type {
    font-family: var(--font-mono);
  }

  .nav-item {
    color: #59636e;
  }

  .nav-item:hover {
    color: #1f2328;
    background: rgb(31 35 40 / 5%);
  }

  .nav-item--active,
  .nav-item--active:hover {
    color: var(--text);
    background: color-mix(in srgb, var(--text) 6%, transparent);
    box-shadow: none;
  }

  .nav-item--active svg {
    color: var(--text);
  }

  .nav-badge {
    color: #59636e;
    background: rgb(31 35 40 / 6%);
  }

  .nav-item--active .nav-badge {
    color: var(--text-muted);
    background: rgb(31 35 40 / 6%);
  }

  .workspace-toolbar {
    background: rgb(255 255 255 / 88%);
  }

  .search-results {
    background: rgb(255 255 255 / 98%);
    border-color: var(--line);
    box-shadow: 0 18px 42px rgb(31 35 40 / 16%);
  }

  .search-results button:hover {
    color: var(--text);
    background: var(--accent-soft);
  }

  .toolbar-icon {
    color: #59636e;
  }

  .toolbar-icon:hover {
    color: var(--text);
    background: rgb(31 35 40 / 5%);
  }

  .notification-dot {
    border-color: #ffffff;
  }

  .profile-trigger:hover {
    background: rgb(31 35 40 / 5%);
  }

  .profile-avatar {
    background: #0969da;
  }

  .profile-popover {
    background: rgb(255 255 255 / 98%);
    border-color: var(--line);
    box-shadow: 0 18px 42px rgb(31 35 40 / 16%);
  }

  .profile-popover > button:hover {
    background: rgb(31 35 40 / 5%);
  }

  .page-heading h1,
  .section-heading h2,
  .rail-heading h2,
  .notes-editor h2,
  .calendar-board__header h2,
  .projects-header h2,
  .favorites-heading h2,
  .settings-group h2,
  .workbench-window h3 {
    color: var(--text);
  }

  .page-eyebrow {
    color: var(--success);
  }

  .page-heading__copy > p:last-child {
    color: var(--text-muted);
  }

  .ghost-action {
    color: var(--text-muted);
    border-color: var(--line);
  }

  .primary-action {
    color: #ffffff;
    background: var(--accent);
  }

  .primary-action:hover {
    background: var(--accent-strong);
  }

  .day-summary,
  .notes-editor,
  .calendar-board,
  .bookmark-card {
    background: #f6f8fa;
  }

  .summary-metric strong {
    color: var(--text);
    font-family: var(--font-mono);
  }

  .task-progress__copy strong {
    color: var(--accent);
  }

  .task-progress__track span,
  .project-row__progress > div span {
    background: var(--accent);
  }

  .section-link:hover,
  .note-card > button:hover,
  .project-row > button:hover {
    color: var(--accent);
  }

  .task-composer {
    color: var(--text-subtle);
    border-color: var(--line);
  }

  .task-composer:focus-within,
  .note-composer textarea:focus {
    background: #ffffff;
    border-color: var(--accent);
  }

  .task-composer input,
  .note-composer textarea,
  .notes-editor textarea {
    color: var(--text);
  }

  .note-tone--sage,
  .agenda-item__line--green,
  .project-color--green {
    background: var(--success);
  }

  .note-tone--sand,
  .agenda-item__line--amber,
  .project-color--amber {
    background: var(--amber);
  }

  .note-tone--rose,
  .agenda-item__line--coral,
  .project-color--coral {
    background: var(--coral);
  }

  .note-composer textarea {
    color: var(--text);
    background: #f6f8fa;
  }

  .note-composer button,
  .task-composer--roomy button,
  .notes-editor__footer button {
    background: var(--accent);
  }

  .focus-card {
    background:
      linear-gradient(145deg, rgb(88 166 255 / 8%), transparent 58%),
      #0d1117;
    border: 1px solid rgb(31 35 40 / 14%);
  }

  .week-strip .week-day--today {
    background: var(--accent);
  }

  .week-strip .week-day--today:hover {
    background: var(--accent-strong);
  }

  .segmented-control {
    background: #eaeef2;
  }

  .segmented-control button.active {
    color: var(--text);
    background: #ffffff;
  }

  .note-card {
    background: #f6f8fa;
  }

  .note-card:hover {
    background: #ffffff;
    box-shadow: 0 8px 24px rgb(31 35 40 / 8%);
  }

  .calendar-week-head .calendar-day--today strong {
    background: var(--accent);
  }

  .calendar-grid > div.calendar-day--today {
    background: rgb(9 105 218 / 4%);
  }

  .calendar-event--green {
    color: #1a7f37;
    background: #dafbe1;
    border-color: #2da44e;
  }

  .calendar-event--coral {
    color: #cf222e;
    background: #ffebe9;
    border-color: #cf222e;
  }

  .calendar-event--amber {
    color: #9a6700;
    background: #fff8c5;
    border-color: #bf8700;
  }

  .project-row__mark--green {
    color: #1a7f37;
    background: #dafbe1;
  }

  .project-row__mark--coral {
    color: #cf222e;
    background: #ffebe9;
  }

  .project-row__mark--amber {
    color: #9a6700;
    background: #fff8c5;
  }

  .favorites-heading button,
  .calendar-agenda__count {
    background: #eaeef2;
  }

  .bookmark-card__icon {
    color: var(--accent);
    background: var(--accent-soft);
  }

  .settings-group header {
    background: #f6f8fa;
  }

  .setting-row input[type="checkbox"]:checked {
    background: var(--accent);
  }

  .setting-status {
    color: #9a6700 !important;
    background: #fff8c5;
  }

  .setting-status--neutral {
    color: var(--text-muted) !important;
    background: #eaeef2;
  }

  .setting-status--success {
    color: var(--success) !important;
    background: color-mix(in srgb, var(--success) 10%, transparent);
  }

  .app-toast {
    color: #ffffff;
    background: rgb(31 35 40 / 94%);
  }

  .sidebar-scrim {
    background: rgb(31 35 40 / 18%);
  }
}

/* IDE-inspired dark theme */
html[data-theme="dark"] {
.desktop-stage {
  background: var(--canvas);
}

.workbench-window {
  color-scheme: dark;
  background: var(--canvas);
  border: 0;
  box-shadow: none;
}

.workbench-sidebar {
  background: rgb(13 17 23 / 92%);
  border-right-color: var(--line);
}

.sidebar-brand__copy span {
  color: #f0f6fc;
}

.sidebar-brand__copy small {
  color: #6e7681;
}

.nav-item {
  color: #8b949e;
}

.nav-item:hover {
  color: #e6edf3;
  background: rgb(240 246 252 / 5%);
}

.nav-item--active,
.nav-item--active:hover {
  color: #e6edf3;
  background: rgb(240 246 252 / 8%);
  box-shadow: none;
}

.nav-item--active svg {
  color: #e6edf3;
}

.nav-badge {
  color: #8b949e;
  background: rgb(240 246 252 / 7%);
}

.nav-item--active .nav-badge {
  color: #b1bac4;
  background: rgb(240 246 252 / 8%);
}

.workspace-main {
  background: var(--canvas-raised);
}

.workspace-toolbar {
  background: rgb(13 17 23 / 86%);
  border-bottom-color: var(--line-soft);
}

.search-results {
  background: rgb(22 27 34 / 98%);
  border-color: var(--line);
  box-shadow: 0 18px 44px rgb(0 0 0 / 46%);
}

.search-results button {
  color: #c9d1d9;
}

.search-results button:hover {
  color: #f0f6fc;
  background: rgb(56 139 253 / 12%);
}

.search-results button small,
.search-empty {
  color: #6e7681;
}

.toolbar-icon {
  color: #8b949e;
}

.toolbar-icon:hover {
  color: #f0f6fc;
  background: rgb(240 246 252 / 6%);
}

.notification-dot {
  background: var(--coral);
  border-color: #0d1117;
}

.profile-trigger {
  color: #8b949e;
}

.profile-trigger:hover {
  background: rgb(240 246 252 / 5%);
}

.profile-avatar {
  color: #f0f6fc;
  background: #1f6feb;
  box-shadow:
    inset 0 0 0 1px rgb(255 255 255 / 12%),
    0 0 0 2px rgb(31 111 235 / 10%);
}

.profile-popover {
  background: rgb(22 27 34 / 98%);
  border-color: var(--line);
  box-shadow: 0 18px 46px rgb(0 0 0 / 50%);
}

.profile-popover__heading {
  border-bottom-color: var(--line-soft);
}

.profile-popover__heading strong {
  color: #e6edf3;
}

.profile-popover__heading small {
  color: #6e7681;
}

.profile-popover > button {
  color: #8b949e;
}

.profile-popover > button:hover {
  color: #f0f6fc;
  background: rgb(240 246 252 / 6%);
}

.profile-popover > button:last-child {
  color: #ff7b72;
}

.page-eyebrow {
  color: #7ee787;
  font-family: var(--font-mono);
}

.page-heading h1,
.section-heading h2,
.rail-heading h2,
.notes-editor h2,
.calendar-board__header h2,
.projects-header h2,
.favorites-heading h2,
.settings-group h2,
.workbench-window h3 {
  color: #f0f6fc;
}

.page-heading__copy > p:last-child {
  color: #8b949e;
}

.ghost-action {
  color: #8b949e;
  border-color: var(--line);
}

.ghost-action:hover {
  color: #e6edf3;
  background: rgb(240 246 252 / 5%);
}

.primary-action {
  color: #fff;
  background: #1f6feb;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 35%),
    inset 0 1px 0 rgb(255 255 255 / 10%);
}

.primary-action:hover {
  background: #388bfd;
  box-shadow:
    0 4px 16px rgb(31 111 235 / 22%),
    inset 0 1px 0 rgb(255 255 255 / 10%);
}

.day-summary {
  background: #161b22;
  border-color: var(--line-soft);
}

.summary-metric strong {
  color: #f0f6fc;
  font-family: var(--font-mono);
}

.summary-metric span,
.task-progress__copy span {
  color: #6e7681;
}

.task-progress {
  border-left-color: var(--line);
}

.task-progress__copy strong {
  color: #79c0ff;
  font-family: var(--font-mono);
}

.task-progress__track {
  background: rgb(240 246 252 / 8%);
}

.task-progress__track span {
  background: linear-gradient(90deg, #1f6feb, #58a6ff);
}

.section-eyebrow,
.rail-heading p,
.note-card__type {
  color: #6e7681;
  font-family: var(--font-mono);
}

.section-link {
  color: #6e7681;
}

.section-link:hover {
  color: var(--accent);
}

.task-composer {
  color: #6e7681;
  border-color: rgb(240 246 252 / 15%);
}

.task-composer:focus-within {
  background: rgb(13 17 23 / 58%);
  border-color: rgb(88 166 255 / 45%);
}

.task-composer input {
  color: var(--text);
}

.task-composer kbd {
  color: #6e7681;
  font-family: var(--font-mono);
}

.recent-note {
  border-bottom-color: var(--line-soft);
}

.note-tone--sage {
  background: var(--success);
}

.note-tone--sand {
  background: var(--amber);
}

.note-tone--rose {
  background: var(--coral);
}

.recent-note h3 {
  color: #c9d1d9;
}

.recent-note p {
  color: #8b949e;
}

.recent-note time {
  color: #6e7681;
}

.note-composer textarea {
  color: var(--text);
  background: #0d1117;
  border-color: var(--line-soft);
}

.note-composer textarea:focus {
  background: #0d1117;
  border-color: rgb(88 166 255 / 46%);
}

.note-composer button {
  background: #1f6feb;
}

.note-composer button:disabled {
  color: #8b949e;
  background: #21262d;
}

.focus-card {
  background:
    linear-gradient(145deg, rgb(88 166 255 / 8%), transparent 58%),
    #010409;
  border: 1px solid rgb(240 246 252 / 9%);
  box-shadow:
    0 12px 30px rgb(0 0 0 / 32%),
    inset 0 1px 0 rgb(255 255 255 / 5%);
}

.focus-card__header {
  color: #c9d1d9;
}

.focus-card__state {
  color: #7ee787;
}

.focus-card__state span {
  background: var(--success);
}

.focus-dial__inner {
  background: #010409;
}

.focus-dial__inner strong {
  color: #f0f6fc;
}

.focus-dial__inner span {
  color: #6e7681;
}

.focus-controls button {
  color: #c9d1d9;
  background: #21262d;
}

.focus-controls button:hover {
  color: #fff;
  background: #30363d;
}

.focus-controls .focus-controls__primary {
  color: #0d1117;
  background: #e6edf3;
}

.focus-controls .focus-controls__primary:hover {
  color: #010409;
  background: #fff;
}

.focus-card > p {
  color: #6e7681;
}

.rail-section {
  border-top-color: var(--line);
}

.rail-heading > svg,
.rail-icon {
  color: #6e7681;
}

.rail-icon:hover {
  color: #f0f6fc;
  background: rgb(240 246 252 / 6%);
}

.week-strip button {
  color: #6e7681;
}

.week-strip button:hover {
  background: rgb(240 246 252 / 5%);
}

.week-strip strong {
  color: #c9d1d9;
}

.week-strip .week-day--today,
.week-strip .week-day--today:hover {
  background: #1f6feb;
}

.agenda-item time {
  color: #6e7681;
  font-family: var(--font-mono);
}

.agenda-item__line--green {
  background: var(--success);
}

.agenda-item__line--coral {
  background: var(--coral);
}

.agenda-item__line--amber {
  background: var(--amber);
}

.agenda-item p {
  color: #6e7681;
}

.project-color--green {
  background: var(--success);
}

.project-color--coral {
  background: var(--coral);
}

.project-color--amber {
  background: var(--amber);
}

.project-mini-list span:not(.project-color) {
  color: #8b949e;
}

.project-mini-list strong {
  color: #6e7681;
}

.mini-progress {
  background: rgb(240 246 252 / 8%);
}

.mini-progress span {
  background: var(--accent);
}

.segmented-control {
  background: #0d1117;
  border: 1px solid var(--line-soft);
}

.segmented-control button {
  color: #6e7681;
}

.segmented-control button.active {
  color: #f0f6fc;
  background: #21262d;
  box-shadow: 0 1px 2px rgb(0 0 0 / 28%);
}

.task-composer--roomy button,
.notes-editor__footer button {
  background: #1f6feb;
}

.task-composer--roomy button:disabled,
.notes-editor__footer button:disabled {
  color: #6e7681;
  background: #21262d;
}

.empty-state {
  color: #6e7681;
}

.notes-editor {
  background: #161b22;
  border-color: var(--line-soft);
}

.notes-editor__heading {
  color: #6e7681;
}

.notes-editor textarea {
  color: var(--text);
}

.notes-editor__footer {
  border-top-color: var(--line-soft);
}

.notes-editor__footer > span {
  color: #6e7681;
}

.note-card {
  background: #161b22;
  border-color: var(--line-soft);
}

.note-card:hover {
  background: #1c2128;
  border-color: var(--line);
  box-shadow: 0 8px 24px rgb(0 0 0 / 22%);
}

.note-card::after {
  opacity: 0.9;
}

.note-card time {
  color: #6e7681;
}

.note-card p {
  color: #8b949e;
}

.note-card > button {
  color: #6e7681;
}

.note-card > button:hover {
  color: var(--accent);
  background: rgb(56 139 253 / 12%);
}

.calendar-board {
  background: #161b22;
  border-color: var(--line-soft);
}

.calendar-board__header span {
  color: #6e7681;
}

.calendar-event {
  font-size: 11px;
  font-weight: 600;
}

.calendar-week-head > div {
  color: #6e7681;
  border-bottom-color: var(--line-soft);
}

.calendar-week-head strong {
  color: #c9d1d9;
}

.calendar-week-head .calendar-day--today {
  color: #79c0ff;
}

.calendar-week-head .calendar-day--today strong {
  color: #fff;
  background: #1f6feb;
}

.calendar-grid > div {
  border-right-color: var(--line-soft);
}

.calendar-grid > div.calendar-day--today {
  background: rgb(56 139 253 / 6%);
}

.calendar-event--green {
  color: #7ee787;
  background: rgb(63 185 80 / 12%);
  border-color: #3fb950;
}

.calendar-event--coral {
  color: #ff7b72;
  background: rgb(248 81 73 / 12%);
  border-color: #f85149;
}

.calendar-event--amber {
  color: #e3b341;
  background: rgb(210 153 34 / 12%);
  border-color: #d29922;
}

.calendar-agenda__count {
  color: #8b949e;
  background: #21262d;
}

.projects-header > span,
.bookmark-card__category {
  color: #6e7681;
}

.project-row {
  border-bottom-color: var(--line-soft);
}

.project-row__mark--green {
  color: #7ee787;
  background: rgb(63 185 80 / 12%);
}

.project-row__mark--coral {
  color: #ff7b72;
  background: rgb(248 81 73 / 12%);
}

.project-row__mark--amber {
  color: #e3b341;
  background: rgb(210 153 34 / 12%);
}

.project-row__copy p {
  color: #6e7681;
}

.project-row__progress > div {
  background: rgb(240 246 252 / 8%);
}

.project-row__progress > div span {
  background: linear-gradient(90deg, #1f6feb, #58a6ff);
}

.project-row__progress strong,
.project-row > button {
  color: #6e7681;
}

.project-row > button:hover {
  color: var(--accent);
  background: rgb(56 139 253 / 12%);
}

.favorites-heading button {
  color: #c9d1d9;
  background: #21262d;
}

.bookmark-card {
  background: #161b22;
  border-color: var(--line-soft);
}

.bookmark-card:hover {
  background: #1c2128;
  border-color: var(--line);
}

.bookmark-card__icon {
  color: #79c0ff;
  background: rgb(56 139 253 / 12%);
}

.bookmark-card p {
  color: #6e7681;
}

.settings-group {
  background: var(--canvas-raised);
}

.settings-group header {
  border-bottom-color: var(--line-soft);
}

.setting-row {
  border-bottom-color: var(--line-soft);
}

.setting-row strong {
  color: var(--text);
}

.setting-row span {
  color: var(--text-subtle);
}

.setting-row > .setting-integration-icon {
  color: var(--asset-tile-icon);
}

.setting-row input[type="checkbox"] {
  background: var(--segmented-track);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 35%);
}

.setting-row input[type="checkbox"]::after {
  background: var(--control-bg);
  box-shadow: var(--shadow-control);
}

.setting-row input[type="checkbox"]:checked {
  background: var(--system-blue);
}

.setting-row input[type="checkbox"]:checked::after {
  background: #fff;
}

.setting-status {
  color: var(--amber) !important;
  background: color-mix(in srgb, var(--system-orange) 14%, transparent);
}

.setting-status--neutral {
  color: var(--text-muted) !important;
  background: var(--hover-bg);
}

.setting-status--success {
  color: var(--system-green) !important;
  background: color-mix(in srgb, var(--system-green) 12%, transparent);
}

.app-toast {
  color: #f0f6fc;
  background: rgb(22 27 34 / 96%);
  border-color: var(--line);
  box-shadow: 0 12px 34px rgb(0 0 0 / 42%);
}

.sidebar-scrim {
  background: rgb(0 0 0 / 35%);
}

.appearance-row strong {
  color: var(--text);
}

.appearance-row span {
  color: var(--text-subtle);
}

.theme-segmented {
  background: #0d1117;
  border-color: rgb(240 246 252 / 8%);
}

.theme-segmented button {
  color: #8b949e;
}

.theme-segmented button:hover {
  color: #e6edf3;
}

.theme-segmented .theme-segmented__option--active {
  color: #f0f6fc;
  background: #21262d;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 30%),
    inset 0 0 0 1px rgb(240 246 252 / 7%);
}
}

@media (max-width: 760px) {
  .appearance-row {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .theme-segmented {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
