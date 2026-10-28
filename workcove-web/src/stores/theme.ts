import { defineStore } from 'pinia'

import {
  readUserScopedValue,
  requireUserScope,
  writeUserScopedValue,
} from '@/services/userScopedStorage'

export type ThemePreference = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'
export type FontPreference = 'system' | 'codex' | 'avenir'

const USER_THEME_STORAGE_KEY = 'workcove.user-themes.v1'
const USER_FONT_STORAGE_KEY = 'workcove.user-fonts.v1'
const supportedPreferences: ThemePreference[] = ['light', 'dark', 'system']
const supportedFontPreferences: FontPreference[] = [
  'system',
  'codex',
  'avenir',
]

const systemTheme = window.matchMedia('(prefers-color-scheme: dark)')
let systemThemeListener: ((event: MediaQueryListEvent) => void) | null = null

export const useThemeStore = defineStore('theme', {
  state: () => ({
    preference: 'light' as ThemePreference,
    fontPreference: 'system' as FontPreference,
    systemPrefersDark: systemTheme.matches,
    ownerScope: '',
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
      this.applyFont()
    },
    setPreference(preference: ThemePreference) {
      this.preference = preference
      if (this.ownerScope) {
        writeUserScopedValue(
          USER_THEME_STORAGE_KEY,
          this.ownerScope,
          preference,
        )
      }
      this.applyTheme()
    },
    setFontPreference(preference: FontPreference) {
      this.fontPreference = preference
      if (this.ownerScope) {
        writeUserScopedValue(
          USER_FONT_STORAGE_KEY,
          this.ownerScope,
          preference,
        )
      }
      this.applyFont()
    },
    setUserScope(scope: string | null) {
      this.ownerScope = scope ? requireUserScope(scope) : ''
      const stored = this.ownerScope
        ? readUserScopedValue<ThemePreference>(
            USER_THEME_STORAGE_KEY,
            this.ownerScope,
          )
        : null
      this.preference = supportedPreferences.includes(
        stored as ThemePreference,
      )
        ? (stored as ThemePreference)
        : 'light'
      const storedFont = this.ownerScope
        ? readUserScopedValue<FontPreference>(
            USER_FONT_STORAGE_KEY,
            this.ownerScope,
          )
        : null
      this.fontPreference = supportedFontPreferences.includes(
        storedFont as FontPreference,
      )
        ? (storedFont as FontPreference)
        : 'system'
      this.applyTheme()
      this.applyFont()
    },
    applyTheme() {
      document.documentElement.dataset.theme = this.resolvedTheme
      document.documentElement.style.colorScheme = this.resolvedTheme
    },
    applyFont() {
      document.documentElement.dataset.font = this.fontPreference
    },
  },
})
