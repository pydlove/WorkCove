package com.pydlove.workcove.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    UserData findById(@Param("id") long id);

    UserData findByEmail(@Param("email") String email);

    int countByEmail(@Param("email") String email);

    int insert(UserData data);

    int update(UserData data);
}
