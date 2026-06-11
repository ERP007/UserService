package com.fallguys.userservice.usermanagement.domain;

import java.util.List;

public record UserListPage(
        List<UserListItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
