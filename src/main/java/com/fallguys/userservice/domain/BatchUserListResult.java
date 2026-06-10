package com.fallguys.userservice.domain;

import java.util.List;

public record BatchUserListResult(
        List<BatchUser> users,
        List<String> notFoundEmployeeNumbers
) {
}
