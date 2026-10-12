package com.nettrack.order;

// Where outbox events go. Today: the log. Part 5: a Kafka topic, keyed by orderId
// so all events for one order stay in order on the same partition.
public interface EventPublisher {
    void publish(String eventType, String key, String payload);
}
