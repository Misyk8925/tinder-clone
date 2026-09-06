CREATE TABLE moderation_policy_version (
    policy_version VARCHAR(80) PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    description VARCHAR(500),
    policy_json JSONB NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_by VARCHAR(128),
    published_at TIMESTAMPTZ,
    CONSTRAINT published_policy_has_metadata CHECK (
        status <> 'PUBLISHED' OR (published_by IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE TABLE moderation_policy_activation (
    activation_id UUID PRIMARY KEY,
    content_type VARCHAR(64),
    locale VARCHAR(35),
    policy_version VARCHAR(80) NOT NULL REFERENCES moderation_policy_version(policy_version),
    previous_policy_version VARCHAR(80) REFERENCES moderation_policy_version(policy_version),
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    activated_by VARCHAR(128) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT moderation_policy_activation_scope_unique UNIQUE NULLS NOT DISTINCT (content_type, locale)
);

CREATE TABLE moderation_decision (
    decision_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128),
    request_hash CHAR(64) NOT NULL,
    source_message_id UUID,
    content_id VARCHAR(128) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    author_id VARCHAR(128),
    locale VARCHAR(35),
    country CHAR(2),
    normalized_text TEXT,
    image_urls_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    context_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('ALLOW', 'FLAG', 'BLOCK', 'HOLD')),
    reason VARCHAR(128),
    confidence DOUBLE PRECISION CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    policy_version VARCHAR(80) NOT NULL REFERENCES moderation_policy_version(policy_version),
    evidence_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    raw_content_expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT moderation_decision_idempotency_unique UNIQUE (idempotency_key),
    CONSTRAINT moderation_decision_source_message_unique UNIQUE (source_message_id)
);

CREATE INDEX moderation_decision_created_at_idx ON moderation_decision (created_at DESC, decision_id DESC);
CREATE INDEX moderation_decision_filter_idx ON moderation_decision (decision, policy_version, created_at DESC);
CREATE INDEX moderation_decision_content_idx ON moderation_decision (content_id, created_at DESC);

CREATE TABLE moderation_review_task (
    review_task_id UUID PRIMARY KEY,
    decision_id UUID NOT NULL UNIQUE REFERENCES moderation_decision(decision_id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'ESCALATED')),
    resolution VARCHAR(16) CHECK (resolution IS NULL OR resolution IN ('ALLOW', 'FLAG', 'BLOCK', 'HOLD')),
    note VARCHAR(2000),
    assigned_to VARCHAR(128),
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ
);

CREATE INDEX moderation_review_queue_idx ON moderation_review_task (status, created_at, review_task_id);

CREATE TABLE moderation_audit_log (
    audit_id UUID PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX moderation_audit_target_idx ON moderation_audit_log (target_type, target_id, occurred_at DESC);

CREATE TABLE moderation_outbox (
    outbox_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    topic VARCHAR(160) NOT NULL,
    message_key VARCHAR(160) NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error VARCHAR(500)
);

CREATE INDEX moderation_outbox_pending_idx
    ON moderation_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;

CREATE TABLE moderation_login_attempt (
    username VARCHAR(128) PRIMARY KEY,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);
