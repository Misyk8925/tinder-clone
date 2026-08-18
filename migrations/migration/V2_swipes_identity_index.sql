CREATE UNIQUE INDEX IF NOT EXISTS profile_cache_user_id_unique
    ON profile_cache (user_id)
    WHERE user_id <> 'unknown';
