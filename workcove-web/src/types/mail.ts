export interface MailAccount {
  id: number
  displayName: string
  emailAddress: string
  username: string
  imapHost: string
  imapPort: number
  imapSsl: boolean
  smtpHost: string
  smtpPort: number
  smtpSsl: boolean
  smtpAuth: boolean
  hasPassword: boolean
  createdAt: string
  updatedAt: string
}

export interface MailAccountInput {
  id?: number
  workspaceId: string
  displayName: string
  emailAddress: string
  username: string
  password: string
  imapHost: string
  imapPort: number
  imapSsl: boolean
  smtpHost: string
  smtpPort: number
  smtpSsl: boolean
  smtpAuth: boolean
}

export interface MailMessageSummary {
  folderPath: string
  uid: string
  messageId: string
  fromName: string
  fromAddress: string
  subject: string
  receivedAt: string
  preview: string
  unread: boolean
  starred: boolean
  attachmentCount: number
}

export interface MailMessagePage {
  messages: MailMessageSummary[]
  total: number
  offset: number
  limit: number
  hasMore: boolean
}

export interface MailSyncResult {
  folderCount: number
  messageCount: number
  syncedAt: string
}

export interface MailRecipient {
  name: string
  address: string
}

export interface MailAttachment {
  filename: string
  contentType: string
  size: number
}

export interface MailMessageDetail {
  folderPath: string
  uid: string
  messageId: string
  fromName: string
  fromAddress: string
  to: MailRecipient[]
  cc: MailRecipient[]
  subject: string
  receivedAt: string
  content: string
  html: boolean
  starred: boolean
  attachments: MailAttachment[]
}

export type MailFolderRole =
  | 'INBOX'
  | 'FLAGGED'
  | 'SENT'
  | 'DRAFTS'
  | 'TRASH'
  | 'JUNK'
  | 'CUSTOM'

export interface MailFolder {
  name: string
  path: string
  role: MailFolderRole
  unreadCount: number
  totalCount: number
}

export type MailRuleMatchMode = 'ALL' | 'ANY'

export interface MailRule {
  id: number
  accountId: number
  name: string
  matchMode: MailRuleMatchMode
  senderContains: string
  subjectContains: string
  requiresAttachment: boolean
  targetFolder: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface MailRuleInput {
  id?: number
  workspaceId: string
  accountId: number
  name: string
  matchMode: MailRuleMatchMode
  senderContains: string
  subjectContains: string
  requiresAttachment: boolean
  targetFolder: string
  enabled: boolean
}

export interface MailRecipientInput {
  name: string
  address: string
}

export interface MailOutgoingInput {
  workspaceId: string
  accountId: number
  to: MailRecipientInput[]
  cc: MailRecipientInput[]
  subject: string
  plainBody: string
  htmlBody: string
  inReplyTo: string
  readReceipt: boolean
}
