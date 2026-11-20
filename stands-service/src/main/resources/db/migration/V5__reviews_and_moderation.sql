CREATE TABLE festival_reviews
(
    id              UUID PRIMARY KEY,
    target_type     VARCHAR(20)  NOT NULL,
    target_id       UUID         NOT NULL,
    stand_id        UUID REFERENCES stands (id) ON DELETE CASCADE,
    target_name     VARCHAR(200) NOT NULL,
    author_user_id  VARCHAR(100) NOT NULL,
    author_username VARCHAR(120) NOT NULL,
    rating          INT          NOT NULL,
    comment         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    moderated_by    VARCHAR(120),
    moderation_note TEXT,
    moderated_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_review_target_type CHECK (target_type IN ('STAND', 'MENU_ITEM', 'EVENT')),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_festival_reviews_target ON festival_reviews (target_type, target_id);
CREATE INDEX idx_festival_reviews_stand ON festival_reviews (stand_id);
CREATE INDEX idx_festival_reviews_status ON festival_reviews (status);
CREATE INDEX idx_festival_reviews_author ON festival_reviews (author_user_id);
CREATE INDEX idx_festival_reviews_created_at ON festival_reviews (created_at DESC);
