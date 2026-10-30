-- 联合查询 · mor-test mor_platform 只读预检（SET SESSION TRANSACTION READ ONLY 下执行）。阶段 6 读取 + 阶段 7 第 ③ 步重跑。
SELECT @@transaction_read_only AS read_only, UTC_TIMESTAMP(6) AS checked_at;
-- 1) 结构：新表 / 新列应不存在（前滚前）
SELECT (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_key_type') AS key_type_table,
       (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_template_version' AND COLUMN_NAME = 'joint_json') AS joint_json_column,
       (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_template_version' AND COLUMN_NAME = 'constraints_json') AS constraints_json_column;
-- 2) 块 144 区间占用（应全部 0）
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000144001 AND 900000000000144999) AS permissions,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000144001 AND 900000000000144999) AS role_grants,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000144001 AND 900000000000144999) AS resources,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000144001 AND 900000000000144999) AS routes,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000144001 AND 900000000000144999) AS headers,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000144001 AND 900000000000144999) AS queries,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000144001 AND 900000000000144999) AS guards;
-- 3) 新编码与 (方法, 路径) 占用（应 0 行）
SELECT 'route_code' AS kind, route_code AS value, route_id, status FROM t_gateway_route WHERE route_code IN (
  'operations-query-key-type-list','operations-query-key-type-create','operations-query-key-type-update','operations-query-key-type-status',
  'operations-query-key-type-order','operations-joint-query-settings','operations-joint-query-graph','operations-joint-query-step')
UNION ALL SELECT 'path', CONCAT(http_method, ' ', external_path_template), route_id, status FROM t_gateway_route
 WHERE external_path_template LIKE '/api/operations/data-query-admin/v1/key-type%' OR external_path_template LIKE '/api/operations/data-query/v1/joint-query%'
UNION ALL SELECT 'resource_code', resource_code, resource_id, status FROM t_console_resource WHERE resource_code IN ('OPERATIONS_JOINT_QUERY','OPERATIONS_QUERY_KEY_TYPES')
UNION ALL SELECT 'frontend_route', frontend_route, resource_id, status FROM t_console_resource WHERE frontend_route IN ('/data-query/joint-query','/data-query/key-types');
-- 4) 依赖：块 131 权限、菜单组、target；平台管理员角色授权
SELECT permission_id, permission_code, status FROM t_permission WHERE permission_id IN (900000000000131101, 900000000000131102, 900000000000131103, 900000000000131104) ORDER BY 1;
SELECT resource_id, resource_code, resource_type, frontend_route, status FROM t_console_resource WHERE resource_id BETWEEN 900000000000131301 AND 900000000000131305 ORDER BY 1;
SELECT role_permission_id, role_id, permission_id, status FROM t_role_permission WHERE permission_id BETWEEN 900000000000131101 AND 900000000000131104 ORDER BY 1;
SELECT target_id, target_service_code, status FROM t_gateway_target WHERE target_id = 900000000000000001;
SELECT route_id, route_code, status FROM t_gateway_route WHERE route_id BETWEEN 900000000000131401 AND 900000000000131418 ORDER BY 1;
-- 5) 路由计数与最大块
SELECT status, COUNT(*) AS routes FROM t_gateway_route GROUP BY status ORDER BY status;
SELECT MAX(route_id) AS max_route_id, MAX(resource_id) AS max_resource_id FROM t_gateway_route, (SELECT MAX(resource_id) AS resource_id FROM t_console_resource) r;
SELECT FLOOR((route_id - 900000000000000000) / 1000) AS block, COUNT(*) AS routes, SUM(status = 'ACTIVE') AS active
FROM t_gateway_route WHERE route_id >= 900000000000140000 GROUP BY block ORDER BY block;
-- 6) data-query 现状
SELECT data_source_id, data_source_code, data_source_name, status, host, port, database_name, username, last_check_result, last_check_time FROM t_data_source ORDER BY data_source_code;
SELECT t.template_code, t.template_name, t.status, t.current_version_no, ds.data_source_code FROM t_query_template t LEFT JOIN t_data_source ds ON ds.data_source_id = t.data_source_id ORDER BY t.template_code;
