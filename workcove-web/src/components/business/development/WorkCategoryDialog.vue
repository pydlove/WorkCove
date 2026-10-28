<script setup lang="ts">
import { computed } from 'vue'

import CategoryManagerDialog from '@/components/business/development/CategoryManagerDialog.vue'
import { useWorkDirectoryStore } from '@/stores/useWorkDirectoryStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { DEFAULT_WORK_DIRECTORY_CATEGORY } from '@/types/workDirectory'

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const directories = useWorkDirectoryStore()
const workspaces = useWorkspaceStore()

const categories = computed(
  () =>
    directories.categoriesByWorkspace[workspaces.activeWorkspaceId] ?? [
      DEFAULT_WORK_DIRECTORY_CATEGORY,
    ],
)
const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const directory of directories.directories) {
    if (directory.workspaceId !== workspaces.activeWorkspaceId) continue
    counts.set(
      directory.category,
      (counts.get(directory.category) ?? 0) + 1,
    )
  }
  return counts
})

function addCategory(category: string) {
  return directories.addCategory(
    category,
    workspaces.activeWorkspaceId,
  )
}

function renameCategory(
  currentCategory: string,
  nextCategory: string,
) {
  return directories.renameCategory(
    currentCategory,
    nextCategory,
    workspaces.activeWorkspaceId,
  )
}

function deleteCategory(
  category: string,
  reassignTo: string,
) {
  return directories.deleteCategory(
    category,
    reassignTo,
    workspaces.activeWorkspaceId,
  )
}
</script>

<template>
  <CategoryManagerDialog
    title="管理目录分类"
    description="新增、重命名或删除工作目录分类。"
    :categories="categories"
    :counts="categoryCounts"
    item-label="目录"
    :default-category="DEFAULT_WORK_DIRECTORY_CATEGORY"
    :add-category="addCategory"
    :rename-category="renameCategory"
    :delete-category="deleteCategory"
    @close="emit('close')"
    @notify="emit('notify', $event)"
  />
</template>
