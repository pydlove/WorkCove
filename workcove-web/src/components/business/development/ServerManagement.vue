<script setup lang="ts">
import {
  ArrowRightLeft,
  Copy,
  FolderOpen,
  ListChecks,
  Pencil,
  Plus,
  Search,
  Server,
  SquareTerminal,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import ServerDialog from '@/components/business/development/ServerDialog.vue'
import ServerFileManager from '@/components/business/development/ServerFileManager.vue'
import ServerTerminal from '@/components/business/development/ServerTerminal.vue'
import WorkspaceBatchBar from '@/components/business/workspaces/WorkspaceBatchBar.vue'
import WorkspaceTransferDialog, {
  type WorkspaceTransferMode,
} from '@/components/business/workspaces/WorkspaceTransferDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import { useManagedServerStore } from '@/stores/useManagedServerStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  ManagedServer,
  ManagedServerInput,
} from '@/types/managedServer'

const emit = defineEmits<{
  notify: [message: string]
}>()

const servers = useManagedServerStore()
const workspaces = useWorkspaceStore()
const searchQuery = ref('')
const dialogOpen = ref(false)
const editingServer = ref<ManagedServer | null>(null)
const duplicateDialog = ref(false)
const transferServer = ref<ManagedServer | null>(null)
const selectionMode = ref(false)
const selectedServerIds = ref<number[]>([])
const batchTransferMode = ref<WorkspaceTransferMode | null>(null)
const terminalServer = ref<ManagedServer | null>(null)
const fileServer = ref<ManagedServer | null>(null)
const loadError = ref('')

const filteredServers = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return servers.servers
  return servers.servers.filter((server) =>
    `${server.name} ${server.host} ${server.port} ${server.username}`
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})
const {
  currentPage,
  pageItems: pagedServers,
  setPage,
} = usePagination(filteredServers, () => searchQuery.value)

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function loadServers(force = false) {
  loadError.value = ''
  try {
    await servers.load(force)
  } catch (error) {
    loadError.value = errorMessage(error)
  }
}

function addServer() {
  editingServer.value = null
  duplicateDialog.value = false
  dialogOpen.value = true
}

function editServer(server: ManagedServer) {
  editingServer.value = server
  duplicateDialog.value = false
  dialogOpen.value = true
}

function duplicateServer(server: ManagedServer) {
  editingServer.value = server
  duplicateDialog.value = true
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  editingServer.value = null
  duplicateDialog.value = false
}

async function saveServer(input: ManagedServerInput) {
  const creatingDuplicate = duplicateDialog.value
  try {
    await servers.save(input)
    emit(
      'notify',
      creatingDuplicate
        ? '服务器副本已创建'
        : input.id
          ? '服务器已更新'
          : '服务器已添加',
    )
    closeDialog()
  } catch (error) {
    emit('notify', errorMessage(error))
  }
}

async function removeServer(id: number) {
  try {
    await servers.remove(id)
    emit('notify', '服务器已删除')
    closeDialog()
  } catch (error) {
    emit('notify', errorMessage(error))
  }
}

async function handleServerTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const server = transferServer.value
  if (!server) return
  const targetWorkspace = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!targetWorkspace) {
    emit('notify', '目标工作空间不存在')
    return
  }

  try {
    if (mode === 'copy') {
      await servers.copyToWorkspace(server.id, targetWorkspaceId)
    } else {
      await servers.moveToWorkspace(server.id, targetWorkspaceId)
    }
    emit(
      'notify',
      mode === 'copy'
        ? `已将「${server.name}」复制到「${targetWorkspace.name}」`
        : `已将「${server.name}」移动到「${targetWorkspace.name}」`,
    )
    transferServer.value = null
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
  } catch (error) {
    emit('notify', errorMessage(error))
  }
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) selectedServerIds.value = []
}

function setServerSelected(id: number, selected: boolean) {
  selectedServerIds.value = selected
    ? [...new Set([...selectedServerIds.value, id])]
    : selectedServerIds.value.filter((item) => item !== id)
}

function toggleSelectAllServers() {
  selectedServerIds.value =
    selectedServerIds.value.length === filteredServers.value.length
      ? []
      : filteredServers.value.map((server) => server.id)
}

function startBatchTransfer(mode: WorkspaceTransferMode) {
  if (selectedServerIds.value.length === 0) return
  batchTransferMode.value = mode
}

async function handleServerBatchTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const ids = [...selectedServerIds.value]
  const targetWorkspace = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!targetWorkspace) {
    emit('notify', '目标工作空间不存在')
    return
  }

  try {
    let changed = 0
    for (const id of ids) {
      if (mode === 'copy') {
        await servers.copyToWorkspace(id, targetWorkspaceId)
      } else {
        await servers.moveToWorkspace(id, targetWorkspaceId)
      }
      changed += 1
    }
    emit(
      'notify',
      `已将 ${changed} 台服务器${mode === 'copy' ? '复制' : '移动'}到「${targetWorkspace.name}」`,
    )
    batchTransferMode.value = null
    selectionMode.value = false
    selectedServerIds.value = []
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
  } catch (error) {
    emit('notify', errorMessage(error))
  }
}

function connectTerminal(server: ManagedServer) {
  fileServer.value = null
  terminalServer.value = server
}

function openFiles(server: ManagedServer) {
  terminalServer.value = null
  fileServer.value = server
}

onMounted(() => {
  void loadServers(true)
})

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    selectionMode.value = false
    selectedServerIds.value = []
    void loadServers(true)
  },
)
</script>

<template>
  <section
    class="server-management"
    :class="{
      'server-management--workspace': terminalServer || fileServer,
    }"
  >
    <ServerTerminal
      v-if="terminalServer"
      :server="terminalServer"
      @close="terminalServer = null"
      @notify="emit('notify', $event)"
    />
    <ServerFileManager
      v-else-if="fileServer"
      :server="fileServer"
      @close="fileServer = null"
      @notify="emit('notify', $event)"
    />

    <template v-else>
      <header class="server-toolbar">
      <span class="server-toolbar__count">
        {{ filteredServers.length }} 台服务器
      </span>
      <label class="server-search">
        <Search :size="15" aria-hidden="true" />
        <input
          v-model="searchQuery"
          type="search"
          aria-label="搜索服务器"
          placeholder="搜索名称、IP 或用户名"
        />
      </label>
      <UiButton @click="toggleSelectionMode">
        <ListChecks :size="14" />
        {{ selectionMode ? '退出批量' : '批量' }}
      </UiButton>
      <UiButton variant="primary" @click="addServer">
        <Plus :size="15" />
        添加服务器
      </UiButton>
    </header>

    <WorkspaceBatchBar
      v-if="selectionMode"
      :count="selectedServerIds.length"
      :total="filteredServers.length"
      @toggle-select-all="toggleSelectAllServers"
      @copy="startBatchTransfer('copy')"
      @move="startBatchTransfer('move')"
      @cancel="toggleSelectionMode"
    />

    <div v-if="loadError" class="server-error" role="alert">
      <span>{{ loadError }}</span>
      <UiButton size="sm" @click="loadServers">重试</UiButton>
    </div>

    <div v-if="filteredServers.length > 0" class="server-list">
      <article
        v-for="serverItem in pagedServers"
        :key="serverItem.id"
        class="server-row"
        :class="{ 'server-row--selecting': selectionMode }"
      >
        <UiCheckbox
          v-if="selectionMode"
          :model-value="selectedServerIds.includes(serverItem.id)"
          :label="`选择服务器 ${serverItem.name}`"
          @update:model-value="
            setServerSelected(serverItem.id, $event)
          "
        />
        <div
          class="server-row__main"
        >
          <span class="server-row__icon">
            <Server :size="18" />
          </span>
          <span class="server-row__copy">
            <strong>{{ serverItem.name }}</strong>
            <small>
              {{ serverItem.username }}@{{ serverItem.host }}:{{ serverItem.port }}
            </small>
          </span>
        </div>

        <div class="server-row__actions">
          <button
            class="server-row__connect"
            type="button"
            :aria-label="`SSH 连接 ${serverItem.name}`"
            title="SSH 连接"
            @click="connectTerminal(serverItem)"
          >
            <SquareTerminal :size="14" />
            连接
          </button>
          <button
            class="server-row__files"
            type="button"
            @click="openFiles(serverItem)"
          >
            <FolderOpen :size="14" />
            文件
          </button>
          <button
            class="server-row__duplicate"
            type="button"
            :aria-label="`复制服务器 ${serverItem.name}`"
            title="复制服务器，修改后保存为新记录"
            @click="duplicateServer(serverItem)"
          >
            <Copy :size="14" />
          </button>
          <button
            class="server-row__edit"
            type="button"
            :aria-label="`编辑 ${serverItem.name}`"
            title="编辑服务器"
            @click="editServer(serverItem)"
          >
            <Pencil :size="14" />
          </button>
          <button
            class="server-row__workspace"
            type="button"
            :aria-label="`处理 ${serverItem.name} 的工作空间`"
            title="复制或移动到其他工作空间"
            @click="transferServer = serverItem"
          >
            <ArrowRightLeft :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div
      v-else-if="!servers.loading"
      class="server-empty"
    >
      <span>
        <Server :size="28" />
      </span>
      <strong>
        {{
          servers.servers.length === 0
            ? '还没有添加服务器'
            : '没有符合条件的服务器'
        }}
      </strong>
      <p>
        {{
          servers.servers.length === 0
            ? '录入常用研发服务器，在 8小时工作台内连接终端和管理文件。'
            : '换一个关键词再试一次。'
        }}
      </p>
      <UiButton
        v-if="servers.servers.length === 0"
        variant="primary"
        @click="addServer"
      >
        <Plus :size="15" />
        添加第一台服务器
      </UiButton>
    </div>

    <div v-else class="server-loading">正在读取服务器...</div>

    <UiPagination
      :page="currentPage"
      :total="filteredServers.length"
      label="服务器分页"
      @update:page="setPage"
    />

    <ServerDialog
      v-if="dialogOpen"
      :server="editingServer"
      :duplicate="duplicateDialog"
      @close="closeDialog"
      @save="saveServer"
      @remove="removeServer"
    />

    <WorkspaceTransferDialog
      v-if="transferServer"
      :item-label="`服务器「${transferServer.name}」`"
      :source-workspace-id="transferServer.workspaceId"
      @close="transferServer = null"
      @transfer="handleServerTransfer"
    />

    <WorkspaceTransferDialog
      v-if="batchTransferMode"
      :item-label="`已选择 ${selectedServerIds.length} 台服务器`"
      :source-workspace-id="workspaces.activeWorkspaceId"
      :initial-mode="batchTransferMode"
      @close="batchTransferMode = null"
      @transfer="handleServerBatchTransfer"
    />
    </template>
  </section>
</template>

<style scoped>
.server-management {
  overflow: visible;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: server-enter var(--duration-window) var(--ease-apple) both;
}

.server-management--workspace {
  height: clamp(460px, calc(100dvh - 250px), 720px);
  min-height: 0;
  overflow: hidden;
  background: transparent;
  box-shadow: none;
}

.server-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.server-toolbar__count {
  flex: 1;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.server-search {
  display: flex;
  width: min(100%, 260px);
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

.server-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.server-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.server-search input:focus-visible {
  outline: none;
}

.server-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
  border-bottom: 1px solid var(--line-soft);
}

.server-list {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  padding: 0;
}

.server-row {
  display: grid;
  min-height: 62px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.server-row--selecting {
  grid-template-columns: 18px minmax(0, 1fr) auto;
}

.server-row:last-child {
  border-bottom: 0;
}

.server-row:hover {
  background: var(--hover-bg);
}

.server-row__main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.server-row__icon,
.server-empty > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.server-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.server-row__copy strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-row__copy small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-row__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.server-row__connect,
.server-row__files {
  display: inline-flex;
  height: 26px;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  color: var(--icon);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.server-row__connect:hover,
.server-row__files:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.server-row__duplicate,
.server-row__workspace,
.server-row__edit {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--icon);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.server-row__duplicate:hover,
.server-row__workspace:hover,
.server-row__edit:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.server-empty {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.server-empty > span {
  width: 48px;
  height: 48px;
}

.server-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.server-empty p {
  max-width: 360px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.server-loading {
  display: grid;
  min-height: 360px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  place-items: center;
}

@media (max-width: 680px) {
  .server-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .server-toolbar__count {
    display: flex;
    flex-basis: 100%;
    align-items: center;
  }

  .server-search {
    width: auto;
    flex: 1;
  }

  .server-row {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-1);
    padding: var(--space-2) var(--space-3);
  }

  .server-row--selecting {
    grid-template-columns: 18px minmax(0, 1fr);
  }

  .server-row__actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@keyframes server-enter {
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
