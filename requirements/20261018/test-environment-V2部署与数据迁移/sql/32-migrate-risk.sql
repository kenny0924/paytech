-- 32-migrate-risk.sql · mor_risk → mor_risk_decision + mor_compliance
-- 影响：只向 mor_compliance、mor_risk_decision 插入行；源库 mor_risk 只读，不修改、不删除。
-- 前置：10 已建库与账号；各目标库已按 paytech/<svc>/docs/database/sql/<svc>-schema.sql 建表且目标表为空
--       （scripts/migrate.sh 在执行前逐表核对为空；重复执行会因主键冲突整体失败，事务不提交）。
-- 列：每张表显式列出 V2 与旧表的共同列（由 scripts/generate_sql.py 从演练库 information_schema 生成，勿手改）。
-- 事务：单个事务；任一语句失败则 mysql 客户端退出，未提交的事务随连接关闭回滚。
-- 回滚：目标库只含本次迁移写入的数据：DROP DATABASE mor_compliance、mor_risk_decision，再按 10 与 Schema 重建；
--       旧库未改动，回滚不需要恢复源数据。
-- 拆分：按 V2 Schema 归属（risk-decision 53 表、compliance 23 表）；t_compensation_event 按 event_type：
--       DECISION_SHADOW_RUN → risk-decision，CLIENT_REVIEW_RESULT → compliance。
-- 初始化目录：risk-decision-initial-data.sql 不执行（目录行已在旧库，随迁移带入；40 与 migrate.sh 核对种子主键齐全）。
-- 不迁移：t_command_idempotency 与 7 张争议表（归档见 33）。

SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- mor_risk.t_action_effect -> mor_risk_decision.t_action_effect
INSERT INTO `mor_risk_decision`.`t_action_effect` (`effect_id`, `evaluation_id`, `action_version_id`, `effect_mode`, `target_type`, `target_key`, `merge_policy`, `effect_hash`, `effect_status`, `idempotency_key`, `execution_type`, `execution_status`, `attempt_count`, `started_time`, `completed_time`, `last_error_code`, `last_error_summary`, `create_time`, `update_time`)
SELECT `effect_id`, `evaluation_id`, `action_version_id`, `effect_mode`, `target_type`, `target_key`, `merge_policy`, `effect_hash`, `effect_status`, `idempotency_key`, `execution_type`, `execution_status`, `attempt_count`, `started_time`, `completed_time`, `last_error_code`, `last_error_summary`, `create_time`, `update_time`
  FROM `mor_risk`.`t_action_effect`;

-- mor_risk.t_action_parameter_definition -> mor_risk_decision.t_action_parameter_definition
INSERT INTO `mor_risk_decision`.`t_action_parameter_definition` (`parameter_definition_id`, `action_version_id`, `parameter_code`, `value_type`, `required_flag`, `allowed_source_type`, `target_parameter_flag`, `create_time`)
SELECT `parameter_definition_id`, `action_version_id`, `parameter_code`, `value_type`, `required_flag`, `allowed_source_type`, `target_parameter_flag`, `create_time`
  FROM `mor_risk`.`t_action_parameter_definition`;

-- mor_risk.t_action_version -> mor_risk_decision.t_action_version
INSERT INTO `mor_risk_decision`.`t_action_version` (`action_version_id`, `action_code`, `action_name`, `action_category`, `version_no`, `handler_code`, `implementation_version`, `implementation_hash`, `terminal_flag`, `decision_code`, `merge_policy`, `shadow_supported_flag`, `status`, `create_time`, `update_time`)
SELECT `action_version_id`, `action_code`, `action_name`, `action_category`, `version_no`, `handler_code`, `implementation_version`, `implementation_hash`, `terminal_flag`, `decision_code`, `merge_policy`, `shadow_supported_flag`, `status`, `create_time`, `update_time`
  FROM `mor_risk`.`t_action_version`;

-- mor_risk.t_checkout_input_snapshot -> mor_risk_decision.t_checkout_input_snapshot
INSERT INTO `mor_risk_decision`.`t_checkout_input_snapshot` (`snapshot_id`, `evaluation_id`, `order_id`, `client_id`, `entity_no`, `entity_source_type`, `local_partner_id`, `consumer_country_code`, `currency`, `merchandise_amount`, `discount_amount`, `net_amount`, `industry_code`, `mcc_code`, `line_count`, `stable_input_hash`, `create_time`)
SELECT `snapshot_id`, `evaluation_id`, `order_id`, `client_id`, `entity_no`, `entity_source_type`, `local_partner_id`, `consumer_country_code`, `currency`, `merchandise_amount`, `discount_amount`, `net_amount`, `industry_code`, `mcc_code`, `line_count`, `stable_input_hash`, `create_time`
  FROM `mor_risk`.`t_checkout_input_snapshot`;

-- mor_risk.t_compensation_event -> mor_risk_decision.t_compensation_event（按条件拆分）
INSERT INTO `mor_risk_decision`.`t_compensation_event` (`id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`)
SELECT `id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_compensation_event` WHERE `event_type` IN ('DECISION_SHADOW_RUN');

-- mor_risk.t_compensation_event -> mor_compliance.t_compensation_event（按条件拆分）
INSERT INTO `mor_compliance`.`t_compensation_event` (`id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`)
SELECT `id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_compensation_event` WHERE `event_type` IN ('CLIENT_REVIEW_RESULT');

-- mor_risk.t_dd_case_classification -> mor_compliance.t_dd_case_classification
INSERT INTO `mor_compliance`.`t_dd_case_classification` (`case_classification_id`, `case_id`, `industry_code`, `mcc_code`, `primary_slot`, `confirmed_by`, `confirmed_time`, `create_time`)
SELECT `case_classification_id`, `case_id`, `industry_code`, `mcc_code`, `primary_slot`, `confirmed_by`, `confirmed_time`, `create_time`
  FROM `mor_risk`.`t_dd_case_classification`;

-- mor_risk.t_dd_case_dependency -> mor_compliance.t_dd_case_dependency
INSERT INTO `mor_compliance`.`t_dd_case_dependency` (`dependency_id`, `parent_case_id`, `child_case_id`, `dependency_type`, `required_flag`, `accepted_decision_id`, `version`, `create_time`, `update_time`)
SELECT `dependency_id`, `parent_case_id`, `child_case_id`, `dependency_type`, `required_flag`, `accepted_decision_id`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_dd_case_dependency`;

-- mor_risk.t_dd_case_state_change -> mor_compliance.t_dd_case_state_change
INSERT INTO `mor_compliance`.`t_dd_case_state_change` (`change_id`, `case_id`, `from_status`, `to_status`, `action_type`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`)
SELECT `change_id`, `case_id`, `from_status`, `to_status`, `action_type`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`
  FROM `mor_risk`.`t_dd_case_state_change`;

-- mor_risk.t_dd_case_subject -> mor_compliance.t_dd_case_subject
INSERT INTO `mor_compliance`.`t_dd_case_subject` (`case_subject_id`, `case_id`, `party_id`, `subject_role`, `primary_slot`, `source_profile_id`, `source_relation_id`, `source_profile_version_no`, `source_relation_version_id`, `source_relation_version_no`, `ownership_percentage`, `required_flag`, `create_time`)
SELECT `case_subject_id`, `case_id`, `party_id`, `subject_role`, `primary_slot`, `source_profile_id`, `source_relation_id`, `source_profile_version_no`, `source_relation_version_id`, `source_relation_version_no`, `ownership_percentage`, `required_flag`, `create_time`
  FROM `mor_risk`.`t_dd_case_subject`;

-- mor_risk.t_dd_check -> mor_compliance.t_dd_check
INSERT INTO `mor_compliance`.`t_dd_check` (`check_id`, `case_subject_id`, `program_check_id`, `provider_code`, `request_ref`, `result_code`, `result_vault_token`, `performed_time`, `valid_until`, `create_time`)
SELECT `check_id`, `case_subject_id`, `program_check_id`, `provider_code`, `request_ref`, `result_code`, `result_vault_token`, `performed_time`, `valid_until`, `create_time`
  FROM `mor_risk`.`t_dd_check`;

-- mor_risk.t_dd_evidence_ref -> mor_compliance.t_dd_evidence_ref
INSERT INTO `mor_compliance`.`t_dd_evidence_ref` (`evidence_id`, `requirement_id`, `case_subject_id`, `file_id`, `content_hash`, `status`, `version`, `create_time`, `update_time`)
SELECT `evidence_id`, `requirement_id`, `case_subject_id`, `file_id`, `content_hash`, `status`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_dd_evidence_ref`;

-- mor_risk.t_dd_party_snapshot -> mor_compliance.t_dd_party_snapshot
INSERT INTO `mor_compliance`.`t_dd_party_snapshot` (`snapshot_id`, `case_subject_id`, `schema_version`, `source_content_hash`, `snapshot_vault_token`, `captured_time`, `create_time`)
SELECT `snapshot_id`, `case_subject_id`, `schema_version`, `source_content_hash`, `snapshot_vault_token`, `captured_time`, `create_time`
  FROM `mor_risk`.`t_dd_party_snapshot`;

-- mor_risk.t_dd_program -> mor_compliance.t_dd_program
INSERT INTO `mor_compliance`.`t_dd_program` (`program_id`, `program_code`, `qualification_type`, `status`, `create_time`, `update_time`)
SELECT `program_id`, `program_code`, `qualification_type`, `status`, `create_time`, `update_time`
  FROM `mor_risk`.`t_dd_program`;

-- mor_risk.t_dd_program_check -> mor_compliance.t_dd_program_check
INSERT INTO `mor_compliance`.`t_dd_program_check` (`program_check_id`, `program_version_id`, `check_code`, `check_type`, `required_flag`, `validity_days`, `create_time`)
SELECT `program_check_id`, `program_version_id`, `check_code`, `check_type`, `required_flag`, `validity_days`, `create_time`
  FROM `mor_risk`.`t_dd_program_check`;

-- mor_risk.t_dd_program_requirement -> mor_compliance.t_dd_program_requirement
INSERT INTO `mor_compliance`.`t_dd_program_requirement` (`program_requirement_id`, `program_version_id`, `requirement_code`, `document_type`, `required_flag`, `create_time`)
SELECT `program_requirement_id`, `program_version_id`, `requirement_code`, `document_type`, `required_flag`, `create_time`
  FROM `mor_risk`.`t_dd_program_requirement`;

-- mor_risk.t_dd_program_screening -> mor_compliance.t_dd_program_screening
INSERT INTO `mor_compliance`.`t_dd_program_screening` (`program_screening_id`, `program_version_id`, `subject_role`, `screening_type`, `required_flag`, `create_time`)
SELECT `program_screening_id`, `program_version_id`, `subject_role`, `screening_type`, `required_flag`, `create_time`
  FROM `mor_risk`.`t_dd_program_screening`;

-- mor_risk.t_dd_program_version -> mor_compliance.t_dd_program_version
INSERT INTO `mor_compliance`.`t_dd_program_version` (`program_version_id`, `program_id`, `version_no`, `review_frequency_code`, `status`, `active_slot`, `content_hash`, `approved_by`, `approved_time`, `create_time`, `update_time`)
SELECT `program_version_id`, `program_id`, `version_no`, `review_frequency_code`, `status`, `active_slot`, `content_hash`, `approved_by`, `approved_time`, `create_time`, `update_time`
  FROM `mor_risk`.`t_dd_program_version`;

-- mor_risk.t_dd_requirement -> mor_compliance.t_dd_requirement
INSERT INTO `mor_compliance`.`t_dd_requirement` (`requirement_id`, `case_id`, `program_requirement_id`, `status`, `due_time`, `version`, `create_time`, `update_time`)
SELECT `requirement_id`, `case_id`, `program_requirement_id`, `status`, `due_time`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_dd_requirement`;

-- mor_risk.t_decision_evaluation -> mor_risk_decision.t_decision_evaluation
INSERT INTO `mor_risk_decision`.`t_decision_evaluation` (`evaluation_id`, `evaluation_request_id`, `decision_point_id`, `point_version_id`, `release_id`, `evaluation_mode`, `evaluation_time`, `technical_status`, `proposed_decision`, `business_decision`, `decision_adjustment_reason_code`, `default_decision_used_flag`, `started_time`, `completed_time`, `elapsed_millis`, `input_snapshot_hash`, `failure_code`, `shadow_run_id`, `baseline_evaluation_id`, `decision_changed_flag`, `action_changed_flag`, `comparison_severity`, `compared_time`, `create_time`, `update_time`)
SELECT `evaluation_id`, `evaluation_request_id`, `decision_point_id`, `point_version_id`, `release_id`, `evaluation_mode`, `evaluation_time`, `technical_status`, `proposed_decision`, `business_decision`, `decision_adjustment_reason_code`, `default_decision_used_flag`, `started_time`, `completed_time`, `elapsed_millis`, `input_snapshot_hash`, `failure_code`, `shadow_run_id`, `baseline_evaluation_id`, `decision_changed_flag`, `action_changed_flag`, `comparison_severity`, `compared_time`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_evaluation`;

-- mor_risk.t_decision_flow -> mor_risk_decision.t_decision_flow
INSERT INTO `mor_risk_decision`.`t_decision_flow` (`flow_id`, `decision_point_id`, `flow_code`, `current_flow_version_id`, `status`, `version`, `deleted_by`, `deleted_time`, `create_time`, `update_time`)
SELECT `flow_id`, `decision_point_id`, `flow_code`, `current_flow_version_id`, `status`, `version`, `deleted_by`, `deleted_time`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_flow`;

-- mor_risk.t_decision_flow_edge -> mor_risk_decision.t_decision_flow_edge
INSERT INTO `mor_risk_decision`.`t_decision_flow_edge` (`edge_id`, `flow_version_id`, `edge_key`, `source_node_id`, `target_node_id`, `edge_type`, `priority_no`, `edge_name`, `condition_json`, `create_time`, `update_time`)
SELECT `edge_id`, `flow_version_id`, `edge_key`, `source_node_id`, `target_node_id`, `edge_type`, `priority_no`, `edge_name`, `condition_json`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_flow_edge`;

-- mor_risk.t_decision_flow_end_output -> mor_risk_decision.t_decision_flow_end_output
INSERT INTO `mor_risk_decision`.`t_decision_flow_end_output` (`output_id`, `flow_version_id`, `node_id`, `output_code`, `output_value`, `create_time`)
SELECT `output_id`, `flow_version_id`, `node_id`, `output_code`, `output_value`, `create_time`
  FROM `mor_risk`.`t_decision_flow_end_output`;

-- mor_risk.t_decision_flow_node -> mor_risk_decision.t_decision_flow_node
INSERT INTO `mor_risk_decision`.`t_decision_flow_node` (`node_id`, `flow_version_id`, `node_key`, `node_type`, `node_name`, `rule_set_version_id`, `decision_code`, `reason_code`, `reason_message`, `position_x`, `position_y`, `create_time`, `update_time`)
SELECT `node_id`, `flow_version_id`, `node_key`, `node_type`, `node_name`, `rule_set_version_id`, `decision_code`, `reason_code`, `reason_message`, `position_x`, `position_y`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_flow_node`;

-- mor_risk.t_decision_flow_version -> mor_risk_decision.t_decision_flow_version
INSERT INTO `mor_risk_decision`.`t_decision_flow_version` (`flow_version_id`, `flow_id`, `version_no`, `flow_name`, `input_contract_code`, `input_contract_version`, `created_by`, `change_reason`, `create_time`, `update_time`)
SELECT `flow_version_id`, `flow_id`, `version_no`, `flow_name`, `input_contract_code`, `input_contract_version`, `created_by`, `change_reason`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_flow_version`;

-- mor_risk.t_decision_point -> mor_risk_decision.t_decision_point
INSERT INTO `mor_risk_decision`.`t_decision_point` (`decision_point_id`, `point_code`, `point_name`, `business_domain`, `status`, `create_time`, `update_time`)
SELECT `decision_point_id`, `point_code`, `point_name`, `business_domain`, `status`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_point`;

-- mor_risk.t_decision_point_feature -> mor_risk_decision.t_decision_point_feature
INSERT INTO `mor_risk_decision`.`t_decision_point_feature` (`binding_id`, `point_version_id`, `feature_version_id`, `sequence_no`, `create_time`)
SELECT `binding_id`, `point_version_id`, `feature_version_id`, `sequence_no`, `create_time`
  FROM `mor_risk`.`t_decision_point_feature`;

-- mor_risk.t_decision_point_input_field -> mor_risk_decision.t_decision_point_input_field
INSERT INTO `mor_risk_decision`.`t_decision_point_input_field` (`input_field_id`, `point_version_id`, `field_code`, `value_type`, `required_flag`, `data_classification`, `rule_reference_flag`, `action_parameter_flag`, `create_time`)
SELECT `input_field_id`, `point_version_id`, `field_code`, `value_type`, `required_flag`, `data_classification`, `rule_reference_flag`, `action_parameter_flag`, `create_time`
  FROM `mor_risk`.`t_decision_point_input_field`;

-- mor_risk.t_decision_point_limit_binding -> mor_risk_decision.t_decision_point_limit_binding
INSERT INTO `mor_risk_decision`.`t_decision_point_limit_binding` (`binding_id`, `obligation_id`, `limit_version_id`, `sequence_no`, `subject_source_type`, `subject_feature_version_id`, `subject_input_field_id`, `amount_source_type`, `amount_feature_version_id`, `amount_input_field_id`, `create_time`)
SELECT `binding_id`, `obligation_id`, `limit_version_id`, `sequence_no`, `subject_source_type`, `subject_feature_version_id`, `subject_input_field_id`, `amount_source_type`, `amount_feature_version_id`, `amount_input_field_id`, `create_time`
  FROM `mor_risk`.`t_decision_point_limit_binding`;

-- mor_risk.t_decision_point_obligation -> mor_risk_decision.t_decision_point_obligation
INSERT INTO `mor_risk_decision`.`t_decision_point_obligation` (`obligation_id`, `point_version_id`, `trigger_decision`, `obligation_type`, `action_version_id`, `sequence_no`, `failure_policy`, `enabled_flag`, `create_time`)
SELECT `obligation_id`, `point_version_id`, `trigger_decision`, `obligation_type`, `action_version_id`, `sequence_no`, `failure_policy`, `enabled_flag`, `create_time`
  FROM `mor_risk`.`t_decision_point_obligation`;

-- mor_risk.t_decision_point_rule_set -> mor_risk_decision.t_decision_point_rule_set
INSERT INTO `mor_risk_decision`.`t_decision_point_rule_set` (`binding_id`, `point_version_id`, `rule_set_version_id`, `sequence_no`, `create_time`)
SELECT `binding_id`, `point_version_id`, `rule_set_version_id`, `sequence_no`, `create_time`
  FROM `mor_risk`.`t_decision_point_rule_set`;

-- mor_risk.t_decision_point_version -> mor_risk_decision.t_decision_point_version
INSERT INTO `mor_risk_decision`.`t_decision_point_version` (`point_version_id`, `decision_point_id`, `version_no`, `input_contract_code`, `input_contract_version`, `output_contract_code`, `default_decision`, `evaluation_strategy`, `failure_strategy`, `timeout_ms`, `status`, `content_hash`, `create_time`, `update_time`)
SELECT `point_version_id`, `decision_point_id`, `version_no`, `input_contract_code`, `input_contract_version`, `output_contract_code`, `default_decision`, `evaluation_strategy`, `failure_strategy`, `timeout_ms`, `status`, `content_hash`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_point_version`;

-- mor_risk.t_decision_release -> mor_risk_decision.t_decision_release
INSERT INTO `mor_risk_decision`.`t_decision_release` (`release_id`, `release_no`, `decision_point_id`, `point_version_id`, `decision_flow_version_id`, `environment_code`, `status`, `active_slot`, `content_hash`, `created_by`, `activated_time`, `superseded_time`, `version`, `create_time`, `update_time`)
SELECT `release_id`, `release_no`, `decision_point_id`, `point_version_id`, `decision_flow_version_id`, `environment_code`, `status`, `active_slot`, `content_hash`, `created_by`, `activated_time`, `superseded_time`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_decision_release`;

-- mor_risk.t_due_diligence_case -> mor_compliance.t_due_diligence_case
INSERT INTO `mor_compliance`.`t_due_diligence_case` (`case_id`, `case_no`, `role_type`, `role_id`, `qualification_type`, `dd_program_version_id`, `case_origin`, `scheduled_review_time`, `source_namespace`, `source_application_id`, `source_submission_id`, `public_note`, `resolution_note`, `return_section`, `source_submission_version`, `source_submission_hash`, `status`, `active_slot`, `submitted_time`, `decision_time`, `version`, `create_time`, `update_time`)
SELECT `case_id`, `case_no`, `role_type`, `role_id`, `qualification_type`, `dd_program_version_id`, `case_origin`, `scheduled_review_time`, `source_namespace`, `source_application_id`, `source_submission_id`, `public_note`, `resolution_note`, `return_section`, `source_submission_version`, `source_submission_hash`, `status`, `active_slot`, `submitted_time`, `decision_time`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_due_diligence_case`;

-- mor_risk.t_evaluation_action_intent -> mor_risk_decision.t_evaluation_action_intent
INSERT INTO `mor_risk_decision`.`t_evaluation_action_intent` (`intent_id`, `evaluation_id`, `source_type`, `rule_action_id`, `obligation_id`, `action_version_id`, `resolved_target_type`, `resolved_target_key`, `intent_sequence`, `merged_effect_id`, `status`, `create_time`, `update_time`)
SELECT `intent_id`, `evaluation_id`, `source_type`, `rule_action_id`, `obligation_id`, `action_version_id`, `resolved_target_type`, `resolved_target_key`, `intent_sequence`, `merged_effect_id`, `status`, `create_time`, `update_time`
  FROM `mor_risk`.`t_evaluation_action_intent`;

-- mor_risk.t_evaluation_feature_result -> mor_risk_decision.t_evaluation_feature_result
INSERT INTO `mor_risk_decision`.`t_evaluation_feature_result` (`result_id`, `evaluation_id`, `feature_version_id`, `execution_sequence`, `technical_status`, `value_type`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `elapsed_millis`, `failure_code`, `create_time`)
SELECT `result_id`, `evaluation_id`, `feature_version_id`, `execution_sequence`, `technical_status`, `value_type`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `elapsed_millis`, `failure_code`, `create_time`
  FROM `mor_risk`.`t_evaluation_feature_result`;

-- mor_risk.t_evaluation_flow_trace -> mor_risk_decision.t_evaluation_flow_trace
INSERT INTO `mor_risk_decision`.`t_evaluation_flow_trace` (`trace_id`, `evaluation_id`, `step_no`, `node_key`, `node_type`, `rule_set_version_id`, `matched_flag`, `node_decision`, `reused_flag`, `exit_edge_key`, `create_time`)
SELECT `trace_id`, `evaluation_id`, `step_no`, `node_key`, `node_type`, `rule_set_version_id`, `matched_flag`, `node_decision`, `reused_flag`, `exit_edge_key`, `create_time`
  FROM `mor_risk`.`t_evaluation_flow_trace`;

-- mor_risk.t_evaluation_rule_trace -> mor_risk_decision.t_evaluation_rule_trace
INSERT INTO `mor_risk_decision`.`t_evaluation_rule_trace` (`trace_id`, `evaluation_id`, `trace_type`, `rule_set_version_id`, `rule_id`, `node_id`, `execution_sequence`, `result_value`, `terminal_flag`, `elapsed_millis`, `failure_code`, `rule_result_slot`, `predicate_result_slot`, `create_time`)
SELECT `trace_id`, `evaluation_id`, `trace_type`, `rule_set_version_id`, `rule_id`, `node_id`, `execution_sequence`, `result_value`, `terminal_flag`, `elapsed_millis`, `failure_code`, `rule_result_slot`, `predicate_result_slot`, `create_time`
  FROM `mor_risk`.`t_evaluation_rule_trace`;

-- mor_risk.t_feature_dependency -> mor_risk_decision.t_feature_dependency
INSERT INTO `mor_risk_decision`.`t_feature_dependency` (`dependency_id`, `feature_version_id`, `dependency_feature_version_id`, `sequence_no`, `required_flag`, `create_time`)
SELECT `dependency_id`, `feature_version_id`, `dependency_feature_version_id`, `sequence_no`, `required_flag`, `create_time`
  FROM `mor_risk`.`t_feature_dependency`;

-- mor_risk.t_feature_version -> mor_risk_decision.t_feature_version
INSERT INTO `mor_risk_decision`.`t_feature_version` (`feature_version_id`, `feature_code`, `feature_name`, `business_category`, `version_no`, `result_value_type`, `handler_code`, `implementation_version`, `implementation_hash`, `timeout_ms`, `status`, `create_time`, `update_time`)
SELECT `feature_version_id`, `feature_code`, `feature_name`, `business_category`, `version_no`, `result_value_type`, `handler_code`, `implementation_version`, `implementation_hash`, `timeout_ms`, `status`, `create_time`, `update_time`
  FROM `mor_risk`.`t_feature_version`;

-- mor_risk.t_limit_group_state_change -> mor_risk_decision.t_limit_group_state_change
INSERT INTO `mor_risk_decision`.`t_limit_group_state_change` (`change_id`, `reservation_group_id`, `from_status`, `to_status`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`)
SELECT `change_id`, `reservation_group_id`, `from_status`, `to_status`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`
  FROM `mor_risk`.`t_limit_group_state_change`;

-- mor_risk.t_limit_movement -> mor_risk_decision.t_limit_movement
INSERT INTO `mor_risk_decision`.`t_limit_movement` (`movement_id`, `reservation_id`, `movement_type`, `amount_delta`, `count_delta`, `trigger_type`, `trigger_id`, `occurred_time`, `position_version`, `create_time`)
SELECT `movement_id`, `reservation_id`, `movement_type`, `amount_delta`, `count_delta`, `trigger_type`, `trigger_id`, `occurred_time`, `position_version`, `create_time`
  FROM `mor_risk`.`t_limit_movement`;

-- mor_risk.t_limit_position -> mor_risk_decision.t_limit_position
INSERT INTO `mor_risk_decision`.`t_limit_position` (`position_id`, `limit_version_id`, `subject_key`, `currency_scope_key`, `window_start`, `window_end`, `reserved_amount`, `committed_amount`, `reserved_count`, `committed_count`, `version`, `create_time`, `update_time`)
SELECT `position_id`, `limit_version_id`, `subject_key`, `currency_scope_key`, `window_start`, `window_end`, `reserved_amount`, `committed_amount`, `reserved_count`, `committed_count`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_limit_position`;

-- mor_risk.t_limit_reservation -> mor_risk_decision.t_limit_reservation
INSERT INTO `mor_risk_decision`.`t_limit_reservation` (`reservation_id`, `reservation_group_id`, `position_id`, `amount`, `count_value`, `create_time`)
SELECT `reservation_id`, `reservation_group_id`, `position_id`, `amount`, `count_value`, `create_time`
  FROM `mor_risk`.`t_limit_reservation`;

-- mor_risk.t_limit_reservation_group -> mor_risk_decision.t_limit_reservation_group
INSERT INTO `mor_risk_decision`.`t_limit_reservation_group` (`reservation_group_id`, `assessment_id`, `obligation_id`, `status`, `hold_reason`, `resolved_time`, `version`, `create_time`, `update_time`)
SELECT `reservation_group_id`, `assessment_id`, `obligation_id`, `status`, `hold_reason`, `resolved_time`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_limit_reservation_group`;

-- mor_risk.t_limit_version -> mor_risk_decision.t_limit_version
INSERT INTO `mor_risk_decision`.`t_limit_version` (`limit_version_id`, `limit_code`, `limit_name`, `version_no`, `subject_type`, `metric_type`, `window_unit`, `window_size`, `amount_limit`, `count_limit`, `currency_mode`, `fixed_currency`, `content_hash`, `status`, `frozen_by`, `frozen_time`, `create_time`, `update_time`)
SELECT `limit_version_id`, `limit_code`, `limit_name`, `version_no`, `subject_type`, `metric_type`, `window_unit`, `window_size`, `amount_limit`, `count_limit`, `currency_mode`, `fixed_currency`, `content_hash`, `status`, `frozen_by`, `frozen_time`, `create_time`, `update_time`
  FROM `mor_risk`.`t_limit_version`;

-- mor_risk.t_list_feature_definition -> mor_risk_decision.t_list_feature_definition
INSERT INTO `mor_risk_decision`.`t_list_feature_definition` (`feature_version_id`, `list_id`, `subject_type`, `create_time`)
SELECT `feature_version_id`, `list_id`, `subject_type`, `create_time`
  FROM `mor_risk`.`t_list_feature_definition`;

-- mor_risk.t_payment_attempt_fact -> mor_risk_decision.t_payment_attempt_fact
INSERT INTO `mor_risk_decision`.`t_payment_attempt_fact` (`fact_id`, `payment_attempt_id`, `payment_intent_id`, `attempt_status`, `result_reason`, `limit_reservation_group_id`, `source_event_key`, `source_version`, `source_payload_hash`, `occurred_time`, `version`, `create_time`, `update_time`)
SELECT `fact_id`, `payment_attempt_id`, `payment_intent_id`, `attempt_status`, `result_reason`, `limit_reservation_group_id`, `source_event_key`, `source_version`, `source_payload_hash`, `occurred_time`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_payment_attempt_fact`;

-- mor_risk.t_qualification_decision -> mor_compliance.t_qualification_decision
INSERT INTO `mor_compliance`.`t_qualification_decision` (`decision_id`, `case_id`, `decision_version`, `role_type`, `role_id`, `qualification_type`, `decision_evaluation_id`, `decision_source`, `recommended_decision`, `outcome`, `override_reason_code`, `valid_from`, `valid_to`, `decided_by`, `decided_time`, `create_time`)
SELECT `decision_id`, `case_id`, `decision_version`, `role_type`, `role_id`, `qualification_type`, `decision_evaluation_id`, `decision_source`, `recommended_decision`, `outcome`, `override_reason_code`, `valid_from`, `valid_to`, `decided_by`, `decided_time`, `create_time`
  FROM `mor_risk`.`t_qualification_decision`;

-- mor_risk.t_qualification_state_change -> mor_compliance.t_qualification_state_change
INSERT INTO `mor_compliance`.`t_qualification_state_change` (`change_id`, `qualification_id`, `from_status`, `to_status`, `decision_id`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`)
SELECT `change_id`, `qualification_id`, `from_status`, `to_status`, `decision_id`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`
  FROM `mor_risk`.`t_qualification_state_change`;

-- mor_risk.t_release_audit_event -> mor_risk_decision.t_release_audit_event
INSERT INTO `mor_risk_decision`.`t_release_audit_event` (`audit_event_id`, `release_id`, `event_type`, `event_time`, `check_code`, `check_status`, `severity`, `error_code`, `error_summary`, `shadow_run_id`, `approval_request_id`, `approval_status`, `reviewer_id`, `review_comment`, `from_status`, `to_status`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `create_time`)
SELECT `audit_event_id`, `release_id`, `event_type`, `event_time`, `check_code`, `check_status`, `severity`, `error_code`, `error_summary`, `shadow_run_id`, `approval_request_id`, `approval_status`, `reviewer_id`, `review_comment`, `from_status`, `to_status`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `create_time`
  FROM `mor_risk`.`t_release_audit_event`;

-- mor_risk.t_release_shadow_run -> mor_risk_decision.t_release_shadow_run
INSERT INTO `mor_risk_decision`.`t_release_shadow_run` (`shadow_run_id`, `run_no`, `baseline_release_id`, `candidate_release_id`, `status`, `sample_target`, `sample_count`, `started_time`, `completed_time`, `allow_to_review_count`, `allow_to_deny_count`, `deny_to_allow_count`, `action_change_count`, `feature_missing_count`, `failed_count`, `timed_out_count`, `p95_millis`, `p99_millis`, `check_status`, `version`, `create_time`, `update_time`)
SELECT `shadow_run_id`, `run_no`, `baseline_release_id`, `candidate_release_id`, `status`, `sample_target`, `sample_count`, `started_time`, `completed_time`, `allow_to_review_count`, `allow_to_deny_count`, `deny_to_allow_count`, `action_change_count`, `feature_missing_count`, `failed_count`, `timed_out_count`, `p95_millis`, `p99_millis`, `check_status`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_release_shadow_run`;

-- mor_risk.t_risk_list -> mor_risk_decision.t_risk_list
INSERT INTO `mor_risk_decision`.`t_risk_list` (`list_id`, `list_code`, `list_name`, `supported_subject_type`, `matching_mode`, `list_purpose`, `status`, `version`, `create_time`, `update_time`)
SELECT `list_id`, `list_code`, `list_name`, `supported_subject_type`, `matching_mode`, `list_purpose`, `status`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_risk_list`;

-- mor_risk.t_risk_list_entry -> mor_risk_decision.t_risk_list_entry
INSERT INTO `mor_risk_decision`.`t_risk_list_entry` (`entry_id`, `list_id`, `subject_type`, `subject_key`, `source_type`, `source_effect_id`, `reason`, `created_by`, `deactivated_by`, `deactivated_reason`, `status`, `active_slot`, `effective_from`, `effective_to`, `supersedes_entry_id`, `version`, `create_time`, `update_time`)
SELECT `entry_id`, `list_id`, `subject_type`, `subject_key`, `source_type`, `source_effect_id`, `reason`, `created_by`, `deactivated_by`, `deactivated_reason`, `status`, `active_slot`, `effective_from`, `effective_to`, `supersedes_entry_id`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_risk_list_entry`;

-- mor_risk.t_risk_restriction -> mor_risk_decision.t_risk_restriction
INSERT INTO `mor_risk_decision`.`t_risk_restriction` (`restriction_id`, `restriction_no`, `restriction_type`, `subject_type`, `subject_key`, `source_effect_id`, `status`, `effective_from`, `effective_to`, `supersedes_restriction_id`, `version`, `create_time`, `update_time`)
SELECT `restriction_id`, `restriction_no`, `restriction_type`, `subject_type`, `subject_key`, `source_effect_id`, `status`, `effective_from`, `effective_to`, `supersedes_restriction_id`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_risk_restriction`;

-- mor_risk.t_risk_rule -> mor_risk_decision.t_risk_rule
INSERT INTO `mor_risk_decision`.`t_risk_rule` (`rule_id`, `rule_set_version_id`, `rule_code`, `rule_name`, `priority_no`, `enabled_flag`, `reason_code`, `reason_message`, `create_time`, `update_time`)
SELECT `rule_id`, `rule_set_version_id`, `rule_code`, `rule_name`, `priority_no`, `enabled_flag`, `reason_code`, `reason_message`, `create_time`, `update_time`
  FROM `mor_risk`.`t_risk_rule`;

-- mor_risk.t_risk_variable -> mor_risk_decision.t_risk_variable
INSERT INTO `mor_risk_decision`.`t_risk_variable` (`variable_id`, `variable_code`, `variable_name`, `description`, `source_type`, `value_type`, `point_code`, `source_contract_code`, `source_contract_version`, `feature_version_id`, `source_field`, `rule_condition_flag`, `flow_condition_flag`, `action_parameter_flag`, `system_managed_flag`, `format_code`, `numeric_unit`, `currency_code`, `currency_variable_code`, `allowed_values_variable_code`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `status`, `version`, `create_by`, `update_by`, `create_time`, `update_time`)
SELECT `variable_id`, `variable_code`, `variable_name`, `description`, `source_type`, `value_type`, `point_code`, `source_contract_code`, `source_contract_version`, `feature_version_id`, `source_field`, `rule_condition_flag`, `flow_condition_flag`, `action_parameter_flag`, `system_managed_flag`, `format_code`, `numeric_unit`, `currency_code`, `currency_variable_code`, `allowed_values_variable_code`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `status`, `version`, `create_by`, `update_by`, `create_time`, `update_time`
  FROM `mor_risk`.`t_risk_variable`;

-- mor_risk.t_risk_variable_member -> mor_risk_decision.t_risk_variable_member
INSERT INTO `mor_risk_decision`.`t_risk_variable_member` (`member_id`, `variable_code`, `value_key`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `display_name`, `sort_order`, `create_time`)
SELECT `member_id`, `variable_code`, `value_key`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `display_name`, `sort_order`, `create_time`
  FROM `mor_risk`.`t_risk_variable_member`;

-- mor_risk.t_role_qualification -> mor_compliance.t_role_qualification
INSERT INTO `mor_compliance`.`t_role_qualification` (`qualification_id`, `role_type`, `role_id`, `qualification_type`, `current_decision_id`, `status`, `valid_from`, `valid_to`, `review_frequency_code`, `next_review_time`, `review_status`, `version`, `create_time`, `update_time`)
SELECT `qualification_id`, `role_type`, `role_id`, `qualification_type`, `current_decision_id`, `status`, `valid_from`, `valid_to`, `review_frequency_code`, `next_review_time`, `review_status`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_role_qualification`;

-- mor_risk.t_rule_action -> mor_risk_decision.t_rule_action
INSERT INTO `mor_risk_decision`.`t_rule_action` (`rule_action_id`, `rule_id`, `action_version_id`, `sequence_no`, `target_binding_code`, `create_time`)
SELECT `rule_action_id`, `rule_id`, `action_version_id`, `sequence_no`, `target_binding_code`, `create_time`
  FROM `mor_risk`.`t_rule_action`;

-- mor_risk.t_rule_action_parameter -> mor_risk_decision.t_rule_action_parameter
INSERT INTO `mor_risk_decision`.`t_rule_action_parameter` (`parameter_id`, `rule_action_id`, `parameter_definition_id`, `value_sequence`, `source_type`, `feature_version_id`, `field_code`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `create_time`, `variable_code`)
SELECT `parameter_id`, `rule_action_id`, `parameter_definition_id`, `value_sequence`, `source_type`, `feature_version_id`, `field_code`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `create_time`, `variable_code`
  FROM `mor_risk`.`t_rule_action_parameter`;

-- mor_risk.t_rule_node -> mor_risk_decision.t_rule_node
INSERT INTO `mor_risk_decision`.`t_rule_node` (`node_id`, `rule_id`, `parent_node_id`, `node_type`, `group_operator`, `comparison_operator`, `sequence_no`, `create_time`, `update_time`)
SELECT `node_id`, `rule_id`, `parent_node_id`, `node_type`, `group_operator`, `comparison_operator`, `sequence_no`, `create_time`, `update_time`
  FROM `mor_risk`.`t_rule_node`;

-- mor_risk.t_rule_operand -> mor_risk_decision.t_rule_operand
INSERT INTO `mor_risk_decision`.`t_rule_operand` (`operand_id`, `node_id`, `operand_role`, `value_sequence`, `source_type`, `feature_version_id`, `field_code`, `value_type`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `create_time`, `update_time`, `variable_code`, `numeric_unit`, `currency_code`, `currency_variable_code`)
SELECT `operand_id`, `node_id`, `operand_role`, `value_sequence`, `source_type`, `feature_version_id`, `field_code`, `value_type`, `string_value`, `number_value`, `boolean_value`, `datetime_value`, `duration_value`, `create_time`, `update_time`, `variable_code`, `numeric_unit`, `currency_code`, `currency_variable_code`
  FROM `mor_risk`.`t_rule_operand`;

-- mor_risk.t_rule_reject_reason -> mor_risk_decision.t_rule_reject_reason
INSERT INTO `mor_risk_decision`.`t_rule_reject_reason` (`rejection_reason_id`, `reason_code`, `reason_name`, `description`, `default_message`, `status`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`)
SELECT `rejection_reason_id`, `reason_code`, `reason_name`, `description`, `default_message`, `status`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`
  FROM `mor_risk`.`t_rule_reject_reason`;

-- mor_risk.t_rule_set -> mor_risk_decision.t_rule_set
INSERT INTO `mor_risk_decision`.`t_rule_set` (`rule_set_id`, `decision_point_id`, `rule_set_code`, `current_rule_set_version_id`, `status`, `version`, `deleted_by`, `deleted_time`, `delete_reason_code`, `delete_trigger_id`, `create_time`, `update_time`)
SELECT `rule_set_id`, `decision_point_id`, `rule_set_code`, `current_rule_set_version_id`, `status`, `version`, `deleted_by`, `deleted_time`, `delete_reason_code`, `delete_trigger_id`, `create_time`, `update_time`
  FROM `mor_risk`.`t_rule_set`;

-- mor_risk.t_rule_set_version -> mor_risk_decision.t_rule_set_version
INSERT INTO `mor_risk_decision`.`t_rule_set_version` (`rule_set_version_id`, `decision_point_id`, `input_contract_code`, `input_contract_version`, `rule_set_code`, `rule_set_name`, `version_no`, `status`, `content_hash`, `content_format`, `created_by`, `change_reason`, `frozen_by`, `frozen_time`, `create_time`, `update_time`)
SELECT `rule_set_version_id`, `decision_point_id`, `input_contract_code`, `input_contract_version`, `rule_set_code`, `rule_set_name`, `version_no`, `status`, `content_hash`, `content_format`, `created_by`, `change_reason`, `frozen_by`, `frozen_time`, `create_time`, `update_time`
  FROM `mor_risk`.`t_rule_set_version`;

-- mor_risk.t_rule_subject_tag -> mor_risk_decision.t_rule_subject_tag
INSERT INTO `mor_risk_decision`.`t_rule_subject_tag` (`tag_id`, `tag_code`, `tag_name`, `description`, `status`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`)
SELECT `tag_id`, `tag_code`, `tag_name`, `description`, `status`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`
  FROM `mor_risk`.`t_rule_subject_tag`;

-- mor_risk.t_screening_hit -> mor_compliance.t_screening_hit
INSERT INTO `mor_compliance`.`t_screening_hit` (`hit_id`, `screening_run_id`, `external_hit_ref`, `match_type`, `match_score`, `status`, `name_vault_token`, `version`, `create_time`, `update_time`)
SELECT `hit_id`, `screening_run_id`, `external_hit_ref`, `match_type`, `match_score`, `status`, `name_vault_token`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_screening_hit`;

-- mor_risk.t_screening_resolution -> mor_compliance.t_screening_resolution
INSERT INTO `mor_compliance`.`t_screening_resolution` (`resolution_id`, `hit_id`, `resolution_version`, `resolution`, `reason_code`, `reviewer_id`, `resolved_time`, `evidence_file_id`, `evidence_content_hash`, `create_time`)
SELECT `resolution_id`, `hit_id`, `resolution_version`, `resolution`, `reason_code`, `reviewer_id`, `resolved_time`, `evidence_file_id`, `evidence_content_hash`, `create_time`
  FROM `mor_risk`.`t_screening_resolution`;

-- mor_risk.t_screening_run -> mor_compliance.t_screening_run
INSERT INTO `mor_compliance`.`t_screening_run` (`screening_run_id`, `case_subject_id`, `program_screening_id`, `provider_code`, `request_ref`, `status`, `started_time`, `completed_time`, `version`, `create_time`, `update_time`)
SELECT `screening_run_id`, `case_subject_id`, `program_screening_id`, `provider_code`, `request_ref`, `status`, `started_time`, `completed_time`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_screening_run`;

-- mor_risk.t_settle_control_state_change -> mor_compliance.t_settle_control_state_change
INSERT INTO `mor_compliance`.`t_settle_control_state_change` (`change_id`, `control_id`, `from_status`, `to_status`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`)
SELECT `change_id`, `control_id`, `from_status`, `to_status`, `reason_code`, `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`
  FROM `mor_risk`.`t_settle_control_state_change`;

-- mor_risk.t_settlement_control -> mor_compliance.t_settlement_control
INSERT INTO `mor_compliance`.`t_settlement_control` (`control_id`, `control_no`, `role_type`, `role_id`, `control_type`, `reason_code`, `source_action_effect_id`, `status`, `effective_from`, `effective_to`, `required_amount`, `required_currency`, `release_condition_code`, `control_instance_key`, `content_hash`, `active_slot`, `version`, `create_time`, `update_time`)
SELECT `control_id`, `control_no`, `role_type`, `role_id`, `control_type`, `reason_code`, `source_action_effect_id`, `status`, `effective_from`, `effective_to`, `required_amount`, `required_currency`, `release_condition_code`, `control_instance_key`, `content_hash`, `active_slot`, `version`, `create_time`, `update_time`
  FROM `mor_risk`.`t_settlement_control`;

-- mor_risk.t_settlement_control_scope -> mor_compliance.t_settlement_control_scope
INSERT INTO `mor_compliance`.`t_settlement_control_scope` (`scope_id`, `control_id`, `operating_entity_id`, `currency`, `bank_country`, `scope_hash`, `create_time`)
SELECT `scope_id`, `control_id`, `operating_entity_id`, `currency`, `bank_country`, `scope_hash`, `create_time`
  FROM `mor_risk`.`t_settlement_control_scope`;

-- mor_risk.t_settlement_risk_assessment -> mor_risk_decision.t_settlement_risk_assessment
INSERT INTO `mor_risk_decision`.`t_settlement_risk_assessment` (`assessment_id`, `evaluation_id`, `checkpoint_type`, `role_type`, `role_id`, `operating_entity_id`, `gross_amount`, `currency`, `bank_country`, `source_fact_type`, `source_fact_id`, `source_fact_version`, `input_hash`, `eligibility_decision`, `required_amount`, `required_currency`, `control_set_hash`, `create_time`)
SELECT `assessment_id`, `evaluation_id`, `checkpoint_type`, `role_type`, `role_id`, `operating_entity_id`, `gross_amount`, `currency`, `bank_country`, `source_fact_type`, `source_fact_id`, `source_fact_version`, `input_hash`, `eligibility_decision`, `required_amount`, `required_currency`, `control_set_hash`, `create_time`
  FROM `mor_risk`.`t_settlement_risk_assessment`;

-- mor_risk.t_transaction_risk_assessment -> mor_risk_decision.t_transaction_risk_assessment
INSERT INTO `mor_risk_decision`.`t_transaction_risk_assessment` (`assessment_id`, `evaluation_id`, `payment_intent_id`, `payment_attempt_id`, `risk_action_id`, `order_id`, `client_id`, `entity_no`, `payment_method_code`, `capture_mode`, `amount`, `currency`, `industry_code`, `mcc_code`, `consumer_country_code`, `consumer_reference`, `consumer_email_vault_token`, `consumer_phone_vault_token`, `client_ip_address`, `user_agent`, `card_fingerprint`, `card_bin`, `authentication_requirement`, `stable_input_hash`, `restricted_input_hash`, `create_time`)
SELECT `assessment_id`, `evaluation_id`, `payment_intent_id`, `payment_attempt_id`, `risk_action_id`, `order_id`, `client_id`, `entity_no`, `payment_method_code`, `capture_mode`, `amount`, `currency`, `industry_code`, `mcc_code`, `consumer_country_code`, `consumer_reference`, `consumer_email_vault_token`, `consumer_phone_vault_token`, `client_ip_address`, `user_agent`, `card_fingerprint`, `card_bin`, `authentication_requirement`, `stable_input_hash`, `restricted_input_hash`, `create_time`
  FROM `mor_risk`.`t_transaction_risk_assessment`;

-- mor_risk.t_velocity_event -> mor_risk_decision.t_velocity_event
INSERT INTO `mor_risk_decision`.`t_velocity_event` (`velocity_event_id`, `event_code`, `source_type`, `source_id`, `source_version`, `occurred_time`, `amount`, `currency`, `event_hash`, `create_time`)
SELECT `velocity_event_id`, `event_code`, `source_type`, `source_id`, `source_version`, `occurred_time`, `amount`, `currency`, `event_hash`, `create_time`
  FROM `mor_risk`.`t_velocity_event`;

-- mor_risk.t_velocity_event_subject -> mor_risk_decision.t_velocity_event_subject
INSERT INTO `mor_risk_decision`.`t_velocity_event_subject` (`event_subject_id`, `velocity_event_id`, `subject_type`, `subject_key`, `event_code`, `occurred_time`, `create_time`)
SELECT `event_subject_id`, `velocity_event_id`, `subject_type`, `subject_key`, `event_code`, `occurred_time`, `create_time`
  FROM `mor_risk`.`t_velocity_event_subject`;

-- mor_risk.t_velocity_feature_definition -> mor_risk_decision.t_velocity_feature_definition
INSERT INTO `mor_risk_decision`.`t_velocity_feature_definition` (`feature_version_id`, `event_code`, `anchor_subject_type`, `metric`, `distinct_subject_type`, `window_seconds`, `include_current`, `create_time`)
SELECT `feature_version_id`, `event_code`, `anchor_subject_type`, `metric`, `distinct_subject_type`, `window_seconds`, `include_current`, `create_time`
  FROM `mor_risk`.`t_velocity_feature_definition`;

COMMIT;
