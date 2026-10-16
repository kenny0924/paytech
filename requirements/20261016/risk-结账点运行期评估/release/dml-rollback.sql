-- 需求：requirements/20261016/risk-结账点运行期评估。目标库：mor-test mor_risk。仅在需要换回旧镜像时执行（见 release.md 第 7 节）。
-- 前提（逐条预检，任一非 0 则不能回滚 DML，只能保持新镜像修复）：
-- SELECT COUNT(*) FROM t_decision_release WHERE point_version_id=220001;                                   -- 结账执行发布
-- SELECT COUNT(*) FROM t_rule_operand WHERE variable_code IN ('INPUT_ENTITY_NO','INPUT_LOCAL_PARTNER_ID','INPUT_LINE_COUNT');
-- SELECT COUNT(*) FROM t_decision_evaluation WHERE point_version_id=220001;                                -- 结账评估（先按验收清理 SQL 清掉）
-- SELECT (SELECT COUNT(*) FROM t_rule_set_version v JOIN t_decision_point p USING (decision_point_id) WHERE p.point_code='CHECKOUT_PREPARE' AND v.input_contract_version=5)
--      + (SELECT COUNT(*) FROM t_decision_flow_version WHERE input_contract_version=5);                    -- v5 规则集/流程版本
-- 用法：先原样执行（末尾 ROLLBACK）核对行数：UPDATE 1+7+1+1、DELETE 3+1，再改为 COMMIT。

START TRANSACTION;
SET @t = UTC_TIMESTAMP(6);
UPDATE t_risk_variable SET source_contract_version=4, source_field='country_code', version=version+1, update_time=@t
 WHERE variable_id=900000000000091001 AND source_contract_version=5 AND source_field='consumer_country_code';
SELECT ROW_COUNT() AS v91001;
UPDATE t_risk_variable SET source_contract_version=4, version=version+1, update_time=@t
 WHERE variable_id IN (900000000000091003,900000000000091004,900000000000091005,900000000000091006,
                       900000000000091007,900000000000091008,900000000000091009)
   AND source_contract_code='ORDER_PREPARATION' AND source_contract_version=5;
SELECT ROW_COUNT() AS v91003_9;
UPDATE t_risk_variable SET source_contract_version=4, rule_condition_flag=0, flow_condition_flag=0, version=version+1, update_time=@t
 WHERE variable_id=900000000000091010 AND source_contract_version=5 AND source_field='client_id';
SELECT ROW_COUNT() AS v91010;
UPDATE t_risk_variable SET status='ACTIVE', version=version+1, update_time=@t
 WHERE variable_id=900000000000091002 AND variable_code='INPUT_LOCALE' AND status='DISABLED';
SELECT ROW_COUNT() AS v91002;
DELETE FROM t_risk_variable WHERE variable_id IN (900000000000091018,900000000000091019,900000000000091020) AND system_managed_flag=1;
SELECT ROW_COUNT() AS new_vars;
DELETE FROM t_decision_point_version WHERE point_version_id=220001;
SELECT ROW_COUNT() AS point_version;
ROLLBACK;
