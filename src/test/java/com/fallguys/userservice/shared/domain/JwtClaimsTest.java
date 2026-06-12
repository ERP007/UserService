package com.fallguys.userservice.shared.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

class JwtClaimsTest {

    @Test
    void rejectsNullJwtWhenAdminRequired() {
        assertInvalidTokenClaim(() -> JwtClaims.requireAdmin(null));
    }

    @Test
    void rejectsNullJwtWhenRoleRequested() {
        assertInvalidTokenClaim(() -> JwtClaims.role(null));
    }

    @Test
    void rejectsNullJwtWhenTenancyRequested() {
        assertInvalidTokenClaim(() -> JwtClaims.tenancy(null));
    }

    private void assertInvalidTokenClaim(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INVALID_TOKEN_CLAIM);
    }
}
