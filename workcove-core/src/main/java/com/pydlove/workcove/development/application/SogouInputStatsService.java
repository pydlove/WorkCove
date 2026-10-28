package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.port.SogouInputStatsGateway;

import java.util.Objects;

public class SogouInputStatsService {

    private final SogouInputStatsGateway gateway;

    public SogouInputStatsService(SogouInputStatsGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "搜狗输入法统计网关");
    }

    public SogouInputStatsGateway.Stats read() {
        return gateway.read();
    }
}
