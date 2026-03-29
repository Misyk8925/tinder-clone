CREATE TABLE match_chat_analytics
(
    active_days             INTEGER NOT NULL,
    audio_duration_ms_total BIGINT  NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    first_message_at        TIMESTAMP WITH TIME ZONE,
    first_reply_at          TIMESTAMP WITH TIME ZONE,
    first_reply_latency_ms  BIGINT,
    last_message_at         TIMESTAMP WITH TIME ZONE,
    matched_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    total_messages          BIGINT                   NOT NULL,
    unmatched_at            TIMESTAMP WITH TIME ZONE,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 BIGINT                   NOT NULL,
    video_duration_ms_total BIGINT                   NOT NULL,
    first_message_sender_id UUID,
    last_message_sender_id  UUID,
    profile1_id             UUID                     NOT NULL,
    profile2_id             UUID                     NOT NULL,
    CONSTRAINT match_chat_analytics_pkey PRIMARY KEY (profile1_id, profile2_id)
);

CREATE TABLE preferences
(
    id        UUID    NOT NULL,
    gender    VARCHAR(255),
    max_age   INTEGER,
    max_range INTEGER NOT NULL,
    min_age   INTEGER,
    CONSTRAINT preferences_pkey PRIMARY KEY (id)
);

CREATE TABLE profiles
(
    id                 UUID                  NOT NULL,
    age                INTEGER               NOT NULL,
    bio                VARCHAR(1023),
    city               VARCHAR(255)          NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    deleted_at         TIMESTAMP WITHOUT TIME ZONE,
    gender             VARCHAR(255)          NOT NULL,
    is_active          BOOLEAN DEFAULT TRUE  NOT NULL,
    is_deleted         BOOLEAN DEFAULT FALSE NOT NULL,
    is_premium         BOOLEAN DEFAULT FALSE NOT NULL,
    name               VARCHAR(255)          NOT NULL,
    premium_expires_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at         TIMESTAMP WITHOUT TIME ZONE,
    user_id            VARCHAR(255),
    version            BIGINT                NOT NULL,
    location_id        UUID                  NOT NULL,
    preferences_id     UUID                  NOT NULL,
    CONSTRAINT profiles_pkey PRIMARY KEY (id)
);

CREATE TABLE photo
(
    photo_id     UUID         NOT NULL,
    content_type VARCHAR(255),
    created_at   TIMESTAMP WITHOUT TIME ZONE,
    is_primary   BOOLEAN      NOT NULL,
    position     INTEGER      NOT NULL,
    s3_key       VARCHAR(255) NOT NULL,
    size         BIGINT,
    url          VARCHAR(255) NOT NULL,
    profile_id   UUID         NOT NULL,
    CONSTRAINT photo_pkey PRIMARY KEY (photo_id)
);

CREATE TABLE profile_event_outbox
(
    id         UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    dead_lettered_at TIMESTAMP WITH TIME ZONE,
    event_id         UUID                     NOT NULL,
    event_type       VARCHAR(32)              NOT NULL,
    last_error       VARCHAR(1000),
    next_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    payload          TEXT                     NOT NULL,
    profile_id       UUID                     NOT NULL,
    published_at     TIMESTAMP WITH TIME ZONE,
    retry_count      INTEGER                  NOT NULL,
    CONSTRAINT profile_event_outbox_pkey PRIMARY KEY (id)
);

CREATE TABLE match_event_outbox
(
    retry_count INTEGER NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
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
    CONSTRAINT match_event_outbox_pkey PRIMARY KEY (id)
);

CREATE TABLE pending_likes
(
    is_super BOOLEAN NOT NULL,
    liked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    id               UUID                     NOT NULL,
    liked_user_id    UUID                     NOT NULL,
    liker_profile_id UUID                     NOT NULL,
    CONSTRAINT pending_likes_pkey PRIMARY KEY (id)
);

CREATE TABLE swipe_event_outbox
(
    retry_count INTEGER NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
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
    CONSTRAINT swipe_event_outbox_pkey PRIMARY KEY (id)
);

CREATE TABLE swipe_records
(
    decision1   BOOLEAN,
    decision2   BOOLEAN,
    version     BIGINT NOT NULL,
    profile1_id UUID   NOT NULL,
    profile2_id UUID   NOT NULL,
    CONSTRAINT swipe_records_pkey PRIMARY KEY (profile1_id, profile2_id)
);

CREATE TABLE conversations
(
    conversation_id UUID NOT NULL,
    participant1_id UUID NOT NULL,
    participant2_id UUID NOT NULL,
    status          VARCHAR(255),
    CONSTRAINT conversations_pkey PRIMARY KEY (conversation_id)
);

CREATE TABLE matches
(
    status     SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    matched_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    unmatched_at TIMESTAMP WITH TIME ZONE,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT                   NOT NULL,
    profile1_id  UUID                     NOT NULL,
    profile2_id  UUID                     NOT NULL,
    CONSTRAINT matches_pkey PRIMARY KEY (profile1_id, profile2_id)
);

CREATE TABLE messages
(
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    client_message_id UUID                     NOT NULL,
    conversation_id   UUID                     NOT NULL,
    message_id        UUID                     NOT NULL,
    sender_id         UUID                     NOT NULL,
    type              VARCHAR(16)              NOT NULL,
    text              VARCHAR(5000),
    CONSTRAINT messages_pkey PRIMARY KEY (message_id)
);

CREATE TABLE profile_hobbies
(
    profile_id UUID NOT NULL,
    hobby      VARCHAR(255)
);

CREATE TABLE spatial_ref_sys
(
    srid      INTEGER NOT NULL,
    auth_name VARCHAR(256),
    auth_srid INTEGER,
    srtext    VARCHAR(2048),
    proj4text VARCHAR(2048),
    CONSTRAINT spatial_ref_sys_pkey PRIMARY KEY (srid)
);

CREATE TABLE stripe_event_inbox
(
    attempts      INTEGER NOT NULL,
    livemode      BOOLEAN NOT NULL,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    processed_at   TIMESTAMP WITH TIME ZONE,
    stripe_created BIGINT,
    event_type     VARCHAR(255) NOT NULL,
    id             VARCHAR(255) NOT NULL,
    last_error     VARCHAR(255),
    object_id      VARCHAR(255),
    payload_json   TEXT         NOT NULL,
    status         VARCHAR(255),
    CONSTRAINT stripe_event_inbox_pkey PRIMARY KEY (id)
);

CREATE TABLE profile_cache_model
(
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_id UUID                     NOT NULL,
    user_id    VARCHAR(255),
    CONSTRAINT profile_cache_model_pkey PRIMARY KEY (profile_id)
);

CREATE INDEX idx_mca_matched_at ON match_chat_analytics (matched_at);

CREATE INDEX idx_mca_last_message_at ON match_chat_analytics (last_message_at);

CREATE INDEX idx_mca_profile1_last_message_at ON match_chat_analytics (profile1_id, last_message_at);

CREATE INDEX idx_mca_profile2_last_message_at ON match_chat_analytics (profile2_id, last_message_at);

ALTER TABLE preferences
    ADD CONSTRAINT uk_preferences_combination UNIQUE (min_age, max_age, gender, max_range);

CREATE INDEX idx_age ON profiles (age);

CREATE INDEX idx_gender ON profiles (gender);

CREATE INDEX idx_city ON profiles (city);

CREATE INDEX idx_active_deleted ON profiles (is_active, is_deleted);

CREATE INDEX idx_search_query ON profiles (is_deleted, age, gender);

CREATE INDEX idx_created_at_deleted ON profiles (created_at, is_deleted);

CREATE INDEX idx_active_created ON profiles (is_active, created_at);

CREATE INDEX idx_name_lower ON profiles (name);

ALTER TABLE profiles
    ADD CONSTRAINT idx_user_id UNIQUE (user_id);

CREATE INDEX idx_photo_profile_id ON photo (profile_id);

CREATE INDEX idx_photo_primary_profile ON photo (profile_id, is_primary);

ALTER TABLE photo
    ADD CONSTRAINT idx_photo_s3_key UNIQUE (s3_key);

CREATE INDEX idx_outbox_publish_window ON profile_event_outbox (published_at, dead_lettered_at, next_attempt_at, created_at);

CREATE INDEX idx_outbox_profile ON profile_event_outbox (profile_id);

ALTER TABLE profile_event_outbox
    ADD CONSTRAINT uk_outbox_event_id UNIQUE (event_id);

CREATE INDEX idx_match_outbox_publish_window ON match_event_outbox (published_at, dead_lettered_at, next_attempt_at, created_at);

CREATE INDEX idx_match_outbox_profile1 ON match_event_outbox (profile1_id);

CREATE INDEX idx_match_outbox_profile2 ON match_event_outbox (profile2_id);

ALTER TABLE match_event_outbox
    ADD CONSTRAINT uk_match_outbox_event_id UNIQUE (event_id);

CREATE INDEX idx_pending_liked_user_id ON pending_likes (liked_user_id);

CREATE INDEX idx_pending_liker_profile_id ON pending_likes (liker_profile_id);

ALTER TABLE pending_likes
    ADD CONSTRAINT idx_pending_unique_pair UNIQUE (liked_user_id, liker_profile_id);

CREATE INDEX idx_swipe_outbox_publish_window ON swipe_event_outbox (published_at, dead_lettered_at, next_attempt_at, created_at);

CREATE INDEX idx_swipe_outbox_swiper ON swipe_event_outbox (swiper_id);

CREATE INDEX idx_swipe_outbox_swiped ON swipe_event_outbox (swiped_id);

ALTER TABLE swipe_event_outbox
    ADD CONSTRAINT uk_swipe_outbox_event_id UNIQUE (event_id);

CREATE INDEX idx_swipe_profile1_id ON swipe_records (profile1_id);

CREATE INDEX idx_swipe_profile2_id ON swipe_records (profile2_id);

CREATE INDEX idx_swipe_profile1_decision ON swipe_records (profile1_id, decision1);

CREATE INDEX idx_swipe_profile2_decision ON swipe_records (profile2_id, decision2);

CREATE INDEX idx_swipe_both_profiles ON swipe_records (profile1_id, profile2_id, decision1, decision2);

CREATE VIEW geography_columns AS
SELECT current_database()               AS f_table_catalog,
       n.nspname                        AS f_table_schema,
       c.relname                        AS f_table_name,
       a.attname                        AS f_geography_column,
       postgis_typmod_dims(a.atttypmod) AS coord_dimension,
       postgis_typmod_srid(a.atttypmod) AS srid,
       postgis_typmod_type(a.atttypmod) AS type
FROM pg_class c,
     pg_attribute a,
     pg_type t,
     pg_namespace n
WHERE ((t.typname = 'geography'::name) AND (a.attisdropped = false) AND (a.atttypid = t.oid) AND
       (a.attrelid = c.oid) AND (c.relnamespace = n.oid) AND
       (c.relkind = ANY (ARRAY ['r'::"char", 'v'::"char", 'm'::"char", 'f'::"char", 'p'::"char"])) AND
       (NOT pg_is_other_temp_schema(c.relnamespace)) AND has_table_privilege(c.oid, 'SELECT'::text));

CREATE VIEW geometry_columns AS
SELECT (current_database())::character varying(256)                      AS f_table_catalog,
       n.nspname                                                         AS f_table_schema,
       c.relname                                                         AS f_table_name,
       a.attname                                                         AS f_geometry_column,
       COALESCE(postgis_typmod_dims(a.atttypmod), sn.ndims, 2)           AS coord_dimension,
       COALESCE(NULLIF(postgis_typmod_srid(a.atttypmod), 0), sr.srid, 0) AS srid,
       (replace(replace(COALESCE(NULLIF(upper(postgis_typmod_type(a.atttypmod)), 'GEOMETRY'::text), st.type,
                                 'GEOMETRY'::text), 'ZM'::text, ''::text), 'Z'::text,
                ''::text))::character varying(30)                        AS type
FROM ((((((pg_class c
    JOIN pg_attribute a ON (((a.attrelid = c.oid) AND (NOT a.attisdropped))))
    JOIN pg_namespace n ON ((c.relnamespace = n.oid)))
    JOIN pg_type t ON ((a.atttypid = t.oid)))
    LEFT JOIN (SELECT s.connamespace,
                      s.conrelid,
                      s.conkey,
                      replace(split_part(s.consrc, ''''::text, 2), ')'::text, ''::text) AS type
               FROM (SELECT pg_constraint.connamespace,
                            pg_constraint.conrelid,
                            pg_constraint.conkey,
                            pg_get_constraintdef(pg_constraint.oid) AS consrc
                     FROM pg_constraint) s
               WHERE (s.consrc ~~* '%geometrytype(% = %'::text)) st
        ON (((st.connamespace = n.oid) AND (st.conrelid = c.oid) AND (a.attnum = ANY (st.conkey)))))
    LEFT JOIN (SELECT s.connamespace,
                      s.conrelid,
                      s.conkey,
                      (replace(split_part(s.consrc, ' = '::text, 2), ')'::text, ''::text))::integer AS ndims
               FROM (SELECT pg_constraint.connamespace,
                            pg_constraint.conrelid,
                            pg_constraint.conkey,
                            pg_get_constraintdef(pg_constraint.oid) AS consrc
                     FROM pg_constraint) s
               WHERE (s.consrc ~~* '%ndims(% = %'::text)) sn
       ON (((sn.connamespace = n.oid) AND (sn.conrelid = c.oid) AND (a.attnum = ANY (sn.conkey)))))
    LEFT JOIN (SELECT s.connamespace,
                      s.conrelid,
                      s.conkey,
                      (replace(replace(split_part(s.consrc, ' = '::text, 2), ')'::text, ''::text), '('::text,
                               ''::text))::integer AS srid
               FROM (SELECT pg_constraint.connamespace,
                            pg_constraint.conrelid,
                            pg_constraint.conkey,
                            pg_get_constraintdef(pg_constraint.oid) AS consrc
                     FROM pg_constraint) s
               WHERE (s.consrc ~~* '%srid(% = %'::text)) sr
      ON (((sr.connamespace = n.oid) AND (sr.conrelid = c.oid) AND (a.attnum = ANY (sr.conkey)))))
WHERE ((c.relkind = ANY (ARRAY ['r'::"char", 'v'::"char", 'm'::"char", 'f'::"char", 'p'::"char"])) AND
       (NOT (c.relname = 'raster_columns'::name)) AND (t.typname = 'geometry'::name) AND
       (NOT pg_is_other_temp_schema(c.relnamespace)) AND has_table_privilege(c.oid, 'SELECT'::text));

ALTER TABLE conversations
    ADD CONSTRAINT ux_conversations_participants UNIQUE (participant1_id, participant2_id);

ALTER TABLE messages
    ADD CONSTRAINT ux_messages_sender_client_id UNIQUE (sender_id, client_message_id);

ALTER TABLE profile_cache_model
    ADD CONSTRAINT profile_cache_model_user_id_key UNIQUE (user_id);

CREATE TABLE billing_subscriptions
(
    cancel_at_period_end BOOLEAN NOT NULL,
    current_period_end   TIMESTAMP WITH TIME ZONE,
    last_stripe_event_created BIGINT,
    updated_at                TIMESTAMP WITH TIME ZONE,
    price_id                  VARCHAR(255),
    status                    VARCHAR(255),
    stripe_customer_id        VARCHAR(255) NOT NULL,
    stripe_subscription_id    VARCHAR(255) NOT NULL,
    user_id                   VARCHAR(255) NOT NULL,
    CONSTRAINT billing_subscriptions_pkey PRIMARY KEY (stripe_subscription_id)
);

CREATE TABLE location
(
    id         UUID         NOT NULL,
    city       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    geo        GEOGRAPHY,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT location_pkey PRIMARY KEY (id)
);

CREATE TABLE message_attachments
(
    height     INTEGER,
    width      INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms   BIGINT,
    size_bytes    BIGINT                   NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    attachment_id UUID                     NOT NULL,
    message_id    UUID                     NOT NULL,
    sha256        VARCHAR(64),
    mime_type     VARCHAR(128)             NOT NULL,
    storage_key   VARCHAR(1024)            NOT NULL,
    url           VARCHAR(2048)            NOT NULL,
    original_name VARCHAR(255),
    CONSTRAINT message_attachments_pkey PRIMARY KEY (attachment_id)
);

CREATE TABLE participants
(
    participant_id   UUID NOT NULL,
    participant_name VARCHAR(255),
    CONSTRAINT participants_pkey PRIMARY KEY (participant_id)
);

CREATE TABLE profile_cache
(
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_id UUID                     NOT NULL,
    user_id    VARCHAR(255)             NOT NULL,
    CONSTRAINT profile_cache_pkey PRIMARY KEY (profile_id)
);

CREATE TABLE stripe_customer
(
    livemode   BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at         TIMESTAMP WITH TIME ZONE,
    id                 VARCHAR(255) NOT NULL,
    stripe_customer_id VARCHAR(255),
    user_id            VARCHAR(255),
    CONSTRAINT stripe_customer_pkey PRIMARY KEY (id)
);

CREATE TABLE swipe_events
(
    decision   BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_id   UUID                     NOT NULL,
    swiped_id  UUID                     NOT NULL,
    swiper_id  UUID                     NOT NULL,
    CONSTRAINT swipe_events_pkey PRIMARY KEY (event_id)
);

ALTER TABLE profile_hobbies
    ADD CONSTRAINT fk5kh3v1wic4fwt7r8dyve8mm0m FOREIGN KEY (profile_id) REFERENCES profiles (id) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE profiles
    ADD CONSTRAINT fk762rpic1pb03pmvrwhm2unxwe FOREIGN KEY (preferences_id) REFERENCES preferences (id) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE profiles
    ADD CONSTRAINT fkd02cp3mvopcpiktc0jpjga8fw FOREIGN KEY (location_id) REFERENCES location (id) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE message_attachments
    ADD CONSTRAINT fkj7twd218e2gqw9cmlhwvo1rth FOREIGN KEY (message_id) REFERENCES messages (message_id) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE photo
    ADD CONSTRAINT fknvysjhw0vcb6kmpnia3conyc1 FOREIGN KEY (profile_id) REFERENCES profiles (id) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE messages
    ADD CONSTRAINT fkt492th6wsovh1nush5yl5jj8e FOREIGN KEY (conversation_id) REFERENCES conversations (conversation_id) ON UPDATE NO ACTION ON DELETE NO ACTION;