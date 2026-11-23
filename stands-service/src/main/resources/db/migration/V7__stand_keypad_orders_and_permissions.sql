-- =============================================================================
-- V7: Pulsantiera IoT 10 Slot (3x ESP32: Cassa, Cucina, Consegna), Coda Ordini
--     e Permessi Operatori Granulari per Cantina (OWNER, CASHIER, KITCHEN, DELIVERY, VIEWER)
-- =============================================================================

CREATE TABLE IF NOT EXISTS stand_keypad_configs
(
    stand_id              UUID PRIMARY KEY REFERENCES stands (id) ON DELETE CASCADE,
    device_token          VARCHAR(120) NOT NULL,
    next_order_number     INTEGER      NOT NULL DEFAULT 1 CHECK (next_order_number >= 1),
    last_cashier_seen_at  TIMESTAMPTZ,
    last_kitchen_seen_at  TIMESTAMPTZ,
    last_delivery_seen_at TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stand_keypad_slots
(
    id           UUID PRIMARY KEY,
    stand_id     UUID        NOT NULL REFERENCES stands (id) ON DELETE CASCADE,
    slot_number  INTEGER     NOT NULL CHECK (slot_number BETWEEN 1 AND 10),
    menu_item_id UUID        REFERENCES menu_items (id) ON DELETE SET NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stand_keypad_slot UNIQUE (stand_id, slot_number)
);

CREATE INDEX IF NOT EXISTS idx_stand_keypad_slots_stand
    ON stand_keypad_slots (stand_id, slot_number);

CREATE TABLE IF NOT EXISTS stand_staff_permissions
(
    id         UUID PRIMARY KEY,
    stand_id   UUID         NOT NULL REFERENCES stands (id) ON DELETE CASCADE,
    username   VARCHAR(120) NOT NULL,
    permission VARCHAR(32)  NOT NULL CHECK (permission IN ('OWNER', 'CASHIER', 'KITCHEN', 'DELIVERY', 'VIEWER')),
    granted_by VARCHAR(120),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stand_staff_permission UNIQUE (stand_id, username, permission)
);

CREATE INDEX IF NOT EXISTS idx_stand_staff_permissions_stand
    ON stand_staff_permissions (stand_id);

CREATE INDEX IF NOT EXISTS idx_stand_staff_permissions_username
    ON stand_staff_permissions (LOWER(username));

CREATE TABLE IF NOT EXISTS stand_orders
(
    id           UUID PRIMARY KEY,
    stand_id     UUID        NOT NULL REFERENCES stands (id) ON DELETE CASCADE,
    order_number INTEGER     NOT NULL,
    status       VARCHAR(24) NOT NULL CHECK (status IN ('IN_PROGRESS', 'READY', 'DELIVERED', 'CANCELLED')),
    created_by   VARCHAR(120),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ready_at     TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_stand_orders_stand_status_number
    ON stand_orders (stand_id, status, order_number ASC);

CREATE TABLE IF NOT EXISTS stand_order_items
(
    id                 UUID PRIMARY KEY,
    order_id           UUID         NOT NULL REFERENCES stand_orders (id) ON DELETE CASCADE,
    slot_number        INTEGER      NOT NULL CHECK (slot_number BETWEEN 1 AND 10),
    menu_item_id       UUID         REFERENCES menu_items (id) ON DELETE SET NULL,
    menu_item_name     VARCHAR(200) NOT NULL,
    kind               VARCHAR(16)  NOT NULL CHECK (kind IN ('FOOD', 'DRINK')),
    ordered_quantity   INTEGER      NOT NULL CHECK (ordered_quantity > 0),
    cooked_quantity    INTEGER      NOT NULL DEFAULT 0 CHECK (cooked_quantity >= 0),
    delivered_quantity INTEGER      NOT NULL DEFAULT 0 CHECK (delivered_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stand_order_items_order
    ON stand_order_items (order_id, slot_number);
