import {
  deleteNoContentWithHeaders,
  get,
  postJsonNoContentWithHeaders,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  MailAccount,
  MailAccountInput,
  MailFolder,
  MailMessageDetail,
  MailMessagePage,
  MailMessageSummary,
  MailOutgoingInput,
  MailRule,
  MailRuleInput,
  MailSyncResult,
} from '@/types/mail'

const ACTION_HEADER = {
  'X-WorkCove-Action': 'manage-mail',
}

export function listMailAccounts(workspaceId: string) {
  return get<MailAccount[]>(
    `/mail/accounts?workspaceId=${encodeURIComponent(workspaceId)}`,
  )
}

export function saveMailAccount(input: MailAccountInput) {
  return putJsonWithHeaders<MailAccount>(
    '/mail/accounts',
    input,
    ACTION_HEADER,
    60_000,
  )
}

export function deleteMailAccount(
  accountId: number,
  workspaceId: string,
) {
  return deleteNoContentWithHeaders(
    `/mail/accounts/${accountId}?workspaceId=${encodeURIComponent(workspaceId)}`,
    ACTION_HEADER,
  )
}

export function listMailInbox(input: {
  workspaceId: string
  accountId: number
  folder?: string
  flagged?: boolean
  limit?: number
  offset?: number
}) {
  return postJsonWithHeaders<MailMessagePage>(
    '/mail/messages',
    {
      ...input,
      limit: input.limit ?? 40,
      offset: input.offset ?? 0,
    },
    ACTION_HEADER,
    60_000,
  )
}

export function getMailMessage(input: {
  workspaceId: string
  accountId: number
  folder: string
  uid: string
}) {
  return postJsonWithHeaders<MailMessageDetail>(
    '/mail/message',
    input,
    ACTION_HEADER,
    45_000,
  )
}

export function replyMail(input: {
  workspaceId: string
  accountId: number
  folder: string
  uid: string
  body: string
}) {
  return postJsonNoContentWithHeaders(
    '/mail/reply',
    input,
    ACTION_HEADER,
    30_000,
  )
}

export function listMailFolders(input: {
  workspaceId: string
  accountId: number
}) {
  return postJsonWithHeaders<MailFolder[]>(
    '/mail/folders',
    input,
    ACTION_HEADER,
    60_000,
  )
}

export function createMailFolder(input: {
  workspaceId: string
  accountId: number
  name: string
}) {
  return postJsonWithHeaders<MailFolder>(
    '/mail/folders/create',
    input,
    ACTION_HEADER,
    45_000,
  )
}

export function syncMail(input: {
  workspaceId: string
  accountId: number
  folder: string
  flagged: boolean
}) {
  return postJsonWithHeaders<MailSyncResult>(
    '/mail/sync',
    input,
    ACTION_HEADER,
    300_000,
  )
}

export function listMailRules(input: {
  workspaceId: string
  accountId: number
}) {
  const query = new URLSearchParams({
    workspaceId: input.workspaceId,
    accountId: String(input.accountId),
  })
  return get<MailRule[]>(`/mail/rules?${query.toString()}`)
}

export function saveMailRule(input: MailRuleInput) {
  return putJsonWithHeaders<MailRule>(
    '/mail/rules',
    input,
    ACTION_HEADER,
    30_000,
  )
}

export function deleteMailRule(input: {
  workspaceId: string
  accountId: number
  ruleId: number
}) {
  const query = new URLSearchParams({
    workspaceId: input.workspaceId,
    accountId: String(input.accountId),
  })
  return deleteNoContentWithHeaders(
    `/mail/rules/${input.ruleId}?${query.toString()}`,
    ACTION_HEADER,
  )
}

export function setMailStarred(input: {
  workspaceId: string
  accountId: number
  folder: string
  uid: string
  starred: boolean
}) {
  return postJsonNoContentWithHeaders(
    '/mail/star',
    input,
    ACTION_HEADER,
    30_000,
  )
}

export function sendMail(input: MailOutgoingInput) {
  return postJsonNoContentWithHeaders(
    '/mail/send',
    input,
    ACTION_HEADER,
    60_000,
  )
}

export function setMailReadState(input: {
  workspaceId: string
  accountId: number
  folder: string
  uid: string
  seen: boolean
}) {
  return postJsonNoContentWithHeaders(
    '/mail/read-state',
    input,
    ACTION_HEADER,
    30_000,
  )
}

export function markAllMailRead(input: {
  workspaceId: string
  accountId: number
  folder: string
}) {
  return postJsonNoContentWithHeaders(
    '/mail/mark-all-read',
    input,
    ACTION_HEADER,
    45_000,
  )
}

export function moveMail(input: {
  workspaceId: string
  accountId: number
  folder: string
  uid: string
  targetFolder: string
}) {
  return postJsonNoContentWithHeaders(
    '/mail/move',
    input,
    ACTION_HEADER,
    45_000,
  )
}

export function mailExportUrl(input: {
  workspaceId: string
  accountId: number
  folder: string
  uid: string
}) {
  const query = new URLSearchParams({
    workspaceId: input.workspaceId,
    accountId: String(input.accountId),
    folder: input.folder,
    uid: input.uid,
  })
  return `/api/mail/export?${query.toString()}`
}
