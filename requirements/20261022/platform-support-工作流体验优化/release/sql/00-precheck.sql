-- 工作流体验优化 · mor-test 执行前只读核对（不写任何数据；root 或只读账号，客户端 utf8mb4）。
-- ok=1 表示符合期望；任一 ok=0 即停。第二段为记录值（迁移行数 V / U、角色版本），10 / 20 的 dry 输出须与之相等。
SELECT 'menu 127311 absent' AS item, COUNT(*) AS actual, 0 AS expect, COUNT(*) = 0 AS ok
  FROM mor_platform.t_console_resource WHERE resource_id = 900000000000127311
UNION ALL SELECT 'resource_code OPERATIONS_WORKFLOW_STARTED unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE resource_code = 'OPERATIONS_WORKFLOW_STARTED'
UNION ALL SELECT 'frontend_route /workflow/started unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE frontend_route = '/workflow/started'
UNION ALL SELECT 'parent menu 127301 ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_console_resource WHERE resource_id = 900000000000127301 AND status = 'ACTIVE'
UNION ALL SELECT 'permission 127105 present', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_permission WHERE permission_id = 900000000000127105
UNION ALL SELECT 'route 127429 reverify present', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_route WHERE route_id = 900000000000127429 AND route_code = 'operations-workflow-action-reverify'
UNION ALL SELECT 'route 127429 headers (127613-127618)', COUNT(*), 6, COUNT(*) = 6
  FROM mor_platform.t_gateway_route_header WHERE route_id = 900000000000127429
   AND route_header_id BETWEEN 900000000000127613 AND 900000000000127618
UNION ALL SELECT 'route 127429 headers total', COUNT(*), 6, COUNT(*) = 6
  FROM mor_platform.t_gateway_route_header WHERE route_id = 900000000000127429
UNION ALL SELECT 'route 127429 guard rules (127869-127874)', COUNT(*), 6, COUNT(*) = 6
  FROM mor_platform.t_gateway_guard_rule WHERE route_id = 900000000000127429
   AND guard_rule_id BETWEEN 900000000000127869 AND 900000000000127874
UNION ALL SELECT 'route 127429 guard rules total', COUNT(*), 6, COUNT(*) = 6
  FROM mor_platform.t_gateway_guard_rule WHERE route_id = 900000000000127429
UNION ALL SELECT 'route 127429 query params', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route_query_param WHERE route_id = 900000000000127429
UNION ALL SELECT 'role 510 PLATFORM_ADMIN present', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL SELECT 'action_run unknown status', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_workflow_action_run WHERE status NOT IN ('PENDING', 'VERIFIED', 'UNVERIFIED', 'CANCELLED')
UNION ALL SELECT 'block 127 console_resource', COUNT(*), 10, COUNT(*) = 10
  FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 gateway_route', COUNT(*), 32, COUNT(*) = 32
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 route_header', COUNT(*), 136, COUNT(*) = 136
  FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 guard_rule', COUNT(*), 192, COUNT(*) = 192
  FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 query_param', COUNT(*), 30, COUNT(*) = 30
  FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999;

-- 记录值
SELECT status, COUNT(*) AS action_runs FROM mor_platform.t_workflow_action_run GROUP BY status ORDER BY status;
SELECT role_id, role_code, version, update_time FROM mor_platform.t_role WHERE role_id = 900000000000000510;
SELECT resource_id, resource_code, status, frontend_route FROM mor_platform.t_console_resource
 WHERE parent_resource_id = 900000000000127301 ORDER BY sort_order, resource_id;
SELECT route_id, route_code, http_method, external_path_template, status, version FROM mor_platform.t_gateway_route
 WHERE route_id = 900000000000127429;
