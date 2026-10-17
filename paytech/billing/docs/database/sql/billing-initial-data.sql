-- Code-MoR Billing initialization DML (step 07).
-- Apply manually to mor_billing after billing-schema.sql; the application and auto tests never execute this file.
-- t_currency_spec is a read-only copy owned by platform-support (D41); the USD row is copied verbatim from
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql and
-- paytech/ledger/docs/database/sql/ledger-initial-data.sql. Fixed IDs are reserved below 1,000,000.
SET NAMES utf8mb4;

INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
