-- Idempotent migration for append-only swipe revocations log in consumer_db (FR-6, FR-7, NFR-3).

CREATE TABLE IF NOT EXISTS swipe_revocations
(
    event_id               UUID                     NOT NULL,
    revoked_swipe_event_id UUID                     NOT NULL,
    profile1_id            UUID                     NOT NULL,
    profile2_id            UUID                     NOT NULL,
    original_decision      BOOLEAN                  NOT NULL,
    swiped_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    status                 VARCHAR(32)              NOT NULL,
    processed_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT swipe_revocations_pkey PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_consumer_swipe_revocations_pair
    ON swipe_revocations (profile1_id, profile2_id, revoked_at DESC);

CREATE INDEX IF NOT EXISTS idx_consumer_swipe_revocations_swipe_event_id
    ON swipe_revocations (revoked_swipe_event_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'consumer_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE swipe_revocations TO consumer_app;
    END IF;
END $$;
