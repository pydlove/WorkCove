<script setup lang="ts">
import {
  ArrowUpRight,
  Bell,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  NotebookPen,
  Plus,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  nextTick,
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
import UiSelect from '@/components/ui/UiSelect.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCalendarStore } from '@/stores/useCalendarStore'
import { useWorkNoteStore } from '@/stores/useWorkNoteStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type {
  CalendarEvent,
  CalendarEventColor,
} from '@/types/calendar'
import type { WorkNote } from '@/types/workNote'
import {
  lunarDateName,
  lunarDayName,
} from '@/utils/lunarCalendar'
import {
  workdayInfo,
  type WorkdayInfo,
} from '@/utils/workday'

interface CalendarDay {
  key: string
  date: Date
  day: number
  lunarDay: string
  workday: WorkdayInfo
  currentMonth: boolean
  isToday: boolean
  events: CalendarEvent[]
  notes: WorkNote[]
}

const emit = defineEmits<{
  notify: [message: string]
  openNote: [id: string]
}>()

const props = withDefaults(
  defineProps<{
    date?: string
  }>(),
  {
    date: '',
  },
)

const auth = useAuthStore()
const calendar = useCalendarStore()
const noteStore = useWorkNoteStore()
const workspaces = useWorkspaceStore()

const today = new Date()
const viewDate = ref(new Date(today.getFullYear(), today.getMonth(), 1))
const selectedDateKey = ref(dateKey(today))
const editorOpen = ref(false)
const reminderSettingsOpen = ref(false)
const editingEvent = ref<CalendarEvent | null>(null)
const deleteTarget = ref<CalendarEvent | null>(null)
const formError = ref('')
const savingEvent = ref(false)
const savingSettings = ref(false)
const deletingEvent = ref(false)
const quickTitle = ref('')
const quickTitleInput = ref<HTMLInputElement | null>(null)
const quickTime = ref(nextDefaultTime(today))
const quickColor = ref<CalendarEventColor>('blue')
const reminderSettingsForm = reactive({
  defaultInApp: true,
  defaultEmail: false,
  defaultMinutesBefore: 10,
})

const eventForm = reactive({
  title: '',
  date: selectedDateKey.value,
  startTime: '09:00',
  endTime: '',
  notes: '',
  color: 'blue' as CalendarEventColor,
  reminderInApp: true,
  reminderEmail: false,
  reminderMinutesBefore: 10,
})

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const colorOptions: Array<{
  value: CalendarEventColor
  label: string
}> = [
  { value: 'blue', label: '蓝色' },
  { value: 'green', label: '绿色' },
  { value: 'orange', label: '橙色' },
  { value: 'red', label: '红色' },
  { value: 'purple', label: '紫色' },
]
const reminderOptions = [
  { value: '0', label: '准时提醒' },
  { value: '5', label: '提前 5 分钟' },
  { value: '10', label: '提前 10 分钟' },
  { value: '15', label: '提前 15 分钟' },
  { value: '30', label: '提前 30 分钟' },
  { value: '60', label: '提前 1 小时' },
  { value: '1440', label: '提前 1 天' },
]

const workspaceEvents = computed(() =>
  calendar.events.filter(
    (event) => event.workspaceId === workspaces.activeWorkspaceId,
  ),
)

const workspaceNotes = computed(() =>
  noteStore.notes.filter(
    (note) =>
      note.workspaceId === workspaces.activeWorkspaceId &&
      !note.archived,
  ),
)

const notesByDate = computed(() => {
  const result = new Map<string, WorkNote[]>()
  workspaceNotes.value.forEach((note) => {
    const timestamp = new Date(note.createdAt)
    if (Number.isNaN(timestamp.getTime())) return
    const key = dateKey(timestamp)
    result.set(key, [...(result.get(key) ?? []), note])
  })
  return result
})

const eventsByDate = computed(() => {
  const result = new Map<string, CalendarEvent[]>()
  workspaceEvents.value.forEach((event) => {
    result.set(event.date, [...(result.get(event.date) ?? []), event])
  })
  for (const events of result.values()) {
    events.sort((left, right) =>
      left.startTime.localeCompare(right.startTime),
    )
  }
  return result
})

const monthTitle = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
  }).format(viewDate.value),
)

const monthDays = computed<CalendarDay[]>(() => {
  const year = viewDate.value.getFullYear()
  const month = viewDate.value.getMonth()
  const first = new Date(year, month, 1, 12)
  const mondayOffset = (first.getDay() + 6) % 7
  const start = new Date(first)
  start.setDate(first.getDate() - mondayOffset)

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const key = dateKey(date)
    return {
      key,
      date,
      day: date.getDate(),
      lunarDay: lunarDayName(date),
      workday: workdayInfo(date),
      currentMonth: date.getMonth() === month,
      isToday: key === dateKey(today),
      events: eventsByDate.value.get(key) ?? [],
      notes: notesByDate.value.get(key) ?? [],
    }
  })
})

const selectedDate = computed(
  () => parseDateKey(selectedDateKey.value) ?? today,
)
const selectedDateParts = computed(() => ({
  day: selectedDate.value.getDate(),
  month: `${selectedDate.value.getMonth() + 1}月`,
  weekday: new Intl.DateTimeFormat('zh-CN', {
    weekday: 'long',
  }).format(selectedDate.value),
  year: String(selectedDate.value.getFullYear()),
  lunar: lunarDateName(selectedDate.value),
  workday: workdayInfo(selectedDate.value),
}))
const monthPrefix = computed(
  () =>
    `${viewDate.value.getFullYear()}-${pad(viewDate.value.getMonth() + 1)}`,
)
const monthEventCount = computed(
  () =>
    workspaceEvents.value.filter((event) =>
      event.date.startsWith(monthPrefix.value),
    ).length,
)
const monthNoteCount = computed(
  () =>
    [...notesByDate.value.entries()]
      .filter(([key]) => key.startsWith(monthPrefix.value))
      .reduce((total, [, notes]) => total + notes.length, 0),
)
const selectedEvents = computed(
  () => eventsByDate.value.get(selectedDateKey.value) ?? [],
)
const selectedNotes = computed(
  () => notesByDate.value.get(selectedDateKey.value) ?? [],
)
const selectedActivityCount = computed(
  () => selectedEvents.value.length + selectedNotes.value.length,
)
const reminderMinutesModel = computed({
  get: () => String(eventForm.reminderMinutesBefore),
  set: (value: string) => {
    eventForm.reminderMinutesBefore = Number(value) || 0
  },
})
const defaultReminderMinutesModel = computed({
  get: () => String(reminderSettingsForm.defaultMinutesBefore),
  set: (value: string) => {
    reminderSettingsForm.defaultMinutesBefore = Number(value) || 0
  },
})

watch(
  () => auth.email,
  (email) => {
    void calendar.load(email).catch((error) => {
      emit(
        'notify',
        error instanceof Error
          ? `日程加载失败：${error.message}`
          : '日程加载失败',
      )
    })
    noteStore.load(email)
  },
  { immediate: true },
)

watch(
  () => workspaces.activeWorkspaceId,
  () => {
    quickTitle.value = ''
    editorOpen.value = false
    editingEvent.value = null
  },
)

watch(
  () => calendar.reminderSettings,
  (settings) => {
    Object.assign(reminderSettingsForm, settings)
  },
  { immediate: true, deep: true },
)

watch(
  () => props.date,
  (value) => {
    const date = parseDateKey(value)
    if (!date) return
    selectedDateKey.value = dateKey(date)
    viewDate.value = new Date(
      date.getFullYear(),
      date.getMonth(),
      1,
    )
    quickTime.value = nextDefaultTime(date)
  },
  { immediate: true },
)

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function dateKey(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function parseDateKey(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const date = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    12,
  )
  return Number.isNaN(date.getTime()) ? null : date
}

function nextDefaultTime(date: Date) {
  const next = new Date(date)
  next.setMinutes(Math.ceil(next.getMinutes() / 15) * 15, 0, 0)
  return `${pad(next.getHours())}:${pad(next.getMinutes())}`
}

function changeMonth(offset: number) {
  const next = new Date(
    viewDate.value.getFullYear(),
    viewDate.value.getMonth() + offset,
    1,
  )
  viewDate.value = next
  selectedDateKey.value = dateKey(next)
}

function goToday() {
  const current = new Date()
  viewDate.value = new Date(
    current.getFullYear(),
    current.getMonth(),
    1,
  )
  selectedDateKey.value = dateKey(current)
  quickTime.value = nextDefaultTime(current)
}

function selectDay(day: CalendarDay) {
  selectedDateKey.value = day.key
  if (!day.currentMonth) {
    viewDate.value = new Date(
      day.date.getFullYear(),
      day.date.getMonth(),
      1,
    )
  }
}

function openNewEvent() {
  editingEvent.value = null
  formError.value = ''
  Object.assign(eventForm, {
    title: '',
    date: selectedDateKey.value,
    startTime:
      selectedDateKey.value === dateKey(today)
        ? nextDefaultTime(today)
        : '09:00',
    endTime: '',
    notes: '',
    color: quickColor.value,
    reminderInApp: calendar.reminderSettings.defaultInApp,
    reminderEmail: calendar.reminderSettings.defaultEmail,
    reminderMinutesBefore:
      calendar.reminderSettings.defaultMinutesBefore,
  })
  editorOpen.value = true
}

function editEvent(event: CalendarEvent) {
  editingEvent.value = event
  formError.value = ''
  Object.assign(eventForm, {
    title: event.title,
    date: event.date,
    startTime: event.startTime,
    endTime: event.endTime,
    notes: event.notes,
    color: event.color,
    reminderInApp: event.reminderInApp,
    reminderEmail: event.reminderEmail,
    reminderMinutesBefore: event.reminderMinutesBefore,
  })
  editorOpen.value = true
}

async function saveEvent() {
  if (savingEvent.value) return
  formError.value = ''
  savingEvent.value = true
  try {
    const saved = await calendar.save({
      id: editingEvent.value?.id,
      workspaceId: workspaces.activeWorkspaceId,
      title: eventForm.title,
      date: eventForm.date,
      startTime: eventForm.startTime,
      endTime: eventForm.endTime,
      notes: eventForm.notes,
      color: eventForm.color,
      reminderInApp: eventForm.reminderInApp,
      reminderEmail: eventForm.reminderEmail,
      reminderMinutesBefore: eventForm.reminderMinutesBefore,
    })
    selectedDateKey.value = saved.date
    const parsed = parseDateKey(saved.date)
    if (parsed) {
      viewDate.value = new Date(
        parsed.getFullYear(),
        parsed.getMonth(),
        1,
      )
    }
    editorOpen.value = false
    editingEvent.value = null
    emit(
      'notify',
      saved ? '日程已保存' : '日程未保存',
    )
  } catch (error) {
    formError.value =
      error instanceof Error ? error.message : '无法保存日程'
  } finally {
    savingEvent.value = false
  }
}

async function quickAddEvent() {
  const title = quickTitle.value.trim()
  if (!title || savingEvent.value) return
  savingEvent.value = true
  try {
    await calendar.save({
      workspaceId: workspaces.activeWorkspaceId,
      title,
      date: selectedDateKey.value,
      startTime: quickTime.value,
      endTime: '',
      notes: '',
      color: quickColor.value,
      reminderInApp: calendar.reminderSettings.defaultInApp,
      reminderEmail: calendar.reminderSettings.defaultEmail,
      reminderMinutesBefore:
        calendar.reminderSettings.defaultMinutesBefore,
    })
    quickTitle.value = ''
    emit('notify', '日程已添加')
    await nextTick()
    quickTitleInput.value?.focus()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法添加日程',
    )
  } finally {
    savingEvent.value = false
  }
}

function requestDelete(event: CalendarEvent) {
  deleteTarget.value = event
}

async function confirmDelete() {
  const event = deleteTarget.value
  if (!event || deletingEvent.value) return
  deletingEvent.value = true
  try {
    await calendar.remove(event.id)
    deleteTarget.value = null
    editorOpen.value = false
    editingEvent.value = null
    emit('notify', '日程已删除')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '日程删除失败',
    )
  } finally {
    deletingEvent.value = false
  }
}

function openReminderSettings() {
  Object.assign(reminderSettingsForm, calendar.reminderSettings)
  reminderSettingsOpen.value = true
}

async function saveReminderSettings() {
  if (savingSettings.value) return
  if (
    !reminderSettingsForm.defaultInApp &&
    !reminderSettingsForm.defaultEmail
  ) {
    emit('notify', '请至少选择一种提醒方式')
    return
  }
  savingSettings.value = true
  try {
    await calendar.saveReminderSettings({
      defaultInApp: reminderSettingsForm.defaultInApp,
      defaultEmail: reminderSettingsForm.defaultEmail,
      defaultMinutesBefore:
        reminderSettingsForm.defaultMinutesBefore,
    })
    reminderSettingsOpen.value = false
    emit('notify', '提醒设置已保存')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '提醒设置保存失败',
    )
  } finally {
    savingSettings.value = false
  }
}

function noteExcerpt(note: WorkNote) {
  return note.content.replace(/\s+/g, ' ').trim().slice(0, 80)
}
</script>

<template>
  <section class="user-calendar">
    <header class="user-calendar__toolbar">
      <div class="user-calendar__period">
        <div class="user-calendar__period-copy">
          <h2>{{ monthTitle }}</h2>
          <span>
            {{ monthEventCount }} 个日程
            <i>·</i>
            {{ monthNoteCount }} 条速记
          </span>
        </div>
      </div>
      <div class="user-calendar__toolbar-actions">
        <div class="user-calendar__arrows">
          <button
            type="button"
            aria-label="上个月"
            @click="changeMonth(-1)"
          >
            <ChevronLeft :size="16" />
          </button>
          <button
            type="button"
            aria-label="下个月"
            @click="changeMonth(1)"
          >
            <ChevronRight :size="16" />
          </button>
        </div>
        <UiButton size="lg" @click="goToday">今天</UiButton>
        <UiButton size="lg" @click="openReminderSettings">
          <Bell :size="14" />
          提醒设置
        </UiButton>
        <UiButton variant="primary" size="lg" @click="openNewEvent">
          <Plus :size="14" />
          新建日程
        </UiButton>
      </div>
    </header>

    <div class="user-calendar__layout">
      <div class="user-calendar__month">
        <div class="user-calendar__weekdays" aria-hidden="true">
          <span
            v-for="(weekday, index) in weekdays"
            :key="weekday"
            :class="{ 'user-calendar__weekday--weekend': index >= 5 }"
          >
            {{ weekday }}
          </span>
        </div>

        <div class="user-calendar__grid" role="grid">
          <div
            v-for="day in monthDays"
            :key="day.key"
            class="calendar-day"
            :class="{
              'calendar-day--muted': !day.currentMonth,
              'calendar-day--today': day.isToday,
              'calendar-day--selected': selectedDateKey === day.key,
              'calendar-day--work': day.workday.workday,
              'calendar-day--rest': !day.workday.workday,
              'calendar-day--busy':
                day.events.length > 0 || day.notes.length > 0,
            }"
            role="gridcell"
            tabindex="0"
            :aria-label="`${day.key}，${day.events.length} 个日程，${day.notes.length} 条速记`"
            @click="selectDay(day)"
            @keydown.enter.prevent="selectDay(day)"
            @keydown.space.prevent="selectDay(day)"
          >
            <div class="calendar-day__number">
              <span class="calendar-day__date">
                <strong>{{ day.day }}</strong>
                <small>{{ day.lunarDay }}</small>
              </span>
              <span class="calendar-day__marks">
                <em
                  class="calendar-day__workday"
                  :class="{
                    'calendar-day__workday--rest':
                      !day.workday.workday,
                  }"
                  :title="
                    [
                      day.workday.workday ? '工作日' : '休息日',
                      day.workday.festival,
                    ]
                      .filter(Boolean)
                      .join(' · ')
                  "
                >
                  {{ day.workday.label }}
                </em>
                <i v-if="day.notes.length" title="有速记">
                  <NotebookPen :size="10" />
                  {{ day.notes.length }}
                </i>
              </span>
            </div>

            <div class="calendar-day__events">
              <button
                v-for="event in day.events.slice(0, 3)"
                :key="event.id"
                class="calendar-event-chip"
                :class="`calendar-event-chip--${event.color}`"
                type="button"
                :title="`${event.startTime} ${event.title}`"
                @click.stop="editEvent(event)"
              >
                <span>{{ event.startTime }}</span>
                <em>{{ event.title }}</em>
              </button>
              <small v-if="day.events.length > 3">
                +{{ day.events.length - 3 }} 项
              </small>
              <button
                v-if="day.notes[0]"
                type="button"
                class="calendar-note-chip"
                :title="day.notes[0].title"
                @click.stop="emit('openNote', day.notes[0].id)"
              >
                <NotebookPen :size="9" />
                {{ day.notes[0].title }}
              </button>
            </div>

            <div class="calendar-day__dots" aria-hidden="true">
              <i
                v-for="event in day.events.slice(0, 4)"
                :key="event.id"
                :class="`calendar-dot--${event.color}`"
              />
              <i
                v-if="day.notes.length"
                class="calendar-dot--note"
              />
            </div>
          </div>
        </div>
      </div>

      <aside class="user-calendar__inspector">
        <header class="calendar-inspector-heading">
          <div class="calendar-inspector-date">
            <strong>{{ selectedDateParts.day }}</strong>
            <span>
              <b>{{ selectedDateParts.month }}</b>
              <small>
                {{ selectedDateParts.weekday }} ·
                {{ selectedDateParts.year }}
              </small>
              <em
                class="calendar-inspector-workday"
                :class="{
                  'calendar-inspector-workday--rest':
                    !selectedDateParts.workday.workday,
                }"
              >
                {{ selectedDateParts.workday.label }}
                <template v-if="selectedDateParts.workday.festival">
                  · {{ selectedDateParts.workday.festival }}
                </template>
              </em>
              <em>农历{{ selectedDateParts.lunar }}</em>
            </span>
          </div>
          <CalendarDays :size="18" />
        </header>
        <p class="calendar-inspector-summary">
          {{ selectedActivityCount }} 项安排
          <span>
            {{ selectedEvents.length }} 个日程 ·
            {{ selectedNotes.length }} 条速记
          </span>
        </p>

        <form class="calendar-quick-add" @submit.prevent="quickAddEvent">
          <label
            class="calendar-quick-add__title"
            for="calendar-quick-title"
          >
            <Plus :size="14" aria-hidden="true" />
            <input
              id="calendar-quick-title"
              ref="quickTitleInput"
              v-model="quickTitle"
              type="text"
              maxlength="120"
              enterkeyhint="done"
              aria-label="快速添加日程"
              placeholder="快速添加日程"
            />
          </label>
          <div class="calendar-quick-add__meta">
            <input
              v-model="quickTime"
              type="time"
              aria-label="日程时间"
            />
            <span class="calendar-color-picker">
              <button
                v-for="color in colorOptions"
                :key="color.value"
                type="button"
                class="calendar-color-dot"
                :class="[
                  `calendar-color-dot--${color.value}`,
                  {
                    'calendar-color-dot--selected':
                      quickColor === color.value,
                  },
                ]"
                :aria-label="`选择${color.label}`"
                :aria-pressed="quickColor === color.value"
                @click="quickColor = color.value"
              />
            </span>
            <button
              type="submit"
              :disabled="!quickTitle.trim() || savingEvent"
            >
              {{ savingEvent ? '保存中' : '添加' }}
            </button>
          </div>
        </form>

        <section class="calendar-inspector-section">
          <header>
            <span>
              <Clock3 :size="13" />
              日程
            </span>
            <small>{{ selectedEvents.length }}</small>
          </header>
          <div v-if="selectedEvents.length" class="calendar-agenda-list">
            <button
              v-for="event in selectedEvents"
              :key="event.id"
              type="button"
              @click="editEvent(event)"
            >
              <time>
                {{ event.startTime }}
                <small v-if="event.endTime">{{ event.endTime }}</small>
              </time>
              <i :class="`calendar-dot--${event.color}`" />
              <span>
                <strong>{{ event.title }}</strong>
                <small>{{ event.notes || '日程安排' }}</small>
              </span>
            </button>
          </div>
          <p v-else class="calendar-inspector-empty">当天没有日程</p>
        </section>

        <section class="calendar-inspector-section">
          <header>
            <span>
              <NotebookPen :size="13" />
              速记
            </span>
            <small>{{ selectedNotes.length }}</small>
          </header>
          <div v-if="selectedNotes.length" class="calendar-notes-list">
            <button
              v-for="note in selectedNotes"
              :key="note.id"
              class="calendar-note-card"
              type="button"
              :aria-label="`打开速记：${note.title}`"
              @click="emit('openNote', note.id)"
            >
              <span class="calendar-note-icon">
                <NotebookPen :size="12" />
              </span>
              <div>
                <strong>{{ note.title }}</strong>
                <p>{{ noteExcerpt(note) }}</p>
              </div>
              <ArrowUpRight :size="13" />
            </button>
          </div>
          <p v-else class="calendar-inspector-empty">
            当天没有创建速记
          </p>
        </section>
      </aside>
    </div>

    <UiDialog
      v-if="editorOpen"
      :title="editingEvent ? '编辑日程' : '新建日程'"
      description="日程保存在当前用户和工作空间中。"
      :suspended="Boolean(deleteTarget)"
      @close="editorOpen = false"
    >
      <form class="calendar-event-form" @submit.prevent="saveEvent">
        <UiFormField
          label="名称"
          control-id="calendar-event-title"
          required
        >
          <UiInput
            id="calendar-event-title"
            v-model="eventForm.title"
            size="lg"
            maxlength="120"
            placeholder="日程名称"
          />
        </UiFormField>
        <UiFormField
          label="日期"
          control-id="calendar-event-date"
          required
        >
          <UiInput
            id="calendar-event-date"
            v-model="eventForm.date"
            type="date"
            size="lg"
          />
        </UiFormField>
        <div class="calendar-event-form__times">
          <UiFormField
            label="开始"
            control-id="calendar-event-start"
            required
          >
            <UiInput
              id="calendar-event-start"
              v-model="eventForm.startTime"
              type="time"
              size="lg"
            />
          </UiFormField>
          <UiFormField
            label="结束"
            control-id="calendar-event-end"
          >
            <UiInput
              id="calendar-event-end"
              v-model="eventForm.endTime"
              type="time"
              size="lg"
            />
          </UiFormField>
        </div>
        <UiFormField label="颜色">
          <div class="calendar-event-form__colors">
            <button
              v-for="color in colorOptions"
              :key="color.value"
              type="button"
              class="calendar-color-dot"
              :class="[
                `calendar-color-dot--${color.value}`,
                {
                  'calendar-color-dot--selected':
                    eventForm.color === color.value,
                },
              ]"
              :aria-label="color.label"
              :aria-pressed="eventForm.color === color.value"
              @click="eventForm.color = color.value"
            />
          </div>
        </UiFormField>
        <section class="calendar-event-reminders">
          <header>
            <Bell :size="15" />
            <div>
              <strong>日程提醒</strong>
              <small>通过站内信或账号邮箱提醒</small>
            </div>
          </header>
          <div class="calendar-event-reminders__options">
            <div>
              <UiCheckbox
                v-model="eventForm.reminderInApp"
                label="启用站内信提醒"
              />
              <span>
                <strong>站内信</strong>
                <small>显示在工作台通知中心</small>
              </span>
            </div>
            <div>
              <UiCheckbox
                v-model="eventForm.reminderEmail"
                label="启用邮件提醒"
              />
              <span>
                <strong>邮件</strong>
                <small>发送到账号注册邮箱</small>
              </span>
            </div>
          </div>
          <UiSelect
            v-model="reminderMinutesModel"
            :options="reminderOptions"
            size="lg"
            aria-label="提醒时间"
          />
        </section>
        <UiFormField label="备注">
          <textarea
            v-model="eventForm.notes"
            rows="3"
            maxlength="2000"
            placeholder="补充地点、参与人或准备事项"
          />
        </UiFormField>
        <p v-if="formError" class="calendar-event-form__error" role="alert">
          {{ formError }}
        </p>
      </form>

      <template #footer>
        <UiButton
          v-if="editingEvent"
          variant="danger-ghost"
          size="lg"
          :disabled="savingEvent"
          @click="requestDelete(editingEvent)"
        >
          <Trash2 :size="14" />
          删除日程
        </UiButton>
        <span class="calendar-event-form__spacer" />
        <UiButton
          size="lg"
          :disabled="savingEvent"
          @click="editorOpen = false"
        >
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          :disabled="savingEvent"
          @click="saveEvent"
        >
          {{ savingEvent ? '保存中…' : '保存' }}
        </UiButton>
      </template>
    </UiDialog>

    <UiDialog
      v-if="reminderSettingsOpen"
      title="提醒设置"
      description="这里设置新建日程默认使用的提醒方式和提前时间。"
      @close="reminderSettingsOpen = false"
    >
      <div class="calendar-reminder-settings-dialog">
        <div>
          <UiCheckbox
            v-model="reminderSettingsForm.defaultInApp"
            label="默认启用站内信提醒"
          />
          <span>
            <strong>站内信提醒</strong>
            <small>工作台右上角通知中心接收提醒</small>
          </span>
        </div>
        <div>
          <UiCheckbox
            v-model="reminderSettingsForm.defaultEmail"
            label="默认启用邮件提醒"
          />
          <span>
            <strong>邮件提醒</strong>
            <small>发送到当前账号的注册邮箱</small>
          </span>
        </div>
        <UiFormField label="默认提前时间">
          <UiSelect
            v-model="defaultReminderMinutesModel"
            :options="reminderOptions"
            size="lg"
            aria-label="默认提醒时间"
          />
        </UiFormField>
      </div>
      <template #footer>
        <UiButton
          size="lg"
          :disabled="savingSettings"
          @click="reminderSettingsOpen = false"
        >
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          :disabled="savingSettings"
          @click="saveReminderSettings"
        >
          {{ savingSettings ? '保存中…' : '保存设置' }}
        </UiButton>
      </template>
    </UiDialog>

    <UiConfirmDialog
      v-if="deleteTarget"
      title="删除这个日程？"
      :description="`「${deleteTarget.title}」将从日历中移除。`"
      warning="删除后无法恢复，请确认后继续。"
      :busy="deletingEvent"
      busy-label="删除中…"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.user-calendar {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-3);
}

.user-calendar__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.user-calendar__period {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.user-calendar__period-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.user-calendar__period-copy h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-metric);
  font-weight: var(--weight-strong);
  line-height: 1.1;
}

.user-calendar__period-copy > span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.user-calendar__period-copy > span i {
  margin: 0 3px;
  font-style: normal;
}

.user-calendar__toolbar-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.user-calendar__arrows {
  display: flex;
  gap: 2px;
}

.user-calendar__arrows button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-muted);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
  place-items: center;
}

.user-calendar__arrows button:hover {
  color: var(--text);
}

.user-calendar__layout {
  display: grid;
  min-height: 0;
  grid-template-columns: minmax(0, 1fr) 308px;
  gap: var(--space-3);
}

.user-calendar__month {
  overflow: hidden;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.user-calendar__weekdays {
  display: grid;
  height: 38px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  color: var(--text-subtle);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: var(--surface-inset);
  border-bottom: 1px solid var(--line-soft);
}

.user-calendar__weekdays span {
  display: grid;
  border-right: 1px solid var(--line-soft);
  place-items: center;
}

.user-calendar__weekdays span:last-child {
  border-right: 0;
}

.user-calendar__weekdays .user-calendar__weekday--weekend {
  color: color-mix(
    in srgb,
    var(--system-red) 42%,
    var(--text-subtle)
  );
}

.user-calendar__grid {
  display: grid;
  gap: 1px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  padding: 1px;
  background: var(--line-soft);
}

.calendar-day {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 104px;
  flex-direction: column;
  gap: var(--space-1);
  padding: 6px;
  cursor: pointer;
  background: var(--canvas-raised);
  transition: background var(--duration-fast) var(--ease-apple);
}

.calendar-day:hover {
  background: var(--hover-bg);
}

.calendar-day:focus-visible {
  z-index: 1;
  outline: 2px solid var(--system-blue);
  outline-offset: -2px;
}

.calendar-day--muted {
  color: var(--text-subtle);
  background: color-mix(in srgb, var(--canvas) 72%, var(--canvas-raised));
}

.calendar-day--rest:not(.calendar-day--selected) {
  background: color-mix(
    in srgb,
    var(--system-red) 3%,
    var(--canvas-raised)
  );
}

.calendar-day--busy .calendar-day__date strong {
  font-weight: var(--weight-emphasis);
}

.calendar-day--selected {
  background: color-mix(
    in srgb,
    var(--accent-soft) 54%,
    var(--canvas-raised)
  );
  box-shadow: inset 0 0 0 2px color-mix(
    in srgb,
    var(--system-blue) 38%,
    transparent
  );
}

.calendar-day__number {
  display: flex;
  min-height: 18px;
  align-items: center;
  justify-content: space-between;
}

.calendar-day__date {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
}

.calendar-day__date strong {
  display: grid;
  width: 20px;
  height: 20px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  font-variant-numeric: tabular-nums;
  border-radius: 50%;
  place-items: center;
}

.calendar-day__date small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-day--today .calendar-day__date strong {
  color: #ffffff;
  background: var(--system-blue);
}

.calendar-day__number i {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--text-subtle);
  font-size: 11px;
  font-style: normal;
}

.calendar-day__marks {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.calendar-day__workday {
  display: grid;
  min-width: 17px;
  height: 16px;
  padding: 0 3px;
  color: color-mix(in srgb, var(--system-blue) 76%, var(--text));
  font-size: 11px;
  font-style: normal;
  font-weight: var(--weight-emphasis);
  line-height: 16px;
  background: color-mix(in srgb, var(--system-blue) 9%, transparent);
  border-radius: var(--radius-badge);
  place-items: center;
}

.calendar-day__workday--rest {
  color: color-mix(in srgb, var(--system-red) 72%, var(--text));
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.calendar-day__events {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.calendar-event-chip {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 3px;
  overflow: hidden;
  padding: 3px 5px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 14px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border-radius: var(--radius-badge);
  box-shadow: inset 2px 0 0 currentcolor;
}

.calendar-event-chip:hover {
  background: var(--hover-bg);
}

.calendar-event-chip > span {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
}

.calendar-event-chip > em {
  min-width: 0;
  overflow: hidden;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-event-chip--blue {
  color: var(--system-blue);
  background: color-mix(in srgb, var(--system-blue) 7%, transparent);
}

.calendar-event-chip--green {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 8%, transparent);
}

.calendar-event-chip--orange {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 8%, transparent);
}

.calendar-event-chip--red {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
}

.calendar-event-chip--purple {
  color: color-mix(in srgb, var(--system-blue) 52%, var(--system-red));
  background: color-mix(
    in srgb,
    color-mix(in srgb, var(--system-blue) 52%, var(--system-red)) 8%,
    transparent
  );
}

.calendar-day__events > small {
  padding-left: 4px;
  color: var(--text-subtle);
  font-size: 11px;
}

.calendar-note-chip {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 3px;
  overflow: hidden;
  padding: 2px 4px;
  color: var(--text-subtle);
  font-size: 11px;
  line-height: 14px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border-top: 0;
  border-right: 0;
  border-bottom: 0;
  border-left: 2px solid var(--line);
}

.calendar-note-chip svg {
  flex: 0 0 auto;
}

.calendar-day__dots {
  display: none;
}

.calendar-dot--blue {
  background: var(--system-blue);
}

.calendar-dot--green {
  background: var(--system-green);
}

.calendar-dot--orange {
  background: var(--system-orange);
}

.calendar-dot--red {
  background: var(--system-red);
}

.calendar-dot--purple {
  background: color-mix(in srgb, var(--system-blue) 52%, var(--system-red));
}

.calendar-dot--note {
  background: var(--text-subtle);
}

.user-calendar__inspector {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.calendar-inspector-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.calendar-inspector-date {
  display: grid;
  min-width: 0;
  grid-template-columns: 48px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-2);
}

.calendar-inspector-date > strong {
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 36px;
  font-weight: var(--weight-strong);
  letter-spacing: 0;
  line-height: 1;
}

.calendar-inspector-date > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.calendar-inspector-date b {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.calendar-inspector-date small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.calendar-inspector-date em {
  color: var(--text-subtle);
  font-size: 11px;
  font-style: normal;
}

.calendar-inspector-date .calendar-inspector-workday {
  color: color-mix(in srgb, var(--system-blue) 76%, var(--text));
  font-weight: var(--weight-emphasis);
}

.calendar-inspector-date .calendar-inspector-workday--rest {
  color: color-mix(in srgb, var(--system-red) 72%, var(--text));
}

.calendar-inspector-heading > svg {
  color: var(--text-subtle);
}

.calendar-inspector-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  margin: calc(-1 * var(--space-1)) 0 0;
  color: var(--text);
  font-size: var(--font-caption);
  border-bottom: 1px solid var(--line-soft);
}

.calendar-inspector-summary span {
  color: var(--text-subtle);
}

.calendar-quick-add {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.calendar-quick-add__title {
  display: flex;
  min-height: var(--control-lg);
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  color: var(--text-subtle);
  cursor: text;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.calendar-quick-add__title:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.calendar-quick-add input[type="text"] {
  width: 100%;
  min-width: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.calendar-quick-add input::placeholder {
  color: var(--placeholder);
}

.calendar-quick-add input:focus-visible {
  outline: none;
}

.calendar-quick-add__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.calendar-quick-add__meta input[type="time"] {
  height: var(--control-md);
  padding: 0 6px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-badge);
}

.calendar-quick-add__meta > button {
  height: var(--control-md);
  padding: 0 var(--space-2);
  margin-left: auto;
  color: #ffffff;
  font-size: var(--font-caption);
  background: var(--system-blue);
  border-radius: var(--radius-badge);
}

.calendar-quick-add__meta > button:disabled {
  opacity: 0.38;
}

.calendar-color-picker,
.calendar-event-form__colors {
  display: flex;
  align-items: center;
  gap: 5px;
}

.calendar-color-picker {
  padding-left: 10px;
  border-left: 1px solid var(--line-soft);
}

.calendar-color-dot {
  width: 15px;
  height: 15px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 16%);
}

.calendar-color-dot--blue {
  background: var(--system-blue);
}

.calendar-color-dot--green {
  background: var(--system-green);
}

.calendar-color-dot--orange {
  background: var(--system-orange);
}

.calendar-color-dot--red {
  background: var(--system-red);
}

.calendar-color-dot--purple {
  background: color-mix(in srgb, var(--system-blue) 52%, var(--system-red));
}

.calendar-color-dot--selected {
  outline: 2px solid var(--text);
  outline-offset: 2px;
}

.calendar-inspector-section {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-2);
}

.calendar-inspector-section > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.calendar-inspector-section > header span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.calendar-inspector-section > header small {
  font-family: var(--font-mono);
}

.calendar-agenda-list,
.calendar-notes-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.calendar-agenda-list button {
  display: grid;
  width: 100%;
  grid-template-columns: 42px 4px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  color: var(--text);
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.calendar-agenda-list button:hover {
  background: var(--hover-bg);
}

.calendar-agenda-list button > time {
  display: flex;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
  flex-direction: column;
  gap: 1px;
}

.calendar-agenda-list button > time small {
  color: var(--text-subtle);
  font-size: 11px;
}

.calendar-agenda-list button > i {
  width: 4px;
  height: 28px;
  border-radius: var(--radius-badge);
}

.calendar-agenda-list button > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.calendar-agenda-list strong {
  overflow: hidden;
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-agenda-list small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.calendar-note-card {
  display: grid;
  width: 100%;
  grid-template-columns: 24px minmax(0, 1fr) 16px;
  align-items: start;
  gap: var(--space-2);
  padding: var(--space-2);
  color: var(--text);
  text-align: left;
  background: var(--canvas);
  border-radius: var(--radius-control);
  transition: background var(--duration-fast) var(--ease-apple);
}

.calendar-note-card:hover {
  background: var(--hover-bg);
}

.calendar-note-card > svg {
  color: var(--text-subtle);
}

.calendar-note-icon {
  display: grid;
  width: 24px;
  height: 24px;
  color: var(--text-subtle);
  background: var(--canvas-raised);
  border-radius: var(--radius-badge);
  place-items: center;
}

.calendar-note-card > div {
  min-width: 0;
}

.calendar-notes-list strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.calendar-notes-list p {
  display: -webkit-box;
  overflow: hidden;
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.calendar-inspector-empty {
  padding: var(--space-3);
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  background: var(--canvas);
  border-radius: var(--radius-control);
}

.calendar-event-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.calendar-event-reminders,
.calendar-reminder-settings-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.calendar-event-reminders {
  padding: var(--space-3);
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--system-blue) 4%, var(--canvas-raised)),
      var(--canvas)
    );
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.calendar-event-reminders > header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--system-blue);
}

.calendar-event-reminders > header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.calendar-event-reminders strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.calendar-event-reminders small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.calendar-event-reminders__options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.calendar-event-reminders__options > div,
.calendar-reminder-settings-dialog > div {
  display: flex;
  min-height: 52px;
  align-items: center;
  gap: var(--space-2);
  padding: 10px 12px;
  background: color-mix(in srgb, var(--canvas-raised) 82%, transparent);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.calendar-event-reminders__options > div > span,
.calendar-reminder-settings-dialog > div > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.calendar-reminder-settings-dialog {
  padding: 0 var(--space-5) var(--space-4);
}

.calendar-reminder-settings-dialog .ui-form-field {
  padding-top: var(--space-1);
}

.calendar-event-form__times {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

.calendar-event-form textarea {
  width: 100%;
  min-height: 72px;
  padding: var(--space-2);
  resize: vertical;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: var(--line-body);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.calendar-event-form textarea:focus {
  border-color: var(--system-blue);
  outline: none;
  box-shadow: var(--focus-ring);
}

.calendar-event-form__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.calendar-event-form__spacer {
  flex: 1;
}

@media (max-width: 980px) {
  .user-calendar__layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .user-calendar__toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .user-calendar__period {
    width: 100%;
  }

  .user-calendar__period-copy {
    flex: 1;
  }

  .user-calendar__period-copy h2 {
    font-size: var(--font-title);
  }

  .user-calendar__toolbar-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .calendar-day {
    min-height: 66px;
    padding: 4px;
  }

  .calendar-day__date {
    align-items: center;
    flex-direction: column;
    gap: 0;
  }

  .calendar-day__marks i {
    display: none;
  }

  .calendar-day__events {
    display: none;
  }

  .calendar-day__dots {
    display: flex;
    gap: 2px;
    flex-wrap: wrap;
  }

  .calendar-day__dots i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
  }

  .calendar-day__number i {
    font-size: 0;
  }

  .calendar-event-form {
    padding: 0 var(--space-4) var(--space-4);
  }

  .calendar-event-reminders__options {
    grid-template-columns: 1fr;
  }

  .calendar-reminder-settings-dialog {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>
