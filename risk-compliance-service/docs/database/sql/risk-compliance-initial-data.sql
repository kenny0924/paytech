-- Code-MoR Risk & Compliance 唯一初始化目录数据。
-- 只适用于完整 Schema 经人工审核并应用后的空 Risk 数据库；应用启动不得自动执行。
-- 所有 DATETIME(6) 初始化值均为固定 UTC 时间。

INSERT INTO `t_decision_point` (`decision_point_id`, `point_code`, `point_name`, `business_domain`, `status`, `create_time`, `update_time`) VALUES
  (210001, 'CHECKOUT_PREPARE', 'Checkout Prepare', 'CHECKOUT', 'ACTIVE', '2026-09-05 00:00:00.000000', '2026-09-05 00:00:00.000000');

INSERT INTO `t_action_version` (`action_version_id`, `action_code`, `action_name`, `action_category`, `version_no`, `handler_code`, `implementation_version`, `implementation_hash`, `terminal_flag`, `decision_code`, `merge_policy`, `shadow_supported_flag`, `status`, `create_time`, `update_time`) VALUES
  (240001, 'ALLOW_CHECKOUT', 'Allow Checkout', 'DECISION', 1, 'DECISION_ALLOW_V1', '1.0.0', '185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1', 1, 'ALLOW', 'MOST_RESTRICTIVE', 1, 'ACTIVE', '2026-09-05 00:00:00.000000', '2026-09-05 00:00:00.000000'),
  (240002, 'REVIEW_CHECKOUT', 'Review Checkout', 'DECISION', 1, 'DECISION_REVIEW_V1', '1.0.0', 'a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716', 1, 'REVIEW', 'MOST_RESTRICTIVE', 1, 'ACTIVE', '2026-09-05 00:00:00.000000', '2026-09-05 00:00:00.000000'),
  (240003, 'DENY_CHECKOUT', 'Deny Checkout', 'DECISION', 1, 'DECISION_DENY_V1', '1.0.0', '8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7', 1, 'DENY', 'MOST_RESTRICTIVE', 1, 'ACTIVE', '2026-09-05 00:00:00.000000', '2026-09-05 00:00:00.000000');

-- 仅配置能力：执行 Handler/Version/Hash 明确为空；不得激活或绕过冻结门禁。
INSERT INTO `t_action_version` (`action_version_id`, `action_code`, `action_name`, `action_category`, `version_no`, `handler_code`, `implementation_version`, `implementation_hash`, `terminal_flag`, `decision_code`, `merge_policy`, `shadow_supported_flag`, `status`, `create_time`, `update_time`) VALUES
  (240004, 'APPLY_COOLDOWN', '冷却', 'RESTRICTION', 1, NULL, NULL, NULL, 0, NULL, 'MAX', 0, 'CONFIGURATION_ONLY', '2026-09-11 00:00:00.000000', '2026-09-11 00:00:00.000000'),
  (240005, 'ADD_RISK_TAG', '添加主体标签', 'TAG', 1, NULL, NULL, NULL, 0, NULL, 'UNION', 0, 'CONFIGURATION_ONLY', '2026-09-11 00:00:00.000000', '2026-09-11 00:00:00.000000');

INSERT INTO `t_action_parameter_definition` (`parameter_definition_id`, `action_version_id`, `parameter_code`, `value_type`, `required_flag`, `allowed_source_type`, `target_parameter_flag`, `create_time`) VALUES
  (2400041, 240004, 'SUBJECT_ID', 'STRING', 1, 'CONTEXT', 1, '2026-09-11 00:00:00.000000'),
  (2400042, 240004, 'DURATION', 'DURATION', 1, 'LITERAL', 0, '2026-09-11 00:00:00.000000'),
  (2400051, 240005, 'SUBJECT_ID', 'STRING', 1, 'CONTEXT', 1, '2026-09-11 00:00:00.000000'),
  (2400052, 240005, 'TAG', 'STRING', 1, 'LITERAL', 0, '2026-09-11 00:00:00.000000');

-- RCO-19：空库初始化的统一变量目录；已有库使用需求目录的增量 SQL。
-- 17 个变量 + 9 个成员；不自动绑定或启用规则。
-- system_managed 定义来自已支持合同，演示变量不绑定/启用任何规则；不改历史行。
START TRANSACTION;
SET @rco19_time=UTC_TIMESTAMP(6);
INSERT INTO t_risk_variable (variable_id, variable_code, variable_name, description, source_type, value_type, point_code, source_contract_code, source_contract_version, source_field, rule_condition_flag, flow_condition_flag, action_parameter_flag, system_managed_flag, format_code, numeric_unit, currency_code, currency_variable_code, allowed_values_variable_code, string_value, number_value, boolean_value, datetime_value, duration_value, status, version, create_by, update_by, create_time, update_time) VALUES
  (900000000000091001, 'INPUT_COUNTRY_CODE', '国家/地区', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'country_code', 1, 1, 0, 1, 'UPPER_ALPHA_2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091002, 'INPUT_LOCALE', '语言地区', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'locale', 1, 1, 0, 1, 'BCP47', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091003, 'INPUT_INDUSTRY_CODE', '行业代码', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'industry_code', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091004, 'INPUT_MCC_CODE', 'MCC', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'mcc_code', 1, 1, 0, 1, 'DIGITS_4', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091005, 'INPUT_CURRENCY', '币种', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'currency', 1, 1, 0, 1, 'UPPER_ALPHA_3', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091006, 'INPUT_MERCHANDISE_AMOUNT', '商品金额', NULL, 'INPUT', 'NUMBER', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'merchandise_amount', 1, 1, 0, 1, 'NONE', 'MONEY', NULL, 'INPUT_CURRENCY', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091007, 'INPUT_DISCOUNT_AMOUNT', '折扣金额', NULL, 'INPUT', 'NUMBER', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'discount_amount', 1, 1, 0, 1, 'NONE', 'MONEY', NULL, 'INPUT_CURRENCY', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091008, 'INPUT_NET_AMOUNT', '实付金额', NULL, 'INPUT', 'NUMBER', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'net_amount', 1, 1, 0, 1, 'NONE', 'MONEY', NULL, 'INPUT_CURRENCY', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091009, 'INPUT_ENTITY_SOURCE_TYPE', '实体来源', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'entity_source_type', 1, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, 'ENTITY_SOURCE_VALUES', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091010, 'INPUT_CLIENT_ID', 'Client 标识', NULL, 'INPUT', 'STRING', 'CHECKOUT_PREPARE', 'ORDER_PREPARATION', 4, 'client_id', 0, 0, 1, 1, 'POSITIVE_ID', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091011, 'NODE_MATCHED', '本节点是否命中', NULL, 'NODE_OUTPUT', 'BOOLEAN', 'CHECKOUT_PREPARE', 'RULE_SET_NODE_RESULT', 1, 'matched', 0, 1, 0, 1, 'NONE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091012, 'NODE_DECISION', '本节点处理结果', NULL, 'NODE_OUTPUT', 'STRING', 'CHECKOUT_PREPARE', 'RULE_SET_NODE_RESULT', 1, 'decision', 0, 1, 0, 1, 'TOKEN', NULL, NULL, NULL, 'NODE_DECISION_VALUES', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091013, 'ENTITY_SOURCE_VALUES', '实体来源可选值', NULL, 'COLLECTION', 'STRING', NULL, NULL, NULL, NULL, 0, 0, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091014, 'NODE_DECISION_VALUES', '节点处理结果可选值', NULL, 'COLLECTION', 'STRING', 'CHECKOUT_PREPARE', NULL, NULL, NULL, 0, 0, 0, 1, 'TOKEN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091015, 'DEMO_AMOUNT_THRESHOLD', '示例金额阈值', NULL, 'CONSTANT', 'NUMBER', 'CHECKOUT_PREPARE', NULL, NULL, NULL, 1, 1, 0, 0, 'NONE', 'MONEY', NULL, 'INPUT_CURRENCY', NULL, NULL, '8000.00000000', NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091016, 'DEMO_STANDARD_CURRENCY', '示例标准币种', NULL, 'CONSTANT', 'STRING', 'CHECKOUT_PREPARE', NULL, NULL, NULL, 1, 1, 0, 0, 'UPPER_ALPHA_3', NULL, NULL, NULL, NULL, 'USD', NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000091017, 'RESTRICTED_COUNTRIES', '示例受限国家集合', NULL, 'COLLECTION', 'STRING', 'CHECKOUT_PREPARE', NULL, NULL, NULL, 1, 1, 0, 0, 'UPPER_ALPHA_2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 1, 1, 1, @rco19_time, @rco19_time);
INSERT INTO t_risk_variable_member (member_id, variable_code, value_key, string_value, number_value, boolean_value, datetime_value, duration_value, display_name, sort_order, create_time) VALUES
  (900000000000092001, 'ENTITY_SOURCE_VALUES', 'PLATFORM', 'PLATFORM', NULL, NULL, NULL, NULL, '平台提供', 1, @rco19_time),
  (900000000000092002, 'ENTITY_SOURCE_VALUES', 'CLIENT', 'CLIENT', NULL, NULL, NULL, NULL, 'Client 提供', 2, @rco19_time),
  (900000000000092003, 'ENTITY_SOURCE_VALUES', 'PARTNER', 'PARTNER', NULL, NULL, NULL, NULL, 'Partner 提供', 3, @rco19_time),
  (900000000000092004, 'NODE_DECISION_VALUES', 'ALLOW', 'ALLOW', NULL, NULL, NULL, NULL, 'PASS', 1, @rco19_time),
  (900000000000092005, 'NODE_DECISION_VALUES', 'REVIEW', 'REVIEW', NULL, NULL, NULL, NULL, 'REVIEW', 2, @rco19_time),
  (900000000000092006, 'NODE_DECISION_VALUES', 'DENY', 'DENY', NULL, NULL, NULL, NULL, 'REJECT', 3, @rco19_time),
  (900000000000092007, 'RESTRICTED_COUNTRIES', 'IR', 'IR', NULL, NULL, NULL, NULL, NULL, 1, @rco19_time),
  (900000000000092008, 'RESTRICTED_COUNTRIES', 'KP', 'KP', NULL, NULL, NULL, NULL, NULL, 2, @rco19_time),
  (900000000000092009, 'RESTRICTED_COUNTRIES', 'SY', 'SY', NULL, NULL, NULL, NULL, NULL, 3, @rco19_time);
COMMIT;

-- 2026-10-14 支付反欺诈一期：支付决策点、冻结点版本、支付终态动作、支付输入与节点输出系统变量（requirements/20261014/risk-支付反欺诈一期）。
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
