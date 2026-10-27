const ACCOUNT_STORAGE_KEY = 'workcove.local-accounts.v1'
const PASSWORD_ITERATIONS = 160_000

interface StoredAccount {
  email: string
  salt: string
  passwordHash: string
  createdAt: string
}

export type LocalAuthErrorCode = 'EMAIL_EXISTS' | 'INVALID_CREDENTIALS'

export class LocalAuthError extends Error {
  code: LocalAuthErrorCode

  constructor(code: LocalAuthErrorCode) {
    super(code)
    this.name = 'LocalAuthError'
    this.code = code
  }
}

export function normalizeEmail(email: string) {
  return email.trim().toLocaleLowerCase('en-US')
}

export function emailToDisplayName(email: string) {
  const localPart = normalizeEmail(email).split('@')[0] || 'Yong'
  return localPart.charAt(0).toUpperCase() + localPart.slice(1)
}

function readAccounts(): StoredAccount[] {
  const raw = window.localStorage.getItem(ACCOUNT_STORAGE_KEY)
  if (!raw) return []

  try {
    const stored = JSON.parse(raw) as StoredAccount[]
    return Array.isArray(stored) ? stored : []
  } catch {
    return []
  }
}

function writeAccounts(accounts: StoredAccount[]) {
  window.localStorage.setItem(ACCOUNT_STORAGE_KEY, JSON.stringify(accounts))
}

function bytesToBase64(bytes: Uint8Array) {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return window.btoa(binary)
}

function base64ToBytes(value: string) {
  const binary = window.atob(value)
  return Uint8Array.from(binary, (character) => character.charCodeAt(0))
}

async function derivePasswordHash(password: string, salt: string) {
  const keyMaterial = await window.crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveBits'],
  )
  const derivedBits = await window.crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      hash: 'SHA-256',
      salt: base64ToBytes(salt),
      iterations: PASSWORD_ITERATIONS,
    },
    keyMaterial,
    256,
  )

  return bytesToBase64(new Uint8Array(derivedBits))
}

function secureEquals(left: string, right: string) {
  if (left.length !== right.length) return false

  let mismatch = 0
  for (let index = 0; index < left.length; index += 1) {
    mismatch |= left.charCodeAt(index) ^ right.charCodeAt(index)
  }
  return mismatch === 0
}

export async function registerLocalAccount(email: string, password: string) {
  const normalizedEmail = normalizeEmail(email)
  const accounts = readAccounts()

  if (accounts.some((account) => account.email === normalizedEmail)) {
    throw new LocalAuthError('EMAIL_EXISTS')
  }

  const salt = bytesToBase64(
    window.crypto.getRandomValues(new Uint8Array(16)),
  )
  const passwordHash = await derivePasswordHash(password, salt)

  accounts.push({
    email: normalizedEmail,
    salt,
    passwordHash,
    createdAt: new Date().toISOString(),
  })
  writeAccounts(accounts)

  return { email: normalizedEmail }
}

export async function authenticateLocalAccount(
  email: string,
  password: string,
) {
  const normalizedEmail = normalizeEmail(email)
  const account = readAccounts().find(
    (storedAccount) => storedAccount.email === normalizedEmail,
  )

  if (!account) {
    throw new LocalAuthError('INVALID_CREDENTIALS')
  }

  const passwordHash = await derivePasswordHash(password, account.salt)
  if (!secureEquals(passwordHash, account.passwordHash)) {
    throw new LocalAuthError('INVALID_CREDENTIALS')
  }

  return { email: account.email }
}
