<script setup lang="ts">
import {
  AtSign,
  Server,
  ShieldCheck,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from 'vue'

import VaultPasswordPicker from '@/components/business/vault/VaultPasswordPicker.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useMailStore } from '@/stores/useMailStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { MailAccount } from '@/types/mail'
import type { VaultEntry } from '@/types/vault'

interface ProviderPreset {
  value: string
  label: string
  imapHost: string
  imapPort: number
  imapSsl: boolean
  smtpHost: string
  smtpPort: number
  smtpSsl: boolean
  smtpAuth: boolean
}

const props = defineProps<{
  account?: MailAccount | null
}>()

const emit = defineEmits<{
  close: []
  saved: [account: MailAccount]
  notify: [message: string]
}>()

const mail = useMailStore()
const workspaces = useWorkspaceStore()
const provider = ref('custom')
const saving = ref(false)
const errorMessage = ref('')
const selectedVaultEntryId = ref<number | null>(null)
const selectedVaultPassword = ref('')
const credentialBusy = ref(false)
const form = reactive({
  displayName: props.account?.displayName ?? '',
  emailAddress: props.account?.emailAddress ?? '',
  username: props.account?.username ?? '',
  imapHost: props.account?.imapHost ?? '',
  imapPort: String(props.account?.imapPort ?? 993),
  imapSsl: props.account?.imapSsl ?? true,
  smtpHost: props.account?.smtpHost ?? '',
  smtpPort: String(props.account?.smtpPort ?? 465),
  smtpSsl: props.account?.smtpSsl ?? true,
  smtpAuth: props.account?.smtpAuth ?? true,
})

const presets: ProviderPreset[] = [
  {
    value: 'qq',
    label: 'QQ 邮箱',
    imapHost: 'imap.qq.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.qq.com',
    smtpPort: 465,
    smtpSsl: true,
    smtpAuth: true,
  },
  {
    value: 'tencent-exmail',
    label: '腾讯企业邮箱',
    imapHost: 'imap.exmail.qq.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.exmail.qq.com',
    smtpPort: 465,
    smtpSsl: true,
    smtpAuth: false,
  },
  {
    value: '163',
    label: '网易 163',
    imapHost: 'imap.163.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.163.com',
    smtpPort: 465,
    smtpSsl: true,
    smtpAuth: true,
  },
  {
    value: '126',
    label: '网易 126',
    imapHost: 'imap.126.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.126.com',
    smtpPort: 465,
    smtpSsl: true,
    smtpAuth: true,
  },
  {
    value: 'gmail',
    label: 'Gmail',
    imapHost: 'imap.gmail.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.gmail.com',
    smtpPort: 465,
    smtpSsl: true,
    smtpAuth: true,
  },
  {
    value: 'outlook',
    label: 'Outlook / Microsoft 365',
    imapHost: 'outlook.office365.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.office365.com',
    smtpPort: 587,
    smtpSsl: false,
    smtpAuth: true,
  },
  {
    value: 'icloud',
    label: 'iCloud 邮箱',
    imapHost: 'imap.mail.me.com',
    imapPort: 993,
    imapSsl: true,
    smtpHost: 'smtp.mail.me.com',
    smtpPort: 587,
    smtpSsl: false,
    smtpAuth: true,
  },
]

const providerOptions = [
  { value: 'custom', label: '自定义服务器' },
  ...presets.map((item) => ({
    value: item.value,
    label: item.label,
  })),
]
const neteaseProvider = computed(
  () =>
    provider.value === '163' ||
    provider.value === '126' ||
    /^imap\.(163|126)\.com$/i.test(form.imapHost.trim()),
)
const credentialLabel = computed(() =>
  neteaseProvider.value
    ? 'IMAP/SMTP 授权码'
    : '邮箱密码/授权码',
)
const credentialDescription = computed(() =>
  props.account?.hasPassword
    ? `可选；不选择时继续使用当前已保存的${credentialLabel.value}。`
    : `必须从密码库选择${credentialLabel.value}，不能手动输入。`,
)
const tlsOptions = [
  { value: 'ssl', label: 'SSL/TLS' },
  { value: 'starttls', label: 'STARTTLS' },
]
const imapMode = computed({
  get: () => (form.imapSsl ? 'ssl' : 'starttls'),
  set: (value: string) => {
    form.imapSsl = value === 'ssl'
  },
})
const smtpMode = computed({
  get: () => (form.smtpSsl ? 'ssl' : 'starttls'),
  set: (value: string) => {
    form.smtpSsl = value === 'ssl'
  },
})
watch(provider, (value) => {
  if (value === 'custom') return
  const preset = presets.find((item) => item.value === value)
  if (!preset) return
  form.imapHost = preset.imapHost
  form.imapPort = String(preset.imapPort)
  form.imapSsl = preset.imapSsl
  form.smtpHost = preset.smtpHost
  form.smtpPort = String(preset.smtpPort)
  form.smtpSsl = preset.smtpSsl
  form.smtpAuth = preset.smtpAuth
  errorMessage.value = ''
})

watch(
  () => form.emailAddress,
  (value, previous) => {
    if (
      !form.username ||
      form.username === previous
    ) {
      form.username = value.trim()
    }
    if (!form.displayName) {
      const prefix = value.split('@')[0]?.trim()
      if (prefix) form.displayName = prefix
    }
  },
)

onBeforeUnmount(() => {
  selectedVaultPassword.value = ''
})

async function save() {
  if (saving.value || credentialBusy.value) return
  errorMessage.value = ''
  if (!props.account?.hasPassword && !selectedVaultPassword.value) {
    errorMessage.value = `请先从密码库选择${credentialLabel.value}`
    return
  }
  saving.value = true
  try {
    const account = await mail.saveAccount({
      id: props.account?.id,
      workspaceId: workspaces.activeWorkspaceId,
      displayName: form.displayName,
      emailAddress: form.emailAddress,
      username: form.username,
      password: selectedVaultPassword.value,
      imapHost: form.imapHost,
      imapPort: Number(form.imapPort),
      imapSsl: form.imapSsl,
      smtpHost: form.smtpHost,
      smtpPort: Number(form.smtpPort),
      smtpSsl: form.smtpSsl,
      smtpAuth: form.smtpAuth,
    })
    selectedVaultPassword.value = ''
    selectedVaultEntryId.value = null
    emit('notify', `邮箱「${account.displayName}」已保存`)
    emit('saved', account)
  } catch (error) {
    const message =
      error instanceof Error ? error.message : '无法保存邮箱账号'
    errorMessage.value =
      neteaseProvider.value && message.includes('被服务器拒绝')
        ? `${message}。请在网易邮箱网页版开启 IMAP/SMTP，并重新生成客户端授权密码`
        : message
  } finally {
    saving.value = false
  }
}

function handleVaultSelection(
  selection: { entry: VaultEntry; password: string } | null,
) {
  selectedVaultEntryId.value = selection?.entry.id ?? null
  selectedVaultPassword.value = selection?.password ?? ''
  if (selection?.entry.username && !form.username.trim()) {
    form.username = selection.entry.username
  }
  errorMessage.value = ''
}
</script>

<template>
  <UiDialog
    :title="account ? '编辑邮箱账号' : '添加邮箱账号'"
    description="使用 IMAP 收取邮件，通过 SMTP 发送回复。"
    size="wide"
    scroll-mode="body"
    max-height="min(760px, calc(100dvh - 96px))"
    :suspended="saving"
    @close="emit('close')"
  >
    <form
      id="mail-account-form"
      class="mail-account-form"
      @submit.prevent="save"
    >
      <section class="mail-account-form__section">
        <header>
          <span><AtSign :size="16" /></span>
          <div>
            <h3>邮箱身份</h3>
            <p>用于识别账号和生成回复发件人。</p>
          </div>
        </header>
        <div class="mail-account-form__grid">
          <UiFormField
            label="账号名称"
            control-id="mail-display-name"
            required
          >
            <UiInput
              id="mail-display-name"
              v-model="form.displayName"
              size="lg"
              maxlength="120"
              placeholder="例如：工作邮箱"
            />
          </UiFormField>
          <UiFormField
            label="邮箱地址"
            control-id="mail-email-address"
            required
          >
            <UiInput
              id="mail-email-address"
              v-model="form.emailAddress"
              size="lg"
              type="email"
              maxlength="320"
              placeholder="name@example.com"
              spellcheck="false"
            />
          </UiFormField>
          <UiFormField
            class="mail-account-form__wide"
            label="登录用户名"
            control-id="mail-username"
            hint="多数邮箱与邮箱地址相同"
            required
          >
            <UiInput
              id="mail-username"
              v-model="form.username"
              size="lg"
              maxlength="320"
              autocomplete="username"
              spellcheck="false"
            />
          </UiFormField>
          <VaultPasswordPicker
            :workspace-id="workspaces.activeWorkspaceId"
            :selected-entry-id="selectedVaultEntryId"
            :has-existing-password="account?.hasPassword"
            :label="credentialLabel"
            :description="credentialDescription"
            @select="handleVaultSelection"
            @busy="credentialBusy = $event"
          />
        </div>
      </section>

      <section class="mail-account-form__section">
        <header>
          <span><Server :size="16" /></span>
          <div>
            <h3>服务器</h3>
            <p>选择常用邮箱预设，或手动填写服务商参数。</p>
          </div>
        </header>
        <UiFormField label="邮箱服务商" control-id="mail-provider">
          <UiSelect
            id="mail-provider"
            v-model="provider"
            :options="providerOptions"
            size="lg"
            aria-label="邮箱服务商"
          />
        </UiFormField>
        <p
          v-if="neteaseProvider"
          class="mail-account-form__provider-hint"
          role="note"
        >
          <ShieldCheck :size="14" />
          <span>
            <strong>网易邮箱需要使用客户端授权密码。</strong>
            请先在网页版“设置 &gt; POP3/SMTP/IMAP”开启 IMAP/SMTP，
            再生成授权码；同一个授权码同时用于 IMAP 收件和 SMTP 发件，
            网页登录密码不能用于此连接。
          </span>
        </p>

        <div class="mail-account-form__server">
          <div class="mail-account-form__server-heading">
            <strong>收件服务器 IMAP</strong>
            <span>用于同步收件箱和阅读正文</span>
          </div>
          <div class="mail-account-form__server-fields">
            <UiFormField
              label="服务器地址"
              control-id="mail-imap-host"
              required
            >
              <UiInput
                id="mail-imap-host"
                v-model="form.imapHost"
                size="lg"
                placeholder="imap.example.com"
                spellcheck="false"
              />
            </UiFormField>
            <UiFormField
              label="端口"
              control-id="mail-imap-port"
              required
            >
              <UiInput
                id="mail-imap-port"
                v-model="form.imapPort"
                size="lg"
                type="number"
                min="1"
                max="65535"
              />
            </UiFormField>
            <UiFormField label="连接方式">
              <UiSegmentedControl
                v-model="imapMode"
                :items="tlsOptions"
                size="lg"
                aria-label="IMAP 加密方式"
              />
            </UiFormField>
          </div>
        </div>

        <div class="mail-account-form__server">
          <div class="mail-account-form__server-heading">
            <strong>发件服务器 SMTP</strong>
            <span>用于发送邮件回复</span>
          </div>
          <div class="mail-account-form__server-fields">
            <UiFormField
              label="服务器地址"
              control-id="mail-smtp-host"
              required
            >
              <UiInput
                id="mail-smtp-host"
                v-model="form.smtpHost"
                size="lg"
                placeholder="smtp.example.com"
                spellcheck="false"
              />
            </UiFormField>
            <UiFormField
              label="端口"
              control-id="mail-smtp-port"
              required
            >
              <UiInput
                id="mail-smtp-port"
                v-model="form.smtpPort"
                size="lg"
                type="number"
                min="1"
                max="65535"
              />
            </UiFormField>
            <UiFormField label="连接方式">
              <UiSegmentedControl
                v-model="smtpMode"
                :items="tlsOptions"
                size="lg"
                aria-label="SMTP 加密方式"
              />
            </UiFormField>
          </div>
          <div class="mail-account-form__auth">
            <UiCheckbox
              v-model="form.smtpAuth"
              label="SMTP 使用账号认证"
            />
            <button
              type="button"
              @click="form.smtpAuth = !form.smtpAuth"
            >
              SMTP 使用认证
            </button>
            <small>
              对应 Foxmail 的“使用鉴定”；腾讯企业邮箱可按需关闭
            </small>
          </div>
        </div>
      </section>

      <p class="mail-account-form__security">
        <ShieldCheck :size="14" />
        {{ credentialLabel }}从密码库读取后会加密保存在本机数据库，
        不会通过账号列表返回。
      </p>
      <p v-if="errorMessage" class="mail-account-form__error" role="alert">
        {{ errorMessage }}
      </p>
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
        form="mail-account-form"
        :loading="saving"
        :disabled="
          credentialBusy ||
          (!account?.hasPassword && !selectedVaultPassword)
        "
      >
        {{ saving ? '正在保存…' : '保存邮箱' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.mail-account-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: 0 var(--space-5) var(--space-5);
}

.mail-account-form__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.mail-account-form__section + .mail-account-form__section {
  padding-top: var(--space-4);
  border-top: 1px solid var(--line-soft);
}

.mail-account-form__section > header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.mail-account-form__section > header > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-account-form__section h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-account-form__section header p {
  margin: 2px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-account-form__grid,
.mail-account-form__server-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.mail-account-form__wide {
  grid-column: 1 / -1;
}

.mail-account-form__server {
  display: flex;
  padding: var(--space-3) 0 0;
  flex-direction: column;
  gap: var(--space-3);
  background: transparent;
  border-top: 1px solid var(--line-soft);
}

.mail-account-form__server-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.mail-account-form__server-heading strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-account-form__server-heading span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-account-form__auth {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.mail-account-form__auth button {
  padding: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: transparent;
}

.mail-account-form__auth button:hover {
  color: var(--text);
}

.mail-account-form__auth small {
  flex-basis: 100%;
  padding-left: 24px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-account-form__server-fields {
  grid-template-columns: minmax(0, 1fr) 88px auto;
  align-items: end;
}

.mail-account-form__security {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.mail-account-form__provider-hint {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  background: var(--surface-secondary);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.mail-account-form__provider-hint svg {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--system-green);
}

.mail-account-form__provider-hint strong {
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.mail-account-form__security svg {
  flex: 0 0 auto;
  color: var(--system-green);
}

.mail-account-form__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 620px) {
  .mail-account-form {
    padding-right: var(--space-4);
    padding-left: var(--space-4);
  }

  .mail-account-form__grid,
  .mail-account-form__server-fields {
    grid-template-columns: 1fr;
  }

  .mail-account-form__server-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }
}
</style>
