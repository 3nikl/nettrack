-- Flyway migration #1. Never edit this after it has run anywhere — add V2__... instead.
CREATE TABLE tickets (
    id          BIGSERIAL PRIMARY KEY,           -- auto-increment id
    alarm_id    VARCHAR(64) UNIQUE,              -- id from the network alarm system; UNIQUE = idempotency
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    region      VARCHAR(64)  NOT NULL,           -- e.g. NJ-MIDDLETOWN
    severity    VARCHAR(16)  NOT NULL,           -- LOW / MEDIUM / HIGH / CRITICAL
    status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Dashboard query is "open tickets, newest first" -> index on exactly that
CREATE INDEX idx_tickets_status_created ON tickets (status, created_at DESC);
