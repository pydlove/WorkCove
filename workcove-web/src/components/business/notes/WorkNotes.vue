<script setup lang="ts">
import {
  Archive,
  ArchiveRestore,
  Bot,
  Check,
  Clock3,
  NotebookPen,
  Pin,
  Plus,
  Search,
  Star,
  Tag,
  Trash2,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiPagination from '@/components/ui/UiPagination.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores/useAuthStore'
import { useWorkNoteStore } from '@/stores/useWorkNoteStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  WorkNote,
  WorkNoteFilter,
} from '@/types/workNote'

type SaveState = 'idle' | 'saving' | 'saved'
type NoteToggleField =
  | 'pinned'
  | 'important'
  | 'resolved'
  | 'archived'

interface TimelineGroup {
  key: string
  label: string
  notes: WorkNote[]
}

const emit = defineEmits<{
  notify: [message: string]
  sendToCodex: [note: WorkNote]
}>()

const auth = useAuthStore()
const noteStore = useWorkNoteStore()
const workspaces = useWorkspaceStore()

const filter = ref<WorkNoteFilter>('active')
const searchQuery = ref('')
const selectedTag = ref('')
const activeNoteId = ref('')
const creating = ref(false)
const saveState = ref<SaveState>('idle')
const tagDraft = ref('')
const tagError = ref('')
const deleteTarget = ref<WorkNote | null>(null)
const noteContextMenuOpen = ref(false)
const noteContextMenu = ref<HTMLElement | null>(null)
const contextMenuNote = ref<WorkNote | null>(null)
const noteContextMenuX = ref(0)
const noteContextMenuY = ref(0)
const contentInput = ref<HTMLTextAreaElement | null>(null)
const tagInput = ref<InstanceType<typeof UiInput> | null>(null)
let saveTimer: number | null = null
let draftDirty = false

const draft = reactive({
  title: '',
  titleAuto: false,
  content: '',
  important: false,
  resolved: false,
  pinned: false,
  archived: false,
  tags: [] as string[],
  createdAt: '',
  updatedAt: '',
})

const filterOptions = [
  { value: 'active', label: '全部' },
  { value: 'important', label: '重要' },
  { value: 'resolved', label: '已解决' },
  { value: 'archived', label: '归档' },
]

const workspaceNotes = computed(() =>
  noteStore.notes.filter(
    (note) => note.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const availableTags = computed(() => {
  const counts = new Map<string, number>()
  workspaceNotes.value
    .filter((note) => !note.archived)
    .forEach((note) => {
      note.tags.forEach((tag) => {
        counts.set(tag, (counts.get(tag) ?? 0) + 1)
      })
    })
  return [...counts]
    .sort(
      ([leftTag, leftCount], [rightTag, rightCount]) =>
        rightCount - leftCount ||
        leftTag.localeCompare(rightTag, 'zh-CN'),
    )
    .slice(0, 12)
    .map(([tag]) => tag)
})

const filteredNotes = computed(() => {
  const keyword = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  return workspaceNotes.value
    .filter((note) => {
      if (filter.value === 'archived') return note.archived
      if (note.archived) return false
      if (filter.value === 'resolved') return note.resolved
      if (filter.value === 'important') return note.important
      return true
    })
    .filter(
      (note) => !selectedTag.value || note.tags.includes(selectedTag.value),
    )
    .filter(
      (note) =>
        !keyword ||
        note.title.toLocaleLowerCase('zh-CN').includes(keyword) ||
        note.content.toLocaleLowerCase('zh-CN').includes(keyword) ||
        note.tags.some((tag) =>
          tag.toLocaleLowerCase('zh-CN').includes(keyword),
        ),
    )
    .sort(
      (left, right) =>
        Number(right.pinned) - Number(left.pinned) ||
        right.updatedAt.localeCompare(left.updatedAt),
    )
})

const {
  currentPage,
  pageItems: pagedNotes,
  setPage,
} = usePagination(
  filteredNotes,
  () => `${filter.value}\n${searchQuery.value}\n${selectedTag.value}`,
)

const timelineGroups = computed<TimelineGroup[]>(() => {
  const groups = new Map<string, TimelineGroup>()
  pagedNotes.value.forEach((note) => {
    const key = note.pinned
      ? 'pinned'
      : noteDateKey(note.updatedAt)
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        label: note.pinned ? '置顶' : noteDateLabel(note.updatedAt),
        notes: [],
      })
    }
    groups.get(key)?.notes.push(note)
  })
  return [...groups.values()]
})

const activeNote = computed(() =>
  workspaceNotes.value.find((note) => note.id === activeNoteId.value),
)
const hasEditor = computed(() => creating.value || Boolean(activeNote.value))
const canPersist = computed(() => Boolean(draft.content.trim()))
const characterCount = computed(() => draft.content.length)
const saveStatusText = computed(() => {
  if (!hasEditor.value) return ''
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'saved') return '已自动保存'
  return '输入内容后自动保存'
})

watch(
  () => auth.email,
  (email) => {
    noteStore.load(email)
    activeNoteId.value = ''
    creating.value = false
    resetDraft()
    void nextTick(ensureSelection)
  },
  { immediate: true },
)

watch(
  () => workspaces.activeWorkspaceId,
  (_, previous) => {
    if (previous) flushDraft()
    selectedTag.value = ''
    activeNoteId.value = ''
    creating.value = false
    resetDraft()
    void nextTick(ensureSelection)
  },
)

watch([filter, searchQuery, selectedTag], () => {
  void nextTick(ensureSelection)
})

watch(
  () => noteStore.requestedNoteId,
  (noteId) => {
    if (!noteId) return
    const note = workspaceNotes.value.find(
      (item) => item.id === noteId,
    )
    if (!note) {
      noteStore.clearOpenRequest()
      return
    }

    filter.value = note.archived ? 'archived' : 'active'
    searchQuery.value = ''
    selectedTag.value = ''
    creating.value = false
    activeNoteId.value = note.id
    loadDraft(note)
    noteStore.clearOpenRequest()
    void nextTick(() => contentInput.value?.focus())
  },
  { immediate: true },
)

function resetDraft() {
  if (saveTimer !== null) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }
  draftDirty = false
  Object.assign(draft, {
    title: '',
    titleAuto: false,
    content: '',
    important: false,
    resolved: false,
    pinned: false,
    archived: false,
    tags: [],
    createdAt: '',
    updatedAt: '',
  })
  tagDraft.value = ''
  tagError.value = ''
  saveState.value = 'idle'
}

function loadDraft(note: WorkNote) {
  if (saveTimer !== null) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }
  draftDirty = false
  activeNoteId.value = note.id
  creating.value = false
  Object.assign(draft, {
    title: note.titleAuto ? '' : note.title,
    titleAuto: note.titleAuto,
    content: note.content,
    important: note.important,
    resolved: note.resolved,
    pinned: note.pinned,
    archived: note.archived,
    tags: [...note.tags],
    createdAt: note.createdAt,
    updatedAt: note.updatedAt,
  })
  tagDraft.value = ''
  tagError.value = ''
  saveState.value = 'saved'
}

function selectNote(note: WorkNote) {
  if (!creating.value && activeNoteId.value === note.id) return
  flushDraft()
  loadDraft(note)
}

function ensureSelection() {
  if (creating.value) return
  const notes = filteredNotes.value
  if (notes.length === 0) {
    const saved = flushDraft()
    activeNoteId.value = ''
    resetDraft()
    const latest = filteredNotes.value
    if (saved && latest.length) loadDraft(latest[0])
    return
  }
  if (!notes.some((note) => note.id === activeNoteId.value)) {
    flushDraft()
    const latest = filteredNotes.value
    if (latest.length) loadDraft(latest[0])
  }
}

function scheduleAutoSave() {
  draftDirty = true
  saveState.value = 'saving'
  if (saveTimer !== null) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveTimer = null
    commitDraft()
  }, 450)
}

function commitDraft() {
  if (!draftDirty) return activeNote.value ?? null
  if (!canPersist.value) {
    saveState.value = 'idle'
    return null
  }
  draftDirty = false
  try {
    const note = noteStore.save({
      id: activeNoteId.value || undefined,
      workspaceId: workspaces.activeWorkspaceId,
      title: draft.title,
      content: draft.content,
      important: draft.important,
      resolved: draft.resolved,
      pinned: draft.pinned,
      archived: draft.archived,
      tags: draft.tags,
    })
    if (!note) return null
    activeNoteId.value = note.id
    creating.value = false
    draft.title = note.titleAuto ? '' : note.title
    draft.titleAuto = note.titleAuto
    draft.tags = [...note.tags]
    draft.createdAt = note.createdAt
    draft.updatedAt = note.updatedAt
    saveState.value = 'saved'
    return note
  } catch (error) {
    draftDirty = true
    saveState.value = 'idle'
    emit(
      'notify',
      error instanceof Error ? error.message : '保存随笔失败',
    )
    return null
  }
}

function flushDraft() {
  if (saveTimer !== null) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }
  return commitDraft()
}

function sendCurrentNoteToCodex() {
  const note = flushDraft() ?? activeNote.value
  if (!note || !note.content.trim()) return
  emit('sendToCodex', note)
}

function startComposer() {
  flushDraft()
  creating.value = true
  activeNoteId.value = ''
  filter.value = 'active'
  searchQuery.value = ''
  selectedTag.value = ''
  resetDraft()
  saveState.value = 'idle'
  void nextTick(() => contentInput.value?.focus())
}

function cancelComposer() {
  if (saveTimer !== null) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }
  draftDirty = false
  creating.value = false
  resetDraft()
  ensureSelection()
}

function togglePinned() {
  if (!canPersist.value) return
  draft.pinned = !draft.pinned
  draftDirty = true
  commitDraft()
  void nextTick(ensureSelection)
}

function toggleImportant() {
  if (!canPersist.value) return
  draft.important = !draft.important
  draftDirty = true
  commitDraft()
  if (filter.value === 'important') void nextTick(ensureSelection)
}

function toggleResolved() {
  if (!canPersist.value) return
  draft.resolved = !draft.resolved
  draftDirty = true
  commitDraft()
  emit('notify', draft.resolved ? '已标记为已解决' : '已恢复为未解决')
  if (filter.value === 'resolved' && !draft.resolved) {
    void nextTick(ensureSelection)
  }
}

function toggleArchived() {
  if (!canPersist.value) return
  const archiving = !draft.archived
  draft.archived = archiving
  draftDirty = true
  commitDraft()
  filter.value = archiving ? 'archived' : 'active'
  emit('notify', archiving ? '随笔已归档' : '随笔已恢复')
}

function addTag() {
  const tag = tagDraft.value.trim().replace(/^#/, '')
  tagError.value = ''
  if (!tag) return
  if ([...tag].length > 24) {
    tagError.value = '标签不能超过 24 个字符'
    return
  }
  if (draft.tags.includes(tag)) {
    tagDraft.value = ''
    return
  }
  if (draft.tags.length >= 8) {
    tagError.value = '每条随笔最多添加 8 个标签'
    return
  }
  draft.tags = [...draft.tags, tag]
  tagDraft.value = ''
  draftDirty = true
  commitDraft()
  void nextTick(() => tagInput.value?.focus())
}

function handleTagKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' && event.key !== ',') return
  event.preventDefault()
  addTag()
}

function removeTag(tag: string) {
  draft.tags = draft.tags.filter((item) => item !== tag)
  if (selectedTag.value === tag && draft.tags.length === 0) {
    selectedTag.value = ''
  }
  draftDirty = true
  commitDraft()
  void nextTick(ensureSelection)
}

function closeNoteContextMenu() {
  noteContextMenuOpen.value = false
  noteContextMenu.value = null
  contextMenuNote.value = null
}

function showNoteContextMenu(
  note: WorkNote,
  clientX: number,
  clientY: number,
) {
  contextMenuNote.value = note
  noteContextMenuX.value = Math.max(8, clientX)
  noteContextMenuY.value = Math.max(8, clientY)
  noteContextMenuOpen.value = true
  void nextTick(() => {
    const rect = noteContextMenu.value?.getBoundingClientRect()
    const width = rect?.width ?? 208
    const height = rect?.height ?? 300
    noteContextMenuX.value = Math.max(
      8,
      Math.min(clientX, window.innerWidth - width - 8),
    )
    noteContextMenuY.value = Math.max(
      8,
      Math.min(clientY, window.innerHeight - height - 8),
    )
    noteContextMenu.value
      ?.querySelector<HTMLButtonElement>('[role="menuitem"]')
      ?.focus()
  })
}

function openNoteContextMenu(event: MouseEvent, note: WorkNote) {
  event.preventDefault()
  event.stopPropagation()
  showNoteContextMenu(note, event.clientX, event.clientY)
}

function handleNoteItemKeydown(event: KeyboardEvent, note: WorkNote) {
  if (
    event.key !== 'ContextMenu' &&
    !(event.shiftKey && event.key === 'F10')
  ) {
    return
  }
  event.preventDefault()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  showNoteContextMenu(note, rect.left + 24, rect.top + 36)
}

function handleNoteContextPointerDown(event: PointerEvent) {
  if (
    !noteContextMenuOpen.value ||
    !(event.target instanceof Node) ||
    noteContextMenu.value?.contains(event.target)
  ) {
    return
  }
  closeNoteContextMenu()
}

function toggleContextNoteFlag(field: NoteToggleField) {
  const target = contextMenuNote.value
  if (!target) return
  flushDraft()
  const note = noteStore.notes.find((item) => item.id === target.id)
  if (!note) {
    closeNoteContextMenu()
    emit('notify', '速记不存在或已被删除')
    return
  }

  const updated = noteStore.save({
    id: note.id,
    workspaceId: note.workspaceId,
    title: note.titleAuto ? '' : note.title,
    content: note.content,
    important: field === 'important' ? !note.important : note.important,
    resolved: field === 'resolved' ? !note.resolved : note.resolved,
    pinned: field === 'pinned' ? !note.pinned : note.pinned,
    archived: field === 'archived' ? !note.archived : note.archived,
    tags: [...note.tags],
  })
  closeNoteContextMenu()
  if (!updated) return

  if (activeNoteId.value === updated.id) {
    draft.important = updated.important
    draft.resolved = updated.resolved
    draft.pinned = updated.pinned
    draft.archived = updated.archived
    draft.updatedAt = updated.updatedAt
    saveState.value = 'saved'
  }

  if (field === 'archived') {
    filter.value = updated.archived ? 'archived' : 'active'
  } else {
    void nextTick(ensureSelection)
  }

  const messages: Record<NoteToggleField, [string, string]> = {
    pinned: ['已置顶速记', '已取消置顶'],
    important: ['已标记为重要', '已取消重要标记'],
    resolved: ['已标记为已解决', '已恢复为未解决'],
    archived: ['速记已归档', '速记已恢复'],
  }
  emit(
    'notify',
    updated[field]
      ? messages[field][0]
      : messages[field][1],
  )
}

function sendContextNoteToCodex() {
  const target = contextMenuNote.value
  if (!target) return
  flushDraft()
  const note = noteStore.notes.find((item) => item.id === target.id)
  closeNoteContextMenu()
  if (note?.content.trim()) emit('sendToCodex', note)
}

function requestContextDelete() {
  const target = contextMenuNote.value
  if (!target) return
  closeNoteContextMenu()
  deleteTarget.value = noteStore.notes.find(
    (note) => note.id === target.id,
  ) ?? target
}

function requestDelete() {
  if (activeNote.value) deleteTarget.value = activeNote.value
}

function confirmDelete() {
  const target = deleteTarget.value
  if (!target) return
  const deletingActiveNote = activeNoteId.value === target.id
  if (deletingActiveNote) {
    if (saveTimer !== null) {
      window.clearTimeout(saveTimer)
      saveTimer = null
    }
    draftDirty = false
  } else {
    flushDraft()
  }
  noteStore.remove(target.id)
  deleteTarget.value = null
  if (deletingActiveNote) {
    activeNoteId.value = ''
    creating.value = false
    resetDraft()
    void nextTick(ensureSelection)
  }
  emit('notify', `随笔「${target.title}」已删除`)
}

function tagClick(tag: string) {
  flushDraft()
  selectedTag.value = selectedTag.value === tag ? '' : tag
}

function clearSearch() {
  searchQuery.value = ''
}

function noteDateKey(value: string) {
  return new Date(value).toDateString()
}

function noteDateLabel(value: string) {
  const date = new Date(value)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return '今天'
  if (date.toDateString() === yesterday.toDateString()) return '昨天'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
  }).format(date)
}

function noteTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function noteExcerpt(note: WorkNote) {
  const lines = note.content.split('\n')
  const firstContentIndex = lines.findIndex((line) => line.trim())
  const firstLine =
    firstContentIndex >= 0
      ? lines[firstContentIndex]
          .trim()
          .replace(/^#+\s*/, '')
          .slice(0, 64)
      : ''
  const showsTitleInBody =
    firstContentIndex >= 0 &&
    (note.titleAuto || firstLine === note.title)
  const body =
    showsTitleInBody
      ? lines.slice(firstContentIndex + 1).join('\n')
      : note.content
  return body
    .replace(/^#+\s*/gm, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 180)
}

function noteMeta(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

onMounted(() => {
  document.addEventListener(
    'pointerdown',
    handleNoteContextPointerDown,
    true,
  )
  document.addEventListener('scroll', closeNoteContextMenu, true)
  window.addEventListener('resize', closeNoteContextMenu)
  window.addEventListener('blur', closeNoteContextMenu)
})

onBeforeUnmount(() => {
  flushDraft()
  document.removeEventListener(
    'pointerdown',
    handleNoteContextPointerDown,
    true,
  )
  document.removeEventListener('scroll', closeNoteContextMenu, true)
  window.removeEventListener('resize', closeNoteContextMenu)
  window.removeEventListener('blur', closeNoteContextMenu)
})

defineExpose({ startComposer })
</script>

<template>
  <section class="work-notes">
    <aside class="notes-timeline" aria-label="随笔记录流">
      <header class="notes-timeline__header">
        <div>
          <small>个人工作记录</small>
          <h2>记录流</h2>
        </div>
        <button
          class="notes-new-button"
          type="button"
          aria-label="新建随笔"
          title="新建随笔"
          @click="startComposer"
        >
          <Plus :size="16" />
        </button>
      </header>

      <div class="notes-timeline__search">
        <Search :size="14" aria-hidden="true" />
        <input
          v-model="searchQuery"
          type="search"
          aria-label="搜索随笔"
          placeholder="搜索内容、标题或标签"
        />
        <button
          v-if="searchQuery"
          type="button"
          aria-label="清除搜索"
          @click="clearSearch"
        >
          <X :size="13" />
        </button>
      </div>

      <UiSegmentedControl
        v-model="filter"
        class="notes-filter"
        size="sm"
        :items="filterOptions"
        aria-label="筛选随笔"
      />

      <div v-if="availableTags.length" class="notes-tag-filter">
        <button
          type="button"
          :class="{ 'notes-tag-filter__active': !selectedTag }"
          @click="selectedTag = ''"
        >
          全部标签
        </button>
        <button
          v-for="tag in availableTags"
          :key="tag"
          type="button"
          :class="{ 'notes-tag-filter__active': selectedTag === tag }"
          @click="tagClick(tag)"
        >
          {{ tag }}
        </button>
      </div>

      <div class="notes-timeline__body">
        <div v-if="timelineGroups.length" class="notes-groups">
          <section
            v-for="group in timelineGroups"
            :key="group.key"
            class="notes-group"
          >
            <header>
              <span>{{ group.label }}</span>
              <small>{{ group.notes.length }}</small>
            </header>
            <div class="notes-group__list">
              <button
                v-for="note in group.notes"
                :key="note.id"
                class="note-list-item"
                :class="{
                  'note-list-item--active':
                    activeNoteId === note.id && !creating,
                  'note-list-item--resolved': note.resolved,
                }"
                type="button"
                aria-haspopup="menu"
                @click="selectNote(note)"
                @contextmenu="openNoteContextMenu($event, note)"
                @keydown="handleNoteItemKeydown($event, note)"
              >
                <span class="note-list-item__top">
                  <strong>{{ note.title }}</strong>
                  <Pin
                    v-if="note.pinned"
                    class="note-list-item__pin"
                    :size="12"
                    aria-label="已置顶"
                  />
                </span>
                <span
                  v-if="noteExcerpt(note)"
                  class="note-list-item__excerpt"
                >
                  {{ noteExcerpt(note) }}
                </span>
                <span class="note-list-item__meta">
                  <time :datetime="note.updatedAt">
                    {{ noteTime(note.updatedAt) }}
                  </time>
                  <span
                    v-for="tag in note.tags.slice(0, 2)"
                    :key="tag"
                    class="note-list-item__tag"
                  >
                    {{ tag }}
                  </span>
                  <span class="note-list-item__state">
                    <Check
                      v-if="note.resolved"
                      class="note-list-item__resolved"
                      :size="12"
                      aria-label="已解决"
                    />
                    <Star
                      v-if="note.important"
                      class="note-list-item__important"
                      :size="11"
                      fill="currentColor"
                      aria-label="重要"
                    />
                  </span>
                </span>
              </button>
            </div>
          </section>
        </div>

        <div v-else class="notes-empty-list">
          <span><NotebookPen :size="22" /></span>
          <strong>
            {{ workspaceNotes.length ? '没有符合条件的记录' : '还没有随笔' }}
          </strong>
          <p>
            {{
              workspaceNotes.length
                ? '试试切换筛选条件或搜索词。'
                : '记录一个决定、一次故障或值得记住的工作细节。'
            }}
          </p>
          <UiButton
            v-if="!workspaceNotes.length"
            size="sm"
            @click="startComposer"
          >
            <Plus :size="14" />
            记录第一条
          </UiButton>
        </div>
      </div>

      <UiPagination
        :page="currentPage"
        :total="filteredNotes.length"
        label="随笔分页"
        compact
        @update:page="setPage"
      />
    </aside>

    <section class="notes-detail" aria-label="随笔编辑区">
      <template v-if="hasEditor">
        <header class="notes-detail__toolbar">
          <div class="notes-save-state">
            <Check v-if="saveState === 'saved'" :size="13" />
            <Clock3 v-else :size="13" />
            {{ saveStatusText }}
          </div>
          <div class="notes-detail__actions">
            <button
              class="notes-action--codex"
              type="button"
              :disabled="!canPersist"
              aria-label="交给 Codex 分析解决"
              title="交给 Codex 分析解决"
              @click="sendCurrentNoteToCodex"
            >
              <Bot :size="14" />
              <span>交给 Codex</span>
            </button>
            <button
              type="button"
              :class="{ 'notes-action--active': draft.pinned }"
              :disabled="!canPersist"
              :aria-label="draft.pinned ? '取消置顶' : '置顶随笔'"
              :title="draft.pinned ? '取消置顶' : '置顶'"
              @click="togglePinned"
            >
              <Pin :size="15" />
            </button>
            <button
              type="button"
              :class="{ 'notes-action--important': draft.important }"
              :disabled="!canPersist"
              :aria-label="draft.important ? '取消重要标记' : '标记为重要'"
              :title="draft.important ? '取消重要标记' : '标记重要'"
              @click="toggleImportant"
            >
              <Star
                :size="15"
                :fill="draft.important ? 'currentColor' : 'none'"
              />
            </button>
            <button
              type="button"
              :class="{ 'notes-action--resolved': draft.resolved }"
              :disabled="!canPersist"
              :aria-label="draft.resolved ? '标记为未解决' : '标记为已解决'"
              :title="draft.resolved ? '恢复为未解决' : '标记为已解决'"
              @click="toggleResolved"
            >
              <Check :size="15" />
            </button>
            <button
              type="button"
              :class="{ 'notes-action--active': draft.archived }"
              :disabled="!canPersist"
              :aria-label="draft.archived ? '恢复随笔' : '归档随笔'"
              :title="draft.archived ? '恢复' : '归档'"
              @click="toggleArchived"
            >
              <ArchiveRestore v-if="draft.archived" :size="15" />
              <Archive v-else :size="15" />
            </button>
            <button
              v-if="creating"
              type="button"
              aria-label="取消新建"
              title="取消"
              @click="cancelComposer"
            >
              <X :size="15" />
            </button>
            <button
              v-else
              class="notes-action--danger"
              type="button"
              aria-label="删除随笔"
              title="删除"
              @click="requestDelete"
            >
              <Trash2 :size="15" />
            </button>
          </div>
        </header>

        <div class="notes-detail__scroll">
          <input
            v-model="draft.title"
            class="notes-title-input"
            type="text"
            maxlength="80"
            aria-label="随笔标题"
            placeholder="标题（可选）"
            @input="scheduleAutoSave"
            @blur="flushDraft"
          />

          <div class="notes-detail__meta">
            <span v-if="draft.updatedAt">
              更新于 {{ noteMeta(draft.updatedAt) }}
            </span>
            <span v-else>新记录</span>
            <span v-if="draft.resolved" class="notes-resolved-badge">
              <Check :size="11" />
              已解决
            </span>
            <span>{{ characterCount }} 字符</span>
          </div>

          <div class="notes-editor-tags">
            <span class="notes-editor-tags__label">
              <Tag :size="13" />
              标签
            </span>
            <button
              v-for="tag in draft.tags"
              :key="tag"
              class="notes-editor-tag"
              type="button"
              :aria-label="`移除标签 ${tag}`"
              @click="removeTag(tag)"
            >
              {{ tag }}
              <X :size="11" />
            </button>
            <UiInput
              ref="tagInput"
              v-model="tagDraft"
              class="notes-editor-tags__input"
              size="sm"
              maxlength="24"
              aria-label="添加标签"
              placeholder="+ 添加标签"
              @keydown="handleTagKeydown"
              @blur="addTag"
            />
          </div>
          <p v-if="tagError" class="notes-tag-error" role="alert">
            {{ tagError }}
          </p>

          <textarea
            ref="contentInput"
            v-model="draft.content"
            class="notes-content-input"
            maxlength="20000"
            aria-label="随笔内容"
            placeholder="写下今天值得记住的事情、决定或结论…"
            @input="scheduleAutoSave"
            @blur="flushDraft"
          />
        </div>
      </template>

      <div v-else class="notes-detail-empty">
        <span><NotebookPen :size="30" /></span>
        <h2>把重要的事，留在这里</h2>
        <p>随手记录工作中的决定、故障、结论和待跟进事项。</p>
        <UiButton variant="primary" size="lg" @click="startComposer">
          <Plus :size="15" />
          新建随笔
        </UiButton>
        <small>快捷键 Command + Shift + N</small>
      </div>
    </section>

    <Teleport to="body">
      <Transition name="notes-context-menu">
        <div
          v-if="noteContextMenuOpen && contextMenuNote"
          ref="noteContextMenu"
          class="notes-context-menu"
          role="menu"
          aria-label="速记操作"
          :style="{
            left: `${noteContextMenuX}px`,
            top: `${noteContextMenuY}px`,
          }"
          @contextmenu.prevent
          @keydown.esc.prevent.stop="closeNoteContextMenu"
        >
          <button
            type="button"
            role="menuitem"
            @click="sendContextNoteToCodex"
          >
            <Bot :size="15" />
            <span>交给 Codex</span>
          </button>
          <div class="notes-context-menu__separator" role="separator" />
          <button
            type="button"
            role="menuitem"
            @click="toggleContextNoteFlag('pinned')"
          >
            <Pin
              :size="15"
              :fill="contextMenuNote.pinned ? 'currentColor' : 'none'"
            />
            <span>
              {{ contextMenuNote.pinned ? '取消置顶' : '置顶' }}
            </span>
          </button>
          <button
            type="button"
            role="menuitem"
            @click="toggleContextNoteFlag('important')"
          >
            <Star
              :size="15"
              :fill="contextMenuNote.important ? 'currentColor' : 'none'"
            />
            <span>
              {{
                contextMenuNote.important
                  ? '取消重要标记'
                  : '标记为重要'
              }}
            </span>
          </button>
          <button
            type="button"
            role="menuitem"
            @click="toggleContextNoteFlag('resolved')"
          >
            <Check :size="15" />
            <span>
              {{
                contextMenuNote.resolved
                  ? '恢复为未解决'
                  : '标记为已解决'
              }}
            </span>
          </button>
          <button
            type="button"
            role="menuitem"
            @click="toggleContextNoteFlag('archived')"
          >
            <ArchiveRestore
              v-if="contextMenuNote.archived"
              :size="15"
            />
            <Archive v-else :size="15" />
            <span>
              {{ contextMenuNote.archived ? '恢复速记' : '归档' }}
            </span>
          </button>
          <div class="notes-context-menu__separator" role="separator" />
          <button
            class="notes-context-menu__danger"
            type="button"
            role="menuitem"
            @click="requestContextDelete"
          >
            <Trash2 :size="15" />
            <span>删除</span>
          </button>
        </div>
      </Transition>
    </Teleport>

    <UiConfirmDialog
      v-if="deleteTarget"
      title="删除随笔？"
      :description="`「${deleteTarget.title}」将从当前工作空间中移除。`"
      warning="删除后无法恢复，请确认这条记录不再需要。"
      confirm-label="删除随笔"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.work-notes {
  display: grid;
  min-height: 620px;
  overflow: hidden;
  grid-template-columns: 292px minmax(0, 1fr);
  background: var(--canvas-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  animation: notes-enter var(--duration-window) var(--ease-apple) both;
}

.notes-timeline {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: var(--surface-secondary);
  border-right: 1px solid var(--line-soft);
}

.notes-timeline__header {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4) var(--space-3);
}

.notes-timeline__header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.notes-timeline__header small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.notes-timeline__header h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.notes-new-button {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--icon);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  place-items: center;
}

.notes-new-button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.notes-timeline__search {
  display: flex;
  height: var(--control-md);
  align-items: center;
  gap: 6px;
  margin: 0 var(--space-3) var(--space-3);
  padding: 0 var(--space-2);
  color: var(--text-subtle);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.notes-timeline__search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.notes-timeline__search input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
}

.notes-timeline__search button {
  display: grid;
  width: 18px;
  height: 18px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.notes-filter {
  width: calc(100% - var(--space-6));
  margin: 0 var(--space-3) var(--space-3);
}

.notes-tag-filter {
  display: flex;
  gap: var(--space-1);
  padding: 0 var(--space-3) var(--space-3);
  overflow-x: auto;
  scrollbar-width: none;
}

.notes-tag-filter::-webkit-scrollbar {
  display: none;
}

.notes-tag-filter button {
  height: 22px;
  flex: 0 0 auto;
  padding: 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-badge);
}

.notes-tag-filter button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.notes-tag-filter .notes-tag-filter__active {
  color: var(--category-chip-color);
  background: var(--category-chip-bg);
  box-shadow: inset 0 0 0 0.5px var(--category-chip-border);
}

.notes-timeline__body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  border-top: 1px solid var(--line-soft);
}

.notes-groups {
  display: flex;
  flex-direction: column;
}

.notes-group > header {
  display: flex;
  height: 30px;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.notes-group > header small {
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.notes-group__list {
  display: flex;
  flex-direction: column;
}

.note-list-item {
  display: flex;
  width: 100%;
  min-height: 82px;
  padding: var(--space-2) var(--space-4) var(--space-3);
  text-align: left;
  background: transparent;
  border-bottom: 1px solid var(--line-soft);
  flex-direction: column;
  transition: background var(--duration-fast) var(--ease-apple);
}

.note-list-item:hover {
  background: var(--hover-bg);
}

.note-list-item--active {
  background: var(--canvas-raised);
  box-shadow: inset 2px 0 0 var(--system-blue);
}

.note-list-item--active:hover {
  background: var(--canvas-raised);
}

.note-list-item--resolved .note-list-item__top strong,
.note-list-item--resolved .note-list-item__excerpt {
  color: var(--text-muted);
  text-decoration: line-through;
  text-decoration-color: var(--text-subtle);
  text-decoration-thickness: 1px;
}

.note-list-item__top {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.note-list-item__top strong {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-list-item__pin {
  flex: 0 0 auto;
  color: var(--system-blue);
}

.note-list-item__excerpt {
  display: -webkit-box;
  overflow: hidden;
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.note-list-item__meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.note-list-item__meta time {
  flex: 0 0 auto;
  font-family: var(--font-mono);
}

.note-list-item__tag {
  overflow: hidden;
  max-width: 76px;
  padding: 1px var(--space-1);
  color: var(--text-muted);
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-list-item__state {
  display: inline-flex;
  margin-left: auto;
  align-items: center;
  gap: var(--space-1);
  flex: 0 0 auto;
}

.note-list-item__resolved {
  color: var(--system-green);
}

.note-list-item__important {
  flex: 0 0 auto;
  color: var(--system-orange);
}

.notes-empty-list {
  display: flex;
  min-height: 350px;
  align-items: center;
  justify-content: center;
  padding: var(--space-5);
  text-align: center;
  flex-direction: column;
}

.notes-empty-list > span {
  display: grid;
  width: 43px;
  height: 43px;
  color: var(--icon);
  background: var(--surface-inset);
  border-radius: var(--radius-panel);
  place-items: center;
}

.notes-empty-list strong {
  margin-top: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.notes-empty-list p {
  max-width: 220px;
  margin: var(--space-1) 0 var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.notes-detail {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: var(--canvas-raised);
}

.notes-detail__toolbar {
  display: flex;
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-5);
  border-bottom: 1px solid var(--line-soft);
}

.notes-save-state {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.notes-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.notes-detail__actions button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.notes-detail__actions button:hover:not(:disabled) {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.notes-detail__actions button.notes-action--codex {
  display: inline-flex;
  width: auto;
  min-width: 0;
  gap: 5px;
  padding: 0 9px;
  color: var(--accent);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: var(--accent-soft);
}

.notes-detail__actions button.notes-action--codex:hover:not(:disabled) {
  color: var(--accent-strong);
  background: color-mix(
    in srgb,
    var(--accent) 14%,
    var(--canvas-raised)
  );
}

.notes-detail__actions button:disabled {
  opacity: 0.35;
}

.notes-detail__actions .notes-action--active {
  color: var(--system-blue);
  background: var(--accent-soft);
}

.notes-detail__actions .notes-action--important {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.notes-detail__actions .notes-action--resolved {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
}

.notes-detail__actions .notes-action--danger:hover {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.notes-detail__scroll {
  display: flex;
  min-height: 0;
  flex: 1;
  padding: var(--space-5) var(--space-8) var(--space-6);
  overflow-y: auto;
  flex-direction: column;
}

.notes-title-input {
  width: 100%;
  padding: 0;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-metric);
  font-weight: var(--weight-strong);
  line-height: 1.35;
  background: transparent;
  border: 0;
}

.notes-title-input::placeholder {
  color: var(--placeholder);
}

.notes-title-input:focus-visible,
.notes-content-input:focus-visible {
  outline: none;
}

.notes-detail__meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.notes-resolved-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px var(--space-1);
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
  border-radius: var(--radius-badge);
}

.notes-editor-tags {
  display: flex;
  min-height: 32px;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-3) 0;
  margin-top: var(--space-3);
  border-top: 1px solid var(--line-soft);
  border-bottom: 1px solid var(--line-soft);
  flex-wrap: wrap;
}

.notes-editor-tags__label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-right: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.notes-editor-tag {
  display: inline-flex;
  height: 22px;
  align-items: center;
  gap: 3px;
  padding: 0 var(--space-2);
  color: var(--category-chip-color);
  font-size: var(--font-caption);
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
  box-shadow: inset 0 0 0 0.5px var(--category-chip-border);
}

.notes-editor-tags__input {
  width: 92px;
  border: 0;
  box-shadow: none;
}

.notes-editor-tags__input:focus {
  box-shadow: none;
}

.notes-tag-error {
  margin: var(--space-1) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.notes-content-input {
  width: 100%;
  min-height: 360px;
  flex: 1;
  padding: var(--space-5) 0 0;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.75;
  resize: vertical;
  background: transparent;
  border: 0;
}

.notes-content-input::placeholder {
  color: var(--placeholder);
}

.notes-detail-empty {
  display: flex;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  text-align: center;
  flex-direction: column;
}

.notes-detail-empty > span {
  display: grid;
  width: 58px;
  height: 58px;
  color: var(--system-blue);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.notes-detail-empty h2 {
  margin: var(--space-4) 0 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.notes-detail-empty p {
  max-width: 360px;
  margin: var(--space-2) 0 var(--space-5);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.notes-detail-empty small {
  margin-top: var(--space-4);
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.notes-context-menu {
  position: fixed;
  z-index: var(--z-popover);
  width: 208px;
  padding: 4px;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: 10px;
  box-shadow: var(--dropdown-shadow);
  backdrop-filter: blur(24px) saturate(1.35);
}

.notes-context-menu > button {
  display: flex;
  width: 100%;
  min-height: 32px;
  align-items: center;
  gap: 9px;
  padding: 0 9px;
  color: var(--text);
  font-size: 13px;
  text-align: left;
  background: transparent;
  border-radius: 7px;
}

.notes-context-menu > button:hover,
.notes-context-menu > button:focus-visible {
  background: var(--hover-bg);
  outline: none;
}

.notes-context-menu > button > svg {
  flex: 0 0 auto;
  color: var(--text-muted);
}

.notes-context-menu > button > span {
  min-width: 0;
  flex: 1;
}

.notes-context-menu > .notes-context-menu__danger,
.notes-context-menu > .notes-context-menu__danger > svg {
  color: var(--system-red);
}

.notes-context-menu__separator {
  height: 1px;
  margin: 4px 6px;
  background: var(--line-soft);
}

.notes-context-menu-enter-active,
.notes-context-menu-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
  transform-origin: top left;
}

.notes-context-menu-enter-from,
.notes-context-menu-leave-to {
  opacity: 0;
  transform: translateY(-3px) scale(0.98);
}

@media (max-width: 900px) {
  .work-notes {
    grid-template-columns: 1fr;
  }

  .notes-timeline {
    max-height: 430px;
    border-right: 0;
    border-bottom: 1px solid var(--line-soft);
  }

  .notes-detail {
    min-height: 520px;
  }
}

@media (max-width: 560px) {
  .work-notes {
    min-height: 0;
    border-radius: var(--radius-panel);
  }

  .notes-timeline {
    max-height: 390px;
  }

  .notes-detail__toolbar {
    padding: var(--space-2) var(--space-3);
  }

  .notes-detail__actions button.notes-action--codex {
    width: 28px;
    padding: 0;
  }

  .notes-detail__actions .notes-action--codex span {
    display: none;
  }

  .notes-save-state {
    overflow: hidden;
    max-width: 122px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .notes-detail__scroll {
    padding: var(--space-4) var(--space-4) var(--space-5);
  }

  .notes-content-input {
    min-height: 300px;
  }
}

@keyframes notes-enter {
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
