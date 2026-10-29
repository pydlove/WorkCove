<script setup lang="ts">
import {
  FlaskConical,
  KeyRound,
  Pencil,
  Plus,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import TestEnvironmentDialog from '@/components/business/development/TestEnvironmentDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import { useTestEnvironmentStore } from '@/stores/useTestEnvironmentStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { TestEnvironment } from '@/types/testEnvironment'

const emit = defineEmits<{
  notify: [message: string]
}>()

const workspaces = useWorkspaceStore()
const environments = useTestEnvironmentStore()
const searchQuery = ref('')
const dialogOpen = ref(false)
const editingEnvironment = ref<TestEnvironment | null>(null)
const deleteTarget = ref<TestEnvironment | null>(null)
const deleting = ref(false)
const loadError = ref('')

const filteredEnvironments = computed(() => {
  const query = searchQuery.value
    .trim()
    .toLocaleLowerCase('zh-CN')
  if (!query) return environments.environments
  return environments.environments.filter((environment) =>
    [
      environment.name,
      environment.url,
      environment.username,
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})

watch(
  () => workspaces.activeWorkspaceId,
  (workspaceId) => {
    dialogOpen.value = false
    editingEnvironment.value = null
    deleteTarget.value = null
    searchQuery.value = ''
    void loadEnvironments(workspaceId, true)
  },
)

onMounted(() => {
  void loadEnvironments(workspaces.activeWorkspaceId, true)
})

async function loadEnvironments(
  workspaceId: string,
  force = false,
) {
  loadError.value = ''
  try {
    await environments.load(workspaceId, force)
  } catch (error) {
    loadError.value =
      error instanceof Error
        ? error.message
        : '测试环境加载失败'
  }
}

function addEnvironment() {
  editingEnvironment.value = null
  dialogOpen.value = true
}

function editEnvironment(environment: TestEnvironment) {
  editingEnvironment.value = environment
  dialogOpen.value = true
}

function environmentSaved(environment: TestEnvironment) {
  const wasEditing = Boolean(editingEnvironment.value)
  dialogOpen.value = false
  editingEnvironment.value = null
  emit(
    'notify',
    wasEditing
      ? `测试环境「${environment.name}」已更新`
      : `测试环境「${environment.name}」已添加`,
  )
}

function requestDelete(environment: TestEnvironment) {
  deleteTarget.value = environment
}

async function confirmDelete() {
  const environment = deleteTarget.value
  if (!environment || deleting.value) return
  deleting.value = true
  try {
    await environments.remove(
      environment.id,
      workspaces.activeWorkspaceId,
    )
    deleteTarget.value = null
    emit('notify', `测试环境「${environment.name}」已删除`)
  } catch (error) {
    emit(
      'notify',
      error instanceof Error
        ? error.message
        : '测试环境删除失败',
    )
  } finally {
    deleting.value = false
  }
}

function updatedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
</script>

<template>
  <section class="test-environment-page">
    <header class="test-environment-toolbar">
      <div>
        <strong>
          {{ environments.environments.length }} 个测试环境
        </strong>
        <span>供 Codex 测试选择，登录信息按工作空间隔离保存。</span>
      </div>
      <div class="test-environment-toolbar__actions">
        <UiSearchInput
          v-model="searchQuery"
          label="搜索测试环境"
          placeholder="搜索名称、链接或用户名"
        />
        <UiButton variant="primary" @click="addEnvironment">
          <Plus :size="14" />
          添加环境
        </UiButton>
      </div>
    </header>

    <p
      v-if="loadError"
      class="test-environment-page__error"
      role="alert"
    >
      {{ loadError }}
    </p>

    <div
      v-if="
        environments.loading &&
        environments.environments.length === 0
      "
      class="test-environment-state"
    >
      <FlaskConical :size="22" />
      <strong>正在加载测试环境</strong>
    </div>

    <div
      v-else-if="filteredEnvironments.length > 0"
      class="test-environment-list"
    >
      <article
        v-for="environment in filteredEnvironments"
        :key="environment.id"
        class="test-environment-row"
      >
        <span class="test-environment-row__icon">
          <FlaskConical :size="17" />
        </span>
        <div class="test-environment-row__copy">
          <strong>{{ environment.name }}</strong>
          <a
            :href="environment.url"
            :title="environment.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ environment.url }}
          </a>
        </div>
        <div class="test-environment-row__account">
          <span>{{ environment.username }}</span>
          <small>
            <KeyRound :size="12" />
            {{ environment.hasPassword ? '密码已保存' : '未保存密码' }}
          </small>
        </div>
        <time :datetime="environment.updatedAt">
          {{ updatedAt(environment.updatedAt) }}
        </time>
        <div class="test-environment-row__actions">
          <button
            type="button"
            :aria-label="`编辑 ${environment.name}`"
            :title="`编辑 ${environment.name}`"
            @click="editEnvironment(environment)"
          >
            <Pencil :size="14" />
          </button>
          <button
            class="test-environment-row__delete"
            type="button"
            :aria-label="`删除 ${environment.name}`"
            :title="`删除 ${environment.name}`"
            @click="requestDelete(environment)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="test-environment-state">
      <FlaskConical :size="22" />
      <strong>
        {{
          searchQuery
            ? '没有符合条件的测试环境'
            : '还没有测试环境'
        }}
      </strong>
      <span>
        {{
          searchQuery
            ? '换个关键词再试。'
            : '添加环境后，可直接从 Bug 清单发起 Codex 测试。'
        }}
      </span>
      <UiButton
        v-if="!searchQuery"
        variant="primary"
        @click="addEnvironment"
      >
        <Plus :size="14" />
        添加第一个环境
      </UiButton>
    </div>

    <TestEnvironmentDialog
      v-if="dialogOpen"
      :workspace-id="workspaces.activeWorkspaceId"
      :environment="editingEnvironment"
      @close="dialogOpen = false"
      @saved="environmentSaved"
    />

    <UiConfirmDialog
      v-if="deleteTarget"
      title="删除测试环境？"
      :description="`测试环境「${deleteTarget.name}」将不再出现在 Codex 测试选择中。`"
      :warning="
        deleteTarget
          ? `地址 ${deleteTarget.url} 和已保存的登录信息会一并删除。`
          : ''
      "
      confirm-label="删除环境"
      :busy="deleting"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.test-environment-page {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6) var(--space-6);
}

.test-environment-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.test-environment-toolbar > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-1);
}

.test-environment-toolbar strong {
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.test-environment-toolbar span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.test-environment-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.test-environment-page__error {
  padding: var(--space-2) var(--space-3);
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-red) 7%,
    transparent
  );
  border-radius: var(--radius-control);
}

.test-environment-list {
  overflow: hidden;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.test-environment-row {
  display: grid;
  min-width: 0;
  grid-template-columns:
    var(--control-lg)
    minmax(180px, 1.5fr)
    minmax(150px, 0.8fr)
    100px
    auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line-soft);
}

.test-environment-row:last-child {
  border-bottom: 0;
}

.test-environment-row:hover {
  background: var(--hover-bg);
}

.test-environment-row__icon {
  display: grid;
  width: var(--control-lg);
  height: var(--control-lg);
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.test-environment-row__copy,
.test-environment-row__account {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.test-environment-row__copy strong,
.test-environment-row__copy a,
.test-environment-row__account span,
.test-environment-row__account small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-environment-row__copy strong,
.test-environment-row__account span {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.test-environment-row__copy a,
.test-environment-row__account small,
.test-environment-row time {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  font-weight: var(--weight-regular);
  text-decoration: none;
}

.test-environment-row__copy a:hover {
  color: var(--accent);
  text-decoration: underline;
}

.test-environment-row__account small {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.test-environment-row time {
  text-align: right;
  white-space: nowrap;
}

.test-environment-row__actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.test-environment-row__actions button {
  display: grid;
  width: var(--control-md);
  height: var(--control-md);
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.test-environment-row__actions button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.test-environment-row__actions .test-environment-row__delete,
.test-environment-row__actions .test-environment-row__delete:hover {
  color: var(--system-red);
}

.test-environment-state {
  display: flex;
  min-height: 260px;
  flex: 1;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-subtle);
  text-align: center;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.test-environment-state strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.test-environment-state span {
  max-width: 420px;
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 900px) {
  .test-environment-page {
    padding: var(--space-4);
  }

  .test-environment-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .test-environment-toolbar__actions {
    width: 100%;
  }

  .test-environment-toolbar__actions :deep(.ui-search-input) {
    flex: 1;
    width: auto;
  }

  .test-environment-row {
    grid-template-columns: var(--control-lg) minmax(0, 1fr) auto;
  }

  .test-environment-row__account,
  .test-environment-row time {
    display: none;
  }
}

@media (max-width: 560px) {
  .test-environment-toolbar__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .test-environment-toolbar__actions :deep(.ui-search-input) {
    width: 100%;
  }
}
</style>
