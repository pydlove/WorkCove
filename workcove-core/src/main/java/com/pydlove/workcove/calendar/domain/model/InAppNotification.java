package com.pydlove.workcove.calendar.domain.model;

import java.time.Instant;

public record InAppNotification(
        Long id,
        long ownerId,
        String workspaceId,
        Long eventId,
        String title,
        String body,
        String linkView,
        String dedupeKey,
        Instant readAt,
        Instant createdAt) {
}
