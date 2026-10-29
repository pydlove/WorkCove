<script setup lang="ts">
import {
  ChevronLeft,
  Download,
  File,
  Folder,
  FolderPlus,
  RefreshCw,
  Trash2,
  Upload,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onMounted,
  ref,
} from 'vue'

import {
  createRemoteDirectory,
  deleteRemoteFile,
  listRemoteFiles,
  remoteDownloadUrl,
  uploadRemoteFile,
} from '@/api/sftp'
import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import { usePagination } from '@/composables/usePagination'
import type { ManagedServer } from '@/types/managedServer'
import type { RemoteFile } from '@/types/remoteFile'

const props = defineProps<{
  server: ManagedServer
}>()

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const MAX_TRANSFER_BYTES = 200 * 1024 * 1024

const currentPath = ref('/')
const files = ref<RemoteFile[]>([])
const loading = ref(false)
const uploading = ref(false)
const errorMessage = ref('')
const uploadInput = ref<HTMLInputElement | null>(null)
const newFolderInput = ref<InstanceType<typeof UiInput> | null>(null)
const creatingFolder = ref(false)
const newFolderName = ref('')
const pendingDelete = ref<RemoteFile | null>(null)
const deleting = ref(false)

const breadcrumbs = computed(() => {
  const parts = currentPath.value.split('/').filter(Boolean)
  return [
    { label: '根目录', path: '/' },
    ...parts.map((label, index) => ({
      label,
      path: `/${parts.slice(0, index + 1).join('/')}`,
    })),
  ]
})
const {
  currentPage: filePage,
  pageItems: pagedFiles,
  setPage: setFilePage,
} = usePagination(files, () => currentPath.value)

function errorText(error: unknown) {
  return error instanceof Error ? error.message : '远程文件操作失败'
}

async function loadFiles(path = currentPath.value) {
  loading.value = true
  errorMessage.value = ''
  try {
    files.value = await listRemoteFiles(
      props.server.id,
      props.server.workspaceId,
      path,
    )
    currentPath.value = path
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

function openDirectory(file: RemoteFile) {
  if (file.directory) void loadFiles(file.path)
}

function goUp() {
  if (currentPath.value === '/') return
  const parent = currentPath.value.replace(/\/[^/]+$/, '') || '/'
  void loadFiles(parent)
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function chooseUpload() {
  uploadInput.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files ?? [])
  input.value = ''
  if (selected.length === 0) return
  if (selected.some((file) => file.size > MAX_TRANSFER_BYTES)) {
    errorMessage.value = '单个文件不能超过 200MB'
    return
  }

  uploading.value = true
  errorMessage.value = ''
  try {
    for (const file of selected) {
      await uploadRemoteFile(
        props.server.id,
        props.server.workspaceId,
        currentPath.value,
        file,
      )
    }
    emit('notify', `已上传 ${selected.length} 个文件`)
    await loadFiles()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    uploading.value = false
  }
}

function startCreateFolder() {
  creatingFolder.value = true
  newFolderName.value = ''
  void nextTick(() => newFolderInput.value?.focus())
}

async function createFolder() {
  const name = newFolderName.value.trim()
  if (!name) return
  if (name.includes('/') || name.includes('\\')) {
    errorMessage.value = '目录名称不能包含 / 或 \\'
    return
  }
  const path =
    currentPath.value === '/'
      ? `/${name}`
      : `${currentPath.value.replace(/\/+$/, '')}/${name}`
  try {
    await createRemoteDirectory(
      props.server.id,
      props.server.workspaceId,
      path,
    )
    creatingFolder.value = false
    newFolderName.value = ''
    emit('notify', `已创建目录 ${name}`)
    await loadFiles()
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

function download(file: RemoteFile) {
  if (file.size > MAX_TRANSFER_BYTES) {
    errorMessage.value = '文件超过 200MB，暂不支持下载'
    return
  }
  const link = document.createElement('a')
  link.href = remoteDownloadUrl(
    props.server.id,
    props.server.workspaceId,
    file.path,
  )
  link.download = file.name
  document.body.append(link)
  link.click()
  link.remove()
}

async function confirmDelete() {
  const file = pendingDelete.value
  if (!file) return
  deleting.value = true
  errorMessage.value = ''
  try {
    await deleteRemoteFile(
      props.server.id,
      props.server.workspaceId,
      file.path,
      file.directory,
    )
    pendingDelete.value = null
    emit('notify', `已删除 ${file.name}`)
    await loadFiles()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    deleting.value = false
  }
}

onMounted(() => loadFiles())
</script>

<template>
  <section class="file-manager" aria-label="SFTP 文件管理">
    <header class="file-manager__header">
      <div class="file-manager__heading">
        <span class="file-manager__icon">
          <Folder :size="17" />
        </span>
        <div>
          <strong>{{ server.name }} · SFTP</strong>
          <small>{{ server.username }}@{{ server.host }}:{{ server.port }}</small>
        </div>
      </div>
      <div class="file-manager__header-actions">
        <UiButton
          size="sm"
          :loading="uploading"
          :disabled="uploading"
          @click="chooseUpload"
        >
          <Upload v-if="!uploading" :size="14" />
          {{ uploading ? '上传中…' : '上传' }}
        </UiButton>
        <UiButton size="sm" @click="startCreateFolder">
          <FolderPlus :size="14" />
          新建目录
        </UiButton>
        <button
          class="file-manager__close"
          type="button"
          aria-label="关闭文件管理"
          @click="emit('close')"
        >
          <X :size="15" />
        </button>
      </div>
      <input
        ref="uploadInput"
        class="file-manager__upload-input"
        type="file"
        multiple
        @change="handleUpload"
      />
    </header>

    <div class="file-manager__toolbar">
      <button
        class="file-manager__nav-button"
        type="button"
        aria-label="返回上一级"
        :disabled="currentPath === '/'"
        @click="goUp"
      >
        <ChevronLeft :size="15" />
      </button>
      <nav class="file-manager__breadcrumbs" aria-label="远程路径">
        <button
          v-for="crumb in breadcrumbs"
          :key="crumb.path"
          type="button"
          @click="loadFiles(crumb.path)"
        >
          {{ crumb.label }}
        </button>
      </nav>
      <button
        class="file-manager__nav-button"
        type="button"
        aria-label="刷新"
        :disabled="loading"
        @click="loadFiles()"
      >
        <UiFidgetSpinner v-if="loading" :size="14" />
        <RefreshCw v-else :size="14" />
      </button>
    </div>

    <form
      v-if="creatingFolder"
      class="file-manager__create-folder"
      @submit.prevent="createFolder"
    >
      <UiInput
        ref="newFolderInput"
        v-model="newFolderName"
        size="sm"
        maxlength="128"
        placeholder="新目录名称"
        aria-label="新目录名称"
      />
      <UiButton size="sm" variant="primary" type="submit">创建</UiButton>
      <UiButton size="sm" @click="creatingFolder = false">取消</UiButton>
    </form>

    <p v-if="errorMessage" class="file-manager__error" role="alert">
      {{ errorMessage }}
    </p>

    <div class="file-manager__list">
      <div class="file-manager__columns">
        <span>名称</span>
        <span>大小</span>
        <span>修改时间</span>
        <span />
      </div>
      <div v-if="loading" class="file-manager__empty">
        <span class="file-manager__loading">
          <UiFidgetSpinner :size="18" />
          正在读取远程目录…
        </span>
      </div>
      <div v-else-if="files.length === 0" class="file-manager__empty">
        这个目录是空的
      </div>
      <div v-else class="file-manager__rows">
        <article
          v-for="file in pagedFiles"
          :key="file.path"
          class="file-row"
          :class="{ 'file-row--directory': file.directory }"
        >
          <button
            class="file-row__name"
            type="button"
            :aria-label="file.directory ? `进入 ${file.name}` : file.name"
            @click="openDirectory(file)"
          >
            <Folder v-if="file.directory" :size="17" />
            <File v-else :size="16" />
            <span>{{ file.name }}</span>
          </button>
          <span class="file-row__size">
            {{ file.directory ? '目录' : formatSize(file.size) }}
          </span>
          <time :datetime="file.modifiedAt">
            {{ formatDate(file.modifiedAt) }}
          </time>
          <div class="file-row__actions">
            <button
              v-if="!file.directory"
              type="button"
              :aria-label="`下载 ${file.name}`"
              title="下载"
              @click="download(file)"
            >
              <Download :size="14" />
            </button>
            <button
              type="button"
              :aria-label="`删除 ${file.name}`"
              title="删除"
              @click="pendingDelete = file"
            >
              <Trash2 :size="14" />
            </button>
          </div>
        </article>
      </div>

      <UiPagination
        :page="filePage"
        :total="files.length"
        label="远程文件分页"
        @update:page="setFilePage"
      />
    </div>

    <UiConfirmDialog
      v-if="pendingDelete"
      title="删除远程文件？"
      :description="`${pendingDelete.path}，此操作不可撤销。`"
      confirm-label="删除"
      :busy="deleting"
      @close="pendingDelete = null"
      @confirm="confirmDelete"
    >
      <div class="file-remove-target">
        <Folder v-if="pendingDelete.directory" :size="17" />
        <File v-else :size="16" />
        <strong>{{ pendingDelete.name }}</strong>
      </div>
    </UiConfirmDialog>
  </section>
</template>

<style scoped>
.file-manager {
  display: flex;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow: hidden;
  flex-direction: column;
  background: var(--canvas-raised);
  border-radius: var(--radius-window);
  box-shadow: var(--shadow-panel);
}

.file-manager__header {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.file-manager__heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
}

.file-manager__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.file-manager__heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.file-manager__heading strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-manager__heading small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-manager__header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.file-manager__close {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.file-manager__close:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.file-manager__upload-input {
  display: none;
}

.file-manager__toolbar {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-3);
  background: var(--canvas);
  border-bottom: 1px solid var(--line-soft);
}

.file-manager__nav-button {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.file-manager__nav-button:hover:not(:disabled) {
  color: var(--text);
  background: var(--hover-bg);
}

.file-manager__breadcrumbs {
  display: flex;
  min-width: 0;
  overflow: hidden;
  align-items: center;
  flex: 1;
}

.file-manager__breadcrumbs button {
  overflow: hidden;
  padding: 2px 5px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border-radius: var(--radius-badge);
}

.file-manager__breadcrumbs button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.file-manager__breadcrumbs button + button::before {
  margin-right: 5px;
  color: var(--text-subtle);
  content: "/";
}

.file-manager__create-folder {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.file-manager__error {
  padding: var(--space-2) var(--space-4);
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
}

.file-manager__list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow-y: auto;
}

.file-manager__columns,
.file-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px 112px 58px;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
}

.file-manager__columns {
  min-height: 28px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  border-bottom: 1px solid var(--line-soft);
}

.file-row {
  min-height: 38px;
  border-bottom: 1px solid var(--line-soft);
}

.file-row:hover {
  background: var(--hover-bg);
}

.file-row__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
}

.file-row__name svg {
  flex: 0 0 auto;
  color: var(--text-subtle);
}

.file-row--directory .file-row__name svg {
  color: var(--system-blue);
}

.file-row__name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-row__size,
.file-row time {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.file-row__actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}

.file-row__actions button {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.file-row__actions button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.file-row__actions button:last-child:hover {
  color: var(--system-red);
}

.file-manager__empty {
  display: grid;
  min-height: 300px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  place-items: center;
}

.file-manager__loading {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.file-remove-target {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
}

.file-remove-target strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .file-manager__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .file-manager__header-actions {
    width: 100%;
  }

  .file-manager__close {
    margin-left: auto;
  }

  .file-manager__columns,
  .file-row {
    grid-template-columns: minmax(0, 1fr) 58px;
  }

  .file-manager__columns span:nth-child(2),
  .file-manager__columns span:nth-child(3),
  .file-row__size,
  .file-row time {
    display: none;
  }

  .file-manager__columns span:last-child {
    display: none;
  }
}
</style>
