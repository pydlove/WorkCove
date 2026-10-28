package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.port.ZentaoGateway;

public record ZentaoProjectView(
        String id,
        String name,
        String status
) {
    public static ZentaoProjectView from(ZentaoGateway.Project project) {
        return new ZentaoProjectView(
                project.id(),
                project.name(),
                project.status());
    }
}
