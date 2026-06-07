package com.fallguys.userservice.domain;

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
