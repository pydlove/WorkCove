package com.pydlove.workcove.development.domain.repository;

import com.pydlove.workcove.development.domain.model.TestEnvironment;

import java.util.List;
import java.util.Optional;

public interface TestEnvironmentRepository {

    List<TestEnvironment> findByOwnerAndWorkspace(
            long ownerId,
            String workspaceId);

    Optional<TestEnvironment> findByIdAndOwnerAndWorkspace(
            long id,
            long ownerId,
            String workspaceId);

    TestEnvironment save(TestEnvironment environment);

    void deleteByIdAndOwnerAndWorkspace(
            long id,
            long ownerId,
            String workspaceId);
}
