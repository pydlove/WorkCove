package com.pydlove.workcove.development.application.command;

import java.util.List;

public record ZentaoTestTaskCreateCommand(
        List<String> types,
        String owner,
        List<String> members,
        String status,
        String begin,
        String end,
        String testReport,
        String name
) {
}
