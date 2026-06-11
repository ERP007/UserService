package com.fallguys.userservice.shared.domain.query;

import java.util.List;

public record BatchUserListResult(
        List<BatchUser> users,
        List<String> notFoundEmployeeNumbers
) {
}
