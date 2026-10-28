<script setup lang="ts">
import { Clock3, TerminalSquare } from '@lucide/vue'
import { computed, ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import type {
  DeploymentConfig,
  DeploymentRun,
} from '@/types/deployment'

const props = defineProps<{
  deployment: DeploymentConfig
  runs: DeploymentRun[]
}>()

const emit = defineEmits<{
  close: []
}>()

const orderedRuns = computed(() =>
  [...props.runs].sort((left, right) =>
    right.startedAt.localeCompare(left.startedAt),
  ),
)
const selectedRunId = ref(orderedRuns.value[0]?.id ?? '')
const selectedRun = computed(
  () =>
    orderedRuns.value.find((run) => run.id === selectedRunId.value) ??
    orderedRuns.value[0] ??
    null,
)

function statusLabel(run: DeploymentRun) {
  if (run.status === 'SUCCESS') return '成功'
  if (run.status === 'TIMEOUT') return '超时'
  return '失败'
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
</script>

<template>
  <UiDialog
    :title="`${deployment.name} · 执行记录`"
    description="记录最近一次部署脚本在服务器上的执行结果和输出。"
    size="wide"
    height="tall"
    initial-focus=".deployment-runs__close"
    @close="emit('close')"
  >
    <div class="deployment-runs">
      <div v-if="orderedRuns.length > 0" class="deployment-runs__body">
        <div class="deployment-runs__list">
          <button
            v-for="run in orderedRuns"
            :key="run.id"
            type="button"
            :class="{
              'deployment-runs__item--active': selectedRun?.id === run.id,
            }"
            @click="selectedRunId = run.id"
          >
            <span
              class="deployment-runs__status"
              :class="`deployment-runs__status--${run.status.toLowerCase()}`"
            >
              {{ statusLabel(run) }}
            </span>
            <span class="deployment-runs__summary">
              <strong>{{ run.summary }}</strong>
              <small>
                <Clock3 :size="11" />
                {{ formatTime(run.startedAt) }}
              </small>
            </span>
          </button>
        </div>

        <div v-if="selectedRun" class="deployment-runs__detail">
          <div class="deployment-runs__detail-heading">
            <span>
              <TerminalSquare :size="14" />
              执行输出
            </span>
            <small>
              {{ formatTime(selectedRun.startedAt) }} –
              {{ formatTime(selectedRun.finishedAt) }}
            </small>
          </div>
          <pre>{{ selectedRun.output || '脚本没有输出。' }}</pre>
        </div>
      </div>

      <div v-else class="deployment-runs__empty">
        <TerminalSquare :size="24" />
        <strong>还没有执行记录</strong>
        <p>点击部署列表中的运行按钮开始第一次部署。</p>
      </div>

    </div>

    <template #footer>
      <UiButton
        class="deployment-runs__close"
        size="lg"
        @click="emit('close')"
      >
        关闭
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.deployment-runs {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 var(--space-5);
}

.deployment-runs__body {
  display: flex;
  min-height: 360px;
  flex: 1;
  flex-direction: column;
  gap: var(--space-2);
  overflow: hidden;
}

.deployment-runs__list {
  display: flex;
  max-height: 150px;
  flex-direction: column;
  overflow-y: auto;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.deployment-runs__list > button {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  text-align: left;
  background: transparent;
  border-bottom: 1px solid var(--line-soft);
}

.deployment-runs__list > button:last-child {
  border-bottom: 0;
}

.deployment-runs__list > button:hover,
.deployment-runs__item--active {
  background: var(--hover-bg) !important;
}

.deployment-runs__status {
  min-width: 34px;
  padding: 3px 6px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
  text-align: center;
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
}

.deployment-runs__status--success {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 10%, transparent);
}

.deployment-runs__status--failed {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 9%, transparent);
}

.deployment-runs__status--timeout {
  color: var(--system-orange);
  background: color-mix(in srgb, var(--system-orange) 10%, transparent);
}

.deployment-runs__summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.deployment-runs__summary strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deployment-runs__summary small {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.deployment-runs__detail {
  display: flex;
  min-height: 220px;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  background: #11151b;
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: var(--radius-panel);
}

.deployment-runs__detail-heading {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  color: #c6d0da;
  font-size: var(--font-caption);
  background: rgb(255 255 255 / 4%);
  border-bottom: 1px solid rgb(255 255 255 / 7%);
}

.deployment-runs__detail-heading > span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.deployment-runs__detail-heading small {
  color: #7d8996;
  font-family: var(--font-mono);
  font-size: 11px;
}

.deployment-runs__detail pre {
  min-height: 0;
  flex: 1;
  padding: var(--space-3);
  margin: 0;
  overflow: auto;
  color: #d7dde5;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.deployment-runs__empty {
  display: flex;
  min-height: 300px;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  text-align: center;
  flex-direction: column;
}

.deployment-runs__empty strong {
  margin-top: var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
}

.deployment-runs__empty p {
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .deployment-runs {
    padding: 0 var(--space-4);
  }
}
</style>
