package com.nettrack.order;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

// A row in the transactional outbox (see V2 migration).
@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OrderEvent() {}

    public OrderEvent(String orderId, String eventType, String payload) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void markPublished() { this.publishedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
}
