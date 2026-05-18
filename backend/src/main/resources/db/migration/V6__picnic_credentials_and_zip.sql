-- Wipe existing plain-text connections — tokens cannot be retroactively encrypted at migration time.
-- Users will be prompted to reconnect their Picnic account after this upgrade.
DELETE FROM picnic_connections;

-- encrypted_password: AES-256-GCM ciphertext stored as "base64(iv):base64(ciphertext)"
ALTER TABLE picnic_connections ADD COLUMN IF NOT EXISTS encrypted_password TEXT;

-- zip_code: user's Picnic delivery area (optional, up to 10 chars for international codes)
ALTER TABLE picnic_connections ADD COLUMN IF NOT EXISTS zip_code VARCHAR(10);
