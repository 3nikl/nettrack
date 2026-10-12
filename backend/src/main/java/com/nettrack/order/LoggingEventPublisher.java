package com.nettrack.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(String eventType, String key, String payload) {
        log.info("event={} orderId={} payloadBytes={}", eventType, key, payload.length());
    }
}
