CREATE TABLE stands (
    id                          UUID         PRIMARY KEY,
    number                      INT          NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    description_it              TEXT         NOT NULL,
    description_en              TEXT         NOT NULL,
    first_participation_year    INT          NOT NULL,
    latitude                    DOUBLE PRECISION NOT NULL,
    longitude                   DOUBLE PRECISION NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_year CHECK (first_participation_year BETWEEN 1900 AND 2200),
    CONSTRAINT chk_lat  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_lng  CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_stands_number ON stands (number);

CREATE TABLE stand_owners (
    id          UUID         PRIMARY KEY,
    stand_id    UUID         NOT NULL REFERENCES stands(id) ON DELETE CASCADE,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(50),
    role        VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_owner_role CHECK (role IN ('PRIMARY', 'SECONDARY'))
);

CREATE INDEX idx_stand_owners_stand ON stand_owners (stand_id);

CREATE TABLE menu_items (
    id                  UUID         PRIMARY KEY,
    stand_id            UUID         NOT NULL REFERENCES stands(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description_it      TEXT         NOT NULL,
    description_en      TEXT         NOT NULL,
    available_plates    INT          NOT NULL DEFAULT 0,
    kind                VARCHAR(10)  NOT NULL,
    CONSTRAINT chk_kind   CHECK (kind IN ('FOOD', 'DRINK')),
    CONSTRAINT chk_plates CHECK (available_plates >= 0)
);

CREATE INDEX idx_menu_items_stand ON menu_items (stand_id);
CREATE INDEX idx_menu_items_kind  ON menu_items (kind);

CREATE TABLE menu_item_keywords (
    menu_item_id UUID         NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    keyword      VARCHAR(100) NOT NULL
);

CREATE INDEX idx_menu_item_keywords_item    ON menu_item_keywords (menu_item_id);
CREATE INDEX idx_menu_item_keywords_keyword ON menu_item_keywords (keyword);
