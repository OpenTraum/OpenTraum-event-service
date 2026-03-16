-- =============================================
-- OpenTraum Event Service Schema
-- Migrated from FairTicket concert + seat domains
-- =============================================

-- 공연 (concerts) - tenantId 추가
CREATE TABLE IF NOT EXISTS concerts (
    id          BIGSERIAL       PRIMARY KEY,
    title       VARCHAR(255)    NOT NULL,
    artist      VARCHAR(255),
    venue       VARCHAR(255)    NOT NULL,
    tenant_id   VARCHAR(64)     NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_concerts_tenant_id ON concerts (tenant_id);

-- 공연 회차 (schedules)
CREATE TABLE IF NOT EXISTS schedules (
    id              BIGSERIAL       PRIMARY KEY,
    concert_id      BIGINT          NOT NULL REFERENCES concerts(id),
    date_time       TIMESTAMP       NOT NULL,
    total_seats     INT             NOT NULL,
    ticket_open_at  TIMESTAMP       NOT NULL,
    ticket_close_at TIMESTAMP       NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'UPCOMING',
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_schedules_concert ON schedules(concert_id);

-- 등급 설정 (grades)
CREATE TABLE IF NOT EXISTS grades (
    id          BIGSERIAL   PRIMARY KEY,
    schedule_id BIGINT      NOT NULL REFERENCES schedules(id),
    grade       VARCHAR(10) NOT NULL,
    price       INT         NOT NULL,
    UNIQUE(schedule_id, grade)
);

CREATE INDEX IF NOT EXISTS idx_grades_schedule ON grades(schedule_id);

-- 구역 설정 (zones)
CREATE TABLE IF NOT EXISTS zones (
    id          BIGSERIAL   PRIMARY KEY,
    schedule_id BIGINT      NOT NULL REFERENCES schedules(id),
    zone        VARCHAR(20) NOT NULL,
    grade       VARCHAR(10) NOT NULL,
    seat_count  INT         NOT NULL,
    UNIQUE(schedule_id, zone)
);

CREATE INDEX IF NOT EXISTS idx_zones_schedule ON zones(schedule_id);
CREATE INDEX IF NOT EXISTS idx_zones_grade ON zones(schedule_id, grade);

-- 좌석 (seats)
CREATE TABLE IF NOT EXISTS seats (
    id          BIGSERIAL       PRIMARY KEY,
    schedule_id BIGINT          NOT NULL REFERENCES schedules(id),
    grade       VARCHAR(10)     NOT NULL,
    zone        VARCHAR(20)     NOT NULL,
    seat_number VARCHAR(20)     NOT NULL,
    price       INT             NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(schedule_id, zone, seat_number)
);

CREATE INDEX IF NOT EXISTS idx_seats_schedule ON seats(schedule_id);
CREATE INDEX IF NOT EXISTS idx_seats_schedule_zone ON seats(schedule_id, zone);
CREATE INDEX IF NOT EXISTS idx_seats_status ON seats(status);
