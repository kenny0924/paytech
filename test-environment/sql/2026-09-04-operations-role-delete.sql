-- 一次性手工增量：Operations 角色删除的独立权限与公开 Route。
-- 前置：现有 17 条 Operations Route、五项独立写权限已发布；新增 ID 均未占用。
-- 不重放初始化，不重建 Bootstrap，不给只读角色或已有自定义角色补授删除权限。
SET NAMES utf8mb4;
START TRANSACTION;
SET @platform_initial_time = UTC_TIMESTAMP(6);

SELECT tenant_id FROM t_access_tenant WHERE tenant_type='INTERNAL' AND owner_ref_id=1 FOR UPDATE;

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
    (900000000000000531, 'OPERATIONS_ROLE_DELETE', 'ROLE', 'DELETE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time) VALUES
    (900000000000000575, 900000000000000510, 900000000000000531, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

-- 授权变化推进管理员角色版本，使已打开的角色详情及时发现并发变化。
UPDATE t_role SET version=version+1,update_time=@platform_initial_time
WHERE role_id=900000000000000510 AND status='ACTIVE';

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time) VALUES
    (900000000000000559, 'OPERATIONS_ROLE_DELETE', 'OPERATIONS', 'BUTTON', 900000000000000556, '删除角色', NULL, NULL, 900000000000000531, 30, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
    (900000000000001014, 'operations-role-delete', 'OPERATIONS', 'DELETE', '/api/operations/iam/v1/roles/{roleId}', '/iam/v1/roles/{roleId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000531, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
    (900000000000005020, 900000000000001014, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005021, 900000000000001014, 'origin', 'FORWARD_REQUEST'),
    (900000000000005022, 900000000000001014, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005023, 900000000000001014, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005024, 900000000000001014, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005025, 900000000000001014, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
    (900000000000006030, 900000000000001014, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006031, 900000000000001014, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006032, 900000000000001014, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006033, 900000000000001014, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006034, 900000000000001014, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006035, 900000000000001014, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

SELECT route_code,http_method,external_path_template,permission_id FROM t_gateway_route WHERE route_id=900000000000001014;
