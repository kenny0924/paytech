-- 需求：requirements/20261014/risk-支付反欺诈一期 阶段 7 验收数据清理。目标库：mor-test 的 mor_risk。
-- 范围：只删除支付决策点（210002）下验收经 HTTP 创建的配置与运行数据，以及编码前缀为 ACCEPT_REASON_ 的验收拒绝原因；
--       不触碰结账决策点（210001）、尽调与争议数据，不删除 dml.sql 初始化的支付变量（900000000000093001～093013）与动作、点版本。
-- 前置：发布前核对过以下表在 test 为 0 行或为本次新增表：t_transaction_risk_assessment、t_velocity_event(_subject)、
--       t_release_shadow_run、t_feature_version、t_velocity/list_feature_definition、t_risk_list(_entry)、
--       DECISION_SHADOW_RUN 补偿任务；验收窗口内不存在其他支付数据写入方。执行前先跑第 0 段核对。
-- 执行：mysql 客户端 --default-character-set=utf8mb4；先 ROLLBACK 试运行核对行数，再以 COMMIT 执行。

-- 0. 核对（只读）：记录将被删除的行数；与验收证据中的创建数一致后再执行。
SELECT (SELECT COUNT(*) FROM t_decision_evaluation WHERE decision_point_id = 210002) payment_evaluations,
  (SELECT COUNT(*) FROM t_velocity_event) velocity_events, (SELECT COUNT(*) FROM t_risk_list) lists,
  (SELECT COUNT(*) FROM t_decision_release WHERE decision_point_id = 210002) payment_releases,
  (SELECT COUNT(*) FROM t_rule_set WHERE decision_point_id = 210002) payment_rule_sets,
  (SELECT COUNT(*) FROM t_rule_reject_reason WHERE reason_code LIKE 'ACCEPT\_REASON\_%') acceptance_reasons;

START TRANSACTION;
DELETE FROM t_evaluation_feature_result WHERE evaluation_id IN (SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id = 210002);
DELETE FROM t_evaluation_rule_trace WHERE evaluation_id IN (SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id = 210002);
DELETE FROM t_transaction_risk_assessment;
DELETE FROM t_decision_evaluation WHERE decision_point_id = 210002;
DELETE FROM t_velocity_event_subject;
DELETE FROM t_velocity_event;
DELETE FROM t_release_shadow_run;
DELETE FROM t_compensation_event WHERE event_type = 'DECISION_SHADOW_RUN';
DELETE FROM t_release_audit_event WHERE release_id IN (SELECT release_id FROM t_decision_release WHERE decision_point_id = 210002);
DELETE FROM t_decision_release WHERE decision_point_id = 210002;
DELETE FROM t_decision_flow_end_output WHERE flow_version_id IN (SELECT v.flow_version_id FROM t_decision_flow_version v
  JOIN t_decision_flow f ON f.flow_id = v.flow_id WHERE f.decision_point_id = 210002);
DELETE FROM t_decision_flow_edge WHERE flow_version_id IN (SELECT v.flow_version_id FROM t_decision_flow_version v
  JOIN t_decision_flow f ON f.flow_id = v.flow_id WHERE f.decision_point_id = 210002);
DELETE FROM t_decision_flow_node WHERE flow_version_id IN (SELECT v.flow_version_id FROM t_decision_flow_version v
  JOIN t_decision_flow f ON f.flow_id = v.flow_id WHERE f.decision_point_id = 210002);
DELETE FROM t_decision_flow_version WHERE flow_id IN (SELECT flow_id FROM t_decision_flow WHERE decision_point_id = 210002);
DELETE FROM t_decision_flow WHERE decision_point_id = 210002;
DELETE FROM t_rule_action_parameter WHERE rule_action_id IN (SELECT rule_action_id FROM t_rule_action WHERE rule_id IN
  (SELECT r.rule_id FROM t_risk_rule r JOIN t_rule_set_version v ON v.rule_set_version_id = r.rule_set_version_id WHERE v.decision_point_id = 210002));
DELETE FROM t_rule_action WHERE rule_id IN (SELECT r.rule_id FROM t_risk_rule r JOIN t_rule_set_version v
  ON v.rule_set_version_id = r.rule_set_version_id WHERE v.decision_point_id = 210002);
DELETE FROM t_rule_operand WHERE node_id IN (SELECT node_id FROM t_rule_node WHERE rule_id IN (SELECT r.rule_id FROM t_risk_rule r
  JOIN t_rule_set_version v ON v.rule_set_version_id = r.rule_set_version_id WHERE v.decision_point_id = 210002));
DELETE FROM t_rule_node WHERE rule_id IN (SELECT r.rule_id FROM t_risk_rule r JOIN t_rule_set_version v
  ON v.rule_set_version_id = r.rule_set_version_id WHERE v.decision_point_id = 210002);
DELETE FROM t_risk_rule WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM t_rule_set_version WHERE decision_point_id = 210002);
DELETE FROM t_rule_set_version WHERE decision_point_id = 210002;
DELETE FROM t_rule_set WHERE decision_point_id = 210002;
DELETE FROM t_risk_variable_member WHERE variable_code IN (SELECT variable_code FROM t_risk_variable
  WHERE point_code = 'PAYMENT_ATTEMPT_RISK' AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013);
DELETE FROM t_risk_variable WHERE point_code = 'PAYMENT_ATTEMPT_RISK' AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013;
DELETE FROM t_velocity_feature_definition;
DELETE FROM t_list_feature_definition;
DELETE FROM t_feature_version;
DELETE FROM t_risk_list_entry;
DELETE FROM t_risk_list;
DELETE FROM t_rule_reject_reason WHERE reason_code LIKE 'ACCEPT\_REASON\_%';
-- 核对后二选一：ROLLBACK;（试运行）或 COMMIT;
