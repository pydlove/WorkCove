import { checkMailInbox, listMailAccounts } from '@/api/mail'
import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import {
  applyMailInboxCheck,
  compareMailNotifications,
  normalizeMailNotificationState,
  type MailInboxNotification,
  type MailNotificationState,
} from '@/utils/mailNotifications'

const STORAGE_KEY = 'workcove.mail-notifications.v1'

export type { MailInboxNotification } from '@/utils/mailNotifications'

export function loadMailNotifications(
  email: string,
  workspaceId: string,
) {
  return loadState(email).notifications
    .filter(
      (notification) =>
        notification.workspaceId === workspaceId,
    )
    .sort(compareMailNotifications)
}

export function unreadMailNotificationCount(
  notifications: MailInboxNotification[],
) {
  return notifications.filter((notification) => !notification.readAt)
    .length
}

export function markMailNotificationRead(
  email: string,
  notificationId: string,
) {
  const state = loadState(email)
  const readAt = new Date().toISOString()
  const notifications = state.notifications.map((notification) =>
    notification.id === notificationId && !notification.readAt
      ? { ...notification, readAt }
      : notification,
  )
  saveState(email, { ...state, notifications })
  return notifications
}

export function markAllMailNotificationsRead(
  email: string,
  workspaceId: string,
) {
  const state = loadState(email)
  const readAt = new Date().toISOString()
  const notifications = state.notifications.map((notification) =>
    notification.workspaceId === workspaceId && !notification.readAt
      ? { ...notification, readAt }
      : notification,
  )
  saveState(email, { ...state, notifications })
  return notifications
}

export async function checkMailNotifications(
  email: string,
  workspaceId: string,
) {
  const accounts = await listMailAccounts(workspaceId)
  let state = loadState(email)
  for (const account of accounts) {
    try {
      const page = await checkMailInbox({
        workspaceId,
        accountId: account.id,
        limit: 20,
      })
      state = applyMailInboxCheck(
        state,
        workspaceId,
        account,
        page.messages,
        new Date().toISOString(),
      )
    } catch {
      // One unavailable mailbox must not block other accounts.
    }
  }
  saveState(email, state)
  return state.notifications
    .filter(
      (notification) =>
        notification.workspaceId === workspaceId,
    )
    .sort(compareMailNotifications)
}

function loadState(email: string) {
  return normalizeMailNotificationState(
    readUserScopedValue<unknown>(STORAGE_KEY, email),
  )
}

function saveState(email: string, state: MailNotificationState) {
  writeUserScopedValue(STORAGE_KEY, email, state)
}
