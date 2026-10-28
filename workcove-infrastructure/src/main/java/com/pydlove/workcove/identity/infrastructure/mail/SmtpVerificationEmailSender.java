package com.pydlove.workcove.identity.infrastructure.mail;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.port.VerificationEmailSender;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

@Component
@ConditionalOnProperty(name = "workcove.mail.mode", havingValue = "smtp")
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log =
            LoggerFactory.getLogger(SmtpVerificationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String from,
            @Value("${workcove.mail.trust-store:${user.home}/.workcove/mail-truststore.p12}")
            String trustStorePath,
            @Value("${workcove.mail.trust-store-password:workcove-mail-trust}")
            String trustStorePassword) {
        this.mailSender = mailSender;
        this.from = from;
        Path resolvedTrustStore =
                WorkCoveMailTls.resolveTrustStore(trustStorePath);
        if (mailSender instanceof JavaMailSenderImpl javaMailSender
                && resolvedTrustStore != null) {
            Properties properties = javaMailSender.getJavaMailProperties();
            properties.put(
                    "mail.smtp.ssl.socketFactory",
                    WorkCoveMailTls.socketFactory(
                            resolvedTrustStore.toString(),
                            trustStorePassword));
            properties.put("mail.smtp.ssl.checkserveridentity", "true");
        }
    }

    @Override
    public void sendRegistrationCode(Email email, String rawCode, Duration ttl) {
        if (from == null || from.isBlank()) {
            throw new DomainException("邮件服务未配置，请设置 WORKCOVE_MAIL_USERNAME 和 WORKCOVE_MAIL_PASSWORD");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.value());
            helper.setSubject("8小时工作台 · 邮箱验证码");
            helper.setText(plainText(rawCode, ttl), html(rawCode, ttl));
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            log.warn("8小时工作台 verification email failed for {}: {}",
                    email.value(), exception.getMessage());
            throw new DomainException("验证码邮件发送失败，请检查 SMTP 配置", exception);
        }
    }

    private static String plainText(String code, Duration ttl) {
        return "你正在注册 8小时工作台。\n\n"
                + "邮箱验证码：" + code + "\n"
                + "验证码 " + Math.max(1, ttl.toMinutes()) + " 分钟内有效，仅可使用一次。\n\n"
                + "如果不是你本人操作，请忽略本邮件。";
    }

    private static String html(String code, Duration ttl) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f7fd;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif;color:#1f2328;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                    <tr><td align="center" style="padding:40px 16px;">
                      <table role="presentation" width="520" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:520px;background:#ffffff;border:1px solid #dce5f2;border-radius:16px;overflow:hidden;">
                        <tr><td style="padding:28px 32px 0;">
                          <div style="font-size:20px;font-weight:700;">8小时工作台</div>
                          <div style="margin-top:4px;font-size:12px;color:#6e6e73;">栖作 · 专注每一天</div>
                        </td></tr>
                        <tr><td style="padding:28px 32px 8px;font-size:15px;line-height:1.7;color:#3a3a3c;">
                          你正在注册 8小时工作台账号，请输入下面的邮箱验证码：
                        </td></tr>
                        <tr><td style="padding:12px 32px 24px;">
                          <div style="padding:22px;background:#eef5ff;border-radius:12px;text-align:center;font-size:40px;font-weight:700;letter-spacing:10px;color:#0969da;">{{code}}</div>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px;font-size:13px;line-height:1.7;color:#6e6e73;">
                          验证码 {{ttl}} 分钟内有效，仅可使用一次。请勿转发给他人。
                        </td></tr>
                        <tr><td style="padding:18px 32px;border-top:1px solid #edf1f7;font-size:12px;line-height:1.7;color:#86868b;">
                          如果不是你本人操作，请忽略本邮件。
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{code}}", code)
                .replace("{{ttl}}", String.valueOf(Math.max(1, ttl.toMinutes())));
    }
}
