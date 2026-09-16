-- READ ONLY. Review against the selected Owner database; no cross-database queries.

-- Missing tables/columns are a baseline mismatch, not zero rows. Stop on any SQL error.

SELECT DATABASE() AS selected_database;

SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('t_due_diligence_case','t_dd_case_subject','t_dd_case_dependency','t_dd_party_snapshot','t_dd_requirement','t_dd_evidence_ref','t_dd_check','t_dd_case_state_change','t_qualification_decision','t_qualification_state_change') ORDER BY TABLE_NAME,ORDINAL_POSITION;

SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('t_due_diligence_case','t_dd_case_subject','t_dd_case_dependency','t_dd_party_snapshot','t_dd_requirement','t_dd_evidence_ref','t_dd_check','t_dd_case_state_change','t_qualification_decision','t_qualification_state_change') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;

-- Exact row counts for the conservative empty-data gate.

SELECT 't_due_diligence_case' AS table_name, COUNT(*) AS row_count FROM `t_due_diligence_case`;

SELECT 't_dd_case_subject' AS table_name, COUNT(*) AS row_count FROM `t_dd_case_subject`;

SELECT 't_dd_case_dependency' AS table_name, COUNT(*) AS row_count FROM `t_dd_case_dependency`;

SELECT 't_dd_party_snapshot' AS table_name, COUNT(*) AS row_count FROM `t_dd_party_snapshot`;

SELECT 't_dd_requirement' AS table_name, COUNT(*) AS row_count FROM `t_dd_requirement`;

SELECT 't_dd_evidence_ref' AS table_name, COUNT(*) AS row_count FROM `t_dd_evidence_ref`;

SELECT 't_dd_check' AS table_name, COUNT(*) AS row_count FROM `t_dd_check`;

SELECT 't_dd_case_state_change' AS table_name, COUNT(*) AS row_count FROM `t_dd_case_state_change`;

SELECT 't_qualification_decision' AS table_name, COUNT(*) AS row_count FROM `t_qualification_decision`;

SELECT 't_qualification_state_change' AS table_name, COUNT(*) AS row_count FROM `t_qualification_state_change`;

SELECT 't_dd_case_subject.case_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_case_subject` child LEFT JOIN `t_due_diligence_case` parent ON child.`case_id`=parent.`case_id` WHERE child.`case_id` IS NOT NULL AND parent.`case_id` IS NULL;

SELECT 't_dd_case_dependency.parent_case_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_case_dependency` child LEFT JOIN `t_due_diligence_case` parent ON child.`parent_case_id`=parent.`case_id` WHERE child.`parent_case_id` IS NOT NULL AND parent.`case_id` IS NULL;

SELECT 't_dd_case_dependency.child_case_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_case_dependency` child LEFT JOIN `t_due_diligence_case` parent ON child.`child_case_id`=parent.`case_id` WHERE child.`child_case_id` IS NOT NULL AND parent.`case_id` IS NULL;

SELECT 't_dd_case_dependency.accepted_decision_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_case_dependency` child LEFT JOIN `t_qualification_decision` parent ON child.`accepted_decision_id`=parent.`decision_id` WHERE child.`accepted_decision_id` IS NOT NULL AND parent.`decision_id` IS NULL;

SELECT 't_dd_party_snapshot.case_subject_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_party_snapshot` child LEFT JOIN `t_dd_case_subject` parent ON child.`case_subject_id`=parent.`case_subject_id` WHERE child.`case_subject_id` IS NOT NULL AND parent.`case_subject_id` IS NULL;

SELECT 't_dd_requirement.case_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_requirement` child LEFT JOIN `t_due_diligence_case` parent ON child.`case_id`=parent.`case_id` WHERE child.`case_id` IS NOT NULL AND parent.`case_id` IS NULL;

SELECT 't_dd_evidence_ref.requirement_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_evidence_ref` child LEFT JOIN `t_dd_requirement` parent ON child.`requirement_id`=parent.`requirement_id` WHERE child.`requirement_id` IS NOT NULL AND parent.`requirement_id` IS NULL;

SELECT 't_dd_evidence_ref.case_subject_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_evidence_ref` child LEFT JOIN `t_dd_case_subject` parent ON child.`case_subject_id`=parent.`case_subject_id` WHERE child.`case_subject_id` IS NOT NULL AND parent.`case_subject_id` IS NULL;

SELECT 't_dd_check.case_subject_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_check` child LEFT JOIN `t_dd_case_subject` parent ON child.`case_subject_id`=parent.`case_subject_id` WHERE child.`case_subject_id` IS NOT NULL AND parent.`case_subject_id` IS NULL;

SELECT 't_dd_case_state_change.case_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_dd_case_state_change` child LEFT JOIN `t_due_diligence_case` parent ON child.`case_id`=parent.`case_id` WHERE child.`case_id` IS NOT NULL AND parent.`case_id` IS NULL;

SELECT 't_qualification_decision.case_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_qualification_decision` child LEFT JOIN `t_due_diligence_case` parent ON child.`case_id`=parent.`case_id` WHERE child.`case_id` IS NOT NULL AND parent.`case_id` IS NULL;

SELECT 't_qualification_state_change.decision_id' AS reference_name,COUNT(*) AS missing_parent_count FROM `t_qualification_state_change` child LEFT JOIN `t_qualification_decision` parent ON child.`decision_id`=parent.`decision_id` WHERE child.`decision_id` IS NOT NULL AND parent.`decision_id` IS NULL;

SELECT source_namespace,source_application_id,role_type,role_id,qualification_type,COUNT(*) AS duplicate_count FROM t_due_diligence_case WHERE case_origin='APPLICATION' GROUP BY source_namespace,source_application_id,role_type,role_id,qualification_type HAVING COUNT(*)>1;

SELECT COUNT(*) AS missing_application_source FROM t_due_diligence_case WHERE case_origin='APPLICATION' AND (source_application_id IS NULL OR source_namespace IS NULL);
