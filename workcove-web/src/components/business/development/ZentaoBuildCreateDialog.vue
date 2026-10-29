<script setup lang="ts">
import { PackagePlus, RefreshCw } from '@lucide/vue'
import {
  computed,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useZentaoStore } from '@/stores/useZentaoStore'
import type {
  ZentaoBuildOptions,
  ZentaoCreatedBuild,
  ZentaoSystem,
} from '@/types/zentao'

const props = defineProps<{
  workspaceId: string
}>()

const emit = defineEmits<{
  close: []
  created: [build: ZentaoCreatedBuild]
}>()

const zentao = useZentaoStore()
const loading = ref(true)
const loadingSystems = ref(false)
const saving = ref(false)
const options = ref<ZentaoBuildOptions>({
  executions: [],
  products: [],
  users: [],
})
const systems = ref<ZentaoSystem[]>([])
const errorMessage = ref('')
const systemError = ref('')
let systemRequest = 0

const form = reactive({
  executionId: '',
  productId: '',
  systemId: '',
  name: '',
  builder: '',
  date: today(),
  downloadUrl: '',
})

const selectedExecution = computed(() =>
  options.value.executions.find(
    (execution) => execution.id === form.executionId,
  ) ?? null,
)
const executionOptions = computed(() =>
  options.value.executions.map((execution) => ({
    value: execution.id,
    label: execution.name,
    description:
      execution.projectName
      || `项目 ${execution.projectId}`,
  })),
)
const productOptions = computed(() =>
  options.value.products.map((product) => ({
    value: product.id,
    label: product.name,
  })),
)
const systemOptions = computed(() =>
  systems.value.map((system) => ({
    value: system.id,
    label: system.name,
  })),
)
const builderOptions = computed(() => {
  const users = options.value.users.map((user) => ({
    value: user.account,
    label: user.name,
    description:
      user.name === user.account ? undefined : user.account,
  }))
  if (
    form.builder
    && !users.some((user) => user.value === form.builder)
  ) {
    users.unshift({
      value: form.builder,
      label: form.builder,
      description: '当前禅道账号',
    })
  }
  return users
})

watch(
  () => form.productId,
  (productId, previous) => {
    if (!productId || productId === previous) return
    void loadSystems(productId)
  },
)

watch(
  () => form.executionId,
  (executionId) => {
    const execution = options.value.executions.find(
      (item) => item.id === executionId,
    )
    if (
      execution?.productId
      && options.value.products.some(
        (product) => product.id === execution.productId,
      )
    ) {
      form.productId = execution.productId
    }
  },
)

onMounted(() => {
  void loadOptions()
})

async function loadOptions() {
  loading.value = true
  errorMessage.value = ''
  try {
    options.value = await zentao.loadBuildOptions()
    const latest = zentao.builds[0]
    const latestExecution = options.value.executions.find(
      (execution) => execution.id === latest?.executionId,
    )
    const latestProduct = options.value.products.find(
      (product) => product.id === latest?.productId,
    )
    form.executionId =
      latestExecution?.id
      ?? options.value.executions[0]?.id
      ?? ''
    form.productId =
      latestProduct?.id
      ?? latestExecution?.productId
      ?? options.value.products[0]?.id
      ?? ''
    form.builder =
      options.value.users.find(
        (user) => user.account === zentao.connection.username,
      )?.account
      ?? latest?.builder
      ?? options.value.users[0]?.account
      ?? zentao.connection.username
    form.name = nextBuildName(latest?.name ?? '')
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '无法读取禅道构建选项'
  } finally {
    loading.value = false
  }
}

async function loadSystems(productId: string) {
  const requestId = ++systemRequest
  loadingSystems.value = true
  systemError.value = ''
  form.systemId = ''
  try {
    const result = await zentao.loadSystems(productId)
    if (requestId !== systemRequest) return
    systems.value = result
    form.systemId = result[0]?.id ?? ''
    if (result.length === 0) {
      systemError.value = '当前产品还没有禅道应用，请先在禅道中创建应用'
    }
  } catch (error) {
    if (requestId !== systemRequest) return
    systems.value = []
    systemError.value =
      error instanceof Error
        ? error.message
        : '无法读取所属应用'
  } finally {
    if (requestId === systemRequest) loadingSystems.value = false
  }
}

function nextBuildName(previousName: string) {
  const match = previousName.trim().match(/^(.*?)(\d+)$/)
  if (!match) return ''
  const prefix = match[1]
  const digits = match[2]
  return `${prefix}${String(Number(digits) + 1).padStart(
    digits.length,
    '0',
  )}`
}

function validate() {
  if (!selectedExecution.value) return '请选择迭代'
  if (!form.productId) return '请选择所属产品'
  if (!form.systemId) return '请选择所属应用'
  if (!form.name.trim()) return '请输入构建名称'
  if (!form.builder.trim()) return '请选择构建者'
  if (!form.date) return '请选择打包日期'
  return ''
}

async function submit() {
  if (saving.value) return
  errorMessage.value = validate()
  if (errorMessage.value) return

  saving.value = true
  try {
    const created = await zentao.createBuild({
      projectId: selectedExecution.value!.projectId,
      executionId: form.executionId,
      productId: form.productId,
      systemId: form.systemId,
      name: form.name.trim(),
      builder: form.builder.trim(),
      date: form.date,
      downloadUrl: form.downloadUrl.trim(),
    })
    emit('created', created)
    emit('close')
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法创建构建'
  } finally {
    saving.value = false
  }
}

function today() {
  return localDate(new Date())
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
    title="新建构建"
    description="构建会直接创建到禅道，并显示在当前构建清单中。"
    size="wide"
    height="standard"
    scroll-mode="body"
    initial-focus="#zentao-build-name"
    :suspended="saving"
    @close="emit('close')"
  >
    <form
      id="zentao-build-create-form"
      class="zentao-build-form"
      @submit.prevent="submit"
    >
      <div
        v-if="loading"
        class="zentao-form-state"
      >
        <UiFidgetSpinner :size="22" />
        <span>正在读取禅道迭代、产品和应用</span>
      </div>

      <template v-else>
        <UiFormField
          label="迭代"
          control-id="zentao-build-execution"
          required
        >
          <UiSelect
            id="zentao-build-execution"
            v-model="form.executionId"
            :options="executionOptions"
            size="lg"
            searchable
            aria-label="选择所属迭代"
            placeholder="选择迭代"
          />
        </UiFormField>

        <div class="zentao-build-form__row">
          <UiFormField
            label="所属产品"
            control-id="zentao-build-product"
            required
          >
            <UiSelect
              id="zentao-build-product"
              v-model="form.productId"
              :options="productOptions"
              size="lg"
              searchable
              aria-label="选择所属产品"
              placeholder="选择产品"
            />
          </UiFormField>

          <UiFormField
            label="所属应用"
            control-id="zentao-build-system"
            :hint="systemError"
            required
          >
            <div class="zentao-build-form__select">
              <UiSelect
                id="zentao-build-system"
                v-model="form.systemId"
                :options="systemOptions"
                size="lg"
                searchable
                :disabled="loadingSystems"
                aria-label="选择所属应用"
                :placeholder="
                  loadingSystems ? '正在读取应用…' : '选择应用'
                "
              />
              <UiFidgetSpinner
                v-if="loadingSystems"
                :size="14"
              />
            </div>
          </UiFormField>
        </div>

        <UiFormField
          label="名称"
          control-id="zentao-build-name"
          hint="名称在产品内不能重复"
          required
        >
          <UiInput
            id="zentao-build-name"
            v-model="form.name"
            size="lg"
            maxlength="500"
            placeholder="例如：V3.0.2_R100B003"
          />
        </UiFormField>

        <div class="zentao-build-form__row">
          <UiFormField
            label="构建者"
            control-id="zentao-build-builder"
            required
          >
            <UiSelect
              id="zentao-build-builder"
              v-model="form.builder"
              :options="builderOptions"
              size="lg"
              searchable
              aria-label="选择构建者"
              placeholder="选择构建者"
            />
          </UiFormField>

          <UiFormField
            label="打包日期"
            control-id="zentao-build-date"
            required
          >
            <UiInput
              id="zentao-build-date"
              v-model="form.date"
              type="date"
              size="lg"
            />
          </UiFormField>
        </div>

        <UiFormField
          label="下载地址"
          control-id="zentao-build-download"
          hint="支持 HTTP、HTTPS、FTP 或共享目录地址"
        >
          <UiInput
            id="zentao-build-download"
            v-model="form.downloadUrl"
            size="lg"
            maxlength="4096"
            placeholder="ftp://... 或 https://..."
            spellcheck="false"
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

        <div
          v-if="options.executions.length === 0"
          class="zentao-form-empty"
        >
          <PackagePlus :size="18" />
          <span>当前同步项目没有可用迭代，请先在禅道中创建迭代。</span>
        </div>

        <UiButton
          v-if="systemError"
          size="sm"
          class="zentao-form-retry"
          :disabled="!form.productId || loadingSystems"
          @click="loadSystems(form.productId)"
        >
          <RefreshCw :size="13" />
          重新读取应用
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
        form="zentao-build-create-form"
        :loading="saving"
        :disabled="loading || options.executions.length === 0"
      >
        {{ saving ? '创建中…' : '创建构建' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.zentao-build-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.zentao-build-form__row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.zentao-build-form__select {
  position: relative;
}

.zentao-build-form__select > :last-child {
  position: absolute;
  top: 50%;
  right: 36px;
  color: var(--text-subtle);
  pointer-events: none;
  transform: translateY(-50%);
}

.zentao-form-state {
  display: flex;
  min-height: 260px;
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

.zentao-form-empty {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.zentao-form-retry {
  align-self: flex-start;
}

@media (max-width: 620px) {
  .zentao-build-form {
    padding: 0 var(--space-4) var(--space-3);
  }

  .zentao-build-form__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
