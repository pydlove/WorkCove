<script setup lang="ts">
import {
  Bot,
  Check,
  CheckCircle2,
  Circle,
  Clock3,
  FileImage,
  FileText,
  ImagePlus,
  LoaderCircle,
  Paperclip,
  Pencil,
  Plus,
  Search,
  Trash2,
  X,
} from '@lucide/vue'
import {
  computed,
  reactive,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import { usePagination } from '@/composables/usePagination'
import { uploadCodexAttachment } from '@/api/codex'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  TaskExecutionStatus,
  TaskPriority,
  WorkTask,
  WorkTaskAttachment,
  WorkTaskInput,
  WorkTaskProjectOption,
} from '@/types/workbench'

type TaskFilter = 'all' | 'todo' | 'running' | 'completed'

const props = defineProps<{
  tasks: WorkTask[]
  projects: WorkTaskProjectOption[]
}>()

const emit = defineEmits<{
  create: [input: WorkTaskInput]
  update: [id: number, input: WorkTaskInput]
  delete: [id: number]
  toggle: [id: number]
  execute: [task: WorkTask]
  executeBatch: [tasks: WorkTask[]]
  openCodex: [sessionId: string]
}>()

const searchQuery = ref('')
const workspaceStore = useWorkspaceStore()
const filter = ref<TaskFilter>('all')
const editorOpen = ref(false)
const editingTask = ref<WorkTask | null>(null)
const deleteTarget = ref<WorkTask | null>(null)
const batchDialogOpen = ref(false)
const batchSearchQuery = ref('')
const batchSelectedIds = ref<number[]>([])
const batchError = ref('')
const resultTaskId = ref<number | null>(null)
const formError = ref('')
const attachmentInput = ref<HTMLInputElement | null>(null)
const attachments = ref<WorkTaskAttachment[]>([])
const attachmentUploading = ref(false)
const attachmentError = ref('')
const attachmentDragActive = ref(false)
const form = reactive<WorkTaskInput>({
  title: '',
  description: '',
  attachments: [],
  project: '收件箱',
  projectPath: '',
  priority: 'medium',
})
const MAX_ATTACHMENTS = 8
const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

const filterItems: Array<{ value: TaskFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'todo', label: '待执行' },
  { value: 'running', label: '运行中' },
  { value: 'completed', label: '已完成' },
]
const priorityItems: Array<{
  value: TaskPriority
  label: string
}> = [
  { value: 'high', label: '高优先级' },
  { value: 'medium', label: '普通' },
  { value: 'low', label: '低优先级' },
]
const projectOptions = computed(() => [
  {
    value: '',
    label: '收件箱',
    description: '不指定 Codex 项目目录',
  },
  ...props.projects.map((project) => ({
    value: project.path,
    label: project.name,
    description: project.path,
  })),
])

function executionStatus(task: WorkTask): TaskExecutionStatus {
  return task.executionStatus ?? (task.done ? 'completed' : 'todo')
}

function statusLabel(task: WorkTask) {
  const status = executionStatus(task)
  if (status === 'running') return '执行中'
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '执行失败'
  if (status === 'interrupted') return '已中断'
  return '待执行'
}

function statusIcon(task: WorkTask) {
  const status = executionStatus(task)
  if (status === 'running') return LoaderCircle
  if (status === 'completed') return CheckCircle2
  if (status === 'failed' || status === 'interrupted') return Clock3
  return Circle
}

function priorityLabel(priority: TaskPriority) {
  if (priority === 'high') return '高优先级'
  if (priority === 'low') return '低优先级'
  return '普通'
}

function formatAttachmentSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) {
    return `${Math.max(1, Math.round(size / 1024))} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function updatedLabel(task: WorkTask) {
  const value = task.updatedAt ?? task.lastExecutedAt
  if (value) {
    return new Intl.DateTimeFormat('zh-CN', {
      month: 'numeric',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value))
  }
  return task.time
}

function resultText(task: WorkTask) {
  const status = executionStatus(task)
  if (
    (status === 'failed' || status === 'interrupted') &&
    task.executionError?.trim()
  ) {
    return task.executionError.trim()
  }
  return (
    task.executionResult?.trim() ||
    task.executionError?.trim() ||
    (status === 'running'
      ? 'Codex 正在执行这个任务，结果会在完成后显示在这里。'
      : '暂无执行结果')
  )
}

function taskMatchesFilter(task: WorkTask, value: TaskFilter) {
  if (value === 'all') return true
  const status = executionStatus(task)
  if (value === 'todo') {
    return (
      status === 'todo' ||
      status === 'failed' ||
      status === 'interrupted'
    )
  }
  return status === value
}

const filteredTasks = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return props.tasks
    .filter((task) => taskMatchesFilter(task, filter.value))
    .filter((task) => {
      if (!query) return true
      return [
        task.title,
        task.description ?? '',
        task.project,
        task.executionResult ?? '',
        task.executionError ?? '',
      ]
        .join(' ')
        .toLocaleLowerCase('zh-CN')
        .includes(query)
    })
    .sort((left, right) => {
      const leftRunning = executionStatus(left) === 'running' ? 1 : 0
      const rightRunning = executionStatus(right) === 'running' ? 1 : 0
      if (leftRunning !== rightRunning) return rightRunning - leftRunning
      const leftTime = new Date(
        left.updatedAt ?? left.createdAt ?? 0,
      ).getTime()
      const rightTime = new Date(
        right.updatedAt ?? right.createdAt ?? 0,
      ).getTime()
      return rightTime - leftTime || right.id - left.id
    })
})
const batchTasks = computed(() => {
  const query = batchSearchQuery.value
    .trim()
    .toLocaleLowerCase('zh-CN')
  return props.tasks
    .filter((task) => executionStatus(task) !== 'running')
    .filter((task) => {
      if (!query) return true
      return [task.title, task.description ?? '', task.project]
        .join(' ')
        .toLocaleLowerCase('zh-CN')
        .includes(query)
    })
})
const selectedBatchTasks = computed(() =>
  props.tasks.filter((task) => batchSelectedIds.value.includes(task.id)),
)
const resultTask = computed(
  () =>
    props.tasks.find((task) => task.id === resultTaskId.value) ?? null,
)

const {
  currentPage,
  pageItems,
  setPage,
} = usePagination(
  filteredTasks,
  () => `${filter.value}\n${searchQuery.value}`,
)

watch(
  () => props.tasks.length,
  (length) => {
    if (length === 0) currentPage.value = 1
  },
)

function openAttachmentPicker() {
  if (attachmentUploading.value) return
  attachmentInput.value?.click()
}

function handleAttachmentInput(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  void addAttachments(files)
}

function handleEditorPaste(event: ClipboardEvent) {
  const clipboard = event.clipboardData
  if (!clipboard) return
  const files = Array.from(clipboard.files)
  if (files.length === 0) {
    for (const item of Array.from(clipboard.items)) {
      if (item.kind !== 'file') continue
      const file = item.getAsFile()
      if (file) files.push(file)
    }
  }
  if (files.length === 0) return
  event.preventDefault()
  void addAttachments(files)
}

function handleAttachmentDrop(event: DragEvent) {
  attachmentDragActive.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length > 0) void addAttachments(files)
}

async function addAttachments(files: File[]) {
  if (attachmentUploading.value) return
  attachmentError.value = ''
  const workspaceId = workspaceStore.activeWorkspaceId
  if (!workspaceId) {
    attachmentError.value = '工作空间尚未初始化'
    return
  }
  const available = Math.max(
    0,
    MAX_ATTACHMENTS - attachments.value.length,
  )
  if (available === 0) {
    attachmentError.value = `一次最多添加 ${MAX_ATTACHMENTS} 个附件`
    return
  }
  const accepted: File[] = []
  for (const file of files.slice(0, available)) {
    if (file.size <= 0) {
      attachmentError.value = `「${file.name}」是空文件`
      continue
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      attachmentError.value = `「${file.name}」超过 20MB`
      continue
    }
    if (
      attachments.value.some(
        (attachment) =>
          attachment.name === file.name &&
          attachment.size === file.size &&
          attachment.contentType === file.type,
      )
    ) {
      continue
    }
    accepted.push(file)
  }
  if (files.length > available) {
    attachmentError.value = `一次最多添加 ${MAX_ATTACHMENTS} 个附件`
  }
  if (accepted.length === 0) return

  attachmentUploading.value = true
  try {
    for (const file of accepted) {
      try {
        const uploaded = await uploadCodexAttachment(file, workspaceId)
        attachments.value = [
          ...attachments.value,
          {
            id: uploaded.id,
            name: uploaded.name,
            contentType: uploaded.contentType,
            size: uploaded.size,
            kind: uploaded.kind,
          },
        ]
      } catch (error) {
        attachmentError.value =
          error instanceof Error
            ? `「${file.name}」上传失败：${error.message}`
            : `「${file.name}」上传失败`
      }
    }
  } finally {
    attachmentUploading.value = false
  }
}

function removeAttachment(id: string) {
  attachments.value = attachments.value.filter(
    (attachment) => attachment.id !== id,
  )
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.attachments = []
  form.project = '收件箱'
  form.projectPath = ''
  form.priority = 'medium'
  formError.value = ''
  attachments.value = []
  attachmentError.value = ''
  attachmentDragActive.value = false
}

function openCreate() {
  editingTask.value = null
  resetForm()
  editorOpen.value = true
}

function openEdit(task: WorkTask) {
  editingTask.value = task
  form.title = task.title
  form.description = task.description ?? ''
  form.attachments = [...(task.attachments ?? [])]
  attachments.value = [...(task.attachments ?? [])]
  form.project = task.project || '收件箱'
  form.projectPath =
    task.projectPath ||
    props.projects.find((project) => project.name === task.project)?.path ||
    ''
  form.priority = task.priority
  formError.value = ''
  attachmentError.value = ''
  attachmentDragActive.value = false
  editorOpen.value = true
}

function closeEditor() {
  if (attachmentUploading.value) return
  editorOpen.value = false
  editingTask.value = null
  formError.value = ''
  attachmentError.value = ''
  attachmentDragActive.value = false
}

function submitForm() {
  const title = form.title.trim()
  if (!title) {
    formError.value = '请输入任务名称'
    return
  }
  const project = props.projects.find(
    (item) => item.path === form.projectPath,
  )
  const input: WorkTaskInput = {
    title: title.slice(0, 200),
    description: form.description.trim().slice(0, 20_000),
    attachments: [...attachments.value],
    project: (project?.name || form.project).trim().slice(0, 80) || '收件箱',
    projectPath: project?.path ?? '',
    priority: form.priority,
  }
  if (editingTask.value) {
    emit('update', editingTask.value.id, input)
  } else {
    emit('create', input)
  }
  closeEditor()
}

function requestDelete(task: WorkTask) {
  deleteTarget.value = task
}

function openBatchDialog() {
  batchSearchQuery.value = ''
  batchSelectedIds.value = []
  batchError.value = ''
  batchDialogOpen.value = true
}

function closeBatchDialog() {
  batchDialogOpen.value = false
  batchSelectedIds.value = []
  batchSearchQuery.value = ''
  batchError.value = ''
}

function toggleBatchTask(taskId: number, selected: boolean) {
  batchError.value = ''
  batchSelectedIds.value = selected
    ? [...new Set([...batchSelectedIds.value, taskId])]
    : batchSelectedIds.value.filter((id) => id !== taskId)
}

function submitBatch() {
  if (selectedBatchTasks.value.length === 0) return
  const projectKeys = new Set(
    selectedBatchTasks.value.map((task) => {
      const path =
        task.projectPath ||
        props.projects.find((project) => project.name === task.project)?.path
      return path || `unlinked:${task.project || '收件箱'}`
    }),
  )
  if (projectKeys.size > 1) {
    batchError.value = '所选任务来自不同项目，请按项目分批交给 Codex'
    return
  }
  emit('executeBatch', [...selectedBatchTasks.value])
  closeBatchDialog()
}

function confirmDelete() {
  const task = deleteTarget.value
  if (!task) return
  emit('delete', task.id)
  deleteTarget.value = null
}

function openResult(task: WorkTask) {
  resultTaskId.value = task.id
}

function hasExecutionResult(task: WorkTask) {
  const status = executionStatus(task)
  return (
    status === 'completed' ||
    status === 'failed' ||
    status === 'interrupted' ||
    Boolean(task.executionResult?.trim()) ||
    Boolean(task.executionError?.trim())
  )
}

function openTaskRow(task: WorkTask) {
  if (!hasExecutionResult(task)) return
  openResult(task)
}

function executeTask(task: WorkTask) {
  if (executionStatus(task) === 'running') {
    emit('openCodex', task.codexSessionId ?? '')
    return
  }
  emit('execute', task)
}

function executionActionLabel(task: WorkTask) {
  const status = executionStatus(task)
  if (status === 'running') return '查看执行'
  if (status === 'completed') return '重新执行'
  if (status === 'failed' || status === 'interrupted') return '重试执行'
  return '交给 Codex'
}
</script>

<template>
  <section class="task-library">
    <div class="task-library__toolbar">
      <UiSearchInput
        v-model="searchQuery"
        label="搜索任务"
        placeholder="搜索任务、项目或执行结果"
      />
      <UiSegmentedControl
        v-model="filter"
        :items="filterItems"
        aria-label="任务状态筛选"
      />
      <UiButton @click="openBatchDialog">
        <Bot :size="14" />
        批量交给 Codex
      </UiButton>
      <UiButton variant="primary" @click="openCreate">
        <Plus :size="14" />
        新增任务
      </UiButton>
    </div>

    <div v-if="pageItems.length" class="task-library__list">
      <article
        v-for="task in pageItems"
        :key="task.id"
        class="task-library__row"
        :class="{
          'task-library__row--done':
            executionStatus(task) === 'completed',
          'task-library__row--result':
            hasExecutionResult(task),
        }"
        :role="hasExecutionResult(task) ? 'button' : undefined"
        :tabindex="hasExecutionResult(task) ? 0 : undefined"
        :aria-label="
          hasExecutionResult(task)
            ? `查看任务“${task.title}”的执行结果`
            : undefined
        "
        @click="openTaskRow(task)"
        @keydown.enter.prevent="openTaskRow(task)"
        @keydown.space.prevent="openTaskRow(task)"
      >
        <button
          class="task-library__check"
          type="button"
          :aria-label="
            task.done
              ? `将“${task.title}”标记为未完成`
              : `完成“${task.title}”`
          "
          @click.stop="emit('toggle', task.id)"
          @keydown.stop
        >
          <Check
            v-if="task.done"
            :size="12"
            :stroke-width="2.6"
          />
        </button>

        <div class="task-library__copy">
          <header>
            <h3>{{ task.title }}</h3>
            <span
              class="task-library__status"
              :class="`task-library__status--${executionStatus(task)}`"
            >
              <component
                :is="statusIcon(task)"
                :size="12"
                :class="{
                  'task-library__spin':
                    executionStatus(task) === 'running',
                }"
              />
              {{ statusLabel(task) }}
            </span>
            <span
              class="task-library__priority"
              :class="`task-library__priority--${task.priority}`"
            >
              {{ priorityLabel(task.priority) }}
            </span>
          </header>
          <p
            v-if="task.description"
            class="task-library__description"
            :title="task.description"
          >
            {{ task.description }}
          </p>
          <div class="task-library__meta">
            <span>{{ task.project || '收件箱' }}</span>
            <span>更新于 {{ updatedLabel(task) }}</span>
            <span
              v-if="task.attachments?.length"
              class="task-library__attachment-count"
            >
              <Paperclip :size="11" />
              {{ task.attachments.length }} 个附件
            </span>
            <button
              class="task-library__result-toggle"
              type="button"
              aria-haspopup="dialog"
              @click.stop="openResult(task)"
              @keydown.stop
            >
              <FileText :size="12" />
              执行结果
            </button>
          </div>
        </div>

        <div
          class="task-library__actions"
          @click.stop
          @keydown.stop
        >
          <UiButton
            class="task-library__execute"
            size="sm"
            variant="primary"
            :disabled="executionStatus(task) === 'running' && !task.codexSessionId"
            @click="executeTask(task)"
          >
            <LoaderCircle
              v-if="executionStatus(task) === 'running'"
              class="task-library__spin"
              :size="13"
            />
            <Bot v-else :size="13" />
            {{ executionActionLabel(task) }}
          </UiButton>
          <button
            class="task-library__icon-action"
            type="button"
            :aria-label="`编辑任务 ${task.title}`"
            title="编辑任务"
            @click="openEdit(task)"
          >
            <Pencil :size="14" />
          </button>
          <button
            class="task-library__icon-action task-library__icon-action--danger"
            type="button"
            :aria-label="`删除任务 ${task.title}`"
            title="删除任务"
            @click="requestDelete(task)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="task-library__empty">
      <CheckCircle2 :size="22" />
      <strong>
        {{ searchQuery.trim() ? '没有匹配的任务' : '还没有任务' }}
      </strong>
      <p>
        {{
          searchQuery.trim()
            ? '换个关键词或调整状态筛选后再试。'
            : '新建任务后，可以选择交给 Codex 执行。'
        }}
      </p>
      <UiButton v-if="!searchQuery.trim()" @click="openCreate">
        <Plus :size="14" />
        新增第一个任务
      </UiButton>
    </div>

    <UiPagination
      :page="currentPage"
      :total="filteredTasks.length"
      label="任务分页"
      @update:page="setPage"
    />

    <UiDialog
      v-if="editorOpen"
      :title="editingTask ? '编辑任务' : '新增任务'"
      description="任务可以独立维护，也可以交给 Codex 执行并回填结果。"
      size="wide"
      height="tall"
      max-height="min(760px, calc(100dvh - 32px))"
      scroll-mode="body"
      initial-focus="#task-title"
      @close="closeEditor"
    >
      <form
        id="task-editor-form"
        class="task-editor"
        :class="{
          'task-editor--dragging': attachmentDragActive,
        }"
        @submit.prevent="submitForm"
        @paste="handleEditorPaste"
        @dragenter.prevent="attachmentDragActive = true"
        @dragover.prevent
        @dragleave="attachmentDragActive = false"
        @drop.prevent="handleAttachmentDrop"
      >
        <UiFormField
          label="任务名称"
          control-id="task-title"
          required
        >
          <UiInput
            id="task-title"
            v-model="form.title"
            maxlength="200"
            placeholder="例如：修复邮件同步失败"
          />
        </UiFormField>
        <UiFormField
          label="任务说明"
          control-id="task-description"
          hint="写清目标、约束和验收方式，可直接粘贴截图，也可以上传图片和文件。"
        >
          <UiTextarea
            id="task-description"
            v-model="form.description"
            :rows="12"
            maxlength="20000"
            placeholder="描述任务背景、需要完成的内容和验收标准"
          />
        </UiFormField>
        <section class="task-editor__attachments">
          <div class="task-editor__attachments-header">
            <span>
              <Paperclip :size="14" />
              附件
            </span>
            <small>
              {{ attachments.length }}/{{ MAX_ATTACHMENTS }}
            </small>
          </div>
          <div
            v-if="attachments.length"
            class="task-editor__attachment-list"
          >
            <div
              v-for="attachment in attachments"
              :key="attachment.id"
              class="task-editor__attachment"
            >
              <FileImage
                v-if="attachment.kind === 'image'"
                :size="15"
              />
              <FileText v-else :size="15" />
              <span :title="attachment.name">
                {{ attachment.name }}
              </span>
              <small>
                {{ formatAttachmentSize(attachment.size) }}
              </small>
              <button
                type="button"
                :aria-label="`移除附件 ${attachment.name}`"
                title="移除附件"
                @click="removeAttachment(attachment.id)"
              >
                <X :size="13" />
              </button>
            </div>
          </div>
          <button
            class="task-editor__upload"
            type="button"
            :disabled="
              attachmentUploading ||
              attachments.length >= MAX_ATTACHMENTS
            "
            @click="openAttachmentPicker"
          >
            <LoaderCircle
              v-if="attachmentUploading"
              class="task-library__spin"
              :size="15"
            />
            <ImagePlus v-else :size="15" />
            <span>
              {{
                attachmentUploading
                  ? '正在上传附件…'
                  : '上传图片或文件'
              }}
            </span>
            <small>按 Ctrl+V 也可以直接粘贴截图</small>
          </button>
          <input
            ref="attachmentInput"
            class="task-editor__file-input"
            type="file"
            multiple
            tabindex="-1"
            aria-hidden="true"
            @change="handleAttachmentInput"
          />
          <p
            v-if="attachmentError"
            class="task-editor__attachment-error"
            role="alert"
          >
            {{ attachmentError }}
          </p>
        </section>
        <div class="task-editor__row">
          <UiFormField
            label="所属项目"
            control-id="task-project"
            hint="执行任务时，Codex 会切换到对应项目目录"
          >
            <UiSelect
              id="task-project"
              v-model="form.projectPath"
              :options="projectOptions"
              placeholder="选择 Codex 项目"
              aria-label="所属 Codex 项目"
            />
          </UiFormField>
          <UiFormField label="优先级">
            <UiSelect
              v-model="form.priority"
              :options="priorityItems"
              aria-label="任务优先级"
            />
          </UiFormField>
        </div>
        <p v-if="formError" class="task-editor__error" role="alert">
          {{ formError }}
        </p>
      </form>

      <template #footer>
        <UiButton
          size="lg"
          :disabled="attachmentUploading"
          @click="closeEditor"
        >
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="task-editor-form"
          :disabled="attachmentUploading"
        >
          {{ editingTask ? '保存修改' : '创建任务' }}
        </UiButton>
      </template>
    </UiDialog>

    <UiConfirmDialog
      v-if="deleteTarget"
      title="删除这个任务？"
      :description="`「${deleteTarget.title}」及其执行结果将从当前账号中移除。`"
      warning="删除后无法恢复，但不会删除 Codex 会话记录。"
      confirm-label="删除任务"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />

    <UiDialog
      v-if="resultTask"
      title="执行结果"
      :description="resultTask.title"
      size="wide"
      height="tall"
      scroll-mode="body"
      @close="resultTaskId = null"
    >
      <div class="task-result-dialog">
        <div class="task-result-dialog__meta">
          <span>{{ resultTask.project || '收件箱' }}</span>
          <span
            class="task-library__status"
            :class="`task-library__status--${executionStatus(resultTask)}`"
          >
            <component
              :is="statusIcon(resultTask)"
              :size="12"
            />
            {{ statusLabel(resultTask) }}
          </span>
        </div>
        <pre>{{ resultText(resultTask) }}</pre>
      </div>
      <template #footer>
        <UiButton size="lg" @click="resultTaskId = null">
          关闭
        </UiButton>
      </template>
    </UiDialog>

    <UiDialog
      v-if="batchDialogOpen"
      title="批量交给 Codex"
      description="选择多项任务后，Codex 会按任务逐条执行并分别回填结果。"
      height="tall"
      scroll-mode="body"
      @close="closeBatchDialog"
    >
      <div class="task-batch">
        <label class="task-batch__search">
          <Search :size="14" aria-hidden="true" />
          <UiInput
            v-model="batchSearchQuery"
            type="search"
            aria-label="搜索可批量执行的任务"
            placeholder="搜索任务名称、说明或项目"
          />
        </label>
        <div v-if="batchTasks.length" class="task-batch__list">
          <div
            v-for="task in batchTasks"
            :key="task.id"
            class="task-batch__item"
            :class="{
              'task-batch__item--selected':
                batchSelectedIds.includes(task.id),
            }"
          >
            <UiCheckbox
              :model-value="batchSelectedIds.includes(task.id)"
              :label="`选择任务 ${task.title}`"
              @update:model-value="
                toggleBatchTask(task.id, $event)
              "
            />
            <span class="task-batch__copy">
              <strong>{{ task.title }}</strong>
              <small>
                {{ task.project || '收件箱' }}
                · {{ statusLabel(task) }}
                · {{ priorityLabel(task.priority) }}
              </small>
            </span>
          </div>
        </div>
        <div v-else class="task-batch__empty">
          {{
            batchSearchQuery.trim()
              ? '没有匹配的任务'
              : '暂无可批量执行的任务'
          }}
        </div>
        <p v-if="batchError" class="task-batch__error" role="alert">
          {{ batchError }}
        </p>
      </div>

      <template #footer>
        <span class="task-batch__count">
          已选择 {{ selectedBatchTasks.length }} 个任务
        </span>
        <UiButton size="lg" @click="closeBatchDialog">取消</UiButton>
        <UiButton
          variant="primary"
          size="lg"
          :disabled="selectedBatchTasks.length === 0"
          @click="submitBatch"
        >
          <Bot :size="14" />
          交给 Codex
        </UiButton>
      </template>
    </UiDialog>
  </section>
</template>

<style scoped>
.task-library {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.task-library__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
}

.task-library__toolbar > :deep(.ui-segmented) {
  margin-left: auto;
}

.task-batch {
  display: flex;
  padding: var(--space-2) var(--space-5) var(--space-5);
  flex-direction: column;
  gap: var(--space-3);
}

.task-batch__search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.task-batch__search > svg {
  flex: 0 0 auto;
  color: var(--icon);
}

.task-batch__search :deep(.ui-input) {
  flex: 1;
}

.task-batch__list {
  border-top: 1px solid var(--line-soft);
}

.task-batch__item {
  display: flex;
  min-height: 50px;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-1);
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
}

.task-batch__item:hover,
.task-batch__item--selected {
  background: var(--hover-bg);
}

.task-batch__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.task-batch__copy strong,
.task-batch__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-batch__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.task-batch__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-batch__empty {
  display: grid;
  min-height: 180px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  place-items: center;
}

.task-batch__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.task-batch__count {
  margin-right: auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-library__list {
  border-top: 1px solid var(--line-soft);
}

.task-library__row {
  display: grid;
  min-height: 64px;
  min-width: 0;
  grid-template-columns: 22px minmax(0, 1fr) 172px;
  align-items: center;
  gap: var(--space-3);
  padding: 7px var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.task-library__row:hover {
  background: var(--hover-bg);
}

.task-library__row--result {
  cursor: pointer;
}

.task-library__row--result:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.task-library__check {
  display: grid;
  width: 18px;
  height: 18px;
  margin-top: 0;
  padding: 0;
  color: var(--selection-label);
  background: transparent;
  border: 1.5px solid var(--control-border);
  border-radius: 50%;
  place-items: center;
}

.task-library__check:hover {
  background: var(--accent-soft);
  border-color: var(--accent);
}

.task-library__row--done .task-library__check {
  background: var(--accent);
  border-color: var(--accent);
}

.task-library__copy {
  min-width: 0;
}

.task-library__copy > header {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.task-library__copy h3 {
  overflow: hidden;
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: var(--line-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-library__row--done .task-library__copy h3 {
  color: var(--text-muted);
  text-decoration: line-through;
}

.task-library__status,
.task-library__priority {
  display: inline-flex;
  min-height: 18px;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 0 5px;
  font-size: var(--font-caption);
  border-radius: var(--radius-badge);
}

.task-library__status {
  color: var(--text-muted);
  background: var(--surface-inset);
}

.task-library__status--running {
  color: var(--system-blue);
  background: color-mix(
    in srgb,
    var(--system-blue) 10%,
    transparent
  );
}

.task-library__status--completed {
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 10%,
    transparent
  );
}

.task-library__status--failed,
.task-library__status--interrupted {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 9%,
    transparent
  );
}

.task-library__priority {
  color: var(--text-subtle);
  background: var(--category-chip-bg);
}

.task-library__priority--high {
  color: var(--system-red);
}

.task-library__priority--low {
  color: var(--system-blue);
}

.task-library__description {
  display: -webkit-box;
  overflow: hidden;
  margin: 2px 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: 16px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.task-library__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-library__meta span + span::before {
  margin-right: 8px;
  content: "·";
}

.task-library__attachment-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.task-library__result-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  margin-left: 2px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
}

.task-library__result-toggle:hover {
  color: var(--text);
}

.task-result-dialog {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.task-result-dialog__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-result-dialog pre {
  min-height: 260px;
  max-height: 56vh;
  margin: 0;
  overflow: auto;
  padding: var(--space-3);
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-caption);
  line-height: 18px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.task-library__actions {
  display: flex;
  width: 172px;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-1);
}

.task-library__execute {
  width: 108px;
}

.task-library__icon-action {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.task-library__icon-action:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.task-library__icon-action--danger,
.task-library__icon-action--danger:hover {
  color: var(--system-red);
}

.task-library__empty {
  display: flex;
  min-height: 260px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  text-align: center;
}

.task-library__empty > svg {
  color: var(--icon);
}

.task-library__empty strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.task-library__empty p {
  max-width: 360px;
  margin: 0 0 var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-editor {
  display: flex;
  padding: var(--space-2) var(--space-5) var(--space-4);
  flex-direction: column;
  gap: var(--space-4);
}

.task-editor :deep(.ui-textarea) {
  min-height: 260px;
  line-height: 1.65;
}

.task-editor__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: var(--space-3);
}

.task-editor__attachments {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.task-editor__attachments-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.task-editor__attachments-header > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.task-editor__attachments-header small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-editor__attachment-list {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.task-editor__attachment {
  display: flex;
  min-width: 0;
  max-width: min(300px, 100%);
  align-items: center;
  gap: 6px;
  padding: 5px 5px 5px 8px;
  color: var(--text-muted);
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.task-editor__attachment > svg {
  flex: 0 0 auto;
  color: var(--accent);
}

.task-editor__attachment > span {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-editor__attachment > small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-editor__attachment > button {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.task-editor__attachment > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.task-editor__upload {
  display: grid;
  width: 100%;
  min-height: 52px;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  color: var(--text-muted);
  text-align: left;
  background: var(--field-bg);
  border: 1px dashed var(--field-border);
  border-radius: var(--radius-control);
}

.task-editor__upload:hover:not(:disabled),
.task-editor--dragging .task-editor__upload {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.task-editor__upload:disabled {
  opacity: 0.55;
}

.task-editor__upload > span {
  color: var(--text);
  font-size: var(--font-body);
}

.task-editor__upload > small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.task-editor__file-input {
  display: none;
}

.task-editor__attachment-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.task-editor__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.task-library__spin {
  animation: task-library-spin 0.8s linear infinite;
}

@keyframes task-library-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 760px) {
  .task-library__toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .task-library__toolbar > :deep(.ui-segmented) {
    margin-left: 0;
  }

  .task-library__row {
    grid-template-columns: 22px minmax(0, 1fr);
  }

  .task-library__actions {
    width: auto;
    grid-column: 2;
    justify-content: flex-start;
  }

  .task-editor__row {
    grid-template-columns: 1fr;
  }

  .task-editor__upload {
    grid-template-columns: 20px minmax(0, 1fr);
  }

  .task-editor__upload > small {
    grid-column: 2;
  }
}

@media (max-width: 520px) {
  .task-library__copy > header {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .task-library__copy h3 {
    width: 100%;
    white-space: normal;
  }

  .task-library__actions {
    flex-wrap: wrap;
  }
}
</style>
