<script setup lang="ts">
import { computed } from 'vue'

import gmailIcon from '@/assets/mail/gmail.png'
import icloudIcon from '@/assets/mail/icloud.png'
import neteaseIcon from '@/assets/mail/netease-mail.png'
import outlookIcon from '@/assets/mail/outlook.svg'
import qqMailIcon from '@/assets/mail/qq-mail.png'
import tencentExmailIcon from '@/assets/mail/tencent-exmail.png'
import type { MailAccount } from '@/types/mail'

type MailProvider =
  | 'tencent-exmail'
  | 'qq'
  | 'gmail'
  | 'outlook'
  | 'icloud'
  | 'netease-163'
  | 'netease-126'
  | 'custom'

const props = withDefaults(
  defineProps<{
    account: Pick<
      MailAccount,
      'displayName' | 'emailAddress' | 'imapHost'
    >
    size?: number
  }>(),
  {
    size: 24,
  },
)

const provider = computed<MailProvider>(() => {
  const email = props.account.emailAddress.toLocaleLowerCase('en-US')
  const domain = email.split('@')[1] ?? ''
  const host = props.account.imapHost.toLocaleLowerCase('en-US')
  const hostMatches = (values: string[]) =>
    values.some((value) => host === value || host.endsWith(`.${value}`))
  const domainMatches = (values: string[]) => values.includes(domain)

  if (hostMatches(['exmail.qq.com'])) return 'tencent-exmail'
  if (domainMatches(['qq.com']) || hostMatches(['imap.qq.com'])) return 'qq'
  if (
    domainMatches(['gmail.com', 'googlemail.com']) ||
    hostMatches(['imap.gmail.com'])
  ) {
    return 'gmail'
  }
  if (
    domainMatches([
      'outlook.com',
      'hotmail.com',
      'live.com',
      'msn.com',
    ]) ||
    hostMatches(['outlook.office365.com'])
  ) {
    return 'outlook'
  }
  if (
    domainMatches(['icloud.com', 'me.com', 'mac.com']) ||
    hostMatches(['imap.mail.me.com'])
  ) {
    return 'icloud'
  }
  if (domainMatches(['126.com'])) return 'netease-126'
  if (domainMatches(['163.com'])) return 'netease-163'
  return 'custom'
})

const providerIcon = computed(() => {
  const icons: Partial<Record<MailProvider, string>> = {
    'tencent-exmail': tencentExmailIcon,
    qq: qqMailIcon,
    gmail: gmailIcon,
    outlook: outlookIcon,
    icloud: icloudIcon,
    'netease-163': neteaseIcon,
    'netease-126': neteaseIcon,
  }
  return icons[provider.value] ?? ''
})

const label = computed(() => {
  const labels: Record<MailProvider, string> = {
    'tencent-exmail': '腾讯企业邮箱',
    qq: 'QQ 邮箱',
    gmail: 'Gmail',
    outlook: 'Outlook',
    icloud: 'iCloud 邮箱',
    'netease-163': '网易 163 邮箱',
    'netease-126': '网易 126 邮箱',
    custom: props.account.displayName,
  }
  return labels[provider.value]
})

const monogram = computed(() => {
  const source =
    props.account.displayName.trim() ||
    props.account.emailAddress.trim()
  return source.charAt(0).toLocaleUpperCase('zh-CN') || '@'
})

const customColor = computed(() => {
  const palette = [
    '#4f75d8',
    '#2b8a69',
    '#c26a2d',
    '#8862bd',
    '#c4556c',
    '#2b8391',
  ]
  const value = props.account.emailAddress || props.account.displayName
  const hash = Array.from(value).reduce(
    (sum, character) => sum + character.charCodeAt(0),
    0,
  )
  return palette[hash % palette.length]
})

</script>

<template>
  <span
    class="mail-account-icon"
    :class="`mail-account-icon--${provider}`"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      '--custom-mail-color': customColor,
    }"
    :title="label"
    :aria-label="label"
    role="img"
  >
    <img
      v-if="providerIcon"
      :src="providerIcon"
      alt=""
      draggable="false"
    />
    <strong v-else>{{ monogram }}</strong>
  </span>
</template>

<style scoped>
.mail-account-icon {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  overflow: hidden;
  background: var(--canvas-raised);
  border-radius: 6px;
  place-items: center;
}

.mail-account-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.mail-account-icon--custom {
  color: #ffffff;
  background: var(--custom-mail-color);
}

.mail-account-icon--custom strong {
  font-size: 11px;
  font-weight: 650;
  line-height: 1;
}

.mail-account-icon--netease-163::after,
.mail-account-icon--netease-126::after {
  position: absolute;
  right: 1px;
  bottom: 1px;
  width: 5px;
  height: 5px;
  content: '';
  border: 1px solid #ffffff;
  border-radius: 50%;
}

.mail-account-icon--netease-163::after {
  background: #d61f2c;
}

.mail-account-icon--netease-126::after {
  background: #2d6cdf;
}

.mail-account-icon--netease-163 img,
.mail-account-icon--netease-126 img {
  padding: 1px;
}
</style>
