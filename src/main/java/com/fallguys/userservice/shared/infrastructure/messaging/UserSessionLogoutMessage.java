package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.usermanagement.domain.UserSessionLogoutEvent;
import java.time.Instant;
import java.util.UUID;

public record UserSessionLogoutMessage(
        String eventId,
        String eventType,
        int eventVersion,
        String producer,
        String occurredAt,
        String correlationId,
        Payload payload
) {

    public static final String EVENT_TYPE = "keycloak.user.sessions.logout";
    private static final int EVENT_VERSION = 1;
    private static final String PRODUCER = "user-service";
    private static final String REASON = "SESSION_SCOPED_PROFILE_UPDATED";

    public static UserSessionLogoutMessage from(UserSessionLogoutEvent event) {
        return new UserSessionLogoutMessage(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                EVENT_VERSION,
                PRODUCER,
                Instant.now().toString(),
                "USER-" + event.keycloakSub(),
                new Payload(
                        event.keycloakSub(),
                        REASON
                )
        );
    }

    public record Payload(
            String keycloakSub,
            String reason
    ) {
    }
}
