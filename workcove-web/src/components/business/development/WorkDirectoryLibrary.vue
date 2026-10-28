<script setup lang="ts">
import {
  ArrowRightLeft,
  Folder,
  FolderOpen,
  ListChecks,
  Pencil,
  Plus,
  Search,
  X,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import ideaIcon from '@/assets/development/intellij-idea.png'
import {
  openLocalDirectory,
  openLocalProjectWithIdea,
} from '@/api/development'
import WorkCategoryDialog from '@/components/business/development/WorkCategoryDialog.vue'
import WorkDirectoryDialog from '@/components/business/development/WorkDirectoryDialog.vue'
import WorkspaceBatchBar from '@/components/business/workspaces/WorkspaceBatchBar.vue'
import WorkspaceTransferDialog, {
  type WorkspaceTransferMode,
} from '@/components/business/workspaces/WorkspaceTransferDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCategoryFilterBar from '@/components/ui/UiCategoryFilterBar.vue'
import UiCategorySelect from '@/components/ui/UiCategorySelect.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import { useWorkDirectoryStore } from '@/stores/useWorkDirectoryStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import {
  DEFAULT_WORK_DIRECTORY_CATEGORY,
  type WorkDirectory,
  type WorkDirectoryInput,
} from '@/types/workDirectory'

const emit = defineEmits<{
  notify: [message: string]
}>()

const session = useAuthStore()
const directories = useWorkDirectoryStore()
const workspaces = useWorkspaceStore()
const searchQuery = ref('')
const selectedCategory = ref('全部')
const categoryManagerOpen = ref(false)
const dialogOpen = ref(false)
const editingDirectory = ref<WorkDirectory | null>(null)
const closingDirectory = ref<WorkDirectory | null>(null)
const transferDirectory = ref<WorkDirectory | null>(null)
const selectionMode = ref(false)
const selectedDirectoryIds = ref<string[]>([])
const batchTransferMode = ref<WorkspaceTransferMode | null>(null)
const selectedDirectoryId = ref('')
const ideaBusyId = ref('')

const categories = computed(
  () =>
    directories.categoriesByWorkspace[workspaces.activeWorkspaceId] ?? [
      DEFAULT_WORK_DIRECTORY_CATEGORY,
    ],
)

const workspaceDirectories = computed(() =>
  directories.directories.filter(
    (directory) =>
      directory.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const directory of workspaceDirectories.value) {
    counts.set(
      directory.category,
      (counts.get(directory.category) ?? 0) + 1,
    )
  }
  return counts
})

const filteredDirectories = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return workspaceDirectories.value.filter((directory) => {
    if (
      selectedCategory.value !== '全部' &&
      directory.category !== selectedCategory.value
    ) {
      return false
    }
    if (!query) return true
    return `${directory.name} ${directory.path} ${directory.category} ${directory.description}`
      .toLocaleLowerCase('zh-CN')
      .includes(query)
  })
})
const {
  currentPage,
  pageItems: pagedDirectories,
  setPage,
} = usePagination(
  filteredDirectories,
  () => `${searchQuery.value}\n${selectedCategory.value}`,
)

const defaultDialogCategory = computed(() =>
  selectedCategory.value === '全部'
    ? categories.value[0] ?? DEFAULT_WORK_DIRECTORY_CATEGORY
    : selectedCategory.value,
)

watch(
  () => session.email,
  (email) => {
    directories.load(email)
    workspaces.load(email)
  },
  { immediate: true },
)

watch(categories, (items) => {
  if (
    selectedCategory.value !== '全部' &&
    !items.includes(selectedCategory.value)
  ) {
    selectedCategory.value = '全部'
  }
})

watch(filteredDirectories, (items) => {
  if (
    selectedDirectoryId.value &&
    !items.some((directory) => directory.id === selectedDirectoryId.value)
  ) {
    selectedDirectoryId.value = ''
  }
})

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    selectionMode.value = false
    selectedDirectoryIds.value = []
  },
)

function addDirectory() {
  editingDirectory.value = null
  dialogOpen.value = true
}

function editDirectory(directory: WorkDirectory) {
  selectedDirectoryId.value = directory.id
  editingDirectory.value = directory
  dialogOpen.value = true
}

function changeDirectoryCategory(
  directory: WorkDirectory,
  category: string,
) {
  if (!category || category === directory.category) return

  selectedDirectoryId.value = directory.id
  directories.saveDirectory({
    id: directory.id,
    workspaceId: directory.workspaceId,
    name: directory.name,
    path: directory.path,
    category,
    description: directory.description,
  })
  emit('notify', `${directory.name} 已移至「${category}」`)
}

function closeDialog() {
  dialogOpen.value = false
  editingDirectory.value = null
}

function saveDirectory(input: WorkDirectoryInput) {
  directories.saveDirectory({
    ...input,
    workspaceId: workspaces.activeWorkspaceId,
  })
  emit('notify', input.id ? '工作目录已更新' : '工作目录已添加')
  closeDialog()
}

function removeDirectory(id: string) {
  directories.removeDirectory(id)
  if (selectedDirectoryId.value === id) selectedDirectoryId.value = ''
  emit('notify', '工作目录已移除')
  closeDialog()
}

async function openDirectory(directory: WorkDirectory) {
  selectedDirectoryId.value = directory.id
  const error = await openLocalDirectory(directory.path)
  if (error) {
    emit('notify', `无法在访达中打开目录：${error}`)
    return
  }
  emit('notify', `已在访达中打开「${directory.name}」`)
}

async function openWithIdea(directory: WorkDirectory) {
  selectedDirectoryId.value = directory.id
  ideaBusyId.value = directory.id
  try {
    const error = await openLocalProjectWithIdea(directory.path)
    if (error) {
      emit('notify', `无法用 IDEA 打开：${error}`)
      return
    }
    emit('notify', `正在用 IntelliJ IDEA 打开 ${directory.name}`)
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法启动 IntelliJ IDEA',
    )
  } finally {
    ideaBusyId.value = ''
  }
}

function requestCloseDirectory(directory: WorkDirectory) {
  selectedDirectoryId.value = directory.id
  closingDirectory.value = directory
}

function handleDirectoryTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const directory = transferDirectory.value
  if (!directory) return
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
        ? directories.copyDirectory(directory.id, targetWorkspaceId)
        : directories.moveDirectory(directory.id, targetWorkspaceId)
    if (!changed) {
      emit('notify', '未找到可处理的工作目录，请刷新后再试')
      return
    }

    transferDirectory.value = null
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
    emit(
      'notify',
      mode === 'copy'
        ? `已将「${directory.name}」复制到「${targetWorkspace.name}」`
        : `已将「${directory.name}」移动到「${targetWorkspace.name}」`,
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
  if (!selectionMode.value) selectedDirectoryIds.value = []
}

function setDirectorySelected(id: string, selected: boolean) {
  selectedDirectoryIds.value = selected
    ? [...new Set([...selectedDirectoryIds.value, id])]
    : selectedDirectoryIds.value.filter((item) => item !== id)
}

function toggleSelectAllDirectories() {
  selectedDirectoryIds.value =
    selectedDirectoryIds.value.length === filteredDirectories.value.length
      ? []
      : filteredDirectories.value.map((directory) => directory.id)
}

function startBatchTransfer(mode: WorkspaceTransferMode) {
  if (selectedDirectoryIds.value.length === 0) return
  batchTransferMode.value = mode
}

function handleDirectoryBatchTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const targetWorkspace = workspaces.workspaces.find(
    (workspace) => workspace.id === targetWorkspaceId,
  )
  if (!targetWorkspace) {
    emit('notify', '目标工作空间不存在')
    return
  }

  try {
    let changed = 0
    for (const id of [...selectedDirectoryIds.value]) {
      if (
        mode === 'copy'
          ? directories.copyDirectory(id, targetWorkspaceId)
          : directories.moveDirectory(id, targetWorkspaceId)
      ) {
        changed += 1
      }
    }
    if (changed === 0) {
      emit('notify', '没有可处理的工作目录，请重新选择后再试')
      return
    }

    batchTransferMode.value = null
    selectionMode.value = false
    selectedDirectoryIds.value = []
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
    emit(
      'notify',
      `已将 ${changed} 个工作目录${mode === 'copy' ? '复制' : '移动'}到「${targetWorkspace.name}」`,
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

function cancelCloseDirectory() {
  closingDirectory.value = null
}

function confirmCloseDirectory() {
  const directory = closingDirectory.value
  if (!directory) return

  directories.removeDirectory(directory.id)
  if (selectedDirectoryId.value === directory.id) {
    selectedDirectoryId.value = ''
  }
  emit('notify', `已关闭 ${directory.name}，本地文件未删除`)
  closingDirectory.value = null
}

function formatUpdatedAt(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  }).format(new Date(value))
}
</script>

<template>
  <section class="directory-library">
    <header class="directory-toolbar">
      <span class="directory-toolbar__count">
        {{ filteredDirectories.length }} 个工作目录
      </span>
      <label class="directory-search">
        <Search :size="15" aria-hidden="true" />
        <input
          v-model="searchQuery"
          type="search"
          aria-label="搜索工作目录"
          placeholder="搜索名称、路径、分类或备注"
        />
      </label>
      <UiButton @click="toggleSelectionMode">
        <ListChecks :size="14" />
        {{ selectionMode ? '退出批量' : '批量' }}
      </UiButton>
      <UiButton variant="primary" @click="addDirectory">
        <Plus :size="15" />
        添加目录
      </UiButton>
    </header>

    <WorkspaceBatchBar
      v-if="selectionMode"
      :count="selectedDirectoryIds.length"
      :total="filteredDirectories.length"
      @toggle-select-all="toggleSelectAllDirectories"
      @copy="startBatchTransfer('copy')"
      @move="startBatchTransfer('move')"
      @cancel="toggleSelectionMode"
    />

    <UiCategoryFilterBar
      v-model="selectedCategory"
      :categories="categories"
      :counts="categoryCounts"
      :total="workspaceDirectories.length"
      group-label="工作目录分类"
      @manage="categoryManagerOpen = true"
    />

    <div v-if="filteredDirectories.length > 0" class="directory-list">
      <article
        v-for="directory in pagedDirectories"
        :key="directory.id"
        class="directory-row"
        :class="{
          'directory-row--selected':
            selectedDirectoryId === directory.id,
          'directory-row--selecting': selectionMode,
        }"
      >
        <UiCheckbox
          v-if="selectionMode"
          :model-value="selectedDirectoryIds.includes(directory.id)"
          :label="`选择工作目录 ${directory.name}`"
          @update:model-value="
            setDirectorySelected(directory.id, $event)
          "
        />
        <button
          class="directory-row__icon"
          type="button"
          tabindex="-1"
          :aria-label="`在访达中打开目录 ${directory.name}`"
          @focus="selectedDirectoryId = directory.id"
          @click="openDirectory(directory)"
        >
          <Folder :size="18" />
        </button>
        <div class="directory-row__copy">
          <div class="directory-row__title">
            <button
              class="directory-row__name"
              type="button"
          :aria-label="`在访达中打开目录 ${directory.name}`"
              :aria-current="
                selectedDirectoryId === directory.id ? 'true' : undefined
              "
              @focus="selectedDirectoryId = directory.id"
              @click="openDirectory(directory)"
            >
              <strong>{{ directory.name }}</strong>
            </button>
          </div>
          <button
            class="directory-row__details"
            type="button"
            tabindex="-1"
            :aria-label="`在访达中打开目录 ${directory.name}`"
            @click="openDirectory(directory)"
          >
            <small>{{ directory.path }}</small>
            <span
              v-if="directory.description"
              class="directory-row__description"
            >
              {{ directory.description }}
            </span>
          </button>
        </div>
        <UiCategorySelect
          class="directory-row__category"
          :value="directory.category"
          :options="categories"
          :label="`切换 ${directory.name} 的分类`"
          title="快速切换目录分类"
          @focus="selectedDirectoryId = directory.id"
          @change="changeDirectoryCategory(directory, $event)"
        />
        <time :datetime="directory.updatedAt">
          {{ formatUpdatedAt(directory.updatedAt) }}
        </time>
        <div class="directory-row__actions">
          <button
            class="directory-row__idea"
            type="button"
            :disabled="ideaBusyId === directory.id"
            :aria-label="`用 IDEA 打开 ${directory.name}`"
            title="用 IntelliJ IDEA 打开"
            @click="openWithIdea(directory)"
          >
            <img :src="ideaIcon" alt="" width="16" height="16" />
            {{ ideaBusyId === directory.id ? '打开中…' : 'IDEA' }}
          </button>
          <button
            class="directory-row__action"
            type="button"
            :aria-label="`打开目录 ${directory.name}`"
            title="在访达中打开"
            @click="openDirectory(directory)"
          >
            <FolderOpen :size="15" />
          </button>
          <button
            class="directory-row__action"
            type="button"
            :aria-label="`编辑 ${directory.name}`"
            title="编辑目录"
            @click="editDirectory(directory)"
          >
            <Pencil :size="14" />
          </button>
          <button
            class="directory-row__action"
            type="button"
            :aria-label="`处理 ${directory.name} 的工作空间`"
            title="复制或移动到其他工作空间"
            @click="transferDirectory = directory"
          >
            <ArrowRightLeft :size="14" />
          </button>
          <button
            class="directory-row__action directory-row__action--close"
            type="button"
            :aria-label="`关闭 ${directory.name}`"
            title="关闭工作目录，不删除本地文件"
            @click="requestCloseDirectory(directory)"
          >
            <X :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="directory-empty">
      <span>
        <Folder :size="28" />
      </span>
      <strong>
        {{
          workspaceDirectories.length === 0
            ? '还没有工作目录'
            : '没有符合条件的目录'
        }}
      </strong>
      <p>
        {{
          workspaceDirectories.length === 0
            ? '把常用项目目录集中到这里，减少每天反复查找路径。'
            : '换一个关键词再试一次。'
        }}
      </p>
      <UiButton
        v-if="workspaceDirectories.length === 0"
        variant="primary"
        @click="addDirectory"
      >
        <Plus :size="15" />
        添加第一个目录
      </UiButton>
    </div>

    <UiPagination
      :page="currentPage"
      :total="filteredDirectories.length"
      label="工作目录分页"
      @update:page="setPage"
    />

    <WorkDirectoryDialog
      v-if="dialogOpen"
      :directory="editingDirectory"
      :categories="categories"
      :default-category="defaultDialogCategory"
      @close="closeDialog"
      @save="saveDirectory"
      @remove="removeDirectory"
    />

    <WorkCategoryDialog
      v-if="categoryManagerOpen"
      @close="categoryManagerOpen = false"
      @notify="emit('notify', $event)"
    />

    <WorkspaceTransferDialog
      v-if="transferDirectory"
      :item-label="`工作目录「${transferDirectory.name}」`"
      :source-workspace-id="transferDirectory.workspaceId"
      @close="transferDirectory = null"
      @transfer="handleDirectoryTransfer"
    />

    <WorkspaceTransferDialog
      v-if="batchTransferMode"
      :item-label="`已选择 ${selectedDirectoryIds.length} 个工作目录`"
      :source-workspace-id="workspaces.activeWorkspaceId"
      :initial-mode="batchTransferMode"
      @close="batchTransferMode = null"
      @transfer="handleDirectoryBatchTransfer"
    />

    <UiConfirmDialog
      v-if="closingDirectory"
      title="关闭工作目录？"
      description="目录会从工作台列表中移除，本地文件不会被删除。"
      confirm-label="关闭目录"
      @close="cancelCloseDirectory"
      @confirm="confirmCloseDirectory"
    >
      <div class="directory-close__target">
        <Folder :size="17" aria-hidden="true" />
        <span>
          <strong>{{ closingDirectory.name }}</strong>
          <small>{{ closingDirectory.path }}</small>
        </span>
      </div>
    </UiConfirmDialog>
  </section>
</template>

<style scoped>
.directory-library {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: directory-enter var(--duration-window) var(--ease-apple) both;
}

.directory-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.directory-toolbar__count {
  flex: 1;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.directory-search {
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

.directory-search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.directory-search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.directory-search input:focus-visible {
  outline: none;
}

.directory-list {
  display: flex;
  min-height: 360px;
  flex-direction: column;
}

.directory-row {
  position: relative;
  display: grid;
  min-height: 72px;
  grid-template-columns:
    36px minmax(0, 1fr) calc(var(--space-8) * 3) 44px
    auto;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  border-bottom: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  transition:
    background var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.directory-row--selecting {
  grid-template-columns:
    18px 36px minmax(0, 1fr) calc(var(--space-8) * 3)
    44px auto;
}

.directory-row:last-child {
  border-bottom: 0;
}

.directory-row:hover {
  background: var(--hover-bg);
}

.directory-row--selected,
.directory-row--selected:hover {
  background: var(--selection-bg);
}

.directory-row--selected::before {
  position: absolute;
  top: var(--space-3);
  bottom: var(--space-3);
  left: 0;
  width: 2px;
  content: "";
  pointer-events: none;
  background: var(--system-blue);
  border-radius: var(--radius-badge);
}

.directory-row__icon,
.directory-empty > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.directory-row__icon {
  padding: 0;
  border: 0;
  cursor: pointer;
}

.directory-row__icon:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.directory-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.directory-row__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.directory-row__name {
  min-width: 0;
  flex: 0 1 auto;
  padding: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
}

.directory-row__name:focus-visible {
  border-radius: var(--radius-badge);
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.directory-row__copy strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-row__details {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  padding: 0;
  text-align: left;
  background: transparent;
}

.directory-row__copy small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-row__description {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-row time {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-align: right;
}

.directory-row__action {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.directory-row__action:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.directory-row__actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.directory-row__idea {
  display: inline-flex;
  height: 26px;
  align-items: center;
  gap: 5px;
  padding: 0 7px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
}

.directory-row__idea:hover:not(:disabled) {
  color: var(--system-blue);
}

.directory-row__idea:disabled {
  opacity: 0.5;
}

.directory-row__idea img {
  width: var(--space-4);
  height: var(--space-4);
  flex: 0 0 auto;
  object-fit: contain;
}

.directory-row__action--close:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.directory-close__target {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-subtle);
}

.directory-close__target > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-1);
}

.directory-close__target strong,
.directory-close__target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-close__target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-close__target small {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.directory-empty {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.directory-empty > span {
  width: 48px;
  height: 48px;
}

.directory-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.directory-empty p {
  max-width: 360px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 620px) {
  .directory-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .directory-toolbar__count {
    display: flex;
    flex-basis: 100%;
    align-items: center;
  }

  .directory-search {
    width: auto;
    flex: 1;
  }

  .directory-categories {
    padding: 0 var(--space-3);
  }

  .directory-categories__label {
    display: none;
  }

  .directory-row {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: var(--space-1);
    padding: var(--space-2);
  }

  .directory-row--selecting {
    grid-template-columns: 18px 36px minmax(0, 1fr);
  }

  .directory-row time {
    display: none;
  }

  .directory-row__actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
    padding-bottom: var(--space-1);
  }

  .directory-row__category {
    grid-column: 2;
  }
}

@keyframes directory-enter {
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
