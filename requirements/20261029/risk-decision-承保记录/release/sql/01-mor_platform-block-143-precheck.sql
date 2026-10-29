-- REQ-03 · mor_platform 注册块 143 发布前只读预检（runbook ④）。READ ONLY 会话执行。
SET NAMES utf8mb4;
-- 1) 块 143 七张表区间均未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000143001 AND 900000000000143999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000143001 AND 900000000000143999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000143001 AND 900000000000143999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000143001 AND 900000000000143999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000143001 AND 900000000000143999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000143001 AND 900000000000143999) AS `queries`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000143001 AND 900000000000143999) AS `guards`;
-- 2) 编码与路径未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_code LIKE 'RISK_UNDERWRITING%') AS `permission_codes`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_code LIKE 'OPERATIONS_RISK_UNDERWRITING%' OR frontend_route = '/risk/underwriting') AS `resource_codes`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_code LIKE 'operations-risk-underwriting-%'
          OR external_path_template LIKE '/api/operations/risk-underwriting/%') AS `route_codes_or_paths`;
-- 3) 依赖：管理员角色 510、风控菜单父资源 565、目标 1（ops-bff）
SELECT role_id, role_code, status FROM t_role WHERE role_id = 900000000000000510;
SELECT resource_id, resource_code, resource_type, status FROM t_console_resource WHERE resource_id = 900000000000000565;
SELECT target_id, status FROM t_gateway_target WHERE target_id = 900000000000000001;
-- 4) 风控菜单现有子菜单与排序（新菜单 sort 90）
SELECT resource_id, resource_code, sort_order, status FROM t_console_resource WHERE parent_resource_id = 900000000000000565 ORDER BY sort_order;
-- 5) 现网最大块与路由计数
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes`, MAX(route_id) AS `max_route` FROM t_gateway_route;
SELECT (SELECT MAX(permission_id) FROM t_permission) AS `max_permission`, (SELECT MAX(resource_id) FROM t_console_resource) AS `max_resource`;
