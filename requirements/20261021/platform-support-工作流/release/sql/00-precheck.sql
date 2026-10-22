-- 工作流 mor-test 发布：执行前只读核对（不写任何数据）。客户端 utf8mb4，root 或只读账号均可。
-- 每行 expect 为期望值，ok=1 表示符合；任一 ok=0 即停。
SELECT 'workflow tables (expect 0)' AS item, COUNT(*) AS actual, 0 AS expect, COUNT(*) = 0 AS ok
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME LIKE 't\_workflow\_%'
UNION ALL SELECT 'mor_platform table count', COUNT(*), NULL, NULL
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform'
UNION ALL SELECT 'block 127 t_permission', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_permission
  WHERE permission_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_role_permission', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_role_permission
  WHERE role_permission_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_console_resource', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_console_resource
  WHERE resource_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_gateway_route', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_gateway_route
  WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_gateway_route_header', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_gateway_route_header
  WHERE route_header_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_gateway_route_query_param', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_gateway_route_query_param
  WHERE route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_gateway_guard_rule', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_gateway_guard_rule
  WHERE guard_rule_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_notification_template', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_notification_template
  WHERE template_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_notification_template_content', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_notification_template_content
  WHERE content_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 t_notification_route', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_notification_route
  WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'unique: permission_code WORKFLOW_*', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_permission
  WHERE permission_code LIKE 'WORKFLOW\_%'
UNION ALL SELECT 'unique: resource_code OPERATIONS_WORKFLOW*', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_console_resource
  WHERE resource_code LIKE 'OPERATIONS\_WORKFLOW%'
UNION ALL SELECT 'unique: route_code operations-workflow-*', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_gateway_route
  WHERE route_code LIKE 'operations-workflow-%'
UNION ALL SELECT 'unique: route path /api/operations/workflow*', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_gateway_route
  WHERE external_path_template LIKE '/api/operations/workflow%'
UNION ALL SELECT 'unique: template_code WORKFLOW_NOTIFICATION', COUNT(*), 0, COUNT(*) = 0 FROM mor_platform.t_notification_template
  WHERE template_code = 'WORKFLOW_NOTIFICATION'
UNION ALL SELECT 'role PLATFORM_ADMIN 510 exists', COUNT(*), 1, COUNT(*) = 1 FROM mor_platform.t_role
  WHERE role_id = 900000000000000510
UNION ALL SELECT 'role PLATFORM_ADMIN 510 version (record)', MAX(version), NULL, NULL FROM mor_platform.t_role
  WHERE role_id = 900000000000000510
UNION ALL SELECT 'gateway target 900000000000000001 (operations-console-bff) exists', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_target WHERE target_id = 900000000000000001
UNION ALL SELECT 'SES provider 900000000000070001 exists', COUNT(*), 1, COUNT(*) = 1 FROM mor_platform.t_notification_provider
  WHERE provider_id = 900000000000070001
UNION ALL SELECT 'scan policy CLIENT_ONBOARDING_FORMAT ACTIVE (workflow attachments)', COUNT(*), 1, COUNT(*) >= 1
  FROM mor_platform.t_file_scan_policy WHERE policy_code = 'CLIENT_ONBOARDING_FORMAT' AND status = 'ACTIVE'
UNION ALL SELECT 'storage default route * (workflow attachments)', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route WHERE source_system = '*'
UNION ALL SELECT 'max used route block (record)', MAX(FLOOR((route_id - 900000000000000000) / 1000)), NULL, NULL
  FROM mor_platform.t_gateway_route WHERE route_id >= 900000000000000000;

SELECT provider_id, provider_code, medium, provider_type, status, from_address IS NOT NULL AS has_from, credential_vault_token IS NOT NULL AS has_credential FROM mor_platform.t_notification_provider
 WHERE provider_id = 900000000000070001;
SELECT role_id, role_code, status, version FROM mor_platform.t_role WHERE role_id = 900000000000000510;
