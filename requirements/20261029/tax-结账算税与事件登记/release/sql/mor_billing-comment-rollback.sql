-- tax 结账算税与事件登记 · mor_billing 注释回退（mor_billing-comment.sql 的逆操作）。目标库：mor_billing；root 手工执行，可重跑。
-- 内容：ref_type 列注释恢复为 mor-test 现网原文（阶段 6 只读导出 2026-10-29T19:37Z 逐字取出，与 2026-10-20-billing-fact-ref-channel-transaction.sql
-- 同列注释相同），即去掉 'TAX_CALCULATION、'。列类型、可空、索引与数据不变。
-- 时机：只在回退 billing 镜像时执行；新 billing 已写入 ref_type = TAX_CALCULATION 的引用行时也可执行（注释不约束数据），旧镜像只读兼容。
SET NAMES utf8mb4;

ALTER TABLE `t_billing_fact_ref`
  MODIFY COLUMN `ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '引用的外部业务对象类型。允许值：ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE、CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。';
