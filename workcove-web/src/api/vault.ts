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

export const unlockVaultWithPassword = (
  password: string,
  unlockMinutes: number,
) =>
  postJsonWithHeaders<{ expiresAt: string }>(
    '/vault/unlock/password',
    { password, unlockMinutes },
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
  unlockMinutes: number,
) =>
  postJsonWithHeaders<{ expiresAt: string }>(
    '/vault/unlock/passkey',
    { credentialId, response, unlockMinutes },
    ACTION_HEADER,
  )

export const listVaultEntries = (workspaceId: string) =>
  get<VaultEntry[]>(
    `/vault/entries?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const listVaultCategories = (workspaceId: string) =>
  get<VaultCategory[]>(
    `/vault/categories?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const createVaultCategory = (
  workspaceId: string,
  name: string,
) =>
  postJsonWithHeaders<VaultCategory>(
    '/vault/categories',
    { workspaceId, name },
    ACTION_HEADER,
  )

export const updateVaultCategory = (
  id: number,
  workspaceId: string,
  name: string,
) =>
  putJsonWithHeaders<VaultCategory>(
    `/vault/categories/${id}`,
    { workspaceId, name },
    ACTION_HEADER,
  )

export const deleteVaultCategory = (
  id: number,
  workspaceId: string,
  reassignTo: number,
) =>
  deleteNoContentWithHeaders(
    `/vault/categories/${id}?workspaceId=${encodeURIComponent(workspaceId)}&reassignTo=${reassignTo}`,
    ACTION_HEADER,
  )

export const createVaultEntry = (
  workspaceId: string,
  input: VaultEntryInput,
) =>
  postJsonWithHeaders<VaultEntry>(
    '/vault/entries',
    { workspaceId, ...input },
    ACTION_HEADER,
  )

export const updateVaultEntry = (
  id: number,
  workspaceId: string,
  input: VaultEntryInput,
) =>
  putJsonWithHeaders<VaultEntry>(
    `/vault/entries/${id}`,
    { workspaceId, ...input },
    ACTION_HEADER,
  )

export const deleteVaultEntry = (
  id: number,
  workspaceId: string,
) =>
  deleteNoContentWithHeaders(
    `/vault/entries/${id}?workspaceId=${encodeURIComponent(workspaceId)}`,
    ACTION_HEADER,
  )

export const getVaultSecret = (
  id: number,
  workspaceId: string,
) =>
  get<{ password: string }>(
    `/vault/entries/${id}/secret?workspaceId=${encodeURIComponent(workspaceId)}`,
  )

export const copyVaultEntry = (
  id: number,
  sourceWorkspaceId: string,
  targetWorkspaceId: string,
) =>
  postJsonWithHeaders<VaultEntry>(
    `/vault/entries/${id}/copy`,
    { sourceWorkspaceId, targetWorkspaceId },
    ACTION_HEADER,
  )

export const moveVaultEntry = (
  id: number,
  sourceWorkspaceId: string,
  targetWorkspaceId: string,
) =>
  postJsonWithHeaders<VaultEntry>(
    `/vault/entries/${id}/move`,
    { sourceWorkspaceId, targetWorkspaceId },
    ACTION_HEADER,
  )

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
