import { defineStore } from 'pinia'

export type ThemePreference = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'workcove.theme'
const supportedPreferences: ThemePreference[] = ['light', 'dark', 'system']

const systemTheme = window.matchMedia('(prefers-color-scheme: dark)')
let systemThemeListener: ((event: MediaQueryListEvent) => void) | null = null

function getStoredPreference(): ThemePreference {
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY)
  return supportedPreferences.includes(stored as ThemePreference)
    ? (stored as ThemePreference)
    : 'light'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    preference: getStoredPreference(),
    systemPrefersDark: systemTheme.matches,
  }),
  getters: {
    resolvedTheme: (state): ResolvedTheme => {
      if (state.preference === 'system') {
        return state.systemPrefersDark ? 'dark' : 'light'
      }

      return state.preference
    },
  },
  actions: {
    initialize() {
      if (!systemThemeListener) {
        systemThemeListener = (event) => {
          this.systemPrefersDark = event.matches
          if (this.preference === 'system') this.applyTheme()
        }
        systemTheme.addEventListener('change', systemThemeListener)
      }

      this.applyTheme()
    },
    setPreference(preference: ThemePreference) {
      this.preference = preference
      window.localStorage.setItem(THEME_STORAGE_KEY, preference)
      this.applyTheme()
    },
    applyTheme() {
      document.documentElement.dataset.theme = this.resolvedTheme
      document.documentElement.style.colorScheme = this.resolvedTheme
    },
  },
})
