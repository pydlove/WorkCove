import assert from 'node:assert/strict'
import test from 'node:test'

import {
  DEFAULT_VAULT_UNLOCK_MINUTES,
  normalizeVaultUnlockMinutes,
  vaultUnlockMinutesLabel,
} from '../src/services/vaultPreferences.ts'

test('defaults vault unlock duration to one hour', () => {
  assert.equal(DEFAULT_VAULT_UNLOCK_MINUTES, 60)
  assert.equal(normalizeVaultUnlockMinutes(undefined), 60)
  assert.equal(normalizeVaultUnlockMinutes(10), 60)
  assert.equal(normalizeVaultUnlockMinutes(240), 240)
})

test('formats vault unlock duration labels', () => {
  assert.equal(vaultUnlockMinutesLabel(15), '15 分钟')
  assert.equal(vaultUnlockMinutesLabel(60), '1 小时')
  assert.equal(vaultUnlockMinutesLabel(120), '2 小时')
})
