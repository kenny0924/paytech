-- billing：计费事实引用类型增加 TAX_CALCULATION（需求 requirements/20261029/tax-结账算税与事件登记，技术方案 8.3、11.2）。审阅后手工执行，应用与测试不执行本文件。
-- 目标库：mor_billing。前提：t_billing_fact_ref 已按 2026-10-20-billing-fact-ref-channel-transaction.sql 更新。可重跑（只改注释）。
-- 影响：仅 ref_type 列注释在 TAX_SNAPSHOT 后追加允许值 TAX_CALCULATION；列类型、可空、索引与数据不变；执行后与 billing-schema.sql、billing.pdma.json 一致。
-- 发布顺序：与新版 billing-service 无先后依赖（应用不读注释）。
-- 回退：把注释中的 'TAX_SNAPSHOT、TAX_CALCULATION、' 改回 'TAX_SNAPSHOT、'，即执行 2026-10-20 文件中同列的 MODIFY COLUMN 注释原文。
SET NAMES utf8mb4;

ALTER TABLE `t_billing_fact_ref`
  MODIFY COLUMN `ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '引用的外部业务对象类型。允许值：ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、TAX_CALCULATION、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE、CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。';
