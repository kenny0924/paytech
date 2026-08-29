export const APPROVED_TABLE_GROUPS = Object.freeze({
  identity_access: Object.freeze([
    "t_idp_connection", "t_access_tenant", "t_user_identity", "t_tenant_member",
    "t_role", "t_permission", "t_member_role", "t_role_permission",
    "t_api_credential", "t_api_credential_scope", "t_credential_event",
    "t_client_integration_readiness", "t_integration_readiness_eval",
    "t_integration_readiness_check", "t_email_otp_challenge", "t_email_otp_attempt",
  ]),
  file_document: Object.freeze([
    "t_file_upload", "t_file_scan_policy", "t_file_scan_policy_ver",
    "t_file_scan_requirement", "t_file_object", "t_file_owner_ref",
    "t_file_scan_result", "t_file_state_change", "t_document_template",
    "t_document_template_ver", "t_document_template_translation", "t_document_generation",
    "t_doc_generation_state_change", "t_consumer_document", "t_document_version",
    "t_document_render_job", "t_document_delivery", "t_document_access_grant",
    "t_document_state_change",
  ]),
  notification: Object.freeze([
    "t_notification_provider", "t_notification_provider_ver", "t_notification_request",
    "t_notification_recipient", "t_notification_message", "t_notification_delivery",
    "t_notification_attempt", "t_notification_route", "t_notification_provider_callback",
    "t_notification_suppression", "t_notification_template", "t_notification_template_ver",
    "t_notify_template_translation", "t_notify_delivery_state_change",
  ]),
  webhook: Object.freeze([
    "t_webhook_endpoint", "t_webhook_endpoint_ver", "t_webhook_endpoint_verification",
    "t_webhook_secret_ver", "t_webhook_subscription", "t_webhook_sub_state_change",
    "t_webhook_event", "t_webhook_delivery", "t_webhook_attempt", "t_webhook_replay",
    "t_webhook_delivery_state_change",
  ]),
  audit_security: Object.freeze([
    "t_audit_chain", "t_audit_event", "t_audit_event_attr", "t_audit_integrity_point",
    "t_audit_archive_batch", "t_security_event", "t_security_alert",
    "t_security_alert_event", "t_security_alert_action",
  ]),
  gateway: Object.freeze([
    "t_gateway_route", "t_gateway_route_ver", "t_gateway_policy", "t_gateway_policy_ver",
    "t_gateway_guard_rule", "t_gateway_policy_binding", "t_gateway_policy_binding_ver",
    "t_gateway_selector", "t_gateway_release", "t_gateway_release_item",
    "t_gateway_release_approval", "t_gateway_release_state_change", "t_gateway_instance_ack",
  ]),
  technical_config: Object.freeze([
    "t_config_namespace", "t_config_item", "t_config_version", "t_config_change_set",
    "t_config_change_item", "t_config_approval", "t_config_release",
    "t_config_release_state_change",
  ]),
  reliability: Object.freeze(["t_compensation_event", "t_command_idempotency"]),
});

export const EXPECTED_GROUP_SIZES = Object.freeze({
  identity_access: 16,
  file_document: 19,
  notification: 14,
  webhook: 11,
  audit_security: 9,
  gateway: 13,
  technical_config: 8,
  reliability: 2,
});

export const APPROVED_TABLES = Object.freeze(Object.values(APPROVED_TABLE_GROUPS).flat());

export const APPROVED_DIAGRAMS = Object.freeze({
  identity_access: "身份、授权与接入",
  file_document: "文件与消费者文档",
  notification: "通知与 Email",
  webhook: "Client Webhook",
  audit_security: "审计与安全",
  gateway: "Gateway 保护配置",
  technical_config: "Platform 技术配置",
  reliability: "可靠执行",
});

export const FORBIDDEN_TABLES = Object.freeze([
  "t_resource_grant", "t_file_access_grant", "t_file_access_event", "t_document_source_ref",
  "t_notification_preference", "t_audit_access_event", "t_dictionary_set",
  "t_dictionary_version", "t_dictionary_entry", "t_dictionary_translation",
  "t_dictionary_release", "t_job_definition", "t_job_schedule", "t_job_run",
  "t_job_attempt", "t_job_lease",
]);

export const REQUIRED_FIELDS = Object.freeze({
  t_member_role: Object.freeze(["tenant_member_id", "tenant_id", "role_id", "role_scope_key", "status", "version"]),
  t_role_permission: Object.freeze(["role_id", "permission_id", "status", "version", "update_time"]),
  t_user_identity: Object.freeze([
    "idp_connection_id", "idp_subject", "email_vault_token", "email_blind_index",
    "email_blind_key_ver", "email_masked", "display_name_vault_token",
    "display_name_masked", "status", "version",
  ]),
  t_email_otp_challenge: Object.freeze([
    "tenant_scope_key", "active_scope_hash", "email_vault_token", "email_blind_index",
    "email_blind_key_ver", "code_hmac", "pepper_key_ver", "active_slot", "version",
  ]),
  t_file_object: Object.freeze([
    "scan_policy_ver_id", "scan_policy_hash", "scan_status", "object_status", "version",
  ]),
  t_file_scan_result: Object.freeze(["file_id", "scan_requirement_id", "scan_policy_ver_id", "attempt_no"]),
  t_document_generation: Object.freeze([
    "generation_key_version", "document_generation_key", "generation_scope_hash",
    "commerce_request_event_key", "commerce_source_version", "commerce_source_payload_hash",
    "fiscal_event_key", "fiscal_record_id", "fiscal_source_version",
    "fiscal_source_payload_hash", "status", "version",
  ]),
  t_consumer_document: Object.freeze([
    "generation_id", "client_id", "order_id", "document_type", "corrects_document_id",
    "current_ver_id", "status", "version",
  ]),
  t_document_version: Object.freeze([
    "document_id", "document_ver", "supersedes_version_id", "file_id",
    "source_set_hash", "content_hash", "status",
  ]),
  t_notification_request: Object.freeze(["tenant_scope_key", "source_event_key", "source_version", "source_payload_hash"]),
  t_notification_recipient: Object.freeze(["address_vault_token", "address_blind_index", "address_blind_key_ver", "address_masked"]),
  t_webhook_endpoint_ver: Object.freeze(["url_vault_token", "url_masked", "host_name", "content_hash"]),
  t_notification_provider_callback: Object.freeze([
    "verification_provider_ver_id", "verification_time", "verification_failure_code",
    "notification_attempt_id", "notification_delivery_id",
  ]),
  t_webhook_event: Object.freeze([
    "payload_storage_mode", "payload_inline", "payload_vault_token", "payload_schema_version", "payload_hash",
  ]),
  t_audit_event_attr: Object.freeze(["value_text", "value_vault_token", "value_masked", "value_hash", "classification"]),
  t_gateway_release: Object.freeze(["items_frozen_time", "activation_slot", "active_slot", "content_hash", "version"]),
  t_gateway_release_item: Object.freeze(["release_id", "item_no", "item_hash", "update_time"]),
  t_config_version: Object.freeze(["storage_mode", "inline_value", "value_vault_token", "secret_ref", "value_hash"]),
  t_config_change_set: Object.freeze(["items_frozen_time", "content_hash", "status", "version"]),
  t_config_change_item: Object.freeze(["change_set_id", "config_item_id", "item_hash", "update_time"]),
  t_compensation_event: Object.freeze(["lock_owner", "lock_until", "lock_version", "execute_status"]),
  t_command_idempotency: Object.freeze(["caller_scope", "operation_type", "idempotency_key", "request_hash", "status"]),
});

export const FORBIDDEN_FIELDS_BY_TABLE = Object.freeze({
  t_user_identity: Object.freeze(["email_vault_record_version", "email_vault_metadata_hash", "display_name_vault_rec_ver", "display_name_vault_metadata_hash"]),
  t_email_otp_challenge: Object.freeze(["vault_record_version", "vault_metadata_hash"]),
  t_notification_recipient: Object.freeze(["vault_record_version", "vault_metadata_hash"]),
  t_notification_message: Object.freeze(["vault_record_version", "vault_metadata_hash"]),
  t_webhook_endpoint_ver: Object.freeze(["url_blind_index", "url_blind_index_key_version", "vault_record_version", "vault_metadata_hash"]),
  t_webhook_event: Object.freeze(["vault_record_version", "vault_metadata_hash"]),
  t_notification_provider_callback: Object.freeze(["verified_time"]),
  t_audit_event_attr: Object.freeze(["value_blind_index", "value_blind_index_key_version", "vault_record_version", "vault_metadata_hash"]),
  t_compensation_event: Object.freeze(["version"]),
  t_command_idempotency: Object.freeze(["version"]),
});

export const FILE_REFERENCE_TUPLES = Object.freeze([
  Object.freeze({ table: "t_document_version", id: "file_id", hash: "content_hash" }),
  Object.freeze({ table: "t_audit_integrity_point", id: "file_id", hash: "content_hash" }),
  Object.freeze({ table: "t_audit_archive_batch", id: "file_id", hash: "content_hash" }),
]);

// These are physical composite-FK contracts. PDManer draws one edge for each
// logical relationship, while the generated entity properties preserve every
// participating column for later Migration/Testcontainers enforcement.
export const REQUIRED_COMPOSITE_FKS = Object.freeze([
  Object.freeze({ name: "fk_member_role_member", childTable: "t_member_role", childFields: Object.freeze(["tenant_member_id", "tenant_id"]), parentTable: "t_tenant_member", parentFields: Object.freeze(["member_id", "tenant_id"]) }),
  Object.freeze({ name: "fk_member_role_role_scope", childTable: "t_member_role", childFields: Object.freeze(["role_id", "role_scope_key"]), parentTable: "t_role", parentFields: Object.freeze(["role_id", "scope_key"]) }),
  Object.freeze({ name: "fk_scan_policy_current", childTable: "t_file_scan_policy", childFields: Object.freeze(["current_ver_id", "policy_id"]), parentTable: "t_file_scan_policy_ver", parentFields: Object.freeze(["policy_ver_id", "policy_id"]) }),
  Object.freeze({ name: "fk_scan_result_file_policy", childTable: "t_file_scan_result", childFields: Object.freeze(["file_id", "scan_policy_ver_id"]), parentTable: "t_file_object", parentFields: Object.freeze(["file_id", "scan_policy_ver_id"]) }),
  Object.freeze({ name: "fk_scan_result_requirement", childTable: "t_file_scan_result", childFields: Object.freeze(["scan_requirement_id", "scan_policy_ver_id"]), parentTable: "t_file_scan_requirement", parentFields: Object.freeze(["requirement_id", "policy_ver_id"]) }),
  Object.freeze({ name: "fk_doc_template_current", childTable: "t_document_template", childFields: Object.freeze(["current_ver_id", "template_id"]), parentTable: "t_document_template_ver", parentFields: Object.freeze(["template_ver_id", "template_id"]) }),
  Object.freeze({ name: "fk_consumer_doc_correction", childTable: "t_consumer_document", childFields: Object.freeze(["corrects_document_id", "client_id", "order_id"]), parentTable: "t_consumer_document", parentFields: Object.freeze(["document_id", "client_id", "order_id"]) }),
  Object.freeze({ name: "fk_consumer_doc_current", childTable: "t_consumer_document", childFields: Object.freeze(["current_ver_id", "document_id"]), parentTable: "t_document_version", parentFields: Object.freeze(["document_ver_id", "document_id"]) }),
  Object.freeze({ name: "fk_doc_ver_supersedes", childTable: "t_document_version", childFields: Object.freeze(["supersedes_version_id", "document_id"]), parentTable: "t_document_version", parentFields: Object.freeze(["document_ver_id", "document_id"]) }),
  Object.freeze({ name: "fk_doc_ver_translation", childTable: "t_document_version", childFields: Object.freeze(["document_template_translation_id", "document_template_ver_id"]), parentTable: "t_document_template_translation", parentFields: Object.freeze(["translation_id", "template_ver_id"]) }),
  Object.freeze({ name: "fk_notify_provider_current", childTable: "t_notification_provider", childFields: Object.freeze(["current_ver_id", "provider_id"]), parentTable: "t_notification_provider_ver", parentFields: Object.freeze(["provider_ver_id", "provider_id"]) }),
  Object.freeze({ name: "fk_notify_attempt_provider_ver", childTable: "t_notification_attempt", childFields: Object.freeze(["provider_ver_id", "provider_id"]), parentTable: "t_notification_provider_ver", parentFields: Object.freeze(["provider_ver_id", "provider_id"]) }),
  Object.freeze({ name: "fk_notify_callback_verify_ver", childTable: "t_notification_provider_callback", childFields: Object.freeze(["verification_provider_ver_id", "provider_id"]), parentTable: "t_notification_provider_ver", parentFields: Object.freeze(["provider_ver_id", "provider_id"]) }),
  Object.freeze({ name: "fk_notify_callback_attempt", childTable: "t_notification_provider_callback", childFields: Object.freeze(["notification_attempt_id", "provider_id", "provider_message_id", "notification_delivery_id"]), parentTable: "t_notification_attempt", parentFields: Object.freeze(["attempt_id", "provider_id", "provider_message_id", "delivery_id"]) }),
  Object.freeze({ name: "fk_notify_template_current", childTable: "t_notification_template", childFields: Object.freeze(["current_ver_id", "template_id"]), parentTable: "t_notification_template_ver", parentFields: Object.freeze(["template_ver_id", "template_id"]) }),
  Object.freeze({ name: "fk_webhook_endpoint_current", childTable: "t_webhook_endpoint", childFields: Object.freeze(["current_ver_id", "endpoint_id"]), parentTable: "t_webhook_endpoint_ver", parentFields: Object.freeze(["endpoint_ver_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_webhook_delivery_event", childTable: "t_webhook_delivery", childFields: Object.freeze(["webhook_event_id", "client_id", "payload_schema_version"]), parentTable: "t_webhook_event", parentFields: Object.freeze(["event_id", "client_id", "payload_schema_version"]) }),
  Object.freeze({ name: "fk_webhook_delivery_endpoint", childTable: "t_webhook_delivery", childFields: Object.freeze(["endpoint_id", "client_id"]), parentTable: "t_webhook_endpoint", parentFields: Object.freeze(["endpoint_id", "client_id"]) }),
  Object.freeze({ name: "fk_webhook_delivery_endpoint_ver", childTable: "t_webhook_delivery", childFields: Object.freeze(["endpoint_ver_id", "endpoint_id"]), parentTable: "t_webhook_endpoint_ver", parentFields: Object.freeze(["endpoint_ver_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_webhook_delivery_subscription", childTable: "t_webhook_delivery", childFields: Object.freeze(["subscription_id", "endpoint_id", "payload_schema_version"]), parentTable: "t_webhook_subscription", parentFields: Object.freeze(["subscription_id", "endpoint_id", "payload_schema_version"]) }),
  Object.freeze({ name: "fk_webhook_attempt_delivery", childTable: "t_webhook_attempt", childFields: Object.freeze(["delivery_id", "endpoint_id"]), parentTable: "t_webhook_delivery", parentFields: Object.freeze(["delivery_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_webhook_attempt_secret", childTable: "t_webhook_attempt", childFields: Object.freeze(["secret_ver_id", "endpoint_id"]), parentTable: "t_webhook_secret_ver", parentFields: Object.freeze(["secret_ver_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_gateway_route_current", childTable: "t_gateway_route", childFields: Object.freeze(["current_ver_id", "route_id"]), parentTable: "t_gateway_route_ver", parentFields: Object.freeze(["route_ver_id", "route_id"]) }),
  Object.freeze({ name: "fk_gateway_policy_current", childTable: "t_gateway_policy", childFields: Object.freeze(["current_ver_id", "policy_id"]), parentTable: "t_gateway_policy_ver", parentFields: Object.freeze(["policy_ver_id", "policy_id"]) }),
  Object.freeze({ name: "fk_gateway_binding_current", childTable: "t_gateway_policy_binding", childFields: Object.freeze(["current_ver_id", "binding_id"]), parentTable: "t_gateway_policy_binding_ver", parentFields: Object.freeze(["binding_ver_id", "binding_id"]) }),
  Object.freeze({ name: "fk_config_item_current", childTable: "t_config_item", childFields: Object.freeze(["current_ver_id", "config_item_id"]), parentTable: "t_config_version", parentFields: Object.freeze(["config_ver_id", "config_item_id"]) }),
  Object.freeze({ name: "fk_config_change_expected", childTable: "t_config_change_item", childFields: Object.freeze(["expected_current_ver_id", "config_item_id"]), parentTable: "t_config_version", parentFields: Object.freeze(["config_ver_id", "config_item_id"]) }),
  Object.freeze({ name: "fk_config_change_target", childTable: "t_config_change_item", childFields: Object.freeze(["target_ver_id", "config_item_id"]), parentTable: "t_config_version", parentFields: Object.freeze(["config_ver_id", "config_item_id"]) }),
]);

export const FORBIDDEN_FIELD_PATTERN = /(?:^|_)(?:pan|cvv|cvc|track_data|pin_block|card_expiry|password|private_key|jdbc_url|ciphertext|encrypted_value|dek)(?:_|$)/iu;

export const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";

export const EXPECTED_CHECK_COUNT = 67;
export const EXPECTED_CHECK_POLICY_DIGEST = "ff1c33cec97cd9e351ce7be4024dfa5333e74008a48977b0b0ba8a4911cf68b4";
export const REQUIRED_CHECK_CONTRACTS = Object.freeze({
  ck_member_role_scope: Object.freeze({
    expression: "role_scope_key='PLATFORM' OR role_scope_key=CAST(tenant_id AS CHAR)",
    tables: Object.freeze(["member_role"]),
  }),
  ck_otp_email_tuple: Object.freeze({
    expression: "email_vault_token IS NOT NULL AND email_blind_index IS NOT NULL AND email_blind_key_ver IS NOT NULL AND email_masked IS NOT NULL",
    tables: Object.freeze(["email_otp_challenge"]),
  }),
  ck_file_available: Object.freeze({
    expression: "(scan_status='AVAILABLE' AND available_time IS NOT NULL) OR (scan_status IN ('PENDING_SCAN','SCANNING','REJECTED') AND available_time IS NULL) OR scan_status='QUARANTINED'",
    tables: Object.freeze(["file_object"]),
  }),
  ck_generation_commerce: Object.freeze({
    expression: "(commerce_request_event_key IS NULL AND commerce_source_version IS NULL AND commerce_source_payload_hash IS NULL AND commerce_order_context_id IS NULL AND consumer_terms_file_id IS NULL AND consumer_terms_content_hash IS NULL AND refund_policy_file_id IS NULL AND refund_policy_content_hash IS NULL AND privacy_notice_file_id IS NULL AND privacy_notice_content_hash IS NULL) OR (commerce_request_event_key IS NOT NULL AND commerce_source_version IS NOT NULL AND commerce_source_version>0 AND commerce_source_payload_hash IS NOT NULL AND commerce_order_context_id IS NOT NULL AND consumer_terms_file_id IS NOT NULL AND consumer_terms_content_hash IS NOT NULL AND refund_policy_file_id IS NOT NULL AND refund_policy_content_hash IS NOT NULL AND privacy_notice_file_id IS NOT NULL AND privacy_notice_content_hash IS NOT NULL)",
    tables: Object.freeze(["document_generation"]),
  }),
  ck_generation_fiscal: Object.freeze({
    expression: "(fiscal_event_key IS NULL AND fiscal_record_id IS NULL AND fiscal_source_version IS NULL AND fiscal_source_payload_hash IS NULL) OR (fiscal_event_key IS NOT NULL AND fiscal_record_id IS NOT NULL AND fiscal_source_version=1 AND fiscal_source_payload_hash IS NOT NULL)",
    tables: Object.freeze(["document_generation"]),
  }),
  ck_generation_status: Object.freeze({
    expression: "(status='WAITING_FISCAL' AND commerce_request_event_key IS NOT NULL AND fiscal_event_key IS NULL) OR (status='WAITING_REQUEST' AND commerce_request_event_key IS NULL AND fiscal_event_key IS NOT NULL) OR (status='MATERIALIZED' AND commerce_request_event_key IS NOT NULL AND fiscal_event_key IS NOT NULL) OR (status='CONFLICT' AND (commerce_request_event_key IS NOT NULL OR fiscal_event_key IS NOT NULL))",
    tables: Object.freeze(["document_generation"]),
  }),
  ck_document_correction: Object.freeze({
    expression: "corrects_document_id IS NULL OR (document_type='CREDIT_NOTE' AND corrects_document_id<>document_id)",
    tables: Object.freeze(["consumer_document"]),
  }),
  ck_document_supersedes: Object.freeze({
    expression: "supersedes_version_id IS NULL OR supersedes_version_id<>document_ver_id",
    tables: Object.freeze(["document_version"]),
  }),
  ck_notify_callback_verify: Object.freeze({
    expression: "verification_provider_ver_id IS NOT NULL AND verification_time IS NOT NULL AND ((signature_status='VERIFIED' AND verification_failure_code IS NULL AND notification_attempt_id IS NOT NULL AND notification_delivery_id IS NOT NULL) OR (signature_status IN ('FAILED','UNSUPPORTED') AND verification_failure_code IS NOT NULL AND notification_attempt_id IS NULL AND notification_delivery_id IS NULL))",
    tables: Object.freeze(["notification_provider_callback"]),
  }),
  ck_webhook_payload_storage: Object.freeze({
    expression: "(payload_storage_mode='INLINE' AND payload_inline IS NOT NULL AND payload_vault_token IS NULL) OR (payload_storage_mode='VAULT_TOKEN' AND payload_inline IS NULL AND payload_vault_token IS NOT NULL)",
    tables: Object.freeze(["webhook_event"]),
  }),
  ck_webhook_delivery_generation: Object.freeze({
    expression: "(generation_no=0 AND replay_request_id IS NULL) OR (generation_no>0 AND replay_request_id IS NOT NULL)",
    tables: Object.freeze(["webhook_delivery"]),
  }),
  ck_audit_attr_value: Object.freeze({
    expression: "(classification IN ('PUBLIC','INTERNAL') AND value_text IS NOT NULL AND value_vault_token IS NULL AND value_masked IS NULL) OR (classification IN ('CONFIDENTIAL','RESTRICTED') AND value_text IS NULL AND value_vault_token IS NOT NULL AND value_masked IS NOT NULL)",
    tables: Object.freeze(["audit_event_attr"]),
  }),
  ck_gateway_guard_shape: Object.freeze({
    expression: "((rule_type='RATE_LIMIT' AND refill_per_sec>0 AND burst_capacity>0 AND concurrency_limit IS NULL AND max_request_bytes IS NULL AND timeout_ms IS NULL AND (request_token_cost IS NULL OR request_token_cost>=1)) OR (rule_type='CONCURRENCY' AND refill_per_sec IS NULL AND burst_capacity IS NULL AND concurrency_limit>0 AND max_request_bytes IS NULL AND timeout_ms IS NULL AND request_token_cost IS NULL) OR (rule_type='REQUEST_SIZE' AND refill_per_sec IS NULL AND burst_capacity IS NULL AND concurrency_limit IS NULL AND max_request_bytes>0 AND timeout_ms IS NULL AND request_token_cost IS NULL) OR (rule_type='TIMEOUT' AND refill_per_sec IS NULL AND burst_capacity IS NULL AND concurrency_limit IS NULL AND max_request_bytes IS NULL AND timeout_ms>0 AND request_token_cost IS NULL) OR (rule_type IN ('IP_ALLOW','IP_DENY') AND refill_per_sec IS NULL AND burst_capacity IS NULL AND concurrency_limit IS NULL AND max_request_bytes IS NULL AND timeout_ms IS NULL AND request_token_cost IS NULL)) AND (reject_status IS NULL OR reject_status BETWEEN 400 AND 599)",
    tables: Object.freeze(["gateway_guard_rule"]),
  }),
  ck_gateway_release_slots: Object.freeze({
    expression: "(status='ACTIVATING' AND activation_slot=1 AND active_slot IS NULL) OR (status='ACTIVE' AND activation_slot IS NULL AND active_slot=1) OR (status NOT IN ('ACTIVATING','ACTIVE') AND activation_slot IS NULL AND active_slot IS NULL)",
    tables: Object.freeze(["gateway_release"]),
  }),
  ck_gateway_release_freeze: Object.freeze({
    expression: "(status IN ('DRAFT','VALIDATING','CANCELLED') AND items_frozen_time IS NULL) OR (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','ACTIVATING','ACTIVE','SUPERSEDED','ACTIVATION_FAILED') AND items_frozen_time IS NOT NULL)",
    tables: Object.freeze(["gateway_release"]),
  }),
  ck_gateway_rollback: Object.freeze({
    expression: "(release_type='NORMAL' AND rollback_of_release_id IS NULL AND restore_from_release_id IS NULL) OR (release_type='ROLLBACK' AND rollback_of_release_id IS NOT NULL AND restore_from_release_id IS NOT NULL AND rollback_of_release_id<>release_id AND restore_from_release_id<>release_id)",
    tables: Object.freeze(["gateway_release"]),
  }),
  ck_gateway_item_shape: Object.freeze({
    expression: "(route_ver_id IS NOT NULL)+(policy_ver_id IS NOT NULL)+(binding_ver_id IS NOT NULL)=1",
    tables: Object.freeze(["gateway_release_item"]),
  }),
  ck_config_storage: Object.freeze({
    expression: "(storage_mode='INLINE' AND inline_value IS NOT NULL AND value_vault_token IS NULL AND secret_ref IS NULL) OR (storage_mode='VAULT_TOKEN' AND inline_value IS NULL AND value_vault_token IS NOT NULL AND secret_ref IS NULL) OR (storage_mode='SECRET_REF' AND inline_value IS NULL AND value_vault_token IS NULL AND secret_ref IS NOT NULL)",
    tables: Object.freeze(["config_version"]),
  }),
  ck_config_change_freeze: Object.freeze({
    expression: "(status IN ('DRAFT','CANCELLED') AND items_frozen_time IS NULL) OR (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','RELEASED') AND items_frozen_time IS NOT NULL)",
    tables: Object.freeze(["config_change_set"]),
  }),
  ck_compensation_lease: Object.freeze({
    expression: "(execute_status='PROCESSING' AND lock_owner IS NOT NULL AND lock_until IS NOT NULL) OR (execute_status IN ('INIT','ERROR','FAILED') AND lock_owner IS NULL AND lock_until IS NULL)",
    tables: Object.freeze(["compensation_event"]),
  }),
  ck_command_response: Object.freeze({
    expression: "(status='SUCCEEDED' AND response_ref IS NOT NULL AND response_hash IS NOT NULL) OR status<>'SUCCEEDED'",
    tables: Object.freeze(["command_idempotency"]),
  }),
});

export const EXPECTED_TOTALS = Object.freeze({
  tables: 92,
  fields: 1076,
  uniqueIndexes: 167,
  accessIndexes: 116,
  relations: 145,
  oneToOneRelations: 19,
  diagramGroups: 8,
});
export const EXPECTED_MODEL_DIGEST = "4c1eb0bbe9528a3b94ae36c8219c466338ced48c523614a4d5788ad38d3dda3d";
export const EXPECTED_STRUCTURAL_DIGEST = "ac4ac75af6389494c37e9cb719daa913f7d09c40dce139af1f1b84b5aa540c68";
