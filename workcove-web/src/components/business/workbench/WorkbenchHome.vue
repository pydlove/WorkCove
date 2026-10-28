<script setup lang="ts">
import {
  AppWindow,
  ArrowUpRight,
  GitBranch,
  Globe2,
  Plus,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import ShortcutDialog from '@/components/business/workbench/ShortcutDialog.vue'
import ShortcutTile from '@/components/business/workbench/ShortcutTile.vue'
import WorkbenchTokenUsage from '@/components/business/workbench/WorkbenchTokenUsage.vue'
import WorkbenchTimePanel from '@/components/business/workbench/WorkbenchTimePanel.vue'
import UiButton from '@/components/ui/UiButton.vue'
import { openHostApplication } from '@/api/hostApplications'
import {
  shortcutIconUrls,
  sortApplicationsByUsage,
  sortWebsitesByUsage,
  websiteHostname,
} from '@/services/localShortcuts'
import { useAuthStore } from '@/stores/useAuthStore'
import { useGitRepositoryStore } from '@/stores/useGitRepositoryStore'
import { useShortcutStore } from '@/stores/useShortcutStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { GitRepository } from '@/types/gitRepository'
import type {
  ApplicationShortcut,
  ShortcutInput,
  ShortcutMode,
  WebsiteShortcut,
} from '@/types/shortcut'
import { openWebsiteUrl } from '@/utils/openWebsite'

const emit = defineEmits<{
  notify: [message: string]
  openCalendar: [date: string]
  openGitRepositories: []
}>()

const session = useAuthStore()
const shortcuts = useShortcutStore()
const gitRepositories = useGitRepositoryStore()
const workspaces = useWorkspaceStore()
const dialogMode = ref<ShortcutMode | null>(null)
const editingShortcut = ref<
  WebsiteShortcut | ApplicationShortcut | null
>(null)

const topWebsites = computed(() =>
  sortWebsitesByUsage(
    shortcuts.websites.filter(
      (website) =>
        website.workspaceId === workspaces.activeWorkspaceId,
    ),
  ).slice(0, 10),
)
const topApplications = computed(() =>
  sortApplicationsByUsage(
    shortcuts.applications.filter(
      (application) =>
        application.workspaceId === workspaces.activeWorkspaceId,
    ),
  ).slice(0, 5),
)
const websiteCount = computed(() => topWebsites.value.length)
const websiteCategories = computed(
  () =>
    shortcuts.categoriesByWorkspace[workspaces.activeWorkspaceId] ?? [],
)
const applicationCount = computed(() => topApplications.value.length)
const topGitRepositories = computed(() =>
  gitRepositories.repositories
    .filter(
      (repository) =>
        repository.workspaceId === workspaces.activeWorkspaceId,
    )
    .slice()
    .sort((left, right) =>
      right.updatedAt.localeCompare(left.updatedAt),
    )
    .slice(0, 6),
)
const gitRepositoryCount = computed(
  () => topGitRepositories.value.length,
)

watch(
  () => session.email,
  (email) => {
    shortcuts.load(email)
    gitRepositories.load(email)
    workspaces.load(email)
  },
  { immediate: true },
)

function addWebsite() {
  editingShortcut.value = null
  dialogMode.value = 'website'
}

function addApplication() {
  editingShortcut.value = null
  dialogMode.value = 'application'
}

function editWebsite(website: WebsiteShortcut) {
  editingShortcut.value = website
  dialogMode.value = 'website'
}

function editApplication(application: ApplicationShortcut) {
  editingShortcut.value = application
  dialogMode.value = 'application'
}

async function openWebsite(website: WebsiteShortcut) {
  const error = await openWebsiteUrl(website.url)
  if (error) {
    emit('notify', `无法打开网址：${error}`)
    return
  }
  shortcuts.recordWebsiteClick(website.id)
}

async function openApplication(application: ApplicationShortcut) {
  const error = await openHostApplication(application.path)
  if (error) {
    emit('notify', `无法启动：${error}`)
    return
  }
  shortcuts.recordApplicationOpen(application.id)
}

function closeDialog() {
  dialogMode.value = null
  editingShortcut.value = null
}

function saveShortcut(input: ShortcutInput) {
  shortcuts.saveShortcut({
    ...input,
    workspaceId: workspaces.activeWorkspaceId,
  })
  emit('notify', input.id ? '快捷方式已更新' : '快捷方式已添加')
  closeDialog()
}

function gitRepositoryDetail(repository: GitRepository) {
  const status = gitRepositories.statuses[repository.id]
  return [
    repository.category,
    status?.branch
      ? `分支 ${status.branch}`
      : repository.source === 'remote'
        ? '远程仓库'
        : repository.path,
  ]
    .filter(Boolean)
    .join(' · ')
}

function removeShortcut(id: string) {
  shortcuts.removeShortcut(id)
  emit('notify', '快捷方式已移除')
  closeDialog()
}
</script>

<template>
  <section class="workbench-home">
    <WorkbenchTimePanel
      @notify="emit('notify', $event)"
      @open-calendar="emit('openCalendar', $event)"
    />
    <WorkbenchTokenUsage />

    <div class="shortcut-layout">
      <section
        class="shortcut-section shortcut-section--websites"
        aria-labelledby="website-section-title"
      >
        <header class="shortcut-section__header">
          <span class="shortcut-section__icon">
            <Globe2 :size="17" />
          </span>
          <div>
            <h2 id="website-section-title">常用网址</h2>
          </div>
          <div class="shortcut-section__actions">
            <span class="shortcut-section__count">{{ websiteCount }}</span>
            <button
              class="shortcut-section__add"
              type="button"
              aria-label="添加常用网址"
              title="添加常用网址"
              @click="addWebsite"
            >
              <Plus :size="15" />
            </button>
          </div>
        </header>

        <div v-if="topWebsites.length" class="shortcut-grid">
          <ShortcutTile
            v-for="website in topWebsites"
            :key="website.id"
            :name="website.name"
            :detail="websiteHostname(website.url)"
            :icon-urls="shortcutIconUrls(website)"
            mode="website"
            @open="openWebsite(website)"
            @edit="editWebsite(website)"
          />
        </div>
        <div v-else class="shortcut-empty">
          <span class="shortcut-empty__icon">
            <Globe2 :size="20" />
          </span>
          <span class="shortcut-empty__copy">
            <strong>暂无常用网址</strong>
          </span>
          <UiButton size="sm" @click="addWebsite">
            <Plus :size="13" />
            添加网址
          </UiButton>
        </div>
      </section>

      <section
        class="shortcut-section shortcut-section--applications"
        aria-labelledby="application-section-title"
      >
        <header class="shortcut-section__header">
          <span class="shortcut-section__icon shortcut-section__icon--app">
            <AppWindow :size="17" />
          </span>
          <div>
            <h2 id="application-section-title">常用 App</h2>
          </div>
          <div class="shortcut-section__actions">
            <span class="shortcut-section__count">{{ applicationCount }}</span>
            <button
              class="shortcut-section__add"
              type="button"
              aria-label="添加本地软件"
              title="添加本地软件"
              @click="addApplication"
            >
              <Plus :size="15" />
            </button>
          </div>
        </header>

        <div
          v-if="topApplications.length"
          class="shortcut-grid shortcut-grid--applications"
        >
          <ShortcutTile
            v-for="application in topApplications"
            :key="application.id"
            :name="application.name"
            :detail="application.path"
            :icon-urls="shortcutIconUrls(application)"
            mode="application"
            @open="openApplication(application)"
            @edit="editApplication(application)"
          />
        </div>
        <div v-else class="shortcut-empty">
          <span class="shortcut-empty__icon">
            <AppWindow :size="20" />
          </span>
          <span class="shortcut-empty__copy">
            <strong>暂无常用 App</strong>
          </span>
          <UiButton size="sm" @click="addApplication">
            <Plus :size="13" />
            添加应用
          </UiButton>
        </div>
      </section>

      <section
        class="shortcut-section shortcut-section--git"
        aria-labelledby="git-section-title"
      >
        <header class="shortcut-section__header">
          <span class="shortcut-section__icon">
            <GitBranch :size="17" />
          </span>
          <div>
            <h2 id="git-section-title">常用 Git</h2>
          </div>
          <div class="shortcut-section__actions">
            <span class="shortcut-section__count">
              {{ gitRepositoryCount }}
            </span>
            <button
              class="shortcut-section__add"
              type="button"
              aria-label="打开 Git 仓库"
              title="打开 Git 仓库"
              @click="emit('openGitRepositories')"
            >
              <ArrowUpRight :size="15" />
            </button>
          </div>
        </header>

        <div
          v-if="topGitRepositories.length"
          class="git-shortcut-grid"
        >
          <button
            v-for="repository in topGitRepositories"
            :key="repository.id"
            class="git-shortcut"
            type="button"
            :aria-label="`打开 Git 仓库 ${repository.name}`"
            @click="emit('openGitRepositories')"
          >
            <span class="git-shortcut__icon">
              <GitBranch :size="14" />
            </span>
            <span class="git-shortcut__copy">
              <strong>{{ repository.name }}</strong>
              <small :title="gitRepositoryDetail(repository)">
                {{ gitRepositoryDetail(repository) }}
              </small>
            </span>
          </button>
        </div>
        <div v-else class="shortcut-empty shortcut-empty--git">
          <span class="shortcut-empty__icon">
            <GitBranch :size="20" />
          </span>
          <span class="shortcut-empty__copy">
            <strong>暂无 Git 仓库</strong>
          </span>
          <UiButton size="sm" @click="emit('openGitRepositories')">
            <ArrowUpRight :size="13" />
            去添加
          </UiButton>
        </div>
      </section>
    </div>

    <ShortcutDialog
      v-if="dialogMode"
      :mode="dialogMode"
      :shortcut="editingShortcut"
      :categories="websiteCategories"
      @close="closeDialog"
      @save="saveShortcut"
      @remove="removeShortcut"
    />
  </section>
</template>

<style scoped>
.workbench-home {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  animation: workbench-enter var(--duration-window) var(--ease-apple) both;
}

.shortcut-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  gap: var(--space-3);
}

.shortcut-section--websites {
  grid-column: 1 / -1;
}

.shortcut-section {
  display: flex;
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  flex-direction: column;
  background: var(--canvas-raised);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.shortcut-section__header {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.shortcut-section__icon {
  display: grid;
  width: 32px;
  height: 32px;
  color: var(--icon);
  background: var(--surface-inset);
  box-shadow: inset 0 0 0 0.5px var(--asset-tile-border);
  border-radius: var(--radius-control);
  place-items: center;
}

.shortcut-section__icon--app {
  color: var(--icon);
  background: var(--surface-inset);
}

.shortcut-section__header h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.shortcut-section__count {
  display: grid;
  min-width: 22px;
  height: 22px;
  padding: 0 5px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
  place-items: center;
}

.shortcut-section__actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.shortcut-section__add {
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.shortcut-section__add:hover {
  color: var(--accent);
  background: var(--hover-bg);
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  gap: var(--space-1) var(--space-1);
  padding-top: var(--space-4);
}

.shortcut-grid--applications {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.git-shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: minmax(38px, auto);
  gap: var(--space-1) var(--space-2);
  padding-top: var(--space-3);
}

.git-shortcut {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  text-align: left;
  background: var(--surface-inset);
  border-radius: var(--radius-control);
  transition: background var(--duration-fast) var(--ease-apple);
}

.git-shortcut:hover {
  background: var(--hover-bg);
}

.git-shortcut:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
}

.git-shortcut__icon {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  color: var(--icon);
  background: var(--asset-tile-bg);
  border-radius: var(--radius-control);
  place-items: center;
}

.git-shortcut__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.git-shortcut__copy strong,
.git-shortcut__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-shortcut__copy strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  line-height: 1.35;
}

.git-shortcut__copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: 1.35;
}

.shortcut-empty {
  display: flex;
  min-height: 110px;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-3);
}

.shortcut-empty__icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  color: var(--icon);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.shortcut-empty__copy {
  min-width: 0;
}

.shortcut-empty__copy strong {
  color: var(--text-muted);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.shortcut-empty--git {
  min-height: 148px;
}

@media (max-width: 1100px) {
  .shortcut-layout {
    grid-template-columns: 1fr;
  }

  .shortcut-section--websites {
    grid-column: auto;
  }
}

@media (max-width: 1050px) {
  .shortcut-grid {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }

  .shortcut-grid--applications {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .workbench-home {
    gap: var(--space-4);
  }

  .shortcut-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .shortcut-grid--applications {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .shortcut-section__header {
    grid-template-columns: 34px minmax(0, 1fr) auto;
  }

  .shortcut-empty {
    justify-content: flex-start;
  }
}

@media (max-width: 520px) {
  .shortcut-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .shortcut-grid--applications {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .git-shortcut-grid {
    gap: var(--space-1);
  }

  .git-shortcut {
    padding-inline: var(--space-1);
  }

  .shortcut-empty {
    min-height: 96px;
    gap: var(--space-2);
    padding: var(--space-3) 0;
  }
}

@keyframes workbench-enter {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
