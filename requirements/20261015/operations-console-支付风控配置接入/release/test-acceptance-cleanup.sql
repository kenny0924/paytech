-- Operations 接入支付风控配置与执行发布：mor-test 合成验收数据清理（阶段 6 评审稿，阶段 7 经授权执行）
-- 目标库：mor-test 的 mor_risk（risk_app）；用 mysql --default-character-set=utf8mb4 执行。
-- 时机：合成验收（stage5_api.py / stage5_browser.py，E2E_TARGET=test）之后、配置联调基线（release.md 第 5 节）之前。
-- 范围：支付决策点（210002）下验收经页面/接口创建的配置与运行数据、编码以 S5_ 开头的变量与拒绝原因。
--       不触碰结账决策点（210001）的规则集、流程、旧候选发布，不删除 Risk dml.sql 初始化的 13 个支付变量（900000000000093001～093013）。
-- 前置（2026-10-15 只读核对）：test 中以下数据为 0 —— 支付点评估、速度事件、名单与成员、影子批次、特征版本、
--       支付点规则集/流程/发布、DECISION_SHADOW_RUN 补偿任务；验收窗口内 Payment 未部署，没有其他支付数据写入方。
--       执行前先跑第 0 段，确认这些数据全部来自本次验收，再以 ROLLBACK 试运行核对影响行数，最后 COMMIT。
-- 依据：沿用 Risk 支付反欺诈一期的已执行清理（requirements/20261014/risk-支付反欺诈一期/release/test-acceptance-cleanup.sql），
--       增加 S5_ 前缀的变量与拒绝原因。

-- 0. 核对（只读）：与验收证据中的创建数一致后再执行。
SELECT (SELECT COUNT(*) FROM t_decision_evaluation WHERE decision_point_id = 210002) payment_evaluations,
  (SELECT COUNT(*) FROM t_velocity_event) velocity_events, (SELECT COUNT(*) FROM t_risk_list) lists,
  (SELECT COUNT(*) FROM t_risk_list_entry) list_entries,
  (SELECT COUNT(*) FROM t_decision_release WHERE decision_point_id = 210002) payment_releases,
  (SELECT COUNT(*) FROM t_decision_flow WHERE decision_point_id = 210002) payment_flows,
  (SELECT COUNT(*) FROM t_rule_set WHERE decision_point_id = 210002) payment_rule_sets,
  (SELECT COUNT(*) FROM t_risk_variable WHERE variable_code LIKE 'S5\_%'
     OR (point_code = 'PAYMENT_ATTEMPT_RISK' AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013)) acceptance_variables,
  (SELECT COUNT(*) FROM t_rule_reject_reason WHERE reason_code LIKE 'S5\_%') acceptance_reasons;

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
DELETE FROM t_risk_variable_member WHERE variable_code IN (SELECT variable_code FROM t_risk_variable WHERE variable_code LIKE 'S5\_%'
  OR (point_code = 'PAYMENT_ATTEMPT_RISK' AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013));
DELETE FROM t_risk_variable WHERE variable_code LIKE 'S5\_%'
  OR (point_code = 'PAYMENT_ATTEMPT_RISK' AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013);
DELETE FROM t_velocity_feature_definition;
DELETE FROM t_list_feature_definition;
DELETE FROM t_feature_version;
DELETE FROM t_risk_list_entry;
DELETE FROM t_risk_list;
DELETE FROM t_rule_reject_reason WHERE reason_code LIKE 'S5\_%';
-- 核对后二选一：ROLLBACK;（试运行）或 COMMIT;
