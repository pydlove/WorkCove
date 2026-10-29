<script setup lang="ts">
import {
  Camera,
  CircleCheck,
  CircleAlert,
  CircleX,
  Clock3,
  MessageSquareText,
  Minimize2,
  Monitor,
  ZoomIn,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import { apiUrl } from '@/api/http'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import TextWithLinks from '@/components/ui/TextWithLinks.vue'
import type { ZentaoBug } from '@/types/zentao'
import type { ZentaoTestResult } from '@/types/zentaoTest'

const props = defineProps<{
  bug: ZentaoBug
  result: ZentaoTestResult
  publishingNote?: boolean
}>()

const emit = defineEmits<{
  close: []
  syncNote: []
  openUrl: [
    payload: {
      url: string
      title: string
    },
  ]
}>()

const failedScreenshots = ref<string[]>([])
const previewFilename = ref('')
const previewZoomed = ref(false)
const previewElement = ref<HTMLElement | null>(null)
const previewDragging = ref(false)
const previewDragMoved = ref(false)
const previewDragStart = {
  x: 0,
  y: 0,
  scrollLeft: 0,
  scrollTop: 0,
  pointerId: -1,
}

const statusLabel = computed(() => {
  if (props.result.status === 'passed') return '测试通过'
  if (props.result.status === 'failed') return '测试未通过'
  return '暂无法验证'
})

const statusIcon = computed(() => {
  if (props.result.status === 'passed') return CircleCheck
  if (props.result.status === 'failed') return CircleX
  return CircleAlert
})
const previewUrl = computed(() =>
  previewFilename.value ? evidenceUrl(previewFilename.value) : '',
)
const environmentLines = computed(() =>
  props.result.environment
    .split(/[\n;；]+/)
    .map((segment) => segment.trim())
    .filter(Boolean)
    .map((segment) => {
      const match = segment.match(
        /^([^:：]{1,24})[:：]\s*(?!\/\/)(.*)$/,
      )
      return match
        ? {
            label: match[1].trim(),
            value: match[2].trim(),
          }
        : {
            label: '',
            value: segment,
          }
    }),
)

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function openResultUrl(url: string) {
  emit('openUrl', {
    url,
    title: `Bug #${props.bug.remoteId} · 测试环境`,
  })
}

function evidenceUrl(filename: string) {
  return apiUrl(
    `/development/zentao/bugs/${props.bug.id}/test-runs/`
      + `${encodeURIComponent(props.result.runId)}/evidence/`
      + `${encodeURIComponent(filename)}`
      + `?workspaceId=${encodeURIComponent(props.result.workspaceId)}`,
  )
}

function markFailed(filename: string) {
  failedScreenshots.value = [
    ...new Set([...failedScreenshots.value, filename]),
  ]
}

function openScreenshot(filename: string) {
  if (failedScreenshots.value.includes(filename)) return
  previewFilename.value = filename
  previewZoomed.value = false
}

function closeScreenshotPreview() {
  previewFilename.value = ''
  previewZoomed.value = false
}

function togglePreviewZoom() {
  previewZoomed.value = !previewZoomed.value
}

function startPreviewDrag(event: PointerEvent) {
  const element = previewElement.value
  if (
    !previewZoomed.value ||
    !element ||
    event.button !== 0
  ) {
    return
  }
  previewDragging.value = true
  previewDragMoved.value = false
  previewDragStart.x = event.clientX
  previewDragStart.y = event.clientY
  previewDragStart.scrollLeft = element.scrollLeft
  previewDragStart.scrollTop = element.scrollTop
  previewDragStart.pointerId = event.pointerId
  element.setPointerCapture(event.pointerId)
  event.preventDefault()
}

function movePreviewDrag(event: PointerEvent) {
  const element = previewElement.value
  if (
    !previewDragging.value ||
    !element ||
    event.pointerId !== previewDragStart.pointerId
  ) {
    return
  }
  const deltaX = event.clientX - previewDragStart.x
  const deltaY = event.clientY - previewDragStart.y
  if (Math.abs(deltaX) > 2 || Math.abs(deltaY) > 2) {
    previewDragMoved.value = true
  }
  element.scrollLeft = previewDragStart.scrollLeft - deltaX
  element.scrollTop = previewDragStart.scrollTop - deltaY
  event.preventDefault()
}

function endPreviewDrag(event?: PointerEvent) {
  const element = previewElement.value
  if (
    !previewDragging.value ||
    !element ||
    (event && event.pointerId !== previewDragStart.pointerId)
  ) {
    return
  }
  if (
    event &&
    element.hasPointerCapture(event.pointerId)
  ) {
    element.releasePointerCapture(event.pointerId)
  }
  previewDragging.value = false
  previewDragStart.pointerId = -1
  if (previewDragMoved.value) {
    window.setTimeout(() => {
      previewDragMoved.value = false
    }, 0)
  }
}

function handlePreviewClick() {
  if (previewDragMoved.value) {
    previewDragMoved.value = false
    return
  }
  togglePreviewZoom()
}

function handlePreviewError() {
  const filename = previewFilename.value
  if (filename) markFailed(filename)
  closeScreenshotPreview()
}
</script>

<template>
  <UiDialog
    :title="bug.title"
    :description="`Bug #${bug.remoteId} · Codex 测试结果`"
    size="extra-wide"
    height="tall"
    title-emphasis="prominent"
    scroll-mode="body"
    max-height="min(860px, calc(100dvh - 32px))"
    @close="emit('close')"
  >
    <div class="zentao-test-result">
      <section
        class="zentao-test-result__summary"
        :class="`zentao-test-result__summary--${result.status}`"
      >
        <span class="zentao-test-result__summary-icon">
          <component :is="statusIcon" :size="22" />
        </span>
        <div>
          <strong>{{ statusLabel }}</strong>
          <p><TextWithLinks :text="result.proof" @open="openResultUrl" /></p>
        </div>
      </section>

      <dl class="zentao-test-result__meta">
        <div class="zentao-test-result__environment">
          <dt><Monitor :size="13" /> 测试环境</dt>
          <dd>
            <div
              v-if="environmentLines.length"
              class="zentao-test-result__environment-grid"
            >
              <span
                v-for="(line, index) in environmentLines"
                :key="`${line.label}:${index}`"
                class="zentao-test-result__environment-item"
              >
                <small v-if="line.label">{{ line.label }}</small>
                <TextWithLinks
                  :text="line.value"
                  @open="openResultUrl"
                />
              </span>
            </div>
            <span v-else>未说明测试环境</span>
          </dd>
        </div>
        <div class="zentao-test-result__time">
          <dt><Clock3 :size="13" /> 测试时间</dt>
          <dd>{{ formatTime(result.createdAt) }}</dd>
        </div>
        <div class="zentao-test-result__shot-count">
          <dt><Camera :size="13" /> 测试截图</dt>
          <dd>{{ result.screenshots.length }} 张</dd>
        </div>
      </dl>

      <div
        v-if="result.zentaoNoteError"
        class="zentao-test-result__note-status zentao-test-result__note-status--error"
      >
        <CircleAlert :size="14" />
        <span>{{ result.zentaoNoteError }}</span>
      </div>
      <div
        v-else-if="result.zentaoNoteAt"
        class="zentao-test-result__note-status"
      >
        <CircleCheck :size="14" />
        <span>
          测试说明已同步到禅道 ·
          {{ formatTime(result.zentaoNoteAt) }}
        </span>
      </div>

      <section class="zentao-test-result__section">
        <h3>问题描述</h3>
        <p>
          <TextWithLinks
            :text="result.problemDescription || bug.title"
            @open="openResultUrl"
          />
        </p>
      </section>

      <section class="zentao-test-result__section">
        <h3>修复证明</h3>
        <p><TextWithLinks :text="result.proof" @open="openResultUrl" /></p>
      </section>

      <section class="zentao-test-result__section">
        <header>
          <h3>验证截图</h3>
          <small v-if="result.screenshots.length">
            点击图片可查看原图
          </small>
        </header>
        <div
          v-if="result.screenshots.length"
          class="zentao-test-result__screenshots"
        >
          <button
            v-for="filename in result.screenshots"
            :key="filename"
            type="button"
            :disabled="failedScreenshots.includes(filename)"
            :aria-label="`放大查看验证截图 ${filename}`"
            :title="`放大查看 ${filename}`"
            @click="openScreenshot(filename)"
          >
            <img
              v-if="!failedScreenshots.includes(filename)"
              :src="evidenceUrl(filename)"
              :alt="`Bug #${bug.remoteId} 测试截图 ${filename}`"
              loading="lazy"
              decoding="async"
              @error="markFailed(filename)"
            />
            <span v-else class="zentao-test-result__screenshot-fallback">
              <CircleAlert :size="18" />
              {{ filename }} 无法加载
            </span>
            <span class="zentao-test-result__zoom" aria-hidden="true">
              <ZoomIn :size="16" />
            </span>
          </button>
        </div>
        <div v-else class="zentao-test-result__empty">
          <Camera :size="18" />
          <span>本次测试没有生成截图，暂时不能作为视觉修复证据。</span>
        </div>
      </section>
    </div>

    <template #footer>
      <UiButton
        v-if="result.status === 'passed'"
        variant="primary"
        size="lg"
        :loading="publishingNote"
        @click="emit('syncNote')"
      >
        <MessageSquareText v-if="!publishingNote" :size="14" />
        {{
          result.zentaoNoteAt
            ? '重新同步测试说明'
            : '同步测试说明到禅道'
        }}
      </UiButton>
      <UiButton size="lg" @click="emit('close')">关闭</UiButton>
    </template>
  </UiDialog>

  <UiDialog
    v-if="previewFilename"
    title="验证截图预览"
    :description="previewFilename"
    size="extra-wide"
    height="tall"
    scroll-mode="body"
    max-height="min(900px, calc(100dvh - 24px))"
    @close="closeScreenshotPreview"
  >
    <div
      ref="previewElement"
      class="zentao-test-result__preview"
      :class="{
        'is-zoomed': previewZoomed,
        'is-dragging': previewDragging,
      }"
      @pointerdown="startPreviewDrag"
      @pointermove="movePreviewDrag"
      @pointerup="endPreviewDrag"
      @pointercancel="endPreviewDrag"
    >
      <img
        :src="previewUrl"
        :alt="`Bug #${bug.remoteId} 验证截图 ${previewFilename}`"
        :title="previewZoomed ? '适应窗口' : '查看原始尺寸'"
        decoding="async"
        draggable="false"
        @click="handlePreviewClick"
        @dragstart.prevent
        @error="handlePreviewError"
      />
    </div>
    <template #footer>
      <UiButton size="lg" @click="togglePreviewZoom">
        <Minimize2 v-if="previewZoomed" :size="14" />
        <ZoomIn v-else :size="14" />
        {{ previewZoomed ? '适应窗口' : '原始尺寸' }}
      </UiButton>
      <UiButton size="lg" @click="closeScreenshotPreview">关闭预览</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.zentao-test-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-5);
}

.zentao-test-result__summary {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.zentao-test-result__summary-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 50%;
  place-items: center;
}

.zentao-test-result__summary--passed
  .zentao-test-result__summary-icon {
  color: var(--system-green);
  background: color-mix(
    in srgb,
    var(--system-green) 11%,
    transparent
  );
}

.zentao-test-result__summary--failed
  .zentao-test-result__summary-icon {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 9%,
    transparent
  );
}

.zentao-test-result__summary--blocked
  .zentao-test-result__summary-icon {
  color: var(--system-orange);
  background: color-mix(
    in srgb,
    var(--system-orange) 10%,
    transparent
  );
}

.zentao-test-result__summary strong {
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.zentao-test-result__summary p {
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: 1.65;
  white-space: pre-wrap;
}

.zentao-test-result__meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px;
  grid-template-areas:
    "environment environment"
    "time screenshots";
  gap: var(--space-2);
  margin: 0;
}

.zentao-test-result__meta > div {
  min-width: 0;
  padding: var(--space-3);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.zentao-test-result__environment {
  grid-area: environment;
  background: var(--surface-inset) !important;
}

.zentao-test-result__time {
  grid-area: time;
}

.zentao-test-result__shot-count {
  grid-area: screenshots;
}

.zentao-test-result__meta dt {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-test-result__meta dt > svg {
  flex: 0 0 auto;
  color: var(--system-blue);
}

.zentao-test-result__meta dd {
  margin: var(--space-1) 0 0;
  color: var(--text);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  overflow-wrap: anywhere;
}

.zentao-test-result__environment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-2) var(--space-5);
  margin-top: var(--space-2);
}

.zentao-test-result__environment-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  padding-left: var(--space-3);
  border-left: 1px solid var(--line-soft);
}

.zentao-test-result__environment-item small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-test-result__environment-item
  :deep(.text-with-links__url) {
  font-weight: var(--weight-emphasis);
}

.zentao-test-result__note-status {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  color: var(--system-green);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-green) 9%,
    transparent
  );
  border-radius: var(--radius-control);
}

.zentao-test-result__note-status--error {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 9%,
    transparent
  );
}

.zentao-test-result__section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-2);
}

.zentao-test-result__section > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.zentao-test-result__section h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-test-result__section > p {
  margin: 0;
  padding: var(--space-3);
  color: var(--text);
  font-size: var(--font-body);
  line-height: 1.72;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--canvas);
  border-left: 3px solid var(--line);
  border-radius: var(--radius-control);
}

.zentao-test-result__section > header small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-test-result__screenshots {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.zentao-test-result__screenshots button {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  padding: 4px;
  overflow: hidden;
  color: var(--text-muted);
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  cursor: zoom-in;
}

.zentao-test-result__screenshots button:hover:not(:disabled) {
  border-color: var(--system-blue);
}

.zentao-test-result__screenshots button:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.zentao-test-result__screenshots button:disabled {
  cursor: default;
}

.zentao-test-result__screenshots img {
  display: block;
  max-width: 100%;
  max-height: 520px;
  object-fit: contain;
  border-radius: 5px;
}

.zentao-test-result__screenshot-fallback {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  text-align: left;
}

.zentao-test-result__zoom {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  width: var(--control-md);
  height: var(--control-md);
  color: #ffffff;
  background: rgb(0 0 0 / 58%);
  border-radius: var(--radius-control);
  place-items: center;
  pointer-events: none;
}

.zentao-test-result__preview {
  display: flex;
  min-height: 0;
  height: 100%;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  overflow: hidden;
  background: var(--canvas);
}

.zentao-test-result__preview img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  box-shadow: var(--shadow-panel);
  cursor: zoom-in;
}

.zentao-test-result__preview.is-zoomed {
  align-items: flex-start;
  justify-content: flex-start;
  overflow: auto;
  cursor: grab;
  overscroll-behavior: contain;
  touch-action: none;
}

.zentao-test-result__preview.is-zoomed img {
  width: auto;
  height: auto;
  max-width: none;
  max-height: none;
  flex: 0 0 auto;
  cursor: grab;
}

.zentao-test-result__preview.is-dragging,
.zentao-test-result__preview.is-dragging img {
  cursor: grabbing;
}

.zentao-test-result__empty {
  display: flex;
  min-height: 110px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  background: var(--canvas);
  border: 1px dashed var(--line);
  border-radius: var(--radius-control);
}

@media (max-width: 760px) {
  .zentao-test-result {
    padding: 0 var(--space-4) var(--space-4);
  }

  .zentao-test-result__meta,
  .zentao-test-result__screenshots {
    grid-template-columns: 1fr;
  }

  .zentao-test-result__meta {
    grid-template-areas:
      "environment"
      "time"
      "screenshots";
  }

  .zentao-test-result__preview {
    padding: var(--space-2);
  }
}
</style>
