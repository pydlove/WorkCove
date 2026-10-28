package com.pydlove.workcove.mail.interfaces.dto;

import java.util.List;

public record MailSendRequest(
        String workspaceId,
        long accountId,
        List<Recipient> to,
        List<Recipient> cc,
        String subject,
        String plainBody,
        String htmlBody,
        String inReplyTo,
        boolean readReceipt
) {

    public record Recipient(String name, String address) {
    }
}
