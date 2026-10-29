package com.pydlove.workcove.development.domain.repository;

import com.pydlove.workcove.development.domain.model.ZentaoBug;
import com.pydlove.workcove.development.domain.model.ZentaoBuild;
import com.pydlove.workcove.development.domain.model.ZentaoConnection;

import java.util.List;
import java.util.Optional;

public interface ZentaoRepository {

    Optional<ZentaoConnection> findConnection(long ownerId, String workspaceId);

    ZentaoConnection saveConnection(ZentaoConnection connection);

    List<ZentaoBug> findBugs(long ownerId, String workspaceId);

    Optional<ZentaoBug> findBug(
            long ownerId,
            String workspaceId,
            long id);

    Optional<ZentaoBug> findBugByRemote(
            long ownerId,
            String workspaceId,
            String productId,
            String remoteId);

    ZentaoBug saveBug(ZentaoBug bug);

    void markBugsInactive(long ownerId, String workspaceId);

    void deleteBugs(long ownerId, String workspaceId);

    void deleteBugLocally(
            long ownerId,
            String workspaceId,
            long bugId);

    void markBugResolvedLocally(
            long ownerId,
            String workspaceId,
            long bugId,
            String resolution,
            String solution,
            String reason);

    List<ZentaoBuild> findBuilds(long ownerId, String workspaceId);

    Optional<ZentaoBuild> findBuildByRemote(
            long ownerId,
            String workspaceId,
            String projectId,
            String remoteId);

    ZentaoBuild saveBuild(ZentaoBuild build);

    void deleteBuilds(long ownerId, String workspaceId);
}
