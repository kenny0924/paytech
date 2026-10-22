-- 工作流 mor-test 发布：激活后只读核对。ok=1 表示符合。PLATFORM_ADMIN 版本期望 = 00-precheck 记录值 + 1（阶段 6 记录为 43，激活后 44）。
SELECT 'workflow tables' AS item, COUNT(*) AS actual, 10 AS expect, COUNT(*) = 10 AS ok
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME LIKE 't\_workflow\_%'
UNION ALL SELECT 'graph_json comment updated (48 KiB)', COUNT(*), 1, COUNT(*) = 1 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_workflow_definition_version' AND COLUMN_NAME='graph_json'
    AND COLUMN_COMMENT LIKE '%不超过 48 KiB%'
UNION ALL SELECT 'start_form_json comment updated (12 KiB)', COUNT(*), 1, COUNT(*) = 1 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_workflow_definition_version' AND COLUMN_NAME='start_form_json'
    AND COLUMN_COMMENT LIKE '%不超过 12 KiB%'
UNION ALL SELECT 'permission ACTIVE', COUNT(*), 7, COUNT(*) = 7 FROM mor_platform.t_permission
  WHERE permission_id BETWEEN 900000000000127001 AND 900000000000127999 AND status='ACTIVE'
UNION ALL SELECT 'role_permission ACTIVE', COUNT(*), 7, COUNT(*) = 7 FROM mor_platform.t_role_permission
  WHERE role_permission_id BETWEEN 900000000000127001 AND 900000000000127999 AND status='ACTIVE'
UNION ALL SELECT 'console_resource ACTIVE', COUNT(*), 10, COUNT(*) = 10 FROM mor_platform.t_console_resource
  WHERE resource_id BETWEEN 900000000000127001 AND 900000000000127999 AND status='ACTIVE'
UNION ALL SELECT 'gateway_route ACTIVE', COUNT(*), 32, COUNT(*) = 32 FROM mor_platform.t_gateway_route
  WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999 AND status='ACTIVE'
UNION ALL SELECT 'route_header', COUNT(*), 136, COUNT(*) = 136 FROM mor_platform.t_gateway_route_header
  WHERE route_header_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'query_param', COUNT(*), 29, COUNT(*) = 29 FROM mor_platform.t_gateway_route_query_param
  WHERE route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'guard_rule', COUNT(*), 192, COUNT(*) = 192 FROM mor_platform.t_gateway_guard_rule
  WHERE guard_rule_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'notification template/content/route', 
    (SELECT COUNT(*) FROM mor_platform.t_notification_template WHERE template_id=900000000000127081 AND status='ACTIVE')
  + (SELECT COUNT(*) FROM mor_platform.t_notification_template_content WHERE content_id=900000000000127082)
  + (SELECT COUNT(*) FROM mor_platform.t_notification_route WHERE route_id=900000000000127083 AND status='ACTIVE'), 3,
    (SELECT COUNT(*) FROM mor_platform.t_notification_template WHERE template_id=900000000000127081 AND status='ACTIVE')
  + (SELECT COUNT(*) FROM mor_platform.t_notification_template_content WHERE content_id=900000000000127082)
  + (SELECT COUNT(*) FROM mor_platform.t_notification_route WHERE route_id=900000000000127083 AND status='ACTIVE') = 3
UNION ALL SELECT 'PLATFORM_ADMIN version (expect precheck+1)', version, NULL, NULL FROM mor_platform.t_role
  WHERE role_id = 900000000000000510;
