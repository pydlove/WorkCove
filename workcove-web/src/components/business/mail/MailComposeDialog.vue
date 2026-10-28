<script setup lang="ts">
import {
  Bold,
  Italic,
  Link2,
  List,
  ListOrdered,
  LoaderCircle,
  Quote,
  Send,
  Underline,
} from '@lucide/vue'
import DOMPurify from 'dompurify'
import {
  computed,
  nextTick,
  onMounted,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiCheckbox from '@/components/ui/UiCheckbox.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import { useMailStore } from '@/stores/useMailStore'
import type {
  MailAccount,
  MailMessageDetail,
  MailRecipientInput,
} from '@/types/mail'

export type MailComposeMode =
  | 'new'
  | 'reply'
  | 'replyAll'
  | 'forward'

const props = defineProps<{
  mode: MailComposeMode
  account: MailAccount
  message?: MailMessageDetail | null
}>()

const emit = defineEmits<{
  close: []
  sent: []
  notify: [message: string]
}>()

const mail = useMailStore()
const editor = ref<HTMLElement | null>(null)
const toInput = ref(initialTo())
const ccVisible = ref(initialCc().length > 0)
const ccInput = ref(initialCc().map(formatRecipient).join(', '))
const subjectInput = ref(initialSubject())
const readReceipt = ref(false)
const errorMessage = ref('')

const title = computed(() => {
  if (props.mode === 'reply') return '回复邮件'
  if (props.mode === 'replyAll') return '回复全部'
  if (props.mode === 'forward') return '转发邮件'
  return '写邮件'
})
const initialHtml = computed(() => {
  if (!props.message || props.mode === 'new') return '<p><br></p>'
  return quotedBody(props.message)
})

onMounted(() => {
  void nextTick(() => {
    if (!editor.value) return
    editor.value.focus()
    placeCaretAtStart(editor.value)
  })
})

function initialTo() {
  if (!props.message) return ''
  if (props.mode === 'reply') {
    return formatRecipient({
      name: props.message.fromName,
      address: props.message.fromAddress,
    })
  }
  if (props.mode === 'replyAll') {
    return uniqueRecipients([
      {
        name: props.message.fromName,
        address: props.message.fromAddress,
      },
      ...props.message.to,
    ]).map(formatRecipient).join(', ')
  }
  return ''
}

function initialCc() {
  if (!props.message || props.mode !== 'replyAll') return []
  return uniqueRecipients(props.message.cc)
}

function initialSubject() {
  if (!props.message || props.mode === 'new') return ''
  const subject = props.message.subject || '(无主题)'
  if (props.mode === 'forward') {
    return /^fwd?:/i.test(subject) ? subject : `Fwd: ${subject}`
  }
  return /^re:/i.test(subject) ? subject : `Re: ${subject}`
}

function uniqueRecipients(recipients: MailRecipientInput[]) {
  const own = new Set([
    props.account.emailAddress.toLocaleLowerCase('en-US'),
    props.account.username.toLocaleLowerCase('en-US'),
  ])
  const seen = new Set<string>()
  return recipients.filter((recipient) => {
    const address =
      recipient.address.trim().toLocaleLowerCase('en-US')
    if (!address || own.has(address) || seen.has(address)) return false
    seen.add(address)
    return true
  })
}

function formatRecipient(recipient: MailRecipientInput) {
  if (!recipient.name) return recipient.address
  return `${recipient.name} <${recipient.address}>`
}

function quotedBody(message: MailMessageDetail) {
  const safeContent = message.html
    ? sanitizeHtml(message.content)
    : `<pre>${escapeHtml(message.content)}</pre>`
  return [
    '<p><br></p>',
    `<p class="mail-compose-quote-meta">在 ${escapeHtml(formatFullTime(message.receivedAt))}，${escapeHtml(message.fromName || message.fromAddress)} &lt;${escapeHtml(message.fromAddress)}&gt; 写道：</p>`,
    `<blockquote class="mail-compose-quote">${safeContent}</blockquote>`,
  ].join('')
}

function sanitizeHtml(value: string) {
  return DOMPurify.sanitize(value, {
    ALLOWED_TAGS: [
      'a',
      'b',
      'blockquote',
      'br',
      'code',
      'div',
      'em',
      'h1',
      'h2',
      'h3',
      'h4',
      'hr',
      'i',
      'li',
      'ol',
      'p',
      'pre',
      'span',
      'strong',
      'table',
      'tbody',
      'td',
      'th',
      'thead',
      'tr',
      'u',
      'ul',
    ],
    ALLOWED_ATTR: [
      'colspan',
      'href',
      'rowspan',
      'title',
    ],
    FORBID_TAGS: [
      'button',
      'embed',
      'form',
      'iframe',
      'img',
      'input',
      'object',
      'script',
      'style',
      'video',
    ],
  })
}

function formatEditorHtml() {
  const html = editor.value?.innerHTML ?? ''
  return sanitizeHtml(html)
}

function plainBody() {
  return editor.value?.innerText?.trim() ?? ''
}

function format(command: string, value?: string) {
  editor.value?.focus()
  document.execCommand(command, false, value)
}

function addLink() {
  const url = window.prompt('输入链接地址')
  if (!url) return
  if (!/^https?:\/\//i.test(url)) {
    errorMessage.value = '链接必须以 http:// 或 https:// 开头'
    return
  }
  format('createLink', url)
}

function handlePaste(event: ClipboardEvent) {
  event.preventDefault()
  const text = event.clipboardData?.getData('text/plain') ?? ''
  if (text) {
    document.execCommand('insertText', false, text)
  }
}

function parseRecipients(value: string) {
  const parts = value
    .split(/[;,]/)
    .map((part) => part.trim())
    .filter(Boolean)
  const result: MailRecipientInput[] = []
  const seen = new Set<string>()
  for (const part of parts) {
    const angle = part.match(/^(.*?)<([^<>]+)>$/)
    const name = angle?.[1]?.trim().replace(/^"|"$/g, '') ?? ''
    const address = (angle?.[2] ?? part).trim()
    if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address) ||
      seen.has(address.toLowerCase())
    ) {
      continue
    }
    seen.add(address.toLowerCase())
    result.push({ name, address })
  }
  return result
}

async function send() {
  if (mail.sending) return
  errorMessage.value = ''
  const to = parseRecipients(toInput.value)
  const cc = parseRecipients(ccInput.value)
  if (to.length === 0) {
    errorMessage.value = '请填写至少一个有效的收件人'
    return
  }
  const htmlBody = formatEditorHtml()
  const plain = plainBody()
  if (!plain && !htmlBody) {
    errorMessage.value = '请输入邮件正文'
    return
  }
  try {
    await mail.sendMessage({
      to,
      cc,
      subject: subjectInput.value.trim() || '(无主题)',
      plainBody: plain,
      htmlBody,
      inReplyTo:
        props.mode === 'reply' || props.mode === 'replyAll'
          ? props.message?.messageId ?? ''
          : '',
      readReceipt: readReceipt.value,
    })
    emit('notify', '邮件已发送')
    emit('sent')
    emit('close')
  } catch (error) {
    errorMessage.value =
      mail.messageError ||
      (error instanceof Error ? error.message : '邮件发送失败')
  }
}

function placeCaretAtStart(element: HTMLElement) {
  const range = document.createRange()
  range.selectNodeContents(element)
  range.collapse(true)
  const selection = window.getSelection()
  selection?.removeAllRanges()
  selection?.addRange(range)
}

function formatFullTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}
</script>

<template>
  <UiDialog
    :title="title"
    :description="`发件账号：${account.emailAddress}`"
    size="wide"
    scroll-mode="body"
    max-height="min(820px, calc(100dvh - 32px))"
    :suspended="mail.sending"
    @close="emit('close')"
  >
    <div class="mail-compose">
      <label class="mail-compose__field">
        <span>收件人</span>
        <input
          v-model="toInput"
          type="text"
          autocomplete="off"
          placeholder="姓名 <name@example.com>，多个地址用逗号分隔"
          aria-label="收件人"
        />
      </label>
      <label v-if="ccVisible" class="mail-compose__field">
        <span>抄送</span>
        <input
          v-model="ccInput"
          type="text"
          autocomplete="off"
          placeholder="多个地址用逗号分隔"
          aria-label="抄送"
        />
        <button
          type="button"
          @click="
            ccVisible = false;
            ccInput = ''
          "
        >
          取消抄送
        </button>
      </label>
      <button
        v-else
        class="mail-compose__add-cc"
        type="button"
        @click="ccVisible = true"
      >
        添加抄送
      </button>
      <label class="mail-compose__field">
        <span>主题</span>
        <input
          v-model="subjectInput"
          type="text"
          maxlength="500"
          placeholder="邮件主题"
          aria-label="邮件主题"
        />
      </label>

      <div class="mail-compose__toolbar" aria-label="正文格式">
        <button type="button" title="加粗" aria-label="加粗" @mousedown.prevent="format('bold')">
          <Bold :size="15" />
        </button>
        <button type="button" title="斜体" aria-label="斜体" @mousedown.prevent="format('italic')">
          <Italic :size="15" />
        </button>
        <button type="button" title="下划线" aria-label="下划线" @mousedown.prevent="format('underline')">
          <Underline :size="15" />
        </button>
        <span />
        <button type="button" title="无序列表" aria-label="无序列表" @mousedown.prevent="format('insertUnorderedList')">
          <List :size="15" />
        </button>
        <button type="button" title="有序列表" aria-label="有序列表" @mousedown.prevent="format('insertOrderedList')">
          <ListOrdered :size="15" />
        </button>
        <button type="button" title="引用" aria-label="引用" @mousedown.prevent="format('formatBlock', 'blockquote')">
          <Quote :size="15" />
        </button>
        <button type="button" title="链接" aria-label="插入链接" @mousedown.prevent="addLink">
          <Link2 :size="15" />
        </button>
      </div>

      <div
        ref="editor"
        class="mail-compose__editor"
        contenteditable="true"
        role="textbox"
        aria-label="邮件正文"
        aria-multiline="true"
        @paste="handlePaste"
        v-html="initialHtml"
      ></div>

      <p v-if="errorMessage" class="mail-compose__error" role="alert">
        {{ errorMessage }}
      </p>
    </div>

    <template #footer>
      <div class="mail-compose__footer">
        <span>发送 {{ account.emailAddress }}</span>
        <div class="mail-compose__receipt">
          <UiCheckbox v-model="readReceipt" label="已读回执" />
          <button type="button" @click="readReceipt = !readReceipt">
            已读回执
          </button>
        </div>
      </div>
      <UiButton
        size="lg"
        :disabled="mail.sending"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        :disabled="mail.sending"
        @click="send"
      >
        <LoaderCircle
          v-if="mail.sending"
          class="mail-compose__spin"
          :size="14"
        />
        <Send v-else :size="14" />
        {{ mail.sending ? '发送中…' : '发送' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.mail-compose {
  padding: 0 var(--space-5) var(--space-4);
}

.mail-compose__field {
  display: grid;
  min-width: 0;
  min-height: 40px;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.mail-compose__field > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.mail-compose__field input {
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.mail-compose__field input:focus {
  outline: none;
}

.mail-compose__field button,
.mail-compose__add-cc {
  padding: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: transparent;
}

.mail-compose__add-cc {
  height: 30px;
}

.mail-compose__field button:hover,
.mail-compose__add-cc:hover {
  color: var(--text);
}

.mail-compose__toolbar {
  display: flex;
  height: 38px;
  align-items: center;
  gap: 2px;
  padding: 0 var(--space-1);
  margin-top: var(--space-2);
  border-bottom: 1px solid var(--line-soft);
}

.mail-compose__toolbar button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.mail-compose__toolbar button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.mail-compose__toolbar > span {
  width: 1px;
  height: 18px;
  margin: 0 5px;
  background: var(--line-soft);
}

.mail-compose__editor {
  min-height: 290px;
  padding: var(--space-4) var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.mail-compose__editor:focus {
  outline: none;
}

.mail-compose__editor :deep(blockquote) {
  padding-left: var(--space-3);
  margin: var(--space-3) 0;
  color: var(--text-muted);
  border-left: 2px solid var(--line);
}

.mail-compose__editor :deep(.mail-compose-quote-meta) {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-compose__editor :deep(table) {
  max-width: 100%;
  border-collapse: collapse;
}

.mail-compose__editor :deep(th),
.mail-compose__editor :deep(td) {
  padding: 4px 6px;
  border: 1px solid var(--line-soft);
}

.mail-compose__error {
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

.mail-compose__footer {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: var(--space-4);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.mail-compose__footer > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-compose__receipt {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
}

.mail-compose__receipt button {
  padding: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  background: transparent;
}

.mail-compose__spin {
  animation: mail-compose-spin 800ms linear infinite;
}

@keyframes mail-compose-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 620px) {
  .mail-compose {
    padding-right: var(--space-4);
    padding-left: var(--space-4);
  }

  .mail-compose__field {
    grid-template-columns: 48px minmax(0, 1fr) auto;
  }

  .mail-compose__editor {
    min-height: 240px;
  }

  .mail-compose__footer {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-1);
  }
}
</style>
