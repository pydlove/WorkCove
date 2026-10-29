package com.pydlove.workcove.development.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.domain.model.ZentaoBug;
import com.pydlove.workcove.development.domain.model.ZentaoBuild;
import com.pydlove.workcove.development.domain.model.ZentaoConnection;
import com.pydlove.workcove.development.domain.repository.ZentaoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcZentaoRepository implements ZentaoRepository {

    private static final RowMapper<ZentaoBug> BUG_MAPPER =
            JdbcZentaoRepository::mapBug;
    private static final RowMapper<ZentaoBuild> BUILD_MAPPER =
            JdbcZentaoRepository::mapBuild;
    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcZentaoRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ZentaoConnection> findConnection(
            long ownerId,
            String workspaceId) {
        List<ZentaoConnection> connections = jdbc.query(
                """
                SELECT id, owner_id, workspace_id, base_url, username,
                       project_ids, password_ciphertext, last_synced_at,
                       created_at, updated_at
                FROM zentao_connection
                WHERE owner_id = ? AND workspace_id = ?
                """,
                this::mapConnection,
                ownerId,
                workspaceId);
        return connections.stream().findFirst();
    }

    @Override
    public ZentaoConnection saveConnection(ZentaoConnection connection) {
        jdbc.update(
                """
                INSERT INTO zentao_connection (
                    owner_id, workspace_id, base_url, username,
                    project_ids, password_ciphertext, last_synced_at,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    base_url = VALUES(base_url),
                    username = VALUES(username),
                    project_ids = VALUES(project_ids),
                    password_ciphertext = VALUES(password_ciphertext),
                    last_synced_at = VALUES(last_synced_at),
                    updated_at = VALUES(updated_at)
                """,
                connection.ownerId(),
                connection.workspaceId(),
                connection.baseUrl(),
                connection.username(),
                projectIdsJson(connection.projectIds()),
                connection.passwordCiphertext(),
                timestamp(connection.lastSyncedAt()),
                timestamp(connection.createdAt()),
                timestamp(connection.updatedAt()));
        return findConnection(connection.ownerId(), connection.workspaceId())
                .orElseThrow();
    }

    @Override
    public List<ZentaoBug> findBugs(long ownerId, String workspaceId) {
        return jdbc.query(
                """
                SELECT *
                FROM zentao_bug
                WHERE owner_id = ? AND workspace_id = ?
                  AND local_deleted = FALSE
                ORDER BY active DESC, updated_at DESC, id DESC
                """,
                BUG_MAPPER,
                ownerId,
                workspaceId);
    }

    @Override
    public Optional<ZentaoBug> findBug(
            long ownerId,
            String workspaceId,
            long id) {
        return jdbc.query(
                """
                SELECT *
                FROM zentao_bug
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                BUG_MAPPER,
                id,
                ownerId,
                workspaceId).stream().findFirst();
    }

    @Override
    public Optional<ZentaoBug> findBugByRemote(
            long ownerId,
            String workspaceId,
            String productId,
            String remoteId) {
        return jdbc.query(
                """
                SELECT *
                FROM zentao_bug
                WHERE owner_id = ?
                  AND workspace_id = ?
                  AND product_id = ?
                  AND remote_id = ?
                """,
                BUG_MAPPER,
                ownerId,
                workspaceId,
                productId,
                remoteId).stream().findFirst();
    }

    @Override
    public ZentaoBug saveBug(ZentaoBug bug) {
        if (bug.id() == null) {
            jdbc.update(
                    """
                    INSERT INTO zentao_bug (
                        owner_id, workspace_id, remote_id, project_id,
                        project_name, product_id, product_name, title,
                        severity, priority, status, confirmed, steps, opened_by,
                        opened_date, assigned_to, active, local_deleted,
                        local_resolved, local_status, resolution,
                        solution_text, reason_text, pushed_at,
                        last_synced_at, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    bug.ownerId(),
                    bug.workspaceId(),
                    bug.remoteId(),
                    bug.projectId(),
                    bug.projectName(),
                    bug.productId(),
                    bug.productName(),
                    bug.title(),
                    bug.severity(),
                    bug.priority(),
                    bug.status(),
                    bug.confirmed(),
                    bug.steps(),
                    bug.openedBy(),
                    bug.openedDate(),
                    bug.assignedTo(),
                    bug.active(),
                    bug.localDeleted(),
                    bug.localResolved(),
                    bug.localStatus(),
                    bug.resolution(),
                    bug.solutionText(),
                    bug.reasonText(),
                    timestamp(bug.pushedAt()),
                    timestamp(bug.lastSyncedAt()),
                    timestamp(bug.createdAt()),
                    timestamp(bug.updatedAt()));
        } else {
            jdbc.update(
                    """
                    UPDATE zentao_bug
                    SET project_id = ?,
                        project_name = ?,
                        product_name = ?,
                        title = ?,
                        severity = ?,
                        priority = ?,
                        status = ?,
                        confirmed = ?,
                        steps = ?,
                        opened_by = ?,
                        opened_date = ?,
                        assigned_to = ?,
                        active = ?,
                        local_deleted = ?,
                        local_resolved = ?,
                        local_status = ?,
                        resolution = ?,
                        solution_text = ?,
                        reason_text = ?,
                        pushed_at = ?,
                        last_synced_at = ?,
                        updated_at = ?
                    WHERE id = ? AND owner_id = ? AND workspace_id = ?
                    """,
                    bug.projectId(),
                    bug.projectName(),
                    bug.productName(),
                    bug.title(),
                    bug.severity(),
                    bug.priority(),
                    bug.status(),
                    bug.confirmed(),
                    bug.steps(),
                    bug.openedBy(),
                    bug.openedDate(),
                    bug.assignedTo(),
                    bug.active(),
                    bug.localDeleted(),
                    bug.localResolved(),
                    bug.localStatus(),
                    bug.resolution(),
                    bug.solutionText(),
                    bug.reasonText(),
                    timestamp(bug.pushedAt()),
                    timestamp(bug.lastSyncedAt()),
                    timestamp(bug.updatedAt()),
                    bug.id(),
                    bug.ownerId(),
                    bug.workspaceId());
        }
        if (bug.id() != null) return bug;
        return findBugByRemote(
                bug.ownerId(),
                bug.workspaceId(),
                bug.productId(),
                bug.remoteId()).orElseThrow();
    }

    @Override
    public void markBugsInactive(long ownerId, String workspaceId) {
        jdbc.update(
                """
                UPDATE zentao_bug
                SET active = FALSE, updated_at = CURRENT_TIMESTAMP(3)
                WHERE owner_id = ? AND workspace_id = ? AND active = TRUE
                """,
                ownerId,
                workspaceId);
    }

    @Override
    public void deleteBugs(long ownerId, String workspaceId) {
        jdbc.update(
                """
                DELETE FROM zentao_bug
                WHERE owner_id = ? AND workspace_id = ?
                """,
                ownerId,
                workspaceId);
    }

    @Override
    public void deleteBugLocally(
            long ownerId,
            String workspaceId,
            long bugId) {
        jdbc.update(
                """
                UPDATE zentao_bug
                SET local_deleted = TRUE,
                    active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                bugId,
                ownerId,
                workspaceId);
    }

    @Override
    public void markBugResolvedLocally(
            long ownerId,
            String workspaceId,
            long bugId,
            String resolution,
            String solution,
            String reason) {
        jdbc.update(
                """
                UPDATE zentao_bug
                SET local_resolved = TRUE,
                    local_status = 'resolved',
                    resolution = ?,
                    solution_text = ?,
                    reason_text = ?,
                    active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                  AND local_deleted = FALSE
                """,
                resolution,
                solution,
                reason,
                bugId,
                ownerId,
                workspaceId);
    }

    @Override
    public List<ZentaoBuild> findBuilds(
            long ownerId,
            String workspaceId) {
        return jdbc.query(
                """
                SELECT *
                FROM zentao_build
                WHERE owner_id = ? AND workspace_id = ?
                ORDER BY build_date DESC, updated_at DESC, id DESC
                """,
                BUILD_MAPPER,
                ownerId,
                workspaceId);
    }

    @Override
    public Optional<ZentaoBuild> findBuildByRemote(
            long ownerId,
            String workspaceId,
            String projectId,
            String remoteId) {
        return jdbc.query(
                """
                SELECT *
                FROM zentao_build
                WHERE owner_id = ?
                  AND workspace_id = ?
                  AND project_id = ?
                  AND remote_id = ?
                """,
                BUILD_MAPPER,
                ownerId,
                workspaceId,
                projectId,
                remoteId).stream().findFirst();
    }

    @Override
    public ZentaoBuild saveBuild(ZentaoBuild build) {
        if (build.id() == null) {
            jdbc.update(
                    """
                    INSERT INTO zentao_build (
                        owner_id, workspace_id, remote_id, project_id,
                        project_name, product_id, product_name, execution_id,
                        name, status, builder, build_date, description,
                        scm_revision, last_synced_at, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    build.ownerId(),
                    build.workspaceId(),
                    build.remoteId(),
                    build.projectId(),
                    build.projectName(),
                    build.productId(),
                    build.productName(),
                    build.executionId(),
                    build.name(),
                    build.status(),
                    build.builder(),
                    build.buildDate(),
                    build.description(),
                    build.scmRevision(),
                    timestamp(build.lastSyncedAt()),
                    timestamp(build.createdAt()),
                    timestamp(build.updatedAt()));
        } else {
            jdbc.update(
                    """
                    UPDATE zentao_build
                    SET project_name = ?,
                        product_id = ?,
                        product_name = ?,
                        execution_id = ?,
                        name = ?,
                        status = ?,
                        builder = ?,
                        build_date = ?,
                        description = ?,
                        scm_revision = ?,
                        last_synced_at = ?,
                        updated_at = ?
                    WHERE id = ? AND owner_id = ? AND workspace_id = ?
                    """,
                    build.projectName(),
                    build.productId(),
                    build.productName(),
                    build.executionId(),
                    build.name(),
                    build.status(),
                    build.builder(),
                    build.buildDate(),
                    build.description(),
                    build.scmRevision(),
                    timestamp(build.lastSyncedAt()),
                    timestamp(build.updatedAt()),
                    build.id(),
                    build.ownerId(),
                    build.workspaceId());
        }
        if (build.id() != null) return build;
        return findBuildByRemote(
                build.ownerId(),
                build.workspaceId(),
                build.projectId(),
                build.remoteId()).orElseThrow();
    }

    @Override
    public void deleteBuilds(long ownerId, String workspaceId) {
        jdbc.update(
                """
                DELETE FROM zentao_build
                WHERE owner_id = ? AND workspace_id = ?
                """,
                ownerId,
                workspaceId);
    }

    private ZentaoConnection mapConnection(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new ZentaoConnection(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getString("base_url"),
                result.getString("username"),
                projectIds(result.getString("project_ids")),
                result.getString("password_ciphertext"),
                instant(result, "last_synced_at"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static ZentaoBug mapBug(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new ZentaoBug(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getString("remote_id"),
                result.getString("project_id"),
                result.getString("project_name"),
                result.getString("product_id"),
                result.getString("product_name"),
                result.getString("title"),
                result.getString("severity"),
                result.getString("priority"),
                result.getString("status"),
                result.getBoolean("confirmed"),
                result.getString("steps"),
                result.getString("opened_by"),
                result.getString("opened_date"),
                result.getString("assigned_to"),
                result.getBoolean("active"),
                result.getBoolean("local_deleted"),
                result.getBoolean("local_resolved"),
                result.getString("local_status"),
                result.getString("resolution"),
                result.getString("solution_text"),
                result.getString("reason_text"),
                instant(result, "pushed_at"),
                instant(result, "last_synced_at"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static ZentaoBuild mapBuild(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new ZentaoBuild(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getString("remote_id"),
                result.getString("project_id"),
                result.getString("project_name"),
                result.getString("product_id"),
                result.getString("product_name"),
                result.getString("execution_id"),
                result.getString("name"),
                result.getString("status"),
                result.getString("builder"),
                result.getString("build_date"),
                result.getString("description"),
                result.getString("scm_revision"),
                instant(result, "last_synced_at"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private List<String> projectIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return List.copyOf(
                    objectMapper.readValue(value, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "禅道项目配置无法解析",
                    exception);
        }
    }

    private String projectIdsJson(List<String> projectIds) {
        try {
            return objectMapper.writeValueAsString(
                    projectIds == null ? List.of() : projectIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "禅道项目配置无法保存",
                    exception);
        }
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
