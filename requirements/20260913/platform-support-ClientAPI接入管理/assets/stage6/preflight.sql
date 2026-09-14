SELECT DATABASE(),@@hostname,@@port;
SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('t_api_credential','t_api_credential_scope','t_api_scope');
SELECT role_id,role_code,version,status FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM';
SELECT route_id,route_code,version,status FROM t_gateway_route WHERE route_code IN ('operations-gateway-route-list','operations-audit-list');
SELECT resource_id,resource_code,status FROM t_console_resource WHERE resource_code='OPERATIONS_PLATFORM_CONFIGURATION';
SELECT target_id,target_service_code,channel,status FROM t_gateway_target WHERE target_service_code='operations-console-bff';
SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE';
SELECT COUNT(*) FROM t_access_tenant WHERE tenant_type='CLIENT';
