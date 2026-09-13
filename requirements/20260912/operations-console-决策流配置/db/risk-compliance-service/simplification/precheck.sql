-- RCO-17 精简修订：只读预检。目标库 mor_risk；不包含 DDL/DML。
-- 所有 bad_* 指标必须为 0。这里只核对迁移关系，不能替代历史 Hash 重算。
SELECT 'rule_versions_to_backfill' AS metric, COUNT(*) AS row_count FROM t_rule_set_version
UNION ALL
SELECT 'context_operands_to_backfill', COUNT(*) FROM t_rule_operand WHERE source_type = 'CONTEXT'
UNION ALL
SELECT 'context_action_parameters_to_backfill', COUNT(*) FROM t_rule_action_parameter WHERE source_type = 'CONTEXT'
UNION ALL
SELECT 'legacy_point_versions_preserved', COUNT(*) FROM t_decision_point_version
UNION ALL
SELECT 'legacy_input_fields_preserved', COUNT(*) FROM t_decision_point_input_field
UNION ALL
SELECT 'legacy_bindings_preserved', COUNT(*) FROM t_decision_point_rule_set
UNION ALL
SELECT 'legacy_releases_preserved', COUNT(*) FROM t_decision_release;

SELECT 'bad_rule_contract_source' AS metric, COUNT(*) AS row_count
FROM (
    SELECT rv.rule_set_version_id
    FROM t_rule_set_version rv
    LEFT JOIN t_decision_point_rule_set b ON b.rule_set_version_id = rv.rule_set_version_id
    LEFT JOIN t_decision_point_version pv ON pv.point_version_id = b.point_version_id
    LEFT JOIN t_decision_point p ON p.decision_point_id = rv.decision_point_id
    GROUP BY rv.rule_set_version_id
    HAVING COUNT(b.binding_id) <> 1
       OR SUM(CASE WHEN pv.point_version_id IS NULL OR p.decision_point_id IS NULL
            OR pv.decision_point_id <> rv.decision_point_id OR b.sequence_no <> 1
            OR BINARY p.point_code <> BINARY 'CHECKOUT_PREPARE'
            OR BINARY pv.input_contract_code <> BINARY 'ORDER_PREPARATION'
            OR pv.input_contract_version <> 4 THEN 1 ELSE 0 END) <> 0
) invalid;

SELECT 'bad_operand_field_source' AS metric, COUNT(*) AS row_count
FROM t_rule_operand o
LEFT JOIN t_rule_node n ON n.node_id = o.node_id
LEFT JOIN t_risk_rule r ON r.rule_id = n.rule_id
LEFT JOIN t_rule_set_version rv ON rv.rule_set_version_id = r.rule_set_version_id
LEFT JOIN t_decision_point_rule_set b ON b.rule_set_version_id = rv.rule_set_version_id
LEFT JOIN t_decision_point_input_field f ON f.input_field_id = o.input_field_id
WHERE rv.rule_set_version_id IS NULL OR o.source_type NOT IN ('CONTEXT', 'LITERAL')
   OR (o.source_type = 'CONTEXT' AND (f.input_field_id IS NULL OR b.binding_id IS NULL
       OR f.point_version_id <> b.point_version_id OR f.rule_reference_flag <> 1
       OR BINARY f.value_type <> BINARY o.value_type OR o.feature_version_id IS NOT NULL))
   OR (o.source_type = 'LITERAL' AND (o.input_field_id IS NOT NULL OR o.feature_version_id IS NOT NULL));

SELECT 'bad_action_parameter_field_source' AS metric, COUNT(*) AS row_count
FROM t_rule_action_parameter a
LEFT JOIN t_rule_action ra ON ra.rule_action_id = a.rule_action_id
LEFT JOIN t_risk_rule r ON r.rule_id = ra.rule_id
LEFT JOIN t_rule_set_version rv ON rv.rule_set_version_id = r.rule_set_version_id
LEFT JOIN t_decision_point_rule_set b ON b.rule_set_version_id = rv.rule_set_version_id
LEFT JOIN t_decision_point_input_field f ON f.input_field_id = a.input_field_id
LEFT JOIN t_action_parameter_definition d ON d.parameter_definition_id = a.parameter_definition_id
WHERE rv.rule_set_version_id IS NULL OR d.parameter_definition_id IS NULL
   OR d.action_version_id <> ra.action_version_id OR a.source_type NOT IN ('CONTEXT', 'LITERAL')
   OR (a.source_type = 'CONTEXT' AND (f.input_field_id IS NULL OR b.binding_id IS NULL
       OR f.point_version_id <> b.point_version_id OR f.action_parameter_flag <> 1
       OR BINARY f.value_type <> BINARY d.value_type OR a.feature_version_id IS NOT NULL))
   OR (a.source_type = 'LITERAL' AND (a.input_field_id IS NOT NULL OR a.feature_version_id IS NOT NULL));
