package com.fallguys.userservice.controller.dto;

import java.util.List;

import com.fallguys.userservice.domain.UserListPage;

public record UserListResponse(
        List<UserListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {

    public static UserListResponse from(UserListPage page) {
        return new UserListResponse(
                page.content().stream()
                        .map(UserListItemResponse::from)
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext()
        );
    }
}
