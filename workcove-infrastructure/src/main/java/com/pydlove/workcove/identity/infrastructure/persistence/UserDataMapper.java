package com.pydlove.workcove.identity.infrastructure.persistence;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.PasswordHash;
import com.pydlove.workcove.identity.domain.model.User;
import com.pydlove.workcove.identity.domain.model.UserId;

final class UserDataMapper {

    private UserDataMapper() {
    }

    static User toDomain(UserData data) {
        return User.restore(
                UserId.of(data.getId()),
                Email.of(data.getEmail()),
                data.getDisplayName(),
                new PasswordHash(data.getPasswordHash()),
                data.getCreatedAt());
    }

    static UserData toData(User user) {
        UserData data = new UserData();
        data.setId(user.getId() == null ? null : user.getId().value());
        data.setEmail(user.getEmail().value());
        data.setDisplayName(user.getDisplayName());
        data.setPasswordHash(user.getPasswordHash().value());
        data.setCreatedAt(user.getCreatedAt());
        data.setUpdatedAt(user.getCreatedAt());
        return data;
    }
}
