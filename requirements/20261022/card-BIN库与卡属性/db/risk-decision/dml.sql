-- 阶段 4 修正（2026-10-22）：唯一执行来源改为服务仓库增量文件 paytech/risk-decision/docs/database/sql/2026-10-22-payment-card-attribute-inputs.sql
--   （DDL 3 列 + 本文件同内容的 DML）；本文件 DML 与之逐行一致，供需求评审对照，发布时执行服务仓库文件。
-- card：BIN 库与卡属性，目标库 mor_risk_decision。
-- 性质：支付点冻结版本 220003（version_no 2）1 行 + 支付风控输入变量 3 行（PAYMENT_ATTEMPT 合同 v1 原地扩展，E5）；
--       已并入 risk-decision-initial-data.sql 末尾「2026-10-22 BIN 库与卡属性」块，执行版本见
--       paytech/risk-decision/docs/database/sql/2026-10-22-payment-card-attribute-inputs.sql（DDL + 本 DML）。
-- 点版本：输入字段增加使点版本内容摘要变化（RiskDecisionCanonicalizerImpl 按合同输入字段计算）。220002（版本 1）保持原摘要，
--       已有执行发布（含当前 ACTIVE）继续引用它，运行期不重算点摘要；新 Risk 创建的执行发布引用 220003。
--       新规则只有在运营基于 220003 发布新执行发布后才能使用这三个字段。
-- 前置：db/risk-decision/ddl.sql 已执行；新 Risk 镜像（合同含 issuer_country_code、card_brand、funding_type）已部署或同批部署；
--       900000000000093014—900000000000093016 与三个 variable_code 未被占用；point_version_id 220003 未被占用，
--       SELECT COUNT(*) FROM t_decision_point_version WHERE decision_point_id = 210002 AND version_no = 2; 结果为 0。
-- 可重跑：否。回退（须先回退新 Risk 镜像，确认没有规则引用这三个变量、没有执行发布引用 220003）：
--       DELETE FROM t_risk_variable WHERE variable_id IN (900000000000093014, 900000000000093015, 900000000000093016);
--       DELETE FROM t_decision_point_version WHERE point_version_id = 220003;
-- 状态：未在 mor-test 执行；仅在本机 mor_risk_decision 执行过（2026-10-22，卡 BIN-B）。
SET NAMES utf8mb4;
START TRANSACTION;
SET @payment_card_attribute_seed_time = UTC_TIMESTAMP(6);

-- content_hash 由 DecisionPointAuthoringDescriptor.paymentAttemptRisk() 在版本号 2 下经 RiskDecisionCanonicalizerImpl 计算。
INSERT INTO `t_decision_point_version` (`point_version_id`, `decision_point_id`, `version_no`, `input_contract_code`, `input_contract_version`, `output_contract_code`, `default_decision`, `evaluation_strategy`, `failure_strategy`, `timeout_ms`, `status`, `content_hash`, `create_time`, `update_time`) VALUES
  (220003, 210002, 2, 'PAYMENT_ATTEMPT', 1, 'RISK_DECISION_RESULT', 'ALLOW', 'FIRST_TERMINAL', 'FAIL_CLOSED', 500, 'FROZEN', 'b4336cc40bb90d81fbacd14a31b8fe67848dc1e832c6f9fdb4228cb529833cd0', @payment_card_attribute_seed_time, @payment_card_attribute_seed_time);

INSERT INTO t_risk_variable (variable_id, variable_code, variable_name, description, source_type, value_type, point_code, source_contract_code, source_contract_version, source_field, rule_condition_flag, flow_condition_flag, action_parameter_flag, system_managed_flag, format_code, numeric_unit, currency_code, currency_variable_code, allowed_values_variable_code, string_value, number_value, boolean_value, datetime_value, duration_value, status, version, create_by, update_by, create_time, update_time) VALUES
  (900000000000093014, 'PAY_INPUT_ISSUER_COUNTRY', '发卡国', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'issuer_country_code', 1, 1, 0, 1, 'UPPER_ALPHA_2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_card_attribute_seed_time, @payment_card_attribute_seed_time),
  (900000000000093015, 'PAY_INPUT_CARD_BRAND', '卡组织', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'card_brand', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_card_attribute_seed_time, @payment_card_attribute_seed_time),
  (900000000000093016, 'PAY_INPUT_FUNDING_TYPE', '借贷记类型', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'funding_type', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_card_attribute_seed_time, @payment_card_attribute_seed_time);

COMMIT;
