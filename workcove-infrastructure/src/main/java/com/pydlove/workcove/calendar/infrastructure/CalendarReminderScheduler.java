package com.pydlove.workcove.calendar.infrastructure;

import com.pydlove.workcove.calendar.application.CalendarReminderDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "workcove.calendar.reminders.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CalendarReminderScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(CalendarReminderScheduler.class);

    private final CalendarReminderDispatcher dispatcher;

    public CalendarReminderScheduler(CalendarReminderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(
            fixedDelayString = "${workcove.calendar.reminder-poll-interval-ms:30000}",
            initialDelayString = "${workcove.calendar.reminder-initial-delay-ms:15000}")
    public void dispatch() {
        try {
            int delivered = dispatcher.dispatchDueReminders();
            if (delivered > 0) {
                log.info("Delivered {} calendar reminder(s)", delivered);
            }
        } catch (RuntimeException exception) {
            log.error("Calendar reminder dispatch failed", exception);
        }
    }
}
