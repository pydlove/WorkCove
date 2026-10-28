package com.pydlove.workcove.development.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ManagedServerMapper {

    List<ManagedServerData> findByOwnerId(@Param("ownerId") long ownerId);

    ManagedServerData findByIdAndOwnerId(
            @Param("id") long id,
            @Param("ownerId") long ownerId);

    int insert(ManagedServerData data);

    int update(ManagedServerData data);

    int deleteByIdAndOwnerId(
            @Param("id") long id,
            @Param("ownerId") long ownerId);
}
