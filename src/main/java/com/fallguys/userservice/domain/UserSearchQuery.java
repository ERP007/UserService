package com.fallguys.userservice.domain;

public record UserSearchQuery(
        int page,
        int size,
        String keyword,
        UserRole role,
        UserTenancy tenancy,
        UserStatus status,
        UserSortBy sortBy,
        UserSortDirection sortDirection
) {
}
