<script setup lang="ts">
import { Eye, EyeOff } from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import type {
  ManagedServer,
  ManagedServerInput,
} from '@/types/managedServer'

const props = defineProps<{
  server?: ManagedServer | null
  duplicate?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [input: ManagedServerInput]
  remove: [id: number]
}>()

const errorMessage = ref('')
const passwordVisible = ref(false)
const confirmingRemove = ref(false)
const form = reactive({
  name: '',
  host: '',
  port: '22',
  username: '',
  password: '',
})

const isEditing = computed(() =>
  Boolean(props.server && !props.duplicate),
)
const title = computed(() => {
  if (props.duplicate) return '复制服务器'
  return isEditing.value ? '编辑服务器' : '添加服务器'
})

watch(
  [() => props.server, () => props.duplicate],
  ([server, duplicate]) => {
    form.name = server
      ? duplicate
        ? `${server.name.slice(0, 77)} 副本`
        : server.name
      : ''
    form.host = server?.host ?? ''
    form.port = String(server?.port ?? 22)
    form.username = server?.username ?? ''
    form.password = ''
    passwordVisible.value = false
    errorMessage.value = ''
  },
  { immediate: true },
)

function submit() {
  const host = form.host.trim()
  const username = form.username.trim()
  const name = form.name.trim() || host
  const port = Number(form.port)

  errorMessage.value = ''
  if (!host) {
    errorMessage.value = '请输入服务器 IP 或主机名'
    return
  }
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    errorMessage.value = '端口必须是 1 到 65535 之间的整数'
    return
  }
  if (!username) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (!isEditing.value && !form.password) {
    errorMessage.value = '请输入密码'
    return
  }

  emit('save', {
    id: isEditing.value ? props.server?.id : undefined,
    name,
    host,
    port,
    username,
    password: form.password,
  })
}

function removeServer() {
  if (isEditing.value) confirmingRemove.value = true
}

function confirmRemoveServer() {
  if (!props.server) return
  confirmingRemove.value = false
  emit('remove', props.server.id)
}
</script>

<template>
  <UiDialog
    :title="title"
    description="密码只加密保存在本机；8小时工作台内置连接不会将密码发送到页面或地址栏。"
    initial-focus="#managed-server-host"
    :suspended="confirmingRemove"
    @close="emit('close')"
  >
    <form
      id="managed-server-form"
      class="server-form"
      @submit.prevent="submit"
    >
      <div class="server-form__body">
        <UiFormField
          label="名称"
          control-id="managed-server-name"
          hint="可选，留空时使用 IP 或主机名"
          layout="dialog"
        >
          <UiInput
            id="managed-server-name"
            v-model="form.name"
            size="lg"
            maxlength="80"
            placeholder="例如：生产环境"
          />
        </UiFormField>

        <UiFormField
          label="IP / 主机"
          control-id="managed-server-host"
          layout="dialog"
          required
        >
          <UiInput
            id="managed-server-host"
            v-model="form.host"
            size="lg"
            maxlength="255"
            placeholder="例如：192.168.1.10"
            spellcheck="false"
          />
        </UiFormField>

        <UiFormField
          label="端口"
          control-id="managed-server-port"
          hint="SSH 默认 22，SFTP 使用同一端口"
          layout="dialog"
          required
        >
          <UiInput
            id="managed-server-port"
            v-model="form.port"
            type="number"
            size="lg"
            min="1"
            max="65535"
          />
        </UiFormField>

        <UiFormField
          label="用户名"
          control-id="managed-server-username"
          layout="dialog"
          required
        >
          <UiInput
            id="managed-server-username"
            v-model="form.username"
            size="lg"
            maxlength="128"
            autocomplete="username"
            spellcheck="false"
          />
        </UiFormField>

        <UiFormField
          label="密码"
          control-id="managed-server-password"
          :hint="
            isEditing
              ? '留空则保持原密码不变'
              : duplicate
                ? '密码不会随服务器复制，请重新输入'
                : '密码使用 AES-GCM 加密后保存'
          "
          layout="dialog"
          :required="!isEditing"
        >
          <span class="server-form__password">
            <UiInput
              id="managed-server-password"
              v-model="form.password"
              :type="passwordVisible ? 'text' : 'password'"
              size="lg"
              maxlength="1024"
              autocomplete="new-password"
              :placeholder="
                isEditing ? '不修改密码' : '输入服务器密码'
              "
            />
            <button
              type="button"
              :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
              @click="passwordVisible = !passwordVisible"
            >
              <EyeOff v-if="passwordVisible" :size="15" />
              <Eye v-else :size="15" />
            </button>
          </span>
        </UiFormField>

        <p v-if="errorMessage" class="server-form__error" role="alert">
          {{ errorMessage }}
        </p>
      </div>
    </form>

    <template #footer>
      <div class="server-form__actions">
        <UiButton
          v-if="isEditing"
          variant="danger-ghost"
          size="lg"
          @click="removeServer"
        >
          删除服务器
        </UiButton>
        <span v-else />
        <div>
          <UiButton size="lg" @click="emit('close')">取消</UiButton>
          <UiButton
            variant="primary"
            size="lg"
            type="submit"
            form="managed-server-form"
          >
            保存
          </UiButton>
        </div>
      </div>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="confirmingRemove && server"
    title="删除服务器？"
    :description="`从 8小时工作台删除「${server.name}」，保存的服务器密码也会一并删除。`"
    confirm-label="删除服务器"
    @close="confirmingRemove = false"
    @confirm="confirmRemoveServer"
  >
    <div class="server-remove-target">
      <strong>{{ server.name }}</strong>
      <small>{{ server.username }}@{{ server.host }}:{{ server.port }}</small>
    </div>
  </UiConfirmDialog>
</template>

<style scoped>
.server-form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 var(--space-5);
}

.server-form__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 2px 0 var(--space-4);
  overflow-y: auto;
}

.server-form__password {
  position: relative;
  display: block;
}

.server-form__password :deep(.ui-input) {
  padding-right: 34px;
}

.server-form__password button {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  width: 28px;
  height: var(--control-md);
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.server-form__password button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.server-form__error {
  margin: -2px 0 0 calc(64px + var(--space-3));
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.server-form__actions {
  display: flex;
  width: 100%;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.server-remove-target {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.server-remove-target strong,
.server-remove-target small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-remove-target strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.server-remove-target small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.server-form__actions > div {
  display: flex;
  gap: var(--space-3);
}

@media (max-width: 520px) {
  .server-form {
    padding: 0 var(--space-4);
  }

  .server-form__error {
    margin-left: 0;
  }
}
</style>
