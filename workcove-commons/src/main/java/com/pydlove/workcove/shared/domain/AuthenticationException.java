package com.pydlove.workcove.shared.domain;

/** 凭证无效或会话失效，接口层应返回 HTTP 401。 */
public class AuthenticationException extends DomainException {

    public AuthenticationException(String message) {
        super(message);
    }
}
