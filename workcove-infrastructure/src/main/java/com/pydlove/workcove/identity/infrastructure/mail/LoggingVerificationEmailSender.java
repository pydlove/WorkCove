package com.pydlove.workcove.identity.infrastructure.mail;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.port.VerificationEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 本地开发模式：验证码只输出到后端日志，不连接外部邮件服务。 */
@Component
@ConditionalOnProperty(
        name = "workcove.mail.mode",
        havingValue = "log",
        matchIfMissing = true)
public class LoggingVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingVerificationEmailSender.class);

    @Override
    public void sendRegistrationCode(Email email, String rawCode, Duration ttl) {
        log.info(
                "8小时工作台 development email code: email={}, code={}, ttlMinutes={}",
                email.value(),
                rawCode,
                Math.max(1, ttl.toMinutes()));
    }
}
