-- tax 结账算税与事件登记 · 注册块 146 核对（插入后 / 激活后 / 停用后均可执行；READ ONLY 会话可执行；目标库 mor_platform）。
SET NAMES utf8mb4;
-- 行数：permission 1 / role_permission 1 / resource 1 / route 2 / header 8 / guard 12 / file_route 1
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000146001 AND 900000000000146999) AS `permissions_1`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000146001 AND 900000000000146999) AS `role_permissions_1`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000146001 AND 900000000000146999) AS `resources_1`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000146001 AND 900000000000146999) AS `routes_2`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000146001 AND 900000000000146999) AS `headers_8`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000146001 AND 900000000000146999) AS `guards_12`,
       (SELECT COUNT(*) FROM t_file_storage_route WHERE route_id = 900000000000146801) AS `file_route_1`;
-- 状态：休眠 = 授权 REVOKED / 资源与路由 DISABLED；激活 = 全部 ACTIVE
SELECT 'role_permission' AS `kind`, status, COUNT(*) AS `n` FROM t_role_permission WHERE role_permission_id = 900000000000146201 GROUP BY status
UNION ALL SELECT 'resource', status, COUNT(*) FROM t_console_resource WHERE resource_id = 900000000000146301 GROUP BY status
UNION ALL SELECT 'route', status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000146401 AND 900000000000146402 GROUP BY status
UNION ALL SELECT 'permission', status, COUNT(*) FROM t_permission WHERE permission_id = 900000000000146101 GROUP BY status;
-- 文件写入路由：与默认私有路由同桶、同扫描策略、同期限
SELECT t.route_id, t.source_system, t.access_level, t.bucket_id = d.bucket_id AS `same_bucket_1`, t.scan_policy_code = d.scan_policy_code AS `same_policy_1`,
       t.max_upload_seconds = d.max_upload_seconds AS `same_ttl_1`
FROM t_file_storage_route t JOIN t_file_storage_route d ON d.source_system = '*' AND d.access_level = 'PRIVATE' WHERE t.route_id = 900000000000146801;
-- 同形核对：新路由与同形现网路由（GET 134411、下载 POST 134412）的路由属性、请求头、Guard 逐项相同（期望 shape_diff 全 0）
SELECT n.route_code,
       (n.security_profile_code, n.authorization_mode, n.request_cookie_mode, n.response_cookie_mode, n.idempotency_mode, n.body_limit_bytes,
        n.body_receive_timeout_millis, n.response_timeout_millis, n.target_id)
       <=> (o.security_profile_code, o.authorization_mode, o.request_cookie_mode, o.response_cookie_mode, o.idempotency_mode, o.body_limit_bytes,
        o.body_receive_timeout_millis, o.response_timeout_millis, o.target_id) AS `route_same_1`,
       (SELECT GROUP_CONCAT(CONCAT(header_name, ':', header_use) ORDER BY header_name, header_use) FROM t_gateway_route_header WHERE route_id = n.route_id)
       <=> (SELECT GROUP_CONCAT(CONCAT(header_name, ':', header_use) ORDER BY header_name, header_use) FROM t_gateway_route_header WHERE route_id = o.route_id) AS `headers_same_1`,
       (SELECT GROUP_CONCAT(CONCAT(rule_type, '/', dimension, '/', IFNULL(capacity, ''), '/', IFNULL(refill_tokens, ''), '/', IFNULL(refill_period_millis, ''), '/', IFNULL(concurrency_limit, '')) ORDER BY rule_type, dimension) FROM t_gateway_guard_rule WHERE route_id = n.route_id)
       <=> (SELECT GROUP_CONCAT(CONCAT(rule_type, '/', dimension, '/', IFNULL(capacity, ''), '/', IFNULL(refill_tokens, ''), '/', IFNULL(refill_period_millis, ''), '/', IFNULL(concurrency_limit, '')) ORDER BY rule_type, dimension) FROM t_gateway_guard_rule WHERE route_id = o.route_id) AS `guards_same_1`
FROM t_gateway_route n JOIN t_gateway_route o ON o.route_id = IF(n.route_id = 900000000000146401, 900000000000134411, 900000000000134412)
WHERE n.route_id BETWEEN 900000000000146401 AND 900000000000146402 ORDER BY n.route_id;
-- 全局路由计数（阶段 6：ACTIVE 631 / DISABLED 6；休眠后 631 / 8；激活后 633 / 6）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes` FROM t_gateway_route;
