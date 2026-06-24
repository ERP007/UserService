package com.fallguys.userservice.shared.controller.dto;

import com.fallguys.userservice.shared.domain.model.User;

public record SessionResponse(
        SessionContentResponse content
) {

    public static SessionResponse from(User user) {
        return new SessionResponse(new SessionContentResponse(
                user.getTenancyCode(),
                user.getTenancyName(),
                user.getTenancy() == null ? user.getTenancyCode() : user.getTenancy().name(),
                user.getRole().name(),
                user.getPosition(),
                user.getEmployeeNumber(),
                user.getDisplayName()
        ));
    }
}
