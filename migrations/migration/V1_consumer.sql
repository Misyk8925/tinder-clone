CREATE TABLE pending_likes
(
    id               UUID                     NOT NULL,
    liked_user_id    UUID                     NOT NULL,
    liker_profile_id UUID                     NOT NULL,
    liked_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    is_super         BOOLEAN                  NOT NULL,
    CONSTRAINT pending_likes_pkey PRIMARY KEY (id),
    CONSTRAINT idx_pending_unique_pair UNIQUE (liked_user_id, liker_profile_id)
);

CREATE TABLE swipe_records
(
    profile1_id UUID   NOT NULL,
    profile2_id UUID   NOT NULL,
    decision1   BOOLEAN,
    decision2   BOOLEAN,
    version     BIGINT NOT NULL,
    CONSTRAINT swipe_records_pkey PRIMARY KEY (profile1_id, profile2_id)
);

CREATE TABLE swipe_event_outbox
(
    retry_count      INTEGER                  NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    dead_lettered_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at     TIMESTAMP WITH TIME ZONE,
    event_id         UUID                     NOT NULL,
    id               UUID                     NOT NULL,
    swiped_id        UUID                     NOT NULL,
    swiper_id        UUID                     NOT NULL,
    event_type       VARCHAR(32)              NOT NULL,
    last_error       VARCHAR(1000),
    payload          TEXT                     NOT NULL,
    CONSTRAINT swipe_event_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT uk_swipe_outbox_event_id UNIQUE (event_id)
);

CREATE TABLE match_event_outbox
(
    retry_count      INTEGER                  NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    dead_lettered_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at     TIMESTAMP WITH TIME ZONE,
    event_id         UUID                     NOT NULL,
    id               UUID                     NOT NULL,
    profile1_id      UUID                     NOT NULL,
    profile2_id      UUID                     NOT NULL,
    event_type       VARCHAR(32)              NOT NULL,
    last_error       VARCHAR(1000),
    payload          TEXT                     NOT NULL,
    CONSTRAINT match_event_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT uk_match_outbox_event_id UNIQUE (event_id)
);

CREATE TABLE profile_cache_model
(
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_id UUID                     NOT NULL,
    user_id    VARCHAR(255),
    CONSTRAINT profile_cache_model_pkey PRIMARY KEY (profile_id),
    CONSTRAINT profile_cache_model_user_id_key UNIQUE (user_id)
);

CREATE TABLE swipe_events
(
    decision   BOOLEAN                  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_id   UUID                     NOT NULL,
    swiped_id  UUID                     NOT NULL,
    swiper_id  UUID                     NOT NULL,
    CONSTRAINT swipe_events_pkey PRIMARY KEY (event_id)
);

CREATE INDEX idx_pending_liked_user_id ON pending_likes (liked_user_id);
CREATE INDEX idx_pending_liker_profile_id ON pending_likes (liker_profile_id);

CREATE INDEX idx_swipe_profile1_id ON swipe_records (profile1_id);
CREATE INDEX idx_swipe_profile2_id ON swipe_records (profile2_id);
CREATE INDEX idx_swipe_profile1_decision ON swipe_records (profile1_id, decision1);
CREATE INDEX idx_swipe_profile2_decision ON swipe_records (profile2_id, decision2);
CREATE INDEX idx_swipe_both_profiles ON swipe_records (profile1_id, profile2_id, decision1, decision2);

CREATE INDEX idx_swipe_outbox_publish_window ON swipe_event_outbox (published_at, dead_lettered_at, next_attempt_at, created_at);
CREATE INDEX idx_swipe_outbox_swiper ON swipe_event_outbox (swiper_id);
CREATE INDEX idx_swipe_outbox_swiped ON swipe_event_outbox (swiped_id);

CREATE INDEX idx_match_outbox_publish_window ON match_event_outbox (published_at, dead_lettered_at, next_attempt_at, created_at);
CREATE INDEX idx_match_outbox_profile1 ON match_event_outbox (profile1_id);
CREATE INDEX idx_match_outbox_profile2 ON match_event_outbox (profile2_id);

