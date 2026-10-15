-- 需求：requirements/20261015/risk-评估结果查询与解释（阶段 7 验收后执行）。目标库：mor-test 的 mor_risk。
-- 作用：删除验收用的 3 个合成支付尝试（991015000000000001～003）产生的评估、快照、Feature 结果、轨迹与累计事件。
-- 用法：先原样执行（末尾 ROLLBACK）核对删除行数，与验收记录一致后把 ROLLBACK 改为 COMMIT 再执行一次。
-- 不触碰 Operations 的支付联调基线（发布、流程、规则集、变量、名单）与其他评估。

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_acceptance_assessment AS
SELECT assessment_id, evaluation_id FROM t_transaction_risk_assessment
WHERE payment_attempt_id IN (991015000000000001, 991015000000000002, 991015000000000003);

SELECT COUNT(*) AS assessments FROM tmp_acceptance_assessment;

DELETE s FROM t_velocity_event_subject s
JOIN t_velocity_event e ON e.velocity_event_id = s.velocity_event_id
JOIN tmp_acceptance_assessment a ON e.source_type = 'TRANSACTION_RISK_ASSESSMENT' AND e.source_id = a.assessment_id;
SELECT ROW_COUNT() AS velocity_subjects;

DELETE e FROM t_velocity_event e
JOIN tmp_acceptance_assessment a ON e.source_type = 'TRANSACTION_RISK_ASSESSMENT' AND e.source_id = a.assessment_id;
SELECT ROW_COUNT() AS velocity_events;

DELETE t FROM t_evaluation_flow_trace t JOIN tmp_acceptance_assessment a ON a.evaluation_id = t.evaluation_id;
SELECT ROW_COUNT() AS flow_traces;

DELETE t FROM t_evaluation_rule_trace t JOIN tmp_acceptance_assessment a ON a.evaluation_id = t.evaluation_id;
SELECT ROW_COUNT() AS rule_traces;

DELETE f FROM t_evaluation_feature_result f JOIN tmp_acceptance_assessment a ON a.evaluation_id = f.evaluation_id;
SELECT ROW_COUNT() AS feature_results;

DELETE ev FROM t_decision_evaluation ev JOIN tmp_acceptance_assessment a ON a.evaluation_id = ev.evaluation_id;
SELECT ROW_COUNT() AS evaluations;

DELETE x FROM t_transaction_risk_assessment x JOIN tmp_acceptance_assessment a ON a.assessment_id = x.assessment_id;
SELECT ROW_COUNT() AS assessments_deleted;

DROP TEMPORARY TABLE tmp_acceptance_assessment;

ROLLBACK;
