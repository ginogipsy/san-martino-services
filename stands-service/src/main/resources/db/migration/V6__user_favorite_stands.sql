-- Cantine preferite ("Dove voglio andare") salvate dagli utenti dell'app mobile (#058).
CREATE TABLE user_favorite_stands
(
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(120) NOT NULL,
    username   VARCHAR(120) NOT NULL,
    stand_id   UUID         NOT NULL REFERENCES stands (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_favorite_stands_user_stand UNIQUE (user_id, stand_id)
);

CREATE INDEX idx_user_favorite_stands_user ON user_favorite_stands (user_id, created_at DESC);
CREATE INDEX idx_user_favorite_stands_stand ON user_favorite_stands (stand_id);
