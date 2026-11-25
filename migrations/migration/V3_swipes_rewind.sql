-- Idempotent migration for Swipe Rewind state in swipes_db (FR-1..FR-6, FR-9).
-- Append-only history and revocation tables; original swipe rows are never deleted.

CREATE TABLE IF NOT EXISTS swipe_history
(
    event_id    UUID                     NOT NULL,
    profile1_id UUID                     NOT NULL,
    profile2_id UUID                     NOT NULL,
    decision    BOOLEAN                  NOT NULL,
    is_super    BOOLEAN                  NOT NULL DEFAULT FALSE,
    swiped_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT swipe_history_pkey PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_swipe_history_profile1_swiped_at
    ON swipe_history (profile1_id, swiped_at DESC, event_id DESC);

CREATE TABLE IF NOT EXISTS swipe_revocations
(
    revocation_event_id    UUID                     NOT NULL,
    revoked_swipe_event_id UUID                     NOT NULL,
    profile1_id            UUID                     NOT NULL,
    profile2_id            UUID                     NOT NULL,
    original_decision      BOOLEAN                  NOT NULL,
    swiped_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT swipe_revocations_pkey PRIMARY KEY (revocation_event_id),
    CONSTRAINT uk_swipe_revocations_swipe_event_id UNIQUE (revoked_swipe_event_id),
    CONSTRAINT fk_swipe_revocations_history FOREIGN KEY (revoked_swipe_event_id)
        REFERENCES swipe_history (event_id)
);

CREATE INDEX IF NOT EXISTS idx_swipe_revocations_profile1_revoked_at
    ON swipe_revocations (profile1_id, revoked_at DESC);

CREATE TABLE IF NOT EXISTS swipe_matches
(
    profile1_id UUID                     NOT NULL,
    profile2_id UUID                     NOT NULL,
    matched_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT swipe_matches_pkey PRIMARY KEY (profile1_id, profile2_id),
    CONSTRAINT chk_swipe_matches_ordered CHECK (profile1_id < profile2_id)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'swipes_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE swipe_history, swipe_revocations, swipe_matches TO swipes_app;
    END IF;
END $$;
