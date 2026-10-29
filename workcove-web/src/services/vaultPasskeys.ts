import {
  finishVaultRegistration,
  getVaultPasskeyOptions,
  getVaultRegistrationOptions,
  unlockVaultWithPasskey,
} from '@/api/vault'
import type {
  PasskeyAuthenticationOptions,
  PasskeyRegistrationOptions,
} from '@/types/vault'

function base64UrlToBuffer(value: string) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(
    Math.ceil(normalized.length / 4) * 4,
    '=',
  )
  const binary = window.atob(padded)
  return Uint8Array.from(binary, (character) =>
    character.charCodeAt(0),
  ).buffer
}

function bufferToBase64Url(value: ArrayBuffer) {
  const bytes = new Uint8Array(value)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return window
    .btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function registrationCredentialJson(
  credential: PublicKeyCredential,
) {
  const response = credential.response as AuthenticatorAttestationResponse
  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      attestationObject: bufferToBase64Url(response.attestationObject),
      transports: response.getTransports?.() ?? [],
    },
    clientExtensionResults: credential.getClientExtensionResults(),
  }
}

function assertionCredentialJson(
  credential: PublicKeyCredential,
) {
  const response = credential.response as AuthenticatorAssertionResponse
  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      authenticatorData: bufferToBase64Url(response.authenticatorData),
      signature: bufferToBase64Url(response.signature),
      userHandle: response.userHandle
        ? bufferToBase64Url(response.userHandle)
        : null,
    },
    clientExtensionResults: credential.getClientExtensionResults(),
  }
}

function authenticationRequest(
  options: PasskeyAuthenticationOptions,
): PublicKeyCredentialRequestOptions {
  return {
    challenge: base64UrlToBuffer(options.challenge),
    rpId: options.rpId,
    allowCredentials: options.allowCredentials.map((id) => ({
      id: base64UrlToBuffer(id),
      type: 'public-key',
      transports: ['internal'],
    })),
    timeout: options.timeout,
    userVerification:
      options.userVerification as UserVerificationRequirement,
  }
}

function registrationRequest(
  options: PasskeyRegistrationOptions,
): PublicKeyCredentialCreationOptions {
  return {
    challenge: base64UrlToBuffer(options.challenge),
    rp: options.rp,
    user: {
      id: base64UrlToBuffer(options.user.id),
      name: options.user.name,
      displayName: options.user.displayName,
    },
    pubKeyCredParams: options.pubKeyCredParams.map((parameter) => ({
      type: parameter.type as PublicKeyCredentialType,
      alg: parameter.alg,
    })),
    timeout: options.timeout,
    attestation:
      options.attestation as AttestationConveyancePreference,
    authenticatorSelection: {
      authenticatorAttachment:
        options.authenticatorSelection
          .authenticatorAttachment as AuthenticatorAttachment,
      residentKey:
        options.authenticatorSelection
          .residentKey as ResidentKeyRequirement,
      requireResidentKey:
        options.authenticatorSelection.requireResidentKey,
      userVerification:
        options.authenticatorSelection
          .userVerification as UserVerificationRequirement,
    },
    excludeCredentials: options.excludeCredentials.map((credential) => ({
      id: base64UrlToBuffer(credential.id),
      type: credential.type as PublicKeyCredentialType,
      transports: ['internal'],
    })),
  }
}

function passkeyError(error: unknown) {
  if (error instanceof DOMException && error.name === 'NotAllowedError') {
    return new Error('触控 ID 验证已取消或失败，请改用登录密码')
  }
  if (error instanceof DOMException && error.name === 'SecurityError') {
    return new Error('当前访问地址无法使用触控 ID，请改用登录密码')
  }
  return error instanceof Error ? error : new Error('触控 ID 操作失败')
}

export async function platformPasskeyAvailable() {
  if (
    typeof window.PublicKeyCredential === 'undefined' ||
    typeof navigator.credentials === 'undefined'
  ) {
    return false
  }
  try {
    return await PublicKeyCredential
      .isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

export async function registerVaultPasskey() {
  if (!(await platformPasskeyAvailable())) {
    throw new Error('当前浏览器或设备不支持触控 ID')
  }
  const options = await getVaultRegistrationOptions()
  try {
    const credential = await navigator.credentials.create({
      publicKey: registrationRequest(options),
    })
    if (!(credential instanceof PublicKeyCredential)) {
      throw new Error('触控 ID 注册未返回有效凭据')
    }
    await finishVaultRegistration(
      registrationCredentialJson(credential),
    )
  } catch (error) {
    throw passkeyError(error)
  }
}

export async function unlockVaultPasskey(unlockMinutes: number) {
  if (!(await platformPasskeyAvailable())) {
    throw new Error('当前浏览器或设备不支持触控 ID')
  }
  const options = await getVaultPasskeyOptions()
  try {
    const credential = await navigator.credentials.get({
      publicKey: authenticationRequest(options),
    })
    if (!(credential instanceof PublicKeyCredential)) {
      throw new Error('触控 ID 验证未返回有效凭据')
    }
    return await unlockVaultWithPasskey(
      credential.id,
      assertionCredentialJson(credential),
      unlockMinutes,
    )
  } catch (error) {
    throw passkeyError(error)
  }
}
