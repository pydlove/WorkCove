<script setup lang="ts">
import {
  Copy,
  FolderInput,
  ListChecks,
  Trash2,
  X,
} from '@lucide/vue'

withDefaults(defineProps<{
  count: number
  total: number
  compact?: boolean
  showDelete?: boolean
}>(), {
  compact: false,
  showDelete: false,
})

const emit = defineEmits<{
  toggleSelectAll: []
  copy: []
  move: []
  delete: []
  cancel: []
}>()
</script>

<template>
  <div
    class="workspace-batch-bar"
    :class="{ 'workspace-batch-bar--compact': compact }"
    role="toolbar"
    aria-label="批量操作"
  >
    <span>已选择 {{ count }} 项</span>
    <button
      type="button"
      :disabled="total === 0"
      @click="emit('toggleSelectAll')"
    >
      <ListChecks :size="13" />
      <span>{{ count === total && total > 0 ? '取消全选' : '全选' }}</span>
    </button>
    <button
      type="button"
      aria-label="复制到其他工作空间"
      title="复制"
      :disabled="count === 0"
      @click="emit('copy')"
    >
      <Copy :size="13" />
      <span>复制</span>
    </button>
    <button
      type="button"
      aria-label="移动到其他工作空间"
      title="移动"
      :disabled="count === 0"
      @click="emit('move')"
    >
      <FolderInput :size="13" />
      <span>移动</span>
    </button>
    <button
      v-if="showDelete"
      class="workspace-batch-bar__delete"
      type="button"
      aria-label="批量删除记录"
      title="删除记录"
      :disabled="count === 0"
      @click="emit('delete')"
    >
      <Trash2 :size="13" />
      <span>删除记录</span>
    </button>
    <button
      class="workspace-batch-bar__cancel"
      type="button"
      aria-label="取消批量操作"
      title="取消"
      @click="emit('cancel')"
    >
      <X :size="13" />
    </button>
  </div>
</template>

<style scoped>
.workspace-batch-bar {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  background: var(--selection-bg);
  border-bottom: 1px solid var(--line-soft);
}

.workspace-batch-bar > span {
  flex: 1;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.workspace-batch-bar > button {
  display: inline-flex;
  height: 26px;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  color: var(--text);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
}

.workspace-batch-bar > button:hover {
  background: var(--canvas-raised);
}

.workspace-batch-bar > .workspace-batch-bar__delete {
  color: var(--system-red);
}

.workspace-batch-bar > .workspace-batch-bar__delete:hover {
  background: color-mix(in srgb, var(--system-red) 8%, var(--canvas-raised));
}

.workspace-batch-bar > button:disabled {
  opacity: 0.4;
}

.workspace-batch-bar > .workspace-batch-bar__cancel {
  width: 26px;
  padding: 0;
  justify-content: center;
  background: transparent;
  box-shadow: none;
}

.workspace-batch-bar > .workspace-batch-bar__cancel:hover {
  background: var(--hover-bg);
}

.workspace-batch-bar--compact {
  gap: 2px;
  padding: var(--space-1) var(--space-2);
}

.workspace-batch-bar--compact > button {
  width: 26px;
  padding: 0;
  justify-content: center;
}

.workspace-batch-bar--compact > button span {
  display: none;
}
</style>
