package com.pydlove.workcove.development.infrastructure.security;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.shared.domain.DomainException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmCredentialCipher implements CredentialCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    public AesGcmCredentialCipher(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("服务器凭据主密钥不能为空");
        }
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    @Override
    public String encrypt(String rawValue) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(
                    rawValue.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(iv.length + ciphertext.length);
            payload.put(iv).put(ciphertext);
            return PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.array());
        } catch (Exception exception) {
            throw new IllegalStateException("服务器密码加密失败", exception);
        }
    }

    @Override
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || !encryptedValue.startsWith(PREFIX)) {
            throw new DomainException("服务器密码密文格式无效");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(
                    encryptedValue.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES + TAG_BITS / 8) {
                throw new DomainException("服务器密码密文长度不正确");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(
                    payload,
                    IV_BYTES,
                    payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("服务器密码解密失败，请重新录入密码");
        }
    }
}
