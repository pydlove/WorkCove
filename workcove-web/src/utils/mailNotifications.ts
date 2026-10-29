const MAX_NOTIFICATIONS = 60
const NOTIFICATION_RETENTION_MS = 30 * 24 * 60 * 60 * 1000

export interface MailInboxNotification {
  source: 'mail'
  id: string
  workspaceId: string
  accountId: number
  accountName: string
  accountAddress: string
  title: string
  body: string
  linkView: 'mail'
  readAt: string | null
  createdAt: string
}

export interface MailNotificationSnapshot {
  signatures: string[]
  checkedAt: string
}

export interface MailNotificationState {
  snapshots: Record<string, MailNotificationSnapshot>
  notifications: MailInboxNotification[]
}

export interface MailNotificationAccount {
  id: number
  displayName: string
  emailAddress: string
}

export interface MailNotificationMessage {
  uid: string
  messageId: string
  fromName: string
  fromAddress: string
  subject: string
  receivedAt: string
  unread: boolean
}

export function applyMailInboxCheck(
  state: MailNotificationState,
  workspaceId: string,
  account: MailNotificationAccount,
  messages: MailNotificationMessage[],
  checkedAt: string,
) {
  const snapshotKey = mailSnapshotKey(workspaceId, account.id)
  const previous = state.snapshots[snapshotKey]
  const signatures = messages.map(messageSignature)
  const nextSnapshots = {
    ...state.snapshots,
    [snapshotKey]: {
      signatures,
      checkedAt,
    },
  }
  if (!previous) {
    return {
      snapshots: nextSnapshots,
      notifications: state.notifications,
    }
  }

  const known = new Set(previous.signatures)
  const newUnread = messages.filter(
    (message) =>
      message.unread &&
      !known.has(messageSignature(message)),
  )
  if (newUnread.length === 0) {
    return {
      snapshots: nextSnapshots,
      notifications: state.notifications,
    }
  }

  const latest = newUnread[0]
  const notification: MailInboxNotification = {
    source: 'mail',
    id: [
      'mail',
      workspaceId,
      account.id,
      latest.messageId || latest.uid,
    ].join(':'),
    workspaceId,
    accountId: account.id,
    accountName: account.displayName,
    accountAddress: account.emailAddress,
    title:
      newUnread.length === 1
        ? `${latest.fromName || latest.fromAddress || account.displayName} 发来新邮件`
        : `${account.displayName} 收到 ${newUnread.length} 封新邮件`,
    body:
      newUnread.length === 1
        ? latest.subject || '(无主题)'
        : `${latest.subject || '(无主题)'} 等 ${newUnread.length} 封`,
    linkView: 'mail',
    readAt: null,
    createdAt: validInstant(latest.receivedAt, checkedAt),
  }
  return {
    snapshots: nextSnapshots,
    notifications: [
      notification,
      ...state.notifications.filter(
        (item) => item.id !== notification.id,
      ),
    ].slice(0, MAX_NOTIFICATIONS),
  }
}

export function normalizeMailNotificationState(
  value: unknown,
): MailNotificationState {
  if (!value || typeof value !== 'object') {
    return { snapshots: {}, notifications: [] }
  }
  const source = value as Partial<MailNotificationState>
  const snapshots: Record<string, MailNotificationSnapshot> = {}
  if (source.snapshots && typeof source.snapshots === 'object') {
    for (const [key, snapshot] of Object.entries(source.snapshots)) {
      if (
        !snapshot ||
        typeof snapshot !== 'object' ||
        !Array.isArray(
          (snapshot as MailNotificationSnapshot).signatures,
        )
      ) {
        continue
      }
      snapshots[key] = {
        signatures: (
          snapshot as MailNotificationSnapshot
        ).signatures.filter(
          (signature): signature is string =>
            typeof signature === 'string',
        ),
        checkedAt:
          typeof (snapshot as MailNotificationSnapshot).checkedAt ===
          'string'
            ? (snapshot as MailNotificationSnapshot).checkedAt
            : new Date(0).toISOString(),
      }
    }
  }
  const notifications = Array.isArray(source.notifications)
    ? source.notifications
        .filter(isMailNotification)
        .filter(isRecentNotification)
        .slice(0, MAX_NOTIFICATIONS)
    : []
  return { snapshots, notifications }
}

export function compareMailNotifications(
  left: MailInboxNotification,
  right: MailInboxNotification,
) {
  return (
    new Date(right.createdAt).getTime() -
    new Date(left.createdAt).getTime()
  )
}

function isMailNotification(
  value: unknown,
): value is MailInboxNotification {
  if (!value || typeof value !== 'object') return false
  const notification = value as Partial<MailInboxNotification>
  return (
    notification.source === 'mail' &&
    typeof notification.id === 'string' &&
    typeof notification.workspaceId === 'string' &&
    typeof notification.accountId === 'number' &&
    typeof notification.accountName === 'string' &&
    typeof notification.accountAddress === 'string' &&
    typeof notification.title === 'string' &&
    typeof notification.body === 'string' &&
    notification.linkView === 'mail' &&
    typeof notification.createdAt === 'string' &&
    (notification.readAt === null ||
      typeof notification.readAt === 'string')
  )
}

function isRecentNotification(notification: MailInboxNotification) {
  const createdAt = new Date(notification.createdAt).getTime()
  return (
    Number.isFinite(createdAt) &&
    Date.now() - createdAt <= NOTIFICATION_RETENTION_MS
  )
}

function messageSignature(message: MailNotificationMessage) {
  return [
    message.uid,
    message.messageId || '',
    message.receivedAt,
  ].join(':')
}

function mailSnapshotKey(workspaceId: string, accountId: number) {
  return `${workspaceId}:${accountId}`
}

function validInstant(value: string, fallback: string) {
  return Number.isFinite(new Date(value).getTime()) ? value : fallback
}
