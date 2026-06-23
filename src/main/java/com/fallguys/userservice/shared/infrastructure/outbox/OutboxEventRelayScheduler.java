package com.fallguys.userservice.shared.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventRelayScheduler {

    private final OutboxEventRelayService relayService;

    @Value("${outbox.relay.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relayPendingEvents() {
        relayService.findPendingEventIds(batchSize)
                .forEach(relayService::relay);
    }
}
