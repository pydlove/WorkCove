<script setup lang="ts">
import {
  ArrowUpRight,
  Bell,
  Bookmark,
  CalendarDays,
  Check,
  CheckSquare2,
  ChevronDown,
  Clock3,
  FileText,
  FolderKanban,
  Globe2,
  Home,
  LogOut,
  Menu,
  NotebookPen,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Search,
  Settings,
  Star,
  UserRound,
  X,
} from '@lucide/vue'
import type { Component } from 'vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue'
import { useRouter } from 'vue-router'

import AppTitlebar from '@/components/business/AppTitlebar.vue'
import ProfileEditor from '@/components/business/ProfileEditor.vue'
import TaskRow from '@/components/business/TaskRow.vue'
import WorkbenchHome from '@/components/business/workbench/WorkbenchHome.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import {
  useThemeStore,
  type ThemePreference,
} from '@/stores/theme'
import type {
  SavedBookmark,
  WorkNote,
  WorkProject,
  WorkTask,
} from '@/types/workbench'

type ViewId =
  | 'overview'
  | 'tasks'
  | 'notes'
  | 'calendar'
  | 'projects'
  | 'favorites'
  | 'profile'
  | 'settings'

type TaskFilter = 'all' | 'open' | 'done'

interface NavigationItem {
  id: ViewId
  label: string
  icon: Component
}

const router = useRouter()
const session = useAuthStore()
const themeStore = useThemeStore()
const now = new Date()

const activeView = ref<ViewId>('overview')
const mobileNavOpen = ref(false)
const profileMenuOpen = ref(false)
const searchQuery = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const taskComposer = ref<HTMLInputElement | null>(null)
const newTaskTitle = ref('')
const noteDraft = ref('')
const taskFilter = ref<TaskFilter>('all')
const launchOnLogin = ref(true)
const compactSidebar = ref(false)
const toastMessage = ref('')
const tasks = ref<WorkTask[]>([
  {
    id: 1,
    title: '梳理本周产品优先级',
    project: 'WorkCove',
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
    project: 'WorkCove',
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
])
const notes = ref<WorkNote[]>([
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
])
const projects = ref<WorkProject[]>([
  {
    id: 1,
    name: 'WorkCove 1.0',
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
])
const bookmarks = ref<SavedBookmark[]>([
  {
    id: 1,
    title: '项目知识库',
    url: 'notion.so',
    category: '文档',
  },
  {
    id: 2,
    title: '产品设计规范',
    url: 'design.local',
    category: '设计',
  },
  {
    id: 3,
    title: '常用代码片段',
    url: 'snippets.local',
    category: '开发',
  },
])
const schedule = [
  {
    time: '10:00',
    title: '产品同步会',
    detail: '设计稿与优先级对齐',
    tone: 'green',
  },
  {
    time: '14:30',
    title: '设计评审',
    detail: '工作台主流程走查',
    tone: 'coral',
  },
  {
    time: '17:00',
    title: '日终回顾',
    detail: '整理今天未完成事项',
    tone: 'amber',
  },
] as const

const navigationPrimary: NavigationItem[] = [
  { id: 'overview', label: '工作台', icon: Home },
  { id: 'tasks', label: '任务', icon: CheckSquare2 },
  { id: 'notes', label: '笔记', icon: NotebookPen },
  { id: 'calendar', label: '日历', icon: CalendarDays },
  { id: 'projects', label: '项目', icon: FolderKanban },
]

const navigationUtility: NavigationItem[] = [
  { id: 'favorites', label: '收藏', icon: Star },
  { id: 'profile', label: '个人资料', icon: UserRound },
  { id: 'settings', label: '设置', icon: Settings },
]

const themeOptions: Array<{ value: ThemePreference; label: string }> = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' },
]

const viewMeta: Record<
  ViewId,
  { label: string; eyebrow: string; description: string }
> = {
  overview: {
    label: '工作台',
    eyebrow: 'WORKBENCH',
    description: '时间、常用网址和本地软件集中在这里。',
  },
  tasks: {
    label: '任务',
    eyebrow: 'TASKS',
    description: '整理、推进并完成今天的工作。',
  },
  notes: {
    label: '笔记',
    eyebrow: 'NOTES',
    description: '让想法有一个随手停留的地方。',
  },
  calendar: {
    label: '日历',
    eyebrow: 'CALENDAR',
    description: '查看这一周的时间安排。',
  },
  projects: {
    label: '项目',
    eyebrow: 'PROJECTS',
    description: '从更长的周期观察工作进展。',
  },
  favorites: {
    label: '收藏',
    eyebrow: 'FAVORITES',
    description: '常用资料与工具集中在这里。',
  },
  profile: {
    label: '个人资料',
    eyebrow: 'PROFILE',
    description: '管理头像、昵称和只属于你的工作台信息。',
  },
  settings: {
    label: '设置',
    eyebrow: 'SETTINGS',
    description: '配置你的本地工作台。',
  },
}

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
const pageEyebrow = computed(() =>
  activeView.value === 'overview' ? 'WORKBENCH' : currentView.value.eyebrow,
)

const openTaskCount = computed(
  () => tasks.value.filter((task) => !task.done).length,
)
const filteredTasks = computed(() => {
  if (taskFilter.value === 'open') {
    return tasks.value.filter((task) => !task.done)
  }

  if (taskFilter.value === 'done') {
    return tasks.value.filter((task) => task.done)
  }

  return tasks.value
})

const monthTitle = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    year: 'numeric',
  }).format(now),
)
const weekdayFormatter = new Intl.DateTimeFormat('zh-CN', {
  weekday: 'short',
})
const weekStart = (() => {
  const date = new Date(now)
  const offset = (date.getDay() + 6) % 7
  date.setDate(date.getDate() - offset)
  date.setHours(12, 0, 0, 0)
  return date
})()
const calendarWeek = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart)
    date.setDate(weekStart.getDate() + index)

    return {
      key: date.toISOString(),
      weekday: weekdayFormatter.format(date).replace('周', ''),
      day: date.getDate(),
      isToday: date.toDateString() === now.toDateString(),
    }
  }),
)

const searchResults = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return []

  const taskHits = tasks.value
    .filter((task) =>
      `${task.title} ${task.project}`.toLocaleLowerCase('zh-CN').includes(query),
    )
    .map((task) => ({
      id: `task-${task.id}`,
      title: task.title,
      meta: task.project,
      type: 'task' as const,
    }))

  const noteHits = notes.value
    .filter((note) =>
      `${note.title} ${note.excerpt}`
        .toLocaleLowerCase('zh-CN')
        .includes(query),
    )
    .map((note) => ({
      id: `note-${note.id}`,
      title: note.title,
      meta: note.updatedAt,
      type: 'note' as const,
    }))

  return [...taskHits, ...noteHits].slice(0, 6)
})

let toastTimer: number | null = null

function notify(message: string) {
  toastMessage.value = message
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMessage.value = ''
  }, 2200)
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

function addNote() {
  const excerpt = noteDraft.value.trim()
  if (!excerpt) return

  notes.value.unshift({
    id: Date.now(),
    title: excerpt.split(/[，。,.!?！？\n]/)[0].slice(0, 18) || '新记录',
    excerpt,
    updatedAt: '刚刚',
    tone: 'sage',
  })
  noteDraft.value = ''
  notify('记录已保存')
}

function selectView(view: ViewId) {
  activeView.value = view
  mobileNavOpen.value = false
  profileMenuOpen.value = false
}

function handleSearchSelection(type: 'task' | 'note') {
  activeView.value = type === 'task' ? 'tasks' : 'notes'
  searchQuery.value = ''
}

function handleGlobalShortcut(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    searchInput.value?.focus()
  }

  if (event.key === 'Escape') {
    searchQuery.value = ''
    mobileNavOpen.value = false
    profileMenuOpen.value = false
  }
}

async function handleSignOut() {
  try {
    await session.logout()
  } finally {
    await router.push({ name: 'login' })
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalShortcut)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalShortcut)
  if (toastTimer) window.clearTimeout(toastTimer)
})
</script>

<template>
  <main class="desktop-stage">
    <section class="workbench-window">
      <AppTitlebar :title="currentView.label" local-status>
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
          :class="{ 'workbench-sidebar--open': mobileNavOpen }"
        >
          <div class="sidebar-brand">
            <BrandMark :size="32" />
            <div class="sidebar-brand__copy">
              <span>WorkCove</span>
              <small>栖作 · PERSONAL DESK</small>
            </div>
          </div>

          <nav class="sidebar-navigation" aria-label="主导航">
            <p class="sidebar-label">工作区</p>
            <button
              v-for="item in navigationPrimary"
              :key="item.id"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === item.id }"
              type="button"
              :aria-current="activeView === item.id ? 'page' : undefined"
              @click="selectView(item.id)"
            >
              <component :is="item.icon" :size="16" :stroke-width="1.85" />
              <span>{{ item.label }}</span>
              <span v-if="item.id === 'tasks'" class="nav-badge">
                {{ openTaskCount }}
              </span>
            </button>

            <p class="sidebar-label sidebar-label--spaced">工具</p>
            <button
              v-for="item in navigationUtility"
              :key="item.id"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === item.id }"
              type="button"
              :aria-current="activeView === item.id ? 'page' : undefined"
              @click="selectView(item.id)"
            >
              <component :is="item.icon" :size="16" :stroke-width="1.85" />
              <span>{{ item.label }}</span>
            </button>
          </nav>

          <div class="sidebar-spacer" />

          <div class="sidebar-storage">
            <span class="sidebar-storage__dot" />
            <div>
              <span>本地模式</span>
              <small>WorkCove v0.1</small>
            </div>
          </div>
        </aside>

        <section class="workspace-main">
          <header class="workspace-toolbar">
            <div class="global-search">
              <Search :size="15" aria-hidden="true" />
              <input
                ref="searchInput"
                v-model="searchQuery"
                type="search"
                aria-label="搜索任务和笔记"
                placeholder="搜索任务与笔记"
              />
              <kbd>⌘ K</kbd>

              <div v-if="searchQuery.trim()" class="search-results">
                <p v-if="searchResults.length === 0" class="search-empty">
                  没有找到相关内容
                </p>
                <button
                  v-for="result in searchResults"
                  :key="result.id"
                  type="button"
                  @click="handleSearchSelection(result.type)"
                >
                  <CheckSquare2
                    v-if="result.type === 'task'"
                    :size="15"
                  />
                  <FileText v-else :size="15" />
                  <span>{{ result.title }}</span>
                  <small>{{ result.meta }}</small>
                </button>
              </div>
            </div>

            <div class="toolbar-actions">
              <button
                class="toolbar-icon"
                type="button"
                aria-label="通知"
                title="通知"
                @click="notify('今天没有新的提醒')"
              >
                <Bell :size="16" />
                <span class="notification-dot" />
              </button>

              <div class="profile-area">
                <button
                  class="profile-trigger"
                  type="button"
                  aria-haspopup="menu"
                  :aria-expanded="profileMenuOpen"
                  @click="profileMenuOpen = !profileMenuOpen"
                >
                  <span class="profile-avatar">
                    <img
                      v-if="session.profile?.avatarDataUrl"
                      :src="session.profile.avatarDataUrl"
                      alt=""
                    />
                    <template v-else>{{ displayInitial }}</template>
                  </span>
                  <span class="profile-trigger__copy">
                    <strong>{{ displayName }}</strong>
                    <small :title="accountLabel">{{ accountLabel }}</small>
                  </span>
                  <ChevronDown :size="13" />
                </button>

                <div
                  v-if="profileMenuOpen"
                  class="profile-popover"
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
                  <button type="button" role="menuitem" @click="selectView('settings')">
                    <Settings :size="15" />
                    设置
                  </button>
                  <button type="button" role="menuitem" @click="handleSignOut">
                    <LogOut :size="15" />
                    退出登录
                  </button>
                </div>
              </div>
            </div>
          </header>

          <div class="content-scroll">
            <div class="content-frame">
              <header class="page-heading">
                <div class="page-heading__copy">
                  <p class="page-eyebrow">{{ pageEyebrow }}</p>
                  <h1>{{ pageTitle }}</h1>
                  <p>{{ pageDescription }}</p>
                </div>
                <div
                  v-if="
                    activeView !== 'overview' &&
                    activeView !== 'profile' &&
                    activeView !== 'settings'
                  "
                  class="page-heading__actions"
                >
                  <button
                    class="ghost-action"
                    type="button"
                    @click="notify('本周暂无新的外部更新')"
                  >
                    <Clock3 :size="15" />
                    <span>最近更新</span>
                  </button>
                  <button
                    class="primary-action"
                    type="button"
                    @click="focusTaskComposer"
                  >
                    <Plus :size="16" :stroke-width="2.2" />
                    <span>新建任务</span>
                  </button>
                </div>
              </header>

              <WorkbenchHome
                v-if="activeView === 'overview'"
                @notify="notify"
              />

              <section v-else-if="activeView === 'tasks'" class="content-section">
                <header class="section-heading section-heading--bordered">
                  <div>
                    <p class="section-eyebrow">ALL TASKS</p>
                    <h2>全部任务</h2>
                  </div>
                  <div class="segmented-control" aria-label="任务筛选">
                    <button
                      type="button"
                      :class="{ active: taskFilter === 'all' }"
                      @click="taskFilter = 'all'"
                    >
                      全部
                    </button>
                    <button
                      type="button"
                      :class="{ active: taskFilter === 'open' }"
                      @click="taskFilter = 'open'"
                    >
                      待完成
                    </button>
                    <button
                      type="button"
                      :class="{ active: taskFilter === 'done' }"
                      @click="taskFilter = 'done'"
                    >
                      已完成
                    </button>
                  </div>
                </header>

                <div class="task-list task-list--roomy">
                  <TaskRow
                    v-for="task in filteredTasks"
                    :key="task.id"
                    :task="task"
                    @toggle="toggleTask"
                  />
                </div>

                <div v-if="filteredTasks.length === 0" class="empty-state">
                  <Check :size="22" />
                  <p>这里暂时没有任务</p>
                </div>

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

              <section v-else-if="activeView === 'notes'" class="notes-page">
                <form class="notes-editor" @submit.prevent="addNote">
                  <div class="notes-editor__heading">
                    <div>
                      <p class="section-eyebrow">QUICK NOTE</p>
                      <h2>快速记录</h2>
                    </div>
                    <NotebookPen :size="18" />
                  </div>
                  <textarea
                    v-model="noteDraft"
                    rows="4"
                    aria-label="笔记内容"
                    placeholder="写下一段想法、会议记录或待整理内容…"
                  />
                  <div class="notes-editor__footer">
                    <span>UTF-8 · Markdown</span>
                    <button type="submit" :disabled="!noteDraft.trim()">
                      <Plus :size="15" />
                      保存笔记
                    </button>
                  </div>
                </form>

                <div class="notes-grid">
                  <article
                    v-for="note in notes"
                    :key="note.id"
                    class="note-card"
                    :class="`note-card--${note.tone}`"
                  >
                    <div class="note-card__top">
                      <span class="note-card__type">NOTE</span>
                      <time>{{ note.updatedAt }}</time>
                    </div>
                    <h3>{{ note.title }}</h3>
                    <p>{{ note.excerpt }}</p>
                    <button type="button" aria-label="打开笔记">
                      <ArrowUpRight :size="15" />
                    </button>
                  </article>
                </div>
              </section>

              <section v-else-if="activeView === 'calendar'" class="calendar-page">
                <div class="calendar-board">
                  <header class="calendar-board__header">
                    <div>
                      <p class="section-eyebrow">THIS WEEK</p>
                      <h2>{{ monthTitle }}</h2>
                    </div>
                    <span>{{ calendarWeek[0].day }} 至 {{ calendarWeek[6].day }}</span>
                  </header>

                  <div class="calendar-week-head">
                    <div
                      v-for="day in calendarWeek"
                      :key="day.key"
                      :class="{ 'calendar-day--today': day.isToday }"
                    >
                      <span>{{ day.weekday }}</span>
                      <strong>{{ day.day }}</strong>
                    </div>
                  </div>

                  <div class="calendar-grid">
                    <div
                      v-for="index in 7"
                      :key="index"
                      :class="{ 'calendar-day--today': calendarWeek[index - 1].isToday }"
                    >
                      <span
                        v-if="index === 2"
                        class="calendar-event calendar-event--green"
                      >
                        10:00 产品同步会
                      </span>
                      <span
                        v-if="index === 4"
                        class="calendar-event calendar-event--coral"
                      >
                        14:30 设计评审
                      </span>
                      <span
                        v-if="index === 4"
                        class="calendar-event calendar-event--amber"
                      >
                        17:00 日终回顾
                      </span>
                    </div>
                  </div>
                </div>

                <aside class="calendar-agenda">
                  <header class="section-heading">
                    <div>
                      <p class="section-eyebrow">TUESDAY</p>
                      <h2>今日安排</h2>
                    </div>
                    <span class="calendar-agenda__count">
                      {{ schedule.length }}
                    </span>
                  </header>
                  <div class="agenda-list agenda-list--large">
                    <article
                      v-for="item in schedule"
                      :key="item.time"
                      class="agenda-item"
                    >
                      <time>{{ item.time }}</time>
                      <span
                        class="agenda-item__line"
                        :class="`agenda-item__line--${item.tone}`"
                      />
                      <div>
                        <h3>{{ item.title }}</h3>
                        <p>{{ item.detail }}</p>
                      </div>
                    </article>
                  </div>
                </aside>
              </section>

              <section v-else-if="activeView === 'projects'" class="projects-page">
                <div class="projects-header">
                  <div>
                    <p class="section-eyebrow">ACTIVE PROJECTS</p>
                    <h2>正在进行</h2>
                  </div>
                  <span>{{ projects.length }} 个项目</span>
                </div>

                <div class="project-list">
                  <article
                    v-for="project in projects"
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
              </section>

              <section v-else-if="activeView === 'favorites'" class="favorites-page">
                <div class="favorites-heading">
                  <div>
                    <p class="section-eyebrow">PINNED</p>
                    <h2>常用入口</h2>
                  </div>
                  <button type="button" @click="notify('收藏入口已准备添加')">
                    <Plus :size="15" />
                    添加收藏
                  </button>
                </div>
                <div class="bookmark-grid">
                  <a
                    v-for="bookmark in bookmarks"
                    :key="bookmark.id"
                    href="#"
                    class="bookmark-card"
                    @click.prevent
                  >
                    <span class="bookmark-card__icon">
                      <Globe2 v-if="bookmark.category === '文档'" :size="18" />
                      <FileText v-else-if="bookmark.category === '设计'" :size="18" />
                      <Bookmark v-else :size="18" />
                    </span>
                    <div>
                      <h3>{{ bookmark.title }}</h3>
                      <p>{{ bookmark.url }}</p>
                    </div>
                    <span class="bookmark-card__category">
                      {{ bookmark.category }}
                    </span>
                  </a>
                </div>
              </section>

              <ProfileEditor
                v-else-if="activeView === 'profile'"
                @saved="notify('个人资料已保存')"
              />

              <section v-else class="settings-page">
                <div class="settings-group">
                  <header>
                    <p class="section-eyebrow">APPEARANCE</p>
                    <h2>外观</h2>
                  </header>
                  <div class="appearance-row">
                    <div>
                      <strong>颜色模式</strong>
                      <span>选择工作台使用浅色或深色外观。</span>
                    </div>
                    <div
                      class="theme-segmented"
                      role="radiogroup"
                      aria-label="颜色模式"
                    >
                      <button
                        v-for="option in themeOptions"
                        :key="option.value"
                        type="button"
                        role="radio"
                        :aria-checked="themeStore.preference === option.value"
                        :class="{
                          'theme-segmented__option--active':
                            themeStore.preference === option.value,
                        }"
                        @click="themeStore.setPreference(option.value)"
                      >
                        {{ option.label }}
                      </button>
                    </div>
                  </div>
                </div>

                <div class="settings-group">
                  <header>
                    <p class="section-eyebrow">GENERAL</p>
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

                <div class="settings-group">
                  <header>
                    <p class="section-eyebrow">LOCAL DATA</p>
                    <h2>本地数据</h2>
                  </header>
                  <div class="setting-row">
                    <div>
                      <strong>数据库</strong>
                      <span>MySQL · 127.0.0.1:3306</span>
                    </div>
                    <span class="setting-status">待接入</span>
                  </div>
                  <div class="setting-row">
                    <div>
                      <strong>应用版本</strong>
                      <span>WorkCove 0.1.0</span>
                    </div>
                    <span class="setting-status setting-status--neutral">
                      原型
                    </span>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </section>
      </div>

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
  padding: 14px;
  overflow: hidden;
  background:
    linear-gradient(125deg, #dfe4de 0%, #e8e5de 48%, #dce2df 100%);
  place-items: center;
}

.workbench-window {
  position: relative;
  display: flex;
  width: min(100%, 1480px);
  height: min(100%, 960px);
  overflow: hidden;
  flex-direction: column;
  background: var(--canvas);
  border: 1px solid rgb(31 41 38 / 15%);
  border-radius: 14px;
  box-shadow:
    0 24px 64px rgb(35 44 38 / 17%),
    0 2px 10px rgb(35 44 38 / 8%),
    inset 0 0 0 1px rgb(255 255 255 / 48%);
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
  width: 218px;
  min-width: 218px;
  flex-direction: column;
  padding: 20px 12px 14px;
  background: var(--sidebar);
  border-right: 1px solid var(--line);
  backdrop-filter: blur(26px) saturate(1.15);
}

.sidebar-brand {
  display: flex;
  height: 42px;
  align-items: center;
  gap: 11px;
  padding: 0 8px;
  margin-bottom: 24px;
}

.sidebar-brand__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.sidebar-brand__copy span {
  color: #29302c;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.2;
}

.sidebar-brand__copy small {
  margin-top: 3px;
  color: #969c97;
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.12em;
}

.sidebar-navigation {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sidebar-label {
  margin: 0 0 7px;
  padding: 0 10px;
  color: #989e99;
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 0.1em;
}

.sidebar-label--spaced {
  margin-top: 23px;
}

.nav-item {
  display: flex;
  width: 100%;
  height: 34px;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  color: #626963;
  font-size: 12px;
  font-weight: 520;
  text-align: left;
  background: transparent;
  border-radius: 7px;
  transition:
    color 120ms ease,
    background 120ms ease;
}

.nav-item:hover {
  color: #343a36;
  background: rgb(31 41 38 / 4%);
}

.nav-item--active {
  color: #234f41;
  font-weight: 620;
  background: rgb(255 255 255 / 67%);
  box-shadow:
    0 1px 2px rgb(31 41 38 / 7%),
    inset 0 0 0 1px rgb(31 41 38 / 4%);
}

.nav-item--active:hover {
  color: #234f41;
  background: rgb(255 255 255 / 76%);
}

.nav-badge {
  min-width: 18px;
  margin-left: auto;
  padding: 1px 5px;
  color: #7e857f;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  text-align: center;
  background: rgb(31 41 38 / 6%);
  border-radius: 4px;
}

.sidebar-spacer {
  flex: 1;
}

.sidebar-storage {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 10px 6px;
  border-top: 1px solid rgb(31 41 38 / 7%);
}

.sidebar-storage__dot {
  width: 6px;
  height: 6px;
  background: #6f967f;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(111 150 127 / 10%);
}

.sidebar-storage div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.sidebar-storage span:not(.sidebar-storage__dot) {
  color: #6c746e;
  font-size: 10px;
  font-weight: 560;
  line-height: 1.4;
}

.sidebar-storage small {
  color: #9ba19c;
  font-size: 8px;
  line-height: 1.4;
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

.global-search {
  position: relative;
  display: flex;
  width: min(100%, 300px);
  height: 30px;
  align-items: center;
  gap: 8px;
  padding: 0 9px;
  color: #9ba19c;
  background: rgb(31 41 38 / 4%);
  border: 1px solid transparent;
  border-radius: 7px;
  transition:
    width 180ms ease,
    background 150ms ease,
    border-color 150ms ease;
}

.global-search:focus-within {
  width: min(100%, 360px);
  background: #fff;
  border-color: rgb(47 111 94 / 30%);
  box-shadow: 0 4px 18px rgb(31 41 38 / 7%);
}

.global-search input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: 11px;
  background: transparent;
  border: 0;
}

.global-search input::-webkit-search-cancel-button {
  display: none;
}

.global-search kbd {
  flex: 0 0 auto;
  padding: 2px 5px;
  color: #9aa09b;
  font-family: inherit;
  font-size: 8px;
  background: rgb(255 255 255 / 60%);
  border: 1px solid rgb(31 41 38 / 7%);
  border-radius: 4px;
}

.search-results {
  position: absolute;
  top: calc(100% + 7px);
  left: 0;
  display: flex;
  width: 100%;
  overflow: hidden;
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
  min-height: 35px;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
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

.search-results button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-results button small {
  color: #9ba19c;
  font-size: 8px;
}

.search-empty {
  margin: 0;
  padding: 15px 10px;
  color: #999f9a;
  font-size: 10px;
  text-align: center;
}

.toolbar-actions {
  display: flex;
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

.profile-area {
  position: relative;
}

.profile-trigger {
  display: flex;
  height: 38px;
  align-items: center;
  gap: 8px;
  padding: 0 6px;
  color: #667069;
  background: transparent;
  border-radius: 7px;
}

.profile-trigger:hover {
  background: rgb(31 41 38 / 4%);
}

.profile-avatar {
  display: grid;
  width: 26px;
  height: 26px;
  overflow: hidden;
  flex: 0 0 auto;
  color: #eaf0ec;
  font-size: 10px;
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

.profile-trigger__copy {
  display: flex;
  max-width: 124px;
  flex-direction: column;
  text-align: left;
}

.profile-trigger__copy strong {
  color: #4a514c;
  font-size: 10px;
  font-weight: 620;
  line-height: 1.3;
}

.profile-trigger__copy small {
  overflow: hidden;
  color: #a0a5a1;
  font-size: 8px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  font-size: 8px;
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
  font-size: 10px;
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

.content-frame {
  width: min(100%, 1130px);
  padding: 34px 38px 48px;
  margin: 0 auto;
}

/* Page heading */
.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: 24px;
}

.page-heading__copy {
  min-width: 0;
}

.page-eyebrow {
  margin: 0 0 9px;
  color: #829188;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.page-heading h1 {
  margin: 0;
  color: #1f2722;
  font-family: "SF Pro Display", "PingFang SC", "Helvetica Neue", sans-serif;
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

.ghost-action,
.primary-action {
  display: inline-flex;
  height: 34px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 12px;
  font-size: 10px;
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
  font-family: "SF Pro Display", "Helvetica Neue", sans-serif;
  font-size: 22px;
  font-weight: 580;
  font-variant-numeric: tabular-nums;
}

.summary-metric span {
  color: #818882;
  font-size: 9px;
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
  font-size: 9px;
}

.task-progress__copy strong {
  color: #4d5a53;
  font-size: 10px;
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
  font-size: 8px;
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
  font-family: "SF Pro Display", "PingFang SC", "Helvetica Neue", sans-serif;
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
  font-size: 9px;
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
  font-size: 9px;
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
  font-size: 10px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-note time {
  align-self: start;
  padding-top: 3px;
  color: #a2a8a3;
  font-size: 8px;
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
  font-size: 10px;
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
  font-size: 9px;
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
  font-size: 9px;
  font-weight: 600;
}

.focus-card__state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #9db8a9;
  font-size: 8px;
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
  font-family: "SF Pro Display", "Helvetica Neue", sans-serif;
  font-size: 27px;
  font-weight: 480;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.focus-dial__inner span {
  margin-top: 4px;
  color: #91a79b;
  font-size: 8px;
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
  font-size: 8px;
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
  font-size: 8px;
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
  font-size: 8px;
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
  font-size: 9px;
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
  font-size: 10px;
  font-weight: 600;
  line-height: 1.4;
}

.agenda-item p {
  margin: 3px 0 0;
  color: #949b95;
  font-size: 8px;
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
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-mini-list strong {
  color: #888f89;
  font-size: 8px;
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
  font-size: 9px;
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
  font-size: 9px;
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
  font-size: 8px;
}

.notes-editor__footer button {
  display: inline-flex;
  height: 29px;
  align-items: center;
  gap: 5px;
  padding: 0 11px;
  color: #fff;
  font-size: 9px;
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
  font-size: 7px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.note-card time {
  color: #a1a7a2;
  font-size: 8px;
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
  font-size: 10px;
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
  font-size: 9px;
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
  font-size: 8px;
}

.calendar-week-head strong {
  display: grid;
  width: 23px;
  height: 23px;
  color: #5e665f;
  font-size: 10px;
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
  font-size: 7px;
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
  font-size: 9px;
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
  font-size: 9px;
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
  font-size: 9px;
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
  font-size: 9px;
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
  font-size: 9px;
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
  font-size: 9px;
}

.bookmark-card__category {
  position: absolute;
  bottom: 14px;
  left: 16px;
  color: #9ca29d;
  font-size: 8px;
}

/* Settings */
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 26px;
  animation: content-arrive 260ms ease both;
}

.settings-group {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.settings-group header {
  padding: 14px 16px 12px;
  background: #f2f1ed;
  border-bottom: 1px solid var(--line-soft);
}

.settings-group h2 {
  font-size: 13px;
}

.setting-row {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.setting-row:last-child {
  border-bottom: 0;
}

.setting-row > div {
  display: flex;
  flex-direction: column;
}

.setting-row strong {
  color: #424a44;
  font-size: 10px;
  font-weight: 600;
}

.setting-row span {
  margin-top: 4px;
  color: #979e98;
  font-size: 9px;
}

.setting-row input[type="checkbox"] {
  width: 32px;
  height: 18px;
  margin: 0;
  appearance: none;
  background: #d9dbd7;
  border-radius: 999px;
  box-shadow: inset 0 1px 2px rgb(31 41 38 / 10%);
  transition: background 150ms ease;
}

.setting-row input[type="checkbox"]::after {
  display: block;
  width: 14px;
  height: 14px;
  margin: 2px;
  content: "";
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgb(31 41 38 / 20%);
  transition: transform 150ms ease;
}

.setting-row input[type="checkbox"]:checked {
  background: var(--accent);
}

.setting-row input[type="checkbox"]:checked::after {
  transform: translateX(14px);
}

.setting-status {
  flex: 0 0 auto;
  padding: 3px 7px;
  margin-top: 0 !important;
  color: #94654e !important;
  font-size: 8px !important;
  background: #f1e7e1;
  border-radius: 5px;
}

.setting-status--neutral {
  color: #69726b !important;
  background: #eceeeb;
}

/* Global feedback */
.app-toast {
  position: absolute;
  z-index: 50;
  bottom: 24px;
  left: 50%;
  display: flex;
  height: 34px;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  color: #edf4ef;
  font-size: 10px;
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
    width: 218px;
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

  .profile-trigger__copy,
  .profile-trigger > svg {
    display: none;
  }

  .profile-trigger {
    padding: 0 3px;
  }

  .content-frame {
    padding: 25px 20px 40px;
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
    bottom: 18px;
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
  gap: 24px;
  padding: 12px 16px;
}

.appearance-row > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.appearance-row strong {
  color: var(--text);
  font-size: 10px;
  font-weight: 600;
}

.appearance-row span {
  margin-top: 4px;
  color: var(--text-subtle);
  font-size: 9px;
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
  font-size: 10px;
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
    background:
      linear-gradient(125deg, #eef2f5 0%, #f6f8fa 48%, #e9edf2 100%);
  }

  .workbench-window {
    border-color: var(--line);
    box-shadow:
      0 22px 60px rgb(31 35 40 / 14%),
      0 2px 10px rgb(31 35 40 / 8%),
      inset 0 0 0 1px rgb(255 255 255 / 65%);
  }

  .workbench-sidebar {
    background: var(--sidebar);
  }

  .sidebar-brand__copy small,
  .sidebar-label,
  .sidebar-storage small,
  .page-eyebrow,
  .section-eyebrow,
  .rail-heading p,
  .note-card__type {
    font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
    color: var(--accent-strong);
    background: var(--accent-soft);
    box-shadow: inset 2px 0 0 var(--accent);
  }

  .nav-item--active svg {
    color: var(--accent);
  }

  .nav-badge {
    color: #59636e;
    background: rgb(31 35 40 / 6%);
  }

  .nav-item--active .nav-badge {
    color: var(--accent-strong);
    background: rgb(9 105 218 / 10%);
  }

  .sidebar-storage__dot {
    background: var(--success);
    box-shadow: 0 0 0 3px rgb(26 127 55 / 10%);
  }

  .workspace-toolbar {
    background: rgb(255 255 255 / 88%);
  }

  .global-search {
    color: #818b98;
    background: #f6f8fa;
    border-color: #d8dee4;
  }

  .global-search:focus-within {
    background: #ffffff;
    border-color: var(--accent);
  }

  .global-search input {
    color: var(--text);
  }

  .global-search kbd {
    color: #6e7781;
    background: #ffffff;
    border-color: #d0d7de;
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

  .profile-trigger__copy strong {
    color: var(--text);
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
    font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  background:
    linear-gradient(125deg, #070b10 0%, #0d141c 48%, #080d13 100%);
}

.workbench-window {
  color-scheme: dark;
  background: var(--canvas);
  border-color: rgb(240 246 252 / 13%);
  box-shadow:
    0 28px 76px rgb(0 0 0 / 58%),
    0 2px 12px rgb(0 0 0 / 38%),
    inset 0 0 0 1px rgb(255 255 255 / 4%);
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
}

.sidebar-label {
  color: #6e7681;
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  color: #f0f6fc;
  background: rgb(56 139 253 / 14%);
  box-shadow:
    inset 2px 0 0 var(--accent),
    inset 0 0 0 1px rgb(88 166 255 / 8%);
}

.nav-item--active svg {
  color: var(--accent);
}

.nav-badge {
  color: #8b949e;
  background: rgb(240 246 252 / 7%);
}

.nav-item--active .nav-badge {
  color: #79c0ff;
  background: rgb(88 166 255 / 12%);
}

.sidebar-storage {
  border-top-color: var(--line-soft);
}

.sidebar-storage__dot {
  background: var(--success);
  box-shadow: 0 0 0 3px rgb(63 185 80 / 12%);
}

.sidebar-storage span:not(.sidebar-storage__dot) {
  color: #8b949e;
}

.sidebar-storage small {
  color: #586069;
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
}

.workspace-main {
  background: var(--canvas-raised);
}

.workspace-toolbar {
  background: rgb(13 17 23 / 86%);
  border-bottom-color: var(--line-soft);
}

.global-search {
  color: #6e7681;
  background: #0d1117;
  border-color: rgb(240 246 252 / 9%);
}

.global-search:focus-within {
  background: #0d1117;
  border-color: rgb(88 166 255 / 46%);
  box-shadow:
    0 0 0 3px rgb(88 166 255 / 10%),
    0 6px 20px rgb(0 0 0 / 24%);
}

.global-search input {
  color: var(--text);
}

.global-search kbd {
  color: #6e7681;
  background: #161b22;
  border-color: var(--line);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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

.profile-trigger__copy strong {
  color: #c9d1d9;
}

.profile-trigger__copy small {
  color: #6e7681;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
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
  font-size: 8px;
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
  border-color: var(--line);
}

.settings-group header {
  background: #161b22;
  border-bottom-color: var(--line-soft);
}

.setting-row {
  border-bottom-color: var(--line-soft);
}

.setting-row strong {
  color: #e6edf3;
}

.setting-row span {
  color: #6e7681;
}

.setting-row input[type="checkbox"] {
  background: #30363d;
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 35%);
}

.setting-row input[type="checkbox"]::after {
  background: #8b949e;
  box-shadow: 0 1px 3px rgb(0 0 0 / 32%);
}

.setting-row input[type="checkbox"]:checked {
  background: #1f6feb;
}

.setting-row input[type="checkbox"]:checked::after {
  background: #fff;
}

.setting-status {
  color: #e3b341 !important;
  background: rgb(210 153 34 / 12%);
}

.setting-status--neutral {
  color: #8b949e !important;
  background: rgb(240 246 252 / 7%);
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
  color: #e6edf3;
}

.appearance-row span {
  color: #6e7681;
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
