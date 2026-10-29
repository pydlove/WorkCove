<script setup lang="ts">
import { FolderPlus } from '@lucide/vue'
import { ref } from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import { useMailStore } from '@/stores/useMailStore'
import type { MailAccount, MailFolder } from '@/types/mail'

const props = defineProps<{
  account: MailAccount
}>()

const emit = defineEmits<{
  close: []
  created: [folder: MailFolder]
  notify: [message: string]
}>()

const mail = useMailStore()
const name = ref('')
const creating = ref(false)
const errorMessage = ref('')

async function create() {
  if (creating.value) return
  const value = name.value.trim()
  errorMessage.value = ''
  if (!value) {
    errorMessage.value = '请输入文件夹名称'
    return
  }
  creating.value = true
  try {
    const folder = await mail.createFolder(value)
    emit('notify', `文件夹「${folder.name}」已创建`)
    emit('created', folder)
    emit('close')
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法创建邮件文件夹'
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <UiDialog
    title="新建邮件文件夹"
    :description="`将在 ${account.emailAddress} 下创建 IMAP 文件夹。`"
    height="compact"
    initial-focus="#mail-folder-name"
    :suspended="creating"
    @close="emit('close')"
  >
    <form
      id="mail-folder-create-form"
      class="mail-folder-create"
      @submit.prevent="create"
    >
      <span class="mail-folder-create__icon">
        <FolderPlus :size="19" />
      </span>
      <UiFormField
        label="文件夹名称"
        control-id="mail-folder-name"
        hint="名称创建后可在 Foxmail 和其他 IMAP 客户端同步看到"
        required
      >
        <UiInput
          id="mail-folder-name"
          v-model="name"
          size="lg"
          maxlength="120"
          placeholder="例如：待处理"
        />
      </UiFormField>
      <p v-if="errorMessage" class="mail-folder-create__error" role="alert">
        {{ errorMessage }}
      </p>
    </form>
    <template #footer>
      <UiButton
        size="lg"
        :disabled="creating"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        type="submit"
        form="mail-folder-create-form"
        :loading="creating"
      >
        {{ creating ? '创建中…' : '创建文件夹' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.mail-folder-create {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: start;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-5);
}

.mail-folder-create__icon {
  display: grid;
  width: 32px;
  height: 32px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-folder-create__error {
  grid-column: 2;
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .mail-folder-create {
    grid-template-columns: 1fr;
    padding-right: var(--space-4);
    padding-left: var(--space-4);
  }

  .mail-folder-create__error {
    grid-column: auto;
  }
}
</style>
