package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.query.BatchUser;
import java.util.List;

public interface InternalUserRepository {

    List<BatchUser> findBatchUsersByEmployeeNumbers(List<String> employeeNumbers);
}
