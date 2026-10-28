package com.pydlove.workcove.development.infrastructure.persistence;

import com.pydlove.workcove.development.domain.model.UserLocalState;
import com.pydlove.workcove.development.domain.repository.UserLocalStateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcUserLocalStateRepository
        implements UserLocalStateRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserLocalStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserLocalState> findByOwnerId(long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, state_key, payload, updated_at
                FROM user_local_state
                WHERE owner_id = ?
                ORDER BY state_key
                """,
                (result, rowNumber) -> new UserLocalState(
                        result.getLong("id"),
                        result.getLong("owner_id"),
                        result.getString("state_key"),
                        result.getString("payload"),
                        result.getTimestamp("updated_at").toInstant()),
                ownerId);
    }

    @Override
    public UserLocalState save(UserLocalState state) {
        jdbc.update(
                """
                INSERT INTO user_local_state (
                    owner_id, state_key, payload, updated_at
                )
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    payload = VALUES(payload),
                    updated_at = VALUES(updated_at)
                """,
                state.ownerId(),
                state.stateKey(),
                state.payload(),
                Timestamp.from(state.updatedAt()));
        return jdbc.queryForObject(
                """
                SELECT id, owner_id, state_key, payload, updated_at
                FROM user_local_state
                WHERE owner_id = ? AND state_key = ?
                """,
                (result, rowNumber) -> new UserLocalState(
                        result.getLong("id"),
                        result.getLong("owner_id"),
                        result.getString("state_key"),
                        result.getString("payload"),
                        result.getTimestamp("updated_at").toInstant()),
                state.ownerId(),
                state.stateKey());
    }
}
