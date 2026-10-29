package com.pydlove.workcove.database.interfaces.rest;

import com.pydlove.workcove.database.application.DatabaseConnectionService;
import com.pydlove.workcove.database.application.DatabaseConnectionService.DatabaseConnectionCommand;
import com.pydlove.workcove.database.application.DatabaseConnectionService.DatabaseConnectionView;
import com.pydlove.workcove.database.application.DatabaseConnectionService.DatabaseColumnView;
import com.pydlove.workcove.database.application.DatabaseConnectionService.DatabaseSchemaView;
import com.pydlove.workcove.database.application.DatabaseConnectionService.DatabaseTableView;
import com.pydlove.workcove.database.domain.model.DatabaseType;
import com.pydlove.workcove.database.domain.port.DatabaseQueryGateway;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/development/databases")
public class DatabaseController {

    private static final String ACTION = "manage-databases";

    private final DatabaseConnectionService databaseService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public DatabaseController(
            DatabaseConnectionService databaseService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.databaseService = databaseService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping("/connections")
    public List<DatabaseConnectionView> connections(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return databaseService.list(
                currentUserId(request),
                workspaceId);
    }

    @PostMapping("/connections")
    public DatabaseConnectionView create(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody DatabaseConnectionRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return databaseService.save(
                currentUserId(request),
                body.workspaceId(),
                null,
                body.command());
    }

    @PutMapping("/connections/{id}")
    public DatabaseConnectionView update(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody DatabaseConnectionRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return databaseService.save(
                currentUserId(request),
                body.workspaceId(),
                id,
                body.command());
    }

    @DeleteMapping("/connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        validateAction(action);
        databaseService.delete(
                currentUserId(request),
                workspaceId,
                id);
    }

    @PostMapping("/connections/test")
    public DatabaseQueryGateway.ConnectionTest test(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody DatabaseConnectionRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return databaseService.test(
                currentUserId(request),
                body.workspaceId(),
                body.id(),
                body.command());
    }

    @PostMapping("/connections/{id}/query")
    public DatabaseQueryGateway.QueryResult query(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody QueryRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return databaseService.execute(
                currentUserId(request),
                body.workspaceId(),
                id,
                body.sql());
    }

    @GetMapping("/connections/{id}/tables")
    public List<DatabaseTableView> tables(
            @PathVariable long id,
            @RequestParam String workspaceId,
            @RequestParam(required = false) String database,
            HttpServletRequest request) {
        return databaseService.tables(
                currentUserId(request),
                workspaceId,
                id,
                database);
    }

    @GetMapping("/connections/{id}/databases")
    public List<DatabaseSchemaView> databases(
            @PathVariable long id,
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return databaseService.databases(
                currentUserId(request),
                workspaceId,
                id);
    }

    @GetMapping("/connections/{id}/columns")
    public List<DatabaseColumnView> columns(
            @PathVariable long id,
            @RequestParam String workspaceId,
            @RequestParam String table,
            @RequestParam(required = false) String database,
            HttpServletRequest request) {
        return databaseService.columns(
                currentUserId(request),
                workspaceId,
                id,
                database,
                table);
    }

    @GetMapping("/connections/{id}/table-data")
    public DatabaseQueryGateway.QueryResult tableData(
            @PathVariable long id,
            @RequestParam String workspaceId,
            @RequestParam String table,
            @RequestParam(required = false) String database,
            HttpServletRequest request) {
        return databaseService.tableData(
                currentUserId(request),
                workspaceId,
                id,
                database,
                table);
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的数据库操作");
        }
    }

    public record DatabaseConnectionRequest(
            Long id,
            String workspaceId,
            String name,
            String type,
            String host,
            int port,
            String databaseName,
            String username,
            String password,
            boolean sslEnabled) {

        DatabaseConnectionCommand command() {
            return new DatabaseConnectionCommand(
                    name,
                    parseType(type),
                    host,
                    port,
                    databaseName,
                    username,
                    password,
                    sslEnabled);
        }

        private static DatabaseType parseType(String value) {
            try {
                return DatabaseType.valueOf(
                        value == null ? "" : value.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new DomainException("不支持的数据库类型");
            }
        }
    }

    public record QueryRequest(String workspaceId, String sql) {
    }
}
