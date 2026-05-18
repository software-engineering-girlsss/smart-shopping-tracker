-- Local user registry keyed by Supabase UUID.
-- Populated lazily on first authenticated request; bulk-synced at startup if service key is set.

CREATE TABLE IF NOT EXISTS users (
    id         VARCHAR(36)  PRIMARY KEY,
    email      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
