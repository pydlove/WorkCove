import {
  readUserScopedValue,
  writeUserScopedValue,
} from '@/services/userScopedStorage'
import type {
  ScreensaverId,
  ScreensaverSettings,
} from '@/types/screensaver'
import { SCREENSAVER_PRESETS } from '@/types/screensaver'

const STORAGE_KEY = 'workcove.screensaver.v1'
export const MAX_SCREENSAVER_DATA_URL_LENGTH = 5 * 1024 * 1024

const validIds = new Set<ScreensaverId>([
  ...SCREENSAVER_PRESETS.map((preset) => preset.id),
  'custom',
])

export function defaultScreensaverSettings(): ScreensaverSettings {
  return {
    selectedId: 'deep-forest',
    customImageDataUrl: '',
    customImageName: '',
  }
}

export function loadLocalScreensaver(email: string) {
  const stored = readUserScopedValue<Partial<ScreensaverSettings>>(
    STORAGE_KEY,
    email,
  )
  if (!stored || typeof stored !== 'object') {
    return defaultScreensaverSettings()
  }
  const customImageDataUrl =
    typeof stored.customImageDataUrl === 'string' &&
    stored.customImageDataUrl.length <= MAX_SCREENSAVER_DATA_URL_LENGTH &&
    /^data:image\/(?:jpeg|png|webp);base64,/i.test(
      stored.customImageDataUrl,
    )
      ? stored.customImageDataUrl
      : ''
  const selectedId =
    validIds.has(stored.selectedId as ScreensaverId)
      ? (stored.selectedId as ScreensaverId)
      : 'deep-forest'
  return {
    selectedId:
      selectedId === 'custom' && !customImageDataUrl
        ? 'deep-forest'
        : selectedId,
    customImageDataUrl,
    customImageName:
      typeof stored.customImageName === 'string'
        ? stored.customImageName.slice(0, 255)
        : '',
  }
}

export function saveLocalScreensaver(
  email: string,
  settings: ScreensaverSettings,
) {
  writeUserScopedValue(STORAGE_KEY, email, settings)
}
