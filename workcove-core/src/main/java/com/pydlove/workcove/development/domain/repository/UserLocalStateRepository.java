package com.pydlove.workcove.development.domain.repository;

import com.pydlove.workcove.development.domain.model.UserLocalState;

import java.util.List;

public interface UserLocalStateRepository {

    List<UserLocalState> findByOwnerId(long ownerId);

    UserLocalState save(UserLocalState state);
}
