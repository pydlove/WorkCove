<script setup lang="ts">
import {
  CalendarDays,
  Camera,
  ImageUp,
  Mail,
  MapPin,
  Save,
  Trash2,
  UserRound,
} from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'

import { useAuthStore } from '@/stores/useAuthStore'
import { fileToSquareWebp } from '@/utils/image'

const emit = defineEmits<{
  saved: []
}>()

const session = useAuthStore()
const avatarInput = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const avatarError = ref('')
const saving = ref(false)
const baseline = ref('')
const form = reactive({
  username: '',
  displayName: '',
  birthday: '',
  location: '',
  bio: '',
  avatarDataUrl: null as string | null,
})

const profile = computed(() => session.profile)
const displayInitial = computed(
  () => (form.displayName.trim() || session.userName || 'W').charAt(0).toUpperCase(),
)
const isDirty = computed(() => snapshot() !== baseline.value)
const joinedAt = computed(() => {
  if (!profile.value?.createdAt) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(new Date(profile.value.createdAt))
})
const maxBirthday = new Date().toISOString().slice(0, 10)

function snapshot() {
  return JSON.stringify({
    username: form.username,
    displayName: form.displayName,
    birthday: form.birthday,
    location: form.location,
    bio: form.bio,
    avatarDataUrl: form.avatarDataUrl,
  })
}

function syncFromProfile() {
  if (!profile.value) return
  form.username = profile.value.username
  form.displayName = profile.value.displayName
  form.birthday = profile.value.birthday
  form.location = profile.value.location
  form.bio = profile.value.bio
  form.avatarDataUrl = profile.value.avatarDataUrl
  baseline.value = snapshot()
  errorMessage.value = ''
  avatarError.value = ''
}

watch(profile, syncFromProfile, { immediate: true })

function chooseAvatar() {
  avatarError.value = ''
  avatarInput.value?.click()
}

async function handleAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  errorMessage.value = ''
  try {
    form.avatarDataUrl = await fileToSquareWebp(file, {
      maxBytes: 8 * 1024 * 1024,
      dimension: 512,
      sizeLabel: '8MB',
    })
  } catch (error) {
    avatarError.value =
      error instanceof Error ? error.message : '头像处理失败，请重新选择'
  }
}

function removeAvatar() {
  form.avatarDataUrl = null
  avatarError.value = ''
}

function resetForm() {
  syncFromProfile()
}

function validateForm() {
  const displayName = form.displayName.trim()
  const username = form.username.trim()

  if (!displayName) return '请输入昵称'
  if ([...displayName].length > 32) return '昵称不能超过 32 个字符'
  if (!/^[a-zA-Z0-9_-]{2,24}$/.test(username)) {
    return '用户名需要 2 到 24 个字符，只能包含英文、数字、下划线和连字符'
  }
  if (form.birthday && form.birthday > maxBirthday) return '生日不能晚于今天'
  if ([...form.location].length > 32) return '所在城市不能超过 32 个字符'
  if ([...form.bio].length > 160) return '个人简介不能超过 160 个字符'

  return ''
}

async function saveProfile() {
  const validationError = validateForm()
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  saving.value = true
  errorMessage.value = ''
  try {
    session.updateProfile({
      username: form.username,
      displayName: form.displayName,
      birthday: form.birthday,
      location: form.location,
      bio: form.bio,
      avatarDataUrl: form.avatarDataUrl,
    })
    baseline.value = snapshot()
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="profile-editor" aria-label="个人资料">
    <aside class="profile-summary">
      <div class="profile-photo-area">
        <button
          class="profile-photo"
          type="button"
          aria-label="上传头像"
          @click="chooseAvatar"
        >
          <img
            v-if="form.avatarDataUrl"
            :src="form.avatarDataUrl"
            alt="当前头像"
          />
          <span v-else>{{ displayInitial }}</span>
          <span class="profile-photo__overlay">
            <Camera :size="18" />
          </span>
        </button>
        <input
          ref="avatarInput"
          class="profile-photo__input"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="handleAvatar"
        />
        <button
          v-if="form.avatarDataUrl"
          class="remove-photo"
          type="button"
          @click="removeAvatar"
        >
          <Trash2 :size="13" />
          移除
        </button>
        <p v-if="avatarError" class="profile-photo-error" role="alert">
          {{ avatarError }}
        </p>
      </div>

      <div class="profile-summary__name">
        <h2>{{ form.displayName || '未设置昵称' }}</h2>
        <p>
          {{ form.username ? `@${form.username}` : '尚未设置用户名' }}
        </p>
      </div>

      <div class="profile-summary__meta">
        <div>
          <Mail :size="15" />
          <span>
            <small>账户邮箱</small>
            {{ profile?.email }}
          </span>
        </div>
        <div>
          <CalendarDays :size="15" />
          <span>
            <small>加入时间</small>
            {{ joinedAt }}
          </span>
        </div>
        <div v-if="form.location">
          <MapPin :size="15" />
          <span>
            <small>所在城市</small>
            {{ form.location }}
          </span>
        </div>
      </div>

      <p class="profile-summary__note">
        <ImageUp :size="14" />
        JPG、PNG 或 WebP，最大 8MB
      </p>
    </aside>

    <form class="profile-form" novalidate @submit.prevent="saveProfile">
      <header class="profile-form__header">
        <div>
          <p class="profile-form__eyebrow">PROFILE</p>
          <h2>基本信息</h2>
          <p>这些信息用于工作台中的身份展示。</p>
        </div>
        <span class="profile-form__local">本地资料</span>
      </header>

      <div class="profile-fields">
        <label class="profile-field">
          <span>用户名</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            maxlength="24"
            spellcheck="false"
          />
          <small>英文、数字、下划线或连字符</small>
        </label>

        <label class="profile-field">
          <span>昵称</span>
          <input
            v-model="form.displayName"
            type="text"
            autocomplete="nickname"
            maxlength="32"
          />
          <small>显示在问候语和头像菜单中</small>
        </label>

        <label class="profile-field">
          <span>邮箱</span>
          <input :value="profile?.email" type="email" readonly />
          <small>当前账号邮箱不可在此修改</small>
        </label>

        <label class="profile-field">
          <span>生日</span>
          <span class="profile-field__control">
            <CalendarDays :size="15" />
            <input
              v-model="form.birthday"
              type="date"
              :max="maxBirthday"
            />
          </span>
          <small>用于生日提醒，可暂不填写</small>
        </label>

        <label class="profile-field">
          <span>所在城市</span>
          <span class="profile-field__control">
            <MapPin :size="15" />
            <input
              v-model="form.location"
              type="text"
              maxlength="32"
              placeholder="例如：杭州"
            />
          </span>
          <small>选填</small>
        </label>

        <label class="profile-field profile-field--bio">
          <span>个人简介</span>
          <span class="profile-field__control profile-field__control--textarea">
            <UserRound :size="15" />
            <textarea
              v-model="form.bio"
              rows="4"
              maxlength="160"
              placeholder="简单介绍一下自己，或写一句工作原则。"
            />
          </span>
          <small>{{ form.bio.length }} / 160</small>
        </label>
      </div>

      <p v-if="errorMessage" class="profile-error" role="alert">
        {{ errorMessage }}
      </p>

      <footer class="profile-actions">
        <span>资料仅保存在此 Mac</span>
        <div>
          <button
            class="profile-actions__secondary"
            type="button"
            :disabled="!isDirty || saving"
            @click="resetForm"
          >
            撤销修改
          </button>
          <button
            class="profile-actions__primary"
            type="submit"
            :disabled="!isDirty || saving"
          >
            <Save :size="15" />
            {{ saving ? '保存中…' : '保存资料' }}
          </button>
        </div>
      </footer>
    </form>
  </section>
</template>

<style scoped>
.profile-editor {
  display: grid;
  overflow: hidden;
  grid-template-columns: 260px minmax(0, 1fr);
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 8px;
  animation: profile-enter 220ms ease both;
}

.profile-summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 28px 24px 22px;
  background: #f6f8fa;
  border-right: 1px solid var(--line-soft);
}

.profile-photo-area {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 10px;
}

.profile-photo {
  position: relative;
  display: grid;
  width: 104px;
  height: 104px;
  overflow: hidden;
  padding: 0;
  color: #ffffff;
  font-size: 34px;
  font-weight: 650;
  background: #0969da;
  border: 1px solid rgb(31 35 40 / 12%);
  border-radius: 50%;
  box-shadow:
    0 6px 20px rgb(31 35 40 / 12%),
    0 0 0 5px #ffffff;
  place-items: center;
}

.profile-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-photo__overlay {
  position: absolute;
  inset: auto 0 0;
  display: grid;
  height: 36px;
  color: #ffffff;
  background: rgb(13 17 23 / 62%);
  opacity: 0;
  place-items: center;
  transition: opacity 140ms ease;
}

.profile-photo:hover .profile-photo__overlay,
.profile-photo:focus-visible .profile-photo__overlay {
  opacity: 1;
}

.profile-photo__input {
  display: none;
}

.remove-photo {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  color: var(--text-subtle);
  font-size: 9px;
  background: transparent;
  border-radius: 5px;
}

.remove-photo:hover {
  color: var(--coral);
  background: rgb(207 34 46 / 6%);
}

.profile-photo-error {
  max-width: 190px;
  margin: 0;
  color: var(--coral);
  font-size: 8px;
  line-height: 1.45;
  text-align: center;
}

.profile-summary__name {
  margin-top: 20px;
  text-align: center;
}

.profile-summary__name h2 {
  overflow: hidden;
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-summary__name p {
  overflow: hidden;
  margin: 5px 0 0;
  color: var(--text-subtle);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-summary__meta {
  display: flex;
  margin-top: 24px;
  padding-top: 8px;
  flex-direction: column;
  border-top: 1px solid var(--line-soft);
}

.profile-summary__meta > div {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 0;
  color: var(--text-subtle);
}

.profile-summary__meta svg {
  margin-top: 2px;
  flex: 0 0 auto;
}

.profile-summary__meta span {
  display: flex;
  min-width: 0;
  color: var(--text-muted);
  font-size: 10px;
  line-height: 1.45;
  flex-direction: column;
  overflow-wrap: anywhere;
}

.profile-summary__meta small {
  margin-bottom: 2px;
  color: var(--text-subtle);
  font-size: 8px;
}

.profile-summary__note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: auto 0 0;
  padding-top: 20px;
  color: var(--text-subtle);
  font-size: 8px;
}

.profile-form {
  display: flex;
  min-width: 0;
  padding: 28px 30px 22px;
  flex-direction: column;
}

.profile-form__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line-soft);
}

.profile-form__eyebrow {
  margin: 0 0 6px;
  color: var(--success);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.profile-form__header h2 {
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 620;
}

.profile-form__header p:last-child {
  margin: 7px 0 0;
  color: var(--text-muted);
  font-size: 10px;
}

.profile-form__local {
  flex: 0 0 auto;
  padding: 4px 8px;
  color: var(--accent);
  font-family: "SFMono-Regular", "SF Mono", "JetBrains Mono", monospace;
  font-size: 8px;
  background: var(--accent-soft);
  border-radius: 5px;
}

.profile-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 16px;
  padding: 22px 0 18px;
}

.profile-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
}

.profile-field > span:first-child {
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 600;
}

.profile-field input,
.profile-field textarea {
  width: 100%;
  color: var(--text);
  font-size: 11px;
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 7px;
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease;
}

.profile-field > input {
  height: 39px;
  padding: 0 11px;
}

.profile-field input[readonly] {
  color: var(--text-subtle);
  background: #f6f8fa;
  cursor: default;
}

.profile-field input:focus,
.profile-field textarea:focus,
.profile-field__control:focus-within {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.profile-field > input:focus-visible,
.profile-field__control input:focus-visible,
.profile-field__control textarea:focus-visible {
  outline: none;
}

.profile-field__control {
  display: flex;
  height: 39px;
  align-items: center;
  gap: 9px;
  padding: 0 10px;
  color: var(--text-subtle);
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 7px;
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease;
}

.profile-field__control input {
  height: 100%;
  padding: 0;
  background: transparent;
  border: 0;
}

.profile-field__control input:focus {
  box-shadow: none;
}

.profile-field__control--textarea {
  height: auto;
  align-items: flex-start;
  padding: 10px;
}

.profile-field__control--textarea svg {
  margin-top: 2px;
}

.profile-field__control textarea {
  min-height: 74px;
  resize: vertical;
  padding: 0;
  line-height: 1.6;
  background: transparent;
  border: 0;
}

.profile-field__control textarea:focus {
  box-shadow: none;
}

.profile-field small {
  min-height: 12px;
  color: var(--text-subtle);
  font-size: 8px;
  line-height: 1.4;
}

.profile-field--bio {
  grid-column: 1 / -1;
}

.profile-error {
  padding: 9px 11px;
  margin: 0 0 14px;
  color: var(--coral);
  font-size: 10px;
  background: rgb(207 34 46 / 6%);
  border-radius: 6px;
}

.profile-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding-top: 16px;
  margin-top: auto;
  border-top: 1px solid var(--line-soft);
}

.profile-actions > span {
  color: var(--text-subtle);
  font-size: 8px;
}

.profile-actions > div {
  display: flex;
  gap: 8px;
}

.profile-actions button {
  height: 32px;
  padding: 0 11px;
  font-size: 9px;
  font-weight: 600;
  border-radius: 6px;
}

.profile-actions__secondary {
  color: var(--text-muted);
  background: transparent;
  border: 1px solid var(--line);
}

.profile-actions__secondary:hover:not(:disabled) {
  color: var(--text);
  background: rgb(31 35 40 / 4%);
}

.profile-actions__primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #ffffff;
  background: var(--accent);
}

.profile-actions__primary:hover:not(:disabled) {
  background: var(--accent-strong);
}

.profile-actions button:disabled {
  color: var(--text-subtle);
  background: #eaeef2;
  border-color: transparent;
}

html[data-theme="dark"] {
  .profile-editor {
    background: #161b22;
  }

  .profile-summary {
    background: #0d1117;
  }

  .profile-photo {
    border-color: rgb(240 246 252 / 12%);
    box-shadow:
      0 6px 20px rgb(0 0 0 / 30%),
      0 0 0 5px #0d1117;
  }

  .profile-field input,
  .profile-field textarea,
  .profile-field__control {
    background: #0d1117;
  }

  .profile-field input[readonly] {
    background: #161b22;
  }

  .profile-actions__secondary:hover:not(:disabled) {
    background: rgb(240 246 252 / 5%);
  }

  .profile-actions button:disabled {
    background: #21262d;
  }
}

@media (max-width: 760px) {
  .profile-editor {
    grid-template-columns: 1fr;
  }

  .profile-summary {
    border-right: 0;
    border-bottom: 1px solid var(--line-soft);
  }

  .profile-summary__meta {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-summary__note {
    margin-top: 12px;
  }

  .profile-form {
    padding: 24px 20px 20px;
  }
}

@media (max-width: 560px) {
  .profile-fields {
    grid-template-columns: 1fr;
  }

  .profile-field--bio {
    grid-column: auto;
  }

  .profile-actions {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .profile-actions > div {
    justify-content: flex-end;
  }
}

@keyframes profile-enter {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
