<script setup lang="ts">
import {
  Check,
  Copy,
  FolderInput,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'

export type WorkspaceTransferMode = 'copy' | 'move'

const props = defineProps<{
  itemLabel: string
  sourceWorkspaceId: string
  initialMode?: WorkspaceTransferMode
}>()

const emit = defineEmits<{
  close: []
  transfer: [mode: WorkspaceTransferMode, targetWorkspaceId: string]
}>()

const workspaces = useWorkspaceStore()
const mode = ref<WorkspaceTransferMode>(props.initialMode ?? 'copy')
const targetWorkspaceId = ref('')
const modeItems = [
  { value: 'copy', label: '复制' },
  { value: 'move', label: '移动' },
]

const targets = computed(() =>
  workspaces.workspaces.filter(
    (workspace) => workspace.id !== props.sourceWorkspaceId,
  ),
)

const sourceWorkspace = computed(() =>
  workspaces.workspaces.find(
    (workspace) => workspace.id === props.sourceWorkspaceId,
  ),
)

const selectedTarget = computed(() =>
  targets.value.find(
    (workspace) => workspace.id === targetWorkspaceId.value,
  ),
)

if (!targetWorkspaceId.value && targets.value.length > 0) {
  targetWorkspaceId.value = targets.value[0].id
}

function submit() {
  if (!targetWorkspaceId.value) return
  emit('transfer', mode.value, targetWorkspaceId.value)
}
</script>

<template>
  <UiDialog
    title="处理到其他工作空间"
    description="复制会保留原数据；移动会从当前工作空间移除。"
    initial-focus="#workspace-transfer-cancel"
    @close="emit('close')"
  >
    <div class="workspace-transfer">
      <div class="workspace-transfer__source">
        <span>{{ itemLabel }}</span>
        <small>当前：{{ sourceWorkspace?.name || '未知工作空间' }}</small>
      </div>

      <UiSegmentedControl
        v-model="mode"
        :items="modeItems"
        aria-label="迁移方式"
      />

      <div class="workspace-transfer__targets" role="listbox">
        <p>目标工作空间</p>
        <button
          v-for="workspace in targets"
          :key="workspace.id"
          class="workspace-transfer__target"
          type="button"
          role="option"
          :aria-selected="targetWorkspaceId === workspace.id"
          @click="targetWorkspaceId = workspace.id"
        >
          <span class="workspace-transfer__mark">
            {{ workspace.name.slice(0, 1).toUpperCase() }}
          </span>
          <span>{{ workspace.name }}</span>
          <Check
            v-if="targetWorkspaceId === workspace.id"
            :size="14"
          />
        </button>
        <p v-if="targets.length === 0" class="workspace-transfer__empty">
          请先新增一个工作空间
        </p>
      </div>

    </div>

    <template #footer>
      <UiButton
        id="workspace-transfer-cancel"
        size="lg"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        :disabled="!selectedTarget"
        @click="submit"
      >
        <Copy v-if="mode === 'copy'" :size="14" />
        <FolderInput v-else :size="14" />
        {{ mode === 'copy' ? '复制到工作空间' : '移动到工作空间' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.workspace-transfer {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.workspace-transfer__source {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.workspace-transfer__source > span {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-transfer__source small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.workspace-transfer__targets {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.workspace-transfer__targets > p {
  margin: 0 0 var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.workspace-transfer__target {
  display: grid;
  width: 100%;
  min-height: 36px;
  grid-template-columns: 26px minmax(0, 1fr) 16px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.workspace-transfer__target:hover,
.workspace-transfer__target[aria-selected="true"] {
  background: var(--hover-bg);
}

.workspace-transfer__target > span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-transfer__mark {
  display: grid;
  width: 26px;
  height: 26px;
  color: var(--accent);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  place-items: center;
}

.workspace-transfer__empty {
  padding: var(--space-3);
  text-align: center;
}

@media (max-width: 520px) {
  .workspace-transfer {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>
