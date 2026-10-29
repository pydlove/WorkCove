<script setup lang="ts">
import {
  ArrowRight,
  CalendarDays,
  GitBranch,
  RefreshCw,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import { getGitReviewContext } from '@/api/gitRepositories'
import { listGitBranches } from '@/api/gitRepositories'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import type {
  GitBranchInfo,
  GitRepository,
  GitReviewContext,
} from '@/types/gitRepository'
import type { CodeReviewRecord } from '@/types/codeReview'

export interface CodeReviewStartPayload {
  repository: GitRepository
  branch: GitBranchInfo
  baseBranch: GitBranchInfo
  context: GitReviewContext
  focus: string
  iteration: number
  previousReview: CodeReviewRecord | null
  incremental: boolean
  since: string
  until: string
}

const props = defineProps<{
  repositories: GitRepository[]
  previousReview?: CodeReviewRecord | null
  reviews: CodeReviewRecord[]
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: CodeReviewStartPayload]
}>()

const repositoryId = ref('')
const branchRef = ref('')
const baseBranchRef = ref('')
const focus = ref('')
const incremental = ref(false)
const rangeMode = ref<ReviewRangeMode>('today')
const since = ref(today())
const until = ref(today())
const branches = ref<GitBranchInfo[]>([])
const loadingBranches = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

type ReviewRangeMode = 'today' | 'last3' | 'custom'

const rangeOptions = [
  { value: 'today', label: '今天' },
  { value: 'last3', label: '近 3 天' },
  { value: 'custom', label: '自定义' },
]

const repositoryOptions = computed(() =>
  props.repositories
    .filter(
      (repository) =>
        repository.source === 'local' && repository.path,
    )
    .map((repository) => ({
      value: repository.id,
      label: repository.name,
      description: `${repository.category} · ${repository.path}`,
    })),
)

const selectedRepository = computed(
  () =>
    props.repositories.find(
      (repository) => repository.id === repositoryId.value,
    ) ?? null,
)

const branchOptions = computed(() =>
  branches.value.map((branch) => ({
    value: branch.refName,
    label: branch.name,
    description: [
      branch.remote ? '远端分支' : '本地分支',
      branch.current ? '当前分支' : '',
      branch.upstream ? `跟踪 ${branch.upstream}` : '',
    ]
      .filter(Boolean)
      .join(' · '),
  })),
)

const baseBranchOptions = computed(() =>
  branchOptions.value.filter(
    (option) => option.value !== branchRef.value,
  ),
)

const selectedBranch = computed(
  () =>
    branches.value.find(
      (branch) => branch.refName === branchRef.value,
    ) ?? null,
)

const selectedBaseBranch = computed(
  () =>
    branches.value.find(
      (branch) => branch.refName === baseBranchRef.value,
    ) ?? null,
)

const previousMatches = computed(
  () =>
    props.reviews.filter(
      (review) =>
        review.repositoryId === repositoryId.value &&
        review.branch === branchRef.value &&
        (!selectedBaseBranch.value ||
          review.baseBranch === selectedBaseBranch.value.name),
    ),
)

const completedPreviousReviews = computed(() =>
  previousMatches.value.filter((review) => isReviewBaseline(review)),
)

const latestPreviousReview = computed(
  () =>
    [...completedPreviousReviews.value].sort(
      (left, right) =>
        new Date(right.updatedAt).getTime() -
        new Date(left.updatedAt).getTime(),
    )[0] ??
    (isReviewBaseline(props.previousReview)
      ? props.previousReview
      : null),
)

const iteration = computed(
  () =>
    Math.max(
      0,
      ...previousMatches.value.map((review) => review.iteration),
    ) + 1,
)

const reviewRangeLabel = computed(() => {
  if (rangeMode.value === 'today') return '今天'
  if (rangeMode.value === 'last3') return '近 3 天'
  if (!since.value && !until.value) return '全部提交'
  if (!until.value || until.value === since.value) return since.value
  return `${since.value} 至 ${until.value}`
})

watch(
  repositoryId,
  () => {
    branchRef.value = ''
    baseBranchRef.value = ''
    branches.value = []
    incremental.value = false
    void loadBranches()
  },
)

watch(repositoryOptions, (options) => {
  if (options.some((option) => option.value === repositoryId.value)) {
    return
  }
  repositoryId.value = options[0]?.value ?? ''
})

watch(branchRef, () => {
  incremental.value = Boolean(latestPreviousReview.value)
  if (
    baseBranchRef.value &&
    baseBranchRef.value !== branchRef.value
  ) {
    return
  }
  baseBranchRef.value = defaultBaseBranch()
})

watch(rangeMode, (mode) => {
  applyRangeMode(mode)
})

onMounted(() => {
  const previousRepository = props.previousReview?.repositoryId
  repositoryId.value =
    previousRepository &&
    props.repositories.some(
      (repository) =>
        repository.id === previousRepository &&
        repository.source === 'local',
    )
      ? previousRepository
      : repositoryOptions.value[0]?.value ?? ''
  if (props.previousReview?.focus) {
    focus.value = props.previousReview.focus
  }
})

async function loadBranches() {
  const repository = selectedRepository.value
  if (!repository?.path) return
  loadingBranches.value = true
  errorMessage.value = ''
  try {
    branches.value = await listGitBranches(repository.path)
    const previousBranch = props.previousReview?.branch
    const featureBranches = branches.value.filter(
      (branch) => !isBaseBranch(branch),
    )
    branchRef.value =
      branches.value.find((branch) => branch.refName === previousBranch)
        ?.refName ??
      featureBranches.find((branch) => !branch.current)?.refName ??
      featureBranches[0]?.refName ??
      branches.value.find((branch) => !branch.current)?.refName ??
      branches.value[0]?.refName ??
      ''
    baseBranchRef.value = defaultBaseBranch()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法读取仓库分支'
  } finally {
    loadingBranches.value = false
  }
}

function isBaseBranch(branch: GitBranchInfo) {
  return /^(?:origin\/)?(develop|development|main|master)$/i.test(
    branch.name,
  )
}

function setRangeMode(value: string) {
  rangeMode.value = value as ReviewRangeMode
}

function applyRangeMode(mode: ReviewRangeMode) {
  if (mode === 'today') {
    since.value = today()
    until.value = today()
    return
  }
  if (mode === 'last3') {
    since.value = dateBefore(2)
    until.value = today()
  }
}

function isReviewBaseline(review: CodeReviewRecord | null | undefined) {
  return Boolean(
    review?.reviewedAt &&
      !['reviewing', 'failed', 'repairing'].includes(review.status),
  )
}

function defaultBaseBranch() {
  const candidates = [
    'refs/heads/develop',
    'refs/remotes/origin/develop',
    'refs/heads/main',
    'refs/remotes/origin/main',
    'refs/heads/master',
    'refs/remotes/origin/master',
  ]
  return (
    candidates.find((candidate) =>
      branches.value.some((branch) => branch.refName === candidate),
    ) ??
    branches.value.find(
      (branch) =>
        branch.refName !== branchRef.value &&
        /develop|main|master/i.test(branch.name),
    )?.refName ??
    branches.value.find(
      (branch) => branch.refName !== branchRef.value,
    )?.refName ??
    ''
  )
}

async function submit() {
  if (submitting.value) return
  errorMessage.value = ''
  const repository = selectedRepository.value
  const branch = selectedBranch.value
  const baseBranch = selectedBaseBranch.value
  if (!repository) {
    errorMessage.value = '请选择 Git 仓库'
    return
  }
  if (!branch) {
    errorMessage.value = '请选择需要评审的成员分支'
    return
  }
  if (!baseBranch) {
    errorMessage.value = '请选择对比分支'
    return
  }
  if (branch.refName === baseBranch.refName) {
    errorMessage.value = '评审分支不能与对比分支相同'
    return
  }
  if (rangeMode.value === 'custom' && !since.value) {
    errorMessage.value = '请选择评审开始日期'
    return
  }
  if (rangeMode.value === 'custom' && !until.value) {
    errorMessage.value = '请选择评审结束日期'
    return
  }
  if (since.value && until.value && until.value < since.value) {
    errorMessage.value = '评审结束日期不能早于开始日期'
    return
  }

  submitting.value = true
  try {
    const previous = incremental.value
      ? latestPreviousReview.value
      : null
    const context = await getGitReviewContext({
      path: repository.path,
      branch: branch.refName,
      baseBranch: baseBranch.refName,
      fromCommit:
        previous && incremental.value
          ? previous.headCommit
          : '',
      since: since.value,
      until: until.value,
    })
    if (context.commitCount === 0) {
      throw new Error(
        incremental.value && previous
          ? `评审范围「${reviewRangeLabel.value}」内没有上次评审之后的新提交，请调整时间范围。`
          : `评审范围「${reviewRangeLabel.value}」内没有提交，请调整时间范围，或确认成员已提交到该分支。`,
      )
    }
    if (context.fileCount === 0) {
      throw new Error(
        previous && incremental.value
          ? '上次评审之后没有新的提交。Codex 修复产生的未提交改动请先提交，再发起复审。'
          : '评审分支相对对比分支没有文件差异，无需发起初审。',
      )
    }
    emit('submit', {
      repository,
      branch,
      baseBranch,
      context,
      focus: focus.value.trim(),
      iteration: iteration.value,
      previousReview: previous,
      incremental: incremental.value,
      since: since.value,
      until: until.value,
    })
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法创建代码评审'
  } finally {
    submitting.value = false
  }
}

function today() {
  return localDate(new Date())
}

function dateBefore(days: number) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return localDate(date)
}

function localDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

<template>
  <UiDialog
    title="发起代码评审"
    description="默认评审今天提交的代码，Codex 只把需要人工判断的内容交给你。"
    size="wide"
    height="tall"
    scroll-mode="body"
    initial-focus="#code-review-repository"
    :suspended="submitting"
    @close="emit('close')"
  >
    <form
      id="code-review-start-form"
      class="code-review-start"
      @submit.prevent="submit"
    >
      <UiFormField
        label="Git 仓库"
        control-id="code-review-repository"
        required
      >
        <UiSelect
          id="code-review-repository"
          v-model="repositoryId"
          :options="repositoryOptions"
          size="lg"
          searchable
          placeholder="选择仓库"
          aria-label="选择代码评审仓库"
        />
      </UiFormField>

      <div class="code-review-start__branches">
        <UiFormField label="评审分支" required>
          <UiSelect
            v-model="branchRef"
            :options="branchOptions"
            size="lg"
            searchable
            :disabled="loadingBranches || branches.length === 0"
            placeholder="选择组员已提交的分支"
            aria-label="选择评审分支"
          />
        </UiFormField>
        <UiFormField label="对比分支" required>
          <UiSelect
            v-model="baseBranchRef"
            :options="baseBranchOptions"
            size="lg"
            searchable
            :disabled="loadingBranches || branches.length === 0"
            placeholder="选择基线分支"
            aria-label="选择代码评审对比分支"
          />
        </UiFormField>
      </div>

      <section class="code-review-start__range">
        <header>
          <span>
            <CalendarDays :size="14" />
            评审时间范围
          </span>
          <UiSegmentedControl
            :model-value="rangeMode"
            :items="rangeOptions"
            size="sm"
            aria-label="选择代码评审时间范围"
            @update:model-value="setRangeMode"
          />
        </header>
        <div
          v-if="rangeMode === 'custom'"
          class="code-review-start__range-dates"
        >
          <UiFormField label="开始日期" required>
            <UiInput v-model="since" type="date" size="lg" />
          </UiFormField>
          <UiFormField label="结束日期" required>
            <UiInput v-model="until" type="date" size="lg" />
          </UiFormField>
        </div>
        <p>
          本次只评审
          <strong>{{ reviewRangeLabel }}</strong>
          内提交的代码；如果范围内没有提交，将不会发起评审。
        </p>
      </section>

      <div class="code-review-start__branch-meta">
        <span>
          <GitBranch :size="14" />
          {{
            selectedBranch
              ? selectedBranch.latestCommit
              : '等待选择分支'
          }}
        </span>
        <UiButton
          size="sm"
          type="button"
          :loading="loadingBranches"
          @click="loadBranches"
        >
          <RefreshCw v-if="!loadingBranches" :size="13" />
          刷新分支
        </UiButton>
      </div>

      <label
        v-if="latestPreviousReview"
        class="code-review-start__incremental"
      >
        <UiCheckbox
          v-model="incremental"
          label="只评审上次之后的新提交"
        />
        <span>
          上次评审停在
          <code>{{ latestPreviousReview.headCommit.slice(0, 8) }}</code>
          ，本次将跳过已评审代码。
        </span>
      </label>

      <UiFormField
        label="本次关注重点"
        hint="可选，例如：重点检查权限、数据库迁移或接口兼容性"
      >
        <UiTextarea
          v-model="focus"
          :rows="4"
          maxlength="2000"
          placeholder="补充本次希望 Codex 重点检查的内容"
        />
      </UiFormField>

      <p v-if="errorMessage" class="code-review-start__error" role="alert">
        {{ errorMessage }}
      </p>
    </form>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="submitting"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="code-review-start-form"
        :loading="submitting"
      >
        <ArrowRight :size="14" />
        开始初审
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.code-review-start {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-5);
}

.code-review-start__branches {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-3);
}

.code-review-start__range {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.code-review-start__range > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.code-review-start__range > header > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text);
  font-size: var(--font-body);
}

.code-review-start__range > header > span > svg {
  color: var(--text-subtle);
}

.code-review-start__range-dates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.code-review-start__range > p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.code-review-start__range > p strong {
  color: var(--text-muted);
  font-weight: var(--weight-emphasis);
}

.code-review-start__branch-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.code-review-start__branch-meta > span {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-1);
  overflow: hidden;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-review-start__incremental {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.code-review-start__incremental > span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.code-review-start__incremental code {
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.code-review-start__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 640px) {
  .code-review-start {
    padding: 0 var(--space-4) var(--space-4);
  }

  .code-review-start__branches {
    grid-template-columns: 1fr;
  }

  .code-review-start__range > header {
    align-items: stretch;
    flex-direction: column;
  }

  .code-review-start__range-dates {
    grid-template-columns: 1fr;
  }

  .code-review-start__branch-meta,
  .code-review-start__incremental {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
