package com.pydlove.workcove.identity.interfaces.dto;

import com.pydlove.workcove.identity.application.query.AuthenticatedUser;

public record AuthUserResponse(
        long id,
        String email,
        String displayName) {

    public static AuthUserResponse from(AuthenticatedUser user) {
        return new AuthUserResponse(user.id(), user.email(), user.displayName());
    }
}
