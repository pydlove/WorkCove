import { defineStore } from 'pinia'

import {
  loadLocalProfile,
  saveLocalProfile,
} from '@/services/localProfile'
import type { ProfileUpdate, UserProfile } from '@/types/profile'

const LOCAL_SESSION_KEY = 'workcove.session'
const TEMP_SESSION_KEY = 'workcove.temp-session'

interface StoredSession {
  userName: string
  email?: string
}

function readSession(): StoredSession | null {
  const raw =
    window.localStorage.getItem(LOCAL_SESSION_KEY) ??
    window.sessionStorage.getItem(TEMP_SESSION_KEY)

  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as StoredSession
    return parsed.userName ? parsed : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const stored = readSession()
    const profile = stored?.email
      ? loadLocalProfile(stored.email, stored.userName)
      : null

    return {
      authenticated: Boolean(stored),
      userName: profile?.displayName ?? stored?.userName ?? 'Yong',
      email: stored?.email ?? '',
      profile: profile as UserProfile | null,
    }
  },
  actions: {
    signIn(userName: string, remember: boolean, email = '') {
      const normalizedName = userName.trim() || 'Yong'
      const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
      const profile = normalizedEmail
        ? loadLocalProfile(normalizedEmail, normalizedName)
        : null
      const canonicalName = profile?.displayName ?? normalizedName
      const session = JSON.stringify({
        userName: canonicalName,
        email: normalizedEmail,
      })

      window.localStorage.removeItem(LOCAL_SESSION_KEY)
      window.sessionStorage.removeItem(TEMP_SESSION_KEY)

      if (remember) {
        window.localStorage.setItem(LOCAL_SESSION_KEY, session)
      } else {
        window.sessionStorage.setItem(TEMP_SESSION_KEY, session)
      }

      this.authenticated = true
      this.userName = canonicalName
      this.email = normalizedEmail
      this.profile = profile
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
    signOut() {
      window.localStorage.removeItem(LOCAL_SESSION_KEY)
      window.sessionStorage.removeItem(TEMP_SESSION_KEY)
      this.authenticated = false
      this.email = ''
      this.profile = null
    },
  },
})
