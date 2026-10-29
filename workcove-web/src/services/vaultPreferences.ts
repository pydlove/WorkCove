export const DEFAULT_VAULT_UNLOCK_MINUTES = 60

export const VAULT_UNLOCK_MINUTE_VALUES = [
  5,
  15,
  30,
  60,
  120,
  240,
] as const

export const VAULT_UNLOCK_MINUTE_OPTIONS =
  VAULT_UNLOCK_MINUTE_VALUES.map((minutes) => ({
    value: String(minutes),
    label: vaultUnlockMinutesLabel(minutes),
  }))

export function normalizeVaultUnlockMinutes(value: unknown) {
  const minutes = Number(value)
  return VAULT_UNLOCK_MINUTE_VALUES.includes(
    minutes as (typeof VAULT_UNLOCK_MINUTE_VALUES)[number],
  )
    ? minutes
    : DEFAULT_VAULT_UNLOCK_MINUTES
}

export function vaultUnlockMinutesLabel(minutes: number) {
  if (minutes < 60) return `${minutes} 分钟`
  const hours = minutes / 60
  return Number.isInteger(hours)
    ? `${hours} 小时`
    : `${minutes} 分钟`
}
