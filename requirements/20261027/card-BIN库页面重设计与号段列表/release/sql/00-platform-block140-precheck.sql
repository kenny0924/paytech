-- 块 140（card：BIN 库页面重设计与号段列表）执行前只读核对，只在 mor_platform 执行；建议会话 SET SESSION TRANSACTION READ ONLY。
-- 期望：① 块 140 区间在所有 BIGINT 主键表 0 行（总和 0）；② 新 route code 0、新 (方法, 路径) 0；
--       ③ 依赖：权限 900000000000132101 CARD_BIN_LIBRARY_READ ACTIVE、target 900000000000000001 operations-console-bff ACTIVE、
--          同形路由 900000000000132405 存在（header 2 / guard 6 与本块逐行同形）；④ 现网路由计数（激活后期望 ACTIVE +1）。
SELECT 'block140_rows_total' AS t, SUM(n) AS n FROM (
SELECT 't_access_tenant' AS t, COUNT(*) AS n FROM t_access_tenant WHERE tenant_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_api_credential' AS t, COUNT(*) AS n FROM t_api_credential WHERE credential_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_api_credential_scope' AS t, COUNT(*) AS n FROM t_api_credential_scope WHERE scope_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_api_scope' AS t, COUNT(*) AS n FROM t_api_scope WHERE api_scope_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_audit_chain' AS t, COUNT(*) AS n FROM t_audit_chain WHERE chain_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_audit_event' AS t, COUNT(*) AS n FROM t_audit_event WHERE audit_event_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_audit_event_attr' AS t, COUNT(*) AS n FROM t_audit_event_attr WHERE attr_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_auth_session' AS t, COUNT(*) AS n FROM t_auth_session WHERE session_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_authentication_challenge' AS t, COUNT(*) AS n FROM t_authentication_challenge WHERE challenge_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_command_idempotency' AS t, COUNT(*) AS n FROM t_command_idempotency WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_compensation_event' AS t, COUNT(*) AS n FROM t_compensation_event WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_console_resource' AS t, COUNT(*) AS n FROM t_console_resource WHERE resource_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_data_source' AS t, COUNT(*) AS n FROM t_data_source WHERE data_source_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_object' AS t, COUNT(*) AS n FROM t_file_object WHERE file_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_scan_policy' AS t, COUNT(*) AS n FROM t_file_scan_policy WHERE policy_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_scan_requirement' AS t, COUNT(*) AS n FROM t_file_scan_requirement WHERE requirement_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_scan_result' AS t, COUNT(*) AS n FROM t_file_scan_result WHERE scan_result_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_storage_bucket' AS t, COUNT(*) AS n FROM t_file_storage_bucket WHERE bucket_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_storage_instance' AS t, COUNT(*) AS n FROM t_file_storage_instance WHERE instance_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_storage_route' AS t, COUNT(*) AS n FROM t_file_storage_route WHERE route_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_upload' AS t, COUNT(*) AS n FROM t_file_upload WHERE upload_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_file_version' AS t, COUNT(*) AS n FROM t_file_version WHERE version_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_gateway_guard_rule' AS t, COUNT(*) AS n FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_gateway_route' AS t, COUNT(*) AS n FROM t_gateway_route WHERE route_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_gateway_route_header' AS t, COUNT(*) AS n FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_gateway_route_query_param' AS t, COUNT(*) AS n FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_gateway_target' AS t, COUNT(*) AS n FROM t_gateway_target WHERE target_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_application' AS t, COUNT(*) AS n FROM t_i18n_application WHERE application_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_key' AS t, COUNT(*) AS n FROM t_i18n_key WHERE key_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_locale' AS t, COUNT(*) AS n FROM t_i18n_locale WHERE locale_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_namespace' AS t, COUNT(*) AS n FROM t_i18n_namespace WHERE namespace_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_release' AS t, COUNT(*) AS n FROM t_i18n_release WHERE release_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_release_bundle' AS t, COUNT(*) AS n FROM t_i18n_release_bundle WHERE bundle_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_i18n_translation' AS t, COUNT(*) AS n FROM t_i18n_translation WHERE translation_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_jwt_signing_key' AS t, COUNT(*) AS n FROM t_jwt_signing_key WHERE signing_key_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_attempt' AS t, COUNT(*) AS n FROM t_notification_attempt WHERE attempt_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_delivery' AS t, COUNT(*) AS n FROM t_notification_delivery WHERE delivery_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_provider' AS t, COUNT(*) AS n FROM t_notification_provider WHERE provider_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_request' AS t, COUNT(*) AS n FROM t_notification_request WHERE request_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_route' AS t, COUNT(*) AS n FROM t_notification_route WHERE route_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_template' AS t, COUNT(*) AS n FROM t_notification_template WHERE template_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_notification_template_content' AS t, COUNT(*) AS n FROM t_notification_template_content WHERE content_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_password_credential' AS t, COUNT(*) AS n FROM t_password_credential WHERE password_credential_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_permission' AS t, COUNT(*) AS n FROM t_permission WHERE permission_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_query_template' AS t, COUNT(*) AS n FROM t_query_template WHERE template_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_query_template_version' AS t, COUNT(*) AS n FROM t_query_template_version WHERE template_version_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_ref_country' AS t, COUNT(*) AS n FROM t_ref_country WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_ref_country_subdivision' AS t, COUNT(*) AS n FROM t_ref_country_subdivision WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_ref_currency' AS t, COUNT(*) AS n FROM t_ref_currency WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_ref_industry' AS t, COUNT(*) AS n FROM t_ref_industry WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_ref_mcc' AS t, COUNT(*) AS n FROM t_ref_mcc WHERE id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_refresh_token_generation' AS t, COUNT(*) AS n FROM t_refresh_token_generation WHERE refresh_generation_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_role' AS t, COUNT(*) AS n FROM t_role WHERE role_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_role_permission' AS t, COUNT(*) AS n FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_security_event' AS t, COUNT(*) AS n FROM t_security_event WHERE security_event_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_tenant_invitation' AS t, COUNT(*) AS n FROM t_tenant_invitation WHERE invitation_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_tenant_member' AS t, COUNT(*) AS n FROM t_tenant_member WHERE member_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_tenant_member_role' AS t, COUNT(*) AS n FROM t_tenant_member_role WHERE tenant_member_role_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_user_account' AS t, COUNT(*) AS n FROM t_user_account WHERE account_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_webhook_attempt' AS t, COUNT(*) AS n FROM t_webhook_attempt WHERE attempt_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_webhook_delivery' AS t, COUNT(*) AS n FROM t_webhook_delivery WHERE delivery_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_webhook_endpoint' AS t, COUNT(*) AS n FROM t_webhook_endpoint WHERE endpoint_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_webhook_event' AS t, COUNT(*) AS n FROM t_webhook_event WHERE event_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_webhook_signing_secret' AS t, COUNT(*) AS n FROM t_webhook_signing_secret WHERE signing_secret_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_action_run' AS t, COUNT(*) AS n FROM t_workflow_action_run WHERE action_run_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_action_template' AS t, COUNT(*) AS n FROM t_workflow_action_template WHERE action_template_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_approval' AS t, COUNT(*) AS n FROM t_workflow_approval WHERE approval_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_callback' AS t, COUNT(*) AS n FROM t_workflow_callback WHERE callback_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_definition' AS t, COUNT(*) AS n FROM t_workflow_definition WHERE definition_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_definition_version' AS t, COUNT(*) AS n FROM t_workflow_definition_version WHERE definition_version_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_instance' AS t, COUNT(*) AS n FROM t_workflow_instance WHERE instance_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_node_run' AS t, COUNT(*) AS n FROM t_workflow_node_run WHERE node_run_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_transfer' AS t, COUNT(*) AS n FROM t_workflow_transfer WHERE transfer_id BETWEEN 900000000000140001 AND 900000000000140999
UNION ALL SELECT 't_workflow_verification' AS t, COUNT(*) AS n FROM t_workflow_verification WHERE verification_id BETWEEN 900000000000140001 AND 900000000000140999
) x;
SELECT 'new_route_code' AS t, COUNT(*) AS n FROM t_gateway_route WHERE route_code = 'operations-card-bin-range-list'
UNION ALL SELECT 'new_method_path', COUNT(*) FROM t_gateway_route WHERE http_method = 'GET' AND external_path_template = '/api/operations/card-admin/v1/bin-library/versions/{versionNo}/ranges'
UNION ALL SELECT 'same_downstream_path', COUNT(*) FROM t_gateway_route WHERE downstream_path_template = '/card-admin/v1/bin-library/versions/{versionNo}/ranges';
SELECT 'dep_permission_132101' AS t, permission_id AS id, permission_code AS code, status FROM t_permission WHERE permission_id = 900000000000132101
UNION ALL SELECT 'dep_target_001', target_id, target_service_code, status FROM t_gateway_target WHERE target_id = 900000000000000001
UNION ALL SELECT 'shape_route_132405', route_id, route_code, status FROM t_gateway_route WHERE route_id = 900000000000132405;
SELECT 'shape_132405' AS t, channel, http_method, security_profile_code, authorization_mode, permission_id, request_cookie_mode, response_cookie_mode,
       idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, target_id
  FROM t_gateway_route WHERE route_id = 900000000000132405;
SELECT 'shape_132405_header' AS t, header_name, header_use FROM t_gateway_route_header WHERE route_id = 900000000000132405 ORDER BY header_name;
SELECT 'shape_132405_guard' AS t, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit
  FROM t_gateway_guard_rule WHERE route_id = 900000000000132405 ORDER BY rule_type, dimension;
SELECT 'routes_by_status' AS t, status, COUNT(*) AS n FROM t_gateway_route GROUP BY status ORDER BY status;
SELECT 'card_routes' AS t, route_id, route_code, status, http_method, external_path_template FROM t_gateway_route
 WHERE external_path_template LIKE '/api/operations/card-admin/%' ORDER BY route_id;
