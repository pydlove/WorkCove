import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyMailInboxCheck,
  type MailNotificationState,
} from '../src/utils/mailNotifications.ts'
import type { MailMessageSummary } from '../src/types/mail.ts'

const account = {
  id: 7,
  displayName: '工作邮箱',
  emailAddress: 'me@example.com',
}

function message(
  uid: string,
  subject: string,
  unread = true,
): MailMessageSummary {
  return {
    folderPath: 'INBOX',
    uid,
    messageId: `<${uid}@example.com>`,
    fromName: 'Alice',
    fromAddress: 'alice@example.com',
    subject,
    receivedAt: `2026-10-29T02:0${uid}:00Z`,
    preview: '',
    unread,
    starred: false,
    attachmentCount: 0,
  }
}

test('uses the first inbox check as a baseline', () => {
  const state: MailNotificationState = {
    snapshots: {},
    notifications: [],
  }

  const next = applyMailInboxCheck(
    state,
    'default',
    account,
    [message('1', '已有邮件')],
    '2026-10-29T02:00:00Z',
  )

  assert.equal(next.notifications.length, 0)
  assert.deepEqual(
    next.snapshots['default:7']?.signatures.length,
    1,
  )
})

test('creates one notification for new unread inbox mail', () => {
  const baseline = applyMailInboxCheck(
    { snapshots: {}, notifications: [] },
    'default',
    account,
    [message('1', '已有邮件')],
    '2026-10-29T02:00:00Z',
  )

  const next = applyMailInboxCheck(
    baseline,
    'default',
    account,
    [message('2', '新邮件'), message('1', '已有邮件')],
    '2026-10-29T02:01:00Z',
  )

  assert.equal(next.notifications.length, 1)
  assert.equal(next.notifications[0]?.title, 'Alice 发来新邮件')
  assert.equal(next.notifications[0]?.body, '新邮件')
  assert.equal(next.notifications[0]?.linkView, 'mail')
})

test('does not notify for mail already marked as read', () => {
  const baseline = applyMailInboxCheck(
    { snapshots: {}, notifications: [] },
    'default',
    account,
    [message('1', '已有邮件')],
    '2026-10-29T02:00:00Z',
  )

  const next = applyMailInboxCheck(
    baseline,
    'default',
    account,
    [message('2', '已读新邮件', false), message('1', '已有邮件')],
    '2026-10-29T02:01:00Z',
  )

  assert.equal(next.notifications.length, 0)
})
