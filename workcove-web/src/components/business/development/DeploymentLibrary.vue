<script setup lang="ts">
import {
  ArrowRightLeft,
  Clock3,
  FileCode2,
  ListChecks,
  Pencil,
  Play,
  Plus,
  Rocket,
  ScrollText,
  Server,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import DeploymentDialog from '@/components/business/development/DeploymentDialog.vue'
import DeploymentRunsDialog from '@/components/business/development/DeploymentRunsDialog.vue'
import WorkspaceBatchBar from '@/components/business/workspaces/WorkspaceBatchBar.vue'
import WorkspaceTransferDialog, {
  type WorkspaceTransferMode,
} from '@/components/business/workspaces/WorkspaceTransferDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import { useDeploymentStore } from '@/stores/useDeploymentStore'
import { useManagedServerStore } from '@/stores/useManagedServerStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  DeploymentConfig,
  DeploymentInput,
  DeploymentRun,
} from '@/types/deployment'

const emit = defineEmits<{
  notify: [message: string]
}>()

const session = useAuthStore()
const deployments = useDeploymentStore()
const servers = useManagedServerStore()
const workspaces = useWorkspaceStore()
const searchQuery = ref('')
const dialogOpen = ref(false)
const editingDeployment = ref<DeploymentConfig | null>(null)
const transferDeployment = ref<DeploymentConfig | null>(null)
const selectionMode = ref(false)
const selectedDeploymentIds = ref<string[]>([])
const batchTransferMode = ref<WorkspaceTransferMode | null>(null)
const deleteTargets = ref<DeploymentConfig[]>([])
const runningId = ref('')
const runTarget = ref<DeploymentConfig | null>(null)
const runsTarget = ref<DeploymentConfig | null>(null)

const workspaceDeployments = computed(() =>
  deployments.deployments.filter(
    (deployment) =>
      deployment.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const filteredDeployments = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return [...workspaceDeployments.value]
    .sort((left, right) =>
      right.updatedAt.localeCompare(left.updatedAt),
    )
    .filter((deployment) => {
      if (!query) return true
      return `${deployment.name} ${deployment.serverName} ${deployment.remoteDirectory} ${deployment.scriptFilename}`
        .toLocaleLowerCase('zh-CN')
        .includes(query)
    })
})

const {
  currentPage,
  pageItems: pagedDeployments,
  setPage,
} = usePagination(filteredDeployments, () => searchQuery.value)

const workspaceRuns = computed(() =>
  deployments.runs.filter(
    (run) => run.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const runsTargetItems = computed(() =>
  runsTarget.value
    ? workspaceRuns.value.filter(
        (run) => run.deploymentId === runsTarget.value?.id,
      )
    : [],
)

watch(
  () => session.email,
  (email) => {
    deployments.load(email)
    workspaces.load(email)
  },
  { immediate: true },
)

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    dialogOpen.value = false
    editingDeployment.value = null
    transferDeployment.value = null
    selectionMode.value = false
    selectedDeploymentIds.value = []
    batchTransferMode.value = null
    deleteTargets.value = []
    runningId.value = ''
    runsTarget.value = null
    runTarget.value = null
    void loadServers(true)
  },
)

onMounted(() => {
  void loadServers(true)
})

async function loadServers(force = false) {
  try {
    await servers.load(force)
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '服务器列表加载失败',
    )
  }
}

function latestRun(deployment: DeploymentConfig): DeploymentRun | null {
  return (
    workspaceRuns.value
      .filter((run) => run.deploymentId === deployment.id)
      .sort((left, right) =>
        right.startedAt.localeCompare(left.startedAt),
      )[0] ?? null
  )
}

function serverOf(deployment: DeploymentConfig) {
  return deployment.serverId === null
    ? null
    : servers.servers.find(
        (server) => server.id === deployment.serverId,
      ) ?? null
}

function statusLabel(run: DeploymentRun | null) {
  if (!run) return '未部署'
  if (run.status === 'SUCCESS') return '成功'
  if (run.status === 'TIMEOUT') return '超时'
  return '失败'
}

function formatRunTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function addDeployment() {
  editingDeployment.value = null
  dialogOpen.value = true
}

function editDeployment(deployment: DeploymentConfig) {
  editingDeployment.value = deployment
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  editingDeployment.value = null
}

function saveDeployment(input: DeploymentInput) {
  deployments.saveDeployment({
    ...input,
    workspaceId: workspaces.activeWorkspaceId,
  })
  emit('notify', input.id ? '部署配置已更新' : '部署配置已创建')
  closeDialog()
}

function removeDeployment(id: string) {
  const removed = deployments.removeDeployments([id])
  if (removed === 0) {
    emit('notify', '部署配置不存在，请刷新后再试')
    return
  }
  emit('notify', '部署配置和执行记录已删除')
  closeDialog()
}

async function runDeployment(deployment: DeploymentConfig) {
  if (runningId.value) return
  if (!serverOf(deployment)) {
    emit('notify', '请先编辑部署并选择当前工作空间中的服务器')
    editDeployment(deployment)
    return
  }

  runningId.value = deployment.id
  try {
    const run = await deployments.executeDeployment(deployment.id)
    runsTarget.value = deployment
    emit(
      'notify',
      run.status === 'SUCCESS'
        ? `部署「${deployment.name}」执行成功`
        : `部署「${deployment.name}」执行${run.status === 'TIMEOUT' ? '超时' : '失败'}：${run.summary}`,
    )
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '部署执行失败',
    )
  } finally {
    runningId.value = ''
  }
}

function requestRunDeployment(deployment: DeploymentConfig) {
  if (runningId.value) return
  if (!serverOf(deployment)) {
    emit('notify', '请先编辑部署并选择当前工作空间中的服务器')
    editDeployment(deployment)
    return
  }
  runTarget.value = deployment
}

async function confirmRunDeployment() {
  const deployment = runTarget.value
  if (!deployment) return
  await runDeployment(deployment)
  runTarget.value = null
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) selectedDeploymentIds.value = []
}

function setDeploymentSelected(id: string, selected: boolean) {
  selectedDeploymentIds.value = selected
    ? [...new Set([...selectedDeploymentIds.value, id])]
    : selectedDeploymentIds.value.filter((item) => item !== id)
}

function toggleSelectAllDeployments() {
  selectedDeploymentIds.value =
    selectedDeploymentIds.value.length ===
    filteredDeployments.value.length
      ? []
      : filteredDeployments.value.map((deployment) => deployment.id)
}

function startBatchTransfer(mode: WorkspaceTransferMode) {
  if (selectedDeploymentIds.value.length === 0) return
  batchTransferMode.value = mode
}

function requestDeleteDeployment(deployment: DeploymentConfig) {
  deleteTargets.value = [deployment]
}

function requestDeleteSelectedDeployments() {
  const selectedIds = new Set(selectedDeploymentIds.value)
  const targets = workspaceDeployments.value.filter((deployment) =>
    selectedIds.has(deployment.id),
  )
  if (targets.length === 0) {
    emit('notify', '请先选择要删除的部署配置')
    return
  }
  deleteTargets.value = targets
}

function cancelDeleteDeployments() {
  deleteTargets.value = []
}

function confirmDeleteDeployments() {
  const removed = deployments.removeDeployments(
    deleteTargets.value.map((deployment) => deployment.id),
  )
  if (removed === 0) {
    emit('notify', '没有可删除的部署配置')
    return
  }
  deleteTargets.value = []
  selectionMode.value = false
  selectedDeploymentIds.value = []
  emit(
    'notify',
    removed === 1 ? '部署配置已删除' : `已删除 ${removed} 个部署配置`,
  )
}

function handleDeploymentTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const deployment = transferDeployment.value
  if (!deployment) return
  const target = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!target) {
    emit('notify', '目标工作空间不存在')
    return
  }

  const changed =
    mode === 'copy'
      ? deployments.copyDeployments(
          [deployment.id],
          targetWorkspaceId,
        )
      : deployments.moveDeployments(
          [deployment.id],
          targetWorkspaceId,
        )
  if (changed === 0) {
    emit('notify', '没有可处理的部署配置')
    return
  }
  transferDeployment.value = null
  if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
  emit(
    'notify',
    `部署配置已${mode === 'copy' ? '复制' : '移动'}到「${target.name}」，请重新选择服务器`,
  )
}

function handleDeploymentBatchTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const target = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!target) {
    emit('notify', '目标工作空间不存在')
    return
  }
  const ids = [...selectedDeploymentIds.value]
  const changed =
    mode === 'copy'
      ? deployments.copyDeployments(ids, targetWorkspaceId)
      : deployments.moveDeployments(ids, targetWorkspaceId)
  if (changed === 0) {
    emit('notify', '没有可处理的部署配置')
    return
  }

  batchTransferMode.value = null
  selectionMode.value = false
  selectedDeploymentIds.value = []
  if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
  emit(
    'notify',
    `已将 ${changed} 个部署配置${mode === 'copy' ? '复制' : '移动'}到「${target.name}」，请重新选择服务器`,
  )
}
</script>

<template>
  <section class="deployment-library">
    <header class="deployment-toolbar">
      <span class="deployment-toolbar__count">
        {{ filteredDeployments.length }} 个部署配置
      </span>
      <UiSearchInput
        v-model="searchQuery"
        label="搜索部署"
        placeholder="搜索名称、服务器、目录或脚本"
      />
      <UiButton @click="toggleSelectionMode">
        <ListChecks :size="14" />
        {{ selectionMode ? '退出批量' : '批量' }}
      </UiButton>
      <UiButton variant="primary" @click="addDeployment">
        <Plus :size="15" />
        新建部署
      </UiButton>
    </header>

    <WorkspaceBatchBar
      v-if="selectionMode"
      :count="selectedDeploymentIds.length"
      :total="filteredDeployments.length"
      show-delete
      @toggle-select-all="toggleSelectAllDeployments"
      @copy="startBatchTransfer('copy')"
      @move="startBatchTransfer('move')"
      @delete="requestDeleteSelectedDeployments"
      @cancel="toggleSelectionMode"
    />

    <div
      v-if="filteredDeployments.length > 0"
      class="deployment-list"
    >
      <article
        v-for="deployment in pagedDeployments"
        :key="deployment.id"
        class="deployment-row"
        :class="{ 'deployment-row--selecting': selectionMode }"
      >
        <UiCheckbox
          v-if="selectionMode"
          :model-value="
            selectedDeploymentIds.includes(deployment.id)
          "
          :label="`选择部署 ${deployment.name}`"
          @update:model-value="
            setDeploymentSelected(deployment.id, $event)
          "
        />

        <div class="deployment-row__main">
          <span class="deployment-row__icon">
            <Rocket :size="18" />
          </span>
          <span class="deployment-row__copy">
            <strong>{{ deployment.name }}</strong>
            <small :title="deployment.scriptFilename">
              <FileCode2 :size="11" />
              {{ deployment.scriptFilename }}
            </small>
            <span class="deployment-row__meta">
              <span>
                <Server :size="11" />
                {{
                  serverOf(deployment)
                    ? `${deployment.serverName} · ${serverOf(deployment)?.username}@${serverOf(deployment)?.host}`
                    : '需要重新选择服务器'
                }}
              </span>
              <span :title="deployment.remoteDirectory">
                {{ deployment.remoteDirectory }}
              </span>
            </span>
          </span>
        </div>

        <div class="deployment-row__latest">
          <span
            v-if="latestRun(deployment)"
            class="deployment-status"
            :class="`deployment-status--${latestRun(deployment)?.status.toLowerCase()}`"
          >
            {{ statusLabel(latestRun(deployment)) }}
          </span>
          <span v-else class="deployment-status">未部署</span>
          <small v-if="latestRun(deployment)">
            {{ formatRunTime(latestRun(deployment)!.startedAt) }}
          </small>
        </div>

        <button
          class="deployment-row__run"
          type="button"
          :disabled="runningId !== '' || !serverOf(deployment)"
          :title="
            serverOf(deployment)
              ? `运行 ${deployment.name}`
              : '请先选择服务器'
          "
          @click="requestRunDeployment(deployment)"
        >
          <UiFidgetSpinner
            v-if="runningId === deployment.id"
            :size="13"
          />
          <Play v-else :size="13" />
          {{ runningId === deployment.id ? '执行中' : '运行' }}
        </button>

        <div class="deployment-row__actions">
          <button
            type="button"
            :aria-label="`查看 ${deployment.name} 的执行记录`"
            title="执行记录"
            @click="runsTarget = deployment"
          >
            <ScrollText :size="14" />
          </button>
          <button
            type="button"
            :aria-label="`处理 ${deployment.name} 的工作空间`"
            title="复制或移动到其他工作空间"
            @click="transferDeployment = deployment"
          >
            <ArrowRightLeft :size="14" />
          </button>
          <button
            type="button"
            :aria-label="`编辑 ${deployment.name}`"
            title="编辑部署"
            @click="editDeployment(deployment)"
          >
            <Pencil :size="14" />
          </button>
          <button
            class="deployment-row__delete"
            type="button"
            :aria-label="`删除 ${deployment.name}`"
            title="删除部署配置"
            @click="requestDeleteDeployment(deployment)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="deployment-empty">
      <span><Rocket :size="28" /></span>
      <strong>
        {{
          workspaceDeployments.length === 0
            ? '还没有部署配置'
            : '没有符合条件的部署'
        }}
      </strong>
      <p>
        {{
          workspaceDeployments.length === 0
            ? '把部署脚本和服务器连接保存到一起，随时执行并查看输出。'
            : '换一个关键词再试一次。'
        }}
      </p>
      <UiButton
        v-if="workspaceDeployments.length === 0"
        variant="primary"
        @click="addDeployment"
      >
        <Plus :size="15" />
        新建第一个部署
      </UiButton>
    </div>

    <UiPagination
      :page="currentPage"
      :total="filteredDeployments.length"
      label="部署分页"
      @update:page="setPage"
    />

    <DeploymentDialog
      v-if="dialogOpen"
      :deployment="editingDeployment"
      :servers="servers.servers"
      @close="closeDialog"
      @save="saveDeployment"
      @remove="removeDeployment"
    />

    <DeploymentRunsDialog
      v-if="runsTarget"
      :deployment="runsTarget"
      :runs="runsTargetItems"
      @close="runsTarget = null"
    />

    <UiConfirmDialog
      v-if="runTarget"
      :title="`运行部署「${runTarget.name}」？`"
      :description="`将在 ${serverOf(runTarget)?.username || ''}@${serverOf(runTarget)?.host || ''}:${serverOf(runTarget)?.port || ''} 的 ${runTarget.remoteDirectory} 目录执行脚本。`"
      warning="脚本会立即在远端服务器执行，可能修改服务、配置或数据，请确认后继续。"
      confirm-label="运行部署"
      busy-label="正在执行…"
      :busy="Boolean(runningId)"
      @close="runTarget = null"
      @confirm="confirmRunDeployment"
    >
      <span>
        执行文件：{{ runTarget.scriptFilename }}
      </span>
    </UiConfirmDialog>

    <WorkspaceTransferDialog
      v-if="transferDeployment"
      :item-label="`部署「${transferDeployment.name}」`"
      :source-workspace-id="transferDeployment.workspaceId"
      @close="transferDeployment = null"
      @transfer="handleDeploymentTransfer"
    />

    <WorkspaceTransferDialog
      v-if="batchTransferMode"
      :item-label="`已选择 ${selectedDeploymentIds.length} 个部署配置`"
      :source-workspace-id="workspaces.activeWorkspaceId"
      :initial-mode="batchTransferMode"
      @close="batchTransferMode = null"
      @transfer="handleDeploymentBatchTransfer"
    />

    <UiConfirmDialog
      v-if="deleteTargets.length > 0"
      :title="
        deleteTargets.length === 1
          ? '删除部署配置？'
          : `删除 ${deleteTargets.length} 个部署配置？`
      "
      description="只会删除 8小时工作台中的部署配置和执行记录，不会删除服务器上的文件。"
      warning="此操作无法撤销，请确认后继续。"
      :confirm-label="
        deleteTargets.length === 1
          ? '删除部署'
          : `删除 ${deleteTargets.length} 个部署`
      "
      @close="cancelDeleteDeployments"
      @confirm="confirmDeleteDeployments"
    >
      <div class="deployment-delete-target">
        <Clock3 :size="15" />
        <span v-if="deleteTargets.length === 1">
          删除「{{ deleteTargets[0].name }}」及其执行记录
        </span>
        <span v-else>
          删除已选择的 {{ deleteTargets.length }} 个部署及其执行记录
        </span>
      </div>
    </UiConfirmDialog>
  </section>
</template>

<style scoped>
.deployment-library {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.deployment-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.deployment-toolbar__count {
  flex: 1;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.deployment-list {
  display: flex;
  min-height: 360px;
  padding: var(--space-2) 0;
  flex-direction: column;
}

.deployment-row {
  display: grid;
  min-height: 82px;
  grid-template-columns: minmax(0, 1fr) 82px 84px auto;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--line-soft);
  transition: background var(--duration-fast) var(--ease-apple);
}

.deployment-row--selecting {
  grid-template-columns:
    18px minmax(0, 1fr) 82px 84px
    auto;
}

.deployment-row:last-child {
  border-bottom: 0;
}

.deployment-row:hover {
  background: var(--hover-bg);
}

.deployment-row__main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
}

.deployment-row__icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.deployment-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.deployment-row__copy > strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deployment-row__copy > small {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  overflow: hidden;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deployment-row__meta {
  display: flex;
  min-width: 0;
  gap: var(--space-3);
  margin-top: 4px;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.deployment-row__meta > span {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deployment-row__meta > span:first-child {
  max-width: 260px;
}

.deployment-row__meta > span:last-child {
  max-width: 260px;
  font-family: var(--font-mono);
}

.deployment-row__latest {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 3px;
}

.deployment-row__latest small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.deployment-status {
  padding: 3px 7px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
}

.deployment-status--success {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
}

.deployment-status--failed {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 9%, transparent);
}

.deployment-status--timeout {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.deployment-row__run {
  display: inline-flex;
  height: 28px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 var(--space-2);
  color: var(--accent);
  font-size: var(--font-caption);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
}

.deployment-row__run:hover:not(:disabled) {
  filter: brightness(0.97);
}

.deployment-row__run:disabled {
  opacity: 0.42;
  cursor: default;
}

.deployment-row__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1px;
}

.deployment-row__actions button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.deployment-row__actions button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.deployment-row__actions .deployment-row__delete:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.deployment-empty {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.deployment-empty > span {
  display: grid;
  width: 52px;
  height: 52px;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.deployment-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.deployment-empty p {
  max-width: 380px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.deployment-delete-target {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 760px) {
  .deployment-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .deployment-toolbar__count {
    display: flex;
    flex-basis: 100%;
    align-items: center;
  }

  .deployment-row {
    grid-template-columns: minmax(0, 1fr) 84px auto;
    gap: var(--space-2);
    padding: 0 var(--space-3);
  }

  .deployment-row--selecting {
    grid-template-columns: 18px minmax(0, 1fr) 84px auto;
  }

  .deployment-row__latest {
    display: none;
  }

  .deployment-row__meta > span:last-child {
    display: none;
  }
}

@media (max-width: 560px) {
  .deployment-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .deployment-row--selecting {
    grid-template-columns: 18px minmax(0, 1fr) auto;
  }

  .deployment-row__run {
    display: none;
  }

  .deployment-row__meta {
    display: none;
  }
}
</style>
