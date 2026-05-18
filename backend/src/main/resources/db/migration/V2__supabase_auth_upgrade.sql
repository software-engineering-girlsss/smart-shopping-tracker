-- Upgrades an existing database to the Supabase-auth model.
-- Safe to run on a fresh database (all statements are idempotent).

-- Picnic token storage keyed by Supabase UUID
CREATE TABLE IF NOT EXISTS picnic_connections (
    user_id      VARCHAR(36)   PRIMARY KEY,
    email        VARCHAR(255)  NOT NULL,
    auth_token   VARCHAR(2048) NOT NULL,
    token_expiry BIGINT,
    connected_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- carts.user_id: BIGINT → VARCHAR(36)
-- Converts existing IDs to strings; those carts become ownerless (BIGINT IDs
-- don't match any Supabase UUID) but the items are preserved.
ALTER TABLE carts ADD COLUMN IF NOT EXISTS user_id_new VARCHAR(36);
UPDATE carts SET user_id_new = CAST(user_id AS VARCHAR(36)) WHERE user_id IS NOT NULL;
ALTER TABLE carts DROP COLUMN IF EXISTS user_id;
ALTER TABLE carts RENAME COLUMN user_id_new TO user_id;

-- favorites.user_id: BIGINT NOT NULL → VARCHAR(36) NOT NULL
-- Same approach: data preserved, existing rows become ownerless.
ALTER TABLE favorites ADD COLUMN IF NOT EXISTS user_id_new VARCHAR(36);
UPDATE favorites SET user_id_new = CAST(user_id AS VARCHAR(36));
ALTER TABLE favorites DROP COLUMN IF EXISTS user_id;
ALTER TABLE favorites RENAME COLUMN user_id_new TO user_id;
ALTER TABLE favorites ALTER COLUMN user_id SET NOT NULL;

-- Remove legacy auth tables (data moved to Supabase)
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS access_tokens;
DROP TABLE IF EXISTS user_sessions;
