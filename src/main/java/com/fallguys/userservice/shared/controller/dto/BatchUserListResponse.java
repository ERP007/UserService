package com.fallguys.userservice.shared.controller.dto;

import java.util.List;

import com.fallguys.userservice.shared.domain.query.BatchUserListResult;

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
