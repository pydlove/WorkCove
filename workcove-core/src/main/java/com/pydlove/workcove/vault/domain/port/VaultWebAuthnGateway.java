package com.pydlove.workcove.vault.domain.port;

public interface VaultWebAuthnGateway {

    RegistrationVerification verifyRegistration(
            String responseJson,
            byte[] challenge,
            String origin,
            String rpId);

    AuthenticationVerification verifyAuthentication(
            String registrationJson,
            String credentialId,
            long storedSignCount,
            String responseJson,
            byte[] challenge,
            String origin,
            String rpId);

    record RegistrationVerification(
            String credentialId,
            long signCount) {
    }

    record AuthenticationVerification(
            String credentialId,
            long signCount) {
    }
}
