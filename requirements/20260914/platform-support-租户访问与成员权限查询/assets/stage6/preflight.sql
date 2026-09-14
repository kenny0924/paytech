-- Read-only test preflight. No initialization, activation, locks or writes.
SELECT 'database',DATABASE(),@@hostname,@@port;
SELECT 'operator',a.account_id,a.realm,a.status,r.role_id,r.version
  FROM t_user_account a JOIN t_tenant_member m ON m.account_id=a.account_id
  JOIN t_access_tenant t ON t.tenant_id=m.tenant_id
  JOIN t_tenant_member_role g ON g.tenant_member_id=m.member_id AND g.tenant_id=m.tenant_id
  JOIN t_role r ON r.role_id=g.role_id AND g.role_scope_key=r.scope_key
  WHERE a.account_id=221546852372267008 AND a.realm='OPERATIONS' AND a.status='ACTIVE'
    AND a.account_type='HUMAN' AND m.status='ACTIVE' AND g.status='ACTIVE' AND r.role_code='PLATFORM_ADMIN'
    AND t.tenant_type='INTERNAL' AND t.owner_ref_id=1 AND t.owner_status='ACTIVE' AND t.security_status='ACTIVE'
    AND r.tenant_scope='PLATFORM' AND r.tenant_id IS NULL AND r.scope_key='PLATFORM' AND r.system_role=1 AND r.status='ACTIVE'
    AND g.effective_from<=UTC_TIMESTAMP(6)
    AND (g.effective_to IS NULL OR g.effective_to>UTC_TIMESTAMP(6));
SELECT 'target',target_id,target_service_code,channel,status,version FROM t_gateway_target
 WHERE target_service_code='operations-console-bff' AND channel='OPERATIONS';
SELECT 'role',role_id,role_code,status,version FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM';
SELECT 'route_collisions',COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000061001 AND 900000000000061008
 OR route_code LIKE 'operations-tenant-access-%' OR external_path_template LIKE '/api/operations/tenant-access-admin/v1/%';
SELECT 'permission_collisions',COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000061101 AND 900000000000061103
 OR permission_code IN ('TENANT_ACCESS_READ','TENANT_ACCESS_MANAGE','TENANT_MEMBER_READ')
 OR (resource_type='ACCESS_TENANT' AND action_code IN ('READ','MANAGE')) OR (resource_type='TENANT_MEMBER' AND action_code='READ');
SELECT 'grant_collisions',COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000061201 AND 900000000000061203
 OR permission_id BETWEEN 900000000000061101 AND 900000000000061103;
SELECT 'resource_collisions',COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000061301 AND 900000000000061303
 OR resource_code IN ('OPERATIONS_TENANT_ACCESS','OPERATIONS_TENANT_ACCESS_MANAGE','OPERATIONS_TENANT_MEMBER_READ')
 OR frontend_route='/tenant-access' OR component_key='OPERATIONS_TENANT_ACCESS';
SELECT 'header_collisions',COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000062001 AND 900000000000062020;
SELECT 'query_collisions',COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000063001 AND 900000000000063020;
SELECT 'guard_collisions',COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000064001 AND 900000000000064048;
SELECT 'active_routes',COUNT(*) FROM t_gateway_route WHERE status='ACTIVE';
SELECT 'customer_tenants',COUNT(*) FROM t_access_tenant WHERE tenant_type IN ('CLIENT','PARTNER');
SELECT 'customer_members',COUNT(*) FROM t_tenant_member m JOIN t_access_tenant t ON t.tenant_id=m.tenant_id WHERE t.tenant_type IN ('CLIENT','PARTNER');
SELECT 'iam_schema',COUNT(DISTINCT table_name),COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('t_access_tenant','t_user_account','t_tenant_member','t_tenant_member_role','t_role','t_role_permission','t_permission');
SELECT 'catalog','t_permission',COUNT(*) FROM t_permission;
SELECT 'catalog','t_role_permission',COUNT(*) FROM t_role_permission;
SELECT 'catalog','t_console_resource',COUNT(*) FROM t_console_resource;
SELECT 'catalog','t_gateway_route',COUNT(*) FROM t_gateway_route;
SELECT 'catalog','t_gateway_route_header',COUNT(*) FROM t_gateway_route_header;
SELECT 'catalog','t_gateway_route_query_param',COUNT(*) FROM t_gateway_route_query_param;
SELECT 'catalog','t_gateway_guard_rule',COUNT(*) FROM t_gateway_guard_rule;
