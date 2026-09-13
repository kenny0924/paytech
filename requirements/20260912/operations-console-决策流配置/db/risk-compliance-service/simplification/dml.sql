-- RCO-17 精简修订 / 各环境执行记录见 README.md；仅回填，不初始化业务示例。
-- ddl.sql 后在停写窗口、同一人工 MySQL 会话中执行。
-- 前置：precheck.sql 所有 bad_* 为 0；四个新列尚未被其他写入占用。
-- 故意不在文件末尾 COMMIT：人工核对下面的计数和未填行后，在同一会话 COMMIT；否则 ROLLBACK。
START TRANSACTION;

UPDATE t_rule_set_version rv
JOIN t_decision_point_rule_set b ON b.rule_set_version_id = rv.rule_set_version_id
JOIN t_decision_point_version pv ON pv.point_version_id = b.point_version_id
SET rv.input_contract_code = pv.input_contract_code,
    rv.input_contract_version = pv.input_contract_version
WHERE rv.input_contract_code IS NULL AND rv.input_contract_version IS NULL
  AND pv.decision_point_id = rv.decision_point_id AND b.sequence_no = 1;
SELECT 'backfilled_rule_versions' AS metric, ROW_COUNT() AS row_count;

UPDATE t_rule_operand o
JOIN t_decision_point_input_field f ON f.input_field_id = o.input_field_id
SET o.field_code = f.field_code
WHERE o.source_type = 'CONTEXT' AND o.field_code IS NULL;
SELECT 'backfilled_context_operands' AS metric, ROW_COUNT() AS row_count;

UPDATE t_rule_action_parameter a
JOIN t_decision_point_input_field f ON f.input_field_id = a.input_field_id
SET a.field_code = f.field_code
WHERE a.source_type = 'CONTEXT' AND a.field_code IS NULL;
SELECT 'backfilled_context_parameters' AS metric, ROW_COUNT() AS row_count;

SELECT 'unfilled_rule_contracts' AS metric, COUNT(*) AS row_count FROM t_rule_set_version
WHERE input_contract_code IS NULL OR input_contract_version IS NULL
UNION ALL
SELECT 'unfilled_context_operands', COUNT(*) FROM t_rule_operand
WHERE source_type = 'CONTEXT' AND field_code IS NULL
UNION ALL
SELECT 'unfilled_context_parameters', COUNT(*) FROM t_rule_action_parameter
WHERE source_type = 'CONTEXT' AND field_code IS NULL;

-- 必須继续在本会话执行 postcheck.sql，所有 bad_* 为 0 且更新行数与本次预检一致后才 COMMIT。
-- 不要将本文件单独交给断开即退出的批处理客户端，否则事务会被回滚。
