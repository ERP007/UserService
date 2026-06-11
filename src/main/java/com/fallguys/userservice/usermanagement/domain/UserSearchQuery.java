package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;

public record UserSearchQuery(
        int page,
        int size,
        String keyword,
        UserRole role,
        String tenancyCode,
        UserStatus status,
        UserSortBy sortBy,
        UserSortDirection sortDirection
) {
}
