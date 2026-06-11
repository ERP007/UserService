package com.fallguys.userservice.shared.controller.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchUserListRequest(
        @JsonProperty("employeeNumbers")
        @JsonAlias("employee_numbers")
        List<String> employeeNumbers
) {
}
