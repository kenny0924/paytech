-- 需求：requirements/20261016/risk-结账点运行期评估（阶段 7 mor-test 验收后执行）。目标库：mor-test mor_risk。
-- 作用：删除 mor-test 验收用合成订单 991017000000000001、991017000000000002 的结账评估族数据（评估、快照、Feature 结果、规则与流程轨迹）。
-- 不删除：结账默认放行基线（流程与执行发布）及其他任何评估。
-- 用法：先原样执行（末尾 ROLLBACK）核对行数，再改为 COMMIT 执行一次。

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_checkout_eval AS
SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id = 210001
  AND evaluation_request_id IN ('CHECKOUT_PREPARE:991017000000000001', 'CHECKOUT_PREPARE:991017000000000002');
SELECT COUNT(*) AS evaluations_selected FROM tmp_checkout_eval;

DELETE t FROM t_evaluation_flow_trace t JOIN tmp_checkout_eval a USING (evaluation_id);
SELECT ROW_COUNT() AS flow_traces;
DELETE t FROM t_evaluation_rule_trace t JOIN tmp_checkout_eval a USING (evaluation_id);
SELECT ROW_COUNT() AS rule_traces;
DELETE f FROM t_evaluation_feature_result f JOIN tmp_checkout_eval a USING (evaluation_id);
SELECT ROW_COUNT() AS feature_results;
DELETE s FROM t_checkout_input_snapshot s JOIN tmp_checkout_eval a USING (evaluation_id);
SELECT ROW_COUNT() AS snapshots;
DELETE ev FROM t_decision_evaluation ev JOIN tmp_checkout_eval a USING (evaluation_id);
SELECT ROW_COUNT() AS evaluations;

DROP TEMPORARY TABLE tmp_checkout_eval;

ROLLBACK;
