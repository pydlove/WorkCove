package com.pydlove.workcove.development.domain.port;

public interface CredentialCipher {

    String encrypt(String rawValue);

    String decrypt(String encryptedValue);
}
