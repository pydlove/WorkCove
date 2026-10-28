package com.pydlove.workcove.mail.domain.model;

import java.util.List;

public record MailMessagePage(
        List<MailMessageSummary> messages,
        int total,
        int offset,
        int limit
) {

    public boolean hasMore() {
        return offset + messages.size() < total;
    }
}
