package com.pydlove.workcove.identity.application.query;

import com.pydlove.workcove.identity.domain.model.User;

public record AuthenticatedUser(
        long id,
        String email,
        String displayName) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId().value(),
                user.getEmail().value(),
                user.getDisplayName());
    }
}
