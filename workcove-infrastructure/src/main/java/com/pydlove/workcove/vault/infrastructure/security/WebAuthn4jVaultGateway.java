package com.pydlove.workcove.vault.infrastructure.security;

import com.pydlove.workcove.shared.domain.DomainException;
import com.pydlove.workcove.vault.domain.port.VaultWebAuthnGateway;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

public final class WebAuthn4jVaultGateway implements VaultWebAuthnGateway {

    private final WebAuthnManager manager =
            WebAuthnManager.createNonStrictWebAuthnManager();

    @Override
    public RegistrationVerification verifyRegistration(
            String responseJson,
            byte[] challenge,
            String origin,
            String rpId) {
        try {
            ServerProperty serverProperty = serverProperty(
                    challenge,
                    origin,
                    rpId);
            RegistrationData registration = manager
                    .verifyRegistrationResponseJSON(
                            responseJson,
                            new RegistrationParameters(
                                    serverProperty,
                                    true,
                                    true));
            AuthenticatorData<?> authenticatorData = registration
                    .getAttestationObject()
                    .getAuthenticatorData();
            AttestedCredentialData credentialData =
                    authenticatorData.getAttestedCredentialData();
            if (credentialData == null) {
                throw new DomainException("触控 ID 凭据无效");
            }
            return new RegistrationVerification(
                    base64Url(credentialData.getCredentialId()),
                    Math.max(0, authenticatorData.getSignCount()));
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("触控 ID 注册验证失败，请重试");
        }
    }

    @Override
    public AuthenticationVerification verifyAuthentication(
            String registrationJson,
            String credentialId,
            long storedSignCount,
            String responseJson,
            byte[] challenge,
            String origin,
            String rpId) {
        try {
            byte[] expectedCredentialId = Base64.getUrlDecoder()
                    .decode(credentialId);
            RegistrationData registration = manager
                    .parseRegistrationResponseJSON(registrationJson);
            CredentialRecordImpl record = new CredentialRecordImpl(
                    registration.getAttestationObject(),
                    registration.getCollectedClientData(),
                    registration.getClientExtensions(),
                    registration.getTransports());
            record.setCounter(storedSignCount);
            AuthenticationData authentication = manager
                    .verifyAuthenticationResponseJSON(
                            responseJson,
                            new AuthenticationParameters(
                                    serverProperty(challenge, origin, rpId),
                                    record,
                                    List.of(expectedCredentialId),
                                    true,
                                    true));
            if (!MessageDigest.isEqual(
                    expectedCredentialId,
                    authentication.getCredentialId())) {
                throw new DomainException("触控 ID 凭据不匹配");
            }
            return new AuthenticationVerification(
                    base64Url(authentication.getCredentialId()),
                    Math.max(
                            0,
                            authentication
                                    .getAuthenticatorData()
                                    .getSignCount()));
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("触控 ID 验证失败，请重试或使用登录密码");
        }
    }

    private static ServerProperty serverProperty(
            byte[] challenge,
            String origin,
            String rpId) {
        if (challenge == null || challenge.length < 16) {
            throw new DomainException("WebAuthn 挑战无效");
        }
        if (origin == null || origin.isBlank()
                || rpId == null || rpId.isBlank()) {
            throw new DomainException("WebAuthn 来源无效");
        }
        return ServerProperty.builder()
                .origin(new Origin(origin))
                .rpId(rpId)
                .challenge(new DefaultChallenge(challenge))
                .build();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }
}
