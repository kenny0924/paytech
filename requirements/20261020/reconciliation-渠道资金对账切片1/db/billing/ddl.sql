-- 需求：requirements/20261020/reconciliation-渠道资金对账切片1（阶段 3 评审稿，2026-10-20），Q-02
-- 目标库：mor_billing
-- 性质：待执行增量（阶段 4 同步复制到 paytech/billing/docs/database/sql/changes/2026-10-20-billing-fact-ref-channel-transaction.sql）。
-- 前置：t_billing_fact_ref 尚无 external_reference_type、external_transaction_id 两列。不可重跑（第二次会因列已存在失败）。
-- 内容：引用表加两列可空外部引用；ref_type 注释追加 CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。只改合同与表，不加生成逻辑。
-- 回退：ALTER TABLE `t_billing_fact_ref` DROP COLUMN `external_transaction_id`, DROP COLUMN `external_reference_type`, 并把 ref_type 注释改回原文（两列在 billing 需求 B 之前不会有数据）。
-- 授权：本文件不携带执行授权。
SET NAMES utf8mb4;

ALTER TABLE `t_billing_fact_ref`
  MODIFY COLUMN `ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '引用的外部业务对象类型。允许值：ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE、CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。',
  ADD COLUMN `external_reference_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '渠道外部交易号类型，仅 ref_type = CHANNEL_TRANSACTION_REF 时非空。取值来源：Payment 渠道交易引用类型目录；非固定枚举。' AFTER `ref_id`,
  ADD COLUMN `external_transaction_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '渠道外部交易号，仅 ref_type = CHANNEL_TRANSACTION_REF 时非空；对账按它匹配渠道账单行。' AFTER `external_reference_type`;
