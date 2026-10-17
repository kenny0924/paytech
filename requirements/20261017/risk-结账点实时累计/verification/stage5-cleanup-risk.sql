-- 需求：requirements/20261017/risk-结账点实时累计（阶段 5 本机验收后执行）。目标库：本机 mor_risk。
-- 作用：删除本机验收产生的全部数据：
--   1. 结账评估族（正式与影子评估、快照、Feature 结果、规则与流程轨迹）——验收前本机结账评估为 0 行；
--   2. 结账累计事件与主体（source_type = CHECKOUT_INPUT_SNAPSHOT）——验收前累计事件为 0 行；
--   3. STAGE5V_ 前缀的变量、Feature 版本与定义、名单与成员、规则集、流程，以及引用 STAGE5V_ 流程的执行发布、其影子批次与发布审计事件。
-- 保留：默认放行基线流程 CHECKOUT_BASELINE_ALLOW 及引用它的执行发布（ACTIVE + SUPERSEDED）与影子批次；拒绝原因 CHECKOUT_STAGE5_HIGH_NET。
-- 用法：先原样执行（末尾 ROLLBACK）核对行数，再把末行改为 COMMIT 执行一次。

START TRANSACTION;

-- 1. 结账评估族
CREATE TEMPORARY TABLE tmp_eval AS SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id = 210001;
SELECT 'evaluations_selected', COUNT(*) FROM tmp_eval;
SELECT 'action_intents', COUNT(*) FROM t_evaluation_action_intent t JOIN tmp_eval a USING (evaluation_id);
SELECT 'action_effects', COUNT(*) FROM t_action_effect t JOIN tmp_eval a USING (evaluation_id);
DELETE t FROM t_evaluation_flow_trace t JOIN tmp_eval a USING (evaluation_id); SELECT 'flow_traces', ROW_COUNT();
DELETE t FROM t_evaluation_rule_trace t JOIN tmp_eval a USING (evaluation_id); SELECT 'rule_traces', ROW_COUNT();
DELETE t FROM t_evaluation_feature_result t JOIN tmp_eval a USING (evaluation_id); SELECT 'feature_results', ROW_COUNT();
DELETE t FROM t_checkout_input_snapshot t JOIN tmp_eval a USING (evaluation_id); SELECT 'snapshots', ROW_COUNT();
DELETE t FROM t_decision_evaluation t JOIN tmp_eval a USING (evaluation_id); SELECT 'evaluations', ROW_COUNT();

-- 2. 结账累计事件
DELETE s FROM t_velocity_event_subject s JOIN t_velocity_event e ON e.velocity_event_id = s.velocity_event_id
  WHERE e.source_type = 'CHECKOUT_INPUT_SNAPSHOT'; SELECT 'event_subjects', ROW_COUNT();
DELETE FROM t_velocity_event WHERE source_type = 'CHECKOUT_INPUT_SNAPSHOT'; SELECT 'events', ROW_COUNT();

-- 3. 引用 STAGE5V_ 流程的执行发布、影子批次与审计事件
CREATE TEMPORARY TABLE tmp_fv AS SELECT v.flow_version_id FROM t_decision_flow_version v JOIN t_decision_flow f ON f.flow_id = v.flow_id
  WHERE f.flow_code LIKE 'STAGE5V\_%';
CREATE TEMPORARY TABLE tmp_rel AS SELECT release_id FROM t_decision_release WHERE decision_flow_version_id IN (SELECT flow_version_id FROM tmp_fv);
-- MySQL 临时表在同一语句中只能引用一次，分两次收集影子批次。
CREATE TEMPORARY TABLE tmp_run AS SELECT shadow_run_id FROM t_release_shadow_run
  WHERE candidate_release_id IN (SELECT release_id FROM tmp_rel);
INSERT INTO tmp_run SELECT shadow_run_id FROM t_release_shadow_run
  WHERE baseline_release_id IN (SELECT release_id FROM tmp_rel) AND candidate_release_id NOT IN (SELECT release_id FROM t_decision_release WHERE decision_flow_version_id IN (SELECT flow_version_id FROM tmp_fv));
SELECT 'releases_selected', COUNT(*) FROM tmp_rel;
DELETE FROM t_release_audit_event WHERE release_id IN (SELECT release_id FROM tmp_rel) OR shadow_run_id IN (SELECT shadow_run_id FROM tmp_run);
SELECT 'release_audit_events', ROW_COUNT();
DELETE FROM t_release_shadow_run WHERE shadow_run_id IN (SELECT shadow_run_id FROM tmp_run); SELECT 'shadow_runs', ROW_COUNT();
DELETE FROM t_decision_release WHERE release_id IN (SELECT release_id FROM tmp_rel); SELECT 'releases', ROW_COUNT();

-- 4. STAGE5V_ 流程
DELETE FROM t_decision_flow_edge WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_edges', ROW_COUNT();
DELETE FROM t_decision_flow_node WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_nodes', ROW_COUNT();
DELETE FROM t_decision_flow_end_output WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_end_outputs', ROW_COUNT();
DELETE FROM t_decision_flow_version WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_versions', ROW_COUNT();
DELETE FROM t_decision_flow WHERE flow_code LIKE 'STAGE5V\_%'; SELECT 'flows', ROW_COUNT();

-- 5. STAGE5V_ 规则集
CREATE TEMPORARY TABLE tmp_rsv AS SELECT rule_set_version_id FROM t_rule_set_version WHERE decision_point_id = 210001 AND rule_set_code LIKE 'STAGE5V\_%';
CREATE TEMPORARY TABLE tmp_rule AS SELECT rule_id FROM t_risk_rule WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM tmp_rsv);
SELECT 'rule_set_bindings', COUNT(*) FROM t_decision_point_rule_set WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM tmp_rsv);
DELETE FROM t_rule_action_parameter WHERE rule_action_id IN (SELECT rule_action_id FROM t_rule_action WHERE rule_id IN (SELECT rule_id FROM tmp_rule));
SELECT 'rule_action_params', ROW_COUNT();
DELETE FROM t_rule_action WHERE rule_id IN (SELECT rule_id FROM tmp_rule); SELECT 'rule_actions', ROW_COUNT();
DELETE FROM t_rule_operand WHERE node_id IN (SELECT node_id FROM t_rule_node WHERE rule_id IN (SELECT rule_id FROM tmp_rule)); SELECT 'rule_operands', ROW_COUNT();
DELETE FROM t_rule_node WHERE rule_id IN (SELECT rule_id FROM tmp_rule); SELECT 'rule_nodes', ROW_COUNT();
DELETE FROM t_risk_rule WHERE rule_id IN (SELECT rule_id FROM tmp_rule); SELECT 'rules', ROW_COUNT();
DELETE FROM t_rule_set_version WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM tmp_rsv); SELECT 'rule_set_versions', ROW_COUNT();
DELETE FROM t_rule_set WHERE decision_point_id = 210001 AND rule_set_code LIKE 'STAGE5V\_%'; SELECT 'rule_sets', ROW_COUNT();

-- 6. STAGE5V_ 变量、Feature 与名单
CREATE TEMPORARY TABLE tmp_feat AS SELECT feature_version_id FROM t_risk_variable WHERE variable_code LIKE 'STAGE5V\_%' AND feature_version_id IS NOT NULL;
SELECT 'point_features', COUNT(*) FROM t_decision_point_feature WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat);
SELECT 'feature_dependencies', COUNT(*) FROM t_feature_dependency WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat);
SELECT 'variable_members', COUNT(*) FROM t_risk_variable_member WHERE variable_code LIKE 'STAGE5V\_%';
DELETE FROM t_risk_variable WHERE variable_code LIKE 'STAGE5V\_%'; SELECT 'variables', ROW_COUNT();
DELETE FROM t_velocity_feature_definition WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat); SELECT 'velocity_definitions', ROW_COUNT();
DELETE FROM t_list_feature_definition WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat); SELECT 'list_definitions', ROW_COUNT();
DELETE FROM t_feature_version WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat); SELECT 'feature_versions', ROW_COUNT();
DELETE e FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id WHERE l.list_code LIKE 'STAGE5V\_%'; SELECT 'list_entries', ROW_COUNT();
DELETE FROM t_risk_list WHERE list_code LIKE 'STAGE5V\_%'; SELECT 'lists', ROW_COUNT();

-- 核对：剩余结账评估、累计事件、STAGE5V_ 配置均为 0；生效发布为默认放行基线
SELECT 'remain', (SELECT COUNT(*) FROM t_decision_evaluation WHERE decision_point_id = 210001),
  (SELECT COUNT(*) FROM t_velocity_event), (SELECT COUNT(*) FROM t_risk_variable WHERE variable_code LIKE 'STAGE5V\_%'),
  (SELECT COUNT(*) FROM t_risk_list WHERE list_code LIKE 'STAGE5V\_%'), (SELECT COUNT(*) FROM t_feature_version),
  (SELECT COUNT(*) FROM t_decision_flow), (SELECT COUNT(*) FROM t_rule_set);
SELECT 'active', r.release_id, f.flow_code FROM t_decision_release r JOIN t_decision_flow_version v ON v.flow_version_id = r.decision_flow_version_id
  JOIN t_decision_flow f ON f.flow_id = v.flow_id WHERE r.decision_point_id = 210001 AND r.status = 'ACTIVE';

ROLLBACK;
