-- 需求：requirements/20261016/risk-结账点运行期评估（阶段 5 本机验收后执行）。目标库：本机 mor_risk。
-- 作用：删除本机验收产生的全部结账评估族数据（正式与影子评估、快照、Feature 结果、规则与流程轨迹）。
-- 依据：迁移前本机 mor_risk 没有任何结账评估（t_checkout_input_snapshot 0 行），所以 decision_point_id=210001 的评估都来自本次验收。
-- 不删除：结账执行发布、流程、规则集、拒绝原因（配置历史保留；生效发布为默认放行基线）。
-- 用法：先原样执行（末尾 ROLLBACK）核对行数，再改为 COMMIT 执行一次。

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_checkout_eval AS
SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id = 210001;
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
