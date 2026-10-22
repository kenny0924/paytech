-- BIN 库与卡属性：支付输入合同 PAYMENT_ATTEMPT v1 原地追加发卡国、卡组织、借贷记类型（待执行，需授权）。
-- 需求：requirements/20261022/card-BIN库与卡属性/technique.md 第 3.5.5 节；目标库：Risk Decision（mor_risk_decision）。
-- 预检：SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
--         AND TABLE_NAME = 't_transaction_risk_assessment' AND COLUMN_NAME IN ('issuer_country_code', 'card_brand', 'funding_type');
--       结果须为 0；point_version_id 220003、variable_id 900000000000093014—016 及三个 variable_code 未被占用；
--       SELECT COUNT(*) FROM t_decision_point_version WHERE decision_point_id = 210002 AND version_no = 2; 结果须为 0。
-- 执行顺序：先执行本文件，再部署新 Risk 镜像；Risk 须先于传卡属性的 Payment 部署。旧 Risk 镜像不读写新列，加列后仍可运行。
-- 点版本：输入字段变化使支付点版本内容摘要变化。220002（版本 1）保持原摘要，已创建的执行发布继续引用它；
--         新增冻结版本 220003（版本 2），新 Risk 创建的执行发布引用它。运行期不重算点版本摘要，生效发布不受影响。
-- 执行后核对：SHOW CREATE TABLE t_transaction_risk_assessment; 三列定义与 risk-decision-schema.sql、risk-decision.pdma.json 一致。
-- 回退（须先回退新 Risk 镜像，并确认没有规则引用三个变量、没有执行发布引用 220003）：
--   DELETE FROM t_risk_variable WHERE variable_id IN (900000000000093014, 900000000000093015, 900000000000093016);
--   DELETE FROM t_decision_point_version WHERE point_version_id = 220003;
--   ALTER TABLE t_transaction_risk_assessment DROP COLUMN funding_type, DROP COLUMN card_brand, DROP COLUMN issuer_country_code;
--   （已写入三列的评估随列丢失卡属性输入，稳定输入摘要仍按原值保存。）
SET NAMES utf8mb4;

ALTER TABLE `t_transaction_risk_assessment`
  ADD COLUMN `issuer_country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的发卡国 ISO 3166-1 alpha-2；未识别或非卡支付为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `card_bin`,
  ADD COLUMN `card_brand` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的卡组织；未识别或非卡支付为空。取值来源：Card BIN 库卡组织代码；非固定枚举。' AFTER `issuer_country_code`,
  ADD COLUMN `funding_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的借贷记类型；未识别或非卡支付为空。取值来源：Card BIN 库借贷记代码；非固定枚举。' AFTER `card_brand`;

START TRANSACTION;
SET @payment_card_attribute_seed_time = UTC_TIMESTAMP(6);

INSERT INTO `t_decision_point_version` (`point_version_id`, `decision_point_id`, `version_no`, `input_contract_code`, `input_contract_version`, `output_contract_code`, `default_decision`, `evaluation_strategy`, `failure_strategy`, `timeout_ms`, `status`, `content_hash`, `create_time`, `update_time`) VALUES
  (220003, 210002, 2, 'PAYMENT_ATTEMPT', 1, 'RISK_DECISION_RESULT', 'ALLOW', 'FIRST_TERMINAL', 'FAIL_CLOSED', 500, 'FROZEN', 'b4336cc40bb90d81fbacd14a31b8fe67848dc1e832c6f9fdb4228cb529833cd0', @payment_card_attribute_seed_time, @payment_card_attribute_seed_time);

INSERT INTO t_risk_variable (variable_id, variable_code, variable_name, description, source_type, value_type, point_code, source_contract_code, source_contract_version, source_field, rule_condition_flag, flow_condition_flag, action_parameter_flag, system_managed_flag, format_code, numeric_unit, currency_code, currency_variable_code, allowed_values_variable_code, string_value, number_value, boolean_value, datetime_value, duration_value, status, version, create_by, update_by, create_time, update_time) VALUES
  (900000000000093014, 'PAY_INPUT_ISSUER_COUNTRY', '发卡国', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'issuer_country_code', 1, 1, 0, 1, 'UPPER_ALPHA_2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_card_attribute_seed_time, @payment_card_attribute_seed_time),
  (900000000000093015, 'PAY_INPUT_CARD_BRAND', '卡组织', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'card_brand', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_card_attribute_seed_time, @payment_card_attribute_seed_time),
  (900000000000093016, 'PAY_INPUT_FUNDING_TYPE', '借贷记类型', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'funding_type', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_card_attribute_seed_time, @payment_card_attribute_seed_time);
COMMIT;
