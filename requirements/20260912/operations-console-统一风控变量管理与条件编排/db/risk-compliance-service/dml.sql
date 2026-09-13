-- RCO-19 阶段 3 评审：17 个变量 + 9 个集合成员，仅准备注册数据；未执行。
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
