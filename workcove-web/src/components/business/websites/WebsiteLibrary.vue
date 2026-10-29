<script setup lang="ts">
import {
  ArrowRightLeft,
  Folder,
  FolderOpen,
  Globe2,
  GripVertical,
  ListChecks,
  Pencil,
  Plus,
  Settings2,
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
import WebsiteCategoryDialog from '@/components/business/websites/WebsiteCategoryDialog.vue'
import EmbeddedBrowserDialog from '@/components/business/websites/EmbeddedBrowserDialog.vue'
import WorkspaceBatchBar from '@/components/business/workspaces/WorkspaceBatchBar.vue'
import WorkspaceTransferDialog, {
  type WorkspaceTransferMode,
} from '@/components/business/workspaces/WorkspaceTransferDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCategorySelect from '@/components/ui/UiCategorySelect.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import { usePagination } from '@/composables/usePagination'
import {
  DEFAULT_WEBSITE_CATEGORY,
  shortcutIconUrls,
  sortWebsitesByUsage,
  websiteHostname,
} from '@/services/localShortcuts'
import { useAuthStore } from '@/stores/useAuthStore'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  ShortcutInput,
  WebsiteShortcut,
} from '@/types/shortcut'
import {
  UNCATEGORIZED_CATEGORY,
  visibleCategoryNames,
} from '@/utils/categoryFilter'
import { openWebsiteUrl } from '@/utils/openWebsite'

const emit = defineEmits<{
  notify: [message: string]
}>()

const session = useAuthStore()
const shortcuts = useShortcutStore()
const workspaces = useWorkspaceStore()
const selectedCategory = ref('全部')
const searchQuery = ref('')
const editingWebsite = ref<WebsiteShortcut | null>(null)
const websiteDialogOpen = ref(false)
const transferWebsite = ref<WebsiteShortcut | null>(null)
const selectionMode = ref(false)
const selectedWebsiteIds = ref<string[]>([])
const batchTransferMode = ref<WorkspaceTransferMode | null>(null)
const defaultWebsiteCategory = ref(DEFAULT_WEBSITE_CATEGORY)
const categoryManagerOpen = ref(false)
const categoryOrder = ref<string[]>([])
const draggingCategory = ref('')
const dragOverCategory = ref('')
const dragPointerId = ref<number | null>(null)
const embeddedWebsite = ref<WebsiteShortcut | null>(null)
const supportsEmbeddedBrowser = Boolean(
  window.workCoveDesktop?.openEmbeddedBrowser,
)

const categories = computed(
  () =>
    shortcuts.categoriesByWorkspace[workspaces.activeWorkspaceId] ?? [
      DEFAULT_WEBSITE_CATEGORY,
    ],
)

const workspaceWebsites = computed(() =>
  shortcuts.websites.filter(
    (website) =>
      website.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  workspaceWebsites.value.forEach((website) => {
    counts.set(website.category, (counts.get(website.category) ?? 0) + 1)
  })
  return counts
})

const categoryDisplayOrder = computed(() =>
  visibleCategoryNames(categoryOrder.value, categoryCounts.value),
)

const filteredWebsites = computed(() => {
  const keyword = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  const matches = workspaceWebsites.value
    .filter(
      (website) =>
        selectedCategory.value === '全部' ||
        website.category === selectedCategory.value,
    )
    .filter(
      (website) =>
        !keyword ||
        website.name.toLocaleLowerCase('zh-CN').includes(keyword) ||
        website.url.toLocaleLowerCase('zh-CN').includes(keyword),
    )
  return sortWebsitesByUsage(matches)
})
const {
  currentPage,
  pageItems: pagedWebsites,
  setPage,
} = usePagination(
  filteredWebsites,
  () => `${searchQuery.value}\n${selectedCategory.value}`,
)

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
    websiteDialogOpen.value = false
    editingWebsite.value = null
    transferWebsite.value = null
    batchTransferMode.value = null
    categoryManagerOpen.value = false
    selectionMode.value = false
    selectedWebsiteIds.value = []
    selectedCategory.value = '全部'
  },
)

watch(
  categories,
  (items) => {
    if (draggingCategory.value) return
    categoryOrder.value = [
      ...items.filter(
        (category) => category !== UNCATEGORIZED_CATEGORY,
      ),
      ...(items.includes(UNCATEGORIZED_CATEGORY)
        ? [UNCATEGORIZED_CATEGORY]
        : []),
    ]
  },
  { immediate: true },
)

watch(categoryDisplayOrder, (items) => {
  if (
    selectedCategory.value !== '全部' &&
    !items.includes(selectedCategory.value)
  ) {
    selectedCategory.value = '全部'
  }
})

onMounted(() => {
  window.addEventListener('pointermove', handleCategoryPointerMove)
  window.addEventListener('pointerup', endCategoryDrag)
  window.addEventListener('pointercancel', endCategoryDrag)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handleCategoryPointerMove)
  window.removeEventListener('pointerup', endCategoryDrag)
  window.removeEventListener('pointercancel', endCategoryDrag)
})

function addWebsite() {
  editingWebsite.value = null
  defaultWebsiteCategory.value =
    selectedCategory.value === '全部'
      ? DEFAULT_WEBSITE_CATEGORY
      : selectedCategory.value
  websiteDialogOpen.value = true
}

function editWebsite(website: WebsiteShortcut) {
  editingWebsite.value = website
  websiteDialogOpen.value = true
}

function closeDialog() {
  websiteDialogOpen.value = false
  editingWebsite.value = null
}

function saveWebsite(input: ShortcutInput) {
  shortcuts.saveShortcut({
    ...input,
    workspaceId: workspaces.activeWorkspaceId,
  })
  emit('notify', input.id ? '网址已更新' : '网址已添加')
  closeDialog()
}

function removeWebsite(id: string) {
  shortcuts.removeShortcut(id)
  emit('notify', '网址已移除')
  closeDialog()
}

function handleWebsiteTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const website = transferWebsite.value
  if (!website) return
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
        ? shortcuts.copyShortcut(website.id, targetWorkspaceId)
        : shortcuts.moveShortcut(website.id, targetWorkspaceId)
    if (!changed) {
      emit('notify', '未找到可处理的网址，请刷新后再试')
      return
    }

    transferWebsite.value = null
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
    emit(
      'notify',
      mode === 'copy'
        ? `已将「${website.name}」复制到「${targetWorkspace.name}」`
        : `已将「${website.name}」移动到「${targetWorkspace.name}」`,
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
  if (!selectionMode.value) selectedWebsiteIds.value = []
}

function setWebsiteSelected(id: string, selected: boolean) {
  selectedWebsiteIds.value = selected
    ? [...new Set([...selectedWebsiteIds.value, id])]
    : selectedWebsiteIds.value.filter((item) => item !== id)
}

function toggleSelectAllWebsites() {
  selectedWebsiteIds.value =
    selectedWebsiteIds.value.length === filteredWebsites.value.length
      ? []
      : filteredWebsites.value.map((website) => website.id)
}

function startBatchTransfer(mode: WorkspaceTransferMode) {
  if (selectedWebsiteIds.value.length === 0) return
  batchTransferMode.value = mode
}

function handleWebsiteBatchTransfer(
  mode: WorkspaceTransferMode,
  targetWorkspaceId: string,
) {
  const ids = [...selectedWebsiteIds.value]
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
      if (
        mode === 'copy'
          ? shortcuts.copyShortcut(id, targetWorkspaceId)
          : shortcuts.moveShortcut(id, targetWorkspaceId)
      ) {
        changed += 1
      }
    }
    if (changed === 0) {
      emit('notify', '没有可处理的网址，请重新选择后再试')
      return
    }

    batchTransferMode.value = null
    selectionMode.value = false
    selectedWebsiteIds.value = []
    if (mode === 'copy') workspaces.selectWorkspace(targetWorkspaceId)
    emit(
      'notify',
      `已将 ${changed} 个网址${mode === 'copy' ? '复制' : '移动'}到「${targetWorkspace.name}」`,
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

function selectCategory(category: string) {
  if (!draggingCategory.value) selectedCategory.value = category
}

function changeWebsiteCategory(
  website: WebsiteShortcut,
  category: string,
) {
  if (!shortcuts.moveWebsiteToCategory(website.id, category)) {
    return
  }
  emit('notify', `已将「${website.name}」移到「${category}」`)
}

function startCategoryDrag(event: PointerEvent, category: string) {
  if (
    event.button !== 0 ||
    category === UNCATEGORIZED_CATEGORY
  ) {
    return
  }
  event.preventDefault()
  event.stopPropagation()
  draggingCategory.value = category
  dragOverCategory.value = category
  dragPointerId.value = event.pointerId
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
}

function handleCategoryPointerMove(event: PointerEvent) {
  if (
    !draggingCategory.value ||
    dragPointerId.value !== event.pointerId
  ) {
    return
  }
  event.preventDefault()

  const target = document
    .elementFromPoint(event.clientX, event.clientY)
    ?.closest<HTMLElement>('[data-category-name]')
    ?.dataset.categoryName
  if (
    !target ||
    target === draggingCategory.value ||
    target === UNCATEGORIZED_CATEGORY
  ) {
    return
  }

  const from = categoryOrder.value.indexOf(draggingCategory.value)
  const to = categoryOrder.value.indexOf(target)
  if (from < 0 || to < 0 || from === to) return

  const next = [...categoryOrder.value]
  next.splice(from, 1)
  next.splice(to, 0, draggingCategory.value)
  categoryOrder.value = next
  dragOverCategory.value = target
}

function endCategoryDrag() {
  if (!draggingCategory.value) return
  const changed =
    categoryOrder.value.join('\n') !== categories.value.join('\n')
  draggingCategory.value = ''
  dragOverCategory.value = ''
  dragPointerId.value = null
  if (changed) {
    shortcuts.reorderCategories(
      categoryOrder.value,
      workspaces.activeWorkspaceId,
    )
  }
}

function moveCategoryByKeyboard(
  category: string,
  direction: -1 | 1,
) {
  if (category === UNCATEGORIZED_CATEGORY) return
  const from = categoryOrder.value.indexOf(category)
  const to = from + direction
  if (from < 0 || to < 0 || to >= categoryOrder.value.length) return
  const next = [...categoryOrder.value]
  next.splice(from, 1)
  next.splice(to, 0, category)
  categoryOrder.value = next
  shortcuts.reorderCategories(next, workspaces.activeWorkspaceId)
}

async function openWebsite(website: WebsiteShortcut) {
  if (supportsEmbeddedBrowser) {
    embeddedWebsite.value = website
    shortcuts.recordWebsiteClick(website.id)
    return
  }
  const error = await openWebsiteUrl(website.url)
  if (error) {
    emit('notify', `无法打开网址：${error}`)
    return
  }
  shortcuts.recordWebsiteClick(website.id)
}
</script>

<template>
  <section class="website-library">
    <header class="website-toolbar">
      <span class="website-toolbar__count">
        {{ filteredWebsites.length }} 个网址
      </span>
      <UiSearchInput
        v-model="searchQuery"
        label="搜索网址"
        placeholder="搜索名称或网址"
      />
      <UiButton @click="toggleSelectionMode">
        <ListChecks :size="14" />
        {{ selectionMode ? '退出批量' : '批量' }}
      </UiButton>
      <UiButton variant="primary" @click="addWebsite">
        <Plus :size="15" />
        新增网址
      </UiButton>
    </header>

    <WorkspaceBatchBar
      v-if="selectionMode"
      :count="selectedWebsiteIds.length"
      :total="filteredWebsites.length"
      @toggle-select-all="toggleSelectAllWebsites"
      @copy="startBatchTransfer('copy')"
      @move="startBatchTransfer('move')"
      @cancel="toggleSelectionMode"
    />

    <div class="website-library__body">
      <aside class="category-sidebar" aria-label="网址分类">
        <div class="category-sidebar__heading">
          <span>分类</span>
          <button
            class="category-manage-button"
            type="button"
            aria-label="管理网址分类"
            @click="categoryManagerOpen = true"
          >
            <Settings2 :size="12" />
            管理
          </button>
        </div>
        <button
          class="category-item category-item--all"
          :class="{ 'category-item--active': selectedCategory === '全部' }"
          type="button"
          @click="selectedCategory = '全部'"
        >
          <span class="category-drag-placeholder" aria-hidden="true" />
          <FolderOpen :size="15" />
          <span>全部</span>
          <small>{{ workspaceWebsites.length }}</small>
        </button>
        <div
          v-for="category in categoryDisplayOrder"
          :key="category"
          class="category-row"
          :data-category-name="category"
        >
          <button
            class="category-item"
            :class="{
              'category-item--active': selectedCategory === category,
              'category-item--dragging': draggingCategory === category,
              'category-item--drop-target':
                dragOverCategory === category &&
                draggingCategory !== category,
            }"
            type="button"
            :title="`拖动排序；Option + ↑/↓ 调整「${category}」`"
            @click="selectCategory(category)"
            @keydown.alt.up.prevent="moveCategoryByKeyboard(category, -1)"
            @keydown.alt.down.prevent="moveCategoryByKeyboard(category, 1)"
          >
            <span
              v-if="category !== UNCATEGORIZED_CATEGORY"
              class="category-drag-handle"
              aria-hidden="true"
              @pointerdown="startCategoryDrag($event, category)"
              @click.stop.prevent
            >
              <GripVertical :size="12" />
            </span>
            <span
              v-else
              class="category-drag-placeholder"
              aria-hidden="true"
            />
            <Folder :size="15" />
            <span>{{ category }}</span>
            <small>{{ categoryCounts.get(category) ?? 0 }}</small>
          </button>
        </div>
      </aside>

      <div class="website-list">
        <div v-if="filteredWebsites.length > 0" class="website-list__rows">
          <article
            v-for="website in pagedWebsites"
            :key="website.id"
            class="website-row"
            :class="{ 'website-row--selecting': selectionMode }"
          >
            <UiCheckbox
              v-if="selectionMode"
              :model-value="selectedWebsiteIds.includes(website.id)"
              :label="`选择网址 ${website.name}`"
              @update:model-value="setWebsiteSelected(website.id, $event)"
            />
            <button
              class="website-row__main"
              type="button"
              :aria-label="`打开 ${website.name}`"
              @click="openWebsite(website)"
            >
              <ShortcutVisual
                :sources="shortcutIconUrls(website)"
                mode="website"
                :size="36"
              />
              <span class="website-row__copy">
                <strong>{{ website.name }}</strong>
                <small>{{ websiteHostname(website.url) }}</small>
              </span>
            </button>
            <UiCategorySelect
              class="website-row__category"
              :value="website.category"
              :options="categoryOrder"
              :label="`更改 ${website.name} 的分类`"
              :title="`更改「${website.name}」的分类`"
              @change="changeWebsiteCategory(website, $event)"
            />
            <span
              class="website-row__clicks"
              :title="`累计点击 ${website.clickCount} 次`"
            >
              {{ website.clickCount }} 次
            </span>
            <button
              class="website-row__workspace"
              type="button"
              :aria-label="`处理 ${website.name} 的工作空间`"
              title="复制或移动到其他工作空间"
              @click="transferWebsite = website"
            >
              <ArrowRightLeft :size="14" />
            </button>
            <button
              class="website-row__edit"
              type="button"
              :aria-label="`编辑 ${website.name}`"
              title="编辑网址"
              @click="editWebsite(website)"
            >
              <Pencil :size="14" />
            </button>
          </article>
        </div>

        <div v-else class="website-empty">
          <Globe2 :size="28" />
          <strong>
            {{
              workspaceWebsites.length === 0
                ? '还没有添加网址'
                : '没有符合条件的网址'
            }}
          </strong>
          <p>
            {{
              workspaceWebsites.length === 0
                ? '添加网址后，工作台会按点击量展示常用 Top 10。'
                : '试试切换分类或调整搜索关键词。'
            }}
          </p>
          <UiButton
            v-if="workspaceWebsites.length === 0"
            variant="primary"
            @click="addWebsite"
          >
            <Plus :size="15" />
            添加第一个网址
          </UiButton>
        </div>

        <UiPagination
          :page="currentPage"
          :total="filteredWebsites.length"
          label="网址分页"
          @update:page="setPage"
        />
      </div>
    </div>

    <ShortcutDialog
      v-if="websiteDialogOpen"
      mode="website"
      :shortcut="editingWebsite"
      :categories="categories"
      :default-category="defaultWebsiteCategory"
      @close="closeDialog"
      @save="saveWebsite"
      @remove="removeWebsite"
    />

    <EmbeddedBrowserDialog
      v-if="embeddedWebsite"
      :url="embeddedWebsite.url"
      :title="embeddedWebsite.name"
      :workspace-id="embeddedWebsite.workspaceId"
      @close="embeddedWebsite = null"
      @notify="emit('notify', $event)"
    />

    <WorkspaceTransferDialog
      v-if="transferWebsite"
      :item-label="`网址「${transferWebsite.name}」`"
      :source-workspace-id="transferWebsite.workspaceId"
      @close="transferWebsite = null"
      @transfer="handleWebsiteTransfer"
    />

    <WorkspaceTransferDialog
      v-if="batchTransferMode"
      :item-label="`已选择 ${selectedWebsiteIds.length} 个网址`"
      :source-workspace-id="workspaces.activeWorkspaceId"
      :initial-mode="batchTransferMode"
      @close="batchTransferMode = null"
      @transfer="handleWebsiteBatchTransfer"
    />

    <WebsiteCategoryDialog
      v-if="categoryManagerOpen"
      @close="categoryManagerOpen = false"
      @notify="emit('notify', $event)"
    />
  </section>
</template>

<style scoped>
.website-library {
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: website-enter var(--duration-window) var(--ease-apple) both;
}

.website-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.website-toolbar__count {
  flex: 1;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.website-library__body {
  display: grid;
  min-height: 420px;
  grid-template-columns: 176px minmax(0, 1fr);
}

.category-sidebar {
  padding: var(--space-2);
  margin: var(--space-3) 0 var(--space-3) var(--space-3);
  background: transparent;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.category-sidebar__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin: 0 var(--space-2) var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.category-manage-button {
  display: inline-flex;
  height: 22px;
  align-items: center;
  gap: 3px;
  padding: 0 5px;
  color: var(--accent);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-badge);
}

.category-manage-button:hover {
  background: var(--accent-soft);
}

.category-item {
  display: grid;
  width: 100%;
  min-height: 30px;
  grid-template-columns: 12px 16px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.category-row {
  position: relative;
}

.category-item--dragging {
  opacity: 0.42;
}

.category-item--drop-target {
  box-shadow: inset 0 2px 0 var(--system-blue);
}

.category-drag-handle {
  display: grid;
  color: var(--text-subtle);
  cursor: grab;
  opacity: 0.28;
  place-items: center;
  touch-action: none;
  transition: opacity var(--duration-fast) var(--ease-apple);
}

.category-drag-placeholder {
  width: 12px;
  height: 1px;
}

.category-item:hover .category-drag-handle,
.category-item:focus-visible .category-drag-handle,
.category-item--dragging .category-drag-handle {
  opacity: 1;
}

.category-item--active .category-drag-handle {
  color: rgb(255 255 255 / 62%);
}

.category-item--dragging .category-drag-handle {
  cursor: grabbing;
}

.category-item:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.category-item--active {
  color: #ffffff;
  background: var(--system-blue);
}

.category-item--active:hover {
  color: #ffffff;
  background: var(--system-blue-hover);
}

.category-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-item small {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.category-item--active small {
  color: rgb(255 255 255 / 72%);
}

.website-list {
  min-width: 0;
}

.website-list__rows {
  display: flex;
  flex-direction: column;
}

.website-row {
  position: relative;
  display: grid;
  min-height: 58px;
  grid-template-columns: minmax(0, 1fr) 96px 58px 28px 28px;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-2);
  border-bottom: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  transition: background var(--duration-fast) var(--ease-apple);
}

.website-row--selecting {
  grid-template-columns:
    18px minmax(0, 1fr) 96px 58px 28px
    28px;
}

.website-row:last-child {
  border-bottom: 0;
}

.website-row:hover {
  background: var(--hover-bg);
}

.website-row:focus-within {
  background: var(--selection-bg);
}

.website-row:focus-within::before {
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

.website-row__main:focus-visible,
.website-row__workspace:focus-visible,
.website-row__edit:focus-visible {
  outline: none;
}

.website-row__main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
  text-align: left;
  background: transparent;
}

.website-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.website-row__copy strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.website-row__copy small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.website-row__clicks {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-align: right;
  white-space: nowrap;
}

.website-row__workspace,
.website-row__edit {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.website-row__workspace:hover,
.website-row__edit:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.website-empty {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.website-empty strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.website-empty p {
  max-width: 320px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 820px) {
  .website-library__body {
    grid-template-columns: 1fr;
  }

  .category-sidebar {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-1);
    padding: var(--space-2);
    margin: 0;
    border: 0;
    border-bottom: 1px solid var(--line-soft);
    border-radius: 0;
  }

  .category-sidebar__heading {
    flex: 0 0 auto;
    margin: 0;
    padding: 0 var(--space-2);
  }

  .category-sidebar__heading > span {
    display: none;
  }

  .category-item {
    width: auto;
    flex: 0 0 auto;
    grid-template-columns: 12px 16px auto auto;
    padding: 0 var(--space-5) 0 var(--space-3);
  }

  .category-row .category-item > small {
    display: none;
  }
}

@media (max-width: 620px) {
  .website-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .website-toolbar__count {
    display: flex;
    flex-basis: 100%;
    align-items: center;
  }

  .website-row {
    grid-template-columns: minmax(0, 1fr) 28px 28px;
  }

  .website-row--selecting {
    grid-template-columns: 18px minmax(0, 1fr) 28px 28px;
  }

  .website-row__category,
  .website-row__clicks {
    display: none;
  }
}

@keyframes website-enter {
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
