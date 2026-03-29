CREATE TABLE preferences
(
    id        UUID    NOT NULL,
    gender    VARCHAR(255),
    max_age   INTEGER,
    max_range INTEGER NOT NULL,
    min_age   INTEGER,
    CONSTRAINT preferences_pkey PRIMARY KEY (id),
    CONSTRAINT uk_preferences_combination UNIQUE (min_age, max_age, gender, max_range)
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
    CONSTRAINT profiles_pkey PRIMARY KEY (id),
    CONSTRAINT idx_user_id UNIQUE (user_id),
    CONSTRAINT fk_profiles_preferences FOREIGN KEY (preferences_id) REFERENCES preferences (id),
    CONSTRAINT fk_profiles_location FOREIGN KEY (location_id) REFERENCES location (id)
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
    CONSTRAINT photo_pkey PRIMARY KEY (photo_id),
    CONSTRAINT idx_photo_s3_key UNIQUE (s3_key),
    CONSTRAINT fk_photo_profile FOREIGN KEY (profile_id) REFERENCES profiles (id)
);

CREATE TABLE profile_hobbies
(
    profile_id UUID NOT NULL,
    hobby      VARCHAR(255),
    CONSTRAINT fk_profile_hobbies_profile FOREIGN KEY (profile_id) REFERENCES profiles (id)
);

CREATE TABLE profile_event_outbox
(
    id               UUID                     NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    dead_lettered_at TIMESTAMP WITH TIME ZONE,
    event_id         UUID                     NOT NULL,
    event_type       VARCHAR(32)              NOT NULL,
    last_error       VARCHAR(1000),
    next_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    payload          TEXT                     NOT NULL,
    profile_id       UUID                     NOT NULL,
    published_at     TIMESTAMP WITH TIME ZONE,
    retry_count      INTEGER                  NOT NULL,
    CONSTRAINT profile_event_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

CREATE TABLE profile_cache_model
(
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_id UUID                     NOT NULL,
    user_id    VARCHAR(255),
    CONSTRAINT profile_cache_model_pkey PRIMARY KEY (profile_id),
    CONSTRAINT profile_cache_model_user_id_key UNIQUE (user_id)
);

CREATE INDEX idx_age ON profiles (age);
CREATE INDEX idx_gender ON profiles (gender);
CREATE INDEX idx_city ON profiles (city);
CREATE INDEX idx_active_deleted ON profiles (is_active, is_deleted);
CREATE INDEX idx_search_query ON profiles (is_deleted, age, gender);
CREATE INDEX idx_created_at_deleted ON profiles (created_at, is_deleted);
CREATE INDEX idx_active_created ON profiles (is_active, created_at);
CREATE INDEX idx_name_lower ON profiles (name);

CREATE INDEX idx_photo_profile_id ON photo (profile_id);
CREATE INDEX idx_photo_primary_profile ON photo (profile_id, is_primary);

CREATE INDEX idx_outbox_publish_window ON profile_event_outbox (published_at, dead_lettered_at, next_attempt_at, created_at);
CREATE INDEX idx_outbox_profile ON profile_event_outbox (profile_id);

