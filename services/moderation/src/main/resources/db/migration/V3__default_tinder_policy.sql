INSERT INTO moderation_policy_version (
    policy_version, status, description, policy_json, aggregate_version,
    created_by, created_at, published_by, published_at
) VALUES (
    'tinder-default-v1', 'PUBLISHED',
    'Default Tinder thresholds for profile, photo, message, and report content',
    '{
      "scopes": [
        {
          "contentType": null,
          "locale": null,
          "thresholds": {
            "HARASSMENT": {"review": 0.55, "block": 0.85},
            "HARASSMENT_THREATENING": {"review": 0.35, "block": 0.70},
            "HATE": {"review": 0.50, "block": 0.80},
            "HATE_THREATENING": {"review": 0.30, "block": 0.65},
            "SEXUAL_CONTENT": {"review": 0.60, "block": 0.90},
            "SEXUAL_MINORS": {"review": 0.10, "block": 0.25},
            "SELF_HARM": {"review": 0.35, "block": 0.70},
            "VIOLENCE": {"review": 0.45, "block": 0.80}
          }
        }
      ]
    }'::jsonb,
    0,
    'system', TIMESTAMPTZ '2026-09-06 00:00:00+00',
    'system', TIMESTAMPTZ '2026-09-06 00:00:00+00'
) ON CONFLICT (policy_version) DO NOTHING;

INSERT INTO moderation_policy_activation (
    activation_id, content_type, locale, policy_version, previous_policy_version,
    aggregate_version, activated_by, activated_at
) VALUES (
    '00000000-0000-0000-0000-00000000a001',
    NULL, NULL, 'tinder-default-v1', 'unconfigured',
    0, 'system', TIMESTAMPTZ '2026-09-06 00:00:00+00'
) ON CONFLICT (activation_id) DO NOTHING;
