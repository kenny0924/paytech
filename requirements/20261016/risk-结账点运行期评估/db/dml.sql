-- 需求：requirements/20261016/risk-结账点运行期评估（阶段 3 待执行文件，未在任何环境执行）。目标库：Risk（mor_risk）。
-- 性质：增量 DML，一个事务；按 ID 带前置条件，重复执行时 UPDATE 命中 0 行、INSERT 因主键冲突失败（执行前先跑预检）。
-- 同一内容并入 risk-compliance-initial-data.sql（空库直接写成 v5 形态），与本文件二选一。
-- 回滚：91001、91003～91010 改回 source_contract_version=4（91001 source_field 改回 country_code，91010 rule/flow 标志改回 0）；
--       91002 改回 ACTIVE；删除 91018～91020 与 220001（须先确认无规则集版本、执行发布引用它们）。
-- 220001 的 content_hash 已于阶段 4 由 DecisionPointAuthoringDescriptor.checkoutPrepare()（v5，version_no=100）经 RiskDecisionCanonicalizerImpl 计算填入。

-- 预检（执行人确认后再继续）：
-- SELECT COUNT(*) FROM t_risk_variable WHERE variable_id BETWEEN 900000000000091001 AND 900000000000091010
--   AND point_code='CHECKOUT_PREPARE' AND source_contract_code='ORDER_PREPARATION' AND source_contract_version=4 AND system_managed_flag=1;  -- 期望 10
-- SELECT COUNT(*) FROM t_rule_operand WHERE variable_code='INPUT_LOCALE' OR field_code='locale';                                            -- 期望 0
-- SELECT COUNT(*) FROM t_rule_action_parameter WHERE variable_code='INPUT_LOCALE';                                                          -- 期望 0
-- SELECT COUNT(*) FROM t_decision_flow_edge WHERE condition_json LIKE '%INPUT_LOCALE%' OR condition_json LIKE '%"locale"%';                 -- 期望 0
-- SELECT COUNT(*) FROM t_risk_variable WHERE variable_id IN (900000000000091018,900000000000091019,900000000000091020)
--   OR variable_code IN ('INPUT_ENTITY_NO','INPUT_LOCAL_PARTNER_ID','INPUT_LINE_COUNT');                                                     -- 期望 0
-- SELECT COUNT(*) FROM t_decision_point_version WHERE point_version_id=220001 OR (decision_point_id=210001 AND version_no=100);             -- 期望 0

START TRANSACTION;
SET @t = UTC_TIMESTAMP(6);

-- 1. 现有结账输入变量迁到 v5（91005～91008 必须同批：MONEY 变量的币种变量须同合同版本）
UPDATE t_risk_variable SET source_contract_version=5, source_field='consumer_country_code', version=version+1, update_time=@t
 WHERE variable_id=900000000000091001 AND source_contract_version=4 AND source_field='country_code';
UPDATE t_risk_variable SET source_contract_version=5, version=version+1, update_time=@t
 WHERE variable_id IN (900000000000091003,900000000000091004,900000000000091005,900000000000091006,
                       900000000000091007,900000000000091008,900000000000091009)
   AND source_contract_code='ORDER_PREPARATION' AND source_contract_version=4;
UPDATE t_risk_variable SET source_contract_version=5, rule_condition_flag=1, flow_condition_flag=1, version=version+1, update_time=@t
 WHERE variable_id=900000000000091010 AND source_contract_version=4 AND source_field='client_id';

-- 2. locale 停用，留在 v4 供旧数据读取
UPDATE t_risk_variable SET status='DISABLED', version=version+1, update_time=@t
 WHERE variable_id=900000000000091002 AND variable_code='INPUT_LOCALE' AND status='ACTIVE';

-- 3. v5 新增字段的系统变量
INSERT INTO t_risk_variable (variable_id, variable_code, variable_name, description, source_type, value_type, point_code,
  source_contract_code, source_contract_version, source_field, rule_condition_flag, flow_condition_flag, action_parameter_flag,
  system_managed_flag, format_code, numeric_unit, currency_code, currency_variable_code, allowed_values_variable_code,
  string_value, number_value, boolean_value, datetime_value, duration_value, status, version, create_by, update_by, create_time, update_time) VALUES
 (900000000000091018,'INPUT_ENTITY_NO','经营主体编号',NULL,'INPUT','STRING','CHECKOUT_PREPARE','ORDER_PREPARATION',5,'entity_no',1,1,0,1,'TEXT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'ACTIVE',1,1,1,@t,@t),
 (900000000000091019,'INPUT_LOCAL_PARTNER_ID','Local Partner 标识',NULL,'INPUT','STRING','CHECKOUT_PREPARE','ORDER_PREPARATION',5,'local_partner_id',1,1,0,1,'POSITIVE_ID',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'ACTIVE',1,1,1,@t,@t),
 (900000000000091020,'INPUT_LINE_COUNT','明细行数',NULL,'INPUT','NUMBER','CHECKOUT_PREPARE','ORDER_PREPARATION',5,'line_count',1,1,0,1,'NONE','SCALAR',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'ACTIVE',1,1,1,@t,@t);

-- 4. 结账运行期 FROZEN 点版本（执行发布引用；version_no=100 避开 mor-test 旧 companion 版本 1～33）
INSERT INTO t_decision_point_version (point_version_id, decision_point_id, version_no, input_contract_code, input_contract_version,
  output_contract_code, default_decision, evaluation_strategy, failure_strategy, timeout_ms, status, content_hash, create_time, update_time)
VALUES (220001, 210001, 100, 'ORDER_PREPARATION', 5, 'RISK_DECISION_RESULT', 'REVIEW', 'FIRST_TERMINAL', 'FAIL_CLOSED', 500, 'FROZEN',
  '7f730e195b2c465db93671fbba3c8fb51e596c9b82256f7098a5416c59b04a45', '2026-10-16 00:00:00.000000', '2026-10-16 00:00:00.000000');

-- 核对后再 COMMIT：UPDATE 命中 1+7+1+1 行、INSERT 3+1 行
-- COMMIT;
