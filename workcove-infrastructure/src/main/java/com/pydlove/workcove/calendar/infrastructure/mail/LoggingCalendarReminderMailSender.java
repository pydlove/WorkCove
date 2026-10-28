package com.pydlove.workcove.calendar.infrastructure.mail;

import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.port.CalendarReminderMailSender;
import com.pydlove.workcove.identity.domain.model.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "workcove.mail.mode",
        havingValue = "log",
        matchIfMissing = true)
public class LoggingCalendarReminderMailSender
        implements CalendarReminderMailSender {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingCalendarReminderMailSender.class);

    @Override
    public void send(Email recipient, CalendarReminder reminder) {
        log.info(
                "8小时工作台 development calendar email: to={}, event={}, start={} {}",
                recipient.value(),
                reminder.eventTitle(),
                reminder.eventDate(),
                reminder.eventStartTime());
    }
}
