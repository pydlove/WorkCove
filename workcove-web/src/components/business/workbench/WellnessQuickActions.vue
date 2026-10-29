<script setup lang="ts">
import {
  Coffee,
  DoorOpen,
  Droplets,
  Footprints,
  HeartPulse,
  History,
  Settings2,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import WellnessHistoryDialog from '@/components/business/workbench/WellnessHistoryDialog.vue'
import WellnessReminderSettingsDialog from '@/components/business/workbench/WellnessReminderSettingsDialog.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useWellnessStore } from '@/stores/useWellnessStore'
import type { WellnessActionType } from '@/types/wellness'
import {
  localDateKey,
  wellnessDaySummary,
} from '@/utils/wellness'

const emit = defineEmits<{
  notify: [message: string]
}>()

const WATER_GOAL = 8
const MOVEMENT_GOAL = 6

const auth = useAuthStore()
const wellness = useWellnessStore()
const root = ref<HTMLElement | null>(null)
const triggerButton = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const open = ref(false)
const now = ref(new Date())
const historyOpen = ref(false)
const reminderSettingsOpen = ref(false)

const today = computed(() => localDateKey(now.value))
const summary = computed(() =>
  wellnessDaySummary(wellness.data, today.value, now.value),
)
const waterProgress = computed(() =>
  Math.min(100, (summary.value.water / WATER_GOAL) * 100),
)
const movementProgress = computed(() =>
  Math.min(100, (summary.value.movements / MOVEMENT_GOAL) * 100),
)
const workStatusLabel = computed(() =>
  summary.value.activeWorkStartedAt
    ? '工作中，可随时快捷记录'
    : summary.value.clockInAt
      ? '今天的工作已结束'
      : '今天还未完成上班打卡',
)

watch(
  () => auth.email,
  (email) => wellness.load(email),
  { immediate: true },
)

function refreshDay() {
  now.value = new Date()
  if (!wellness.ownerEmail) return
  wellness.ensureDay(now.value)
  wellness.closeStaleSessions(now.value)
}

function togglePanel() {
  if (open.value) {
    closePanel()
    return
  }
  refreshDay()
  open.value = true
  void nextTick(() => {
    panel.value
      ?.querySelector<HTMLButtonElement>('.wellness-quick-actions__action')
      ?.focus()
  })
}

function closePanel(restoreFocus = false) {
  open.value = false
  if (restoreFocus) {
    void nextTick(() => triggerButton.value?.focus())
  }
}

function recordAction(type: WellnessActionType) {
  refreshDay()
  wellness.recordAction(type, now.value)
  emit(
    'notify',
    type === 'water'
      ? '已记录一杯水'
      : type === 'restroom'
        ? '已记录一次洗手间'
        : '已记录一次工位活动',
  )
}

function startRest() {
  refreshDay()
  if (!wellness.startRest(now.value)) return
  closePanel()
  emit('notify', '休息计时开始，先离开屏幕一会儿')
}

function openHistory() {
  closePanel()
  historyOpen.value = true
}

function openReminderSettings() {
  closePanel()
  reminderSettingsOpen.value = true
}

function handlePointerDown(event: PointerEvent) {
  if (!open.value) return
  const target = event.target
  if (target instanceof Node && !root.value?.contains(target)) {
    closePanel()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    closePanel(true)
  }
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
  <div ref="root" class="wellness-quick-actions">
    <button
      ref="triggerButton"
      class="wellness-quick-actions__trigger"
      type="button"
      aria-label="打开健康快捷操作"
      aria-controls="wellness-quick-actions-panel"
      :aria-expanded="open"
      title="健康快捷操作"
      @click="togglePanel"
    >
      <Coffee :size="15" />
      <span
        v-if="wellness.data.activeRest"
        class="wellness-quick-actions__status"
        aria-hidden="true"
      />
    </button>

    <Transition name="wellness-quick-actions">
      <section
        v-if="open"
        id="wellness-quick-actions-panel"
        ref="panel"
        class="wellness-quick-actions__panel"
        role="region"
        aria-label="今日关怀快捷操作"
      >
        <header class="wellness-quick-actions__header">
          <span class="wellness-quick-actions__heading">
            <span class="wellness-quick-actions__heading-icon">
              <HeartPulse :size="16" />
            </span>
            <span>
              <strong>今日关怀</strong>
              <small>{{ workStatusLabel }}</small>
            </span>
          </span>
          <button
            class="wellness-quick-actions__close"
            type="button"
            aria-label="关闭健康快捷操作"
            @click="closePanel(true)"
          >
            <X :size="14" />
          </button>
        </header>

        <div class="wellness-quick-actions__list">
          <button
            class="wellness-quick-actions__action is-water"
            type="button"
            aria-label="记录喝了一杯水"
            @click="recordAction('water')"
          >
            <span class="wellness-quick-actions__icon">
              <Droplets :size="15" />
            </span>
            <span class="wellness-quick-actions__copy">
              <span class="wellness-quick-actions__line">
                <strong>喝水</strong>
                <small>{{ summary.water }} / {{ WATER_GOAL }}</small>
              </span>
              <span class="wellness-quick-actions__progress" aria-hidden="true">
                <i :style="{ width: `${waterProgress}%` }" />
              </span>
            </span>
            <span class="wellness-quick-actions__add">+1</span>
          </button>

          <button
            class="wellness-quick-actions__action is-movement"
            type="button"
            aria-label="记录一次工位活动"
            @click="recordAction('stand')"
          >
            <span class="wellness-quick-actions__icon">
              <Footprints :size="15" />
            </span>
            <span class="wellness-quick-actions__copy">
              <span class="wellness-quick-actions__line">
                <strong>活动</strong>
                <small>{{ summary.movements }} / {{ MOVEMENT_GOAL }}</small>
              </span>
              <span class="wellness-quick-actions__progress" aria-hidden="true">
                <i :style="{ width: `${movementProgress}%` }" />
              </span>
            </span>
            <span class="wellness-quick-actions__add">+1</span>
          </button>

          <button
            class="wellness-quick-actions__action is-restroom"
            type="button"
            aria-label="记录去洗手间"
            @click="recordAction('restroom')"
          >
            <span class="wellness-quick-actions__icon">
              <DoorOpen :size="15" />
            </span>
            <span class="wellness-quick-actions__copy">
              <span class="wellness-quick-actions__line">
                <strong>洗手间</strong>
                <small>{{ summary.restroom }} 次</small>
                <em>按需</em>
              </span>
            </span>
            <span class="wellness-quick-actions__add">+1</span>
          </button>
        </div>

        <footer class="wellness-quick-actions__footer">
          <button
            type="button"
            :disabled="Boolean(wellness.data.activeRest)"
            @click="startRest"
          >
            <Coffee :size="14" />
            <span>
              {{ wellness.data.activeRest ? '休息中' : '休息一下' }}
            </span>
          </button>
          <button type="button" @click="openHistory">
            <History :size="14" />
            <span>记录</span>
          </button>
          <button type="button" @click="openReminderSettings">
            <Settings2 :size="14" />
            <span>提醒设置</span>
          </button>
        </footer>
      </section>
    </Transition>

    <WellnessHistoryDialog
      v-if="historyOpen"
      @close="historyOpen = false"
    />

    <WellnessReminderSettingsDialog
      v-if="reminderSettingsOpen"
      @close="reminderSettingsOpen = false"
      @saved="emit('notify', '健康提醒间隔已保存')"
    />
  </div>
</template>

<style scoped>
.wellness-quick-actions {
  position: relative;
  display: flex;
}

.wellness-quick-actions__trigger {
  position: relative;
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--accent);
  background: color-mix(
    in srgb,
    var(--accent) 13%,
    transparent
  );
  border-radius: var(--radius-control);
  box-shadow: inset 0 0 0 0.5px
    color-mix(in srgb, var(--accent) 30%, transparent);
  place-items: center;
}

.wellness-quick-actions__trigger:hover,
.wellness-quick-actions__trigger[aria-expanded="true"] {
  color: var(--accent-strong);
  background: color-mix(
    in srgb,
    var(--accent) 20%,
    transparent
  );
}

.wellness-quick-actions__status {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 6px;
  height: 6px;
  background: var(--system-green);
  border: 1.5px solid var(--material);
  border-radius: var(--radius-round);
}

.wellness-quick-actions__panel {
  position: absolute;
  z-index: 80;
  right: 0;
  bottom: calc(100% + 10px);
  width: min(340px, calc(100vw - 24px));
  padding: var(--space-3);
  color: var(--text);
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-radius);
  box-shadow: var(--dropdown-shadow);
}

.wellness-quick-actions__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 2px 2px var(--space-2);
}

.wellness-quick-actions__heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.wellness-quick-actions__heading-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-quick-actions__heading > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.wellness-quick-actions__heading strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.wellness-quick-actions__heading small {
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-quick-actions__close {
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

.wellness-quick-actions__close:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.wellness-quick-actions__list {
  border-top: 1px solid var(--line-soft);
  border-bottom: 1px solid var(--line-soft);
}

.wellness-quick-actions__action {
  --wellness-quick-tone: var(--accent);

  display: grid;
  width: 100%;
  min-height: 52px;
  grid-template-columns: 28px minmax(0, 1fr) 32px;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 4px;
  text-align: left;
  background: transparent;
  border-radius: var(--radius-control);
}

.wellness-quick-actions__action + .wellness-quick-actions__action {
  border-top: 1px solid var(--line-soft);
}

.wellness-quick-actions__action:hover {
  background: var(--hover-bg);
}

.wellness-quick-actions__action.is-movement {
  --wellness-quick-tone: var(--system-green);
}

.wellness-quick-actions__action.is-restroom {
  --wellness-quick-tone: var(--system-orange);
}

.wellness-quick-actions__icon {
  display: grid;
  width: 28px;
  height: 28px;
  color: var(--wellness-quick-tone);
  background: color-mix(
    in srgb,
    var(--wellness-quick-tone) 10%,
    transparent
  );
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-quick-actions__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.wellness-quick-actions__line {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 6px;
}

.wellness-quick-actions__line strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.wellness-quick-actions__line small {
  overflow: hidden;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-quick-actions__line em {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  font-style: normal;
}

.wellness-quick-actions__progress {
  display: block;
  width: 100%;
  height: 3px;
  overflow: hidden;
  background: var(--line-soft);
  border-radius: var(--radius-badge);
}

.wellness-quick-actions__progress i {
  display: block;
  height: 100%;
  background: var(--wellness-quick-tone);
  border-radius: inherit;
  transition: width var(--duration-fast) var(--ease-apple);
}

.wellness-quick-actions__add {
  display: grid;
  height: 24px;
  color: var(--wellness-quick-tone);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  background: color-mix(
    in srgb,
    var(--wellness-quick-tone) 8%,
    transparent
  );
  border-radius: var(--radius-control);
  place-items: center;
}

.wellness-quick-actions__footer {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-1);
  padding-top: var(--space-2);
}

.wellness-quick-actions__footer button {
  display: flex;
  min-width: 0;
  height: 30px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 6px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-control);
}

.wellness-quick-actions__footer button:hover:not(:disabled) {
  color: var(--accent);
  background: var(--accent-soft);
}

.wellness-quick-actions__footer button:disabled {
  opacity: 0.5;
}

.wellness-quick-actions__footer span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wellness-quick-actions-enter-active,
.wellness-quick-actions-leave-active {
  transition:
    opacity var(--duration-popover) var(--ease-apple),
    transform var(--duration-popover) var(--ease-apple);
  transform-origin: bottom right;
}

.wellness-quick-actions-enter-from,
.wellness-quick-actions-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.98);
}

@media (max-width: 520px) {
  .wellness-quick-actions__panel {
    width: min(340px, calc(100vw - 20px));
  }
}

@media (prefers-reduced-motion: reduce) {
  .wellness-quick-actions-enter-active,
  .wellness-quick-actions-leave-active {
    transition: none;
  }
}
</style>
