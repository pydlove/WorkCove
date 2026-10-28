package com.pydlove.workcove.calendar.infrastructure.mail;

import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.port.CalendarReminderMailSender;
import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.infrastructure.mail.WorkCoveMailTls;
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
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Component
@ConditionalOnProperty(name = "workcove.mail.mode", havingValue = "smtp")
public class SmtpCalendarReminderMailSender
        implements CalendarReminderMailSender {

    private static final Logger log =
            LoggerFactory.getLogger(SmtpCalendarReminderMailSender.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpCalendarReminderMailSender(
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
    public void send(Email recipient, CalendarReminder reminder) {
        if (from == null || from.isBlank()) {
            throw new DomainException(
                    "邮件服务未配置，请设置 WORKCOVE_MAIL_USERNAME 和 WORKCOVE_MAIL_PASSWORD");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient.value());
            helper.setSubject("8小时工作台 日程提醒 · " + reminder.eventTitle());
            helper.setText(plainText(reminder), html(reminder));
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            log.warn(
                    "8小时工作台 calendar reminder email failed for {}: {}",
                    recipient.value(),
                    exception.getMessage());
            throw new DomainException("日程提醒邮件发送失败", exception);
        }
    }

    private static String plainText(CalendarReminder reminder) {
        return "8小时工作台 日程提醒\n\n"
                + reminder.eventTitle() + "\n"
                + reminder.eventDate().format(DATE_FORMAT) + " "
                + reminder.eventStartTime().format(TIME_FORMAT) + "\n\n"
                + "请提前安排好时间。";
    }

    private static String html(CalendarReminder reminder) {
        String title = escapeHtml(reminder.eventTitle());
        String date = reminder.eventDate().format(DATE_FORMAT);
        String time = reminder.eventStartTime().format(TIME_FORMAT);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f7fd;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif;color:#1f2328;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                    <tr><td align="center" style="padding:40px 16px;">
                      <table role="presentation" width="520" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:520px;background:#ffffff;border:1px solid #dce5f2;border-radius:16px;overflow:hidden;">
                        <tr><td style="padding:28px 32px 0;">
                          <div style="font-size:20px;font-weight:700;">8小时工作台</div>
                          <div style="margin-top:4px;font-size:12px;color:#6e6e73;">栖作 · 日程提醒</div>
                        </td></tr>
                        <tr><td style="padding:28px 32px 12px;">
                          <div style="font-size:12px;color:#6e6e73;letter-spacing:.08em;">UPCOMING EVENT</div>
                          <div style="margin-top:8px;font-size:22px;font-weight:700;line-height:1.4;">{{title}}</div>
                        </td></tr>
                        <tr><td style="padding:0 32px 30px;">
                          <div style="padding:18px 20px;background:#eef5ff;border-radius:12px;color:#0969da;font-size:16px;font-weight:600;">
                            {{date}} · {{time}}
                          </div>
                        </td></tr>
                        <tr><td style="padding:18px 32px;border-top:1px solid #edf1f7;font-size:12px;line-height:1.7;color:#86868b;">
                          这是你在 8小时工作台中设置的日程提醒。
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{title}}", title)
                .replace("{{date}}", date)
                .replace("{{time}}", time);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
