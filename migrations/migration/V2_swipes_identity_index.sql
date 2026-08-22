-- JWT-sub ownership lookups need one profile_id per real user_id. Local/Java
-- cache rows can duplicate the same user_id; drop the older ones first so the
-- unique index can be created on an already-populated swipes_db.
DELETE FROM profile_cache AS stale
    USING profile_cache AS kept
    WHERE stale.user_id <> 'unknown'
      AND stale.user_id = kept.user_id
      AND (
          stale.created_at < kept.created_at
          OR (stale.created_at = kept.created_at AND stale.profile_id < kept.profile_id)
      );

CREATE UNIQUE INDEX IF NOT EXISTS profile_cache_user_id_unique
    ON profile_cache (user_id)
    WHERE user_id <> 'unknown';
