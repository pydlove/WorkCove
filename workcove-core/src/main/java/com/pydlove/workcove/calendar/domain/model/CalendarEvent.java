package com.pydlove.workcove.calendar.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarEvent(
        Long id,
        long ownerId,
        String eventKey,
        String workspaceId,
        String title,
        LocalDate eventDate,
        LocalTime startTime,
        LocalTime endTime,
        String notes,
        String color,
        boolean reminderInApp,
        boolean reminderEmail,
        int reminderMinutesBefore,
        Instant createdAt,
        Instant updatedAt) {
}
