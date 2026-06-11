package com.fallguys.userservice.usermanagement.controller.dto;

import java.util.Locale;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.usermanagement.domain.UserSearchQuery;
import com.fallguys.userservice.usermanagement.domain.UserSortBy;
import com.fallguys.userservice.usermanagement.domain.UserSortDirection;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

public record UserSearchRequest(
        int page,
        int size,
        String keyword,
        String role,
        String tenancyCode,
        String status,
        String sortBy,
        String sortDirection
) {

    private static final int MIN_PAGE = 1;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    public UserSearchQuery toQuery() {
        int normalizedPage = validatePage(page);
        int normalizedSize = validateSize(size);

        return new UserSearchQuery(
                normalizedPage,
                normalizedSize,
                normalizeKeyword(keyword),
                enumFilter(role, UserRole.class, "role"),
                codeFilter(tenancyCode),
                enumFilter(status, UserStatus.class, "status"),
                enumValue(sortBy, UserSortBy.class, "sortBy"),
                enumValue(sortDirection, UserSortDirection.class, "sortDirection")
        );
    }

    private static int validatePage(int page) {
        if (page < MIN_PAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 1");
        }

        return page;
    }

    private static int validateSize(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }

        return size;
    }

    private static String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        return keyword.trim();
    }

    private static String codeFilter(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T extends Enum<T>> T enumFilter(String value, Class<T> enumType, String parameterName) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }

        return enumValue(value, enumType, parameterName);
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> enumType, String parameterName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, parameterName + " is required");
        }

        try {
            return Enum.valueOf(enumType, normalizeEnumValue(value));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, parameterName + " is unsupported");
        }
    }

    private static String normalizeEnumValue(String value) {
        return value.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}
