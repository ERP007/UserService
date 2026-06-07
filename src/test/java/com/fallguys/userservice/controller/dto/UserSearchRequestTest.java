package com.fallguys.userservice.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.domain.UserRole;
import com.fallguys.userservice.domain.UserSearchQuery;
import com.fallguys.userservice.domain.UserSortBy;
import com.fallguys.userservice.domain.UserSortDirection;
import com.fallguys.userservice.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UserSearchRequestTest {

    @Test
    void convertsQueryParametersToUserSearchQuery() {
        UserSearchRequest request = new UserSearchRequest(
                2,
                20,
                " HMC0001 ",
                "BRANCH_MANAGER",
                " wh-br-001 ",
                "ACTIVE",
                "joinedAt",
                "DESC"
        );

        UserSearchQuery query = request.toQuery();

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.keyword()).isEqualTo("HMC0001");
        assertThat(query.role()).isEqualTo(UserRole.BRANCH_MANAGER);
        assertThat(query.tenancyCode()).isEqualTo("WH-BR-001");
        assertThat(query.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(query.sortBy()).isEqualTo(UserSortBy.JOINED_AT);
        assertThat(query.sortDirection()).isEqualTo(UserSortDirection.DESC);
    }

    @Test
    void convertsAllFiltersToNull() {
        UserSearchRequest request = new UserSearchRequest(
                1,
                10,
                null,
                "ALL",
                "ALL",
                "ALL",
                "employeeNo",
                "ASC"
        );

        UserSearchQuery query = request.toQuery();

        assertThat(query.role()).isNull();
        assertThat(query.tenancyCode()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.sortBy()).isEqualTo(UserSortBy.EMPLOYEE_NO);
        assertThat(query.sortDirection()).isEqualTo(UserSortDirection.ASC);
    }

    @Test
    void rejectsUnsupportedFilterValue() {
        UserSearchRequest request = new UserSearchRequest(
                1,
                10,
                null,
                "UNKNOWN",
                "ALL",
                "ALL",
                "employeeNo",
                "ASC"
        );

        assertThatThrownBy(request::toQuery)
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnsupportedSortBy() {
        UserSearchRequest request = new UserSearchRequest(
                1,
                10,
                null,
                "ALL",
                "ALL",
                "ALL",
                "email",
                "ASC"
        );

        assertThatThrownBy(request::toQuery)
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
