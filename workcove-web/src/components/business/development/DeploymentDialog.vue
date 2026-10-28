<script setup lang="ts">
import {
  CloudUpload,
  FileCode2,
  Server,
  Trash2,
} from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import type {
  DeploymentConfig,
  DeploymentInput,
} from '@/types/deployment'
import type { ManagedServer } from '@/types/managedServer'

const MAX_SCRIPT_BYTES = 512 * 1024

const props = defineProps<{
  deployment?: DeploymentConfig | null
  servers: ManagedServer[]
}>()

const emit = defineEmits<{
  close: []
  save: [input: DeploymentInput]
  remove: [id: string]
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const confirmingRemove = ref(false)
const form = reactive({
  name: '',
  serverId: '',
  remoteDirectory: '',
  scriptFilename: '',
  scriptContent: '',
})

const selectedServer = computed(
  () =>
    props.servers.find(
      (server) => String(server.id) === form.serverId,
    ) ?? null,
)
const serverOptions = computed(() =>
  props.servers.map((server) => ({
    value: String(server.id),
    label: server.name,
    description: `${server.username}@${server.host}:${server.port}`,
  })),
)

const scriptSize = computed(
  () => new Blob([form.scriptContent]).size,
)

const scriptSizeLabel = computed(() => {
  if (scriptSize.value < 1024) return `${scriptSize.value} B`
  return `${(scriptSize.value / 1024).toFixed(1)} KB`
})

watch(
  () => props.deployment,
  (deployment) => {
    form.name = deployment?.name ?? ''
    form.serverId =
      deployment?.serverId === null ||
      deployment?.serverId === undefined
        ? ''
        : String(deployment.serverId)
    form.remoteDirectory = deployment?.remoteDirectory ?? ''
    form.scriptFilename = deployment?.scriptFilename ?? ''
    form.scriptContent = deployment?.scriptContent ?? ''
    errorMessage.value = ''
  },
  { immediate: true },
)

function validate() {
  const name = form.name.trim()
  const directory = form.remoteDirectory.trim()
  const filename = form.scriptFilename.trim()
  const bytes = new Blob([form.scriptContent]).size

  if (!name) return '请输入部署名称'
  if ([...name].length > 80) return '部署名称不能超过 80 个字符'
  if (!selectedServer.value) return '请选择部署服务器'
  if (!directory.startsWith('/')) {
    return '远程目录必须是以 / 开头的绝对路径'
  }
  if ([...directory].length > 512) return '远程目录过长'
  if (!filename) return '请上传或填写脚本文件名'
  if ([...filename].length > 255) return '脚本文件名过长'
  if (filename.includes('/') || filename.includes('\\')) {
    return '脚本文件名不能包含路径'
  }
  if (!form.scriptContent.trim()) return '请上传或填写部署脚本'
  if (bytes > MAX_SCRIPT_BYTES) return '部署脚本不能超过 512 KB'
  if (form.scriptContent.includes('\0')) {
    return '部署脚本包含无效字符'
  }
  return ''
}

function submit() {
  errorMessage.value = validate()
  if (errorMessage.value || !selectedServer.value) return

  emit('save', {
    id: props.deployment?.id,
    name: form.name.trim(),
    serverId: selectedServer.value.id,
    serverName: selectedServer.value.name,
    remoteDirectory: form.remoteDirectory.trim(),
    scriptFilename: form.scriptFilename.trim(),
    scriptContent: form.scriptContent,
  })
}

async function handleScriptFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  errorMessage.value = ''
  if (file.size > MAX_SCRIPT_BYTES) {
    errorMessage.value = '部署脚本不能超过 512 KB'
    input.value = ''
    return
  }

  try {
    const content = await file.text()
    if (content.includes('\0')) {
      errorMessage.value = '该文件不是可用的文本脚本'
      return
    }
    form.scriptFilename = file.name
    form.scriptContent = content
    if (!form.name.trim()) {
      form.name = file.name.replace(/\.[^.]+$/, '') || '新建部署'
    }
  } catch {
    errorMessage.value = '无法读取脚本文件'
  } finally {
    input.value = ''
  }
}

function removeDeployment() {
  if (props.deployment) confirmingRemove.value = true
}

function confirmRemoveDeployment() {
  if (!props.deployment) return
  confirmingRemove.value = false
  emit('remove', props.deployment.id)
}
</script>

<template>
  <UiDialog
    :title="deployment ? '编辑部署' : '新建部署'"
    description="选择服务器并上传要执行的部署脚本。"
    size="wide"
    initial-focus="#deployment-name"
    :suspended="confirmingRemove"
    @close="emit('close')"
  >
    <form
      id="deployment-form"
      class="deployment-form"
      @submit.prevent="submit"
    >
      <div class="deployment-form__body">
        <div v-if="servers.length === 0" class="deployment-form__notice">
          <Server :size="16" />
          <span>
            当前工作空间还没有服务器，请先在“服务器管理”中添加。
          </span>
        </div>

        <UiFormField
          label="名称"
          control-id="deployment-name"
          layout="dialog"
          required
        >
          <UiInput
            id="deployment-name"
            v-model="form.name"
            size="lg"
            maxlength="80"
            placeholder="例如：生产环境发布"
          />
        </UiFormField>

        <UiFormField
          label="服务器"
          control-id="deployment-server"
          layout="dialog"
          required
        >
          <UiSelect
            id="deployment-server"
            v-model="form.serverId"
            :options="serverOptions"
            size="lg"
            placeholder="请选择服务器"
            aria-label="部署服务器"
            :disabled="servers.length === 0"
          />
        </UiFormField>

        <UiFormField
          label="远程目录"
          control-id="deployment-directory"
          hint="脚本会在这个绝对路径下执行，例如 /opt/workcove"
          layout="dialog"
          required
        >
          <UiInput
            id="deployment-directory"
            v-model="form.remoteDirectory"
            size="lg"
            maxlength="512"
            placeholder="/opt/workcove"
            spellcheck="false"
          />
        </UiFormField>

        <UiFormField
          label="部署脚本"
          layout="dialog"
          hint="可选择任意文本文件，脚本最大 512 KB"
          required
        >
          <div class="deployment-script">
            <div class="deployment-script__toolbar">
              <span class="deployment-script__file">
                <FileCode2 :size="15" />
                <strong>
                  {{ form.scriptFilename || '尚未选择脚本' }}
                </strong>
                <small v-if="form.scriptContent">
                  {{ scriptSizeLabel }}
                </small>
              </span>
              <UiButton
                size="sm"
                :disabled="servers.length === 0"
                @click="fileInput?.click()"
              >
                <CloudUpload :size="14" />
                上传脚本
              </UiButton>
              <input
                ref="fileInput"
                class="deployment-script__input"
                type="file"
                @change="handleScriptFile"
              />
            </div>
            <textarea
              v-model="form.scriptContent"
              class="deployment-script__editor"
              aria-label="部署脚本内容"
              rows="14"
              spellcheck="false"
              placeholder="上传脚本，或在这里直接粘贴 Bash 脚本"
            />
          </div>
        </UiFormField>

        <p v-if="errorMessage" class="deployment-form__error" role="alert">
          {{ errorMessage }}
        </p>
      </div>
    </form>

    <template #footer>
      <div class="deployment-form__actions">
        <UiButton
          v-if="deployment"
          variant="danger-ghost"
          size="lg"
          @click="removeDeployment"
        >
          <Trash2 :size="14" />
          删除部署
        </UiButton>
        <span v-else />
        <div>
          <UiButton size="lg" @click="emit('close')">取消</UiButton>
          <UiButton
            variant="primary"
            size="lg"
            type="submit"
            form="deployment-form"
          >
            保存部署
          </UiButton>
        </div>
      </div>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="confirmingRemove && deployment"
    title="删除部署配置？"
    description="部署配置和执行记录会从当前工作空间删除，不会删除服务器上的文件。"
    confirm-label="删除部署"
    @close="confirmingRemove = false"
    @confirm="confirmRemoveDeployment"
  >
    <strong>{{ deployment.name }}</strong>
    <p>{{ deployment.scriptFilename }}</p>
  </UiConfirmDialog>
</template>

<style scoped>
.deployment-form {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 var(--space-5);
}

.deployment-form__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  padding: 2px 0 var(--space-4);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.deployment-form__notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.deployment-form__notice svg {
  flex: 0 0 auto;
  color: var(--text-subtle);
}

.deployment-script {
  overflow: hidden;
  background: var(--canvas);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.deployment-script__toolbar {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.deployment-script__file {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
}

.deployment-script__file strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deployment-script__file small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.deployment-script__input {
  display: none;
}

.deployment-script__editor {
  display: block;
  width: 100%;
  min-height: 220px;
  padding: var(--space-3);
  resize: vertical;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  background: var(--surface-inset);
  border: 0;
}

.deployment-script__editor:focus {
  outline: 2px solid var(--system-blue);
  outline-offset: -2px;
}

.deployment-form__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.deployment-form__actions {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.deployment-form__actions > div {
  display: flex;
  gap: var(--space-2);
}

@media (max-width: 520px) {
  .deployment-form {
    padding: 0 var(--space-4) var(--space-4);
  }

  .deployment-form__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .deployment-form__actions > div {
    justify-content: flex-end;
  }
}
</style>
