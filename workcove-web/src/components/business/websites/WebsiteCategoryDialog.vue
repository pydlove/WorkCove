<script setup lang="ts">
import { Check, Pencil, Plus, Trash2, X } from '@lucide/vue'
import { computed, nextTick, ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import { DEFAULT_WEBSITE_CATEGORY } from '@/services/localShortcuts'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const shortcuts = useShortcutStore()
const workspaces = useWorkspaceStore()
const newCategoryName = ref('')
const newCategoryInput = ref<InstanceType<typeof UiInput> | null>(null)
const editingCategory = ref('')
const editingName = ref('')
const deletingCategory = ref('')
const deleteFallback = ref('')
const errorMessage = ref('')

const activeCategories = computed(
  () =>
    shortcuts.categoriesByWorkspace[workspaces.activeWorkspaceId] ?? [
      DEFAULT_WEBSITE_CATEGORY,
    ],
)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const website of shortcuts.websites) {
    if (website.workspaceId !== workspaces.activeWorkspaceId) continue
    counts.set(website.category, (counts.get(website.category) ?? 0) + 1)
  }
  return counts
})

const deletingWebsiteCount = computed(
  () => categoryCounts.value.get(deletingCategory.value) ?? 0,
)

const fallbackCategories = computed(() =>
  activeCategories.value.filter(
    (category) => category !== deletingCategory.value,
  ),
)
const categoryItems = computed(() => activeCategories.value)
const {
  currentPage: categoryPage,
  pageItems: pagedCategories,
  setPage: setCategoryPage,
} = usePagination(categoryItems)

function isProtected(category: string) {
  return category === DEFAULT_WEBSITE_CATEGORY
}

function categoryErrorMessage(
  name: string,
  currentCategory = '',
) {
  if (!name) return '请输入分类名称'
  if ([...name].length > 24) return '分类不能超过 24 个字符'
  if (name === '全部') return '“全部”是系统筛选项，请换一个名称'
  if (
    activeCategories.value.some(
      (category) =>
        category !== currentCategory && category === name,
    )
  ) {
    return '这个分类已经存在'
  }
  return ''
}

function addCategory() {
  const category = newCategoryName.value.trim()
  errorMessage.value = categoryErrorMessage(category)
  if (errorMessage.value) return

  try {
    if (
      !shortcuts.addCategory(
        category,
        workspaces.activeWorkspaceId,
      )
    ) {
      errorMessage.value = '保存分类失败，请刷新页面后重试'
      return
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '保存分类失败'
    return
  }

  newCategoryName.value = ''
  emit('notify', `分类「${category}」已添加`)
  void nextTick(() => newCategoryInput.value?.focus())
}

function startEditing(category: string) {
  if (isProtected(category)) return
  editingCategory.value = category
  editingName.value = category
  errorMessage.value = ''
  void nextTick(() => {
    document.getElementById('edit-website-category-name')?.focus()
  })
}

function cancelEditing() {
  editingCategory.value = ''
  editingName.value = ''
  errorMessage.value = ''
}

function saveEditing() {
  const currentCategory = editingCategory.value
  const nextCategory = editingName.value.trim()
  if (nextCategory === currentCategory) {
    cancelEditing()
    return
  }

  errorMessage.value = categoryErrorMessage(
    nextCategory,
    currentCategory,
  )
  if (errorMessage.value) return

  try {
    if (
      !shortcuts.renameCategory(
        currentCategory,
        nextCategory,
        workspaces.activeWorkspaceId,
      )
    ) {
      errorMessage.value = '重命名分类失败，请刷新页面后重试'
      return
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '重命名分类失败'
    return
  }

  emit(
    'notify',
    `分类「${currentCategory}」已重命名为「${nextCategory}」`,
  )
  cancelEditing()
}

function startDeleting(category: string) {
  if (isProtected(category)) return
  deletingCategory.value = category
  deleteFallback.value = fallbackCategories.value[0] ?? ''
  errorMessage.value = ''
}

function cancelDeleting() {
  deletingCategory.value = ''
  deleteFallback.value = ''
  errorMessage.value = ''
}

function confirmDeleting() {
  const category = deletingCategory.value
  const movedCount = deletingWebsiteCount.value
  if (movedCount > 0 && !deleteFallback.value) {
    errorMessage.value = '请选择网址迁移目标'
    return
  }

  try {
    if (
      !shortcuts.deleteCategory(
        category,
        deleteFallback.value,
        workspaces.activeWorkspaceId,
      )
    ) {
      errorMessage.value = '删除分类失败，请刷新页面后重试'
      return
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '删除分类失败'
    return
  }

  emit(
    'notify',
    movedCount > 0
      ? `分类「${category}」已删除，${movedCount} 个网址已移至「${deleteFallback.value}」`
      : `分类「${category}」已删除`,
  )
  cancelDeleting()
}
</script>

<template>
  <UiDialog
    title="管理网址分类"
    description="新增、重命名或删除当前工作空间的网址分类。"
    initial-focus="#new-website-category-name"
    :suspended="Boolean(deletingCategory)"
    @close="emit('close')"
  >
    <div class="category-manager">
      <form class="category-manager__add" @submit.prevent="addCategory">
        <UiInput
          id="new-website-category-name"
          ref="newCategoryInput"
          v-model="newCategoryName"
          size="lg"
          maxlength="24"
          aria-label="新分类名称"
          placeholder="输入新分类名称"
        />
        <UiButton variant="primary" size="lg" type="submit">
          <Plus :size="14" />
          新增
        </UiButton>
      </form>

      <p v-if="errorMessage" class="category-manager__error" role="alert">
        {{ errorMessage }}
      </p>

      <div class="category-manager__list">
        <div
          v-for="category in pagedCategories"
          :key="category"
          class="category-manager__item"
        >
          <template v-if="editingCategory === category">
            <UiInput
              id="edit-website-category-name"
              v-model="editingName"
              size="sm"
              maxlength="24"
              :aria-label="`重命名 ${category}`"
              @keydown.enter.prevent="saveEditing"
              @keydown.esc.prevent="cancelEditing"
            />
            <button
              class="category-manager__action"
              type="button"
              aria-label="保存分类名称"
              title="保存"
              @click="saveEditing"
            >
              <Check :size="13" />
            </button>
            <button
              class="category-manager__action"
              type="button"
              aria-label="取消重命名"
              title="取消"
              @click="cancelEditing"
            >
              <X :size="13" />
            </button>
          </template>
          <template v-else>
            <span class="category-manager__name">
              <strong>{{ category }}</strong>
              <small>
                {{ categoryCounts.get(category) ?? 0 }} 个网址
              </small>
            </span>
            <button
              class="category-manager__action"
              type="button"
              :disabled="isProtected(category)"
              :aria-label="`编辑分类 ${category}`"
              :title="
                isProtected(category)
                  ? '默认分类不能重命名'
                  : '重命名分类'
              "
              @click="startEditing(category)"
            >
              <Pencil :size="13" />
            </button>
            <button
              class="category-manager__action category-manager__action--danger"
              type="button"
              :disabled="isProtected(category)"
              :aria-label="`删除分类 ${category}`"
              :title="
                isProtected(category)
                  ? '默认分类不能删除'
                  : '删除分类'
              "
              @click="startDeleting(category)"
            >
              <Trash2 :size="13" />
            </button>
          </template>
        </div>
      </div>

      <UiPagination
        :page="categoryPage"
        :total="activeCategories.length"
        label="分类分页"
        @update:page="setCategoryPage"
      />
    </div>
  </UiDialog>

  <UiConfirmDialog
    v-if="deletingCategory"
    title="删除网址分类？"
    :description="`分类「${deletingCategory}」将从当前工作空间删除。`"
    :warning="
      deletingWebsiteCount > 0
        ? `${deletingWebsiteCount} 个网址不会删除，请选择迁移目标。`
        : '该分类下暂无网址，删除后无法恢复。'
    "
    confirm-label="删除分类"
    @close="cancelDeleting"
    @confirm="confirmDeleting"
  >
    <div class="category-delete-confirm">
      <div
        v-if="deletingWebsiteCount > 0"
        class="category-delete__targets"
        role="group"
        aria-label="网址迁移目标"
      >
        <span>网址移动到</span>
        <button
          v-for="category in fallbackCategories"
          :key="category"
          type="button"
          :aria-pressed="deleteFallback === category"
          @click="deleteFallback = category"
        >
          {{ category }}
        </button>
      </div>
      <p v-if="errorMessage" class="category-manager__error" role="alert">
        {{ errorMessage }}
      </p>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.category-manager {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.category-manager__add {
  display: flex;
  gap: var(--space-2);
}

.category-manager__error {
  margin: calc(-1 * var(--space-1)) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.category-manager__list {
  max-height: min(420px, 52vh);
  overflow-y: auto;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.category-manager__item {
  display: grid;
  min-height: 42px;
  grid-template-columns: minmax(0, 1fr) 26px 26px;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.category-manager__item:last-child {
  border-bottom: 0;
}

.category-manager__name {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.category-manager__name strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-manager__name small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.category-manager__action {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.category-manager__action:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.category-manager__action--danger:hover:not(:disabled) {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.category-manager__action:disabled {
  opacity: 0.3;
}

.category-delete-confirm {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.category-delete__targets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
}

.category-delete__targets > span {
  margin-right: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.category-delete__targets button {
  height: var(--control-sm);
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
}

.category-delete__targets button[aria-pressed="true"] {
  color: var(--accent);
  background: var(--accent-soft);
  box-shadow: none;
}

@media (max-width: 520px) {
  .category-manager {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>
