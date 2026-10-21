-- billing：计费事实引用增加渠道外部交易号（需求 requirements/20261020/reconciliation-渠道资金对账切片1，Q-02，技术方案 2.2）。审阅后手工执行，应用与测试不执行本文件。
-- 目标库：mor_billing。前提：t_billing_fact_ref 已按 2026-10-19-billing-fact-model.sql 建立，且尚无 external_reference_type、external_transaction_id 两列；不可重跑（第二次会因列已存在失败）。
-- 影响：t_billing_fact_ref 在 ref_id 后增加两列可空外部引用；ref_type 列注释追加允许值 CHANNEL_TRANSACTION_REF、RECON_RESOLUTION，列类型、可空与索引不变，不改数据；执行后与 billing-schema.sql、billing.pdma.json 一致。
-- 发布顺序：先执行本文件，再发布读写这两列的新版 billing-service；旧版应用不读写新列，不受影响。
-- 回退：ALTER TABLE `t_billing_fact_ref` DROP COLUMN `external_transaction_id`, DROP COLUMN `external_reference_type`，并将 ref_type 注释改回以 'TICKET、FX_DEAL、FX_QUOTE。' 结尾的原文（两列在 billing 需求 B 之前不会有数据）。
SET NAMES utf8mb4;

ALTER TABLE `t_billing_fact_ref`
  MODIFY COLUMN `ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '引用的外部业务对象类型。允许值：ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE、CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。',
  ADD COLUMN `external_reference_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '渠道外部交易号类型，仅 ref_type = CHANNEL_TRANSACTION_REF 时非空。取值来源：Payment 渠道交易引用类型目录；非固定枚举。' AFTER `ref_id`,
  ADD COLUMN `external_transaction_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '渠道外部交易号，仅 ref_type = CHANNEL_TRANSACTION_REF 时非空；对账按它匹配渠道账单行。' AFTER `external_reference_type`;
