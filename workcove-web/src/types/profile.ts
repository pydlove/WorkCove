export interface UserProfile {
  email: string
  username: string
  displayName: string
  birthday: string
  location: string
  bio: string
  avatarDataUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface ProfileUpdate {
  username: string
  displayName: string
  birthday: string
  location: string
  bio: string
  avatarDataUrl: string | null
}
