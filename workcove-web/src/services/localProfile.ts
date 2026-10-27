import type { UserProfile } from '@/types/profile'

const PROFILE_STORAGE_KEY = 'workcove.profiles.v1'
const MAX_AVATAR_BYTES = 8 * 1024 * 1024
const AVATAR_DIMENSION = 512
const ACCEPTED_AVATAR_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
])

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

export async function compressAvatar(file: File) {
  if (!ACCEPTED_AVATAR_TYPES.has(file.type)) {
    throw new Error('请选择 JPG、PNG 或 WebP 图片')
  }
  if (file.size > MAX_AVATAR_BYTES) {
    throw new Error('头像文件不能超过 8MB')
  }

  const bitmap = await window.createImageBitmap(file)
  const side = Math.min(bitmap.width, bitmap.height)
  const sourceX = Math.max(0, (bitmap.width - side) / 2)
  const sourceY = Math.max(0, (bitmap.height - side) / 2)
  const canvas = document.createElement('canvas')
  canvas.width = AVATAR_DIMENSION
  canvas.height = AVATAR_DIMENSION

  const context = canvas.getContext('2d')
  if (!context) {
    bitmap.close()
    throw new Error('当前环境无法处理头像')
  }

  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(
    bitmap,
    sourceX,
    sourceY,
    side,
    side,
    0,
    0,
    AVATAR_DIMENSION,
    AVATAR_DIMENSION,
  )
  bitmap.close()

  return canvas.toDataURL('image/webp', 0.86)
}
