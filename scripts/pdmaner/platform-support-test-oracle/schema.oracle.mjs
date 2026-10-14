export const APPROVED_TABLE_GROUPS = Object.freeze({
  identity_access: Object.freeze([
    "t_user_account", "t_password_credential", "t_federated_identity", "t_access_tenant",
    "t_tenant_member", "t_tenant_invitation", "t_authentication_challenge", "t_auth_session",
    "t_refresh_token_generation", "t_jwt_signing_key", "t_role", "t_permission",
    "t_tenant_member_role", "t_role_permission", "t_api_credential", "t_api_scope", "t_api_credential_scope",
    "t_email_otp_challenge", "t_email_otp_attempt",
  ]),
  file_document: Object.freeze([
    "t_file_upload", "t_file_scan_policy", "t_file_scan_requirement", "t_file_object",
    "t_file_owner_ref", "t_file_scan_result", "t_document_template",
    "t_document_template_translation", "t_consumer_document", "t_document_version",
    "t_document_delivery", "t_document_access_grant",
  ]),
  notification: Object.freeze([
    "t_notification_request", "t_notification_delivery", "t_notification_attempt",
    "t_notification_template", "t_notification_template_content", "t_notification_provider", "t_notification_route",
  ]),
  webhook: Object.freeze([
    "t_webhook_endpoint", "t_webhook_endpoint_revision", "t_webhook_endpoint_verification",
    "t_webhook_signing_secret", "t_webhook_subscription", "t_webhook_event",
    "t_webhook_delivery", "t_webhook_attempt",
  ]),
  audit_security: Object.freeze([
    "t_audit_chain", "t_audit_event", "t_audit_event_attr", "t_audit_integrity_point",
    "t_security_event", "t_security_alert", "t_security_alert_event", "t_security_alert_action",
  ]),
  gateway_control: Object.freeze([
    "t_gateway_target", "t_gateway_route", "t_gateway_route_header",
    "t_gateway_route_query_param", "t_gateway_guard_rule", "t_console_resource",
  ]),
  reliability: Object.freeze(["t_compensation_event", "t_command_idempotency"]),
  integration: Object.freeze([
    "t_standard_api", "t_api_field", "t_integration", "t_integration_api",
    "t_integration_script", "t_integration_standard_api",
    "t_integration_verify_account", "t_integration_verify_case",
    "t_integration_verify_run", "t_integration_verify_run_step",
  ]),
});

export const EXPECTED_GROUP_SIZES = Object.freeze({
  identity_access: 19,
  file_document: 12,
  notification: 7,
  webhook: 8,
  audit_security: 8,
  gateway_control: 6,
  reliability: 2,
  integration: 10,
});

export const EXPECTED_TABLE_TOTAL = 72;
export const APPROVED_TABLES = Object.freeze(Object.values(APPROVED_TABLE_GROUPS).flat());

export const APPROVED_DIAGRAMS = Object.freeze({
  identity_access: "身份、授权与接入",
  file_document: "文件与消费者文档",
  notification: "通知与 Email",
  webhook: "Client Webhook",
  audit_security: "审计与安全",
  gateway_control: "公共 API 控制面",
  reliability: "可靠执行",
  integration: "外部 API 配置",
});

export const GATEWAY_CONTROL_CONTRACTS = Object.freeze({
  gateway_target: Object.freeze({
    fields: Object.freeze([
      "target_id", "target_service_code", "discovery_service_name", "channel", "status", "version",
      "created_by", "updated_by", "create_time", "update_time",
    ]),
    nullableFields: Object.freeze([]),
    fieldTypes: Object.freeze({
      target_id: "BIGINT", target_service_code: "VARCHAR(128)", discovery_service_name: "VARCHAR(255)",
      channel: "VARCHAR(64)", status: "VARCHAR(64)", version: "INT", created_by: "BIGINT",
      updated_by: "BIGINT", create_time: "DATETIME(6)", update_time: "DATETIME(6)",
    }),
    uniqueIndexes: Object.freeze({
      uk_gateway_target_code: Object.freeze(["target_service_code"]),
      uk_gateway_target_channel: Object.freeze(["target_id", "channel"]),
    }),
    accessIndexes: Object.freeze({
      idx_gateway_target_status: Object.freeze(["channel", "status"]),
    }),
    relations: Object.freeze([]),
    enums: Object.freeze({
      channel: Object.freeze(["OPERATIONS", "CLIENT", "PARTNER", "CHECKOUT", "CLIENT_API", "CHANNEL_CALLBACK"]),
      status: Object.freeze(["ACTIVE", "DISABLED"]),
    }),
  }),
  gateway_route: Object.freeze({
    fields: Object.freeze([
      "route_id", "route_code", "channel", "http_method", "external_path_template",
      "downstream_path_template", "target_id", "security_profile_code", "authorization_mode",
      "permission_id", "required_scope_code", "request_cookie_mode", "response_cookie_mode",
      "body_limit_bytes", "body_receive_timeout_millis", "response_timeout_millis", "status", "version",
      "created_by", "updated_by", "create_time", "update_time",
    ]),
    nullableFields: Object.freeze(["permission_id", "required_scope_code"]),
    fieldTypes: Object.freeze({
      route_id: "BIGINT", route_code: "VARCHAR(128)", channel: "VARCHAR(64)", http_method: "VARCHAR(16)",
      external_path_template: "VARCHAR(1000)", downstream_path_template: "VARCHAR(1000)", target_id: "BIGINT",
      security_profile_code: "VARCHAR(64)", authorization_mode: "VARCHAR(64)", permission_id: "BIGINT",
      required_scope_code: "VARCHAR(128)", request_cookie_mode: "VARCHAR(64)", response_cookie_mode: "VARCHAR(64)",
      body_limit_bytes: "BIGINT", body_receive_timeout_millis: "INT", response_timeout_millis: "INT",
      status: "VARCHAR(64)", version: "INT", created_by: "BIGINT", updated_by: "BIGINT",
      create_time: "DATETIME(6)", update_time: "DATETIME(6)",
    }),
    uniqueIndexes: Object.freeze({
      uk_gateway_route_code: Object.freeze(["route_code"]),
      uk_gateway_route_path: Object.freeze(["http_method", "external_path_template"]),
    }),
    accessIndexes: Object.freeze({
      idx_gateway_route_target: Object.freeze(["target_id", "channel", "status"]),
      idx_gateway_route_channel: Object.freeze(["channel", "status", "target_id"]),
      idx_gateway_route_permission: Object.freeze(["permission_id"]),
      idx_gateway_route_scope: Object.freeze(["required_scope_code", "route_id"]),
    }),
    relations: Object.freeze([
      Object.freeze({ childField: "target_id", parentTable: "gateway_target", parentField: "target_id", cardinality: "n:1" }),
      Object.freeze({ childField: "permission_id", parentTable: "permission", parentField: "permission_id", cardinality: "n:1" }),
      Object.freeze({ childField: "required_scope_code", parentTable: "api_scope", parentField: "scope_code", cardinality: "n:1" }),
    ]),
    enums: Object.freeze({
      channel: Object.freeze(["OPERATIONS", "CLIENT", "PARTNER", "CHECKOUT", "CLIENT_API", "CHANNEL_CALLBACK"]),
      security_profile_code: Object.freeze(["OPERATIONS_JWT", "CLIENT_JWT", "PARTNER_JWT", "CLIENT_API_CREDENTIAL", "CHECKOUT_SESSION", "CHANNEL_CALLBACK", "PUBLIC"]),
      authorization_mode: Object.freeze(["PUBLIC", "HUMAN_AUTHENTICATED", "HUMAN_PERMISSION", "API_SCOPE", "CHECKOUT_RESOURCE", "CHANNEL_CALLBACK"]),
      request_cookie_mode: Object.freeze(["DROP", "REQUIRE_PROFILE_COOKIE"]),
      response_cookie_mode: Object.freeze(["NONE", "ISSUE_ONLY", "ISSUE_OR_CLEAR", "CLEAR_ONLY"]),
      status: Object.freeze(["ACTIVE", "DISABLED"]),
    }),
    fieldStorage: Object.freeze({
      http_method: Object.freeze({ characterSet: "ascii", collation: "ascii_bin" }),
      external_path_template: Object.freeze({ characterSet: "ascii", collation: "ascii_bin" }),
      downstream_path_template: Object.freeze({ characterSet: "ascii", collation: "ascii_bin" }),
    }),
  }),
  gateway_route_header: Object.freeze({
    fields: Object.freeze(["route_header_id", "route_id", "header_name", "header_use"]),
    nullableFields: Object.freeze([]),
    fieldTypes: Object.freeze({
      route_header_id: "BIGINT", route_id: "BIGINT", header_name: "VARCHAR(255)",
      header_use: "VARCHAR(64)",
    }),
    uniqueIndexes: Object.freeze({
      uk_gateway_route_header: Object.freeze(["route_id", "header_name", "header_use"]),
    }),
    accessIndexes: Object.freeze({}),
    relations: Object.freeze([
      Object.freeze({ childField: "route_id", parentTable: "gateway_route", parentField: "route_id", cardinality: "n:1" }),
    ]),
    enums: Object.freeze({
      header_use: Object.freeze(["CORS_REQUEST_ALLOWED", "FORWARD_REQUEST"]),
    }),
    fieldStorage: Object.freeze({
      header_name: Object.freeze({ characterSet: "ascii", collation: "ascii_general_ci" }),
    }),
  }),
  gateway_route_query_param: Object.freeze({
    fields: Object.freeze(["route_query_param_id", "route_id", "parameter_name"]),
    nullableFields: Object.freeze([]),
    fieldTypes: Object.freeze({
      route_query_param_id: "BIGINT", route_id: "BIGINT", parameter_name: "VARCHAR(255)",
    }),
    uniqueIndexes: Object.freeze({
      uk_gateway_route_query: Object.freeze(["route_id", "parameter_name"]),
    }),
    accessIndexes: Object.freeze({}),
    relations: Object.freeze([
      Object.freeze({ childField: "route_id", parentTable: "gateway_route", parentField: "route_id", cardinality: "n:1" }),
    ]),
    enums: Object.freeze({}),
  }),
  gateway_guard_rule: Object.freeze({
    fields: Object.freeze([
      "guard_rule_id", "route_id", "rule_type", "dimension", "capacity", "refill_tokens",
      "refill_period_millis", "concurrency_limit",
    ]),
    nullableFields: Object.freeze(["capacity", "refill_tokens", "refill_period_millis", "concurrency_limit"]),
    fieldTypes: Object.freeze({
      guard_rule_id: "BIGINT", route_id: "BIGINT", rule_type: "VARCHAR(64)", dimension: "VARCHAR(64)",
      capacity: "BIGINT", refill_tokens: "BIGINT", refill_period_millis: "BIGINT",
      concurrency_limit: "BIGINT",
    }),
    uniqueIndexes: Object.freeze({
      uk_gateway_guard_rule: Object.freeze(["route_id", "rule_type", "dimension"]),
    }),
    accessIndexes: Object.freeze({}),
    relations: Object.freeze([
      Object.freeze({ childField: "route_id", parentTable: "gateway_route", parentField: "route_id", cardinality: "n:1" }),
    ]),
    enums: Object.freeze({
      rule_type: Object.freeze(["RATE_LIMIT", "CONCURRENCY"]),
      dimension: Object.freeze(["SHARED_ROUTE", "CLIENT_IP", "AUTHENTICATED_PRINCIPAL", "TENANT"]),
    }),
  }),
  console_resource: Object.freeze({
    fields: Object.freeze([
      "resource_id", "resource_code", "channel", "resource_type", "parent_resource_id", "display_name",
      "frontend_route", "component_key", "permission_id", "sort_order", "status", "version",
      "create_time", "update_time",
    ]),
    nullableFields: Object.freeze(["parent_resource_id", "frontend_route", "component_key"]),
    fieldTypes: Object.freeze({
      resource_id: "BIGINT", resource_code: "VARCHAR(128)", channel: "VARCHAR(64)",
      resource_type: "VARCHAR(64)", parent_resource_id: "BIGINT", display_name: "VARCHAR(255)",
      frontend_route: "VARCHAR(1000)", component_key: "VARCHAR(255)", permission_id: "BIGINT",
      sort_order: "INT", status: "VARCHAR(64)", version: "INT",
      create_time: "DATETIME(6)", update_time: "DATETIME(6)",
    }),
    uniqueIndexes: Object.freeze({
      uk_console_resource_code: Object.freeze(["resource_code"]),
      uk_console_resource_channel: Object.freeze(["resource_id", "channel"]),
    }),
    accessIndexes: Object.freeze({
      idx_console_resource_parent: Object.freeze(["parent_resource_id", "channel", "sort_order"]),
      idx_console_resource_permission: Object.freeze(["permission_id"]),
      idx_console_resource_channel: Object.freeze(["channel", "status", "parent_resource_id", "sort_order"]),
    }),
    relations: Object.freeze([
      Object.freeze({ childField: "parent_resource_id", parentTable: "console_resource", parentField: "resource_id", cardinality: "n:1" }),
      Object.freeze({ childField: "permission_id", parentTable: "permission", parentField: "permission_id", cardinality: "n:1" }),
    ]),
    enums: Object.freeze({
      channel: Object.freeze(["OPERATIONS", "CLIENT", "PARTNER"]),
      resource_type: Object.freeze(["MENU", "BUTTON"]),
      status: Object.freeze(["ACTIVE", "DISABLED"]),
    }),
  }),
});

export const NO_AUDIT_TABLES = Object.freeze([
  "t_gateway_route_header", "t_gateway_route_query_param", "t_gateway_guard_rule",
]);

export const REQUIRED_GATEWAY_ROUTE_COMPATIBILITY = Object.freeze({
  OPERATIONS: Object.freeze([
    "PUBLIC:PUBLIC",
    "OPERATIONS_JWT:HUMAN_AUTHENTICATED",
    "OPERATIONS_JWT:HUMAN_PERMISSION",
  ]),
  CLIENT: Object.freeze([
    "PUBLIC:PUBLIC",
    "CLIENT_JWT:HUMAN_AUTHENTICATED",
    "CLIENT_JWT:HUMAN_PERMISSION",
  ]),
  PARTNER: Object.freeze([
    "PUBLIC:PUBLIC",
    "PARTNER_JWT:HUMAN_AUTHENTICATED",
    "PARTNER_JWT:HUMAN_PERMISSION",
  ]),
  CLIENT_API: Object.freeze(["CLIENT_API_CREDENTIAL:API_SCOPE"]),
  CHECKOUT: Object.freeze(["CHECKOUT_SESSION:CHECKOUT_RESOURCE"]),
  CHANNEL_CALLBACK: Object.freeze(["CHANNEL_CALLBACK:CHANNEL_CALLBACK"]),
});

export const REMOVED_PLATFORM_TABLES = Object.freeze([
  "t_oidc_provider", "t_credential_event", "t_client_integration_readiness",
  "t_integration_readiness_eval", "t_integration_readiness_check",
  "t_file_scan_policy_ver", "t_file_state_change", "t_document_template_ver",
  "t_document_generation", "t_doc_generation_state_change", "t_document_render_job",
  "t_document_state_change", "t_notification_provider_ver", "t_notification_recipient",
  "t_notification_message", "t_notification_template_ver", "t_notify_delivery_state_change",
  "t_webhook_endpoint_ver", "t_webhook_secret_ver", "t_webhook_sub_state_change",
  "t_webhook_replay", "t_webhook_delivery_state_change", "t_audit_archive_batch",
  "t_gateway_policy", "t_gateway_policy_ver",
  "t_gateway_policy_binding", "t_gateway_policy_binding_ver", "t_gateway_selector",
  "t_gateway_release", "t_gateway_release_item", "t_gateway_release_approval",
  "t_gateway_release_state_change", "t_gateway_instance_ack", "t_config_namespace",
  "t_config_item", "t_config_version", "t_config_change_set", "t_config_change_item",
  "t_config_approval", "t_config_release", "t_config_release_state_change",
]);

export const FORBIDDEN_TABLES = Object.freeze([
  ...REMOVED_PLATFORM_TABLES,
  "t_member_role",
  "t_idp_connection", "t_user_identity", "t_gateway_route_ver", "t_gateway_instance",
  "t_resource_grant", "t_file_access_grant", "t_file_access_event", "t_document_source_ref",
  "t_notification_preference", "t_audit_access_event", "t_dictionary_set",
  "t_dictionary_version", "t_dictionary_entry", "t_dictionary_translation",
  "t_dictionary_release", "t_job_definition", "t_job_schedule", "t_job_run",
  "t_job_attempt", "t_job_lease",
]);

export const EXPECTED_HASH_FIELDS = Object.freeze([
  "access_tenant.source_payload_hash",
  "audit_chain.last_event_hash",
  "audit_chain.previous_partition_hash",
  "audit_event.attribute_root_hash",
  "audit_event.event_hash",
  "audit_event.payload_hash",
  "audit_event.previous_event_hash",
  "audit_event.source_payload_hash",
  "audit_event_attr.value_hash",
  "audit_integrity_point.content_hash",
  "audit_integrity_point.end_event_hash",
  "audit_integrity_point.root_hash",
  "audit_integrity_point.start_event_hash",
  "authentication_challenge.active_scope_hash",
  "command_idempotency.request_hash",
  "consumer_document.commerce_source_payload_hash",
  "consumer_document.consumer_ref_hash",
  "consumer_document.fiscal_source_payload_hash",
  "document_access_grant.consumer_ref_hash",
  "email_otp_attempt.device_hash",
  "email_otp_attempt.ip_hash",
  "email_otp_challenge.context_ref_hash",
  "email_otp_challenge.subject_ref_hash",
  "file_object.content_hash",
  "security_event.event_hash",
  "security_event.source_payload_hash",
  "webhook_attempt.request_hash",
  "webhook_endpoint_verification.challenge_hash",
  "webhook_endpoint_verification.resolved_ip_hash",
  "webhook_event.payload_hash",
  "webhook_event.source_payload_hash",
]);

export const REQUIRED_FIELDS = Object.freeze({
  t_user_account: Object.freeze(["account_id", "realm", "account_type", "login_name", "email_vault_token", "email_masked", "status", "credential_version", "auth_epoch", "version"]),
  t_password_credential: Object.freeze(["account_id", "password_verifier_phc", "algorithm_code", "memory_kib", "iterations", "parallelism", "credential_version", "status", "version"]),
  t_federated_identity: Object.freeze(["account_id", "provider_code", "provider_subject", "binding_method", "status", "version"]),
  t_access_tenant: Object.freeze(["tenant_type", "owner_ref_id", "owner_status", "source_version", "source_payload_hash", "security_status", "version"]),
  t_tenant_member: Object.freeze(["tenant_id", "account_id", "member_type", "status", "version"]),
  t_tenant_invitation: Object.freeze(["tenant_id", "accepted_account_id", "accepted_member_id", "status", "version"]),
  t_authentication_challenge: Object.freeze(["realm", "purpose", "active_scope_hash", "secret_hmac", "hmac_key_version", "status", "active_slot", "version"]),
  t_auth_session: Object.freeze(["account_id", "realm", "channel", "tenant_id", "tenant_member_id", "authentication_method", "status", "version"]),
  t_refresh_token_generation: Object.freeze(["session_id", "generation_no", "token_lookup_id", "token_secret_hmac", "hmac_key_version", "status", "active_slot", "version"]),
  t_jwt_signing_key: Object.freeze(["realm", "kid", "algorithm", "kms_key_ref", "public_jwk_canonical_json", "status", "active_slot", "version"]),
  t_tenant_member_role: Object.freeze(["tenant_member_role_id", "tenant_member_id", "tenant_id", "role_id", "role_scope_key", "status", "version"]),
  t_api_credential: Object.freeze(["tenant_id", "key_prefix", "key_verifier_phc", "expires_time", "status", "rotated_from_id", "version"]),
  t_email_otp_challenge: Object.freeze(["tenant_scope_key", "subject_ref_hash", "context_ref_hash", "email_vault_token", "email_blind_index", "code_hmac", "active_slot", "version"]),
  t_file_scan_policy: Object.freeze(["policy_code", "revision_no", "allowed_mime_rules", "max_size_bytes", "status", "active_slot", "version"]),
  t_file_object: Object.freeze(["content_hash", "scan_policy_id", "scan_status", "object_status", "version"]),
  t_file_scan_result: Object.freeze(["file_id", "scan_requirement_id", "scan_policy_id", "attempt_no"]),
  t_consumer_document: Object.freeze(["tenant_id", "generation_key_version", "document_generation_key", "commerce_source_payload_hash", "fiscal_source_payload_hash", "consumer_ref_hash", "status", "version"]),
  t_document_version: Object.freeze(["document_id", "document_version_no", "document_template_id", "document_template_translation_id", "renderer_version", "file_id", "active_slot", "attempt_count", "lease_owner", "lease_until", "fencing_token", "version"]),
  t_document_delivery: Object.freeze(["document_ver_id", "document_id", "tenant_id", "notification_delivery_id"]),
  t_document_access_grant: Object.freeze(["document_ver_id", "document_id", "tenant_id", "consumer_ref_hash", "order_id", "status", "version"]),
  t_notification_provider: Object.freeze(["provider_code", "medium", "status", "version"]),
  t_notification_request: Object.freeze(["tenant_id", "source_system", "source_event_key", "notification_template_id", "notification_route_id", "default_locale", "default_parameters"]),
  t_notification_delivery: Object.freeze(["request_id", "address_vault_token", "locale", "parameters", "status", "attempt_count", "next_retry_time", "version"]),
  t_notification_attempt: Object.freeze(["delivery_id", "provider_id", "provider_request_no", "provider_message_id", "status", "response_code", "resolution_source", "resolution_code", "started_time", "finished_time", "resolved_time", "update_time"]),
  t_webhook_endpoint: Object.freeze(["tenant_id", "endpoint_code", "status", "version"]),
  t_webhook_endpoint_revision: Object.freeze(["endpoint_id", "revision_no", "url_vault_token", "url_masked", "host_name", "status", "active_slot", "version"]),
  t_webhook_signing_secret: Object.freeze(["endpoint_id", "rotation_no", "secret_ref", "secret_fingerprint", "status", "signing_slot", "version"]),
  t_webhook_event: Object.freeze(["tenant_id", "source_payload_hash", "payload_storage_mode", "payload_inline", "payload_vault_token", "payload_schema_version", "payload_hash"]),
  t_webhook_delivery: Object.freeze(["webhook_event_id", "tenant_id", "endpoint_id", "endpoint_revision_id", "subscription_id", "event_type", "payload_schema_version", "generation_no", "replay_of_delivery_id", "requested_by", "replay_reason_code", "lease_owner", "lease_until", "fencing_token", "version"]),
  t_webhook_attempt: Object.freeze(["delivery_id", "tenant_id", "endpoint_id", "signing_secret_id", "request_hash", "result"]),
  t_audit_event_attr: Object.freeze(["value_text", "value_vault_token", "value_masked", "value_hash", "classification"]),
  t_compensation_event: Object.freeze(["lock_owner", "lock_until", "lock_version", "execute_status", "sub_event_type"]),
  t_command_idempotency: Object.freeze(["caller_scope", "operation_type", "idempotency_key", "request_hash", "response_ref", "status"]),
});

export const FORBIDDEN_FIELDS_BY_TABLE = Object.freeze({
  t_user_account: Object.freeze(["email_blind_index", "email_vault_record_version", "email_vault_metadata_hash"]),
  t_federated_identity: Object.freeze(["oidc_provider_id"]),
  t_access_tenant: Object.freeze(["owner_ref_type", "client_id"]),
  t_jwt_signing_key: Object.freeze(["public_jwk_hash"]),
  t_api_credential: Object.freeze(["client_id", "key_hash", "scope_hash"]),
  t_email_otp_challenge: Object.freeze(["active_scope_hash", "vault_record_version", "vault_metadata_hash"]),
  t_file_object: Object.freeze(["scan_policy_ver_id", "scan_policy_hash"]),
  t_consumer_document: Object.freeze(["generation_id", "client_id", "current_ver_id", "generation_scope_hash"]),
  t_document_version: Object.freeze(["source_set_hash", "content_hash"]),
  t_notification_request: Object.freeze(["client_id", "scope_key", "recipient_set_hash", "notification_type", "medium", "send_mode", "source_payload_hash"]),
  t_notification_delivery: Object.freeze(["recipient_id", "route_hash", "recipient_hash", "content_hash", "tenant_id", "lease_owner", "lease_until", "fencing_token", "address_blind_index", "address_masked", "message_vault_token"]),
  t_notification_attempt: Object.freeze(["provider_ver_id", "response_hash", "provider_request_key", "provider_code", "attempt_no", "request_hash", "result"]),
  t_webhook_endpoint: Object.freeze(["client_id", "current_ver_id"]),
  t_webhook_endpoint_revision: Object.freeze(["content_hash", "url_blind_index", "vault_record_version", "vault_metadata_hash"]),
  t_webhook_event: Object.freeze(["client_id", "vault_record_version", "vault_metadata_hash"]),
  t_webhook_delivery: Object.freeze(["client_id", "replay_request_id"]),
  t_webhook_attempt: Object.freeze(["secret_ver_id", "response_hash"]),
  t_audit_event_attr: Object.freeze(["value_blind_index", "value_blind_index_key_version", "vault_record_version", "vault_metadata_hash"]),
  t_compensation_event: Object.freeze(["version"]),
  t_command_idempotency: Object.freeze(["version", "response_hash"]),
});

export const FILE_REFERENCE_TUPLES = Object.freeze([
  Object.freeze({ table: "t_audit_integrity_point", id: "file_id", hash: "content_hash" }),
]);

export const REQUIRED_COMPOSITE_FKS = Object.freeze([
  Object.freeze({ name: "fk_tenant_member_inviter", childTable: "t_tenant_member", childFields: Object.freeze(["invited_by", "tenant_id"]), parentTable: "t_tenant_member", parentFields: Object.freeze(["member_id", "tenant_id"]) }),
  Object.freeze({ name: "fk_auth_session_account_realm", childTable: "t_auth_session", childFields: Object.freeze(["account_id", "realm"]), parentTable: "t_user_account", parentFields: Object.freeze(["account_id", "realm"]) }),
  Object.freeze({ name: "fk_auth_session_member_identity", childTable: "t_auth_session", childFields: Object.freeze(["tenant_member_id", "tenant_id", "account_id"]), parentTable: "t_tenant_member", parentFields: Object.freeze(["member_id", "tenant_id", "account_id"]) }),
  Object.freeze({ name: "fk_invite_inviter", childTable: "t_tenant_invitation", childFields: Object.freeze(["invited_by_member_id", "tenant_id"]), parentTable: "t_tenant_member", parentFields: Object.freeze(["member_id", "tenant_id"]) }),
  Object.freeze({ name: "fk_invite_accepted_identity", childTable: "t_tenant_invitation", childFields: Object.freeze(["accepted_member_id", "tenant_id", "accepted_account_id"]), parentTable: "t_tenant_member", parentFields: Object.freeze(["member_id", "tenant_id", "account_id"]) }),
  Object.freeze({ name: "fk_tenant_member_role_member", childTable: "t_tenant_member_role", childFields: Object.freeze(["tenant_member_id", "tenant_id"]), parentTable: "t_tenant_member", parentFields: Object.freeze(["member_id", "tenant_id"]) }),
  Object.freeze({ name: "fk_tenant_member_role_role_scope", childTable: "t_tenant_member_role", childFields: Object.freeze(["role_id", "role_scope_key"]), parentTable: "t_role", parentFields: Object.freeze(["role_id", "scope_key"]) }),
  Object.freeze({ name: "fk_gateway_route_target_channel", childTable: "t_gateway_route", childFields: Object.freeze(["target_id", "channel"]), parentTable: "t_gateway_target", parentFields: Object.freeze(["target_id", "channel"]) }),
  Object.freeze({ name: "fk_console_resource_parent", childTable: "t_console_resource", childFields: Object.freeze(["parent_resource_id", "channel"]), parentTable: "t_console_resource", parentFields: Object.freeze(["resource_id", "channel"]) }),
  Object.freeze({ name: "fk_scan_result_file_policy", childTable: "t_file_scan_result", childFields: Object.freeze(["file_id", "scan_policy_id"]), parentTable: "t_file_object", parentFields: Object.freeze(["file_id", "scan_policy_id"]) }),
  Object.freeze({ name: "fk_scan_result_requirement", childTable: "t_file_scan_result", childFields: Object.freeze(["scan_requirement_id", "scan_policy_id"]), parentTable: "t_file_scan_requirement", parentFields: Object.freeze(["requirement_id", "policy_id"]) }),
  Object.freeze({ name: "fk_consumer_doc_correction", childTable: "t_consumer_document", childFields: Object.freeze(["corrects_document_id", "tenant_id", "order_id", "consumer_ref_hash"]), parentTable: "t_consumer_document", parentFields: Object.freeze(["document_id", "tenant_id", "order_id", "consumer_ref_hash"]) }),
  Object.freeze({ name: "fk_doc_ver_supersedes", childTable: "t_document_version", childFields: Object.freeze(["supersedes_version_id", "document_id"]), parentTable: "t_document_version", parentFields: Object.freeze(["document_ver_id", "document_id"]) }),
  Object.freeze({ name: "fk_doc_ver_translation", childTable: "t_document_version", childFields: Object.freeze(["document_template_translation_id", "document_template_id"]), parentTable: "t_document_template_translation", parentFields: Object.freeze(["translation_id", "document_template_id"]) }),
  Object.freeze({ name: "fk_doc_delivery_version", childTable: "t_document_delivery", childFields: Object.freeze(["document_ver_id", "document_id"]), parentTable: "t_document_version", parentFields: Object.freeze(["document_ver_id", "document_id"]) }),
  Object.freeze({ name: "fk_doc_delivery_document", childTable: "t_document_delivery", childFields: Object.freeze(["document_id", "tenant_id"]), parentTable: "t_consumer_document", parentFields: Object.freeze(["document_id", "tenant_id"]) }),
  Object.freeze({ name: "fk_document_grant_version", childTable: "t_document_access_grant", childFields: Object.freeze(["document_ver_id", "document_id"]), parentTable: "t_document_version", parentFields: Object.freeze(["document_ver_id", "document_id"]) }),
  Object.freeze({ name: "fk_document_grant_subject", childTable: "t_document_access_grant", childFields: Object.freeze(["document_id", "tenant_id", "order_id", "consumer_ref_hash"]), parentTable: "t_consumer_document", parentFields: Object.freeze(["document_id", "tenant_id", "order_id", "consumer_ref_hash"]) }),
  Object.freeze({ name: "fk_audit_integrity_file", childTable: "t_audit_integrity_point", childFields: Object.freeze(["file_id", "content_hash"]), parentTable: "t_file_object", parentFields: Object.freeze(["file_id", "content_hash"]) }),
  Object.freeze({ name: "fk_webhook_delivery_event", childTable: "t_webhook_delivery", childFields: Object.freeze(["webhook_event_id", "tenant_id", "event_type", "payload_schema_version"]), parentTable: "t_webhook_event", parentFields: Object.freeze(["event_id", "tenant_id", "event_type", "payload_schema_version"]) }),
  Object.freeze({ name: "fk_webhook_delivery_endpoint", childTable: "t_webhook_delivery", childFields: Object.freeze(["endpoint_id", "tenant_id"]), parentTable: "t_webhook_endpoint", parentFields: Object.freeze(["endpoint_id", "tenant_id"]) }),
  Object.freeze({ name: "fk_webhook_delivery_revision", childTable: "t_webhook_delivery", childFields: Object.freeze(["endpoint_revision_id", "endpoint_id"]), parentTable: "t_webhook_endpoint_revision", parentFields: Object.freeze(["endpoint_revision_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_webhook_delivery_sub", childTable: "t_webhook_delivery", childFields: Object.freeze(["subscription_id", "endpoint_id", "event_type", "payload_schema_version"]), parentTable: "t_webhook_subscription", parentFields: Object.freeze(["subscription_id", "endpoint_id", "event_type", "payload_schema_version"]) }),
  Object.freeze({ name: "fk_webhook_delivery_replay", childTable: "t_webhook_delivery", childFields: Object.freeze(["replay_of_delivery_id", "tenant_id", "endpoint_id"]), parentTable: "t_webhook_delivery", parentFields: Object.freeze(["delivery_id", "tenant_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_webhook_attempt_delivery", childTable: "t_webhook_attempt", childFields: Object.freeze(["delivery_id", "tenant_id", "endpoint_id"]), parentTable: "t_webhook_delivery", parentFields: Object.freeze(["delivery_id", "tenant_id", "endpoint_id"]) }),
  Object.freeze({ name: "fk_webhook_attempt_secret", childTable: "t_webhook_attempt", childFields: Object.freeze(["signing_secret_id", "endpoint_id"]), parentTable: "t_webhook_signing_secret", parentFields: Object.freeze(["signing_secret_id", "endpoint_id"]) }),
]);

export const APPROVED_SECRET_FIELD_NAMES = Object.freeze([
  "password_credential_id", "password_verifier_phc", "password_changed_time",
  "last_password_failure_time", "consecutive_password_failures", "password_status",
  "password_algorithm_code", "public_jwk_canonical_json",
]);

export const FORBIDDEN_FIELD_PATTERN = /(?:^|_)password(?:_|$)|(?:^|_)(?:reset|verification|session|oidc|access|refresh|id)_(?:token|secret)(?:_|$)|(?:^|_)(?:authorization_code|client_secret|private|symmetric_jwk)(?:_|$)|(?:^|_)(?:pan|cvv|cvc|track_data|pin_block|card_expiry|card_number|jdbc_url|ciphertext|encrypted_value|dek)(?:_|$)/iu;

export function isForbiddenSecretField(fieldName) {
  return !APPROVED_SECRET_FIELD_NAMES.includes(fieldName) && FORBIDDEN_FIELD_PATTERN.test(fieldName);
}

export const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
export const EXPECTED_CHECK_COUNT = 83;
export const EXPECTED_CHECK_POLICY_DIGEST = "026f425c4a3ce14250746244d0b120d6e9e91e13eaa276aabfd710c6381036f1";

export const REQUIRED_CHECK_CONTRACTS = Object.freeze({
  ck_federated_provider: Object.freeze({
    expression: "provider_code='GOOGLE'",
    tables: Object.freeze(["federated_identity"]),
  }),
  ck_tenant_source_tuple: Object.freeze({
    expression: "(tenant_type='INTERNAL' AND source_version IS NULL AND source_payload_hash IS NULL) OR (tenant_type IN ('CLIENT','PARTNER') AND source_version>0 AND source_payload_hash IS NOT NULL)",
    tables: Object.freeze(["access_tenant"]),
  }),
  ck_api_credential_verifier: Object.freeze({
    expression: "key_prefix<>'' AND key_verifier_phc<>'' AND expires_time>create_time",
    tables: Object.freeze(["api_credential"]),
  }),
  ck_tenant_member_role_scope: Object.freeze({
    expression: "role_scope_key='PLATFORM' OR role_scope_key=CAST(tenant_id AS CHAR)",
    tables: Object.freeze(["tenant_member_role"]),
  }),
  ck_gateway_target_status: Object.freeze({
    expression: "status IN ('ACTIVE','DISABLED')",
    tables: Object.freeze(["gateway_target"]),
  }),
  ck_gateway_target_version: Object.freeze({
    expression: "version>0",
    tables: Object.freeze(["gateway_target"]),
  }),
  ck_gateway_route_status: Object.freeze({
    expression: "status IN ('ACTIVE','DISABLED')",
    tables: Object.freeze(["gateway_route"]),
  }),
  ck_gateway_route_version: Object.freeze({
    expression: "version>0",
    tables: Object.freeze(["gateway_route"]),
  }),
  ck_gateway_route_authorization: Object.freeze({
    expression: "(authorization_mode='HUMAN_PERMISSION' AND permission_id IS NOT NULL AND required_scope_code IS NULL) OR (authorization_mode='API_SCOPE' AND permission_id IS NULL AND required_scope_code IS NOT NULL) OR (authorization_mode IN ('PUBLIC','HUMAN_AUTHENTICATED','CHECKOUT_RESOURCE','CHANNEL_CALLBACK') AND permission_id IS NULL AND required_scope_code IS NULL)",
    tables: Object.freeze(["gateway_route"]),
  }),
  ck_gateway_route_limits: Object.freeze({
    expression: "body_limit_bytes>=0 AND body_receive_timeout_millis>0 AND response_timeout_millis>0",
    tables: Object.freeze(["gateway_route"]),
  }),
  ck_gateway_guard_shape: Object.freeze({
    expression: "(rule_type='RATE_LIMIT' AND capacity IS NOT NULL AND refill_tokens IS NOT NULL AND refill_period_millis IS NOT NULL AND concurrency_limit IS NULL) OR (rule_type='CONCURRENCY' AND capacity IS NULL AND refill_tokens IS NULL AND refill_period_millis IS NULL AND concurrency_limit IS NOT NULL)",
    tables: Object.freeze(["gateway_guard_rule"]),
  }),
  ck_gateway_guard_positive: Object.freeze({
    expression: "(capacity IS NULL OR capacity>0) AND (refill_tokens IS NULL OR refill_tokens>0) AND (refill_period_millis IS NULL OR refill_period_millis>0) AND (concurrency_limit IS NULL OR concurrency_limit>0)",
    tables: Object.freeze(["gateway_guard_rule"]),
  }),
  ck_console_resource_shape: Object.freeze({
    expression: "display_name<>'' AND sort_order>=0 AND (parent_resource_id IS NULL OR parent_resource_id<>resource_id)",
    tables: Object.freeze(["console_resource"]),
  }),
  ck_console_resource_status: Object.freeze({
    expression: "status IN ('ACTIVE','DISABLED')",
    tables: Object.freeze(["console_resource"]),
  }),
  ck_console_resource_version: Object.freeze({
    expression: "version>0",
    tables: Object.freeze(["console_resource"]),
  }),
  ck_revision_active_slot: Object.freeze({
    expression: "(status='ACTIVE' AND active_slot=1) OR (status IN ('DRAFT','RETIRED') AND active_slot IS NULL)",
    tables: Object.freeze(["file_scan_policy", "document_template"]),
  }),
  ck_document_commerce: Object.freeze({
    expression: "(commerce_request_event_key IS NULL AND commerce_source_version IS NULL AND commerce_source_payload_hash IS NULL AND commerce_order_context_id IS NULL AND consumer_terms_file_id IS NULL AND refund_policy_file_id IS NULL AND privacy_notice_file_id IS NULL) OR (commerce_request_event_key IS NOT NULL AND commerce_source_version>0 AND commerce_source_payload_hash IS NOT NULL AND commerce_order_context_id IS NOT NULL AND consumer_terms_file_id IS NOT NULL AND refund_policy_file_id IS NOT NULL AND privacy_notice_file_id IS NOT NULL)",
    tables: Object.freeze(["consumer_document"]),
  }),
  ck_document_fiscal: Object.freeze({
    expression: "(fiscal_event_key IS NULL AND fiscal_record_id IS NULL AND fiscal_source_version IS NULL AND fiscal_source_payload_hash IS NULL) OR (fiscal_event_key IS NOT NULL AND fiscal_record_id IS NOT NULL AND fiscal_source_version=1 AND fiscal_source_payload_hash IS NOT NULL)",
    tables: Object.freeze(["consumer_document"]),
  }),
  ck_document_status: Object.freeze({
    expression: "(status='WAITING_FISCAL' AND commerce_request_event_key IS NOT NULL AND fiscal_event_key IS NULL) OR (status='WAITING_REQUEST' AND commerce_request_event_key IS NULL AND fiscal_event_key IS NOT NULL) OR (status IN ('READY_TO_RENDER','RENDERING','GENERATED','SUPERSEDED','CANCELLED') AND commerce_request_event_key IS NOT NULL AND fiscal_event_key IS NOT NULL) OR (status='CONFLICT' AND (commerce_request_event_key IS NOT NULL OR fiscal_event_key IS NOT NULL))",
    tables: Object.freeze(["consumer_document"]),
  }),
  ck_document_version_slot: Object.freeze({
    expression: "(status='GENERATED' AND active_slot=1 AND file_id IS NOT NULL) OR (status IN ('CREATED','PROCESSING','ERROR','FAILED','SUPERSEDED') AND active_slot IS NULL)",
    tables: Object.freeze(["document_version"]),
  }),
  ck_notify_delivery_retry: Object.freeze({
    expression: "attempt_count>=0 AND version>0 AND ((status='RETRY_WAIT' AND next_retry_time IS NOT NULL) OR (status<>'RETRY_WAIT' AND next_retry_time IS NULL))",
    tables: Object.freeze(["notification_delivery"]),
  }),
  ck_notify_attempt_identity: Object.freeze({
    expression: "provider_request_no<>''",
    tables: Object.freeze(["notification_attempt"]),
  }),
  ck_webhook_payload_storage: Object.freeze({
    expression: "(payload_storage_mode='INLINE' AND payload_inline IS NOT NULL AND payload_vault_token IS NULL) OR (payload_storage_mode='VAULT_TOKEN' AND payload_inline IS NULL AND payload_vault_token IS NOT NULL)",
    tables: Object.freeze(["webhook_event"]),
  }),
  ck_webhook_delivery_generation: Object.freeze({
    expression: "(generation_no=0 AND replay_of_delivery_id IS NULL AND requested_by IS NULL AND replay_reason_code IS NULL) OR (generation_no>0 AND replay_of_delivery_id IS NOT NULL AND replay_of_delivery_id<>delivery_id AND requested_by IS NOT NULL AND replay_reason_code IS NOT NULL)",
    tables: Object.freeze(["webhook_delivery"]),
  }),
  ck_audit_attr_value: Object.freeze({
    expression: "(classification IN ('PUBLIC','INTERNAL') AND value_text IS NOT NULL AND value_vault_token IS NULL AND value_masked IS NULL) OR (classification IN ('CONFIDENTIAL','RESTRICTED') AND value_text IS NULL AND value_vault_token IS NOT NULL AND value_masked IS NOT NULL)",
    tables: Object.freeze(["audit_event_attr"]),
  }),
  ck_compensation_lease: Object.freeze({
    expression: "(execute_status='PROCESSING' AND lock_owner IS NOT NULL AND lock_until IS NOT NULL) OR (execute_status IN ('INIT','ERROR','FAILED') AND lock_owner IS NULL AND lock_until IS NULL)",
    tables: Object.freeze(["compensation_event"]),
  }),
  ck_command_response: Object.freeze({
    expression: "(status='SUCCEEDED' AND response_ref IS NOT NULL) OR (status IN ('PROCESSING','FAILED') AND response_ref IS NULL)",
    tables: Object.freeze(["command_idempotency"]),
  }),
});

export const EXPECTED_TOTALS = Object.freeze({
  tables: 72,
  fields: 875,
  uniqueIndexes: 120,
  accessIndexes: 95,
  relations: 111,
  oneToOneRelations: 6,
  diagramGroups: 8,
});

export const EXPECTED_MODEL_DIGEST = "b7b0bf92e23da77a16a39a99d7ddb581dc40ef872a58abc63c9355feeae40247";
export const EXPECTED_STRUCTURAL_DIGEST = "be83e49b805c6c6f86a29c0efc3bf679c6bd54eb328e6d49aec3ab89a0301a3c";
