package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.usermanagement.domain.UserAuthorityChangedEvent;
import java.time.Instant;
import java.util.UUID;

public record UserAuthorityChangedMessage(
        String eventId,
        String eventType,
        int eventVersion,
        String producer,
        String occurredAt,
        String correlationId,
        Payload payload
) {

    private static final String EVENT_TYPE = "user.authority.changed";
    private static final int EVENT_VERSION = 1;
    private static final String PRODUCER = "user-service";
    private static final String REASON = "USER_PROFILE_UPDATED";

    public static UserAuthorityChangedMessage from(UserAuthorityChangedEvent event) {
        return new UserAuthorityChangedMessage(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                EVENT_VERSION,
                PRODUCER,
                Instant.now().toString(),
                "USER-" + event.keycloakSub(),
                new Payload(
                        event.keycloakSub(),
                        event.employeeNo(),
                        REASON
                )
        );
    }

    public record Payload(
            String keycloakSub,
            String employeeNo,
            String reason
    ) {
    }
}
