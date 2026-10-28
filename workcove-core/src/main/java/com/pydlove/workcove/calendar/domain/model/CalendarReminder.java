package com.pydlove.workcove.calendar.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarReminder(
        Long id,
        long ownerId,
        long eventId,
        String eventKey,
        String workspaceId,
        String eventTitle,
        LocalDate eventDate,
        LocalTime eventStartTime,
        CalendarReminderChannel channel,
        Instant scheduledAt,
        Instant availableAt,
        CalendarReminderStatus status,
        int attempts,
        Instant sentAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
