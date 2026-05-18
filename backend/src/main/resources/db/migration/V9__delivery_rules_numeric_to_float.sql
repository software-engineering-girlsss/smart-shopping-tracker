-- DeliveryRule entity maps monetary fields to Kotlin Double (Hibernate float8/DOUBLE PRECISION).
-- The V7 migration created them as DECIMAL(10,2) / NUMERIC, causing schema-validation failure.
-- Cast all five monetary columns to DOUBLE PRECISION so Hibernate validation passes.
ALTER TABLE delivery_rules ALTER COLUMN min_basket_amount TYPE DOUBLE PRECISION;
ALTER TABLE delivery_rules ALTER COLUMN max_basket_amount TYPE DOUBLE PRECISION;
ALTER TABLE delivery_rules ALTER COLUMN delivery_fee TYPE DOUBLE PRECISION;
ALTER TABLE delivery_rules ALTER COLUMN minimum_order_amount TYPE DOUBLE PRECISION;
ALTER TABLE delivery_rules ALTER COLUMN free_delivery_threshold TYPE DOUBLE PRECISION;
