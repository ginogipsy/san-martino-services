CREATE TABLE stand_search_logs
(
    id               UUID PRIMARY KEY,
    raw_query        VARCHAR(200) NOT NULL,
    normalized_query VARCHAR(200) NOT NULL,
    kind_filter      VARCHAR(10),
    only_available   BOOLEAN      NOT NULL DEFAULT FALSE,
    results_count    INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_log_kind CHECK (kind_filter IS NULL OR kind_filter IN ('FOOD', 'DRINK')),
    CONSTRAINT chk_search_log_results CHECK (results_count >= 0)
);

CREATE INDEX idx_stand_search_logs_norm_query ON stand_search_logs (normalized_query);
CREATE INDEX idx_stand_search_logs_created_at ON stand_search_logs (created_at DESC);
CREATE INDEX idx_stand_search_logs_results ON stand_search_logs (results_count);

CREATE TABLE stand_interaction_events
(
    id               UUID PRIMARY KEY,
    stand_id         UUID        NOT NULL REFERENCES stands (id) ON DELETE CASCADE,
    menu_item_id     UUID REFERENCES menu_items (id) ON DELETE CASCADE,
    interaction_type VARCHAR(30) NOT NULL,
    search_query     VARCHAR(200),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_interaction_type CHECK (
        interaction_type IN (
                             'MENU_ITEM_CLICK',
                             'STAND_FOOD_OPEN',
                             'STAND_DRINK_OPEN',
                             'STAND_DESCRIPTION_OPEN',
                             'STAND_MAP_OPEN'
            )
        )
);

CREATE INDEX idx_stand_interactions_stand ON stand_interaction_events (stand_id);
CREATE INDEX idx_stand_interactions_menu_item ON stand_interaction_events (menu_item_id);
CREATE INDEX idx_stand_interactions_type ON stand_interaction_events (interaction_type);
CREATE INDEX idx_stand_interactions_created_at ON stand_interaction_events (created_at DESC);
