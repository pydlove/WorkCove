<script setup lang="ts">
import {
  Camera,
  CircleCheck,
  CircleAlert,
  CircleX,
  Clock3,
  Monitor,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import { apiUrl } from '@/api/http'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import type { ZentaoBug } from '@/types/zentao'
import type { ZentaoTestResult } from '@/types/zentaoTest'

const props = defineProps<{
  bug: ZentaoBug
  result: ZentaoTestResult
}>()

const emit = defineEmits<{
  close: []
}>()

const failedScreenshots = ref<string[]>([])

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

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
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
  window.open(
    evidenceUrl(filename),
    '_blank',
    'noopener,noreferrer',
  )
}
</script>

<template>
  <UiDialog
    :title="`Bug #${bug.remoteId} · Codex 测试结果`"
    :description="bug.title"
    size="extra-wide"
    height="tall"
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
          <p>{{ result.proof }}</p>
        </div>
      </section>

      <dl class="zentao-test-result__meta">
        <div>
          <dt><Monitor :size="13" /> 测试环境</dt>
          <dd>{{ result.environment || '未说明测试环境' }}</dd>
        </div>
        <div>
          <dt><Clock3 :size="13" /> 测试时间</dt>
          <dd>{{ formatTime(result.createdAt) }}</dd>
        </div>
        <div>
          <dt><Camera :size="13" /> 测试截图</dt>
          <dd>{{ result.screenshots.length }} 张</dd>
        </div>
      </dl>

      <section class="zentao-test-result__section">
        <h3>问题描述</h3>
        <p>{{ result.problemDescription || bug.title }}</p>
      </section>

      <section class="zentao-test-result__section">
        <h3>修复证明</h3>
        <p>{{ result.proof }}</p>
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
            <span v-else>
              <CircleAlert :size="18" />
              {{ filename }} 无法加载
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
      <UiButton size="lg" @click="emit('close')">关闭</UiButton>
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
  grid-template-columns: minmax(0, 1.4fr) minmax(150px, 0.7fr) 110px;
  gap: var(--space-2);
  margin: 0;
}

.zentao-test-result__meta > div {
  min-width: 0;
  padding: var(--space-3);
  background: var(--canvas);
  border-radius: var(--radius-control);
}

.zentao-test-result__meta dt {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-test-result__meta dd {
  margin: var(--space-1) 0 0;
  color: var(--text);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  overflow-wrap: anywhere;
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
}

.zentao-test-result__screenshots button:hover {
  border-color: var(--system-blue);
}

.zentao-test-result__screenshots img {
  display: block;
  max-width: 100%;
  max-height: 520px;
  object-fit: contain;
  border-radius: 5px;
}

.zentao-test-result__screenshots button > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  text-align: left;
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
}
</style>
