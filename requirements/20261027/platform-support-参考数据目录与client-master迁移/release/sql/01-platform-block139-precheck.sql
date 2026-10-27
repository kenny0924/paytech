-- Platform 块 139 执行前只读核对，只在 mor_platform 执行。由 gen_block139.py 生成。
-- 期望：第 1 段本文件 ID 区间各表 0；第 2 段新 code 0；第 3 段新路径 0；第 4 段既有依赖每行 1。
SELECT 't_permission' AS t, COUNT(*) AS n FROM t_permission WHERE permission_id BETWEEN 900000000000139101 AND 900000000000139101
UNION ALL SELECT 't_role_permission' AS t, COUNT(*) AS n FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000139201 AND 900000000000139201
UNION ALL SELECT 't_console_resource' AS t, COUNT(*) AS n FROM t_console_resource WHERE resource_id BETWEEN 900000000000139301 AND 900000000000139301
UNION ALL SELECT 't_gateway_route' AS t, COUNT(*) AS n FROM t_gateway_route WHERE route_id BETWEEN 900000000000139401 AND 900000000000139405
UNION ALL SELECT 't_gateway_route_header' AS t, COUNT(*) AS n FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000139501 AND 900000000000139510
UNION ALL SELECT 't_gateway_route_query_param' AS t, COUNT(*) AS n FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000139601 AND 900000000000139611
UNION ALL SELECT 't_gateway_guard_rule' AS t, COUNT(*) AS n FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000139901 AND 900000000000139930;
SELECT 'new_permission_codes' AS t, COUNT(*) AS n FROM t_permission WHERE permission_code IN ('REFERENCE_DATA_READ')
UNION ALL SELECT 'new_resource_codes', COUNT(*) FROM t_console_resource WHERE resource_code IN ('OPERATIONS_REFERENCE_DATA')
UNION ALL SELECT 'new_route_codes', COUNT(*) FROM t_gateway_route WHERE route_code IN ('operations-reference-data-countries', 'operations-reference-data-subdivisions', 'operations-reference-data-industries', 'operations-reference-data-mccs', 'client-onboarding-mcc-search');
SELECT 'new_paths' AS t, COUNT(*) AS n FROM t_gateway_route WHERE (http_method, external_path_template) IN (('GET', '/api/operations/reference-data-admin/v1/countries'), ('GET', '/api/operations/reference-data-admin/v1/country-subdivisions'), ('GET', '/api/operations/reference-data-admin/v1/industries'), ('GET', '/api/operations/reference-data-admin/v1/mccs'), ('GET', '/api/operations/client-onboarding-admin/v1/mccs'));
SELECT 'dep:@b139_perm_client_onboarding_classification_edit' AS t, COUNT(*) AS n FROM (SELECT permission_id FROM t_permission WHERE permission_code = 'CLIENT_ONBOARDING_CLASSIFICATION_EDIT') x
UNION ALL SELECT 'dep:PLATFORM_ADMIN role' AS t, COUNT(*) AS n FROM t_role WHERE role_id = 900000000000000510;
