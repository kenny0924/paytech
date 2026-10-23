-- 工作流体验优化 · mor-test 执行后只读核对（10 与 20 都已提交后执行）。ok=1 表示符合。
-- 角色 510 版本期望 = 00-precheck 记录值 + 1（2026-10-23 阶段 6 记录为 46，执行后 47）；动作运行 COMPLETED 数 = 预检 VERIFIED 数（记录为 4）。
SELECT 'action_run legacy status left' AS item, COUNT(*) AS actual, 0 AS expect, COUNT(*) = 0 AS ok
  FROM mor_platform.t_workflow_action_run WHERE status IN ('VERIFIED', 'UNVERIFIED')
UNION ALL SELECT 'action_run unknown status', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_workflow_action_run WHERE status NOT IN ('PENDING', 'COMPLETED', 'CANCELLED')
UNION ALL SELECT 'status column comment updated', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME = 't_workflow_action_run'
   AND COLUMN_NAME = 'status' AND COLUMN_COMMENT LIKE 'PENDING 待处理；COMPLETED 已完成；%'
UNION ALL SELECT 'required column comment updated', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME = 't_workflow_action_run'
   AND COLUMN_NAME = 'required' AND COLUMN_COMMENT LIKE '%必须 COMPLETED%'
UNION ALL SELECT 'table comment updated', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME = 't_workflow_action_run'
   AND TABLE_COMMENT LIKE '%最新一轮 COMPLETED 的输出'
UNION ALL SELECT 'menu 127311 ACTIVE as specified', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_console_resource WHERE resource_id = 900000000000127311
   AND resource_code = 'OPERATIONS_WORKFLOW_STARTED' AND component_key = 'OPERATIONS_WORKFLOW_STARTED'
   AND frontend_route = '/workflow/started' AND parent_resource_id = 900000000000127301
   AND permission_id = 900000000000127105 AND status = 'ACTIVE'
UNION ALL SELECT 'route 127429 removed', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route WHERE route_id = 900000000000127429
UNION ALL SELECT 'route 127429 headers removed', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route_header WHERE route_id = 900000000000127429
UNION ALL SELECT 'route 127429 guard rules removed', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_guard_rule WHERE route_id = 900000000000127429
UNION ALL SELECT 'block 127 console_resource', COUNT(*), 11, COUNT(*) = 11
  FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 gateway_route', COUNT(*), 31, COUNT(*) = 31
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 gateway_route ACTIVE', COUNT(*), 31, COUNT(*) = 31
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999 AND status = 'ACTIVE'
UNION ALL SELECT 'block 127 route_header', COUNT(*), 130, COUNT(*) = 130
  FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 guard_rule', COUNT(*), 186, COUNT(*) = 186
  FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 query_param', COUNT(*), 30, COUNT(*) = 30
  FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'role 510 version 47', MAX(version), 47, MAX(version) = 47
  FROM mor_platform.t_role WHERE role_id = 900000000000000510;

SELECT status, COUNT(*) AS action_runs FROM mor_platform.t_workflow_action_run GROUP BY status ORDER BY status;
SELECT role_id, version, update_time FROM mor_platform.t_role WHERE role_id = 900000000000000510;
