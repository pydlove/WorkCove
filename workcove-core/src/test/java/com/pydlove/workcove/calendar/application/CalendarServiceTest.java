package com.pydlove.workcove.calendar.application;

import com.pydlove.workcove.calendar.domain.model.CalendarEvent;
import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderSettings;
import com.pydlove.workcove.calendar.domain.model.InAppNotification;
import com.pydlove.workcove.calendar.domain.repository.CalendarRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalendarServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-10-27T01:00:00Z");
    private static final long OWNER_ID = 42L;

    private final InMemoryCalendarRepository repository =
            new InMemoryCalendarRepository();
    private final CalendarService service = new CalendarService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void schedulesInAppAndEmailRemindersInShanghaiTime() {
        CalendarService.CalendarEventView saved = service.saveEvent(
                OWNER_ID,
                new CalendarService.CalendarEventInput(
                        null,
                        "workspace-1",
                        "版本评审",
                        LocalDate.of(2026, 10, 28),
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        "带上发布清单",
                        "blue",
                        true,
                        true,
                        10));

        assertEquals("2026-10-28", saved.date());
        assertEquals("10:00", saved.startTime());
        assertEquals(2, repository.reminders.size());
        assertEquals(
                Instant.parse("2026-10-28T01:50:00Z"),
                repository.reminders.get(0).scheduledAt());
    }

    @Test
    void schedulesImmediatelyWhenLeadTimeAlreadyPassed() {
        service.saveEvent(
                OWNER_ID,
                new CalendarService.CalendarEventInput(
                        null,
                        "workspace-1",
                        "临近日程",
                        LocalDate.of(2026, 10, 27),
                        LocalTime.of(9, 5),
                        null,
                        "",
                        "green",
                        true,
                        false,
                        30));

        assertEquals(
                NOW.plusSeconds(5),
                repository.reminders.get(0).scheduledAt());
    }

    @Test
    void requiresAtLeastOneDefaultReminderChannel() {
        assertThrows(
                DomainException.class,
                () -> service.saveReminderSettings(
                        OWNER_ID,
                        false,
                        false,
                        10));
    }

    private static final class InMemoryCalendarRepository
            implements CalendarRepository {

        private final List<CalendarEvent> events = new ArrayList<>();
        private final List<CalendarReminder> reminders = new ArrayList<>();

        @Override
        public List<CalendarEvent> findEvents(long ownerId) {
            return events.stream()
                    .filter((event) -> event.ownerId() == ownerId)
                    .toList();
        }

        @Override
        public Optional<CalendarEvent> findEvent(
                long ownerId,
                String eventKey) {
            return events.stream()
                    .filter((event) -> event.ownerId() == ownerId)
                    .filter((event) -> event.eventKey().equals(eventKey))
                    .findFirst();
        }

        @Override
        public CalendarEvent saveEventWithReminders(
                CalendarEvent event,
                List<CalendarReminder> nextReminders,
                Instant now) {
            CalendarEvent saved = new CalendarEvent(
                    1L,
                    event.ownerId(),
                    event.eventKey(),
                    event.workspaceId(),
                    event.title(),
                    event.eventDate(),
                    event.startTime(),
                    event.endTime(),
                    event.notes(),
                    event.color(),
                    event.reminderInApp(),
                    event.reminderEmail(),
                    event.reminderMinutesBefore(),
                    event.createdAt(),
                    event.updatedAt());
            events.clear();
            events.add(saved);
            reminders.clear();
            reminders.addAll(nextReminders);
            return saved;
        }

        @Override
        public void deleteEvent(long ownerId, String eventKey) {
            events.removeIf((event) ->
                    event.ownerId() == ownerId
                            && event.eventKey().equals(eventKey));
        }

        @Override
        public Optional<CalendarReminderSettings> findReminderSettings(
                long ownerId) {
            return Optional.empty();
        }

        @Override
        public CalendarReminderSettings saveReminderSettings(
                CalendarReminderSettings settings) {
            return settings;
        }

        @Override
        public List<CalendarReminder> findDueReminders(
                Instant now,
                int limit) {
            return List.of();
        }

        @Override
        public boolean claimReminder(long reminderId, Instant now) {
            return false;
        }

        @Override
        public void markReminderSent(long reminderId, Instant sentAt) {
        }

        @Override
        public void markReminderFailed(
                long reminderId,
                String error,
                Instant availableAt,
                boolean terminal,
                Instant now) {
        }

        @Override
        public void createInAppNotification(
                InAppNotification notification) {
        }

        @Override
        public List<InAppNotification> findNotifications(
                long ownerId,
                int limit) {
            return List.of();
        }

        @Override
        public int countUnreadNotifications(long ownerId) {
            return 0;
        }

        @Override
        public void markNotificationRead(
                long ownerId,
                long notificationId,
                Instant readAt) {
        }

        @Override
        public void markAllNotificationsRead(
                long ownerId,
                Instant readAt) {
        }
    }
}
