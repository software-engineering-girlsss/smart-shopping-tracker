-- REWE has no minimum order amount — only a free-delivery threshold at €50.
-- Corrects the V7 seed which incorrectly set minimum_order_amount=50 for REWE rows.
UPDATE delivery_rules SET minimum_order_amount = NULL WHERE store_id = 'rewe';
