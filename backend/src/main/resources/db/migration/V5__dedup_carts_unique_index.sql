-- Removes duplicate carts per user (keeps the oldest by id) and their orphaned items.
-- Idempotent: safe to run on a DB that already has unique carts.
-- Uses GROUP BY / MIN instead of DISTINCT ON for H2 compatibility.

DELETE FROM cart_items
WHERE cart_id IN (
    SELECT id FROM carts
    WHERE user_id IS NOT NULL
      AND id NOT IN (
          SELECT MIN(id) FROM carts WHERE user_id IS NOT NULL GROUP BY user_id
      )
);

DELETE FROM carts
WHERE user_id IS NOT NULL
  AND id NOT IN (
      SELECT MIN(id) FROM carts WHERE user_id IS NOT NULL GROUP BY user_id
  );

-- Create unique index if absent (IF NOT EXISTS makes this idempotent).
-- On DBs that already have the constraint from V3, this creates a second
-- covering index with a different name — harmless, enforces the same invariant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_idx_carts_user_id ON carts (user_id);
