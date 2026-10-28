<script setup lang="ts">
import {
  Globe2,
  Pencil,
  Plus,
  Server,
  TerminalSquare,
  Trash2,
  X,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { useQuickCommandStore } from '@/stores/useQuickCommandStore'
import type {
  QuickCommandScope,
  TerminalQuickCommand,
} from '@/types/quickCommand'
import type { ManagedServer } from '@/types/managedServer'

const props = defineProps<{
  server: ManagedServer
}>()

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const quickCommands = useQuickCommandStore()
const editingId = ref('')
const name = ref('')
const command = ref('')
const scope = ref<QuickCommandScope>('global')
const errorMessage = ref('')
const deleting = ref<TerminalQuickCommand | null>(null)
const scopeItems = [
  { value: 'global', label: '全局' },
  { value: 'server', label: '仅当前服务器' },
]

const commands = computed(() =>
  quickCommands.commands
    .filter(
      (item) =>
        item.workspaceId === props.server.workspaceId &&
        (item.scope === 'global' ||
          item.serverId === props.server.id),
    )
    .sort(
      (left, right) =>
        Number(right.scope === 'global') -
        Number(left.scope === 'global'),
    ),
)

function resetForm() {
  editingId.value = ''
  name.value = ''
  command.value = ''
  scope.value = 'global'
  errorMessage.value = ''
}

function editCommand(item: TerminalQuickCommand) {
  editingId.value = item.id
  name.value = item.name
  command.value = item.command
  scope.value = item.scope
  errorMessage.value = ''
}

function validate() {
  const normalizedName = name.value.trim()
  const normalizedCommand = command.value
  if (!normalizedCommand.trim()) return '请输入命令'
  if (normalizedCommand.includes('\n') || normalizedCommand.includes('\r')) {
    return '快捷命令只能填写一行，请去掉换行'
  }
  if ([...normalizedCommand].length > 2000) {
    return '命令不能超过 2000 个字符'
  }
  if ([...normalizedName].length > 40) {
    return '名称不能超过 40 个字符'
  }
  if (
    commands.value.some(
      (item) =>
        item.id !== editingId.value &&
        item.command === normalizedCommand,
    )
  ) {
    return '这个快捷命令已经存在'
  }
  return ''
}

function save() {
  errorMessage.value = validate()
  if (errorMessage.value) return

  const normalizedCommand = command.value
  const saved = quickCommands.saveCommand({
    id: editingId.value || undefined,
    workspaceId: props.server.workspaceId,
    scope: scope.value,
    serverId: scope.value === 'server' ? props.server.id : null,
    name:
      name.value.trim() ||
      normalizedCommand.trim().split(/\s+/, 1)[0] ||
      '快捷命令',
    command: normalizedCommand,
  })
  emit(
    'notify',
    editingId.value
      ? `快捷命令「${saved.name}」已更新`
      : `快捷命令「${saved.name}」已添加`,
  )
  resetForm()
}

function confirmDelete() {
  const target = deleting.value
  if (!target) return
  quickCommands.removeCommand(target.id)
  deleting.value = null
  if (editingId.value === target.id) resetForm()
  emit('notify', `快捷命令「${target.name}」已删除`)
}
</script>

<template>
  <UiDialog
    title="管理快捷命令"
    :description="`${server.name} · 全局命令在当前工作空间的所有服务器显示。`"
    initial-focus="#quick-command-name"
    :suspended="Boolean(deleting)"
    @close="emit('close')"
  >
    <div class="quick-command-manager">
      <form
        id="quick-command-form"
        class="quick-command-form"
        @submit.prevent="save"
      >
        <UiFormField
          label="名称"
          control-id="quick-command-name"
          hint="可选，留空时使用命令的第一个单词"
          layout="dialog"
        >
          <UiInput
            id="quick-command-name"
            v-model="name"
            size="lg"
            maxlength="40"
            placeholder="例如：查看磁盘"
          />
        </UiFormField>

        <UiFormField
          label="命令"
          control-id="quick-command-value"
          hint="只支持单行命令，点击后仍需按 Enter 执行"
          layout="dialog"
          required
        >
          <UiInput
            id="quick-command-value"
            v-model="command"
            size="lg"
            maxlength="2000"
            placeholder="例如：df -h"
            spellcheck="false"
          />
        </UiFormField>

        <UiFormField
          label="范围"
          layout="dialog"
          hint="全局命令会在当前工作空间的所有服务器上显示"
        >
          <UiSegmentedControl
            v-model="scope"
            :items="scopeItems"
            size="lg"
            aria-label="快捷命令范围"
          />
        </UiFormField>

        <p v-if="errorMessage" class="quick-command-form__error" role="alert">
          {{ errorMessage }}
        </p>
      </form>

      <div class="quick-command-list">
        <div
          v-for="item in commands"
          :key="item.id"
          class="quick-command-row"
        >
          <span class="quick-command-row__icon">
            <Globe2 v-if="item.scope === 'global'" :size="15" />
            <Server v-else :size="15" />
          </span>
          <span class="quick-command-row__copy">
            <span>
              <strong>{{ item.name }}</strong>
              <small>
                {{ item.scope === 'global' ? '全局' : '仅当前服务器' }}
              </small>
            </span>
            <code>{{ item.command }}</code>
          </span>
          <button
            type="button"
            :aria-label="`编辑快捷命令 ${item.name}`"
            title="编辑"
            @click="editCommand(item)"
          >
            <Pencil :size="13" />
          </button>
          <button
            class="quick-command-row__delete"
            type="button"
            :aria-label="`删除快捷命令 ${item.name}`"
            title="删除"
            @click="deleting = item"
          >
            <Trash2 :size="13" />
          </button>
        </div>

        <div v-if="commands.length === 0" class="quick-command-list__empty">
          <TerminalSquare :size="22" />
          <strong>还没有快捷命令</strong>
          <p>把经常使用的命令添加到终端顶部。</p>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="quick-command-form__actions">
        <UiButton
          v-if="editingId"
          size="lg"
          @click="resetForm"
        >
          <X :size="14" />
          取消编辑
        </UiButton>
        <span v-else />
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="quick-command-form"
        >
          <Plus v-if="!editingId" :size="14" />
          <Pencil v-else :size="14" />
          {{ editingId ? '保存修改' : '新增命令' }}
        </UiButton>
      </div>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="deleting"
    title="删除快捷命令？"
    description="只会删除 8小时工作台中保存的命令，不会影响服务器。"
    confirm-label="删除命令"
    @close="deleting = null"
    @confirm="confirmDelete"
  >
    <strong>{{ deleting.name }}</strong>
    <code>{{ deleting.command }}</code>
  </UiConfirmDialog>
</template>

<style scoped>
.quick-command-manager {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-5) var(--space-4);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.quick-command-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.quick-command-form__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.quick-command-form__actions {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.quick-command-list {
  max-height: 250px;
  overflow-y: auto;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.quick-command-row {
  display: grid;
  min-height: 48px;
  grid-template-columns: 28px minmax(0, 1fr) 26px 26px;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.quick-command-row:last-child {
  border-bottom: 0;
}

.quick-command-row__icon {
  display: grid;
  width: 26px;
  height: 26px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  place-items: center;
}

.quick-command-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.quick-command-row__copy > span {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.quick-command-row__copy small {
  flex: 0 0 auto;
  padding: 1px 5px;
  color: var(--text-subtle);
  font-size: 11px;
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
}

.quick-command-row__copy strong,
.quick-command-row__copy code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-command-row__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.quick-command-row__copy code {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.quick-command-row > button {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.quick-command-row > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.quick-command-row > .quick-command-row__delete:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.quick-command-list__empty {
  display: flex;
  min-height: 130px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.quick-command-list__empty strong {
  margin-top: var(--space-2);
  color: var(--text);
  font-size: var(--font-caption);
}

.quick-command-list__empty p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

code {
  overflow: hidden;
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 520px) {
  .quick-command-manager {
    padding: var(--space-3) var(--space-4) var(--space-4);
  }
}
</style>
