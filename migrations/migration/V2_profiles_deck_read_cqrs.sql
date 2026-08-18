ALTER TABLE profile_event_outbox
    ADD COLUMN IF NOT EXISTS backfill_run_id UUID;

CREATE INDEX IF NOT EXISTS idx_outbox_backfill_run
    ON profile_event_outbox (backfill_run_id);

CREATE TABLE IF NOT EXISTS deck_card_projection_backfill_run
(
    run_id            UUID                     NOT NULL,
    status            VARCHAR(16)              NOT NULL,
    active_slot       INTEGER,
    last_profile_id   UUID,
    processed_count   BIGINT                   NOT NULL,
    expected_count    BIGINT                   NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at      TIMESTAMP WITH TIME ZONE,
    last_error        VARCHAR(500),
    CONSTRAINT deck_card_projection_backfill_run_pkey PRIMARY KEY (run_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_deck_card_projection_backfill_active_slot'
          AND conrelid = 'deck_card_projection_backfill_run'::regclass
    ) THEN
        ALTER TABLE deck_card_projection_backfill_run
            ADD CONSTRAINT uk_deck_card_projection_backfill_active_slot UNIQUE (active_slot);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_deck_card_projection_backfill_status'
          AND conrelid = 'deck_card_projection_backfill_run'::regclass
    ) THEN
        ALTER TABLE deck_card_projection_backfill_run
            ADD CONSTRAINT chk_deck_card_projection_backfill_status
                CHECK (status IN ('RUNNING', 'ENQUEUED', 'COMPLETED', 'FAILED'));
    END IF;
END
$$;
