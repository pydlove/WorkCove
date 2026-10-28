<script setup lang="ts">
import {
  Eye,
  EyeOff,
  LoaderCircle,
  PlugZap,
  Save,
} from '@lucide/vue'
import { reactive, ref, watch } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import { useDatabaseStore } from '@/stores/useDatabaseStore'
import type {
  DatabaseConnection,
  DatabaseConnectionInput,
  DatabaseType,
} from '@/types/database'

const props = defineProps<{
  workspaceId: string
  connection: DatabaseConnection | null
}>()

const emit = defineEmits<{
  close: []
  saved: [connection: DatabaseConnection]
  notify: [message: string]
}>()

const databases = useDatabaseStore()
const saving = ref(false)
const testing = ref(false)
const passwordVisible = ref(false)
const errorMessage = ref('')
const testMessage = ref('')
const form = reactive({
  name: props.connection?.name ?? '',
  type: (props.connection?.type ?? 'MYSQL') as DatabaseType,
  host: props.connection?.host ?? '127.0.0.1',
  port: String(
    props.connection?.port ??
      (props.connection?.type === 'CLICKHOUSE' ? 8123 : 3306),
  ),
  databaseName: props.connection?.databaseName ?? '',
  username: props.connection?.username ?? '',
  password: '',
  sslEnabled: props.connection?.sslEnabled ?? false,
})

const typeItems = [
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'CLICKHOUSE', label: 'ClickHouse' },
]

watch(
  () => props.connection,
  (connection) => {
    form.name = connection?.name ?? ''
    form.type = connection?.type ?? 'MYSQL'
    form.host = connection?.host ?? '127.0.0.1'
    form.port = String(
      connection?.port ?? (connection?.type === 'CLICKHOUSE' ? 8123 : 3306),
    )
    form.databaseName = connection?.databaseName ?? ''
    form.username = connection?.username ?? ''
    form.password = ''
    form.sslEnabled = connection?.sslEnabled ?? false
  },
)

function setType(value: string) {
  const next = value as DatabaseType
  const previousDefault = form.type === 'CLICKHOUSE' ? 8123 : 3306
  const currentPort = Number(form.port)
  form.type = next
  if (!currentPort || currentPort === previousDefault) {
    form.port = next === 'CLICKHOUSE' ? '8123' : '3306'
  }
  if (next === 'CLICKHOUSE' && !form.username.trim()) {
    form.username = 'default'
  }
  testMessage.value = ''
}

function setSslEnabled(enabled: boolean) {
  const clickHouseHttp = form.port === '8123'
  const clickHouseHttps = form.port === '8443'
  form.sslEnabled = enabled
  if (form.type === 'CLICKHOUSE' && clickHouseHttp && enabled) {
    form.port = '8443'
  } else if (form.type === 'CLICKHOUSE' && clickHouseHttps && !enabled) {
    form.port = '8123'
  }
  testMessage.value = ''
}

function input(): DatabaseConnectionInput {
  const port = Number(form.port)
  if (!form.name.trim()) throw new Error('请输入连接名称')
  if (!form.host.trim()) throw new Error('请输入主机地址')
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('端口必须在 1 到 65535 之间')
  }
  if (!form.username.trim()) throw new Error('请输入用户名')
  return {
    id: props.connection?.id,
    workspaceId: props.workspaceId,
    name: form.name.trim(),
    type: form.type,
    host: form.host.trim(),
    port,
    databaseName: form.databaseName.trim(),
    username: form.username.trim(),
    password: form.password,
    sslEnabled: form.sslEnabled,
  }
}

async function testConnection() {
  if (testing.value) return
  errorMessage.value = ''
  testMessage.value = ''
  testing.value = true
  try {
    const result = await databases.test(input())
    testMessage.value = `${result.message} · ${result.elapsedMs} ms`
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '连接测试失败'
  } finally {
    testing.value = false
  }
}

async function save() {
  if (saving.value) return
  errorMessage.value = ''
  saving.value = true
  try {
    const connection = await databases.save(input())
    emit('saved', connection)
    emit(
      'notify',
      props.connection ? '数据库连接已更新' : '数据库连接已添加',
    )
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '数据库连接保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UiDialog
    :title="connection ? '编辑数据库连接' : '添加数据库连接'"
    description="连接信息保存在 MySQL，密码使用应用主密钥加密。"
    size="wide"
    initial-focus="#database-connection-name"
    :suspended="saving || testing"
    @close="emit('close')"
  >
    <form
      id="database-connection-form"
      class="database-connection-form"
      @submit.prevent="save"
    >
      <div class="database-connection-form__type">
        <span>数据库类型</span>
        <UiSegmentedControl
          :model-value="form.type"
          :items="typeItems"
          aria-label="数据库类型"
          @update:model-value="setType"
        />
      </div>

      <UiFormField
        label="连接名称"
        control-id="database-connection-name"
        required
      >
        <UiInput
          id="database-connection-name"
          v-model="form.name"
          size="lg"
          maxlength="80"
          placeholder="例如：生产数据仓库"
        />
      </UiFormField>

      <div class="database-connection-form__address">
        <UiFormField label="主机地址" required>
          <UiInput
            v-model="form.host"
            size="lg"
            maxlength="255"
            placeholder="127.0.0.1"
          />
        </UiFormField>
        <UiFormField label="端口" required>
          <UiInput
            v-model="form.port"
            size="lg"
            inputmode="numeric"
            maxlength="5"
          />
        </UiFormField>
      </div>

      <UiFormField label="数据库">
        <UiInput
          v-model="form.databaseName"
          size="lg"
          maxlength="128"
          :placeholder="
            form.type === 'CLICKHOUSE' ? 'default' : '可留空'
          "
        />
      </UiFormField>

      <UiFormField label="用户名" required>
        <UiInput
          v-model="form.username"
          size="lg"
          maxlength="128"
          autocomplete="username"
          :placeholder="
            form.type === 'CLICKHOUSE' ? 'default' : '数据库用户名'
          "
        />
      </UiFormField>

      <UiFormField
        label="密码"
        :hint="
          connection
            ? '留空则保持原密码不变'
            : '密码会加密后保存'
        "
      >
        <div class="database-connection-form__password">
          <UiInput
            v-model="form.password"
            size="lg"
            :type="passwordVisible ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="数据库密码"
          />
          <button
            type="button"
            :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
            :title="passwordVisible ? '隐藏密码' : '显示密码'"
            @click="passwordVisible = !passwordVisible"
          >
            <EyeOff v-if="passwordVisible" :size="15" />
            <Eye v-else :size="15" />
          </button>
        </div>
      </UiFormField>

      <div class="database-connection-form__ssl">
        <UiCheckbox
          :model-value="form.sslEnabled"
          label="启用 SSL"
          @update:model-value="setSslEnabled"
        />
        <span>
          <strong>启用 SSL / TLS</strong>
          <small>连接远程数据库时建议开启</small>
        </span>
      </div>

      <p
        v-if="testMessage"
        class="database-connection-form__success"
        role="status"
      >
        {{ testMessage }}
      </p>
      <p
        v-if="errorMessage"
        class="database-connection-form__error"
        role="alert"
      >
        {{ errorMessage }}
      </p>
    </form>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="saving || testing"
        @click="testConnection"
      >
        <LoaderCircle
          v-if="testing"
          class="database-spin"
          :size="14"
        />
        <PlugZap v-else :size="14" />
        {{ testing ? '测试中…' : '测试连接' }}
      </UiButton>
      <span class="database-connection-form__spacer" />
      <UiButton
        size="lg"
        :disabled="saving || testing"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="database-connection-form"
        :disabled="saving || testing"
      >
        <LoaderCircle
          v-if="saving"
          class="database-spin"
          :size="14"
        />
        <Save v-else :size="14" />
        {{ saving ? '保存中…' : '保存连接' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.database-connection-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-4);
}

.database-connection-form__type {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.database-connection-form__type > span {
  color: var(--text);
  font-size: var(--font-body);
}

.database-connection-form__address {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px;
  gap: var(--space-3);
}

.database-connection-form__password {
  position: relative;
}

.database-connection-form__password :deep(.ui-input) {
  padding-right: 36px;
}

.database-connection-form__password > button {
  position: absolute;
  top: 50%;
  right: 7px;
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  transform: translateY(-50%);
  place-items: center;
}

.database-connection-form__password > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.database-connection-form__ssl {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.database-connection-form__ssl > span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.database-connection-form__ssl strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.database-connection-form__ssl small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.database-connection-form__success,
.database-connection-form__error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-caption);
  border-radius: var(--radius-control);
}

.database-connection-form__success {
  color: var(--system-green);
  background: color-mix(in srgb, var(--system-green) 8%, transparent);
}

.database-connection-form__error {
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
}

.database-connection-form__spacer {
  flex: 1;
}

.database-spin {
  animation: database-spin 900ms linear infinite;
}

@keyframes database-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 560px) {
  .database-connection-form {
    padding: 0 var(--space-4) var(--space-4);
  }

  .database-connection-form__type {
    align-items: flex-start;
    flex-direction: column;
  }

  .database-connection-form__address {
    grid-template-columns: minmax(0, 1fr) 92px;
  }
}
</style>
