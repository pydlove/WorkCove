package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/** 规范化后的邮箱地址，领域层不依赖 Jakarta Validation 或 Spring。 */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainException("邮箱不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH) {
            throw new DomainException("邮箱长度不能超过 " + MAX_LENGTH + " 个字符");
        }
        if (!FORMAT.matcher(normalized).matches()) {
            throw new DomainException("请输入有效的邮箱地址");
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
