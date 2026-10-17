-- 需求：requirements/20261017/risk-结账点实时累计（阶段 7 mor-test 验收后执行）。目标库：mor-test mor_risk。
-- 作用（D1：验收数据不保留）：
--   1. 合成结账评估族：业务身份 991017100000000001～099（evaluation_request_id = CHECKOUT_PREPARE:99101710000000000xx）的正式评估，
--      以及本次验收影子批次产生的影子评估；它们的快照、Feature 结果、规则与流程轨迹；
--   2. 上述合成订单快照的累计事件与主体（source_type = CHECKOUT_INPUT_SNAPSHOT 且 source_id 为这些快照 ID）；
--   3. STAGE7V_ 前缀的变量、Feature、名单、规则集、流程、拒绝原因，引用 STAGE7V_ 流程的执行发布、本次验收的影子批次与发布审计事件。
-- 保留：Commerce 6 笔订单的结账评估与快照（以及窗口内 Commerce 新建订单的评估与事件）；支付全部数据；
--      基线流程 CHECKOUT_BASELINE_ALLOW 与其全部执行发布（原基线 236943228086095872 SUPERSEDED、切回时新建的同版本基线 ACTIVE）。
-- 影子批次：删除候选为 STAGE7V_ 发布的批次，以及以 STAGE7V_ 发布为对照基线的批次（切回基线时产生，保留会成为悬空引用）。
-- 用法：先原样执行（末尾 ROLLBACK）核对行数，再把末行改为 COMMIT 执行一次。

START TRANSACTION;

-- 0. 选取范围
CREATE TEMPORARY TABLE tmp_fv AS SELECT v.flow_version_id FROM t_decision_flow_version v JOIN t_decision_flow f ON f.flow_id = v.flow_id
  WHERE f.flow_code LIKE 'STAGE7V\_%';
CREATE TEMPORARY TABLE tmp_rel AS SELECT release_id FROM t_decision_release WHERE decision_flow_version_id IN (SELECT flow_version_id FROM tmp_fv);
CREATE TEMPORARY TABLE tmp_rel2 AS SELECT release_id FROM tmp_rel;
CREATE TEMPORARY TABLE tmp_run AS SELECT shadow_run_id FROM t_release_shadow_run
  WHERE candidate_release_id IN (SELECT release_id FROM tmp_rel) OR baseline_release_id IN (SELECT release_id FROM tmp_rel2);
CREATE TEMPORARY TABLE tmp_eval AS SELECT evaluation_id FROM t_decision_evaluation
  WHERE decision_point_id = 210001 AND evaluation_mode = 'PRIMARY' AND evaluation_request_id LIKE 'CHECKOUT\_PREPARE:9910171000000000%';
INSERT INTO tmp_eval SELECT evaluation_id FROM t_decision_evaluation WHERE shadow_run_id IN (SELECT shadow_run_id FROM tmp_run);
CREATE TEMPORARY TABLE tmp_snap AS SELECT snapshot_id FROM t_checkout_input_snapshot WHERE order_id BETWEEN 991017100000000001 AND 991017100000000099;
SELECT 'releases_selected', COUNT(*) FROM tmp_rel;
SELECT 'shadow_runs_selected', COUNT(*) FROM tmp_run;
SELECT 'evaluations_selected', COUNT(*) FROM tmp_eval;
SELECT 'snapshots_selected', COUNT(*) FROM tmp_snap;
SELECT 'action_intents', COUNT(*) FROM t_evaluation_action_intent t JOIN tmp_eval a USING (evaluation_id);
SELECT 'action_effects', COUNT(*) FROM t_action_effect t JOIN tmp_eval a USING (evaluation_id);

-- 1. 合成评估族
DELETE t FROM t_evaluation_flow_trace t JOIN tmp_eval a USING (evaluation_id); SELECT 'flow_traces', ROW_COUNT();
DELETE t FROM t_evaluation_rule_trace t JOIN tmp_eval a USING (evaluation_id); SELECT 'rule_traces', ROW_COUNT();
DELETE t FROM t_evaluation_feature_result t JOIN tmp_eval a USING (evaluation_id); SELECT 'feature_results', ROW_COUNT();

-- 2. 合成订单的累计事件（先删主体再删事件）
DELETE s FROM t_velocity_event_subject s JOIN t_velocity_event e ON e.velocity_event_id = s.velocity_event_id
  JOIN tmp_snap n ON n.snapshot_id = e.source_id WHERE e.source_type = 'CHECKOUT_INPUT_SNAPSHOT'; SELECT 'event_subjects', ROW_COUNT();
DELETE e FROM t_velocity_event e JOIN tmp_snap n ON n.snapshot_id = e.source_id WHERE e.source_type = 'CHECKOUT_INPUT_SNAPSHOT'; SELECT 'events', ROW_COUNT();

DELETE t FROM t_checkout_input_snapshot t JOIN tmp_snap n USING (snapshot_id); SELECT 'snapshots', ROW_COUNT();
DELETE t FROM t_decision_evaluation t JOIN tmp_eval a USING (evaluation_id); SELECT 'evaluations', ROW_COUNT();

-- 3. 执行发布、影子批次与审计事件
DELETE FROM t_release_audit_event WHERE release_id IN (SELECT release_id FROM tmp_rel) OR shadow_run_id IN (SELECT shadow_run_id FROM tmp_run);
SELECT 'release_audit_events', ROW_COUNT();
DELETE FROM t_release_shadow_run WHERE shadow_run_id IN (SELECT shadow_run_id FROM tmp_run); SELECT 'shadow_runs', ROW_COUNT();
DELETE FROM t_decision_release WHERE release_id IN (SELECT release_id FROM tmp_rel); SELECT 'releases', ROW_COUNT();

-- 4. STAGE7V_ 流程
DELETE FROM t_decision_flow_edge WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_edges', ROW_COUNT();
DELETE FROM t_decision_flow_node WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_nodes', ROW_COUNT();
DELETE FROM t_decision_flow_end_output WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_end_outputs', ROW_COUNT();
DELETE FROM t_decision_flow_version WHERE flow_version_id IN (SELECT flow_version_id FROM tmp_fv); SELECT 'flow_versions', ROW_COUNT();
DELETE FROM t_decision_flow WHERE flow_code LIKE 'STAGE7V\_%'; SELECT 'flows', ROW_COUNT();

-- 5. STAGE7V_ 规则集
CREATE TEMPORARY TABLE tmp_rsv AS SELECT rule_set_version_id FROM t_rule_set_version WHERE decision_point_id = 210001 AND rule_set_code LIKE 'STAGE7V\_%';
CREATE TEMPORARY TABLE tmp_rule AS SELECT rule_id FROM t_risk_rule WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM tmp_rsv);
SELECT 'rule_set_bindings', COUNT(*) FROM t_decision_point_rule_set WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM tmp_rsv);
DELETE FROM t_rule_action_parameter WHERE rule_action_id IN (SELECT rule_action_id FROM t_rule_action WHERE rule_id IN (SELECT rule_id FROM tmp_rule));
SELECT 'rule_action_params', ROW_COUNT();
DELETE FROM t_rule_action WHERE rule_id IN (SELECT rule_id FROM tmp_rule); SELECT 'rule_actions', ROW_COUNT();
DELETE FROM t_rule_operand WHERE node_id IN (SELECT node_id FROM t_rule_node WHERE rule_id IN (SELECT rule_id FROM tmp_rule)); SELECT 'rule_operands', ROW_COUNT();
DELETE FROM t_rule_node WHERE rule_id IN (SELECT rule_id FROM tmp_rule); SELECT 'rule_nodes', ROW_COUNT();
DELETE FROM t_risk_rule WHERE rule_id IN (SELECT rule_id FROM tmp_rule); SELECT 'rules', ROW_COUNT();
DELETE FROM t_rule_set_version WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM tmp_rsv); SELECT 'rule_set_versions', ROW_COUNT();
DELETE FROM t_rule_set WHERE decision_point_id = 210001 AND rule_set_code LIKE 'STAGE7V\_%'; SELECT 'rule_sets', ROW_COUNT();
DELETE FROM t_rule_reject_reason WHERE reason_code LIKE 'STAGE7V\_%'; SELECT 'reject_reasons', ROW_COUNT();

-- 6. STAGE7V_ 变量、Feature 与名单
CREATE TEMPORARY TABLE tmp_feat AS SELECT feature_version_id FROM t_risk_variable WHERE variable_code LIKE 'STAGE7V\_%' AND feature_version_id IS NOT NULL;
SELECT 'point_features', COUNT(*) FROM t_decision_point_feature WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat);
SELECT 'feature_dependencies', COUNT(*) FROM t_feature_dependency WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat);
SELECT 'variable_members', COUNT(*) FROM t_risk_variable_member WHERE variable_code LIKE 'STAGE7V\_%';
DELETE FROM t_risk_variable WHERE variable_code LIKE 'STAGE7V\_%'; SELECT 'variables', ROW_COUNT();
DELETE FROM t_velocity_feature_definition WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat); SELECT 'velocity_definitions', ROW_COUNT();
DELETE FROM t_list_feature_definition WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat); SELECT 'list_definitions', ROW_COUNT();
DELETE FROM t_feature_version WHERE feature_version_id IN (SELECT feature_version_id FROM tmp_feat); SELECT 'feature_versions', ROW_COUNT();
DELETE e FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id WHERE l.list_code LIKE 'STAGE7V\_%'; SELECT 'list_entries', ROW_COUNT();
DELETE FROM t_risk_list WHERE list_code LIKE 'STAGE7V\_%'; SELECT 'lists', ROW_COUNT();

-- 核对：合成评估、STAGE7V_ 配置、结账 Feature 变量均为 0；支付与 Commerce 数据未动；生效发布为两条基线
SELECT 'remain_synthetic', (SELECT COUNT(*) FROM t_decision_evaluation WHERE evaluation_request_id LIKE 'CHECKOUT\_PREPARE:9910171000000000%'),
  (SELECT COUNT(*) FROM t_checkout_input_snapshot WHERE order_id BETWEEN 991017100000000001 AND 991017100000000099),
  (SELECT COUNT(*) FROM t_risk_variable WHERE variable_code LIKE 'STAGE7V\_%'),
  (SELECT COUNT(*) FROM t_risk_list WHERE list_code LIKE 'STAGE7V\_%'),
  (SELECT COUNT(*) FROM t_risk_variable WHERE point_code = 'CHECKOUT_PREPARE' AND source_type IN ('VELOCITY', 'LIST'));
SELECT 'payment', (SELECT COUNT(*) FROM t_velocity_event WHERE source_type = 'TRANSACTION_RISK_ASSESSMENT'), (SELECT COUNT(*) FROM t_feature_version),
  (SELECT COUNT(*) FROM t_decision_evaluation WHERE decision_point_id = 210002);
SELECT 'checkout_primary_left', COUNT(*) FROM t_decision_evaluation WHERE decision_point_id = 210001 AND evaluation_mode = 'PRIMARY';
SELECT 'active', r.release_id, r.decision_point_id, f.flow_code FROM t_decision_release r JOIN t_decision_flow_version v ON v.flow_version_id = r.decision_flow_version_id
  JOIN t_decision_flow f ON f.flow_id = v.flow_id WHERE r.status = 'ACTIVE';

ROLLBACK;
