-- REQ-04 · mor_platform 注册块 145 只读预检（runbook ③）。在 READ ONLY 会话执行。
SET NAMES utf8mb4;
-- 1) 七张表块 145 区间未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000145001 AND 900000000000145999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000145001 AND 900000000000145999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000145001 AND 900000000000145999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000145001 AND 900000000000145999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000145001 AND 900000000000145999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000145001 AND 900000000000145999) AS `queries`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000145001 AND 900000000000145999) AS `guards`;
-- 2) 编码 / 路径未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_code LIKE 'RISK\_LIMIT\_TIER%') AS `perm_codes`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_code LIKE 'OPERATIONS\_RISK\_LIMIT\_TIER%' OR frontend_route = '/risk/limit-tiers') AS `resource_codes`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_code LIKE 'operations-risk-limit-tier-%' OR external_path_template LIKE '/api/operations/risk-limit-tiers/%') AS `route_codes`;
-- 3) 依赖：管理员角色 510、风控父菜单 565、ops-bff target 1、目录权限 536 均 ACTIVE
SELECT 'role' AS `kind`, role_id AS `id`, role_code AS `code`, status FROM t_role WHERE role_id = 900000000000000510
UNION ALL SELECT 'parent_menu', resource_id, resource_code, status FROM t_console_resource WHERE resource_id = 900000000000000565
UNION ALL SELECT 'target', target_id, target_service_code, status FROM t_gateway_target WHERE target_id = 900000000000000001
UNION ALL SELECT 'config_read', permission_id, permission_code, status FROM t_permission WHERE permission_id = 900000000000000536;
-- 4) 现网最大块（应 ≤ 144；若已有 146+ 说明并行需求已占用后续块，仍不冲突）
SELECT MAX(route_id) AS `max_route`, (SELECT MAX(permission_id) FROM t_permission WHERE permission_id < 900000000001000000) AS `max_permission`,
       (SELECT MAX(resource_id) FROM t_console_resource WHERE resource_id < 900000000001000000) AS `max_resource` FROM t_gateway_route WHERE route_id < 900000000001000000;
-- 5) 风控父菜单下现有子菜单与排序（新菜单"限额档位"sort 见块正文）
SELECT resource_id, resource_code, sort_order, status FROM t_console_resource WHERE parent_resource_id = 900000000000000565 ORDER BY sort_order, resource_id;
-- 6) 全局路由计数
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes` FROM t_gateway_route;
