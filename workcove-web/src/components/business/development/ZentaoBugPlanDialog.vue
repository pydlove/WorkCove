<script setup lang="ts">
import {
  Bot,
  Bug,
  Clock3,
  ListChecks,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import type { ZentaoBug } from '@/types/zentao'

const props = defineProps<{
  bugs: ZentaoBug[]
}>()

const emit = defineEmits<{
  close: []
  submit: [plan: string]
}>()

const defaultPlan =
  '请按 Bug 的优先级和严重程度逐项处理。每个 Bug 都先定位根因，再完成代码修复和必要验证；优先复用现有实现，避免无关重构。若多个 Bug 存在关联，请一并说明处理关系。' +
  '任务完成后，必须为每个 Bug 分别提供“解决方案”和“定位原因”，平台会自动回填到 Bug 清单。解决方案需包含修复内容、涉及模块和验证结果；定位原因必须是可核验的根因，不能只写“已修复”或“逻辑问题”。'
const plan = ref(defaultPlan)
const error = ref('')

const projectCount = computed(
  () =>
    new Set(
      props.bugs
        .map((bug) => bug.projectName)
        .filter(Boolean),
    ).size,
)

function submitPlan() {
  const value = plan.value.trim()
  if (!value) {
    error.value = '请填写本次解决计划'
    return
  }
  error.value = ''
  emit('submit', value)
}
</script>

<template>
  <UiDialog
    title="设置一次性解决计划"
    :description="`已选择 ${bugs.length} 个 Bug，将作为一笔 Codex 任务统一处理。`"
    size="wide"
    height="tall"
    scroll-mode="body"
    max-height="min(740px, calc(100dvh - 32px))"
    initial-focus="#zentao-batch-plan"
    @close="emit('close')"
  >
    <form
      class="zentao-plan-dialog"
      @submit.prevent="submitPlan"
    >
      <section class="zentao-plan-summary">
        <span class="zentao-plan-summary__icon">
          <ListChecks :size="18" />
        </span>
        <div>
          <strong>{{ bugs.length }} 个 Bug</strong>
          <small>
            {{
              projectCount > 0
                ? `来自 ${projectCount} 个项目`
                : '未关联项目'
            }}
          </small>
        </div>
        <span class="zentao-plan-once">
          <Clock3 :size="12" />
          仅本次
        </span>
      </section>

      <section class="zentao-plan-bugs">
        <header>
          <h3>本次处理范围</h3>
          <small>Codex 会收到以下 Bug 的完整详情</small>
        </header>
        <ul>
          <li v-for="bug in bugs" :key="bug.id">
            <span class="zentao-plan-bug-icon">
              <Bug :size="13" />
            </span>
            <span class="zentao-plan-bug-copy">
              <strong>
                #{{ bug.remoteId }} · {{ bug.title }}
              </strong>
              <small>
                P{{ bug.priority || '-' }}
                · {{ bug.productName || '未知产品' }}
                <template v-if="bug.projectName">
                  · {{ bug.projectName }}
                </template>
              </small>
            </span>
          </li>
        </ul>
      </section>

      <label class="zentao-plan-field" for="zentao-batch-plan">
        <span>
          <strong>解决计划</strong>
          <small>可补充处理顺序、验收要求或本次修复边界</small>
        </span>
        <textarea
          id="zentao-batch-plan"
          v-model="plan"
          rows="7"
          maxlength="4000"
          placeholder="填写本次批量修复计划"
        />
        <small>
          这段内容只用于当前 Codex 任务，不会保存为长期模板。
        </small>
      </label>

      <p v-if="error" class="zentao-plan-error" role="alert">
        {{ error }}
      </p>
    </form>

    <template #footer>
      <UiButton size="lg" @click="emit('close')">取消</UiButton>
      <UiButton
        variant="primary"
        size="lg"
        @click="submitPlan"
      >
        <Bot :size="14" />
        交给 Codex
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.zentao-plan-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-5);
}

.zentao-plan-summary {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.zentao-plan-summary__icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.zentao-plan-summary > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.zentao-plan-summary strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-plan-summary small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-plan-once {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-1);
  padding: 2px 6px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--category-chip-bg);
  border-radius: var(--radius-badge);
}

.zentao-plan-bugs {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.zentao-plan-bugs > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.zentao-plan-bugs h3,
.zentao-plan-field strong {
  margin: 0;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.zentao-plan-bugs header small,
.zentao-plan-field > span small,
.zentao-plan-field > small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-plan-bugs ul {
  padding: 0;
  margin: 0;
  list-style: none;
  border-top: 1px solid var(--line-soft);
}

.zentao-plan-bugs li {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--line-soft);
}

.zentao-plan-bug-icon {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  color: var(--icon);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
  place-items: center;
}

.zentao-plan-bug-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.zentao-plan-bug-copy strong,
.zentao-plan-bug-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-plan-bug-copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-plan-bug-copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-plan-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.zentao-plan-field > span {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.zentao-plan-field textarea {
  min-height: 132px;
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

.zentao-plan-field textarea:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
  outline: none;
}

.zentao-plan-error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .zentao-plan-dialog {
    padding: 0 var(--space-4) var(--space-4);
  }

  .zentao-plan-bugs > header,
  .zentao-plan-field > span {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }
}
</style>
