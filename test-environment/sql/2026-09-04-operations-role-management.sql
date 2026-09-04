-- Operations 自定义角色：一次性手工目录增量。只增加权限、管理员授权、按钮与四条精确 Route。

-- 应用前确认当前 mor-test 为此前 13 条 Operations Route；不重放初始化、不重建 Bootstrap。

SET NAMES utf8mb4;

START TRANSACTION;

SET @platform_initial_time = UTC_TIMESTAMP(6);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
    (900000000000000525, 'OPERATIONS_ROLE_MANAGE', 'ROLE', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time) VALUES
    (900000000000000539, 900000000000000510, 900000000000000525, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time) VALUES
    (900000000000000557, 'OPERATIONS_ROLE_CREATE', 'OPERATIONS', 'BUTTON', 900000000000000556, '创建角色', NULL, NULL, 900000000000000525, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000558, 'OPERATIONS_ROLE_EDIT', 'OPERATIONS', 'BUTTON', 900000000000000556, '编辑角色', NULL, NULL, 900000000000000525, 20, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route (
    route_id,
    route_code,
    channel,
    http_method,
    external_path_template,
    downstream_path_template,
    target_id,
    security_profile_code,
    authorization_mode,
    permission_id,
    required_scope_code,
    request_cookie_mode,
    response_cookie_mode,
    body_limit_bytes,
    body_receive_timeout_millis,
    response_timeout_millis,
    status,
    version,
    created_by,
    updated_by,
    create_time,
    update_time
) VALUES
    (900000000000001010, 'operations-role-permission-options', 'OPERATIONS', 'GET', '/api/operations/iam/v1/role-permission-options', '/iam/v1/role-permission-options', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000525, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001011, 'operations-role-detail', 'OPERATIONS', 'GET', '/api/operations/iam/v1/roles/{roleId}', '/iam/v1/roles/{roleId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000525, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001012, 'operations-role-create', 'OPERATIONS', 'POST', '/api/operations/iam/v1/roles', '/iam/v1/roles', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000525, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001013, 'operations-role-update', 'OPERATIONS', 'PUT', '/api/operations/iam/v1/roles/{roleId}', '/iam/v1/roles/{roleId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000525, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (
    route_header_id,
    route_id,
    header_name,
    header_use
) VALUES
    (900000000000005000, 900000000000001010, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005001, 900000000000001010, 'origin', 'FORWARD_REQUEST'),
    (900000000000005002, 900000000000001011, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005003, 900000000000001011, 'origin', 'FORWARD_REQUEST'),
    (900000000000005004, 900000000000001012, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005005, 900000000000001012, 'origin', 'FORWARD_REQUEST'),
    (900000000000005006, 900000000000001012, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005007, 900000000000001012, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005008, 900000000000001012, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005009, 900000000000001012, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005010, 900000000000001013, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005011, 900000000000001013, 'origin', 'FORWARD_REQUEST'),
    (900000000000005012, 900000000000001013, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005013, 900000000000001013, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005014, 900000000000001013, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005015, 900000000000001013, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (
    guard_rule_id,
    route_id,
    rule_type,
    dimension,
    capacity,
    refill_tokens,
    refill_period_millis,
    concurrency_limit
) VALUES
    (900000000000006000, 900000000000001010, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006001, 900000000000001010, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006002, 900000000000001010, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006003, 900000000000001010, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006004, 900000000000001010, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006005, 900000000000001010, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006006, 900000000000001011, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006007, 900000000000001011, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006008, 900000000000001011, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006009, 900000000000001011, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006010, 900000000000001011, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006011, 900000000000001011, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006012, 900000000000001012, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006013, 900000000000001012, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006014, 900000000000001012, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006015, 900000000000001012, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006016, 900000000000001012, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006017, 900000000000001012, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006018, 900000000000001013, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006019, 900000000000001013, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006020, 900000000000001013, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006021, 900000000000001013, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006022, 900000000000001013, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006023, 900000000000001013, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

SELECT route_code, http_method, external_path_template, permission_id FROM t_gateway_route WHERE route_id BETWEEN 900000000000001010 AND 900000000000001013 ORDER BY route_id;
