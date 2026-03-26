-- db99_migration_mybatis.sql
-- Purpose:
-- 1. Align the existing db99 schema with the current MyBatis-based code.
-- 2. Remove obsolete JWT/session token table usage.
-- 3. Document the MD5 password migration boundary.
-- 4. Keep a few manual maintenance SQL examples.

-- MyBatis code now reads/writes users.email directly.
-- `ADD COLUMN IF NOT EXISTS` is not available on some MySQL versions,
-- so this migration uses information_schema + dynamic SQL instead.
SET @users_email_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'email'
);

SET @users_email_sql := IF(
    @users_email_exists = 0,
    'ALTER TABLE `users` ADD COLUMN `email` varchar(120) NULL AFTER `password`',
    'SELECT ''users.email already exists'''
);

PREPARE stmt_users_email FROM @users_email_sql;
EXECUTE stmt_users_email;
DEALLOCATE PREPARE stmt_users_email;

-- JWT authentication no longer uses persistent auth_tokens.
DROP TABLE IF EXISTS `auth_tokens`;

-- --------------------------------------------------------------------
-- Manual maintenance examples below.
-- These are intentionally commented out and are not required for migration.
-- Remove the leading "-- " before executing a specific statement.
-- --------------------------------------------------------------------

-- Mark Alipay-paid order as placed and release escrow immediately
-- UPDATE `customer_orders`
-- SET `status` = 'PLACED',
--     `escrow_amount` = 0
-- WHERE `order_number` = 'YOUR_ORDER_NO';

-- Mark paid order as pending admin approval
-- UPDATE `customer_orders`
-- SET `status` = 'PENDING_ADMIN',
--     `escrow_amount` = `total_amount`
-- WHERE `order_number` = 'YOUR_ORDER_NO';

-- Admin approves an order
-- UPDATE `customer_orders`
-- SET `status` = 'APPROVED',
--     `escrow_amount` = 0
-- WHERE `id` = YOUR_ORDER_ID;

-- Admin rejects an order
-- UPDATE `customer_orders`
-- SET `status` = 'REJECTED',
--     `escrow_amount` = 0
-- WHERE `id` = YOUR_ORDER_ID;

-- User requests a refund
-- UPDATE `customer_orders`
-- SET `status` = 'REFUND_REQUESTED',
--     `refund_reason` = 'YOUR_REFUND_REASON'
-- WHERE `id` = YOUR_ORDER_ID;

-- Refund completed
-- UPDATE `customer_orders`
-- SET `status` = 'REFUNDED'
-- WHERE `id` = YOUR_ORDER_ID;

-- Refund rejected and order restored to approved
-- UPDATE `customer_orders`
-- SET `status` = 'APPROVED'
-- WHERE `id` = YOUR_ORDER_ID;

-- MD5 password note:
-- The current code stores lowercase 32-character MD5 strings in users.password.
-- This does not require any schema change.
-- Existing BCrypt hashes cannot be converted to MD5 by SQL alone.
-- If you need to migrate an old account, you must reset it from known plaintext.

-- Reset a specific account to a known plaintext password
-- UPDATE `users`
-- SET `password` = MD5('YOUR_NEW_PLAINTEXT_PASSWORD')
-- WHERE `username` = 'YOUR_USERNAME';

-- Force-reset built-in demo accounts to the current MD5 defaults
-- user01 / user123
-- UPDATE `users`
-- SET `password` = '6ad14ba9986e3615423dfca256d04e3f'
-- WHERE `username` = 'user01';

-- merchant01 / merchant123
-- UPDATE `users`
-- SET `password` = 'a52f2c0dbf38ade4f715e02c7124046e'
-- WHERE `username` = 'merchant01';

-- admin01 / admin123
-- UPDATE `users`
-- SET `password` = '0192023a7bbd73250516f069df18b500'
-- WHERE `username` = 'admin01';
