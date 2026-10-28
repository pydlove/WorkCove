<script setup lang="ts">
import { Check, Pencil, Plus, Trash2, X } from '@lucide/vue'
import { computed, nextTick, ref } from 'vue'

import {
  createVaultCategory,
  deleteVaultCategory,
  updateVaultCategory,
} from '@/api/vault'
import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import type {
  VaultCategory,
  VaultEntry,
} from '@/types/vault'

const props = defineProps<{
  categories: VaultCategory[]
  entries: VaultEntry[]
}>()

const emit = defineEmits<{
  close: []
  changed: []
  notify: [message: string]
}>()

const newCategoryName = ref('')
const newCategoryInput = ref<InstanceType<typeof UiInput> | null>(null)
const editingCategoryId = ref<number | null>(null)
const editingName = ref('')
const deletingCategoryId = ref<number | null>(null)
const fallbackCategoryId = ref<number | null>(null)
const errorMessage = ref('')
const busy = ref(false)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const entry of props.entries) {
    counts.set(entry.category, (counts.get(entry.category) ?? 0) + 1)
  }
  return counts
})

const categoryItems = computed(() => props.categories)
const {
  currentPage: categoryPage,
  pageItems: pagedCategories,
  setPage: setCategoryPage,
} = usePagination(categoryItems)

const deletingCategory = computed(
  () =>
    props.categories.find(
      (category) => category.id === deletingCategoryId.value,
    ) ?? null,
)

const deletingEntryCount = computed(
  () =>
    categoryCounts.value.get(deletingCategory.value?.name ?? '') ?? 0,
)

const fallbackCategories = computed(() =>
  props.categories.filter(
    (category) => category.id !== deletingCategoryId.value,
  ),
)

function categoryNameError(name: string, currentId: number | null = null) {
  if (!name) return '请输入分类名称'
  if ([...name].length > 24) return '分类不能超过 24 个字符'
  if (name === '全部') return '“全部”是系统筛选项，请换一个名称'
  if (
    props.categories.some(
      (category) =>
        category.id !== currentId && category.name === name,
    )
  ) {
    return '这个分类已经存在'
  }
  return ''
}

async function addCategory() {
  const name = newCategoryName.value.trim()
  errorMessage.value = categoryNameError(name)
  if (errorMessage.value || busy.value) return

  busy.value = true
  try {
    await createVaultCategory(name)
    newCategoryName.value = ''
    emit('changed')
    emit('notify', `分类「${name}」已添加`)
    await nextTick()
    newCategoryInput.value?.focus()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    busy.value = false
  }
}

function startEditing(category: VaultCategory) {
  editingCategoryId.value = category.id
  editingName.value = category.name
  errorMessage.value = ''
  void nextTick(() => {
    document.getElementById('edit-vault-category-name')?.focus()
  })
}

function cancelEditing() {
  editingCategoryId.value = null
  editingName.value = ''
  errorMessage.value = ''
}

async function saveEditing() {
  const categoryId = editingCategoryId.value
  const current = props.categories.find(
    (category) => category.id === categoryId,
  )
  if (!current || busy.value) return

  const nextName = editingName.value.trim()
  if (nextName === current.name) {
    cancelEditing()
    return
  }
  errorMessage.value = categoryNameError(nextName, current.id)
  if (errorMessage.value) return

  busy.value = true
  try {
    await updateVaultCategory(current.id, nextName)
    emit('changed')
    emit(
      'notify',
      `分类「${current.name}」已重命名为「${nextName}」`,
    )
    cancelEditing()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    busy.value = false
  }
}

function startDeleting(category: VaultCategory) {
  deletingCategoryId.value = category.id
  fallbackCategoryId.value =
    props.categories.find((item) => item.id !== category.id)?.id ?? null
  errorMessage.value = ''
}

function cancelDeleting() {
  deletingCategoryId.value = null
  fallbackCategoryId.value = null
  errorMessage.value = ''
}

async function confirmDeleting() {
  const category = deletingCategory.value
  if (!category || busy.value) return
  if (!fallbackCategoryId.value) {
    errorMessage.value = '请选择分类迁移目标'
    return
  }

  busy.value = true
  try {
    await deleteVaultCategory(category.id, fallbackCategoryId.value)
    emit('changed')
    emit(
      'notify',
      deletingEntryCount.value > 0
        ? `分类「${category.name}」已删除，条目已迁移`
        : `分类「${category.name}」已删除`,
    )
    cancelDeleting()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    busy.value = false
  }
}

function errorText(error: unknown) {
  return error instanceof Error ? error.message : '分类操作失败，请重试'
}
</script>

<template>
  <UiDialog
    title="管理密码分类"
    description="新增、重命名或删除密码分类，分类由当前账号独立保存。"
    initial-focus="#new-vault-category-name"
    :suspended="Boolean(deletingCategory)"
    @close="emit('close')"
  >
    <div class="vault-category-manager">
      <form class="vault-category-manager__add" @submit.prevent="addCategory">
        <UiInput
          id="new-vault-category-name"
          ref="newCategoryInput"
          v-model="newCategoryName"
          size="lg"
          aria-label="新密码分类名称"
          placeholder="输入新分类名称"
        />
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          :disabled="busy"
        >
          <Plus :size="14" />
          新增
        </UiButton>
      </form>

      <p v-if="errorMessage" class="vault-category-manager__error" role="alert">
        {{ errorMessage }}
      </p>

      <div class="vault-category-manager__list">
        <div
          v-for="category in pagedCategories"
          :key="category.id"
          class="vault-category-manager__item"
        >
          <template v-if="editingCategoryId === category.id">
            <UiInput
              id="edit-vault-category-name"
              v-model="editingName"
              size="sm"
              :aria-label="`重命名 ${category.name}`"
              @keydown.enter.prevent="saveEditing"
              @keydown.esc.prevent="cancelEditing"
            />
            <button
              class="vault-category-manager__action"
              type="button"
              :disabled="busy"
              aria-label="保存分类名称"
              title="保存"
              @click="saveEditing"
            >
              <Check :size="13" />
            </button>
            <button
              class="vault-category-manager__action"
              type="button"
              :disabled="busy"
              aria-label="取消重命名"
              title="取消"
              @click="cancelEditing"
            >
              <X :size="13" />
            </button>
          </template>
          <template v-else>
            <span class="vault-category-manager__name">
              <strong>{{ category.name }}</strong>
              <small>
                {{ categoryCounts.get(category.name) ?? 0 }} 个密码
              </small>
            </span>
            <button
              class="vault-category-manager__action"
              type="button"
              :disabled="busy"
              :aria-label="`编辑分类 ${category.name}`"
              title="重命名分类"
              @click="startEditing(category)"
            >
              <Pencil :size="13" />
            </button>
            <button
              class="vault-category-manager__action vault-category-manager__action--danger"
              type="button"
              :disabled="busy || categories.length <= 1"
              :aria-label="`删除分类 ${category.name}`"
              :title="
                categories.length <= 1
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
        :page="categoryPage"
        :total="categories.length"
        label="密码分类分页"
        @update:page="setCategoryPage"
      />
    </div>
  </UiDialog>

  <UiConfirmDialog
    v-if="deletingCategory"
    title="删除分类？"
    :description="`分类「${deletingCategory.name}」将从密码库中删除。`"
    :warning="
      deletingEntryCount > 0
        ? `${deletingEntryCount} 个密码条目不会被删除，请选择迁移目标。`
        : '该分类下暂无密码条目，删除后无法恢复。'
    "
    confirm-label="删除分类"
    :busy="busy"
    @close="cancelDeleting"
    @confirm="confirmDeleting"
  >
    <div class="vault-category-delete">
      <div
        v-if="deletingEntryCount > 0"
        class="vault-category-delete__targets"
        role="group"
        aria-label="密码分类迁移目标"
      >
        <span>密码移动到</span>
        <button
          v-for="category in fallbackCategories"
          :key="category.id"
          type="button"
          :aria-pressed="fallbackCategoryId === category.id"
          @click="fallbackCategoryId = category.id"
        >
          {{ category.name }}
        </button>
      </div>
      <p v-if="errorMessage" class="vault-category-manager__error" role="alert">
        {{ errorMessage }}
      </p>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.vault-category-manager {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.vault-category-manager__add {
  display: flex;
  gap: var(--space-2);
}

.vault-category-manager__error {
  margin: calc(-1 * var(--space-1)) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.vault-category-manager__list {
  max-height: min(420px, 52vh);
  overflow-y: auto;
  background: var(--surface-secondary);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.vault-category-manager__item {
  display: grid;
  min-height: 44px;
  grid-template-columns: minmax(0, 1fr) 28px 28px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  border-bottom: 1px solid var(--line-soft);
}

.vault-category-manager__item:last-child {
  border-bottom: 0;
}

.vault-category-manager__name {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.vault-category-manager__name strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vault-category-manager__name small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.vault-category-manager__action {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.vault-category-manager__action:hover:not(:disabled) {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.vault-category-manager__action--danger:hover:not(:disabled) {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.vault-category-manager__action:disabled {
  opacity: 0.3;
}

.vault-category-delete {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.vault-category-delete__targets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.vault-category-delete__targets > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.vault-category-delete__targets button {
  height: var(--control-sm);
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
}

.vault-category-delete__targets button[aria-pressed="true"] {
  color: var(--accent);
  background: var(--accent-soft);
  box-shadow: none;
}

@media (max-width: 520px) {
  .vault-category-manager {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>
