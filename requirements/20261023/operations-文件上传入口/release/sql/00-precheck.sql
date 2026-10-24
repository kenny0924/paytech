-- 只读预检（阶段 7 第 ① 步，mor_platform）：块 136 的 ID 段、权限码、资源码、路由与菜单排序未被占用；角色 510 与 ops-bff Target 存在；
-- 公开写入规则的目标（S3_TEST / mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT）可用，(operations-console-bff, PUBLIC) 规则尚不存在。
-- 期望：前 9 行计数全部为 0；role 一行（记下 version 供回滚核对）；target ACTIVE；bucket PUBLIC ACTIVE；policy 一行 ACTIVE；route_ops_public 0。
SELECT 'permission_ids_or_codes', COUNT(*) FROM t_permission
 WHERE permission_id BETWEEN 900000000000136001 AND 900000000000136999 OR permission_code IN ('FILE_UPLOAD_READ','FILE_UPLOAD_CREATE')
    OR resource_type = 'FILE_UPLOAD'
UNION ALL SELECT 'role_permission_ids', COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'console_resource_ids_or_codes', COUNT(*) FROM t_console_resource
 WHERE resource_id BETWEEN 900000000000136001 AND 900000000000136999
    OR resource_code IN ('OPERATIONS_FILE_UPLOAD','OPERATIONS_FILE_UPLOAD_CREATE') OR (channel = 'OPERATIONS' AND frontend_route = '/file-upload')
UNION ALL SELECT 'menu_sort_86_under_562', COUNT(*) FROM t_console_resource WHERE parent_resource_id = 900000000000000562 AND sort_order = 86
UNION ALL SELECT 'gateway_route_ids_codes_paths', COUNT(*) FROM t_gateway_route
 WHERE route_id BETWEEN 900000000000136001 AND 900000000000136999 OR route_code LIKE 'operations-file-upload-%'
    OR external_path_template LIKE '/api/operations/file-upload/%'
UNION ALL SELECT 'route_header_ids', COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'route_query_param_ids', COUNT(*) FROM t_gateway_route_query_param
 WHERE route_query_param_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'guard_rule_ids', COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000136001 AND 900000000000136999
UNION ALL SELECT 'route_ops_public', COUNT(*) FROM t_file_storage_route WHERE source_system = 'operations-console-bff' AND access_level = 'PUBLIC';
SELECT 'role', role_id, role_code, version FROM t_role WHERE role_id = 900000000000000510;
SELECT 'target', target_id, target_service_code, status FROM t_gateway_target WHERE target_id = 900000000000000001;
SELECT 'menu_parent', resource_id, resource_code, status FROM t_console_resource WHERE resource_id = 900000000000000562;
SELECT 'bucket', b.bucket_id, i.instance_code, b.bucket_name, b.access_level, b.status, i.status
  FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id = b.instance_id
 WHERE i.instance_code = 'S3_TEST' AND b.bucket_name = 'mor-test-platform-public-k7q2';
SELECT 'policy', policy_code, revision_no, status, max_size_bytes FROM t_file_scan_policy
 WHERE policy_code = 'PUBLIC_DOCUMENT' AND status = 'ACTIVE';
SELECT 'routes_active', COUNT(*) FROM t_gateway_route WHERE status = 'ACTIVE';
