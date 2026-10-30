-- tax 结账算税与事件登记 · 注册块 146 · 发布前只读预检（runbook ④）。READ ONLY 会话执行，目标库 mor_platform。
SET NAMES utf8mb4;
-- 1) 块 146 七张表区间均未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000146001 AND 900000000000146999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000146001 AND 900000000000146999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000146001 AND 900000000000146999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000146001 AND 900000000000146999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000146001 AND 900000000000146999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000146001 AND 900000000000146999) AS `guards`,
       (SELECT COUNT(*) FROM t_file_storage_route WHERE route_id BETWEEN 900000000000146001 AND 900000000000146999) AS `file_routes`;
-- 2) 编码、路径与写入路由未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_code = 'TAX_VIEW') AS `permission_code`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_code = 'OPERATIONS_FINANCE_TAX' OR frontend_route = '/finance/tax') AS `resource_code`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_code LIKE 'operations-finance-tax-%' OR external_path_template LIKE '/api/operations/tax/%') AS `route_codes_or_paths`,
       (SELECT COUNT(*) FROM t_file_storage_route WHERE source_system = 'tax-service') AS `file_route_tax_service`;
-- 3) 依赖：管理员角色 510、财务菜单 97301、目标 1（ops-bff）均 ACTIVE；默认私有写入路由存在且扫描策略允许 application/pdf
SELECT role_id, role_code, status, version FROM t_role WHERE role_id = 900000000000000510;
SELECT resource_id, resource_code, resource_type, status FROM t_console_resource WHERE resource_id = 900000000000097301;
SELECT target_id, status FROM t_gateway_target WHERE target_id = 900000000000000001;
SELECT r.route_id, r.bucket_id, b.bucket_name, r.scan_policy_code, r.max_upload_seconds,
       JSON_CONTAINS(p.allowed_mime_rules, '"application/pdf"', '$.mimeTypes') AS `pdf_allowed_expect_1`, p.max_size_bytes, p.status
FROM t_file_storage_route r JOIN t_file_storage_bucket b ON b.bucket_id = r.bucket_id
JOIN t_file_scan_policy p ON p.policy_code = r.scan_policy_code AND p.status = 'ACTIVE'
WHERE r.source_system = '*' AND r.access_level = 'PRIVATE';  -- 238885821824974848 / mor-platform-files / CLIENT_ONBOARDING_FORMAT / 1
-- 4) 财务菜单现有子菜单与排序（新菜单 sort 75：记账规则 70 之后、对账 80 之前）
SELECT resource_id, resource_code, sort_order, status FROM t_console_resource WHERE parent_resource_id = 900000000000097301 ORDER BY sort_order;
-- 5) 现网路由计数（阶段 6：ACTIVE 631 / DISABLED 6）与同形路由（GET 134411、下载 POST 134412）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes`, MAX(route_id) AS `max_route` FROM t_gateway_route;
SELECT route_id, route_code, status FROM t_gateway_route WHERE route_id IN (900000000000134411, 900000000000134412);
