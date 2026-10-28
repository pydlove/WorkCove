package com.pydlove.workcove.system.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DataSource dataSource;

    public SystemController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean databaseUp = false;
        try (Connection connection = dataSource.getConnection()) {
            databaseUp = connection.isValid(2);
        } catch (Exception ignored) {
            // 健康检查不暴露连接细节，只返回可用状态。
        }
        String databaseStatus = databaseUp ? "UP" : "DOWN";
        return new HealthResponse(
                databaseUp ? "UP" : "DOWN",
                databaseStatus,
                Instant.now()
        );
    }

    public record HealthResponse(
            String status,
            String database,
            Instant checkedAt
    ) {
    }
}
