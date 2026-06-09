package com.fallguys.userservice.controller.dto;

import java.util.List;

import com.fallguys.userservice.domain.BatchUserListResult;

public record BatchUserListResponse(
        List<BatchUserItemResponse> users,
        List<String> notFoundEmployeeNumbers
) {

    public static BatchUserListResponse from(BatchUserListResult result) {
        return new BatchUserListResponse(
                result.users().stream()
                        .map(BatchUserItemResponse::from)
                        .toList(),
                result.notFoundEmployeeNumbers()
        );
    }
}
