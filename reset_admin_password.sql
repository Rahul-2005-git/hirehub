-- =============================================================================
-- Reset Admin Password Script for HireHub
-- Database: hirehub
-- User: hirehub
-- =============================================================================

-- Enable pgcrypto extension for bcrypt password hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Set new password for admin@hirehub.local
-- You can change 'Password123' to whatever password you want:
UPDATE users 
SET password_hash = crypt('Password123', gen_salt('bf', 10)),
    status = 'ACTIVE',
    updated_at = NOW()
WHERE email = 'admin@hirehub.local';

-- Verify the update
SELECT id, email, full_name, role_id, status, updated_at 
FROM users 
WHERE email = 'admin@hirehub.local';
