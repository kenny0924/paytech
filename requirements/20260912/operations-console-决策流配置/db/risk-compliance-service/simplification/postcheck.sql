-- RCO-17 精简修订 / 只读；扩展列及回填存在、旧列仍在时执行。
-- 所有 bad_* 必须为 0；此检查不代替完整历史 Hash 比对。
SELECT 'bad_contract_backfill' AS metric, COUNT(*) AS row_count
FROM t_rule_set_version rv
LEFT JOIN t_decision_point_rule_set b ON b.rule_set_version_id = rv.rule_set_version_id
LEFT JOIN t_decision_point_version pv ON pv.point_version_id = b.point_version_id
WHERE pv.point_version_id IS NULL OR pv.decision_point_id <> rv.decision_point_id
   OR NOT (BINARY rv.input_contract_code <=> BINARY pv.input_contract_code)
   OR NOT (rv.input_contract_version <=> pv.input_contract_version);

SELECT 'bad_operand_backfill_or_id_reconstruction' AS metric, COUNT(*) AS row_count
FROM t_rule_operand o
LEFT JOIN t_rule_node n ON n.node_id = o.node_id
LEFT JOIN t_risk_rule r ON r.rule_id = n.rule_id
LEFT JOIN t_decision_point_rule_set b ON b.rule_set_version_id = r.rule_set_version_id
LEFT JOIN t_decision_point_input_field f ON f.point_version_id = b.point_version_id
    AND BINARY f.field_code = BINARY o.field_code
WHERE (o.source_type = 'CONTEXT' AND (f.input_field_id IS NULL
       OR NOT (f.input_field_id <=> o.input_field_id)))
   OR (o.source_type <> 'CONTEXT' AND o.field_code IS NOT NULL);

SELECT 'bad_parameter_backfill_or_id_reconstruction' AS metric, COUNT(*) AS row_count
FROM t_rule_action_parameter a
LEFT JOIN t_rule_action ra ON ra.rule_action_id = a.rule_action_id
LEFT JOIN t_risk_rule r ON r.rule_id = ra.rule_id
LEFT JOIN t_decision_point_rule_set b ON b.rule_set_version_id = r.rule_set_version_id
LEFT JOIN t_decision_point_input_field f ON f.point_version_id = b.point_version_id
    AND BINARY f.field_code = BINARY a.field_code
WHERE (a.source_type = 'CONTEXT' AND (f.input_field_id IS NULL
       OR NOT (f.input_field_id <=> a.input_field_id)))
   OR (a.source_type <> 'CONTEXT' AND a.field_code IS NOT NULL);
