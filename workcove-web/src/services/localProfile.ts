import { PRODUCT_NAME } from '@/brand'
import type { UserProfile } from '@/types/profile'
import {
  readUserScopedMap,
  requireUserScope,
  writeUserScopedMap,
} from '@/services/userScopedStorage'

const PROFILE_STORAGE_KEY = 'workcove.profiles.v1'

function readProfiles(): Record<string, UserProfile> {
  return readUserScopedMap<UserProfile>(PROFILE_STORAGE_KEY)
}

function writeProfiles(profiles: Record<string, UserProfile>) {
  writeUserScopedMap(PROFILE_STORAGE_KEY, profiles)
}

function defaultUsername(email: string) {
  const localPart = email.split('@')[0] ?? ''
  return localPart.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 24) || 'workcove'
}

export function loadLocalProfile(
  email: string,
  fallbackDisplayName: string,
): UserProfile {
  const normalizedEmail = requireUserScope(email)
  const existing = readProfiles()[normalizedEmail]
  if (existing) return existing

  const now = new Date().toISOString()
  return {
    email: normalizedEmail,
    username: defaultUsername(normalizedEmail),
    displayName: fallbackDisplayName.trim() || PRODUCT_NAME,
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
  profiles[requireUserScope(profile.email)] = profile
  writeProfiles(profiles)
  return profile
}
