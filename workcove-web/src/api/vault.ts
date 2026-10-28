import {
  deleteNoContentWithHeaders,
  get,
  postJson,
  postJsonNoContentWithHeaders,
  postJsonWithHeaders,
  putJsonWithHeaders,
} from '@/api/http'
import type {
  PasskeyAuthenticationOptions,
  PasskeyRegistrationOptions,
  VaultCategory,
  VaultEntry,
  VaultEntryInput,
  VaultStatus,
} from '@/types/vault'

const ACTION_HEADER = { 'X-WorkCove-Action': 'manage-vault' }

export const getVaultStatus = () =>
  get<VaultStatus>('/vault/status')

export const unlockVaultWithPassword = (password: string) =>
  postJsonWithHeaders<{ expiresAt: string }>(
    '/vault/unlock/password',
    { password },
    ACTION_HEADER,
  )

export const lockVault = () =>
  postJsonNoContentWithHeaders(
    '/vault/lock',
    {},
    ACTION_HEADER,
  )

export const getVaultPasskeyOptions = () =>
  postJsonWithHeaders<PasskeyAuthenticationOptions>(
    '/vault/unlock/passkey/options',
    {},
    ACTION_HEADER,
  )

export const unlockVaultWithPasskey = (
  credentialId: string,
  response: unknown,
) =>
  postJsonWithHeaders<{ expiresAt: string }>(
    '/vault/unlock/passkey',
    { credentialId, response },
    ACTION_HEADER,
  )

export const listVaultEntries = () =>
  get<VaultEntry[]>('/vault/entries')

export const listVaultCategories = () =>
  get<VaultCategory[]>('/vault/categories')

export const createVaultCategory = (name: string) =>
  postJsonWithHeaders<VaultCategory>(
    '/vault/categories',
    { name },
    ACTION_HEADER,
  )

export const updateVaultCategory = (id: number, name: string) =>
  putJsonWithHeaders<VaultCategory>(
    `/vault/categories/${id}`,
    { name },
    ACTION_HEADER,
  )

export const deleteVaultCategory = (
  id: number,
  reassignTo: number,
) =>
  deleteNoContentWithHeaders(
    `/vault/categories/${id}?reassignTo=${reassignTo}`,
    ACTION_HEADER,
  )

export const createVaultEntry = (input: VaultEntryInput) =>
  postJsonWithHeaders<VaultEntry>(
    '/vault/entries',
    input,
    ACTION_HEADER,
  )

export const updateVaultEntry = (
  id: number,
  input: VaultEntryInput,
) =>
  putJsonWithHeaders<VaultEntry>(
    `/vault/entries/${id}`,
    input,
    ACTION_HEADER,
  )

export const deleteVaultEntry = (id: number) =>
  deleteNoContentWithHeaders(
    `/vault/entries/${id}`,
    ACTION_HEADER,
  )

export const getVaultSecret = (id: number) =>
  get<{ password: string }>(`/vault/entries/${id}/secret`)

export const getVaultRegistrationOptions = () =>
  postJsonWithHeaders<PasskeyRegistrationOptions>(
    '/vault/passkeys/registration/options',
    {},
    ACTION_HEADER,
  )

export const finishVaultRegistration = (response: unknown) =>
  postJsonNoContentWithHeaders(
    '/vault/passkeys/registration',
    { response },
    ACTION_HEADER,
  )
