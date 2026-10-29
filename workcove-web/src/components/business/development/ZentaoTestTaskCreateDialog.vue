<script setup lang="ts">
import { FlaskConical, RefreshCw } from '@lucide/vue'
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCombobox from '@/components/ui/UiCombobox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useZentaoStore } from '@/stores/useZentaoStore'
import type {
  ZentaoBuild,
  ZentaoCreatedTestTask,
  ZentaoTestTaskOptions,
} from '@/types/zentao'

const props = defineProps<{
  workspaceId: string
  build: ZentaoBuild
}>()

const emit = defineEmits<{
  close: []
  created: [testTask: ZentaoCreatedTestTask]
}>()

const TYPE_OPTIONS = [
  { value: '', label: '不指定' },
  { value: 'integrate', label: '集成测试' },
  { value: 'system', label: '系统测试' },
  { value: 'acceptance', label: '验收测试' },
  { value: 'performance', label: '性能测试' },
  { value: 'safety', label: '安全测试' },
]
const STATUS_OPTIONS = [
  { value: 'wait', label: '未开始' },
  { value: 'doing', label: '进行中' },
  { value: 'blocked', label: '被阻塞' },
  { value: 'done', label: '已关闭' },
]

const zentao = useZentaoStore()
const loading = ref(true)
const saving = ref(false)
const options = ref<ZentaoTestTaskOptions>({
  users: [],
  reports: [],
})
const errorMessage = ref('')
const form = reactive({
  type: '',
  owner: '',
  members: '',
  begin: today(),
  end: dateAfter(7),
  status: 'wait',
  testReport: '',
  name: `${props.build.name} 测试单`,
})

const userOptions = computed(() => [
  { value: '', label: '未指定' },
  ...options.value.users.map((user) => ({
    value: user.account,
    label: user.name,
    description:
      user.name === user.account ? undefined : user.account,
  })),
])
const reportOptions = computed(() => [
  { value: '', label: '不关联测试报告' },
  ...options.value.reports.map((report) => ({
    value: report.id,
    label: report.title || `测试报告 ${report.id}`,
  })),
])
const memberAccounts = computed(() =>
  options.value.users.map((user) => user.account),
)

onMounted(() => {
  void loadOptions()
})

async function loadOptions() {
  loading.value = true
  errorMessage.value = ''
  try {
    options.value = await zentao.loadTestTaskOptions(
      props.build.productId,
    )
    form.owner =
      options.value.users.find(
        (user) => user.account === zentao.connection.username,
      )?.account
      ?? props.build.builder
      ?? ''
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '无法读取禅道用户和测试报告'
  } finally {
    loading.value = false
  }
}

function validate() {
  if (!form.name.trim()) return '请输入测试单名称'
  if (!form.begin) return '请选择开始日期'
  if (!form.end) return '请选择结束日期'
  if (form.end < form.begin) return '结束日期不能早于开始日期'
  if (form.members.trim()) {
    const invalid = memberList().find(
      (account) => !/^[a-zA-Z0-9_.@-]{1,128}$/.test(account),
    )
    if (invalid) return `参与人账号「${invalid}」格式不正确`
  }
  return ''
}

function memberList() {
  return form.members
    .replaceAll('，', ',')
    .split(',')
    .map((account) => account.trim())
    .filter(Boolean)
}

async function submit() {
  if (saving.value) return
  errorMessage.value = validate()
  if (errorMessage.value) return

  saving.value = true
  try {
    const created = await zentao.createTestTask(props.build.id, {
      types: form.type ? [form.type] : [],
      owner: form.owner,
      members: memberList(),
      status: form.status,
      begin: form.begin,
      end: form.end,
      testReport: form.testReport,
      name: form.name.trim(),
    })
    emit('created', created)
    emit('close')
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法提交测试单'
  } finally {
    saving.value = false
  }
}

function today() {
  return localDate(new Date())
}

function dateAfter(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
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
    title="提交测试单"
    :description="`基于构建 ${build.name} 创建禅道测试单。`"
    size="wide"
    height="standard"
    scroll-mode="body"
    initial-focus="#zentao-test-task-name"
    :suspended="saving"
    @close="emit('close')"
  >
    <form
      id="zentao-test-task-create-form"
      class="zentao-test-task-form"
      @submit.prevent="submit"
    >
      <div class="zentao-test-task-context">
        <FlaskConical :size="16" />
        <div>
          <strong>{{ build.name }}</strong>
          <span>
            {{ build.projectName || '未知项目' }}
            · {{ build.productName || '未知产品' }}
          </span>
        </div>
      </div>

      <div
        v-if="loading"
        class="zentao-form-state"
      >
        <UiFidgetSpinner :size="22" />
        <span>正在读取测试人员和测试报告</span>
      </div>

      <template v-else>
        <div class="zentao-test-task-form__row">
          <UiFormField label="测试类型">
            <UiSelect
              v-model="form.type"
              :options="TYPE_OPTIONS"
              size="lg"
              aria-label="选择测试类型"
            />
          </UiFormField>
          <UiFormField label="负责人">
            <UiSelect
              v-model="form.owner"
              :options="userOptions"
              size="lg"
              searchable
              aria-label="选择测试单负责人"
            />
          </UiFormField>
        </div>

        <UiFormField
          label="参与人"
          hint="可选择账号，多个账号用英文逗号分隔"
        >
          <UiCombobox
            v-model="form.members"
            :options="memberAccounts"
            aria-label="选择或输入测试单参与人"
            placeholder="例如：panyong,zhangsan"
          />
        </UiFormField>

        <div class="zentao-test-task-form__row">
          <UiFormField
            label="开始日期"
            control-id="zentao-test-task-begin"
            required
          >
            <UiInput
              id="zentao-test-task-begin"
              v-model="form.begin"
              type="date"
              size="lg"
            />
          </UiFormField>
          <UiFormField
            label="结束日期"
            control-id="zentao-test-task-end"
            required
          >
            <UiInput
              id="zentao-test-task-end"
              v-model="form.end"
              type="date"
              size="lg"
            />
          </UiFormField>
        </div>

        <div class="zentao-test-task-form__row">
          <UiFormField label="当前状态" required>
            <UiSelect
              v-model="form.status"
              :options="STATUS_OPTIONS"
              size="lg"
              aria-label="选择测试单当前状态"
            />
          </UiFormField>
          <UiFormField label="相关测试报告">
            <UiSelect
              v-model="form.testReport"
              :options="reportOptions"
              size="lg"
              searchable
              aria-label="关联测试报告"
            />
          </UiFormField>
        </div>

        <UiFormField
          label="测试单名称"
          control-id="zentao-test-task-name"
          required
        >
          <UiInput
            id="zentao-test-task-name"
            v-model="form.name"
            size="lg"
            maxlength="500"
            placeholder="输入测试单名称"
          />
        </UiFormField>

        <p
          v-if="errorMessage"
          class="zentao-form-error"
          role="alert"
        >
          {{ errorMessage }}
        </p>

        <UiButton
          v-if="errorMessage"
          size="sm"
          class="zentao-form-retry"
          :disabled="loading"
          @click="loadOptions"
        >
          <RefreshCw :size="13" />
          重试
        </UiButton>
      </template>
    </form>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="saving"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="zentao-test-task-create-form"
        :loading="saving"
        :disabled="loading"
      >
        {{ saving ? '提交中…' : '提交测试单' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.zentao-test-task-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.zentao-test-task-context {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  color: var(--accent);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.zentao-test-task-context > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.zentao-test-task-context strong,
.zentao-test-task-context span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zentao-test-task-context strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.zentao-test-task-context span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-test-task-form__row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.zentao-form-state {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.zentao-form-error {
  padding: var(--space-2) var(--space-3);
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-red) 7%,
    transparent
  );
  border-radius: var(--radius-control);
}

.zentao-form-retry {
  align-self: flex-start;
}

@media (max-width: 620px) {
  .zentao-test-task-form {
    padding: 0 var(--space-4) var(--space-3);
  }

  .zentao-test-task-form__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
