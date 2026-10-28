package com.pydlove.workcove.mail.infrastructure;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.user.GreenMailUser;
import com.pydlove.workcove.mail.domain.model.MailConnectionSettings;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailMessageSummary;
import com.pydlove.workcove.mail.domain.model.MailOutgoingMessage;
import com.pydlove.workcove.mail.domain.model.MailRecipient;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class JakartaMailGatewayTest {

    private GreenMail greenMail;
    private GreenMailUser user;
    private MailConnectionSettings settings;

    @BeforeEach
    void startMailServer() throws Exception {
        greenMail = new GreenMail(new ServerSetup[] {
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP),
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP),
        });
        greenMail.start();
        user = greenMail.setUser(
                "me@example.com",
                "me@example.com",
                "secret");
        user.deliver(message("Test mail", "<incoming@example.com>"));

        settings = new MailConnectionSettings(
                "工作邮箱",
                "me@example.com",
                "me@example.com",
                "secret",
                "127.0.0.1",
                greenMail.getImap().getPort(),
                false,
                "127.0.0.1",
                greenMail.getSmtp().getPort(),
                false,
                true);
    }

    @AfterEach
    void stopMailServer() {
        if (greenMail != null) greenMail.stop();
    }

    @Test
    void verifiesReadsAndRepliesToMail() throws Exception {
        JakartaMailGateway gateway = new JakartaMailGateway(false);

        gateway.verify(settings);
        assertThat(gateway.folders(settings))
                .anySatisfy((folder) -> {
                    assertThat(folder.path()).isEqualTo("INBOX");
                    assertThat(folder.role().name()).isEqualTo("INBOX");
                });
        MailMessagePage inboxPage = gateway.messages(
                settings,
                "INBOX",
                false,
                20,
                0);
        List<MailMessageSummary> inbox = inboxPage.messages();
        assertThat(inboxPage.total()).isEqualTo(1);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).fromAddress())
                .isEqualTo("sender@example.com");
        assertThat(inbox.get(0).subject()).isEqualTo("Test mail");
        assertThat(inbox.get(0).preview()).contains("第一行");

        MailMessageDetail detail = gateway.message(
                settings,
                "INBOX",
                inbox.get(0).uid());
        assertThat(detail.fromName()).isEqualTo("Alice");
        assertThat(detail.content()).contains("第二行");
        assertThat(detail.html()).isFalse();
        assertThat(detail.messageId()).isNotBlank();
        gateway.setStarred(
                settings,
                "INBOX",
                inbox.get(0).uid(),
                true);
        assertThat(gateway.message(
                settings,
                "INBOX",
                inbox.get(0).uid()).starred()).isTrue();

        gateway.reply(
                settings,
                detail,
                "收到，谢谢。");
        assertThat(greenMail.waitForIncomingEmail(2_000, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(2);
        MimeMessage reply = java.util.Arrays.stream(messages)
                .filter((message) -> {
                    try {
                        return message.getRecipients(Message.RecipientType.TO)[0]
                                .toString()
                                .contains("sender@example.com");
                    } catch (Exception exception) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow();
        assertThat(reply.getSubject()).isEqualTo("Re: Test mail");
        assertThat(reply.getHeader("In-Reply-To", ","))
                .contains(detail.messageId());
    }

    @Test
    void paginatesMailNewestFirst() throws Exception {
        user.deliver(message("Second mail", "<second@example.com>"));
        user.deliver(message("Third mail", "<third@example.com>"));
        JakartaMailGateway gateway = new JakartaMailGateway(false);

        MailMessagePage first = gateway.messages(
                settings,
                "INBOX",
                false,
                2,
                0);
        MailMessagePage second = gateway.messages(
                settings,
                "INBOX",
                false,
                2,
                2);

        assertThat(first.total()).isEqualTo(3);
        assertThat(first.messages())
                .extracting(MailMessageSummary::subject)
                .containsExactly("Third mail", "Second mail");
        assertThat(first.hasMore()).isTrue();
        assertThat(second.messages())
                .extracting(MailMessageSummary::subject)
                .containsExactly("Test mail");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void createsAndDiscoversAnImapFolder() {
        JakartaMailGateway gateway = new JakartaMailGateway(false);

        var created = gateway.createFolder(settings, "待处理");

        assertThat(created.name()).isEqualTo("待处理");
        assertThat(created.path()).isEqualTo("待处理");
        assertThat(gateway.folders(settings))
                .anySatisfy((folder) -> {
                    assertThat(folder.path()).isEqualTo("待处理");
                    assertThat(folder.role().name()).isEqualTo("CUSTOM");
                });
    }

    @Test
    void movesMailIntoAnImapFolder() {
        JakartaMailGateway gateway = new JakartaMailGateway(false);
        gateway.createFolder(settings, "待处理");
        String uid = gateway.messages(settings, "INBOX", false, 20, 0)
                .messages()
                .get(0)
                .uid();

        gateway.move(settings, "INBOX", uid, "待处理");

        assertThat(gateway.messages(
                settings,
                "INBOX",
                false,
                20,
                0).messages()).isEmpty();
        assertThat(gateway.messages(
                settings,
                "待处理",
                false,
                20,
                0).messages()).hasSize(1);
    }

    @Test
    void canUseSmtpWithoutAuthenticationWhenTheServerAllowsIt()
            throws Exception {
        MailConnectionSettings unauthenticated = new MailConnectionSettings(
                settings.displayName(),
                settings.emailAddress(),
                settings.username(),
                settings.password(),
                settings.imapHost(),
                settings.imapPort(),
                settings.imapSsl(),
                settings.smtpHost(),
                settings.smtpPort(),
                settings.smtpSsl(),
                false);
        JakartaMailGateway gateway = new JakartaMailGateway(false);

        gateway.verify(unauthenticated);
        MailMessageDetail detail = gateway.message(
                unauthenticated,
                "INBOX",
                gateway.messages(
                        unauthenticated,
                        "INBOX",
                        false,
                        20,
                        0).messages().get(0).uid());
        gateway.reply(unauthenticated, detail, "No-auth reply");

        assertThat(greenMail.waitForIncomingEmail(2_000, 1)).isTrue();
    }

    @Test
    void sendsHtmlMailWithCcAndReadReceipt() throws Exception {
        JakartaMailGateway gateway = new JakartaMailGateway(false);
        gateway.send(
                settings,
                new MailOutgoingMessage(
                        List.of(new MailRecipient(
                                "Alice",
                                "alice@example.com")),
                        List.of(new MailRecipient(
                                "Bob",
                                "bob@example.com")),
                        "Composed mail",
                        "Plain body",
                        "<p><strong>HTML body</strong></p>",
                        "",
                        true));

        assertThat(greenMail.waitForIncomingEmail(2_000, 1)).isTrue();
        MimeMessage sent = java.util.Arrays.stream(
                        greenMail.getReceivedMessages())
                .filter((message) -> {
                    try {
                        return "Composed mail".equals(message.getSubject());
                    } catch (Exception exception) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow();
        assertThat(sent.getSubject()).isEqualTo("Composed mail");
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0]
                .toString()).contains("alice@example.com");
        assertThat(sent.getRecipients(Message.RecipientType.CC)[0]
                .toString()).contains("bob@example.com");
        assertThat(sent.getHeader(
                "Disposition-Notification-To",
                ",")).contains("me@example.com");
    }

    private static MimeMessage message(
            String subject,
            String messageId) throws Exception {
        MimeMessage message = new MimeMessage(
                Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(
                "sender@example.com",
                "Alice"));
        message.setRecipient(
                Message.RecipientType.TO,
                new InternetAddress("me@example.com"));
        message.setSubject(subject);
        message.setText("第一行\n第二行");
        message.setHeader("Message-ID", messageId);
        return message;
    }
}
