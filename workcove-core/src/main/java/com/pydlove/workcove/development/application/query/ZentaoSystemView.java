package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.port.ZentaoGateway;

public record ZentaoSystemView(
        String id,
        String productId,
        String name
) {

    public static ZentaoSystemView from(
            ZentaoGateway.RemoteSystem system) {
        return new ZentaoSystemView(
                system.id(),
                system.productId(),
                system.name());
    }
}
