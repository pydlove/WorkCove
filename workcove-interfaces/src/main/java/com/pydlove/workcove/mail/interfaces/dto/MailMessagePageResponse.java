package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.domain.model.MailMessagePage;

import java.util.List;

public record MailMessagePageResponse(
        List<MailMessageSummaryResponse> messages,
        int total,
        int offset,
        int limit,
        boolean hasMore
) {

    public static MailMessagePageResponse from(MailMessagePage page) {
        return new MailMessagePageResponse(
                page.messages().stream()
                        .map(MailMessageSummaryResponse::from)
                        .toList(),
                page.total(),
                page.offset(),
                page.limit(),
                page.hasMore());
    }
}
