-- operations 明细账运维 · 注册块 148 核对（插入后 / 激活后 / 停用后均可执行；READ ONLY 会话可执行；目标库 mor_platform）。
SET NAMES utf8mb4;
-- 行数：permission 1 / role_permission 1 / resource 3 / route 4 / header 16 / query_param 2 / guard 24
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000148001 AND 900000000000148999) AS `permissions_1`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000148001 AND 900000000000148999) AS `role_permissions_1`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000148001 AND 900000000000148999) AS `resources_3`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000148001 AND 900000000000148999) AS `routes_4`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000148001 AND 900000000000148999) AS `headers_16`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000148001 AND 900000000000148999) AS `query_params_2`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000148001 AND 900000000000148999) AS `guards_24`;
-- 状态：休眠 = 授权 REVOKED / 资源与路由 DISABLED；激活 = 全部 ACTIVE；权限始终 ACTIVE
SELECT 'role_permission' AS `kind`, status, COUNT(*) AS `n` FROM t_role_permission WHERE role_permission_id = 900000000000148201 GROUP BY status
UNION ALL SELECT 'resource', status, COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000148301 AND 900000000000148303 GROUP BY status
UNION ALL SELECT 'route', status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404 GROUP BY status
UNION ALL SELECT 'permission', status, COUNT(*) FROM t_permission WHERE permission_id = 900000000000148101 GROUP BY status;
-- 路由权限绑定：GET 两条 = 97101（FINANCE_ACCOUNTING_READ），POST 两条 = 148101（FINANCE_SUBLEDGER_MANAGE）
SELECT route_id, route_code, http_method, permission_id, body_limit_bytes, idempotency_mode, response_timeout_millis
FROM t_gateway_route WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404 ORDER BY route_id;
-- 同形核对：GET 与明细账批次详情 99403、POST 与发起关账 121404 的路由属性、请求头逐项相同（期望 route_same / headers_same 全 1；
-- 重建 148404 的 body_limit_bytes 为 1024，route_same 为 0 属预期）。
SELECT n.route_code,
       (n.security_profile_code, n.authorization_mode, n.request_cookie_mode, n.response_cookie_mode, n.idempotency_mode, n.body_limit_bytes,
        n.body_receive_timeout_millis, n.response_timeout_millis, n.target_id)
       <=> (o.security_profile_code, o.authorization_mode, o.request_cookie_mode, o.response_cookie_mode, o.idempotency_mode, o.body_limit_bytes,
        o.body_receive_timeout_millis, o.response_timeout_millis, o.target_id) AS `route_same_1`,
       (SELECT GROUP_CONCAT(CONCAT(header_name, ':', header_use) ORDER BY header_name, header_use) FROM t_gateway_route_header WHERE route_id = n.route_id)
       <=> (SELECT GROUP_CONCAT(CONCAT(header_name, ':', header_use) ORDER BY header_name, header_use) FROM t_gateway_route_header WHERE route_id = o.route_id) AS `headers_same_1`
FROM t_gateway_route n JOIN t_gateway_route o ON o.route_id = IF(n.http_method = 'GET', 900000000000099403, 900000000000121404)
WHERE n.route_id BETWEEN 900000000000148401 AND 900000000000148404 ORDER BY n.route_id;
-- Guard：GET 300+50 / 30+3 / 30+2；POST 300+50 / 30+3 / 10+1
SELECT route_id, GROUP_CONCAT(CONCAT(rule_type, '/', dimension, '/', IFNULL(capacity, ''), '/', IFNULL(concurrency_limit, '')) ORDER BY dimension, rule_type) AS `guards`
FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000148701 AND 900000000000148724 GROUP BY route_id ORDER BY route_id;
-- 全局路由计数（休眠后 DISABLED +4；激活后 ACTIVE +4）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes` FROM t_gateway_route;
