-- REQ-04 · 注册块 145 核对（插入后 / 激活后 / 停用后均可执行；READ ONLY 会话可执行）。
SET NAMES utf8mb4;
-- 行数：permissions 4 / role_permissions 4 / resources 4 / routes 11 / headers 46 / queries 6 / guards 66（共 141）
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000145001 AND 900000000000145999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000145001 AND 900000000000145999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000145001 AND 900000000000145999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000145001 AND 900000000000145999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000145001 AND 900000000000145999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000145001 AND 900000000000145999) AS `queries`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000145001 AND 900000000000145999) AS `guards`;
-- 状态：休眠 = 授权 REVOKED / 资源与路由 DISABLED；激活 = 全部 ACTIVE；权限始终 ACTIVE
SELECT 'role_permission' AS `kind`, status, COUNT(*) AS `n` FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000145201 AND 900000000000145204 GROUP BY status
UNION ALL SELECT 'resource', status, COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000145301 AND 900000000000145304 GROUP BY status
UNION ALL SELECT 'route', status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000145401 AND 900000000000145411 GROUP BY status
UNION ALL SELECT 'permission', status, COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000145101 AND 900000000000145104 GROUP BY status;
-- 全局路由计数（激活后 ACTIVE +11）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes` FROM t_gateway_route;
