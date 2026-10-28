<script setup lang="ts">
import { Check, Pencil, Plus, Trash2, X } from '@lucide/vue'
import {
  computed,
  nextTick,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'

const props = withDefaults(
  defineProps<{
    title?: string
    description: string
    categories: string[]
    counts: ReadonlyMap<string, number>
    itemLabel: string
    defaultCategory: string
    addCategory: (category: string) => boolean
    renameCategory: (
      currentCategory: string,
      nextCategory: string,
    ) => boolean
    deleteCategory: (
      category: string,
      reassignTo: string,
    ) => boolean
  }>(),
  {
    title: '管理分类',
  },
)

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const newCategoryName = ref('')
const newCategoryInput = ref<InstanceType<typeof UiInput> | null>(null)
const editingCategory = ref('')
const editingName = ref('')
const deletingCategory = ref('')
const deleteFallback = ref('')
const errorMessage = ref('')

const activeCategories = computed(() =>
  props.categories.length > 0
    ? props.categories
    : [props.defaultCategory],
)
const deletingItemCount = computed(
  () => props.counts.get(deletingCategory.value) ?? 0,
)
const fallbackCategories = computed(() =>
  activeCategories.value.filter(
    (category) => category !== deletingCategory.value,
  ),
)
const {
  currentPage,
  pageItems: pagedCategories,
  setPage,
} = usePagination(activeCategories)

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
  if (!props.addCategory(category)) {
    errorMessage.value = '保存分类失败，请刷新页面后重试'
    return
  }
  newCategoryName.value = ''
  emit('notify', `分类「${category}」已添加`)
  void nextTick(() => newCategoryInput.value?.focus())
}

function startEditing(category: string) {
  editingCategory.value = category
  editingName.value = category
  errorMessage.value = ''
  void nextTick(() => {
    document.getElementById('edit-category-name')?.focus()
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
  if (
    !props.renameCategory(currentCategory, nextCategory)
  ) {
    errorMessage.value = '重命名分类失败，请刷新页面后重试'
    return
  }
  emit(
    'notify',
    `分类「${currentCategory}」已重命名为「${nextCategory}」`,
  )
  cancelEditing()
}

function startDeleting(category: string) {
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
  const movedCount = deletingItemCount.value
  if (movedCount > 0 && !deleteFallback.value) {
    errorMessage.value = `请选择${props.itemLabel}迁移目标`
    return
  }
  if (
    !props.deleteCategory(category, deleteFallback.value)
  ) {
    errorMessage.value = '删除分类失败，请刷新页面后重试'
    return
  }
  emit(
    'notify',
    movedCount > 0
      ? `分类「${category}」已删除，${movedCount} 个${props.itemLabel}已移至「${deleteFallback.value}」`
      : `分类「${category}」已删除`,
  )
  cancelDeleting()
}
</script>

<template>
  <UiDialog
    :title="title"
    :description="description"
    initial-focus="#new-category-name"
    scroll-mode="body"
    :suspended="Boolean(deletingCategory)"
    @close="emit('close')"
  >
    <div class="category-manager">
      <form class="category-manager__add" @submit.prevent="addCategory">
        <UiInput
          id="new-category-name"
          ref="newCategoryInput"
          v-model="newCategoryName"
          size="lg"
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
              id="edit-category-name"
              v-model="editingName"
              size="sm"
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
              <small>{{ counts.get(category) ?? 0 }} 个{{ itemLabel }}</small>
            </span>
            <button
              class="category-manager__action"
              type="button"
              :aria-label="`编辑分类 ${category}`"
              title="重命名分类"
              @click="startEditing(category)"
            >
              <Pencil :size="13" />
            </button>
            <button
              class="category-manager__action category-manager__action--danger"
              type="button"
              :disabled="activeCategories.length <= 1"
              :aria-label="`删除分类 ${category}`"
              :title="
                activeCategories.length <= 1
                  ? '至少保留一个分类'
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
        :page="currentPage"
        :total="activeCategories.length"
        label="分类分页"
        @update:page="setPage"
      />
    </div>
  </UiDialog>

  <UiConfirmDialog
    v-if="deletingCategory"
    title="删除分类？"
    :description="`分类「${deletingCategory}」将从 8小时工作台中删除。`"
    :warning="
      deletingItemCount > 0
        ? `${deletingItemCount} 个${itemLabel}不会删除，请先选择迁移目标。`
        : `该分类下暂无${itemLabel}，删除后无法恢复。`
    "
    confirm-label="删除分类"
    @close="cancelDeleting"
    @confirm="confirmDeleting"
  >
    <div class="category-delete-confirm">
      <div
        v-if="deletingItemCount > 0"
        class="category-delete__targets"
        role="group"
        :aria-label="`${itemLabel}迁移目标`"
      >
        <span>{{ itemLabel }}移动到</span>
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
