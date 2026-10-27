import { defineStore } from 'pinia'

import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  sendRegistrationCode,
} from '@/api/auth'
import { ApiError } from '@/api/http'
import {
  loadLocalProfile,
  saveLocalProfile,
} from '@/services/localProfile'
import type { AuthUser, RegisterInput } from '@/types/auth'
import type { ProfileUpdate, UserProfile } from '@/types/profile'

const PREVIEW_SESSION_KEY = 'workcove.preview-session'

interface PreviewSession {
  email: string
  displayName: string
}

function readPreviewSession(): PreviewSession | null {
  const raw = window.sessionStorage.getItem(PREVIEW_SESSION_KEY)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as PreviewSession
    return parsed.email && parsed.displayName ? parsed : null
  } catch {
    return null
  }
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

      const preview = readPreviewSession()
      if (preview) {
        this.applyLocalUser(preview.email, preview.displayName)
        this.initialized = true
        return true
      }

      try {
        this.applyUser(await getCurrentUser())
      } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) {
          this.clearAuth()
        }
      } finally {
        this.initialized = true
      }
      return this.authenticated
    },
    async login(email: string, password: string, rememberMe: boolean) {
      const user = await loginRequest({ email, password, rememberMe })
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
        await logoutRequest()
      } finally {
        window.sessionStorage.removeItem(PREVIEW_SESSION_KEY)
        this.clearAuth()
      }
    },
    startPreview() {
      const preview: PreviewSession = {
        email: 'preview@workcove.local',
        displayName: 'Yong',
      }
      window.sessionStorage.setItem(PREVIEW_SESSION_KEY, JSON.stringify(preview))
      this.applyLocalUser(preview.email, preview.displayName)
      this.initialized = true
    },
    applyUser(user: AuthUser) {
      this.authenticated = true
      this.user = user
      this.email = user.email
      const profile = loadLocalProfile(user.email, user.displayName)
      this.profile = profile
      this.userName = profile.displayName
    },
    applyLocalUser(email: string, displayName: string) {
      this.authenticated = true
      this.user = null
      this.email = email
      const profile = loadLocalProfile(email, displayName)
      this.profile = profile
      this.userName = profile.displayName
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
    },
  },
})
