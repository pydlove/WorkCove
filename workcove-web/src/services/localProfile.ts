import type { UserProfile } from '@/types/profile'

const PROFILE_STORAGE_KEY = 'workcove.profiles.v1'

function readProfiles(): Record<string, UserProfile> {
  const raw = window.localStorage.getItem(PROFILE_STORAGE_KEY)
  if (!raw) return {}

  try {
    const parsed = JSON.parse(raw) as Record<string, UserProfile>
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function writeProfiles(profiles: Record<string, UserProfile>) {
  window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profiles))
}

function defaultUsername(email: string) {
  const localPart = email.split('@')[0] ?? ''
  return localPart.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 24) || 'workcove'
}

export function loadLocalProfile(
  email: string,
  fallbackDisplayName: string,
): UserProfile {
  const normalizedEmail = email.trim().toLocaleLowerCase('en-US')
  const existing = readProfiles()[normalizedEmail]
  if (existing) return existing

  const now = new Date().toISOString()
  return {
    email: normalizedEmail,
    username: defaultUsername(normalizedEmail),
    displayName: fallbackDisplayName.trim() || 'WorkCove',
    birthday: '',
    location: '',
    bio: '',
    avatarDataUrl: null,
    createdAt: now,
    updatedAt: now,
  }
}

export function saveLocalProfile(profile: UserProfile) {
  const profiles = readProfiles()
  profiles[profile.email] = profile
  writeProfiles(profiles)
  return profile
}
