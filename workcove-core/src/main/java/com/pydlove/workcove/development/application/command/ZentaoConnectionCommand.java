package com.pydlove.workcove.development.application.command;

import java.util.List;

public record ZentaoConnectionCommand(
        String baseUrl,
        String username,
        String password,
        List<String> projectIds
) {
}
