export const DEFAULT_VAULT_CATEGORY = '未分类'

export interface VaultStatus {
  unlocked: boolean
  expiresAt: string | null
  passkeyEnabled: boolean
}

export interface VaultEntry {
  id: number
  name: string
  username: string
  url: string
  category: string
  notes: string
  updatedAt: string
}

export interface VaultCategory {
  id: number
  name: string
  sortOrder: number
}

export interface VaultEntryInput {
  name: string
  username: string
  password: string
  url: string
  category: string
  notes: string
}

export interface PasskeyAuthenticationOptions {
  rpId: string
  challenge: string
  allowCredentials: string[]
  timeout: number
  userVerification: string
}

export interface PasskeyRegistrationOptions {
  rp: {
    name: string
    id: string
  }
  user: {
    id: string
    name: string
    displayName: string
  }
  challenge: string
  timeout: number
  pubKeyCredParams: Array<{
    type: string
    alg: number
  }>
  attestation: string
  authenticatorSelection: {
    authenticatorAttachment: string
    residentKey: string
    requireResidentKey: boolean
    userVerification: string
  }
  excludeCredentials: Array<{
    type: string
    id: string
  }>
}
