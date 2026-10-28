package com.pydlove.workcove.identity.infrastructure.security;

import com.pydlove.workcove.identity.domain.model.EmailVerificationCodeHash;
import com.pydlove.workcove.identity.domain.port.EmailVerificationCodec;
import com.pydlove.workcove.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class SecureEmailVerificationCodec implements EmailVerificationCodec {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public EmailVerificationCodeHash hash(String rawCode) {
        if (rawCode == null || !rawCode.matches("\\d{6}")) {
            throw new DomainException("验证码格式无效");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawCode.getBytes(StandardCharsets.US_ASCII));
            return new EmailVerificationCodeHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
