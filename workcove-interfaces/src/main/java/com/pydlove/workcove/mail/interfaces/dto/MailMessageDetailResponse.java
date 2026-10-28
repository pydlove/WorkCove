package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.domain.model.MailAttachment;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailRecipient;

import java.time.Instant;
import java.util.List;

public record MailMessageDetailResponse(
        String folderPath,
        String uid,
        String messageId,
        String fromName,
        String fromAddress,
        List<Recipient> to,
        List<Recipient> cc,
        String subject,
        Instant receivedAt,
        String content,
        boolean html,
        boolean starred,
        List<Attachment> attachments
) {

    public static MailMessageDetailResponse from(MailMessageDetail message) {
        return new MailMessageDetailResponse(
                message.folderPath(),
                message.uid(),
                message.messageId(),
                message.fromName(),
                message.fromAddress(),
                message.to().stream().map(Recipient::from).toList(),
                message.cc().stream().map(Recipient::from).toList(),
                message.subject(),
                message.receivedAt(),
                message.content(),
                message.html(),
                message.starred(),
                message.attachments().stream().map(Attachment::from).toList());
    }

    public record Recipient(String name, String address) {

        public static Recipient from(MailRecipient recipient) {
            return new Recipient(recipient.name(), recipient.address());
        }
    }

    public record Attachment(
            String filename,
            String contentType,
            long size) {

        public static Attachment from(MailAttachment attachment) {
            return new Attachment(
                    attachment.filename(),
                    attachment.contentType(),
                    attachment.size());
        }
    }
}
