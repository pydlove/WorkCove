package com.pydlove.workcove.development.domain.repository;

import com.pydlove.workcove.development.domain.model.ManagedServer;

import java.util.List;
import java.util.Optional;

public interface ManagedServerRepository {

    List<ManagedServer> findByOwnerId(long ownerId);

    Optional<ManagedServer> findByIdAndOwnerId(long id, long ownerId);

    ManagedServer save(ManagedServer server);

    void deleteByIdAndOwnerId(long id, long ownerId);
}
