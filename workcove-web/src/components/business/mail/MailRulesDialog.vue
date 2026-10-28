<script setup lang="ts">
import {
  ArrowRight,
  CheckCircle2,
  Filter,
  Folder,
  LoaderCircle,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiConfirmDialog from '@/components/ui/UiConfirmDialog.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import { useMailStore } from '@/stores/useMailStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import {
  deleteMailRule,
  listMailRules,
  saveMailRule,
} from '@/api/mail'
import type {
  MailAccount,
  MailFolderRole,
  MailRule,
  MailRuleMatchMode,
} from '@/types/mail'

const props = defineProps<{
  account: MailAccount
}>()

const emit = defineEmits<{
  close: []
  changed: []
  notify: [message: string]
}>()

const mail = useMailStore()
const workspaces = useWorkspaceStore()
const rules = ref<MailRule[]>([])
const loading = ref(false)
const saving = ref(false)
const toggling = ref<number | null>(null)
const deleting = ref<number | null>(null)
const editing = ref(false)
const editId = ref<number | null>(null)
const errorMessage = ref('')
const loadError = ref('')
const deleteTarget = ref<MailRule | null>(null)

const form = reactive({
  name: '',
  matchMode: 'ALL' as MailRuleMatchMode,
  senderContains: '',
  subjectContains: '',
  requiresAttachment: false,
  targetFolder: '',
  enabled: true,
})

const matchModeOptions = [
  { value: 'ALL', label: '满足全部' },
  { value: 'ANY', label: '满足任一' },
]
const folderOptions = computed(() =>
  mail.folders
    .filter((folder) => folder.role !== 'FLAGGED')
    .map((folder) => ({
      value: folder.path,
      label: folder.name,
      description: folderDescription(folder.role),
    })),
)

onMounted(() => {
  void loadRules()
})

async function loadRules() {
  if (!workspaces.activeWorkspaceId) return
  loading.value = true
  loadError.value = ''
  try {
    rules.value = await listMailRules({
      workspaceId: workspaces.activeWorkspaceId,
      accountId: props.account.id,
    })
  } catch (error) {
    loadError.value =
      error instanceof Error ? error.message : '无法读取邮件规则'
  } finally {
    loading.value = false
  }
}

function startNewRule() {
  editId.value = null
  form.name = ''
  form.matchMode = 'ALL'
  form.senderContains = ''
  form.subjectContains = ''
  form.requiresAttachment = false
  const customFolder = folderOptions.value.find((option) =>
    mail.folders.some(
      (folder) =>
        folder.path === option.value && folder.role === 'CUSTOM',
    ),
  )
  form.targetFolder =
    customFolder?.value ??
    folderOptions.value.find((option) => option.value !== 'INBOX')?.value ??
    folderOptions.value[0]?.value ??
    ''
  form.enabled = true
  errorMessage.value = ''
  editing.value = true
}

function startEditRule(rule: MailRule) {
  editId.value = rule.id
  form.name = rule.name
  form.matchMode = rule.matchMode
  form.senderContains = rule.senderContains
  form.subjectContains = rule.subjectContains
  form.requiresAttachment = rule.requiresAttachment
  form.targetFolder = rule.targetFolder
  form.enabled = rule.enabled
  errorMessage.value = ''
  editing.value = true
}

function cancelEdit() {
  if (saving.value) return
  editing.value = false
  editId.value = null
  errorMessage.value = ''
}

async function save() {
  if (saving.value) return
  errorMessage.value = ''
  if (!form.name.trim()) {
    errorMessage.value = '请输入规则名称'
    return
  }
  if (
    !form.senderContains.trim() &&
    !form.subjectContains.trim() &&
    !form.requiresAttachment
  ) {
    errorMessage.value = '请至少设置一个匹配条件'
    return
  }
  if (!form.targetFolder) {
    errorMessage.value = '请选择目标文件夹'
    return
  }

  saving.value = true
  try {
    const saved = await saveMailRule({
      id: editId.value ?? undefined,
      workspaceId: workspaces.activeWorkspaceId,
      accountId: props.account.id,
      name: form.name,
      matchMode: form.matchMode,
      senderContains: form.senderContains,
      subjectContains: form.subjectContains,
      requiresAttachment: form.requiresAttachment,
      targetFolder: form.targetFolder,
      enabled: form.enabled,
    })
    editing.value = false
    editId.value = null
    await loadRules()
    emit('changed')
    emit('notify', `规则「${saved.name}」已保存`)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '无法保存邮件规则'
  } finally {
    saving.value = false
  }
}

async function toggleRule(rule: MailRule, enabled: boolean) {
  if (toggling.value !== null) return
  toggling.value = rule.id
  try {
    const updated = await saveMailRule({
      id: rule.id,
      workspaceId: workspaces.activeWorkspaceId,
      accountId: props.account.id,
      name: rule.name,
      matchMode: rule.matchMode,
      senderContains: rule.senderContains,
      subjectContains: rule.subjectContains,
      requiresAttachment: rule.requiresAttachment,
      targetFolder: rule.targetFolder,
      enabled,
    })
    rules.value = rules.value.map((item) =>
      item.id === updated.id ? updated : item,
    )
    emit('changed')
    emit('notify', enabled ? '规则已启用' : '规则已停用')
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法更新规则状态',
    )
  } finally {
    toggling.value = null
  }
}

async function confirmDelete() {
  const rule = deleteTarget.value
  if (!rule || deleting.value !== null) return
  deleting.value = rule.id
  try {
    await deleteMailRule({
      workspaceId: workspaces.activeWorkspaceId,
      accountId: props.account.id,
      ruleId: rule.id,
    })
    rules.value = rules.value.filter((item) => item.id !== rule.id)
    deleteTarget.value = null
    emit('changed')
    emit('notify', `规则「${rule.name}」已删除`)
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法删除邮件规则',
    )
  } finally {
    deleting.value = null
  }
}

function conditionSummary(rule: MailRule) {
  const parts: string[] = []
  if (rule.senderContains.trim()) {
    parts.push(`发件人包含「${rule.senderContains.trim()}」`)
  }
  if (rule.subjectContains.trim()) {
    parts.push(`主题包含「${rule.subjectContains.trim()}」`)
  }
  if (rule.requiresAttachment) {
    parts.push('带有附件')
  }
  const connector = rule.matchMode === 'ALL' ? '，且 ' : '，或 '
  return parts.join(connector)
}

function folderName(path: string) {
  return mail.folders.find((folder) => folder.path === path)?.name ?? path
}

function folderDescription(role: MailFolderRole) {
  return role === 'CUSTOM' ? '自定义文件夹' : '默认文件夹'
}
</script>

<template>
  <UiDialog
    title="收信规则"
    :description="`为 ${account.emailAddress} 自动归类未读邮件；规则按当前顺序匹配，命中第一条后停止。`"
    size="wide"
    height="standard"
    scroll-mode="body"
    max-height="min(720px, calc(100dvh - 32px))"
    :suspended="saving"
    @close="emit('close')"
  >
    <section class="mail-rules">
      <template v-if="!editing">
        <header class="mail-rules__toolbar">
          <div>
            <span class="mail-rules__eyebrow">
              <Filter :size="13" />
              自动归类
            </span>
            <strong>{{ rules.length }} 条规则</strong>
          </div>
          <div>
            <UiButton
              size="sm"
              :disabled="loading"
              @click="loadRules"
            >
              <RefreshCw
                :size="13"
                :class="{ 'mail-rules-spin': loading }"
              />
              刷新
            </UiButton>
            <UiButton
              variant="primary"
              size="sm"
              :disabled="folderOptions.length === 0"
              @click="startNewRule"
            >
              <Plus :size="13" />
              新建规则
            </UiButton>
          </div>
        </header>

        <p v-if="loadError" class="mail-rules__error" role="alert">
          {{ loadError }}
        </p>

        <div v-if="loading && rules.length === 0" class="mail-rules__state">
          <LoaderCircle class="mail-rules-spin" :size="22" />
          <span>正在读取规则…</span>
        </div>

        <div v-else-if="rules.length === 0" class="mail-rules__empty">
          <span><Filter :size="24" /></span>
          <strong>还没有收信规则</strong>
          <p>设置发件人、主题或附件条件，让邮件自动进入指定目录。</p>
          <UiButton
            variant="primary"
            size="sm"
            :disabled="folderOptions.length === 0"
            @click="startNewRule"
          >
            <Plus :size="13" />
            创建第一条规则
          </UiButton>
        </div>

        <div v-else class="mail-rule-list">
          <article
            v-for="(rule, index) in rules"
            :key="rule.id"
            :class="{ 'is-disabled': !rule.enabled }"
          >
            <span class="mail-rule-list__index">{{ index + 1 }}</span>
            <div class="mail-rule-list__main">
              <header>
                <strong>{{ rule.name }}</strong>
                <span
                  v-if="rule.enabled"
                  class="mail-rule-list__status"
                >
                  <CheckCircle2 :size="12" />
                  已启用
                </span>
                <span v-else class="mail-rule-list__status is-off">
                  已停用
                </span>
              </header>
              <p>{{ conditionSummary(rule) }}</p>
              <div class="mail-rule-list__target">
                <ArrowRight :size="12" />
                <Folder :size="13" />
                移入 {{ folderName(rule.targetFolder) }}
              </div>
            </div>
            <div class="mail-rule-list__actions">
              <UiCheckbox
                :model-value="rule.enabled"
                :label="rule.enabled ? '停用规则' : '启用规则'"
                :disabled="toggling !== null"
                @update:model-value="toggleRule(rule, $event)"
              />
              <button
                type="button"
                title="编辑规则"
                aria-label="编辑规则"
                @click="startEditRule(rule)"
              >
                <Pencil :size="14" />
              </button>
              <button
                type="button"
                title="删除规则"
                aria-label="删除规则"
                @click="deleteTarget = rule"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </article>
        </div>
      </template>

      <form
        v-else
        id="mail-rule-form"
        class="mail-rule-editor"
        @submit.prevent="save"
      >
        <header>
          <span><Filter :size="16" /></span>
          <div>
            <strong>{{ editId ? '编辑规则' : '新建规则' }}</strong>
            <p>只处理刷新时收件箱中的未读邮件。</p>
          </div>
        </header>

        <div class="mail-rule-editor__grid">
          <UiFormField
            label="规则名称"
            control-id="mail-rule-name"
            required
          >
            <UiInput
              id="mail-rule-name"
              v-model="form.name"
              maxlength="120"
              placeholder="例如：项目通知"
            />
          </UiFormField>

          <UiFormField label="匹配方式">
            <UiSegmentedControl
              v-model="form.matchMode"
              :items="matchModeOptions"
              aria-label="规则匹配方式"
            />
          </UiFormField>
        </div>

        <div class="mail-rule-editor__conditions">
          <div class="mail-rule-editor__section-heading">
            <strong>匹配条件</strong>
            <span>留空的条件不会参与匹配</span>
          </div>
          <UiFormField
            label="发件人包含"
            control-id="mail-rule-sender"
          >
            <UiInput
              id="mail-rule-sender"
              v-model="form.senderContains"
              maxlength="320"
              placeholder="name@example.com 或发件人名称"
            />
          </UiFormField>
          <UiFormField
            label="主题包含"
            control-id="mail-rule-subject"
          >
            <UiInput
              id="mail-rule-subject"
              v-model="form.subjectContains"
              maxlength="500"
              placeholder="例如：发布通知"
            />
          </UiFormField>
          <label class="mail-rule-editor__check">
            <UiCheckbox
              v-model="form.requiresAttachment"
              label="带有附件"
            />
            <span>带有附件</span>
          </label>
        </div>

        <div class="mail-rule-editor__grid">
          <UiFormField label="移入文件夹" required>
            <UiSelect
              v-model="form.targetFolder"
              :options="folderOptions"
              placeholder="选择默认或自定义文件夹"
              aria-label="选择规则目标文件夹"
            />
          </UiFormField>
          <label class="mail-rule-editor__check mail-rule-editor__check--end">
            <UiCheckbox
              v-model="form.enabled"
              label="保存后启用"
            />
            <span>保存后启用</span>
          </label>
        </div>

        <p v-if="folderOptions.length === 0" class="mail-rules__hint">
          当前邮箱还没有可用目录，请先刷新邮件目录。
        </p>
        <p v-if="errorMessage" class="mail-rules__error" role="alert">
          {{ errorMessage }}
        </p>
      </form>
    </section>

    <template #footer>
      <template v-if="editing">
        <UiButton
          size="lg"
          :disabled="saving"
          @click="cancelEdit"
        >
          返回列表
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          type="submit"
          form="mail-rule-form"
          :disabled="saving || folderOptions.length === 0"
        >
          {{ saving ? '保存中…' : '保存规则' }}
        </UiButton>
      </template>
      <template v-else>
        <UiButton size="lg" @click="emit('close')">关闭</UiButton>
      </template>
    </template>
  </UiDialog>

  <UiConfirmDialog
    v-if="deleteTarget"
    title="删除收信规则？"
    :description="`删除后「${deleteTarget.name}」将不再自动归类邮件。`"
    confirm-label="删除规则"
    busy-label="正在删除…"
    :busy="deleting !== null"
    @close="deleteTarget = null"
    @confirm="confirmDelete"
  />
</template>

<style scoped>
.mail-rules {
  min-height: 0;
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.mail-rules__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.mail-rules__toolbar > div {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.mail-rules__toolbar > div:first-child {
  align-items: flex-start;
  flex-direction: column;
  gap: 2px;
}

.mail-rules__toolbar strong {
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.mail-rules__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-rules__error {
  margin: var(--space-3) 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.mail-rules__hint {
  margin: var(--space-2) 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-rules__state,
.mail-rules__empty {
  display: flex;
  min-height: 260px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-subtle);
  text-align: center;
}

.mail-rules__state span,
.mail-rules__empty p {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-rules__empty > span {
  display: grid;
  width: 48px;
  height: 48px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-panel);
  place-items: center;
}

.mail-rules__empty strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-rules__empty p {
  max-width: 320px;
  margin: 0 0 var(--space-2);
  line-height: var(--line-caption);
}

.mail-rule-list {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.mail-rule-list article {
  display: grid;
  min-width: 0;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-bottom: 1px solid var(--line-soft);
}

.mail-rule-list article:last-child {
  border-bottom: 0;
}

.mail-rule-list article.is-disabled {
  background: color-mix(
    in srgb,
    var(--surface-secondary) 62%,
    transparent
  );
}

.mail-rule-list__index {
  display: grid;
  width: 24px;
  height: 24px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--surface-inset);
  border-radius: var(--radius-badge);
  place-items: center;
}

.mail-rule-list__main {
  min-width: 0;
}

.mail-rule-list__main header {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.mail-rule-list__main header strong {
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-rule-list__status {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 3px;
  color: var(--system-green);
  font-size: var(--font-caption);
}

.mail-rule-list__status.is-off {
  color: var(--text-subtle);
}

.mail-rule-list__main p {
  overflow: hidden;
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-rule-list__target {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: var(--accent);
  font-size: var(--font-caption);
}

.mail-rule-list__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.mail-rule-list__actions > button {
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-rule-list__actions > button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.mail-rule-list__actions > button:last-child:hover {
  color: var(--system-red);
}

.mail-rule-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.mail-rule-editor > header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.mail-rule-editor > header > span {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-rule-editor > header strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-rule-editor > header p {
  margin: 3px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.mail-rule-editor__grid,
.mail-rule-editor__conditions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.mail-rule-editor__conditions {
  padding: var(--space-4);
  background: var(--surface-secondary);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-control);
}

.mail-rule-editor__section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  grid-column: 1 / -1;
  gap: var(--space-3);
}

.mail-rule-editor__section-heading strong {
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.mail-rule-editor__section-heading span {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-rule-editor__check {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  width: fit-content;
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-rule-editor__check--end {
  align-self: end;
  min-height: var(--control-md);
}

.mail-rules-spin {
  animation: mail-rules-spin 800ms linear infinite;
}

@keyframes mail-rules-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 620px) {
  .mail-rules {
    padding-right: var(--space-4);
    padding-left: var(--space-4);
  }

  .mail-rules__toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .mail-rule-list article {
    grid-template-columns: 24px minmax(0, 1fr);
  }

  .mail-rule-list__actions {
    grid-column: 2;
    justify-content: flex-end;
  }

  .mail-rule-editor__grid,
  .mail-rule-editor__conditions {
    grid-template-columns: 1fr;
  }

  .mail-rule-editor__section-heading {
    grid-column: auto;
  }
}
</style>
