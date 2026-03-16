CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    venue           VARCHAR(255)    NOT NULL,
    start_date      TIMESTAMP       NOT NULL,
    end_date        TIMESTAMP       NOT NULL,
    total_seats     INTEGER         NOT NULL DEFAULT 0,
    available_seats INTEGER         NOT NULL DEFAULT 0,
    price           NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    status          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_events_tenant_id ON events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events (status);
CREATE INDEX IF NOT EXISTS idx_events_tenant_status ON events (tenant_id, status);

CREATE TABLE IF NOT EXISTS seats (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        BIGINT          NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    section         VARCHAR(64)     NOT NULL,
    seat_row        VARCHAR(16)     NOT NULL,
    seat_number     INTEGER         NOT NULL,
    grade           VARCHAR(32)     NOT NULL,
    price           NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    status          VARCHAR(32)     NOT NULL DEFAULT 'AVAILABLE'
);

CREATE INDEX IF NOT EXISTS idx_seats_event_id ON seats (event_id);
CREATE INDEX IF NOT EXISTS idx_seats_event_status ON seats (event_id, status);
CREATE INDEX IF NOT EXISTS idx_seats_event_grade ON seats (event_id, grade);
