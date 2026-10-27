-- Platform 块 138 执行前只读核对，只在 mor_platform 执行。由 gen_block138.py 生成。
-- 期望：第 1 段本文件 ID 区间各表 0；第 2 段新 code 0；第 3 段新路径 0；第 4 段既有依赖每行 1；第 5 段 c03 为 ACTIVE（激活时将停用）。
SELECT 't_gateway_route' AS t, COUNT(*) AS n FROM t_gateway_route WHERE route_id BETWEEN 900000000000138441 AND 900000000000138445
UNION ALL SELECT 't_gateway_route_header' AS t, COUNT(*) AS n FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000138581 AND 900000000000138593
UNION ALL SELECT 't_gateway_route_query_param' AS t, COUNT(*) AS n FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000138651 AND 900000000000138654
UNION ALL SELECT 't_gateway_guard_rule' AS t, COUNT(*) AS n FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000138901 AND 900000000000138930;
SELECT 'new_permission_codes' AS t, COUNT(*) AS n FROM t_permission WHERE permission_code IN (NULL)
UNION ALL SELECT 'new_resource_codes', COUNT(*) FROM t_console_resource WHERE resource_code IN (NULL)
UNION ALL SELECT 'new_route_codes', COUNT(*) FROM t_gateway_route WHERE route_code IN ('client-onboarding-c17', 'client-onboarding-c18', 'client-onboarding-c19', 'client-onboarding-c20', 'client-onboarding-c21');
SELECT 'new_paths' AS t, COUNT(*) AS n FROM t_gateway_route WHERE (http_method, external_path_template) IN (('GET', '/api/client/onboarding/v1/jurisdictions'), ('GET', '/api/client/onboarding/v1/form-schema'), ('POST', '/api/client/onboarding/v1/applications/{applicationId}/declarations'), ('GET', '/api/client/onboarding/v1/applications/{applicationId}/requirements'), ('POST', '/api/client/onboarding/v1/applications/{applicationId}/withdrawal'));
SELECT 'dep:@b138_client_target' AS t, COUNT(*) AS n FROM (SELECT target_id FROM t_gateway_target WHERE target_service_code = 'client-console-bff' AND channel = 'CLIENT') x
UNION ALL SELECT 'dep:PLATFORM_ADMIN role' AS t, COUNT(*) AS n FROM t_role WHERE role_id = 900000000000000510;
