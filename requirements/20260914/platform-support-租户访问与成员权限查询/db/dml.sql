-- Stage 3 review draft; Platform-owned database only. Manual application requires separate approval.
-- 105 INSERT rows; 0 UPDATE/DELETE. No tenant/account/member/credential seed.
-- New routes/resources DISABLED; PLATFORM_ADMIN grants REVOKED. Activation is separate.
-- BEFORE sourcing: set @plt_tenant_operator to the approved real Operations account ID.
-- Follow README preflight in ONE interactive connection; no --force. No COMMIT here.
SET @plt_tenant_time=UTC_TIMESTAMP(6);
SET @plt_tenant_target=(SELECT target_id FROM t_gateway_target WHERE target_service_code='operations-console-bff' AND channel='OPERATIONS' AND status='ACTIVE');
SET @plt_tenant_admin=(SELECT role_id FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE');
SELECT @plt_tenant_operator operator_id,@plt_tenant_target target_id,@plt_tenant_admin admin_role_id;
-- Stop before START unless all preflight checks pass.
START TRANSACTION;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000061101, 'TENANT_ACCESS_READ', 'ACCESS_TENANT', 'READ', 'LOW', 'ACTIVE', 1, @plt_tenant_time, @plt_tenant_time),
  (900000000000061102, 'TENANT_ACCESS_MANAGE', 'ACCESS_TENANT', 'MANAGE', 'HIGH', 'ACTIVE', 1, @plt_tenant_time, @plt_tenant_time),
  (900000000000061103, 'TENANT_MEMBER_READ', 'TENANT_MEMBER', 'READ', 'LOW', 'ACTIVE', 1, @plt_tenant_time, @plt_tenant_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000061201, @plt_tenant_admin, 900000000000061101, 'REVOKED', 1, @plt_tenant_time, @plt_tenant_time),
  (900000000000061202, @plt_tenant_admin, 900000000000061102, 'REVOKED', 1, @plt_tenant_time, @plt_tenant_time),
  (900000000000061203, @plt_tenant_admin, 900000000000061103, 'REVOKED', 1, @plt_tenant_time, @plt_tenant_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000061301, 'OPERATIONS_TENANT_ACCESS', 'OPERATIONS', 'MENU', NULL, '租户访问', '/tenant-access', 'OPERATIONS_TENANT_ACCESS', 900000000000061101, 25, 'DISABLED', 1, @plt_tenant_time, @plt_tenant_time),
  (900000000000061302, 'OPERATIONS_TENANT_ACCESS_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000061301, '安全暂停与恢复', NULL, NULL, 900000000000061102, 10, 'DISABLED', 1, @plt_tenant_time, @plt_tenant_time),
  (900000000000061303, 'OPERATIONS_TENANT_MEMBER_READ', 'OPERATIONS', 'BUTTON', 900000000000061301, '查看成员权限', NULL, NULL, 900000000000061103, 20, 'DISABLED', 1, @plt_tenant_time, @plt_tenant_time);

INSERT INTO t_gateway_route (route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000061001, 'operations-tenant-access-list', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants', '/tenant-access-admin/v1/tenants', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061002, 'operations-tenant-access-detail', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}', '/tenant-access-admin/v1/tenants/{tenantId}', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061003, 'operations-tenant-access-security-status', 'OPERATIONS', 'POST', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/security-status', '/tenant-access-admin/v1/tenants/{tenantId}/security-status', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061102, NULL, 'DROP', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061004, 'operations-tenant-access-members', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members', '/tenant-access-admin/v1/tenants/{tenantId}/members', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061005, 'operations-tenant-access-member-detail', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061006, 'operations-tenant-access-member-roles', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/roles', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/roles', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061007, 'operations-tenant-access-member-permissions', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time),
  (900000000000061008, 'operations-tenant-access-permission-sources', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions/{permissionId}/sources', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions/{permissionId}/sources', @plt_tenant_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_tenant_operator, @plt_tenant_operator, @plt_tenant_time, @plt_tenant_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000062001, 900000000000061001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062002, 900000000000061001, 'origin', 'FORWARD_REQUEST'),
  (900000000000062003, 900000000000061002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062004, 900000000000061002, 'origin', 'FORWARD_REQUEST'),
  (900000000000062005, 900000000000061003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062006, 900000000000061003, 'origin', 'FORWARD_REQUEST'),
  (900000000000062007, 900000000000061003, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000062008, 900000000000061003, 'content-type', 'FORWARD_REQUEST'),
  (900000000000062009, 900000000000061003, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000062010, 900000000000061003, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000062011, 900000000000061004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062012, 900000000000061004, 'origin', 'FORWARD_REQUEST'),
  (900000000000062013, 900000000000061005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062014, 900000000000061005, 'origin', 'FORWARD_REQUEST'),
  (900000000000062015, 900000000000061006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062016, 900000000000061006, 'origin', 'FORWARD_REQUEST'),
  (900000000000062017, 900000000000061007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062018, 900000000000061007, 'origin', 'FORWARD_REQUEST'),
  (900000000000062019, 900000000000061008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000062020, 900000000000061008, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000063001, 900000000000061001, 'page'),
  (900000000000063002, 900000000000061001, 'size'),
  (900000000000063003, 900000000000061001, 'tenantType'),
  (900000000000063004, 900000000000061001, 'tenantId'),
  (900000000000063005, 900000000000061001, 'ownerRefId'),
  (900000000000063006, 900000000000061001, 'tenantName'),
  (900000000000063007, 900000000000061001, 'ownerStatus'),
  (900000000000063008, 900000000000061001, 'securityStatus'),
  (900000000000063009, 900000000000061004, 'page'),
  (900000000000063010, 900000000000061004, 'size'),
  (900000000000063011, 900000000000061004, 'accountId'),
  (900000000000063012, 900000000000061004, 'emailMasked'),
  (900000000000063013, 900000000000061004, 'memberType'),
  (900000000000063014, 900000000000061004, 'memberStatus'),
  (900000000000063015, 900000000000061006, 'page'),
  (900000000000063016, 900000000000061006, 'size'),
  (900000000000063017, 900000000000061007, 'page'),
  (900000000000063018, 900000000000061007, 'size'),
  (900000000000063019, 900000000000061008, 'page'),
  (900000000000063020, 900000000000061008, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000064001, 900000000000061001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064002, 900000000000061001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064003, 900000000000061001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064004, 900000000000061001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064005, 900000000000061001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064006, 900000000000061001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064007, 900000000000061002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064008, 900000000000061002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064009, 900000000000061002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064010, 900000000000061002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064011, 900000000000061002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064012, 900000000000061002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064013, 900000000000061003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064014, 900000000000061003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064015, 900000000000061003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064016, 900000000000061003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064017, 900000000000061003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064018, 900000000000061003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064019, 900000000000061004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064020, 900000000000061004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064021, 900000000000061004, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064022, 900000000000061004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064023, 900000000000061004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064024, 900000000000061004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064025, 900000000000061005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064026, 900000000000061005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064027, 900000000000061005, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064028, 900000000000061005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064029, 900000000000061005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064030, 900000000000061005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064031, 900000000000061006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064032, 900000000000061006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064033, 900000000000061006, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064034, 900000000000061006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064035, 900000000000061006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064036, 900000000000061006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064037, 900000000000061007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064038, 900000000000061007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064039, 900000000000061007, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064040, 900000000000061007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064041, 900000000000061007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064042, 900000000000061007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000064043, 900000000000061008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000064044, 900000000000061008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000064045, 900000000000061008, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000064046, 900000000000061008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000064047, 900000000000061008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000064048, 900000000000061008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

-- Inspect exact inserted IDs/counts before manual COMMIT; on any mismatch ROLLBACK.
