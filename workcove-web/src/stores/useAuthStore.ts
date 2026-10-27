import { defineStore } from 'pinia'

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

    return {
      authenticated: Boolean(stored),
      userName: stored?.userName ?? 'Yong',
      email: stored?.email ?? '',
    }
  },
  actions: {
    signIn(userName: string, remember: boolean, email = '') {
      const normalizedName = userName.trim() || 'Yong'
      const session = JSON.stringify({
        userName: normalizedName,
        email: email.trim(),
      })

      window.localStorage.removeItem(LOCAL_SESSION_KEY)
      window.sessionStorage.removeItem(TEMP_SESSION_KEY)

      if (remember) {
        window.localStorage.setItem(LOCAL_SESSION_KEY, session)
      } else {
        window.sessionStorage.setItem(TEMP_SESSION_KEY, session)
      }

      this.authenticated = true
      this.userName = normalizedName
      this.email = email.trim()
    },
    signOut() {
      window.localStorage.removeItem(LOCAL_SESSION_KEY)
      window.sessionStorage.removeItem(TEMP_SESSION_KEY)
      this.authenticated = false
      this.email = ''
    },
  },
})
