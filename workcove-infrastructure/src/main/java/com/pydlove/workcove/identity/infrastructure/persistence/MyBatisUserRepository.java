package com.pydlove.workcove.identity.infrastructure.persistence;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.User;
import com.pydlove.workcove.identity.domain.model.UserId;
import com.pydlove.workcove.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper mapper;

    public MyBatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(mapper.findById(id.value()))
                .map(UserDataMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return Optional.ofNullable(mapper.findByEmail(email.value()))
                .map(UserDataMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return mapper.countByEmail(email.value()) > 0;
    }

    @Override
    public User save(User user) {
        UserData data = UserDataMapper.toData(user);
        if (data.getId() == null) {
            mapper.insert(data);
        } else {
            mapper.update(data);
        }
        return UserDataMapper.toDomain(data);
    }
}
