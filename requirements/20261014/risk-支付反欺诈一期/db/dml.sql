-- 需求：requirements/20261014/risk-支付反欺诈一期（阶段 4）。目标库：Risk（本机 mor_risk）。
-- 性质：增量执行文件；先执行同目录 ddl.sql。内容同时并入完整初始化 DML risk-compliance-initial-data.sql，二者不得重复执行。
-- 内容：支付决策点及其冻结点版本、三个支付终态动作（2026-10-15 修订删除 3DS 动作，见文末迁移段）、支付输入合同的系统变量与节点输出变量。不初始化规则、名单或流程（阈值属于业务决定）。
-- 点版本 content_hash 由 Risk 规范投影计算：ExecutionReleaseContent.pointVersion(PAYMENT_ATTEMPT_RISK, 1)。
-- 回滚：按下列主键删除本文件插入的行（变量成员、变量、动作版本、点版本、决策点）。

START TRANSACTION;
SET @payment_seed_time = '2026-10-14 00:00:00.000000';

INSERT INTO `t_decision_point` (`decision_point_id`, `point_code`, `point_name`, `business_domain`, `status`, `create_time`, `update_time`) VALUES
  (210002, 'PAYMENT_ATTEMPT_RISK', 'Payment Attempt Risk', 'PAYMENT', 'ACTIVE', @payment_seed_time, @payment_seed_time);

INSERT INTO `t_decision_point_version` (`point_version_id`, `decision_point_id`, `version_no`, `input_contract_code`, `input_contract_version`, `output_contract_code`, `default_decision`, `evaluation_strategy`, `failure_strategy`, `timeout_ms`, `status`, `content_hash`, `create_time`, `update_time`) VALUES
  (220002, 210002, 1, 'PAYMENT_ATTEMPT', 1, 'RISK_DECISION_RESULT', 'ALLOW', 'FIRST_TERMINAL', 'FAIL_CLOSED', 500, 'FROZEN', '265ad2859a6b79e54aa040769255544300ecee9add59b412202f4eccce911e52', @payment_seed_time, @payment_seed_time);

INSERT INTO `t_action_version` (`action_version_id`, `action_code`, `action_name`, `action_category`, `version_no`, `handler_code`, `implementation_version`, `implementation_hash`, `terminal_flag`, `decision_code`, `merge_policy`, `shadow_supported_flag`, `status`, `create_time`, `update_time`) VALUES
  (240006, 'ALLOW_PAYMENT', '放行支付', 'DECISION', 1, 'DECISION_ALLOW_V1', '1.0.0', '185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1', 1, 'ALLOW', 'MOST_RESTRICTIVE', 1, 'ACTIVE', @payment_seed_time, @payment_seed_time),
  (240007, 'REVIEW_PAYMENT', '人工审核', 'DECISION', 1, 'DECISION_REVIEW_V1', '1.0.0', 'a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716', 1, 'REVIEW', 'MOST_RESTRICTIVE', 1, 'ACTIVE', @payment_seed_time, @payment_seed_time),
  (240008, 'DENY_PAYMENT', '拒绝支付', 'DECISION', 1, 'DECISION_DENY_V1', '1.0.0', '8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7', 1, 'DENY', 'MOST_RESTRICTIVE', 1, 'ACTIVE', @payment_seed_time, @payment_seed_time);

INSERT INTO t_risk_variable (variable_id, variable_code, variable_name, description, source_type, value_type, point_code, source_contract_code, source_contract_version, source_field, rule_condition_flag, flow_condition_flag, action_parameter_flag, system_managed_flag, format_code, numeric_unit, currency_code, currency_variable_code, allowed_values_variable_code, string_value, number_value, boolean_value, datetime_value, duration_value, status, version, create_by, update_by, create_time, update_time) VALUES
  (900000000000093001, 'PAY_INPUT_CLIENT_ID', '支付 Client 标识', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'client_id', 1, 1, 0, 1, 'POSITIVE_ID', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093002, 'PAY_INPUT_ENTITY_NO', '支付经营主体编号', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'entity_no', 1, 1, 0, 1, 'TEXT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093003, 'PAY_INPUT_PAYMENT_METHOD', '支付方式', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'payment_method_code', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093004, 'PAY_INPUT_CAPTURE_MODE', '捕获模式', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'capture_mode', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093005, 'PAY_INPUT_CURRENCY', '支付币种', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'currency', 1, 1, 0, 1, 'UPPER_ALPHA_3', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093006, 'PAY_INPUT_AMOUNT', '支付金额', NULL, 'INPUT', 'NUMBER', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'amount', 1, 1, 0, 1, 'NONE', 'MONEY', NULL, 'PAY_INPUT_CURRENCY', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093007, 'PAY_INPUT_INDUSTRY_CODE', '支付行业代码', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'industry_code', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093008, 'PAY_INPUT_MCC_CODE', '支付 MCC', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'mcc_code', 1, 1, 0, 1, 'DIGITS_4', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093009, 'PAY_INPUT_CONSUMER_COUNTRY', '消费者国家', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'consumer_country_code', 1, 1, 0, 1, 'UPPER_ALPHA_2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093010, 'PAY_INPUT_CARD_BIN', '卡 BIN', NULL, 'INPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'PAYMENT_ATTEMPT', 1, 'card_bin', 1, 1, 0, 1, 'TEXT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093011, 'PAY_NODE_DECISION_VALUES', '支付节点处理结果可选值', NULL, 'COLLECTION', 'STRING', 'PAYMENT_ATTEMPT_RISK', NULL, NULL, NULL, 0, 0, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093012, 'PAY_NODE_MATCHED', '支付节点是否命中', NULL, 'NODE_OUTPUT', 'BOOLEAN', 'PAYMENT_ATTEMPT_RISK', 'RULE_SET_NODE_RESULT', 1, 'matched', 0, 1, 0, 1, 'NONE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time),
  (900000000000093013, 'PAY_NODE_DECISION', '支付节点处理结果', NULL, 'NODE_OUTPUT', 'STRING', 'PAYMENT_ATTEMPT_RISK', 'RULE_SET_NODE_RESULT', 1, 'decision', 0, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, 'PAY_NODE_DECISION_VALUES', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @payment_seed_time, @payment_seed_time);

INSERT INTO t_risk_variable_member (member_id, variable_code, value_key, string_value, number_value, boolean_value, datetime_value, duration_value, display_name, sort_order, create_time) VALUES
  (900000000000094001, 'PAY_NODE_DECISION_VALUES', 'ALLOW', 'ALLOW', NULL, NULL, NULL, NULL, 'PASS', 1, @payment_seed_time),
  (900000000000094002, 'PAY_NODE_DECISION_VALUES', 'REVIEW', 'REVIEW', NULL, NULL, NULL, NULL, 'REVIEW', 2, @payment_seed_time),
  (900000000000094003, 'PAY_NODE_DECISION_VALUES', 'DENY', 'DENY', NULL, NULL, NULL, NULL, 'REJECT', 3, @payment_seed_time);
COMMIT;

-- =====================================================================
-- 修订（2026-10-15，统一评估接口与可配置输出，见 ../design-amendment-unified-evaluation.md）
-- 适用：已执行本文件修订前版本（含 240007 ALLOW_PAYMENT_WITH_THREE_DS）的库；按动作编码过滤，重复执行无副作用。
-- 前提：库中没有引用这三个动作版本的规则动作（t_rule_action）。
-- 回滚：按修订前的行重新插入 240007 并把 240007/240008 改回 240008/240009。
START TRANSACTION;
DELETE FROM `t_action_version` WHERE `action_version_id` = 240007 AND `action_code` = 'ALLOW_PAYMENT_WITH_THREE_DS';
UPDATE `t_action_version` SET `action_version_id` = 240007 WHERE `action_version_id` = 240008 AND `action_code` = 'REVIEW_PAYMENT';
UPDATE `t_action_version` SET `action_version_id` = 240008 WHERE `action_version_id` = 240009 AND `action_code` = 'DENY_PAYMENT';
COMMIT;
