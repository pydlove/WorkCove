package com.pydlove.workcove.boot.config;

import com.pydlove.workcove.calendar.application.CalendarReminderDispatcher;
import com.pydlove.workcove.calendar.application.CalendarService;
import com.pydlove.workcove.calendar.domain.port.CalendarReminderMailSender;
import com.pydlove.workcove.calendar.domain.repository.CalendarRepository;
import com.pydlove.workcove.identity.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CalendarConfiguration {

    @Bean
    public CalendarService calendarService(
            CalendarRepository repository,
            @Qualifier("clock") Clock clock) {
        return new CalendarService(repository, clock);
    }

    @Bean
    public CalendarReminderDispatcher calendarReminderDispatcher(
            CalendarRepository repository,
            UserRepository userRepository,
            CalendarReminderMailSender mailSender,
            @Qualifier("clock") Clock clock) {
        return new CalendarReminderDispatcher(
                repository,
                userRepository,
                mailSender,
                clock);
    }
}
