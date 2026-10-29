<script setup lang="ts">
import {
  FileCode2,
  RefreshCw,
  TriangleAlert,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import { getGitFileContent } from '@/api/gitRepositories'
import type { GitFileContent } from '@/types/gitRepository'

const props = defineProps<{
  repositoryPath: string
  revision: string
  filePath: string
  line?: number | null
}>()

const emit = defineEmits<{
  close: []
}>()

const loading = ref(false)
const error = ref('')
const content = ref<GitFileContent | null>(null)
const viewport = ref<HTMLElement | null>(null)
let requestSequence = 0

const shortRevision = computed(
  () => props.revision.slice(0, 10),
)
const locationLabel = computed(() => {
  const target = content.value?.requestedLine
  return target ? `第 ${target} 行已高亮` : '未定位到指定行'
})
const rangeLabel = computed(() => {
  const value = content.value
  if (!value) return ''
  if (value.totalLines === 0) return '空文件'
  return `显示第 ${value.startLine}-${value.endLine} 行，共 ${value.totalLines} 行`
})
const truncated = computed(
  () =>
    Boolean(
      content.value?.truncatedBefore ||
        content.value?.truncatedAfter,
    ),
)

function lineNumber(index: number) {
  return (content.value?.startLine ?? 1) + index
}

function isTargetLine(index: number) {
  return (
    content.value?.requestedLine === lineNumber(index)
  )
}

async function scrollToTarget() {
  await nextTick()
  const target = viewport.value?.querySelector<HTMLElement>(
    '[data-target-line="true"]',
  )
  if (!target || !viewport.value) return
  viewport.value.scrollTop = Math.max(
    0,
    target.offsetTop -
      viewport.value.clientHeight / 2 +
      target.offsetHeight / 2,
  )
}

async function loadContent() {
  const sequence = ++requestSequence
  loading.value = true
  error.value = ''
  content.value = null
  try {
    const result = await getGitFileContent({
      path: props.repositoryPath,
      revision: props.revision,
      filePath: props.filePath,
      line: props.line,
    })
    if (sequence !== requestSequence) return
    content.value = result
    await scrollToTarget()
  } catch (loadError) {
    if (sequence !== requestSequence) return
    error.value =
      loadError instanceof Error
        ? loadError.message
        : '无法读取文件内容'
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
    }
  }
}

watch(
  () => [
    props.repositoryPath,
    props.revision,
    props.filePath,
    props.line,
  ],
  () => void loadContent(),
  { immediate: true },
)
</script>

<template>
  <UiDialog
    title="文件内容"
    :description="filePath"
    size="extra-wide"
    height="tall"
    @close="emit('close')"
  >
    <section class="code-review-file">
      <header class="code-review-file__header">
        <div>
          <FileCode2 :size="16" />
          <code>{{ filePath }}{{ line ? `:${line}` : '' }}</code>
        </div>
        <span>
          提交 {{ shortRevision }} · {{ locationLabel }}
        </span>
      </header>

      <div
        v-if="loading"
        class="code-review-file__state"
        role="status"
      >
        <UiFidgetSpinner :size="18" />
        <strong>正在读取提交中的文件内容</strong>
        <span v-if="rangeLabel">{{ rangeLabel }}</span>
      </div>

      <div
        v-else-if="error"
        class="code-review-file__state code-review-file__state--error"
        role="alert"
      >
        <TriangleAlert :size="20" />
        <strong>无法打开文件</strong>
        <span>{{ error }}</span>
        <UiButton size="sm" @click="loadContent">
          <RefreshCw :size="13" />
          重试
        </UiButton>
      </div>

      <template v-else-if="content">
        <div class="code-review-file__meta">
          <span>{{ rangeLabel }}</span>
          <span v-if="truncated">
            为避免读取超大文件，仅展示目标行附近上下文
          </span>
        </div>
        <div
          v-if="content.lines.length"
          ref="viewport"
          class="code-review-file__viewport"
          role="region"
          :aria-label="`${filePath} 文件内容`"
          tabindex="0"
        >
          <div
            v-for="(lineContent, index) in content.lines"
            :key="lineNumber(index)"
            class="code-review-file__line"
            :class="{
              'code-review-file__line--target':
                isTargetLine(index),
            }"
            :data-target-line="isTargetLine(index)"
          >
            <span>{{ lineNumber(index) }}</span>
            <code>{{ lineContent || ' ' }}</code>
          </div>
        </div>
        <div v-else class="code-review-file__state">
          <FileCode2 :size="20" />
          <strong>这是一个空文件</strong>
          <span>提交 {{ shortRevision }} 中没有可显示的内容</span>
        </div>
      </template>
    </section>

    <template #footer>
      <UiButton @click="emit('close')">关闭</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.code-review-file {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 var(--space-5) var(--space-4);
}

.code-review-file__header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-file__header > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  color: var(--accent);
}

.code-review-file__header code {
  overflow: hidden;
  color: var(--accent);
  font-family: var(--font-mono);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-review-file__header > span {
  flex: 0 0 auto;
  font-family: var(--font-mono);
}

.code-review-file__meta {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.code-review-file__viewport {
  min-height: 0;
  flex: 1;
  overflow: auto;
  background: var(--surface-inset);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
  overscroll-behavior: contain;
}

.code-review-file__line {
  display: grid;
  width: max-content;
  min-width: 100%;
  grid-template-columns: 54px minmax(max-content, 1fr);
  min-height: 21px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  line-height: 21px;
}

.code-review-file__line > span {
  position: sticky;
  left: 0;
  z-index: 1;
  padding: 0 var(--space-2);
  color: var(--text-subtle);
  text-align: right;
  user-select: none;
  background: var(--surface-inset);
  border-right: 1px solid var(--line-soft);
}

.code-review-file__line > code {
  padding: 0 var(--space-3);
  white-space: pre;
}

.code-review-file__line--target,
.code-review-file__line--target > span {
  color: var(--text);
  background: color-mix(
    in srgb,
    var(--system-orange) 13%,
    var(--surface-inset)
  );
}

.code-review-file__line--target > span {
  color: var(--system-orange);
  font-weight: var(--weight-emphasis);
}

.code-review-file__state {
  display: flex;
  min-height: 220px;
  flex: 1;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-subtle);
  text-align: center;
}

.code-review-file__state > svg {
  color: var(--accent);
}

.code-review-file__state strong {
  color: var(--text);
  font-size: var(--font-body);
}

.code-review-file__state span {
  max-width: 520px;
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.code-review-file__state--error > svg,
.code-review-file__state--error strong {
  color: var(--system-red);
}

@media (max-width: 640px) {
  .code-review-file {
    padding: 0 var(--space-3) var(--space-3);
  }

  .code-review-file__header,
  .code-review-file__meta {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-1);
  }

  .code-review-file__line {
    grid-template-columns: 44px minmax(max-content, 1fr);
  }
}
</style>
