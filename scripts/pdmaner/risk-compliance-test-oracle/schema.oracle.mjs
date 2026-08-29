export const APPROVED_TABLE_GROUPS = Object.freeze({
  due_diligence: Object.freeze([
    "t_dd_program",
    "t_dd_program_version",
    "t_dd_program_requirement",
    "t_dd_program_check",
    "t_dd_program_screening",
    "t_due_diligence_case",
    "t_dd_case_subject",
    "t_dd_case_dependency",
    "t_dd_party_snapshot",
    "t_dd_requirement",
    "t_dd_evidence_ref",
    "t_dd_check",
    "t_screening_run",
    "t_screening_hit",
    "t_screening_resolution",
    "t_dd_case_state_change",
    "t_qualification_decision",
    "t_role_qualification",
    "t_qualification_state_change",
  ]),
  decision_engine: Object.freeze([
    "t_decision_point",
    "t_decision_point_version",
    "t_decision_point_input_field",
    "t_decision_point_feature",
    "t_decision_point_rule_set",
    "t_decision_point_obligation",
    "t_feature_version",
    "t_feature_dependency",
    "t_rule_set_version",
    "t_risk_rule",
    "t_rule_node",
    "t_rule_operand",
    "t_action_version",
    "t_action_parameter_definition",
    "t_rule_action",
    "t_rule_action_parameter",
    "t_decision_release",
    "t_release_audit_event",
    "t_release_shadow_run",
  ]),
  evaluation: Object.freeze([
    "t_decision_evaluation",
    "t_evaluation_feature_result",
    "t_evaluation_rule_trace",
    "t_evaluation_action_intent",
    "t_action_effect",
    "t_checkout_input_snapshot",
    "t_transaction_risk_assessment",
    "t_settlement_risk_assessment",
  ]),
  risk_data: Object.freeze([
    "t_risk_restriction",
    "t_risk_list",
    "t_risk_list_entry",
    "t_payment_attempt_fact",
    "t_velocity_event",
    "t_velocity_event_subject",
  ]),
  limits: Object.freeze([
    "t_limit_version",
    "t_decision_point_limit_binding",
    "t_limit_position",
    "t_limit_reservation_group",
    "t_limit_reservation",
    "t_limit_group_state_change",
    "t_limit_movement",
  ]),
  settlement: Object.freeze([
    "t_settlement_control",
    "t_settlement_control_scope",
    "t_settle_control_state_change",
  ]),
  dispute: Object.freeze([
    "t_dispute_case",
    "t_dispute_state_change",
    "t_dispute_evidence_package",
    "t_dispute_evidence_ref",
    "t_dispute_submission_request",
    "t_dispute_outcome",
    "t_dispute_liability_decision",
  ]),
  reliability: Object.freeze([
    "t_compensation_event",
    "t_command_idempotency",
  ]),
});

export const APPROVED_TABLES = Object.freeze(Object.values(APPROVED_TABLE_GROUPS).flat());

export const APPROVED_DIAGRAMS = Object.freeze({
  due_diligence: "尽调与资格",
  decision_engine: "决策点与规则发布",
  evaluation: "评估与 Action",
  risk_data: "冷却、名单与风险数据",
  limits: "限额与占用",
  settlement: "结算资格控制",
  dispute: "争议与举证",
  reliability: "可靠执行",
});

export const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
export const EXPECTED_MODEL_DIGEST = "a067ea4a4663b7974140029809d265661088fa546f6fc929df7b247ebc3fb41c";
export const EXPECTED_STRUCTURAL_DIGEST = "3c30e37cc104324830c853f55577f0259df192fe4d350641d4a624217657d2cf";
export const EXPECTED_CHECK_POLICY_DIGEST = "0dcbabe97dd005282539024356e86c2a5c588097f4955aaae9143150867d9e01";

export const EXPECTED_TOTALS = Object.freeze({
  tables: 71,
  fields: 865,
  uniqueIndexes: 106,
  accessIndexes: 74,
  relations: 111,
  oneToOneRelations: 9,
  diagramGroups: 8,
});

export const EXPECTED_GROUP_SIZES = Object.freeze({
  due_diligence: 19,
  decision_engine: 19,
  evaluation: 8,
  risk_data: 6,
  limits: 7,
  settlement: 3,
  dispute: 7,
  reliability: 2,
});

export const EXPECTED_CHECK_COUNT = 83;

export const REQUIRED_COMPOSITE_FK_CONTRACTS = Object.freeze([
  Object.freeze({
    name: "fk_dispute_req_package_case",
    childTable: "t_dispute_submission_request",
    childFields: Object.freeze(["package_id", "dispute_case_id"]),
    parentTable: "t_dispute_evidence_package",
    parentFields: Object.freeze(["package_id", "dispute_case_id"]),
  }),
  Object.freeze({
    name: "fk_dispute_case_current_out",
    childTable: "t_dispute_case",
    childFields: Object.freeze(["current_outcome_id", "dispute_case_id"]),
    parentTable: "t_dispute_outcome",
    parentFields: Object.freeze(["outcome_id", "dispute_case_id"]),
  }),
  Object.freeze({
    name: "fk_dispute_out_prev_case",
    childTable: "t_dispute_outcome",
    childFields: Object.freeze(["supersedes_outcome_id", "dispute_case_id"]),
    parentTable: "t_dispute_outcome",
    parentFields: Object.freeze(["outcome_id", "dispute_case_id"]),
  }),
]);

// Settlement Eligibility contracts remain separate from the generated-model
// totals and digests so focused tests can name the approved logical target.
export const SETTLEMENT_ELIGIBILITY_TARGET = Object.freeze({
  tableKeys: Object.freeze([
    "settlement_control",
    "settlement_control_scope",
    "settlement_control_state_change",
  ]),
  totalTables: 71,
  diagramGroups: 8,
  controlFields: Object.freeze([
    "control_id", "control_no", "role_type", "role_id", "control_type", "reason_code",
    "source_action_effect_id", "status", "effective_from", "effective_to",
    "required_amount", "required_currency", "release_condition_code",
    "control_instance_key", "content_hash", "active_slot", "version",
  ]),
  scopeFields: Object.freeze([
    "scope_id", "control_id", "operating_entity_id", "currency", "bank_country", "scope_hash",
  ]),
  assessmentFields: Object.freeze([
    "checkpoint_type", "gross_amount", "bank_country", "eligibility_decision",
    "required_amount", "required_currency", "control_set_hash",
  ]),
  assessmentSourceFactMatrix: Object.freeze({
    PAYOUT_CREATION: Object.freeze(["PAYOUT"]),
    PAYOUT_FINAL_APPROVAL: Object.freeze(["PAYOUT"]),
    PAYOUT_EXECUTION_CREATION: Object.freeze(["PAYOUT_EXECUTION"]),
    PAYOUT_EXTERNAL_SUBMISSION: Object.freeze(["PAYOUT_EXECUTION"]),
  }),
  assessmentSourceFactTypes: Object.freeze([
    "PAYOUT", "PAYOUT_EXECUTION",
  ]),
  actionParameterUniqueKey: Object.freeze([
    "rule_action_id", "parameter_definition_id", "value_sequence",
  ]),
});

export const REQUIRED_FIELDS = Object.freeze({
  t_dd_program_version: Object.freeze([
    "program_version_id", "program_id", "version_no", "review_frequency_code",
    "status", "active_slot", "content_hash",
  ]),
  t_due_diligence_case: Object.freeze([
    "case_id", "case_no", "role_type", "role_id", "qualification_type",
    "dd_program_version_id", "case_origin", "scheduled_review_time",
    "source_namespace", "source_application_id", "source_submission_version",
    "source_submission_hash", "status", "version",
  ]),
  t_dd_case_subject: Object.freeze([
    "case_subject_id", "case_id", "party_id", "subject_role", "primary_slot",
    "source_profile_id", "source_profile_version_no", "source_relation_version_id",
    "source_relation_version_no", "ownership_percentage", "required_flag",
  ]),
  t_dd_case_dependency: Object.freeze([
    "dependency_id", "parent_case_id", "child_case_id", "dependency_type",
    "required_flag", "accepted_decision_id", "version", "update_time",
  ]),
  t_dd_evidence_ref: Object.freeze([
    "evidence_id", "requirement_id", "case_subject_id", "file_id",
    "content_hash", "status", "version", "update_time",
  ]),
  t_screening_run: Object.freeze([
    "screening_run_id", "case_subject_id", "program_screening_id", "provider_code",
    "request_ref", "status", "started_time", "completed_time", "version",
  ]),
  t_role_qualification: Object.freeze([
    "qualification_id", "role_type", "role_id", "qualification_type",
    "current_decision_id", "status", "review_frequency_code", "next_review_time",
    "review_status", "version",
  ]),
  t_screening_resolution: Object.freeze([
    "resolution_id", "hit_id", "resolution_version", "resolution",
    "evidence_file_id", "evidence_content_hash",
  ]),
  t_qualification_decision: Object.freeze([
    "decision_id", "case_id", "decision_version", "role_type", "role_id",
    "qualification_type", "decision_source", "outcome", "valid_from", "decided_time",
  ]),
  t_rule_node: Object.freeze([
    "node_id", "rule_id", "parent_node_id", "node_type", "group_operator",
    "comparison_operator", "sequence_no",
  ]),
  t_rule_operand: Object.freeze([
    "operand_id", "node_id", "operand_role", "value_sequence", "source_type",
    "feature_version_id", "input_field_id", "value_type", "string_value", "number_value",
    "boolean_value", "datetime_value", "duration_value",
  ]),
  t_feature_version: Object.freeze([
    "feature_version_id", "feature_code", "feature_name", "business_category", "version_no",
  ]),
  t_action_version: Object.freeze([
    "action_version_id", "action_code", "action_name", "action_category", "version_no",
    "implementation_hash",
  ]),
  t_rule_set_version: Object.freeze([
    "rule_set_version_id", "decision_point_id", "rule_set_code", "rule_set_name", "version_no",
  ]),
  t_decision_release: Object.freeze([
    "release_id", "decision_point_id", "point_version_id", "environment_code",
    "status", "active_slot", "content_hash", "version",
  ]),
  t_decision_point_obligation: Object.freeze([
    "obligation_id", "point_version_id", "trigger_decision", "obligation_type",
    "action_version_id", "sequence_no", "failure_policy", "enabled_flag",
  ]),
  t_release_shadow_run: Object.freeze([
    "shadow_run_id", "baseline_release_id", "candidate_release_id", "status",
    "sample_target", "sample_count", "allow_to_review_count", "allow_to_deny_count",
    "deny_to_allow_count", "action_change_count", "feature_missing_count", "failed_count",
    "timed_out_count", "p95_millis", "p99_millis", "check_status", "version",
  ]),
  t_release_audit_event: Object.freeze([
    "audit_event_id", "release_id", "event_type", "event_time", "check_code", "check_status",
    "severity", "error_code", "error_summary", "shadow_run_id", "approval_request_id",
    "approval_status", "reviewer_id", "review_comment", "from_status", "to_status",
    "reason_code", "trigger_type", "trigger_id", "actor_id", "aggregate_version",
  ]),
  t_decision_evaluation: Object.freeze([
    "evaluation_id", "evaluation_request_id", "release_id", "evaluation_mode",
    "technical_status", "proposed_decision", "business_decision", "input_snapshot_hash",
    "shadow_run_id", "baseline_evaluation_id", "decision_changed_flag", "action_changed_flag",
    "comparison_severity", "compared_time",
  ]),
  t_evaluation_rule_trace: Object.freeze([
    "trace_id", "evaluation_id", "trace_type", "rule_set_version_id", "rule_id", "node_id",
    "execution_sequence", "result_value", "terminal_flag", "elapsed_millis", "failure_code",
    "rule_result_slot", "predicate_result_slot",
  ]),
  t_evaluation_action_intent: Object.freeze([
    "intent_id", "evaluation_id", "source_type", "rule_action_id", "obligation_id",
    "action_version_id", "resolved_target_type", "resolved_target_key", "intent_sequence",
    "merged_effect_id", "status",
  ]),
  t_action_effect: Object.freeze([
    "effect_id", "evaluation_id", "action_version_id", "effect_mode", "target_type",
    "target_key", "merge_policy", "effect_hash", "effect_status", "idempotency_key",
    "execution_type", "execution_status", "attempt_count", "started_time", "completed_time",
    "last_error_code", "last_error_summary",
  ]),
  t_risk_list_entry: Object.freeze([
    "entry_id", "list_id", "subject_type", "subject_key", "source_effect_id",
    "status", "active_slot", "effective_from", "effective_to", "supersedes_entry_id", "version",
  ]),
  t_checkout_input_snapshot: Object.freeze([
    "snapshot_id", "evaluation_id", "order_id", "order_item_id", "client_id",
    "operating_entity_id", "assignment_id", "industry_code", "mcc_code", "amount", "currency", "input_hash",
  ]),
  t_transaction_risk_assessment: Object.freeze([
    "assessment_id", "evaluation_id", "payment_attempt_id", "order_context_id", "client_id",
    "operating_entity_id", "payment_method_code", "amount", "currency", "restricted_input_hash",
  ]),
  t_payment_attempt_fact: Object.freeze([
    "fact_id", "payment_attempt_id", "attempt_status", "limit_reservation_group_id",
    "source_event_key", "source_version", "source_payload_hash", "version",
  ]),
  t_limit_version: Object.freeze([
    "limit_version_id", "limit_code", "limit_name", "version_no", "subject_type",
    "metric_type", "window_unit", "window_size", "amount_limit", "count_limit",
    "currency_mode", "fixed_currency", "content_hash", "status", "frozen_by", "frozen_time",
  ]),
  t_decision_point_limit_binding: Object.freeze([
    "binding_id", "obligation_id", "limit_version_id", "sequence_no",
    "subject_source_type", "subject_feature_version_id", "subject_input_field_id",
    "amount_source_type", "amount_feature_version_id", "amount_input_field_id",
  ]),
  t_limit_position: Object.freeze([
    "position_id", "limit_version_id", "subject_key", "currency_scope_key", "window_start", "window_end",
    "reserved_amount", "committed_amount", "reserved_count", "committed_count", "version",
  ]),
  t_limit_reservation_group: Object.freeze([
    "reservation_group_id", "assessment_id", "obligation_id", "status", "hold_reason",
    "resolved_time", "version",
  ]),
  t_limit_reservation: Object.freeze([
    "reservation_id", "reservation_group_id", "position_id", "amount", "count_value", "create_time",
  ]),
  t_limit_group_state_change: Object.freeze([
    "change_id", "reservation_group_id", "from_status", "to_status", "trigger_type",
    "trigger_id", "aggregate_version", "occurred_time",
  ]),
  t_limit_movement: Object.freeze([
    "movement_id", "reservation_id", "movement_type", "amount_delta", "count_delta",
    "trigger_type", "trigger_id", "occurred_time", "position_version",
  ]),
  t_settlement_control: Object.freeze([
    "control_id", "control_no", "role_type", "role_id", "control_type", "reason_code",
    "source_action_effect_id", "status", "effective_from", "effective_to",
    "required_amount", "required_currency", "release_condition_code",
    "control_instance_key", "content_hash", "active_slot", "version",
  ]),
  t_settlement_control_scope: Object.freeze([
    "scope_id", "control_id", "operating_entity_id", "currency", "bank_country", "scope_hash",
  ]),
  t_settle_control_state_change: Object.freeze([
    "change_id", "control_id", "from_status", "to_status", "reason_code", "trigger_type",
    "trigger_id", "actor_id", "aggregate_version", "occurred_time",
  ]),
  t_settlement_risk_assessment: Object.freeze([
    "assessment_id", "evaluation_id", "checkpoint_type", "role_type", "role_id",
    "operating_entity_id", "gross_amount", "currency", "bank_country", "source_fact_type",
    "source_fact_id", "source_fact_version", "input_hash", "eligibility_decision",
    "required_amount", "required_currency", "control_set_hash",
  ]),
  t_dispute_case: Object.freeze([
    "dispute_case_id", "provider_dispute_notice_id", "payment_attempt_id", "order_id",
    "client_id", "disputed_amount", "currency", "client_response_due_time", "provider_response_due_time", "version",
  ]),
  t_dispute_liability_decision: Object.freeze([
    "outcome_id", "decision_evaluation_id", "action_effect_id",
    "principal_bearer", "provider_fee_bearer", "platform_error_flag", "provider_recovery_required",
    "security_deposit_use_allowed",
  ]),
  t_compensation_event: Object.freeze([
    "id", "event_key", "event_type", "execution_type", "source_type", "source_id", "source_version",
    "schema_version", "payload_json", "payload_hash", "execute_status", "execute_count",
    "next_execute_at", "lock_owner", "lock_until", "lock_version",
  ]),
  t_command_idempotency: Object.freeze([
    "idempotency_id", "caller_scope", "operation_type", "idempotency_key",
    "request_hash", "response_ref", "status", "expire_time",
  ]),
});

export const REMOVED_DD_FIELDS = Object.freeze({
  t_due_diligence_case: Object.freeze(["case_type", "current_decision_id", "source_system"]),
  t_dd_program_requirement: Object.freeze(["validity_days"]),
  t_dd_program_screening: Object.freeze(["frequency_days"]),
  t_dd_case_subject: Object.freeze(["source_profile_version", "source_relation_id", "source_relation_version"]),
  t_dd_party_snapshot: Object.freeze(["vault_record_version", "vault_metadata_hash"]),
  t_dd_requirement: Object.freeze(["requirement_code", "requirement_type"]),
  t_dd_evidence_ref: Object.freeze(["case_id", "party_id", "document_type", "document_version"]),
  t_dd_check: Object.freeze(["case_id", "check_type"]),
  t_screening_run: Object.freeze(["case_id", "screening_type"]),
  t_screening_hit: Object.freeze(["current_resolution_id"]),
  t_screening_resolution: Object.freeze(["supersedes_resolution_id"]),
  t_qualification_decision: Object.freeze(["supersedes_decision_id"]),
});

export const REQUIRED_DD_INDEXES = Object.freeze({
  t_due_diligence_case: Object.freeze({
    uk_dd_case_source: Object.freeze([
      "source_namespace",
      "source_application_id",
      "role_type",
      "role_id",
      "qualification_type",
    ]),
    uk_dd_case_active: Object.freeze(["role_type", "role_id", "qualification_type", "active_slot"]),
    uk_dd_case_review: Object.freeze(["role_type", "role_id", "qualification_type", "scheduled_review_time"]),
  }),
  t_dd_case_subject: Object.freeze({
    uk_dd_case_primary: Object.freeze(["case_id", "primary_slot"]),
    uk_dd_subject_relation: Object.freeze(["case_id", "source_relation_version_id"]),
  }),
  t_dd_case_dependency: Object.freeze({
    uk_dd_case_dep: Object.freeze(["parent_case_id", "child_case_id", "dependency_type"]),
  }),
  t_dd_requirement: Object.freeze({
    uk_dd_req_case: Object.freeze(["case_id", "program_requirement_id"]),
  }),
  t_dd_evidence_ref: Object.freeze({
    uk_dd_evidence_req_file: Object.freeze(["requirement_id", "file_id"]),
    idx_dd_evidence_subject: Object.freeze(["case_subject_id"]),
  }),
  t_role_qualification: Object.freeze({
    idx_role_qual_review: Object.freeze(["review_status", "next_review_time"]),
  }),
});

export const REQUIRED_DD_CHECK_CONTRACTS = Object.freeze({
  ck_program_active_slot: Object.freeze({
    expression: "(status='ACTIVE' AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('DRAFT','RETIRED') AND active_slot IS NULL)",
    tables: Object.freeze(["dd_program_version"]),
  }),
  ck_program_approval: Object.freeze({
    expression: "(status='DRAFT' AND approved_by IS NULL AND approved_time IS NULL) OR (status IN ('ACTIVE','RETIRED') AND approved_by IS NOT NULL AND approved_time IS NOT NULL)",
    tables: Object.freeze(["dd_program_version"]),
  }),
  ck_program_version_no: Object.freeze({
    expression: "version_no>0",
    tables: Object.freeze(["dd_program_version"]),
  }),
  ck_program_check_validity: Object.freeze({
    expression: "validity_days IS NULL OR validity_days>0",
    tables: Object.freeze(["dd_program_check"]),
  }),
  ck_dd_case_active_slot: Object.freeze({
    expression: "(status IN ('DRAFT','SUBMITTED','IN_REVIEW','WAITING_EVIDENCE') AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('APPROVED','REJECTED','WITHDRAWN','EXPIRED') AND active_slot IS NULL)",
    tables: Object.freeze(["due_diligence_case"]),
  }),
  ck_dd_case_times: Object.freeze({
    expression: "(status='DRAFT' AND submitted_time IS NULL AND decision_time IS NULL) OR (status IN ('SUBMITTED','IN_REVIEW','WAITING_EVIDENCE') AND submitted_time IS NOT NULL AND decision_time IS NULL) OR (status IN ('APPROVED','REJECTED') AND submitted_time IS NOT NULL AND decision_time IS NOT NULL AND submitted_time<=decision_time) OR (status IN ('WITHDRAWN','EXPIRED') AND decision_time IS NULL)",
    tables: Object.freeze(["due_diligence_case"]),
  }),
  ck_dd_case_tuple: Object.freeze({
    expression: "(role_type='CLIENT' AND qualification_type IN ('INDIVIDUAL_KYC','ORGANIZATION_KYB')) OR (role_type='LOCAL_PARTNER' AND qualification_type='PARTNER_DUE_DILIGENCE') OR (role_type='OPERATING_ENTITY' AND qualification_type='ENTITY_UNDERWRITING') OR (role_type='RELATED_PERSON' AND qualification_type='RELATED_PERSON_KYC')",
    tables: Object.freeze(["due_diligence_case"]),
  }),
  ck_dd_case_origin: Object.freeze({
    expression: "(case_origin='APPLICATION' AND ((source_namespace='CCT_CLIENT_APPLICATION' AND role_type IN ('CLIENT','RELATED_PERSON')) OR (source_namespace='CCT_OPERATING_ENTITY_APPLICATION' AND role_type IN ('OPERATING_ENTITY','RELATED_PERSON'))) AND source_application_id IS NOT NULL AND source_submission_version IS NOT NULL AND source_submission_version>0 AND source_submission_hash IS NOT NULL AND scheduled_review_time IS NULL) OR (case_origin='CONTINUOUS_REVIEW' AND source_namespace='RISK_COMPLIANCE' AND source_application_id IS NULL AND source_submission_version IS NULL AND source_submission_hash IS NULL AND scheduled_review_time IS NOT NULL) OR (case_origin='MANUAL' AND source_namespace='RISK_COMPLIANCE' AND source_application_id IS NULL AND source_submission_version IS NULL AND source_submission_hash IS NULL AND scheduled_review_time IS NULL)",
    tables: Object.freeze(["due_diligence_case"]),
  }),
  ck_dd_subject_relation: Object.freeze({
    expression: "(subject_role='PRIMARY' AND primary_slot IS NOT NULL AND primary_slot=1 AND source_relation_version_id IS NULL AND source_relation_version_no IS NULL AND ownership_percentage IS NULL) OR (subject_role='UBO' AND primary_slot IS NULL AND source_relation_version_id IS NOT NULL AND source_relation_version_no IS NOT NULL AND source_relation_version_no>0) OR (subject_role IN ('DIRECTOR','SIGNATORY') AND primary_slot IS NULL AND source_relation_version_id IS NOT NULL AND source_relation_version_no IS NOT NULL AND source_relation_version_no>0 AND ownership_percentage IS NULL)",
    tables: Object.freeze(["dd_case_subject"]),
  }),
  ck_dd_subject_ownership: Object.freeze({
    expression: "ownership_percentage IS NULL OR (ownership_percentage>0 AND ownership_percentage<=100)",
    tables: Object.freeze(["dd_case_subject"]),
  }),
  ck_dd_subject_profile_version: Object.freeze({
    expression: "source_profile_version_no>0",
    tables: Object.freeze(["dd_case_subject"]),
  }),
  ck_snapshot_schema_version: Object.freeze({
    expression: "schema_version>0",
    tables: Object.freeze(["dd_party_snapshot"]),
  }),
  ck_dd_check_validity: Object.freeze({
    expression: "valid_until IS NULL OR performed_time<valid_until",
    tables: Object.freeze(["dd_check"]),
  }),
  ck_screen_run_times: Object.freeze({
    expression: "(status='STARTED' AND completed_time IS NULL) OR (status IN ('SUCCEEDED','FAILED','TIMED_OUT') AND completed_time IS NOT NULL AND started_time<=completed_time)",
    tables: Object.freeze(["screening_run"]),
  }),
  ck_screen_resolution_version: Object.freeze({
    expression: "resolution_version>0",
    tables: Object.freeze(["screening_resolution"]),
  }),
  ck_qual_decision_version: Object.freeze({
    expression: "decision_version>0",
    tables: Object.freeze(["qualification_decision"]),
  }),
  ck_aggregate_version: Object.freeze({
    expression: "aggregate_version>0",
    tables: Object.freeze(["dd_case_state_change", "qualification_state_change"]),
  }),
  ck_state_change_initial: Object.freeze({
    expression: "(aggregate_version=1 AND from_status IS NULL) OR (aggregate_version>1 AND from_status IS NOT NULL)",
    tables: Object.freeze(["dd_case_state_change", "qualification_state_change"]),
  }),
  ck_qual_decision_source: Object.freeze({
    expression: "(decision_source='AUTOMATED' AND decision_evaluation_id IS NOT NULL AND ((recommended_decision='APPROVE' AND outcome='APPROVED') OR (recommended_decision='REJECT' AND outcome='REJECTED')) AND override_reason_code IS NULL) OR (decision_source='REVIEWER' AND decided_by IS NOT NULL AND override_reason_code IS NULL AND (recommended_decision IS NULL OR recommended_decision='REVIEW' OR (recommended_decision='APPROVE' AND outcome='APPROVED') OR (recommended_decision='REJECT' AND outcome='REJECTED'))) OR (decision_source='OVERRIDE' AND decision_evaluation_id IS NOT NULL AND recommended_decision IS NOT NULL AND override_reason_code IS NOT NULL AND decided_by IS NOT NULL)",
    tables: Object.freeze(["qualification_decision"]),
  }),
  ck_role_qual_review: Object.freeze({
    expression: "(review_status='NOT_REQUIRED' AND status='APPROVED' AND review_frequency_code IS NULL AND next_review_time IS NULL) OR (review_status IN ('ACTIVE','PAUSED') AND status='APPROVED' AND review_frequency_code IS NOT NULL AND next_review_time IS NOT NULL) OR (review_status='COMPLETED' AND status IN ('REJECTED','REVOKED','EXPIRED') AND next_review_time IS NULL)",
    tables: Object.freeze(["role_qualification"]),
  }),
});

export const REQUIRED_DD_FIELD_SHAPES = Object.freeze({
  t_due_diligence_case: Object.freeze({
    source_submission_version: Object.freeze({ type: "BIGINT", notNull: false }),
    source_submission_hash: Object.freeze({ type: "CHAR", notNull: false }),
    version: Object.freeze({ type: "BIGINT", notNull: true }),
  }),
  t_dd_case_subject: Object.freeze({
    source_profile_version_no: Object.freeze({ type: "BIGINT", notNull: true }),
    source_relation_version_id: Object.freeze({ type: "BIGINT", notNull: false }),
    source_relation_version_no: Object.freeze({ type: "BIGINT", notNull: false }),
  }),
  t_dd_case_dependency: Object.freeze({ version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_dd_requirement: Object.freeze({ version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_dd_evidence_ref: Object.freeze({ version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_screening_run: Object.freeze({ version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_screening_hit: Object.freeze({ version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_dd_case_state_change: Object.freeze({ aggregate_version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_role_qualification: Object.freeze({ version: Object.freeze({ type: "BIGINT", notNull: true }) }),
  t_qualification_state_change: Object.freeze({ aggregate_version: Object.freeze({ type: "BIGINT", notNull: true }) }),
});

export const REQUIRED_DD_RELATIONS = Object.freeze([
  Object.freeze({ childTable: "t_dd_evidence_ref", childField: "case_subject_id", parentTable: "t_dd_case_subject", parentField: "case_subject_id" }),
]);

export const REQUIRED_DD_ENUMS = Object.freeze({
  t_dd_program: Object.freeze({ qualification_type: Object.freeze(["INDIVIDUAL_KYC", "ORGANIZATION_KYB", "PARTNER_DUE_DILIGENCE", "ENTITY_UNDERWRITING", "RELATED_PERSON_KYC"]) }),
  t_due_diligence_case: Object.freeze({
    qualification_type: Object.freeze(["INDIVIDUAL_KYC", "ORGANIZATION_KYB", "PARTNER_DUE_DILIGENCE", "ENTITY_UNDERWRITING", "RELATED_PERSON_KYC"]),
    source_namespace: Object.freeze(["CCT_CLIENT_APPLICATION", "CCT_OPERATING_ENTITY_APPLICATION", "RISK_COMPLIANCE"]),
  }),
  t_screening_hit: Object.freeze({ status: Object.freeze(["OPEN", "RESOLVED"]) }),
  t_qualification_decision: Object.freeze({ qualification_type: Object.freeze(["INDIVIDUAL_KYC", "ORGANIZATION_KYB", "PARTNER_DUE_DILIGENCE", "ENTITY_UNDERWRITING", "RELATED_PERSON_KYC"]) }),
  t_role_qualification: Object.freeze({ qualification_type: Object.freeze(["INDIVIDUAL_KYC", "ORGANIZATION_KYB", "PARTNER_DUE_DILIGENCE", "ENTITY_UNDERWRITING", "RELATED_PERSON_KYC"]) }),
});

export const REQUIRED_DD_COMMENT_FRAGMENTS = Object.freeze({
  t_dd_program_version: Object.freeze({
    content_hash: Object.freeze(["Canonical Schema", "材料、检查和筛查模板"]),
  }),
  t_due_diligence_case: Object.freeze({
    source_namespace: Object.freeze(["CCT Client Application", "CCT Operating Entity Application"]),
    source_submission_version: Object.freeze(["仅 APPLICATION", "CCT Application Submission"]),
  }),
  t_dd_case_subject: Object.freeze({
    source_relation_version_id: Object.freeze(["CCT Party Relation Version"]),
    ownership_percentage: Object.freeze(["百分比", "大于 0", "不超过 100"]),
  }),
  t_dd_party_snapshot: Object.freeze({
    schema_version: Object.freeze(["RISK_DD_PARTY_SNAPSHOT_V1"]),
    source_content_hash: Object.freeze(["规范化来源 Payload"]),
    snapshot_vault_token: Object.freeze(["授权 Reveal", "COMPLIANCE_REVIEW"]),
    captured_time: Object.freeze(["来源快照采集"]),
  }),
  t_dd_check: Object.freeze({
    result_vault_token: Object.freeze(["RISK_DD_CHECK_RESULT_V1", "COMPLIANCE_REVIEW"]),
  }),
  t_screening_hit: Object.freeze({
    name_vault_token: Object.freeze(["RISK_SCREENING_HIT_NAME_V1", "COMPLIANCE_REVIEW"]),
  }),
});

export const REQUIRED_DD_POSITIVE_VERSION_TABLES = Object.freeze([
  "dd_case_dependency",
  "dd_evidence_ref",
  "screening_run",
]);

export const REQUIRED_DD_VAULT_TOKEN_REFERENCES = Object.freeze([
  Object.freeze({ table: "dd_party_snapshot", token: "snapshot_vault_token" }),
  Object.freeze({ table: "dd_check", token: "result_vault_token" }),
  Object.freeze({ table: "screening_hit", token: "name_vault_token" }),
]);

export const FORBIDDEN_TABLES = Object.freeze([
  "t_limit_definition",
  "t_limit_plan",
  "t_limit_plan_version",
  "t_limit_plan_item",
  "t_feature_definition",
  "t_action_definition",
  "t_rule_set",
  "t_rule_operand_value",
  "t_release_static_check",
  "t_release_approval",
  "t_release_state_change",
  "t_release_shadow_summary",
  "t_release_shadow_comparison",
  "t_evaluation_rule_result",
  "t_evaluation_predicate_result",
  "t_action_execution",
  "t_dd_party_relation_snapshot",
  "t_continuous_review_plan",
  "t_continuous_review_run",
  "t_velocity_bucket",
  "t_rule_script",
  "t_rule_sql",
  "t_release_traffic_scope",
  "t_release_gray_config",
  "t_generic_case",
  "t_generic_rfi",
  "t_inbox_event",
  "t_outbox_event",
  "t_retention_policy",
]);

export const FILE_REFERENCE_TUPLES = Object.freeze([
  Object.freeze({ table: "t_dd_evidence_ref", id: "file_id", hash: "content_hash" }),
  Object.freeze({ table: "t_screening_resolution", id: "evidence_file_id", hash: "evidence_content_hash" }),
  Object.freeze({ table: "t_dispute_evidence_ref", id: "file_id", hash: "content_hash" }),
]);

export const FORBIDDEN_FIELD_PATTERN = /(?:^|_)(?:pan|cvv|cvc|track_data|pin_block|card_expiry|password|private_key|jdbc_url)(?:_|$)/iu;
