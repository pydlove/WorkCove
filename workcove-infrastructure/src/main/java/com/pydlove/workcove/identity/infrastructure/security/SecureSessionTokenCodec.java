package com.pydlove.workcove.identity.infrastructure.security;

import com.pydlove.workcove.identity.domain.model.SessionTokenHash;
import com.pydlove.workcove.identity.domain.port.SessionTokenCodec;
import com.pydlove.workcove.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SecureSessionTokenCodec implements SessionTokenCodec {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public SessionTokenHash hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException("会话令牌不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return new SessionTokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
