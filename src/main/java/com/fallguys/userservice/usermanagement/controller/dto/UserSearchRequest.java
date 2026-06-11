package com.fallguys.userservice.usermanagement.controller.dto;

import java.util.Locale;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import com.fallguys.userservice.usermanagement.domain.UserSearchQuery;
import com.fallguys.userservice.usermanagement.domain.UserSortBy;
import com.fallguys.userservice.usermanagement.domain.UserSortDirection;
import org.springframework.util.StringUtils;

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
            throw new UserException(UserErrorCode.USER_INVALID_PAGE);
        }

        return page;
    }

    private static int validateSize(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new UserException(UserErrorCode.USER_INVALID_SIZE);
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
            throw new UserException(UserErrorCode.USER_REQUIRED_PARAMETER);
        }

        try {
            return Enum.valueOf(enumType, normalizeEnumValue(value));
        } catch (IllegalArgumentException ex) {
            throw new UserException(UserErrorCode.USER_UNSUPPORTED_PARAMETER);
        }
    }

    private static String normalizeEnumValue(String value) {
        return value.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}
