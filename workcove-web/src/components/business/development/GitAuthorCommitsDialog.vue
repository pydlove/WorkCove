<script setup lang="ts">
import {
  CircleAlert,
  GitCommitHorizontal,
  RefreshCw,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import { listGitAuthorCommits } from '@/api/gitRepositories'
import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiSearchInput from '@/components/ui/UiSearchInput.vue'
import type {
  GitAuthorStatistics,
  GitCommitInfo,
  GitRepository,
} from '@/types/gitRepository'

const props = defineProps<{
  repository: GitRepository
  author: GitAuthorStatistics
}>()

const emit = defineEmits<{
  close: []
}>()

const loading = ref(false)
const error = ref('')
const commits = ref<GitCommitInfo[]>([])
const searchQuery = ref('')

const filteredCommits = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return commits.value
  return commits.value.filter((commit) =>
    [
      commit.subject,
      commit.hash,
      commit.shortHash,
      commit.authorName,
      commit.authorEmail,
    ]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})

onMounted(loadCommits)

async function loadCommits() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    commits.value = await listGitAuthorCommits(
      props.repository.path,
      props.author.name,
      props.author.email,
    )
  } catch (cause) {
    error.value =
      cause instanceof Error ? cause.message : '无法读取作者提交记录'
  } finally {
    loading.value = false
  }
}

function formatCommitTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <UiDialog
    title="作者提交记录"
    :description="`${author.name}${author.email ? ` · ${author.email}` : ''} · 近一年`"
    size="wide"
    height="tall"
    scroll-mode="body"
    @close="emit('close')"
  >
    <div class="git-author-commits">
      <div class="git-author-commits__summary">
        <span>
          <GitCommitHorizontal :size="15" />
          共
          {{
            (loading ? author.commits : commits.length).toLocaleString(
              'zh-CN',
            )
          }}
          次有效提交
        </span>
        <span class="git-author-commits__changes">
          <span class="is-additions">
            +{{ author.additions.toLocaleString('zh-CN') }}
          </span>
          <span class="is-deletions">
            -{{ author.deletions.toLocaleString('zh-CN') }}
          </span>
        </span>
      </div>

      <UiSearchInput
        v-if="!loading && !error && commits.length > 0"
        v-model="searchQuery"
        label="搜索作者提交记录"
        placeholder="搜索提交说明、Hash 或作者信息"
      />

      <div v-if="loading" class="git-author-commits__state">
        <UiFidgetSpinner :size="22" />
        <strong>正在读取作者提交记录…</strong>
        <span>提交较多时可能需要几秒钟</span>
      </div>

      <div
        v-else-if="error"
        class="git-author-commits__state is-error"
      >
        <CircleAlert :size="22" />
        <strong>读取失败</strong>
        <span>{{ error }}</span>
        <UiButton size="sm" :loading="loading" @click="loadCommits">
          <RefreshCw v-if="!loading" :size="13" />
          重试
        </UiButton>
      </div>

      <div
        v-else-if="filteredCommits.length"
        class="git-author-commits__list"
      >
        <article
          v-for="commit in filteredCommits"
          :key="commit.hash"
          class="git-author-commits__row"
        >
          <span class="git-author-commits__icon">
            <GitCommitHorizontal :size="15" />
          </span>
          <div class="git-author-commits__copy">
            <strong>{{ commit.subject }}</strong>
            <div>
              <code :title="commit.hash">{{ commit.shortHash }}</code>
              <span>{{ formatCommitTime(commit.authoredAt) }}</span>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="git-author-commits__state">
        <GitCommitHorizontal :size="22" />
        <strong>
          {{ searchQuery.trim() ? '没有匹配的提交' : '没有提交记录' }}
        </strong>
        <span>
          {{
            searchQuery.trim()
              ? '换一个提交说明或 Hash 再试。'
              : '该作者近一年没有有效提交。'
          }}
        </span>
      </div>
    </div>

    <template #footer>
      <UiButton size="lg" @click="emit('close')">
        关闭
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.git-author-commits {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.git-author-commits__summary {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.git-author-commits__summary > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.git-author-commits__changes {
  font-family: var(--font-mono);
}

.git-author-commits__changes .is-additions {
  color: var(--system-green);
}

.git-author-commits__changes .is-deletions {
  color: var(--system-red);
}

.git-author-commits__list {
  display: flex;
  min-width: 0;
  flex-direction: column;
  border-top: 1px solid var(--line-soft);
}

.git-author-commits__row {
  display: grid;
  min-width: 0;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.git-author-commits__icon {
  display: grid;
  width: 24px;
  height: 24px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-badge);
  place-items: center;
}

.git-author-commits__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-1);
}

.git-author-commits__copy > strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  line-height: var(--line-body);
  overflow-wrap: anywhere;
}

.git-author-commits__copy > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-author-commits__copy code {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.git-author-commits__state {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  text-align: center;
  flex-direction: column;
  gap: var(--space-2);
}

.git-author-commits__state > svg {
  color: var(--accent);
}

.git-author-commits__state strong {
  color: var(--text);
  font-size: var(--font-title);
}

.git-author-commits__state span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.git-author-commits__state.is-error > svg {
  color: var(--system-red);
}

@media (max-width: 520px) {
  .git-author-commits {
    padding: var(--space-3) var(--space-4) var(--space-4);
  }

  .git-author-commits__summary {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
