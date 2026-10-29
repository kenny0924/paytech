-- REQ-03 · 注册块 143 核对（插入后 / 激活后 / 停用后均可执行；READ ONLY 会话可执行）。
SET NAMES utf8mb4;
-- 行数：permissions 3 / role_permissions 3 / resources 3 / routes 12 / headers 52 / queries 8 / guards 72（共 153）
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000143001 AND 900000000000143999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000143001 AND 900000000000143999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000143001 AND 900000000000143999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000143001 AND 900000000000143999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000143001 AND 900000000000143999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000143001 AND 900000000000143999) AS `queries`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000143001 AND 900000000000143999) AS `guards`;
-- 状态：休眠 = 授权 REVOKED / 资源与路由 DISABLED；激活 = 全部 ACTIVE
SELECT 'role_permission' AS `kind`, status, COUNT(*) AS `n` FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000143201 AND 900000000000143203 GROUP BY status
UNION ALL SELECT 'resource', status, COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000143301 AND 900000000000143303 GROUP BY status
UNION ALL SELECT 'route', status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000143401 AND 900000000000143412 GROUP BY status
UNION ALL SELECT 'permission', status, COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000143101 AND 900000000000143103 GROUP BY status;
-- 全局路由计数（阶段 6：ACTIVE 619 / DISABLED 6；休眠后 619 / 18；激活后 631 / 6）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes` FROM t_gateway_route;
