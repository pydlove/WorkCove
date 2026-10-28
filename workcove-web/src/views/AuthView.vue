<script setup lang="ts">
import {
  ArrowRight,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  ShieldCheck,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { PRODUCT_NAME, PRODUCT_TAGLINE } from '@/brand'
import AppTitlebar from '@/components/business/AppTitlebar.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import { useAuthStore } from '@/stores/useAuthStore'

type AuthMode = 'login' | 'register'

const router = useRouter()
const auth = useAuthStore()

const authMode = ref<AuthMode>('login')
const email = ref('')
const emailCode = ref('')
const password = ref('')
const confirmPassword = ref('')
const remember = ref(true)
const passwordVisible = ref(false)
const confirmPasswordVisible = ref(false)
const submitting = ref(false)
const sendingCode = ref(false)
const resendSeconds = ref(0)
const errorMessage = ref('')
const noticeMessage = ref('')
const emailInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)

let countdownTimer: number | null = null
const isRegisterMode = computed(() => authMode.value === 'register')
const codeButtonLabel = computed(() => {
  if (resendSeconds.value > 0) return `${resendSeconds.value}s 后重发`
  return sendingCode.value ? '发送中…' : '获取验证码'
})

function normalizeEmail(value: string) {
  return value.trim().toLocaleLowerCase('en-US')
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function errorText(error: unknown) {
  if (error instanceof ApiError) return error.message
  return '无法连接账号服务，请确认后端已启动'
}

function stopCountdown() {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function startCountdown() {
  stopCountdown()
  resendSeconds.value = 60
  countdownTimer = window.setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0) stopCountdown()
  }, 1000)
}

async function sendCode() {
  const normalizedEmail = normalizeEmail(email.value)
  errorMessage.value = ''
  noticeMessage.value = ''

  if (!isValidEmail(normalizedEmail)) {
    errorMessage.value = '请输入有效的邮箱地址'
    await nextTick()
    emailInput.value?.focus()
    return
  }

  sendingCode.value = true
  try {
    await auth.sendRegistrationCode(normalizedEmail)
    startCountdown()
    noticeMessage.value = `验证码已发送至 ${normalizedEmail}，5 分钟内有效。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    sendingCode.value = false
  }
}

async function handleSubmit() {
  const normalizedEmail = normalizeEmail(email.value)
  errorMessage.value = ''
  noticeMessage.value = ''

  if (!normalizedEmail) {
    errorMessage.value = '请输入邮箱'
    await nextTick()
    emailInput.value?.focus()
    return
  }
  if (!isValidEmail(normalizedEmail)) {
    errorMessage.value = '请输入有效的邮箱地址'
    await nextTick()
    emailInput.value?.focus()
    return
  }
  if (!password.value) {
    errorMessage.value = '请输入密码'
    return
  }
  if (isRegisterMode.value && !/^\d{6}$/.test(emailCode.value.trim())) {
    errorMessage.value = '请输入 6 位邮箱验证码'
    return
  }
  if (isRegisterMode.value && password.value.length < 8) {
    errorMessage.value = '密码至少需要 8 个字符'
    return
  }
  if (isRegisterMode.value && password.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  submitting.value = true
  try {
    if (isRegisterMode.value) {
      await auth.register({
        email: normalizedEmail,
        emailCode: emailCode.value.trim(),
        password: password.value,
        confirmPassword: confirmPassword.value,
      })
      stopCountdown()
      resendSeconds.value = 0
      emailCode.value = ''
      password.value = ''
      confirmPassword.value = ''
      passwordVisible.value = false
      confirmPasswordVisible.value = false
      authMode.value = 'login'
      noticeMessage.value = '账号已创建，请使用邮箱和密码登录。'
      await nextTick()
      passwordInput.value?.focus()
      return
    }

    await auth.login(normalizedEmail, password.value, remember.value)
    await router.push({ name: 'dashboard' })
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    submitting.value = false
  }
}

async function previewWorkbench() {
  await auth.startPreview()
  await router.push({ name: 'dashboard' })
}

async function switchMode(mode: AuthMode) {
  stopCountdown()
  authMode.value = mode
  emailCode.value = ''
  password.value = ''
  confirmPassword.value = ''
  passwordVisible.value = false
  confirmPasswordVisible.value = false
  resendSeconds.value = 0
  errorMessage.value = ''
  noticeMessage.value = ''
  await nextTick()
  emailInput.value?.focus()
}

onMounted(() => {
  emailInput.value?.focus()
})

onBeforeUnmount(stopCountdown)
</script>

<template>
  <main class="login-stage">
    <section class="login-window" aria-labelledby="login-title">
      <AppTitlebar :title="PRODUCT_NAME" />

      <div class="login-window__body">
        <aside class="login-story">
          <div class="story-brand">
            <BrandMark :size="36" />
            <div>
              <p class="story-brand__name">{{ PRODUCT_NAME }}</p>
              <p class="story-brand__meta">{{ PRODUCT_TAGLINE }}</p>
            </div>
          </div>

          <div class="story-copy">
            <p class="story-kicker">8小时工作平台</p>
            <h1>把工作，<br />放回自己的节奏。</h1>
            <p class="story-intro">
              时间、常用网址与本地软件，在一个安静的工作台里触手可及。
            </p>
          </div>

          <div class="story-features">
            <div>
              <strong>时间节奏</strong>
              <span>把握每一天</span>
            </div>
            <div>
              <strong>快捷入口</strong>
              <span>常用网址一处打开</span>
            </div>
            <div>
              <strong>本地工具</strong>
              <span>工作应用随手可达</span>
            </div>
          </div>
        </aside>

        <section class="login-panel">
          <div class="login-panel__inner">
            <div class="login-heading">
              <div class="login-heading__top">
                <div
                  class="auth-mode-tabs"
                  role="tablist"
                  aria-label="账号操作"
                >
                  <button
                    type="button"
                    role="tab"
                    :aria-selected="authMode === 'login'"
                    :class="{ 'auth-mode-tabs__active': authMode === 'login' }"
                    @click="switchMode('login')"
                  >
                    登录
                  </button>
                  <button
                    type="button"
                    role="tab"
                    :aria-selected="authMode === 'register'"
                    :class="{
                      'auth-mode-tabs__active': authMode === 'register',
                    }"
                    @click="switchMode('register')"
                  >
                    注册
                  </button>
                </div>
              </div>
              <h2 id="login-title">
                {{ isRegisterMode ? '创建账号' : '欢迎回来' }}
              </h2>
              <p>
                {{
                  isRegisterMode
                    ? `使用邮箱验证码创建 ${PRODUCT_NAME}账号。`
                    : '登录后继续处理今天的工作。'
                }}
              </p>
            </div>

            <form
              class="login-form"
              :class="{ 'login-form--register': isRegisterMode }"
              novalidate
              @submit.prevent="handleSubmit"
            >
              <label class="form-field">
                <span class="form-field__label">邮箱</span>
                <span class="form-field__control">
                  <Mail :size="16" aria-hidden="true" />
                  <input
                    ref="emailInput"
                    v-model="email"
                    type="email"
                    name="email"
                    autocomplete="email"
                    inputmode="email"
                    placeholder="you@example.com"
                    spellcheck="false"
                    aria-label="邮箱"
                  />
                </span>
              </label>

              <label v-if="isRegisterMode" class="form-field">
                <span class="form-field__label">邮箱验证码</span>
                <span class="form-field__control form-field__control--code">
                  <ShieldCheck :size="16" aria-hidden="true" />
                  <input
                    v-model="emailCode"
                    type="text"
                    name="email-code"
                    inputmode="numeric"
                    maxlength="6"
                    autocomplete="one-time-code"
                    aria-label="邮箱验证码"
                    placeholder="6 位数字"
                  />
                  <button
                    class="code-send"
                    type="button"
                    :disabled="
                      sendingCode || resendSeconds > 0 || submitting
                    "
                    @click="sendCode"
                  >
                    {{ codeButtonLabel }}
                  </button>
                </span>
              </label>

              <label class="form-field">
                <span class="form-field__label">密码</span>
                <span class="form-field__control">
                  <LockKeyhole :size="16" aria-hidden="true" />
                  <input
                    ref="passwordInput"
                    v-model="password"
                    :type="passwordVisible ? 'text' : 'password'"
                    name="password"
                    :autocomplete="
                      isRegisterMode ? 'new-password' : 'current-password'
                    "
                    aria-label="密码"
                    :placeholder="
                      isRegisterMode ? '至少 8 个字符' : '输入密码'
                    "
                  />
                  <button
                    class="password-toggle"
                    type="button"
                    :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                    @click="passwordVisible = !passwordVisible"
                  >
                    <EyeOff v-if="passwordVisible" :size="16" />
                    <Eye v-else :size="16" />
                  </button>
                </span>
              </label>

              <label v-if="isRegisterMode" class="form-field">
                <span class="form-field__label">确认密码</span>
                <span class="form-field__control">
                  <LockKeyhole :size="16" aria-hidden="true" />
                  <input
                    v-model="confirmPassword"
                    :type="confirmPasswordVisible ? 'text' : 'password'"
                    name="confirm-password"
                    autocomplete="new-password"
                    aria-label="确认密码"
                    placeholder="再次输入密码"
                  />
                  <button
                    class="password-toggle"
                    type="button"
                    :aria-label="
                      confirmPasswordVisible
                        ? '隐藏确认密码'
                        : '显示确认密码'
                    "
                    @click="
                      confirmPasswordVisible = !confirmPasswordVisible
                    "
                  >
                    <EyeOff v-if="confirmPasswordVisible" :size="16" />
                    <Eye v-else :size="16" />
                  </button>
                </span>
              </label>

              <div v-if="!isRegisterMode" class="form-options">
                <label class="remember-control">
                  <input v-model="remember" type="checkbox" />
                  <span class="remember-control__box">
                    <span />
                  </span>
                  <span>保持登录</span>
                </label>
                <span class="secure-hint">安全会话</span>
              </div>

              <p v-if="isRegisterMode" class="local-account-note">
                验证码 5 分钟内有效，同一邮箱 60 秒后可重发。
              </p>

              <p v-if="noticeMessage" class="form-notice" role="status">
                {{ noticeMessage }}
              </p>

              <p v-if="errorMessage" class="form-error" role="alert">
                {{ errorMessage }}
              </p>

              <button
                class="login-submit"
                type="submit"
                :disabled="submitting"
              >
                <span v-if="submitting">
                  {{ isRegisterMode ? '正在创建…' : '正在进入…' }}
                </span>
                <span v-else>
                  {{ isRegisterMode ? '创建账号' : '进入工作台' }}
                </span>
                <ArrowRight :size="17" :stroke-width="2" />
              </button>
            </form>

            <div class="login-secondary">
              <span>无需账号预览界面</span>
              <button type="button" @click="previewWorkbench">查看工作台</button>
            </div>
          </div>

        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-stage {
  position: relative;
  display: grid;
  width: 100%;
  min-height: 100%;
  overflow: auto;
  padding: 42px;
  place-items: center;
  background:
    linear-gradient(128deg, #edf1f5 0%, #f7f8fa 50%, #e7ebf0 100%);
}

.login-window {
  position: relative;
  display: flex;
  width: min(100%, 1120px);
  min-height: 680px;
  overflow: hidden;
  flex-direction: column;
  background: var(--canvas-raised);
  border: 1px solid var(--line);
  border-radius: 15px;
  box-shadow:
    0 30px 80px rgb(31 35 40 / 16%),
    0 4px 18px rgb(31 35 40 / 9%),
    inset 0 0 0 1px rgb(255 255 255 / 65%);
  animation: login-window-in 420ms cubic-bezier(0.2, 0.75, 0.3, 1) both;
}

.login-window__body {
  display: grid;
  min-height: 634px;
  flex: 1;
  grid-template-columns: minmax(0, 1.06fr) minmax(380px, 0.94fr);
}

.login-story {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  justify-content: space-between;
  padding: 42px 44px 34px;
  background: linear-gradient(
      180deg,
      rgb(3 12 22 / 34%) 0%,
      rgb(3 12 22 / 10%) 38%,
      rgb(3 12 22 / 78%) 100%
    ),
    url("@/assets/auth/workcove-login-glass.webp") center / cover no-repeat,
    #07111c;
  border-right: 1px solid rgb(255 255 255 / 7%);
  isolation: isolate;
}

.story-brand,
.story-copy,
.story-features {
  position: relative;
  z-index: 1;
}

.story-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.story-brand > div {
  min-width: 0;
}

.story-brand p {
  margin: 0;
}

.story-brand__name {
  overflow: hidden;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.story-brand__meta {
  margin-top: 2px !important;
  color: rgb(255 255 255 / 52%);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.12em;
  font-family: var(--font-mono);
  text-transform: uppercase;
}

.story-copy {
  max-width: 390px;
  margin-top: auto;
  padding-top: 48px;
}

.story-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px;
  color: rgb(255 255 255 / 62%);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.13em;
  font-family: var(--font-mono);
}

.story-kicker::before {
  width: 28px;
  height: 1px;
  content: "";
  background: rgb(255 255 255 / 34%);
}

.story-copy h1 {
  margin: 0;
  color: #ffffff;
  font-family: var(--font-sans);
  font-size: clamp(36px, 4vw, 50px);
  font-weight: 630;
  letter-spacing: 0;
  line-height: 1.16;
  white-space: nowrap;
  text-shadow: 0 2px 24px rgb(0 0 0 / 18%);
}

.story-intro {
  max-width: 330px;
  margin: 23px 0 0;
  color: rgb(255 255 255 / 66%);
  font-size: 14px;
  line-height: 1.75;
}

.story-features {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding-top: 20px;
  margin-top: 34px;
  border-top: 1px solid rgb(255 255 255 / 14%);
}

.story-features > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.story-features strong {
  color: rgb(255 255 255 / 88%);
  font-size: 11px;
  font-weight: 620;
}

.story-features span {
  overflow: hidden;
  color: rgb(255 255 255 / 42%);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 58px 28px;
  background:
    linear-gradient(145deg, rgb(9 105 218 / 4%), transparent 40%),
    #ffffff;
}

.login-panel__inner {
  width: min(100%, 352px);
  margin: auto;
}

.login-heading__top {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 16px;
  margin-bottom: 12px;
}

.auth-mode-tabs {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
  padding: 2px;
  margin-top: -4px;
  background: #eaeef2;
  border: 1px solid rgb(31 35 40 / 7%);
  border-radius: 6px;
}

.auth-mode-tabs button {
  height: 24px;
  padding: 0 9px;
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: 550;
  background: transparent;
  border-radius: 4px;
}

.auth-mode-tabs button:hover {
  color: var(--text);
}

.auth-mode-tabs .auth-mode-tabs__active {
  color: var(--text);
  font-weight: 620;
  background: #ffffff;
  box-shadow:
    0 1px 2px rgb(31 35 40 / 11%),
    inset 0 0 0 1px rgb(31 35 40 / 4%);
}

.login-heading h2 {
  margin: 0;
  color: var(--text);
  font-family: var(--font-sans);
  font-size: 32px;
  font-weight: 630;
  letter-spacing: 0;
  line-height: 1.2;
}

.login-heading > p:last-child {
  margin: 10px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.login-form {
  display: flex;
  margin-top: 34px;
  gap: 18px;
  flex-direction: column;
}

.login-form--register {
  margin-top: 26px;
  gap: 14px;
}

.form-field {
  display: flex;
  gap: 8px;
  flex-direction: column;
}

.form-field__label {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-mono);
}

.form-field__control {
  display: flex;
  height: 43px;
  align-items: center;
  gap: 10px;
  padding: 0 13px;
  color: var(--text-subtle);
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow:
    0 1px 1px rgb(31 35 40 / 3%),
    inset 0 1px 0 rgb(255 255 255 / 70%);
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease,
    background 150ms ease;
}

.form-field__control:focus-within {
  background: #ffffff;
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.form-field__control input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: 13px;
  color: var(--text);
  background: transparent;
  border: 0;
}

.form-field__control input:focus-visible {
  outline: none;
}

.form-field__control--code {
  padding-right: 5px;
}

.code-send {
  height: 29px;
  flex: 0 0 auto;
  padding: 0 9px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  background: var(--accent-soft);
  border-radius: 5px;
}

.code-send:hover:not(:disabled) {
  color: #ffffff;
  background: var(--accent);
}

.code-send:disabled {
  color: var(--text-subtle);
  background: rgb(31 35 40 / 5%);
}

.password-toggle {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 6px;
  place-items: center;
}

.password-toggle:hover {
  color: var(--text);
  background: rgb(31 35 40 / 5%);
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -2px;
}

.remember-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 11px;
  user-select: none;
}

.remember-control input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.remember-control__box {
  display: grid;
  width: 15px;
  height: 15px;
  background: #ffffff;
  border: 1px solid rgb(31 35 40 / 18%);
  border-radius: 4px;
  place-items: center;
  transition:
    background 140ms ease,
    border-color 140ms ease;
}

.remember-control__box span {
  width: 5px;
  height: 8px;
  border-right: 1.5px solid white;
  border-bottom: 1.5px solid white;
  opacity: 0;
  transform: translateY(-1px) rotate(43deg);
}

.remember-control input:checked + .remember-control__box {
  background: var(--accent);
  border-color: var(--accent);
}

.remember-control input:checked + .remember-control__box span {
  opacity: 1;
}

.remember-control input:focus-visible + .remember-control__box {
  box-shadow: var(--focus-ring);
}

.secure-hint {
  color: var(--text-subtle);
  font-size: 11px;
}

.form-error {
  margin: -5px 0;
  color: var(--coral);
  font-size: 11px;
}

.form-notice {
  padding: 8px 10px;
  margin: -5px 0;
  color: #1a7f37;
  font-size: 11px;
  line-height: 1.5;
  background: rgb(26 127 55 / 7%);
  border-radius: 6px;
}

.local-account-note {
  margin: -6px 0 -2px;
  color: var(--text-subtle);
  font-size: 11px;
  line-height: 1.55;
}

.login-submit {
  display: flex;
  width: 100%;
  height: 44px;
  align-items: center;
  justify-content: space-between;
  margin-top: 3px;
  padding: 0 16px 0 18px;
  color: #fff;
  font-size: 12px;
  font-weight: 620;
  background: var(--accent);
  border-radius: 8px;
  box-shadow:
    0 1px 2px rgb(9 105 218 / 24%),
    inset 0 1px 0 rgb(255 255 255 / 10%);
  transition:
    background 150ms ease,
    transform 150ms ease,
    box-shadow 150ms ease;
}

.login-submit:hover:not(:disabled) {
  background: var(--accent-strong);
  box-shadow:
    0 4px 16px rgb(9 105 218 / 20%),
    inset 0 1px 0 rgb(255 255 255 / 10%);
  transform: translateY(-1px);
}

.login-submit:active:not(:disabled) {
  transform: translateY(0);
}

.login-submit svg {
  transition: transform 150ms ease;
}

.login-submit:hover svg {
  transform: translateX(2px);
}

.login-secondary {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 23px;
  color: var(--text-subtle);
  font-size: 11px;
}

.login-secondary button {
  padding: 2px;
  color: var(--accent);
  font-size: inherit;
  font-weight: 600;
  background: transparent;
}

.login-secondary button:hover {
  color: var(--accent-strong);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@keyframes login-window-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.992);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 820px) {
  .login-stage {
    padding: 24px;
  }

  .login-window {
    min-height: 600px;
  }

  .login-window__body {
    grid-template-columns: minmax(280px, 0.8fr) minmax(360px, 1.2fr);
  }

  .login-story {
    padding: 36px 32px;
  }

  .story-copy h1 {
    font-size: 33px;
  }

  .login-panel {
    padding: 42px 42px 24px;
  }
}

@media (max-width: 700px) {
  body {
    overflow: auto;
  }

  .login-stage {
    min-height: 100dvh;
    padding: 0;
    place-items: stretch;
  }

  .login-window {
    width: 100%;
    min-height: 100dvh;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .login-window__body {
    min-height: calc(100dvh - 46px);
    grid-template-columns: 1fr;
  }

  .login-story {
    min-height: 280px;
    padding: 30px 28px;
    border-right: 0;
    border-bottom: 1px solid var(--line-soft);
  }

  .story-copy {
    margin-top: 42px;
    padding-top: 0;
  }

  .story-kicker,
  .story-intro,
  .story-features {
    display: none;
  }

  .story-copy h1 {
    font-size: 34px;
    line-height: 1.16;
  }

  .login-panel {
    padding: 38px 28px 24px;
  }

  .login-panel__inner {
    margin: 0 auto auto;
  }
}

html[data-theme="dark"] {
  .login-stage {
    background:
      linear-gradient(128deg, #080c11 0%, #111820 50%, #090e14 100%);
  }

  .login-window {
    border-color: rgb(240 246 252 / 13%);
    box-shadow:
      0 34px 90px rgb(0 0 0 / 55%),
      0 4px 18px rgb(0 0 0 / 35%),
      inset 0 0 0 1px rgb(255 255 255 / 5%);
  }

  .story-brand__meta {
    color: rgb(255 255 255 / 52%);
  }

  .story-kicker {
    color: #b9ddff;
  }

  .story-copy h1,
  .login-heading h2 {
    color: #f0f6fc;
  }

  .story-intro,
  .form-field__label {
    color: rgb(255 255 255 / 60%);
  }

  .login-panel {
    background:
      linear-gradient(145deg, rgb(88 166 255 / 5%), transparent 40%),
      #161b22;
  }

  .form-field__control {
    color: #6e7681;
    background: #0d1117;
    border-color: rgb(240 246 252 / 13%);
    box-shadow:
      0 1px 1px rgb(0 0 0 / 24%),
      inset 0 1px 0 rgb(255 255 255 / 3%);
  }

  .form-field__control:focus-within {
    background: #0d1117;
    border-color: rgb(88 166 255 / 72%);
  }

  .auth-mode-tabs {
    background: #0d1117;
    border-color: rgb(240 246 252 / 8%);
  }

  .auth-mode-tabs button {
    color: #8b949e;
  }

  .auth-mode-tabs .auth-mode-tabs__active {
    color: #f0f6fc;
    background: #21262d;
    box-shadow:
      0 1px 3px rgb(0 0 0 / 30%),
      inset 0 0 0 1px rgb(240 246 252 / 7%);
  }

  .code-send {
    color: #79c0ff;
    background: rgb(56 139 253 / 12%);
  }

  .code-send:hover:not(:disabled) {
    color: #ffffff;
    background: #1f6feb;
  }

  .code-send:disabled {
    color: #6e7681;
    background: rgb(240 246 252 / 6%);
  }

  .form-notice {
    color: #7ee787;
    background: rgb(63 185 80 / 10%);
  }

  .password-toggle {
    color: #6e7681;
  }

  .password-toggle:hover {
    color: #c9d1d9;
    background: rgb(240 246 252 / 6%);
  }

  .remember-control {
    color: #8b949e;
  }

  .remember-control__box {
    background: #0d1117;
    border-color: rgb(240 246 252 / 20%);
  }

  .secure-hint,
  .login-secondary,
  .local-account-note {
    color: #6e7681;
  }

  .form-error {
    color: #ff7b72;
  }

  .login-submit {
    background: #1f6feb;
    box-shadow:
      0 1px 2px rgb(0 0 0 / 30%),
      inset 0 1px 0 rgb(255 255 255 / 10%);
  }

  .login-submit:hover:not(:disabled) {
    background: #388bfd;
    box-shadow:
      0 4px 16px rgb(31 111 235 / 24%),
      inset 0 1px 0 rgb(255 255 255 / 10%);
  }

  .login-secondary button {
    color: #79c0ff;
  }
}
</style>
