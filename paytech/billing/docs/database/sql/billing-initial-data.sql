-- Code-MoR Billing initialization DML.
-- Apply manually to mor_billing after billing-schema.sql; the application and auto tests never execute this file.
-- Currency rules are not stored here: billing reads them from platform-support (mor_platform.t_currency_spec) into an in-memory
-- snapshot (requirement 20261020 platform-support-币种主数据维护与读取缓存).
-- t_business_calendar is a read-only reference copy (requirement 20261019 billing-计费事实模型重构与渠道成本迁出); the DEFAULT row
-- (UTC, Monday to Friday, no exception days) is reserved and identical to that requirement's db/billing/dml.sql.
-- AE_BUSINESS_DAYS (Asia/Dubai, Monday to Friday, no exception days) is the calendar code commercial hard-codes for Client T+N
-- settlement (CommercialCodes.AE_BUSINESS_DAYS); added by requirement 20261022 billing-支付事实自动登记 (S15), change file
-- changes/2026-10-23-business-calendar-ae.sql.
SET NAMES utf8mb4;

INSERT INTO `t_business_calendar` (`calendar_code`, `display_name`, `timezone`, `status`, `create_time`, `update_time`)
VALUES ('DEFAULT', '默认工作日日历（周一至周五）', 'UTC', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_business_calendar` (`calendar_code`, `display_name`, `timezone`, `status`, `create_time`, `update_time`)
VALUES ('AE_BUSINESS_DAYS', '阿联酋工作日', 'Asia/Dubai', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
