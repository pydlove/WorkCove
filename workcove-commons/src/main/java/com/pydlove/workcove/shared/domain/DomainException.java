package com.pydlove.workcove.shared.domain;

/** 可预期的业务规则失败，接口层负责转换成用户可读的错误响应。 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
