<script setup lang="ts">
import {
  Check,
  ChevronDown,
  Pencil,
  Plus,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import WorkspaceIcon from '@/components/business/workspaces/WorkspaceIcon.vue'
import WorkspaceIconPicker from '@/components/business/workspaces/WorkspaceIconPicker.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import {
  type Workspace,
} from '@/types/workspace'

const emit = defineEmits<{
  notify: [message: string]
}>()

const session = useAuthStore()
const workspaces = useWorkspaceStore()
const open = ref(false)
const creating = ref(false)
const newName = ref('')
const newIconImage = ref('')
const newIconProcessing = ref(false)
const editingWorkspace = ref<Workspace | null>(null)
const editingIconImage = ref('')
const editingIconProcessing = ref(false)
const errorMessage = ref('')
const root = ref<HTMLElement | null>(null)
const activeWorkspaceDisplayName = computed(() => {
  const name = workspaces.activeWorkspace?.name ?? ''
  const characters = Array.from(name)
  return characters.length > 6
    ? `${characters.slice(0, 6).join('')}…`
    : name
})

watch(
  () => session.email,
  (email) => workspaces.load(email),
  { immediate: true },
)

function selectWorkspace(id: string) {
  workspaces.selectWorkspace(id)
  open.value = false
}

function startCreating() {
  open.value = false
  creating.value = true
  newName.value = ''
  newIconImage.value = ''
  newIconProcessing.value = false
  errorMessage.value = ''
}

function createWorkspace() {
  if (newIconProcessing.value) return
  const name = newName.value.trim()
  if (!name) {
    errorMessage.value = '请输入工作空间名称'
    return
  }
  if ([...name].length > 40) {
    errorMessage.value = '工作空间名称不能超过 40 个字符'
    return
  }
  if (workspaces.workspaces.some((workspace) => workspace.name === name)) {
    errorMessage.value = '这个工作空间已经存在'
    return
  }

  const workspace = workspaces.createWorkspace(name, newIconImage.value)
  if (!workspace) {
    errorMessage.value = '无法创建工作空间'
    return
  }
  creating.value = false
  emit('notify', '工作空间已创建')
}

function startEditingIcon(workspace: Workspace) {
  open.value = false
  editingWorkspace.value = workspace
  editingIconImage.value = workspace.iconImage
  editingIconProcessing.value = false
}

function saveWorkspaceIcon() {
  if (editingIconProcessing.value) return
  const workspace = editingWorkspace.value
  if (!workspace) return
  const saved = workspaces.updateWorkspaceIcon(
    workspace.id,
    editingIconImage.value,
  )
  if (!saved) {
    emit('notify', '工作空间不存在')
    return
  }
  editingWorkspace.value = null
  emit('notify', '工作空间图标已更新')
}

function handlePointerDown(event: PointerEvent) {
  if (
    open.value &&
    event.target instanceof Node &&
    !root.value?.contains(event.target)
  ) {
    open.value = false
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown)
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div ref="root" class="workspace-switcher">
    <button
      class="workspace-switcher__trigger"
      :class="{ 'workspace-switcher__trigger--open': open }"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open"
      :title="`切换工作空间 · ${workspaces.activeWorkspace?.name ?? ''}`"
      @click="open = !open"
    >
      <WorkspaceIcon
        v-if="workspaces.activeWorkspace"
        :icon="workspaces.activeWorkspace.icon"
        :tone="workspaces.activeWorkspace.iconTone"
        :image-url="workspaces.activeWorkspace.iconImage"
        :size="12"
      />
      <span class="workspace-switcher__copy">
        <strong>{{ activeWorkspaceDisplayName }}</strong>
      </span>
      <ChevronDown :size="13" />
    </button>

    <div v-if="open" class="workspace-switcher__menu" role="menu">
      <div class="workspace-switcher__heading">
        <span>工作空间</span>
        <button type="button" @click="startCreating">
          <Plus :size="13" />
          新增
        </button>
      </div>
      <div
        v-for="workspace in workspaces.workspaces"
        :key="workspace.id"
        class="workspace-switcher__option"
        role="none"
        :class="{
          'workspace-switcher__option--active':
            workspaces.activeWorkspaceId === workspace.id,
        }"
      >
        <button
          class="workspace-switcher__option-main"
          type="button"
          role="menuitemradio"
          :aria-checked="workspaces.activeWorkspaceId === workspace.id"
          @click="selectWorkspace(workspace.id)"
        >
          <WorkspaceIcon
            :icon="workspace.icon"
            :tone="workspace.iconTone"
            :image-url="workspace.iconImage"
            :size="14"
          />
          <span>{{ workspace.name }}</span>
          <Check
            v-if="workspaces.activeWorkspaceId === workspace.id"
            :size="14"
          />
        </button>
        <button
          class="workspace-switcher__edit"
          type="button"
          role="menuitem"
          :aria-label="`设置 ${workspace.name} 的图标`"
          :title="`设置 ${workspace.name} 的图标`"
          @click="startEditingIcon(workspace)"
        >
          <Pencil :size="12" />
        </button>
      </div>
    </div>

    <UiDialog
      v-if="creating"
      title="新增工作空间"
      description="上传一张图片作为工作空间图标。"
      height="compact"
      initial-focus="#workspace-name"
      @close="creating = false"
    >
      <form
        id="workspace-create-form"
        class="workspace-create"
        @submit.prevent="createWorkspace"
      >
        <UiFormField label="图标">
          <WorkspaceIconPicker
            v-model:image="newIconImage"
            v-model:processing="newIconProcessing"
            :fallback-icon="workspaces.activeWorkspace?.icon"
            :fallback-tone="workspaces.activeWorkspace?.iconTone"
          />
        </UiFormField>
        <UiFormField
          label="名称"
          control-id="workspace-name"
          required
        >
          <UiInput
            id="workspace-name"
            v-model="newName"
            size="lg"
            maxlength="40"
            aria-label="工作空间名称"
            placeholder="例如：个人项目"
          />
        </UiFormField>
        <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>
      </form>

      <template #footer>
        <UiButton size="lg" @click="creating = false">取消</UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="workspace-create-form"
          :disabled="newIconProcessing"
        >
          {{ newIconProcessing ? '正在处理图片…' : '创建工作空间' }}
        </UiButton>
      </template>
    </UiDialog>

    <UiDialog
      v-if="editingWorkspace"
      title="设置工作空间图标"
      :description="`为「${editingWorkspace.name}」上传一张方形图片作为图标。`"
      height="compact"
      @close="editingWorkspace = null"
    >
      <div class="workspace-icon-dialog">
        <WorkspaceIconPicker
          v-model:image="editingIconImage"
          v-model:processing="editingIconProcessing"
          :fallback-icon="editingWorkspace.icon"
          :fallback-tone="editingWorkspace.iconTone"
        />
      </div>
      <template #footer>
        <UiButton size="lg" @click="editingWorkspace = null">
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          :disabled="editingIconProcessing"
          @click="saveWorkspaceIcon"
        >
          {{ editingIconProcessing ? '正在处理图片…' : '保存图标' }}
        </UiButton>
      </template>
    </UiDialog>
  </div>
</template>

<style scoped>
.workspace-switcher {
  position: relative;
}

.workspace-switcher__trigger {
  display: flex;
  width: 160px;
  height: 32px;
  flex: 0 0 160px;
  align-items: center;
  gap: 6px;
  padding: 0 var(--space-1) 0 var(--space-2);
  color: var(--text);
  text-align: left;
  background: var(--control-bg);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  transition:
    background var(--duration-fast) var(--ease-apple),
    border-color var(--duration-fast) var(--ease-apple);
}

.workspace-switcher__trigger:hover,
.workspace-switcher__trigger--open {
  background: var(--canvas);
  border-color: var(--control-border);
}

.workspace-switcher__trigger :deep(.workspace-icon) {
  width: 20px;
  height: 20px;
  background: transparent;
  border: 0;
  border-radius: var(--radius-control);
}

.workspace-switcher__copy {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
}

.workspace-switcher__copy strong {
  width: 100%;
  overflow: hidden;
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-switcher__menu {
  position: absolute;
  top: calc(100% + var(--space-1));
  left: 0;
  z-index: 30;
  width: 252px;
  padding: var(--space-1);
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-radius);
  box-shadow: var(--dropdown-shadow);
  backdrop-filter: blur(24px) saturate(1.3);
  transform-origin: top left;
  animation: workspace-menu-in var(--duration-popover) var(--ease-apple) both;
}

.workspace-switcher__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-1) var(--space-2) var(--space-2);
  margin-bottom: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  border-bottom: 1px solid var(--line-soft);
}

.workspace-switcher__heading button {
  display: inline-flex;
  height: 22px;
  align-items: center;
  gap: 3px;
  padding: 0 var(--space-1);
  color: var(--accent);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-badge);
}

.workspace-switcher__heading button:hover {
  background: var(--accent-soft);
}

.workspace-switcher__option {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 26px;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid transparent;
  border-radius: var(--dropdown-item-radius);
}

.workspace-switcher__option:hover {
  background: var(--hover-bg);
}

.workspace-switcher__option--active {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--system-blue) 12%, transparent);
}

.workspace-switcher__option--active:hover {
  background: var(--accent-soft);
}

.workspace-switcher__option-main {
  display: grid;
  width: 100%;
  min-width: 0;
  height: 34px;
  grid-template-columns: 28px minmax(0, 1fr) 16px;
  align-items: center;
  gap: var(--space-2);
  padding: 0 4px;
  color: var(--text);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
}

.workspace-switcher__option-main > span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-switcher__option--active
  .workspace-switcher__option-main
  > svg {
  color: var(--accent);
}

.workspace-switcher__edit {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  opacity: 0;
  place-items: center;
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple),
    color var(--duration-fast) var(--ease-apple);
}

.workspace-switcher__option:hover .workspace-switcher__edit,
.workspace-switcher__option:focus-within .workspace-switcher__edit,
.workspace-switcher__option--active .workspace-switcher__edit {
  opacity: 1;
}

.workspace-switcher__edit:hover {
  color: var(--accent);
  background: var(--canvas-raised);
}

.workspace-create,
.workspace-icon-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5);
}

.workspace-create > p {
  margin: calc(-1 * var(--space-1)) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@keyframes workspace-menu-in {
  from {
    opacity: 0;
    transform: translateY(-3px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 760px) {
  .workspace-switcher__copy {
    display: none;
  }

  .workspace-switcher__trigger {
    width: 32px;
    flex-basis: 32px;
    padding: 0;
    justify-content: center;
  }

  .workspace-switcher__trigger > svg {
    display: none;
  }

  .workspace-switcher__menu {
    left: 0;
    right: auto;
    width: min(84vw, 260px);
  }

  .workspace-switcher__edit {
    opacity: 0.72;
  }

  .workspace-create,
  .workspace-icon-dialog {
    padding: 0 var(--space-4);
  }
}
</style>
