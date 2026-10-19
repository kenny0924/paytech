-- Code-MoR Billing initialization DML.
-- Apply manually to mor_billing after billing-schema.sql; the application and auto tests never execute this file.
-- t_currency_spec is a read-only copy owned by platform-support (D41); the USD row is copied verbatim from
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql and
-- paytech/ledger/docs/database/sql/ledger-initial-data.sql. Fixed IDs are reserved below 1,000,000.
-- t_business_calendar is a read-only reference copy (requirement 20261019 billing-计费事实模型重构与渠道成本迁出); the DEFAULT row
-- (UTC, Monday to Friday, no exception days) is reserved and identical to that requirement's db/billing/dml.sql.
SET NAMES utf8mb4;

INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_business_calendar` (`calendar_code`, `display_name`, `timezone`, `status`, `create_time`, `update_time`)
VALUES ('DEFAULT', '默认工作日日历（周一至周五）', 'UTC', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
