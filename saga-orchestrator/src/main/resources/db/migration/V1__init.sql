CREATE TABLE saga_instances (
    id                UUID         PRIMARY KEY,
    type              VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_event_id  UUID,
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ,
    message           TEXT,
    CONSTRAINT chk_saga_status CHECK (status IN ('STARTED','COMPLETED','FAILED','COMPENSATING','COMPENSATED'))
);

CREATE INDEX idx_saga_instances_started_at ON saga_instances (started_at DESC);
CREATE INDEX idx_saga_instances_status     ON saga_instances (status);

CREATE TABLE saga_steps (
    id          UUID         PRIMARY KEY,
    saga_id     UUID         NOT NULL REFERENCES saga_instances(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    step_order  INT          NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    message     TEXT,
    CONSTRAINT chk_step_status CHECK (status IN ('PENDING','COMPLETED','FAILED','COMPENSATED'))
);

CREATE INDEX idx_saga_steps_saga ON saga_steps (saga_id, step_order);

CREATE TABLE participations (
    id        UUID PRIMARY KEY,
    saga_id   UUID NOT NULL REFERENCES saga_instances(id) ON DELETE CASCADE,
    event_id  UUID NOT NULL,
    stand_id  UUID NOT NULL
);

CREATE INDEX idx_participations_event ON participations (event_id);
CREATE INDEX idx_participations_stand ON participations (stand_id);
