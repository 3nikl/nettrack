package com.nettrack.order;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Polls the outbox and publishes events that haven't gone out yet.
// If publishing fails, the row stays unpublished and is retried on the next poll:
// delivery is "at least once", so consumers must be idempotent (dedupe by event id).
@Component
public class OutboxRelay {

    private final OrderEventRepository events;
    private final EventPublisher publisher;

    public OutboxRelay(OrderEventRepository events, EventPublisher publisher) {
        this.events = events;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:2000}")
    @Transactional
    public void publishPending() {
        for (var e : events.findTop100ByPublishedAtIsNullOrderByIdAsc()) {
            publisher.publish(e.getEventType(), e.getOrderId(), e.getPayload());
            e.markPublished();
        }
    }
}
