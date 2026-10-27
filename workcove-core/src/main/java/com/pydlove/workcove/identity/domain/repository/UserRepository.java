package com.pydlove.workcove.identity.domain.repository;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.User;
import com.pydlove.workcove.identity.domain.model.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    User save(User user);
}
