-- =============================================
-- OpenTraum Event Service Schema
-- Migrated from FairTicket concert + seat domains
-- =============================================

-- 공연 (concerts) - tenantId 추가
CREATE TABLE IF NOT EXISTS concerts (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255)    NOT NULL,
    artist      VARCHAR(255),
    venue       VARCHAR(255)    NOT NULL,
    tenant_id   VARCHAR(64)     NOT NULL,
    organizer_name VARCHAR(100),
    category    VARCHAR(30)     DEFAULT 'OTHER',
    image_url   TEXT,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_concerts_tenant_id (tenant_id)
);

-- 공연 회차 (schedules)
CREATE TABLE IF NOT EXISTS schedules (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    concert_id      BIGINT          NOT NULL,
    date_time       TIMESTAMP       NOT NULL,
    total_seats     INT             NOT NULL,
    ticket_open_at  TIMESTAMP       NOT NULL,
    ticket_close_at TIMESTAMP       NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'UPCOMING',
    track_policy    VARCHAR(20)     NOT NULL DEFAULT 'DUAL_TRACK',
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_schedules_concert (concert_id),
    FOREIGN KEY (concert_id) REFERENCES concerts(id)
);

-- 등급 설정 (grades)
CREATE TABLE IF NOT EXISTS grades (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT      NOT NULL,
    grade       VARCHAR(10) NOT NULL,
    price       INT         NOT NULL,
    UNIQUE (schedule_id, grade),
    INDEX idx_grades_schedule (schedule_id),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id)
);

-- 구역 설정 (zones)
CREATE TABLE IF NOT EXISTS zones (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT      NOT NULL,
    zone        VARCHAR(20) NOT NULL,
    grade       VARCHAR(10) NOT NULL,
    seat_count  INT         NOT NULL,
    UNIQUE (schedule_id, zone),
    INDEX idx_zones_schedule (schedule_id),
    INDEX idx_zones_grade (schedule_id, grade),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id)
);

-- 좌석 (seats)
CREATE TABLE IF NOT EXISTS seats (
    id             BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    schedule_id    BIGINT          NOT NULL,
    grade          VARCHAR(10)     NOT NULL,
    zone           VARCHAR(20)     NOT NULL,
    seat_number    VARCHAR(20)     NOT NULL,
    price          INT             NOT NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    held_until     TIMESTAMP(3)    NULL,
    reservation_id BIGINT          NULL,
    created_at     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (schedule_id, zone, seat_number),
    INDEX idx_seats_schedule (schedule_id),
    INDEX idx_seats_schedule_zone (schedule_id, zone),
    INDEX idx_seats_status (status),
    INDEX idx_seats_reservation (reservation_id),
    INDEX idx_seats_held_until (held_until),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id)
);

-- 공연장 프리셋 (venues)
CREATE TABLE IF NOT EXISTS venues (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL UNIQUE,
    total_seats INT             NOT NULL,
    zone_config JSON,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- SAGA Outbox 인프라
-- =============================================

-- outbox_events: Debezium이 감시해 Kafka로 발행
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) NOT NULL UNIQUE,
    aggregate_id BIGINT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    saga_id CHAR(36) NOT NULL,
    payload JSON NOT NULL,
    trace_id CHAR(32) NULL,
    occurred_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id),
    INDEX idx_outbox_saga (saga_id),
    INDEX idx_outbox_occurred (occurred_at),
    INDEX idx_outbox_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- processed_events: Consumer 멱등성
CREATE TABLE IF NOT EXISTS processed_events (
    event_id CHAR(36) PRIMARY KEY,
    consumer_group VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
