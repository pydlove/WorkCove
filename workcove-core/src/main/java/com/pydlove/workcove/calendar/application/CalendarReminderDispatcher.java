package com.pydlove.workcove.calendar.application;

import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.model.InAppNotification;
import com.pydlove.workcove.calendar.domain.port.CalendarReminderMailSender;
import com.pydlove.workcove.calendar.domain.repository.CalendarRepository;
import com.pydlove.workcove.identity.domain.model.User;
import com.pydlove.workcove.identity.domain.model.UserId;
import com.pydlove.workcove.identity.domain.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class CalendarReminderDispatcher {

    private static final int MAX_ATTEMPTS = 3;
    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M月d日");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarRepository repository;
    private final UserRepository userRepository;
    private final CalendarReminderMailSender mailSender;
    private final Clock clock;

    public CalendarReminderDispatcher(
            CalendarRepository repository,
            UserRepository userRepository,
            CalendarReminderMailSender mailSender,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.mailSender = Objects.requireNonNull(mailSender);
        this.clock = Objects.requireNonNull(clock);
    }

    public int dispatchDueReminders() {
        return dispatchDueReminders(DEFAULT_BATCH_SIZE);
    }

    int dispatchDueReminders(int batchSize) {
        Instant now = clock.instant();
        List<CalendarReminder> due =
                repository.findDueReminders(now, Math.max(1, batchSize));
        int delivered = 0;
        for (CalendarReminder reminder : due) {
            if (!repository.claimReminder(reminder.id(), now)) continue;
            try {
                deliver(reminder, now);
                repository.markReminderSent(reminder.id(), clock.instant());
                delivered += 1;
            } catch (RuntimeException exception) {
                int attempts = reminder.attempts() + 1;
                boolean terminal = attempts >= MAX_ATTEMPTS;
                Duration retryDelay = Duration.ofMinutes(attempts);
                repository.markReminderFailed(
                        reminder.id(),
                        safeMessage(exception),
                        clock.instant().plus(retryDelay),
                        terminal,
                        clock.instant());
            }
        }
        return delivered;
    }

    private void deliver(CalendarReminder reminder, Instant now) {
        String body = reminder.eventDate().format(DATE_FORMAT)
                + " " + reminder.eventStartTime().format(TIME_FORMAT)
                + " · " + reminder.eventTitle();
        if (reminder.channel()
                == com.pydlove.workcove.calendar.domain.model.CalendarReminderChannel.IN_APP) {
            repository.createInAppNotification(new InAppNotification(
                    null,
                    reminder.ownerId(),
                    reminder.workspaceId(),
                    reminder.eventId(),
                    "日程提醒 · " + reminder.eventTitle(),
                    body,
                    "calendar",
                    "calendar:" + reminder.eventKey() + ":"
                            + reminder.channel() + ":"
                            + reminder.scheduledAt(),
                    null,
                    now));
            return;
        }

        User user = userRepository.findById(UserId.of(reminder.ownerId()))
                .orElseThrow(() -> new IllegalStateException(
                        "日程所属用户不存在"));
        mailSender.send(user.getEmail(), reminder);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 1000
                ? message.substring(0, 1000)
                : message;
    }
}
