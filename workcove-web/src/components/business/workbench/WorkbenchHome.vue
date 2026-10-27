<script setup lang="ts">
import {
  AppWindow,
  ArrowUpRight,
  Globe2,
  Plus,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import ShortcutDialog from '@/components/business/workbench/ShortcutDialog.vue'
import ShortcutTile from '@/components/business/workbench/ShortcutTile.vue'
import WorkbenchTimePanel from '@/components/business/workbench/WorkbenchTimePanel.vue'
import {
  shortcutIconUrl,
  websiteHostname,
} from '@/services/localShortcuts'
import { useAuthStore } from '@/stores/useAuthStore'
import { useShortcutStore } from '@/stores/useShortcutStore'
import type {
  ApplicationShortcut,
  ShortcutInput,
  ShortcutMode,
  WebsiteShortcut,
} from '@/types/shortcut'

const emit = defineEmits<{
  notify: [message: string]
}>()

const session = useAuthStore()
const shortcuts = useShortcutStore()
const dialogMode = ref<ShortcutMode | null>(null)
const editingShortcut = ref<
  WebsiteShortcut | ApplicationShortcut | null
>(null)

const websiteCount = computed(() => shortcuts.websites.length)
const applicationCount = computed(() => shortcuts.applications.length)

watch(
  () => session.email,
  (email) => shortcuts.load(email),
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
  if (window.workCoveDesktop) {
    const error = await window.workCoveDesktop.openExternal(website.url)
    if (error) emit('notify', `无法打开网址：${error}`)
    return
  }

  const opened = window.open(website.url, '_blank', 'noopener,noreferrer')
  if (!opened) emit('notify', '浏览器阻止了新窗口，请检查弹窗权限')
}

async function openApplication(application: ApplicationShortcut) {
  if (!window.workCoveDesktop) {
    emit('notify', '本地软件需要在 WorkCove 桌面应用中启动')
    return
  }

  const error = await window.workCoveDesktop.openPath(application.path)
  if (error) emit('notify', `无法启动：${error}`)
}

function closeDialog() {
  dialogMode.value = null
  editingShortcut.value = null
}

function saveShortcut(input: ShortcutInput) {
  shortcuts.saveShortcut(input)
  emit('notify', input.id ? '快捷方式已更新' : '快捷方式已添加')
  closeDialog()
}

function removeShortcut(id: string) {
  shortcuts.removeShortcut(id)
  emit('notify', '快捷方式已移除')
  closeDialog()
}
</script>

<template>
  <section class="workbench-home">
    <WorkbenchTimePanel />

    <div class="shortcut-layout">
      <section class="shortcut-section" aria-labelledby="website-section-title">
        <header class="shortcut-section__header">
          <span class="shortcut-section__icon">
            <Globe2 :size="17" />
          </span>
          <div>
            <p>WEB SHORTCUTS</p>
            <h2 id="website-section-title">常用网址</h2>
          </div>
          <span class="shortcut-section__count">{{ websiteCount }}</span>
          <button
            class="shortcut-section__add"
            type="button"
            @click="addWebsite"
          >
            <Plus :size="14" />
            添加网址
          </button>
        </header>

        <div v-if="websiteCount > 0" class="shortcut-grid">
          <ShortcutTile
            v-for="website in shortcuts.websites"
            :key="website.id"
            :name="website.name"
            :detail="websiteHostname(website.url)"
            :icon-url="shortcutIconUrl(website)"
            mode="website"
            @open="openWebsite(website)"
            @edit="editWebsite(website)"
          />
          <button class="shortcut-add-tile" type="button" @click="addWebsite">
            <Plus :size="17" />
            <span>添加常用网址</span>
          </button>
        </div>

        <button
          v-else
          class="application-empty"
          type="button"
          @click="addWebsite"
        >
          <span class="application-empty__icon application-empty__icon--website">
            <Globe2 :size="22" />
          </span>
          <span>
            <strong>添加第一个常用网址</strong>
            <small>不内置任何网站，完全由你自己决定</small>
          </span>
          <ArrowUpRight :size="16" />
        </button>
      </section>

      <section
        class="shortcut-section"
        aria-labelledby="application-section-title"
      >
        <header class="shortcut-section__header">
          <span class="shortcut-section__icon shortcut-section__icon--app">
            <AppWindow :size="17" />
          </span>
          <div>
            <p>LOCAL APPLICATIONS</p>
            <h2 id="application-section-title">常用本地软件</h2>
          </div>
          <span class="shortcut-section__count">{{ applicationCount }}</span>
          <button
            class="shortcut-section__add"
            type="button"
            @click="addApplication"
          >
            <Plus :size="14" />
            添加软件
          </button>
        </header>

        <div v-if="applicationCount > 0" class="shortcut-grid">
          <ShortcutTile
            v-for="application in shortcuts.applications"
            :key="application.id"
            :name="application.name"
            :detail="application.path"
            :icon-url="shortcutIconUrl(application)"
            mode="application"
            @open="openApplication(application)"
            @edit="editApplication(application)"
          />
          <button
            class="shortcut-add-tile"
            type="button"
            @click="addApplication"
          >
            <Plus :size="17" />
            <span>添加本地软件</span>
          </button>
        </div>

        <button
          v-else
          class="application-empty"
          type="button"
          @click="addApplication"
        >
          <span class="application-empty__icon">
            <AppWindow :size="22" />
          </span>
          <span>
            <strong>把常用软件放到手边</strong>
            <small>桌面版可自动读取应用图标并直接启动</small>
          </span>
          <ArrowUpRight :size="16" />
        </button>
      </section>
    </div>

    <ShortcutDialog
      v-if="dialogMode"
      :mode="dialogMode"
      :shortcut="editingShortcut"
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
  gap: 30px;
  animation: workbench-enter 240ms ease both;
}

.shortcut-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: 34px;
}

.shortcut-section {
  min-width: 0;
}

.shortcut-section + .shortcut-section {
  padding-left: 34px;
  border-left: 1px solid var(--line);
}

.shortcut-section__header {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line-soft);
}

.shortcut-section__icon {
  display: grid;
  width: 32px;
  height: 32px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 7px;
  place-items: center;
}

.shortcut-section__icon--app {
  color: var(--success);
  background: rgb(26 127 55 / 9%);
}

.shortcut-section__header p {
  margin: 0 0 4px;
  color: var(--text-subtle);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 7px;
  font-weight: 700;
  letter-spacing: 0.11em;
}

.shortcut-section__header h2 {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 620;
}

.shortcut-section__count {
  display: grid;
  min-width: 22px;
  height: 22px;
  padding: 0 5px;
  color: var(--text-subtle);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  background: rgb(31 35 40 / 5%);
  border-radius: 5px;
  place-items: center;
}

.shortcut-section__add {
  display: inline-flex;
  height: 27px;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  color: var(--text-muted);
  font-size: 8px;
  font-weight: 600;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.shortcut-section__add:hover {
  color: var(--accent);
  border-color: rgb(9 105 218 / 28%);
  background: var(--accent-soft);
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  padding-top: 14px;
}

.shortcut-add-tile {
  display: flex;
  min-height: 70px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--text-subtle);
  font-size: 9px;
  background: transparent;
  border: 1px dashed var(--line);
  border-radius: 8px;
}

.shortcut-add-tile:hover {
  color: var(--accent);
  background: rgb(9 105 218 / 3%);
  border-color: rgb(9 105 218 / 32%);
}

.application-empty {
  display: grid;
  width: 100%;
  min-height: 94px;
  grid-template-columns: 42px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 12px;
  padding: 16px;
  margin-top: 14px;
  color: var(--text-muted);
  text-align: left;
  background: #f6f8fa;
  border: 1px dashed var(--line);
  border-radius: 8px;
}

.application-empty:hover {
  border-color: rgb(26 127 55 / 32%);
  background: rgb(26 127 55 / 3%);
}

.application-empty__icon {
  display: grid;
  width: 40px;
  height: 40px;
  color: var(--success);
  background: rgb(26 127 55 / 9%);
  border-radius: 8px;
  place-items: center;
}

.application-empty__icon--website {
  color: var(--accent);
  background: var(--accent-soft);
}

.application-empty > span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.application-empty strong {
  color: var(--text);
  font-size: 10px;
  font-weight: 620;
}

.application-empty small {
  color: var(--text-subtle);
  font-size: 8px;
}

html[data-theme="dark"] {
  .shortcut-section__count {
    background: rgb(240 246 252 / 6%);
  }

  .shortcut-section__icon {
    color: #79c0ff;
    background: rgb(56 139 253 / 14%);
  }

  .shortcut-section__icon--app,
  .application-empty__icon {
    color: #7ee787;
    background: rgb(63 185 80 / 12%);
  }

  .application-empty__icon--website {
    color: #79c0ff;
    background: rgb(56 139 253 / 14%);
  }

  .shortcut-section__add:hover {
    color: #79c0ff;
    border-color: rgb(88 166 255 / 22%);
    background: rgb(56 139 253 / 10%);
  }

  .shortcut-add-tile:hover {
    color: #79c0ff;
    background: rgb(56 139 253 / 5%);
    border-color: rgb(88 166 255 / 24%);
  }

  .application-empty {
    background: #161b22;
  }

  .application-empty:hover {
    background: rgb(63 185 80 / 4%);
    border-color: rgb(63 185 80 / 24%);
  }
}

@media (max-width: 1050px) {
  .shortcut-layout {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .shortcut-section + .shortcut-section {
    padding-top: 28px;
    padding-left: 0;
    border-top: 1px solid var(--line);
    border-left: 0;
  }
}

@media (max-width: 700px) {
  .workbench-home {
    gap: 24px;
  }

  .shortcut-grid {
    grid-template-columns: 1fr;
  }

  .shortcut-section__header {
    grid-template-columns: 34px minmax(0, 1fr) auto;
  }

  .shortcut-section__add {
    grid-column: 1 / -1;
    justify-self: stretch;
    justify-content: center;
    margin-top: 3px;
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
