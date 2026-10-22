-- 未执行草案（2026-10-22 阶段 3 评审稿）：card：BIN 库与卡属性，目标库 mor_risk_decision（risk-decision-service）。
-- 性质：需求级增量 DDL；t_transaction_risk_assessment 追加 3 个可空列，冻结支付评估的卡属性输入（PAYMENT_ATTEMPT 合同 v1 原地扩展，E5）。
-- 同步：PDManer risk-decision 模型与生成器、risk-decision-schema.sql、测试库 DDL。
-- 顺序：最先执行（先于 Risk 新镜像；Risk 须先于 Payment 部署）。可重跑：否。
-- 回退：ALTER TABLE t_transaction_risk_assessment DROP COLUMN funding_type, DROP COLUMN card_brand, DROP COLUMN issuer_country_code;
SET NAMES utf8mb4;

ALTER TABLE `t_transaction_risk_assessment`
  ADD COLUMN `issuer_country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的发卡国 ISO 3166-1 alpha-2；未识别或非卡支付为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `card_bin`,
  ADD COLUMN `card_brand` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的卡组织；未识别或非卡支付为空。取值来源：Card BIN 库卡组织代码；非固定枚举。' AFTER `issuer_country_code`,
  ADD COLUMN `funding_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的借贷记类型；未识别或非卡支付为空。取值来源：Card BIN 库借贷记代码；非固定枚举。' AFTER `card_brand`;
