CREATE TABLE profile_cache
(
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_id UUID                     NOT NULL,
    user_id    VARCHAR(255)             NOT NULL,
    CONSTRAINT profile_cache_pkey PRIMARY KEY (profile_id)
);

