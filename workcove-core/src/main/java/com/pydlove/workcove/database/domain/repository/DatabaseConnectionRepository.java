package com.pydlove.workcove.database.domain.repository;

import com.pydlove.workcove.database.domain.model.DatabaseConnection;

import java.util.List;
import java.util.Optional;

public interface DatabaseConnectionRepository {

    List<DatabaseConnection> findByOwnerAndWorkspace(
            long ownerId,
            String workspaceId);

    Optional<DatabaseConnection> findByIdAndOwnerAndWorkspace(
            long id,
            long ownerId,
            String workspaceId);

    DatabaseConnection save(DatabaseConnection connection);

    void deleteByIdAndOwnerAndWorkspace(
            long id,
            long ownerId,
            String workspaceId);
}
