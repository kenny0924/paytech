-- 只读后核对（mor_platform）。注册后：各表行数 2/2/2/5/18/5/30；权限 ACTIVE；激活前授权 REVOKED、资源与路由 DISABLED，激活后 ACTIVE。
-- 写入规则建立后：(operations-console-bff, PUBLIC) 指向 S3_TEST / mor-test-platform-public-k7q2、PUBLIC_DOCUMENT、900 秒。
SELECT 'permission', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM t_permission WHERE permission_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'role_permission', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'console_resource', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM t_console_resource WHERE resource_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'gateway_route', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM t_gateway_route WHERE route_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'route_header', COUNT(*), NULL FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'route_query_param', COUNT(*), NULL FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'guard_rule', COUNT(*), NULL FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000136001 AND 900000000000136999;
SELECT 'role', role_id, version FROM t_role WHERE role_id = 900000000000000510;
SELECT 'routes_active', COUNT(*) FROM t_gateway_route WHERE status = 'ACTIVE';
SELECT 'route_ops_public', r.source_system, r.access_level, i.instance_code, b.bucket_name, r.scan_policy_code, r.max_upload_seconds
  FROM t_file_storage_route r JOIN t_file_storage_bucket b ON b.bucket_id = r.bucket_id JOIN t_file_storage_instance i ON i.instance_id = b.instance_id
 WHERE r.source_system = 'operations-console-bff';
