package com.pydlove.workcove.development.domain.repository;

import com.pydlove.workcove.development.domain.model.ManagedServer;

import java.util.List;
import java.util.Optional;

public interface ManagedServerRepository {

    List<ManagedServer> findByOwnerIdAndWorkspaceId(
            long ownerId,
            String workspaceId);

    Optional<ManagedServer> findByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId);

    ManagedServer save(ManagedServer server);

    void deleteByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId);
}
