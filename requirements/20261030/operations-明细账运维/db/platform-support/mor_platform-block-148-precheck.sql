-- operations 明细账运维 · 注册块 148 · 发布前只读预检。READ ONLY 会话执行，目标库 mor_platform。
SET NAMES utf8mb4;
-- 1) 块 148 七张表区间均未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000148001 AND 900000000000148999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000148001 AND 900000000000148999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000148001 AND 900000000000148999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000148001 AND 900000000000148999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000148001 AND 900000000000148999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000148001 AND 900000000000148999) AS `query_params`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000148001 AND 900000000000148999) AS `guards`;
-- 2) 编码与路径未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_code = 'FINANCE_SUBLEDGER_MANAGE') AS `permission_code`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_code LIKE 'OPERATIONS_FINANCE_POSTING_OPS%' OR frontend_route = '/finance/posting-ops') AS `resource_codes`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_code IN ('operations-finance-posting-progress', 'operations-finance-posting-stream-postings',
            'operations-finance-posting-stream-catch-up', 'operations-finance-posting-rebuild')
          OR external_path_template LIKE '/api/operations/finance/posting-progress/%'
          OR external_path_template LIKE '/api/operations/finance/posting-streams/%'
          OR external_path_template LIKE '/api/operations/finance/posting-rebuilds/%') AS `route_codes_or_paths`;
-- 3) 依赖：管理员角色 510、财务菜单 97301、查看权限 97101（FINANCE_ACCOUNTING_READ）、目标 1（ops-bff）均 ACTIVE
SELECT role_id, role_code, status, version FROM t_role WHERE role_id = 900000000000000510;
SELECT resource_id, resource_code, resource_type, status FROM t_console_resource WHERE resource_id = 900000000000097301;
SELECT permission_id, permission_code, status FROM t_permission WHERE permission_id = 900000000000097101;
SELECT target_id, status FROM t_gateway_target WHERE target_id = 900000000000000001;
-- 4) 财务菜单现有子菜单与排序（新菜单 sort 45：明细账查询 40 之后、调整 50 之前）
SELECT resource_id, resource_code, sort_order, status FROM t_console_resource WHERE parent_resource_id = 900000000000097301 ORDER BY sort_order;
-- 5) 现网路由计数与同形路由（GET 99403 明细账批次详情、POST 121404 发起关账）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes`, MAX(route_id) AS `max_route` FROM t_gateway_route;
SELECT route_id, route_code, status FROM t_gateway_route WHERE route_id IN (900000000000099403, 900000000000121404);
