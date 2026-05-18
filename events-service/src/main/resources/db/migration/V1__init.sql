CREATE TABLE events (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    place           VARCHAR(200) NOT NULL,
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    description_it  TEXT         NOT NULL,
    description_en  TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_events_start_date ON events (start_date);
CREATE INDEX idx_events_end_date   ON events (end_date);
