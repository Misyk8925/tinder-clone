CREATE TABLE deck_experiment (
    experiment_id   UUID PRIMARY KEY,
    name            VARCHAR(120) NOT NULL UNIQUE,
    salt            VARCHAR(120) NOT NULL,
    default_variant VARCHAR(80)  NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('DRAFT', 'RUNNING', 'STOPPED')),
    created_at      TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    stopped_at      TIMESTAMPTZ,
    CONSTRAINT deck_experiment_running_has_start CHECK (status <> 'RUNNING' OR started_at IS NOT NULL),
    CONSTRAINT deck_experiment_stopped_has_stop  CHECK (status <> 'STOPPED' OR stopped_at IS NOT NULL)
);

-- The scoring path resolves exactly one running experiment, so the database refuses a
-- second one rather than letting the choice depend on row order.
CREATE UNIQUE INDEX deck_experiment_single_running_idx
    ON deck_experiment (status)
    WHERE status = 'RUNNING';

CREATE TABLE deck_experiment_variant (
    experiment_id UUID        NOT NULL REFERENCES deck_experiment (experiment_id) ON DELETE CASCADE,
    variant       VARCHAR(80) NOT NULL,
    weight        INTEGER     NOT NULL CHECK (weight >= 0),
    PRIMARY KEY (experiment_id, variant)
);
