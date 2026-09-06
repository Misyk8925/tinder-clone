INSERT INTO moderation_policy_version (
    policy_version, status, description, policy_json, aggregate_version,
    created_by, created_at, published_by, published_at
) VALUES (
    'unconfigured', 'PUBLISHED',
    'System fallback: no moderation thresholds are configured',
    '{"scopes":[]}'::jsonb, 0,
    'system', TIMESTAMPTZ '1970-01-01 00:00:00+00',
    'system', TIMESTAMPTZ '1970-01-01 00:00:00+00'
) ON CONFLICT (policy_version) DO NOTHING;
