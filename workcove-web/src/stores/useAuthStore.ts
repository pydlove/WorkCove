import { defineStore } from 'pinia'

import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  sendRegistrationCode,
} from '@/api/auth'
import {
  loadLocalProfile,
  saveLocalProfile,
} from '@/services/localProfile'
import {
  clearUserStorageCache,
  flushUserStorage,
  initializeUserStorage,
} from '@/services/userScopedStorage'
import { useThemeStore } from '@/stores/theme'
import type { AuthUser, RegisterInput } from '@/types/auth'
import type { ProfileUpdate, UserProfile } from '@/types/profile'

interface PreviewSession {
  email: string
  displayName: string
}

let previewSession: PreviewSession | null = null

function reportStartupStatus(message: string) {
  window.dispatchEvent(
    new CustomEvent('workcove:startup-status', {
      detail: message,
    }),
  )
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    authenticated: false,
    initialized: false,
    user: null as AuthUser | null,
    userName: 'Yong',
    email: '',
    profile: null as UserProfile | null,
  }),
  actions: {
    async initialize() {
      if (this.initialized) return this.authenticated

      if (previewSession) {
        reportStartupStatus('正在进入预览工作台')
        await initializeUserStorage(previewSession.email, {
          persist: false,
        })
        this.applyLocalUser(
          previewSession.email,
          previewSession.displayName,
        )
        this.initialized = true
        return true
      }

      try {
        reportStartupStatus('正在恢复登录状态')
        const user = await getCurrentUser()
        reportStartupStatus('正在载入你的工作空间')
        await initializeUserStorage(user.email)
        this.applyUser(user)
      } catch {
        reportStartupStatus('正在进入登录页')
        this.clearAuth()
      } finally {
        this.initialized = true
      }
      return this.authenticated
    },
    async login(email: string, password: string, rememberMe: boolean) {
      reportStartupStatus('正在登录')
      const user = await loginRequest({ email, password, rememberMe })
      reportStartupStatus('正在载入你的工作空间')
      await initializeUserStorage(user.email)
      this.applyUser(user)
      this.initialized = true
    },
    async register(input: RegisterInput) {
      return registerRequest(input)
    },
    async sendRegistrationCode(email: string) {
      await sendRegistrationCode(email)
    },
    async logout() {
      try {
        await flushUserStorage()
      } catch {
        // The failed write has already been reported through storage error.
      }
      try {
        await logoutRequest()
      } finally {
        this.clearAuth()
      }
    },
    async startPreview() {
      previewSession = {
        email: 'preview@workcove.local',
        displayName: 'Yong',
      }
      await initializeUserStorage(previewSession.email, { persist: false })
      this.applyLocalUser(
        previewSession.email,
        previewSession.displayName,
      )
      this.initialized = true
    },
    applyUser(user: AuthUser) {
      this.authenticated = true
      this.user = user
      this.email = user.email
      const profile = loadLocalProfile(user.email, user.displayName)
      this.profile = profile
      this.userName = profile.displayName
      useThemeStore().setUserScope(user.email)
    },
    applyLocalUser(email: string, displayName: string) {
      this.authenticated = true
      this.user = null
      this.email = email
      const profile = loadLocalProfile(email, displayName)
      this.profile = profile
      this.userName = profile.displayName
      useThemeStore().setUserScope(email)
    },
    updateProfile(update: ProfileUpdate) {
      if (!this.profile) return

      const nextProfile: UserProfile = {
        ...this.profile,
        ...update,
        displayName: update.displayName.trim(),
        username: update.username.trim(),
        location: update.location.trim(),
        bio: update.bio.trim(),
        updatedAt: new Date().toISOString(),
      }
      saveLocalProfile(nextProfile)
      this.profile = nextProfile
      this.userName = nextProfile.displayName
    },
    clearAuth() {
      this.authenticated = false
      this.user = null
      this.userName = 'Yong'
      this.email = ''
      this.profile = null
      previewSession = null
      clearUserStorageCache()
      useThemeStore().setUserScope(null)
    },
  },
})
