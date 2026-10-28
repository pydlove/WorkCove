<script setup lang="ts">
import {
  AppWindow,
  ArrowRightLeft,
  Check,
  ListChecks,
  Pencil,
  Play,
  Plus,
  Power,
  Search,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import ShortcutDialog from '@/components/business/workbench/ShortcutDialog.vue'
import ShortcutVisual from '@/components/business/workbench/ShortcutVisual.vue'
import WorkspaceBatchBar from '@/components/business/workspaces/WorkspaceBatchBar.vue'
import WorkspaceTransferDialog, {
  type WorkspaceTransferMode,
} from '@/components/business/workspaces/WorkspaceTransferDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import {
  forceQuitHostApplication,
  getHostApplicationStatuses,
  openHostApplication,
} from '@/api/hostApplications'
import {
  shortcutIconUrls,
  sortApplicationsByUsage,
} from '@/services/localShortcuts'
import { useAuthStore } from '@/stores/useAuthStore'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  ApplicationShortcut,
  ShortcutInput,
} from '@/types/shortcut'

const emit = defineEmits<{
  notify: [message: string]
}>()

const APPLICATION_STATUS_POLL_MS = 5000

const session = useAuthStore()
const shortcuts = useShortcutStore()
const workspaces = useWorkspaceStore()
const searchQuery = ref('')
const dialogOpen = ref(false)
const editingApplication = ref<ApplicationShortcut | null>(null)
const openingId = ref('')
const forceQuitTarget = ref<ApplicationShortcut | null>(null)
const transferApplication = ref<ApplicationShortcut | null>(null)
const selectionMode = ref(false)
const selectedApplicationIds = ref<string[]>([])
const batchTransferMode = ref<WorkspaceTransferMode | null>(null)
const forceQuitting = ref(false)
const deleteTargets = ref<ApplicationShortcut[]>([])
const deletingApplications = ref(false)
const applicationStatuses = ref<Record<string, boolean>>({})
let statusPollTimer: number | null = null
let statusRefreshTimer: number | null = null
let statusGeneration = 0
let statusFailureCount = 0

const workspaceApplications = computed(() =>
  shortcuts.applications.filter(
    (application) =>
      application.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const filteredApplications = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  const applications = sortApplicationsByUsage(
    workspaceApplications.value,
  )
  if (!query) return applications
  return applications.filter((application) =>
    `${application.name} ${application.path}`
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})

const runningApplicationCount = computed(
  () => filteredApplications.value.filter(isApplicationRunning).length,
)
const {
  currentPage,
  pageItems: pagedApplications,
  setPage,
} = usePagination(filteredApplications, () => searchQuery.value)

watch(
  () => session.email,
  (email) => {
    shortcuts.load(email)
    workspaces.load(email)
  },
  { immediate: true },
)

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    selectionMode.value = false
    selectedApplicationIds.value = []
  },
)

watch(
  () =>
    workspaceApplications.value
      .map((application) => application.path)
      .join('\n'),
  () => {
    applicationStatuses.value = {}
    statusFailureCount = 0
    void refreshApplicationStatuses()
  },
  { immediate: true },
)

function isApplicationRunning(application: ApplicationShortcut) {
  return applicationStatuses.value[application.path] === true
}

function hasApplicationStatus(application: ApplicationShortcut) {
  return Object.hasOwn(applicationStatuses.value, application.path)
}

function applicationStatusLabel(application: ApplicationShortcut) {
  if (!hasApplicationStatus(application)) return '检测中'
  return isApplicationRunning(application) ? '已打开' : '已关闭'
}

async function refreshApplicationStatuses() {
  const paths = [
    ...new Set(
      workspaceApplications.value.map((application) => application.path),
    ),
  ]
  const generation = ++statusGeneration
  if (paths.length === 0) {
    applicationStatuses.value = {}
    return
  }

  try {
    const statuses = await getHostApplicationStatuses(paths)
    if (generation !== statusGeneration) return
    applicationStatuses.value = Object.fromEntries(
      statuses.map((status) => [status.path, status.running]),
    )
    statusFailureCount = 0
  } catch {
    if (generation === statusGeneration) {
      statusFailureCount += 1
      if (statusFailureCount >= 3) applicationStatuses.value = {}
    }
  }
}

function scheduleStatusRefresh(delay = 700) {
  if (statusRefreshTimer !== null) {
    window.clearTimeout(statusRefreshTimer)
  }
  statusRefreshTimer = window.setTimeout(() => {
    statusRefreshTimer = null
    void refreshApplicationStatuses()
  }, delay)
}

function addApplication() {
  editingApplication.value = null
  dialogOpen.value = true
}

function editApplication(application: ApplicationShortcut) {
  editingApplication.value = application
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  editingApplication.value = null
}

function saveApplication(input: ShortcutInput) {
  shortcuts.saveShortcut({
    ...input,
    workspaceId: workspaces.activeWorkspaceId,
  })
  emit('notify', input.id ? '应用已更新' : '应用已添加')
  closeDialog()
}

function removeApplication(id: string) {
  try {
    const removed = shortcuts.removeApplications([id])
    if (removed === 0) {
      emit('notify', '应用记录不存在，请刷新后再试')
      return
    }
    emit('notify', 'App 记录已删除，本地应用未受影响')
    closeDialog()
  } catch (error) {
    emit(
      'notify',
      `删除失败：${
        error instanceof Error ? error.message : '请稍后重试'
      }`,
    )
  }
}

async function openApplication(application: ApplicationShortcut) {
  if (openingId.value) return

  const wasRunning = isApplicationRunning(application)
  openingId.value = application.id
  try {
    const error = await openHostApplication(application.path)
    if (error) {
      emit('notify', `无法打开应用：${error}`)
      return
    }
    shortcuts.recordApplicationOpen(application.id)
    applicationStatuses.value = {
      ...applicationStatuses.value,
      [application.path]: true,
    }
    emit(
      'notify',
      wasRunning
        ? `已将「${application.name}」切换到前台`
        : `已打开「${application.name}」`,
    )
    scheduleStatusRefresh()
  } finally {
    openingId.value = ''
  }
}

function requestForceQuit(application: ApplicationShortcut) {
  forceQuitTarget.value = application
}

async function confirmForceQuit() {
  const application = forceQuitTarget.value
  if (!application || forceQuitting.value) return

  forceQuitting.value = true
  try {
    const error = await forceQuitHostApplication(application.path)
    if (error) {
      emit('notify', `无法退出应用：${error}`)
      return
    }
    applicationStatuses.value = {
      ...applicationStatuses.value,
      [application.path]: false,
    }
    emit('notify', `已强制退出「${application.name}」`)
    forceQuitTarget.value = null
    scheduleStatusRefresh(400)
  } finally {
    forceQuitting.value = false
  }
}

function handleApplicationTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const application = transferApplication.value
  if (!application) return
  const targetWorkspace = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!targetWorkspace) {
    emit('notify', '目标工作空间不存在')
    return
  }

  try {
    const changed =
      mode === 'copy'
        ? shortcuts.copyApplications(
            [application.id],
            targetWorkspaceId,
          )
        : shortcuts.moveApplications(
            [application.id],
            targetWorkspaceId,
          )
    if (changed === 0) {
      emit('notify', '未找到可处理的应用，请刷新后再试')
      return
    }

    transferApplication.value = null
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
    emit(
      'notify',
      mode === 'copy'
        ? `已将「${application.name}」复制到「${targetWorkspace.name}」`
        : `已将「${application.name}」移动到「${targetWorkspace.name}」`,
    )
  } catch (error) {
    emit(
      'notify',
      `${mode === 'copy' ? '复制' : '移动'}失败：${
        error instanceof Error ? error.message : '请稍后重试'
      }`,
    )
  }
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) selectedApplicationIds.value = []
}

function setApplicationSelected(id: string, selected: boolean) {
  selectedApplicationIds.value = selected
    ? [...new Set([...selectedApplicationIds.value, id])]
    : selectedApplicationIds.value.filter((item) => item !== id)
}

function toggleSelectAllApplications() {
  selectedApplicationIds.value =
    selectedApplicationIds.value.length ===
    filteredApplications.value.length
      ? []
      : filteredApplications.value.map((application) => application.id)
}

function startBatchTransfer(mode: WorkspaceTransferMode) {
  if (selectedApplicationIds.value.length === 0) return
  batchTransferMode.value = mode
}

function requestDeleteApplication(application: ApplicationShortcut) {
  deleteTargets.value = [application]
}

function requestDeleteSelectedApplications() {
  const selectedIds = new Set(selectedApplicationIds.value)
  const applications = workspaceApplications.value.filter((application) =>
    selectedIds.has(application.id),
  )
  if (applications.length === 0) {
    emit('notify', '请先选择要删除的应用')
    return
  }
  deleteTargets.value = applications
}

function cancelDeleteApplications() {
  if (deletingApplications.value) return
  deleteTargets.value = []
}

function confirmDeleteApplications() {
  if (deletingApplications.value || deleteTargets.value.length === 0) return

  const targets = [...deleteTargets.value]
  deletingApplications.value = true
  try {
    const removed = shortcuts.removeApplications(
      targets.map((application) => application.id),
    )
    if (removed === 0) {
      emit('notify', '未找到可删除的应用，请刷新后再试')
      return
    }

    deleteTargets.value = []
    selectionMode.value = false
    selectedApplicationIds.value = []
    emit(
      'notify',
      removed === 1
        ? `已删除「${targets[0].name}」的 App 记录`
        : `已删除 ${removed} 条 App 记录`,
    )
  } catch (error) {
    emit(
      'notify',
      `删除失败：${
        error instanceof Error ? error.message : '请稍后重试'
      }`,
    )
  } finally {
    deletingApplications.value = false
  }
}

function handleApplicationBatchTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const selectedIds = [...selectedApplicationIds.value]
  const targetWorkspace = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!targetWorkspace) {
    emit('notify', '目标工作空间不存在')
    return
  }

  try {
    const changed =
      mode === 'copy'
        ? shortcuts.copyApplications(selectedIds, targetWorkspaceId)
        : shortcuts.moveApplications(selectedIds, targetWorkspaceId)
    if (changed === 0) {
      emit('notify', '没有可处理的应用，请重新选择后再试')
      return
    }

    batchTransferMode.value = null
    selectionMode.value = false
    selectedApplicationIds.value = []
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
    emit(
      'notify',
      `已将 ${changed} 个应用${mode === 'copy' ? '复制' : '移动'}到「${targetWorkspace.name}」`,
    )
  } catch (error) {
    emit(
      'notify',
      `${mode === 'copy' ? '复制' : '移动'}失败：${
        error instanceof Error ? error.message : '请稍后重试'
      }`,
    )
  }
}

function handleWindowFocus() {
  void refreshApplicationStatuses()
}

function handleVisibilityChange() {
  if (!document.hidden) void refreshApplicationStatuses()
}

onMounted(() => {
  statusPollTimer = window.setInterval(() => {
    if (!document.hidden) void refreshApplicationStatuses()
  }, APPLICATION_STATUS_POLL_MS)
  window.addEventListener('focus', handleWindowFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  statusGeneration += 1
  if (statusPollTimer !== null) window.clearInterval(statusPollTimer)
  if (statusRefreshTimer !== null) window.clearTimeout(statusRefreshTimer)
  window.removeEventListener('focus', handleWindowFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <section class="application-library">
    <header class="application-toolbar">
      <span class="application-toolbar__count">
        {{ filteredApplications.length }} 个应用
        <span v-if="runningApplicationCount > 0" class="application-toolbar__running">
          · {{ runningApplicationCount }} 个已打开
        </span>
      </span>
      <label class="application-search">
        <Search :size="15" aria-hidden="true" />
        <input
          v-model="searchQuery"
          type="search"
          aria-label="搜索应用"
          placeholder="搜索应用名称或路径"
        />
      </label>
      <UiButton @click="toggleSelectionMode">
        <ListChecks :size="14" />
        {{ selectionMode ? '退出批量' : '批量' }}
      </UiButton>
      <UiButton variant="primary" @click="addApplication">
        <Plus :size="15" />
        添加应用
      </UiButton>
    </header>

    <WorkspaceBatchBar
      v-if="selectionMode"
      :count="selectedApplicationIds.length"
      :total="filteredApplications.length"
      show-delete
      @toggle-select-all="toggleSelectAllApplications"
      @copy="startBatchTransfer('copy')"
      @move="startBatchTransfer('move')"
      @delete="requestDeleteSelectedApplications"
      @cancel="toggleSelectionMode"
    />

    <div
      v-if="filteredApplications.length > 0"
      class="application-list"
    >
      <article
        v-for="application in pagedApplications"
        :key="application.id"
        class="application-row"
        :class="{ 'application-row--selecting': selectionMode }"
      >
        <UiCheckbox
          v-if="selectionMode"
          :model-value="selectedApplicationIds.includes(application.id)"
          :label="`选择应用 ${application.name}`"
          @update:model-value="
            setApplicationSelected(application.id, $event)
          "
        />
        <div
          class="application-row__main"
        >
          <ShortcutVisual
            :sources="shortcutIconUrls(application)"
            mode="application"
            :size="38"
          />
          <span class="application-row__copy">
            <span class="application-row__title">
              <strong>{{ application.name }}</strong>
            </span>
            <small :title="application.path">{{ application.path }}</small>
          </span>
        </div>
        <span
          class="application-row__status"
          :class="{
            'application-row__status--running':
              isApplicationRunning(application),
            'application-row__status--pending':
              !hasApplicationStatus(application),
          }"
          :aria-label="`运行状态：${applicationStatusLabel(application)}`"
        >
          <i />
          {{ applicationStatusLabel(application) }}
        </span>
        <div class="application-row__actions">
          <span
            class="application-row__count"
            :title="
              application.lastOpenedAt
                ? `最后打开：${new Date(application.lastOpenedAt).toLocaleString('zh-CN')}`
                : '尚未从 8小时工作台打开'
            "
          >
            {{ application.clickCount }} 次
          </span>
          <button
            class="application-row__open"
            :class="{ 'application-row__open--running': isApplicationRunning(application) }"
            type="button"
            :aria-label="
              isApplicationRunning(application)
                ? `切换 ${application.name} 到前台`
                : `打开 ${application.name}`
            "
            :disabled="Boolean(openingId)"
            :title="
              isApplicationRunning(application)
                ? `「${application.name}」已打开，点击切换到前台`
                : `打开 ${application.name}`
            "
            @click="openApplication(application)"
          >
            <Check v-if="isApplicationRunning(application)" :size="13" />
            <Play v-else :size="13" />
            {{
              openingId === application.id
                ? '打开中…'
                : isApplicationRunning(application)
                  ? '已打开'
                  : '打开'
            }}
          </button>
          <button
            class="application-row__quit"
            type="button"
            :disabled="
              hasApplicationStatus(application) &&
              !isApplicationRunning(application)
            "
            :aria-label="`强制退出 ${application.name}`"
            :title="
              hasApplicationStatus(application) &&
              !isApplicationRunning(application)
                ? `${application.name} 未在运行`
                : `强制退出 ${application.name}`
            "
            @click="requestForceQuit(application)"
          >
            <Power :size="13" />
            强制退出
          </button>
          <button
            class="application-row__workspace"
            type="button"
            :aria-label="`处理 ${application.name} 的工作空间`"
            title="复制或移动到其他工作空间"
            @click="transferApplication = application"
          >
            <ArrowRightLeft :size="14" />
          </button>
          <button
            class="application-row__edit"
            type="button"
            :aria-label="`编辑 ${application.name}`"
            title="编辑应用"
            @click="editApplication(application)"
          >
            <Pencil :size="14" />
          </button>
          <button
            class="application-row__delete"
            type="button"
            :aria-label="`删除 App 记录 ${application.name}`"
            title="删除 App 记录，不会删除或卸载本地应用"
            @click="requestDeleteApplication(application)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="application-empty">
      <span>
        <AppWindow :size="28" />
      </span>
      <strong>
        {{
          workspaceApplications.length === 0
            ? '还没有添加应用'
            : '没有符合条件的应用'
        }}
      </strong>
      <p>
        {{
          workspaceApplications.length === 0
            ? '从系统中选择本地应用，8小时工作台会自动读取名称和图标。'
            : '换一个关键词再试一次。'
        }}
      </p>
      <UiButton
        v-if="workspaceApplications.length === 0"
        variant="primary"
        @click="addApplication"
      >
        <Plus :size="15" />
        添加第一个应用
      </UiButton>
    </div>

    <UiPagination
      :page="currentPage"
      :total="filteredApplications.length"
      label="应用分页"
      @update:page="setPage"
    />

    <ShortcutDialog
      v-if="dialogOpen"
      mode="application"
      :shortcut="editingApplication"
      @close="closeDialog"
      @save="saveApplication"
      @remove="removeApplication"
    />

    <UiDialog
      v-if="forceQuitTarget"
      title="强制退出应用"
      description="这会立即结束应用进程，未保存的内容可能丢失。"
      height="compact"
      @close="forceQuitTarget = null"
    >
      <div class="force-quit-confirm">
        <div class="force-quit-confirm__summary">
          <ShortcutVisual
            :sources="shortcutIconUrls(forceQuitTarget)"
            mode="application"
            :size="40"
          />
          <span>
            <strong>{{ forceQuitTarget.name }}</strong>
            <small :title="forceQuitTarget.path">
              {{ forceQuitTarget.path }}
            </small>
          </span>
        </div>
        <p>如果应用未在运行，系统会提示无法退出。</p>
      </div>

      <template #footer>
        <UiButton
          size="lg"
          :disabled="forceQuitting"
          @click="forceQuitTarget = null"
        >
          取消
        </UiButton>
        <UiButton
          variant="danger"
          size="lg"
          :disabled="forceQuitting"
          @click="confirmForceQuit"
        >
          <Power :size="14" />
          {{ forceQuitting ? '正在退出…' : '强制退出' }}
        </UiButton>
      </template>
    </UiDialog>

    <WorkspaceTransferDialog
      v-if="transferApplication"
      :item-label="`App「${transferApplication.name}」`"
      :source-workspace-id="transferApplication.workspaceId"
      @close="transferApplication = null"
      @transfer="handleApplicationTransfer"
    />

    <WorkspaceTransferDialog
      v-if="batchTransferMode"
      :item-label="`已选择 ${selectedApplicationIds.length} 个 App`"
      :source-workspace-id="workspaces.activeWorkspaceId"
      :initial-mode="batchTransferMode"
      @close="batchTransferMode = null"
      @transfer="handleApplicationBatchTransfer"
    />

    <UiConfirmDialog
      v-if="deleteTargets.length > 0"
      :title="
        deleteTargets.length === 1
          ? '删除 App 记录？'
          : `删除 ${deleteTargets.length} 条 App 记录？`
      "
      description="只会从 8小时工作台当前工作空间移除记录，不会删除或卸载本地 App。"
      warning="本地 App 文件保持不变；之后可以通过“添加应用”重新关联。"
      :confirm-label="
        deleteTargets.length === 1
          ? '删除记录'
          : `删除 ${deleteTargets.length} 条记录`
      "
      busy-label="正在删除…"
      :busy="deletingApplications"
      @close="cancelDeleteApplications"
      @confirm="confirmDeleteApplications"
    >
      <div class="application-delete-target">
        <template v-if="deleteTargets.length === 1">
          <ShortcutVisual
            :sources="shortcutIconUrls(deleteTargets[0])"
            mode="application"
            :size="36"
          />
          <span>
            <strong>{{ deleteTargets[0].name }}</strong>
            <small :title="deleteTargets[0].path">
              {{ deleteTargets[0].path }}
            </small>
          </span>
        </template>
        <span v-else>
          已选择 {{ deleteTargets.length }} 条 App 记录，删除后无法恢复。
        </span>
      </div>
    </UiConfirmDialog>
  </section>
</template>

<style scoped>
.application-library {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: application-enter var(--duration-window) var(--ease-apple) both;
}

.application-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.application-toolbar__count {
  flex: 1;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.application-toolbar__running {
  color: var(--system-green);
}

.application-search {
  display: flex;
  width: min(100%, 240px);
  height: var(--control-md);
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  color: var(--text-subtle);
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 4%);
}

.application-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.application-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.application-search input:focus-visible {
  outline: none;
}

.application-list {
  display: flex;
  min-height: 360px;
  padding: var(--space-2) 0;
  flex-direction: column;
}

.application-row {
  position: relative;
  display: grid;
  min-height: 64px;
  grid-template-columns: minmax(0, 1fr) 72px auto;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  transition: background var(--duration-fast) var(--ease-apple);
}

.application-row--selecting {
  grid-template-columns: 18px minmax(0, 1fr) 72px auto;
}

.application-row:last-child {
  border-bottom: 0;
}

.application-row:hover {
  background: var(--hover-bg);
}

.application-row__main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.application-row__actions {
  display: grid;
  grid-template-columns: 36px 72px 76px repeat(3, 26px);
  align-items: center;
  gap: var(--space-3);
}

.application-row__open:focus-visible,
.application-row__quit:focus-visible,
.application-row__workspace:focus-visible,
.application-row__edit:focus-visible,
.application-row__delete:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
}

.application-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.application-row__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.application-row__copy strong,
.application-row__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-row__copy strong {
  min-width: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.application-row__status {
  display: inline-flex;
  flex: 0 0 auto;
  width: 72px;
  align-items: center;
  gap: 6px;
  justify-self: start;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.application-row__status i {
  width: 6px;
  height: 6px;
  background: var(--text-subtle);
  border-radius: 50%;
}

.application-row__status--running {
  color: var(--system-green);
}

.application-row__status--running i {
  background: var(--system-green);
  box-shadow: 0 0 0 3px color-mix(
    in srgb,
    var(--system-green) 10%,
    transparent
  );
}

.application-row__status--pending {
  color: var(--system-orange);
}

.application-row__status--pending i {
  background: var(--system-orange);
}

.application-row__copy small {
  margin-top: 2px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.application-row__count {
  width: 36px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-align: right;
  white-space: nowrap;
}

.application-row__open {
  display: inline-flex;
  width: 72px;
  height: 26px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  color: var(--icon);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  white-space: nowrap;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.application-row__open:hover:not(:disabled) {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.application-row__open:disabled {
  cursor: default;
  opacity: 0.55;
}

.application-row__open--running {
  color: var(--text-muted);
}

.application-row__quit {
  display: inline-flex;
  width: 76px;
  height: 26px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0 7px;
  color: var(--icon);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-control);
  white-space: nowrap;
}

.application-row__quit:hover:not(:disabled) {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.application-row__quit:disabled {
  cursor: default;
  opacity: 0.35;
}

.application-row__workspace,
.application-row__edit,
.application-row__delete {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.application-row__workspace:hover,
.application-row__edit:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.application-row__delete:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.application-delete-target {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
}

.application-delete-target > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.application-delete-target strong,
.application-delete-target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-delete-target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.application-delete-target small {
  margin-top: 2px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
}

.application-empty {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.application-empty > span {
  display: grid;
  width: 52px;
  height: 52px;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.application-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.application-empty p {
  max-width: 360px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.force-quit-confirm {
  padding: 0 var(--space-5) var(--space-4);
}

.force-quit-confirm__summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--canvas);
  border-radius: var(--radius-control);
}

.force-quit-confirm__summary > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.force-quit-confirm__summary strong,
.force-quit-confirm__summary small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.force-quit-confirm__summary strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.force-quit-confirm__summary small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.force-quit-confirm p {
  margin: var(--space-3) 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 620px) {
  .application-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .application-toolbar__count {
    display: flex;
    flex-basis: 100%;
    align-items: center;
  }

  .application-search {
    width: auto;
    flex: 1;
  }

  .application-list {
    padding: var(--space-2) 0;
  }

  .application-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-3);
  }

  .application-row--selecting {
    grid-template-columns: 18px minmax(0, 1fr) auto;
  }

  .application-row--selecting .application-row__main {
    grid-column: 2;
  }

  .application-row__actions {
    display: flex;
    grid-column: 1 / -1;
    gap: var(--space-2);
  }

  .application-row__status {
    justify-self: end;
  }

  .application-row__count {
    display: none;
  }

  .application-row__quit {
    width: 28px;
    padding: 0;
    justify-content: center;
    font-size: 0;
  }

  .force-quit-confirm {
    padding: 0 var(--space-4) var(--space-4);
  }
}

@keyframes application-enter {
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
