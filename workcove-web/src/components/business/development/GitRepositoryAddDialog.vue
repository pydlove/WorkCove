<script setup lang="ts">
import {
  FolderOpen,
  GitBranch,
} from '@lucide/vue'
import {
  computed,
  ref,
  watch,
} from 'vue'

import { chooseLocalDirectory } from '@/api/development'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useGitRepositoryStore } from '@/stores/useGitRepositoryStore'
import { useWorkDirectoryStore } from '@/stores/useWorkDirectoryStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { GitRepository } from '@/types/gitRepository'

type AddMode = 'remote' | 'workDirectory' | 'folder'

const props = defineProps<{
  categories: string[]
  defaultCategory: string
}>()

const emit = defineEmits<{
  close: []
  added: [repository: GitRepository]
  notify: [message: string]
}>()

const auth = useAuthStore()
const repositories = useGitRepositoryStore()
const workDirectories = useWorkDirectoryStore()
const workspaces = useWorkspaceStore()
const mode = ref<AddMode>('remote')
const remoteUrl = ref('')
const remoteName = ref('')
const selectedDirectoryPath = ref('')
const localPath = ref('')
const category = ref(props.defaultCategory)
const saving = ref(false)
const choosingDirectory = ref(false)
const errorMessage = ref('')

const modeItems: Array<{ value: AddMode; label: string }> = [
  { value: 'remote', label: '手动添加' },
  { value: 'workDirectory', label: '选择工作目录' },
  { value: 'folder', label: '选择文件夹' },
]

const existingPaths = computed(
  () =>
    new Set(
      repositories.repositories
        .filter(
          (repository) =>
            repository.workspaceId === workspaces.activeWorkspaceId &&
            repository.source === 'local',
        )
        .map((repository) => repository.path),
    ),
)

const availableDirectories = computed(() =>
  workDirectories.directories.filter(
    (directory) =>
      directory.workspaceId === workspaces.activeWorkspaceId &&
      !existingPaths.value.has(directory.path),
  ),
)
const workDirectoryOptions = computed(() =>
  availableDirectories.value.map((directory) => ({
    value: directory.path,
    label: directory.name,
    description: directory.path,
  })),
)
const categoryOptions = computed(() =>
  (props.categories.length > 0
    ? props.categories
    : [props.defaultCategory]
  ).map((value) => ({ value, label: value })),
)

watch(
  () => auth.email,
  (email) => workDirectories.load(email),
  { immediate: true },
)

watch(
  mode,
  (value) => {
    errorMessage.value = ''
    if (
      value === 'workDirectory' &&
      !selectedDirectoryPath.value &&
      availableDirectories.value.length > 0
    ) {
      selectedDirectoryPath.value = availableDirectories.value[0]!.path
    }
  },
)

watch(availableDirectories, (items) => {
  if (
    selectedDirectoryPath.value &&
    !items.some(
      (directory) => directory.path === selectedDirectoryPath.value,
    )
  ) {
    selectedDirectoryPath.value = items[0]?.path ?? ''
  }
})

async function chooseFolder() {
  choosingDirectory.value = true
  errorMessage.value = ''
  try {
    const path = await chooseLocalDirectory()
    if (path) localPath.value = path
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法打开目录选择器'
  } finally {
    choosingDirectory.value = false
  }
}

async function submit() {
  if (saving.value) return
  errorMessage.value = ''
  saving.value = true
  try {
    let repository: GitRepository
    if (mode.value === 'remote') {
      repository = repositories.addRemoteRepository(
        workspaces.activeWorkspaceId,
        remoteUrl.value,
        remoteName.value,
        category.value,
      )
    } else {
      const path =
        mode.value === 'workDirectory'
          ? selectedDirectoryPath.value
          : localPath.value
      if (!path) {
        throw new Error(
          mode.value === 'workDirectory'
            ? '请选择一个工作目录'
            : '请选择本地 Git 仓库目录',
        )
      }
      repository = await repositories.addRepository(
        workspaces.activeWorkspaceId,
        path,
        category.value,
      )
    }
    emit('added', repository)
    emit(
      'notify',
      repository.source === 'remote'
        ? `已添加远端 Git 仓库「${repository.name}」`
        : `已添加 Git 仓库「${repository.name}」`,
    )
    emit('close')
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法添加 Git 仓库'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UiDialog
    title="添加 Git 仓库"
    description="支持未克隆的远端仓库、已登记工作目录或任意本地文件夹。"
    initial-focus="#git-repository-url"
    :suspended="saving"
    @close="emit('close')"
  >
    <form
      id="git-repository-add-form"
      class="git-repository-add"
      @submit.prevent="submit"
    >
      <UiSegmentedControl
        :model-value="mode"
        :items="modeItems"
        aria-label="Git 仓库添加方式"
        @update:model-value="mode = $event as AddMode"
      />

      <template v-if="mode === 'remote'">
        <UiFormField
          label="仓库地址"
          control-id="git-repository-url"
          hint="支持 HTTPS、SSH 或 git@host:path 格式"
          layout="dialog"
          required
        >
          <UiInput
            id="git-repository-url"
            v-model="remoteUrl"
            size="lg"
            maxlength="2048"
            placeholder="https://github.com/org/repository.git"
            spellcheck="false"
          />
        </UiFormField>
        <UiFormField
          label="名称"
          control-id="git-repository-name"
          hint="可选，留空时根据仓库地址生成"
          layout="dialog"
        >
          <UiInput
            id="git-repository-name"
            v-model="remoteName"
            size="lg"
            maxlength="80"
            placeholder="例如：核心服务"
          />
        </UiFormField>
        <p class="git-repository-add__note">
          <GitBranch :size="14" />
          远端仓库不会读取本地分支状态；克隆后可通过“选择文件夹”补全。
        </p>
      </template>

      <UiFormField
        v-else-if="mode === 'workDirectory'"
        label="工作目录"
        hint="从当前工作空间已登记的工作目录中选择"
        layout="dialog"
        required
      >
        <UiSelect
          v-model="selectedDirectoryPath"
          :options="workDirectoryOptions"
          size="lg"
          :menu-height="220"
          placeholder="请选择工作目录"
          aria-label="选择工作目录作为 Git 仓库"
          :disabled="workDirectoryOptions.length === 0"
        />
        <small
          v-if="workDirectoryOptions.length === 0"
          class="git-repository-add__hint"
        >
          当前工作空间没有可用的工作目录，或目录已经添加为 Git 仓库。
        </small>
      </UiFormField>

      <UiFormField
        v-else
        label="本地目录"
        hint="适用于尚未登记为工作目录的本地 Git 仓库"
        layout="dialog"
        required
      >
        <div class="git-repository-folder">
          <UiInput
            :model-value="localPath"
            size="lg"
            readonly
            placeholder="请选择 Git 仓库文件夹"
            aria-label="本地 Git 仓库目录"
          />
          <UiButton
            size="lg"
            :disabled="choosingDirectory"
            @click="chooseFolder"
          >
            <FolderOpen :size="14" />
            {{ choosingDirectory ? '选择中…' : '浏览本地文件夹' }}
          </UiButton>
        </div>
      </UiFormField>

      <UiFormField label="分类" layout="dialog">
        <UiSelect
          v-model="category"
          :options="categoryOptions"
          size="lg"
          aria-label="Git 仓库分类"
        />
      </UiFormField>

      <p v-if="errorMessage" class="git-repository-add__error" role="alert">
        {{ errorMessage }}
      </p>
    </form>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="saving"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="git-repository-add-form"
        :disabled="saving"
      >
        {{ saving ? '添加中…' : '添加仓库' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.git-repository-add {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.git-repository-add__note {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.git-repository-add__note svg {
  flex: 0 0 auto;
  color: var(--accent);
}

.git-repository-add__hint {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.git-repository-folder {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.git-repository-folder :deep(.ui-input) {
  min-width: 0;
  flex: 1;
}

.git-repository-add__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .git-repository-add {
    padding: 0 var(--space-4) var(--space-3);
  }

  .git-repository-folder {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
