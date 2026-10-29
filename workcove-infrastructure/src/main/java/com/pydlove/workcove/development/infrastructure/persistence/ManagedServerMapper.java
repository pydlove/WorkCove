package com.pydlove.workcove.development.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ManagedServerMapper {

    List<ManagedServerData> findByOwnerIdAndWorkspaceId(
            @Param("ownerId") long ownerId,
            @Param("workspaceId") String workspaceId);

    ManagedServerData findByIdAndOwnerIdAndWorkspaceId(
            @Param("id") long id,
            @Param("ownerId") long ownerId,
            @Param("workspaceId") String workspaceId);

    int insert(ManagedServerData data);

    int update(ManagedServerData data);

    int deleteByIdAndOwnerIdAndWorkspaceId(
            @Param("id") long id,
            @Param("ownerId") long ownerId,
            @Param("workspaceId") String workspaceId);
}
