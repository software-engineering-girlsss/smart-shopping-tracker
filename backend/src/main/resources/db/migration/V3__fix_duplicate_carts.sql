-- Remove duplicate carts, keeping the oldest per user (lowest id).
-- cart_items has no CASCADE DELETE, so items must be deleted first.

DELETE FROM cart_items WHERE cart_id IN (
    SELECT c.id FROM carts c
    WHERE c.user_id IS NOT NULL
      AND c.id NOT IN (
          SELECT DISTINCT ON (user_id) id
          FROM carts
          WHERE user_id IS NOT NULL
          ORDER BY user_id, id ASC
      )
);

DELETE FROM carts
WHERE user_id IS NOT NULL
  AND id NOT IN (
      SELECT DISTINCT ON (user_id) id
      FROM carts
      WHERE user_id IS NOT NULL
      ORDER BY user_id, id ASC
  );

ALTER TABLE carts ADD CONSTRAINT uq_carts_user_id UNIQUE (user_id);
