-- Platform Support Operations catalog: apply ONCE after manual schema review.
-- No password, Account or fake ACTIVE key is supplied. See the explicit operator block below.

SET NAMES utf8mb4;

START TRANSACTION;

SET @platform_initial_time = UTC_TIMESTAMP(6);
-- Reserved operator-owned audit actor for this one-time manual initialization.
-- It is not a login Account; later management commands write the authenticated Account ID.
SET @platform_initial_actor_id = 1;
SET @operations_target_id = 900000000000000001;
SET @operations_login_route_id = 900000000000000101;
SET @operations_refresh_route_id = 900000000000000102;
SET @operations_logout_route_id = 900000000000000103;

INSERT INTO t_gateway_target (
    target_id,
    target_service_code,
    discovery_service_name,
    channel,
    status,
    version,
    created_by,
    updated_by,
    create_time,
    update_time
) VALUES (
    @operations_target_id,
    'operations-console-bff',
    'operations-console-bff',
    'OPERATIONS',
    'ACTIVE',
    1,
    @platform_initial_actor_id,
    @platform_initial_actor_id,
    @platform_initial_time,
    @platform_initial_time
);

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
    idempotency_mode,
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
    (
        @operations_login_route_id,
        'operations-auth-login',
        'OPERATIONS',
        'POST',
        '/api/operations/auth/v1/login',
        '/auth/v1/login',
        @operations_target_id,
        'PUBLIC',
        'PUBLIC',
        NULL,
        NULL,
        'DROP',
        'NONE',
        'NONE',
        16384,
        5000,
        10000,
        'ACTIVE',
        1,
        @platform_initial_actor_id,
        @platform_initial_actor_id,
        @platform_initial_time,
        @platform_initial_time
    ),
    (
        @operations_refresh_route_id,
        'operations-auth-refresh',
        'OPERATIONS',
        'POST',
        '/api/operations/auth/v1/refresh',
        '/auth/v1/refresh',
        @operations_target_id,
        'PUBLIC',
        'PUBLIC',
        NULL,
        NULL,
        'DROP',
        'NONE',
        'NONE',
        16384,
        5000,
        10000,
        'ACTIVE',
        1,
        @platform_initial_actor_id,
        @platform_initial_actor_id,
        @platform_initial_time,
        @platform_initial_time
    ),
    (
        @operations_logout_route_id,
        'operations-auth-logout',
        'OPERATIONS',
        'POST',
        '/api/operations/auth/v1/logout',
        '/auth/v1/logout',
        @operations_target_id,
        'OPERATIONS_JWT',
        'HUMAN_AUTHENTICATED',
        NULL,
        NULL,
        'DROP',
        'NONE',
        'NONE',
        16384,
        5000,
        10000,
        'ACTIVE',
        1,
        @platform_initial_actor_id,
        @platform_initial_actor_id,
        @platform_initial_time,
        @platform_initial_time
    );

INSERT INTO t_gateway_route_header (
    route_header_id,
    route_id,
    header_name,
    header_use
) VALUES
    (900000000000000201, @operations_login_route_id, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000000202, @operations_login_route_id, 'origin', 'FORWARD_REQUEST'),
    (900000000000000203, @operations_login_route_id, 'content-type', 'FORWARD_REQUEST'),
    (900000000000000204, @operations_refresh_route_id, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000000206, @operations_refresh_route_id, 'origin', 'FORWARD_REQUEST'),
    (900000000000000207, @operations_refresh_route_id, 'content-type', 'FORWARD_REQUEST'),
    (900000000000000209, @operations_logout_route_id, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000000210, @operations_logout_route_id, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000000212, @operations_logout_route_id, 'origin', 'FORWARD_REQUEST'),
    (900000000000000213, @operations_logout_route_id, 'content-type', 'FORWARD_REQUEST');

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
    (900000000000000301, @operations_login_route_id, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000000302, @operations_login_route_id, 'RATE_LIMIT', 'CLIENT_IP', 5, 5, 60000, NULL),
    (900000000000000303, @operations_login_route_id, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 20),
    (900000000000000304, @operations_login_route_id, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 2),
    (900000000000000305, @operations_refresh_route_id, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
    (900000000000000306, @operations_refresh_route_id, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000000307, @operations_refresh_route_id, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000000308, @operations_refresh_route_id, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000000309, @operations_logout_route_id, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000000310, @operations_logout_route_id, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000000311, @operations_logout_route_id, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000000312, @operations_logout_route_id, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000000313, @operations_logout_route_id, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000000314, @operations_logout_route_id, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);


INSERT INTO t_access_tenant (tenant_id, tenant_type, owner_ref_id, tenant_name, owner_status, security_status, version, create_time, update_time) VALUES
    (900000000000000500, 'INTERNAL', 1, 'Operations', 'ACTIVE', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role (role_id, tenant_scope, tenant_id, scope_key, role_code, role_name, status, system_role, version, create_time, update_time) VALUES
    (900000000000000510, 'PLATFORM', NULL, 'PLATFORM', 'PLATFORM_ADMIN', '平台管理员', 'ACTIVE', 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000000511, 'PLATFORM', NULL, 'PLATFORM', 'OPERATIONS_VIEWER', '运营只读人员', 'ACTIVE', 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
    (900000000000000520, 'OPERATIONS_ACCOUNT_READ', 'USER_ACCOUNT', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000522, 'OPERATIONS_ROLE_READ', 'ROLE', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000523, 'OPERATIONS_AUDIT_READ', 'AUDIT_EVENT', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000524, 'OPERATIONS_SYSTEM_READ', 'OPERATIONS_SYSTEM', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000526, 'OPERATIONS_ACCOUNT_CREATE', 'USER_ACCOUNT', 'CREATE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000527, 'OPERATIONS_ACCOUNT_DISABLE', 'USER_ACCOUNT', 'DISABLE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000528, 'OPERATIONS_ACCOUNT_ASSIGN_ROLES', 'USER_ACCOUNT', 'ASSIGN_ROLES', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000529, 'OPERATIONS_ROLE_CREATE', 'ROLE', 'CREATE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000530, 'OPERATIONS_ROLE_UPDATE', 'ROLE', 'UPDATE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000531, 'OPERATIONS_ROLE_DELETE', 'ROLE', 'DELETE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000532, 'OPERATIONS_ACCOUNT_DELETE', 'USER_ACCOUNT', 'DELETE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000533, 'OPERATIONS_ACCOUNT_ENABLE', 'USER_ACCOUNT', 'ENABLE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000534, 'GATEWAY_CONFIG_READ', 'GATEWAY_CONFIGURATION', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000535, 'GATEWAY_CONFIG_MANAGE', 'GATEWAY_CONFIGURATION', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000536, 'RISK_DECISION_CONFIG_READ', 'RISK_DECISION_CONFIGURATION', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000537, 'RISK_RULE_SET_SAVE', 'RISK_DECISION_CONFIGURATION', 'SAVE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000538, 'RISK_RULE_SET_FREEZE', 'RISK_DECISION_CONFIGURATION', 'FREEZE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000539, 'RISK_TEST_CANDIDATE_CREATE', 'RISK_DECISION_CONFIGURATION', 'CREATE_TEST_CANDIDATE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000540, 'RISK_TEST_CANDIDATE_VALIDATE', 'RISK_DECISION_CONFIGURATION', 'VALIDATE_TEST_CANDIDATE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time) VALUES
    (900000000000000530, 900000000000000510, 900000000000000520, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000532, 900000000000000510, 900000000000000522, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000533, 900000000000000510, 900000000000000523, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000534, 900000000000000511, 900000000000000520, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000535, 900000000000000511, 900000000000000522, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000536, 900000000000000511, 900000000000000523, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000537, 900000000000000510, 900000000000000524, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000538, 900000000000000511, 900000000000000524, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000570, 900000000000000510, 900000000000000526, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000571, 900000000000000510, 900000000000000527, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000572, 900000000000000510, 900000000000000528, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000573, 900000000000000510, 900000000000000529, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000574, 900000000000000510, 900000000000000530, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000575, 900000000000000510, 900000000000000531, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000576, 900000000000000510, 900000000000000532, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000577, 900000000000000510, 900000000000000533, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000578, 900000000000000510, 900000000000000534, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000579, 900000000000000510, 900000000000000535, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000580, 900000000000000511, 900000000000000534, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000581, 900000000000000510, 900000000000000536, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000582, 900000000000000510, 900000000000000537, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000583, 900000000000000510, 900000000000000538, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000584, 900000000000000510, 900000000000000539, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000585, 900000000000000510, 900000000000000540, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000586, 900000000000000511, 900000000000000536, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time) VALUES
    (900000000000000555, 'OPERATIONS_SYSTEM', 'OPERATIONS', 'MENU', NULL, '系统管理', NULL, NULL, 900000000000000524, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000550, 'OPERATIONS_ACCOUNTS', 'OPERATIONS', 'MENU', 900000000000000555, '人员管理', '/accounts', 'OPERATIONS_ACCOUNTS', 900000000000000520, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000556, 'OPERATIONS_ROLES', 'OPERATIONS', 'MENU', 900000000000000555, '角色管理', '/roles', 'OPERATIONS_ROLES', 900000000000000522, 20, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000551, 'OPERATIONS_AUDIT', 'OPERATIONS', 'MENU', 900000000000000555, '审计日志', '/audit', 'OPERATIONS_AUDIT', 900000000000000523, 30, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000552, 'OPERATIONS_ACCOUNT_CREATE', 'OPERATIONS', 'BUTTON', 900000000000000550, '创建人员', NULL, NULL, 900000000000000526, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000553, 'OPERATIONS_ACCOUNT_DISABLE', 'OPERATIONS', 'BUTTON', 900000000000000550, '禁用人员', NULL, NULL, 900000000000000527, 20, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000554, 'OPERATIONS_ACCOUNT_ASSIGN_ROLES', 'OPERATIONS', 'BUTTON', 900000000000000550, '分配角色', NULL, NULL, 900000000000000528, 30, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000557, 'OPERATIONS_ROLE_CREATE', 'OPERATIONS', 'BUTTON', 900000000000000556, '创建角色', NULL, NULL, 900000000000000529, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000558, 'OPERATIONS_ROLE_EDIT', 'OPERATIONS', 'BUTTON', 900000000000000556, '编辑角色', NULL, NULL, 900000000000000530, 20, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000559, 'OPERATIONS_ROLE_DELETE', 'OPERATIONS', 'BUTTON', 900000000000000556, '删除角色', NULL, NULL, 900000000000000531, 30, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000560, 'OPERATIONS_ACCOUNT_DELETE', 'OPERATIONS', 'BUTTON', 900000000000000550, '删除人员', NULL, NULL, 900000000000000532, 40, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000561, 'OPERATIONS_ACCOUNT_ENABLE', 'OPERATIONS', 'BUTTON', 900000000000000550, '启用人员', NULL, NULL, 900000000000000533, 50, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000562, 'OPERATIONS_PLATFORM_CONFIGURATION', 'OPERATIONS', 'MENU', NULL, '平台配置', NULL, NULL, 900000000000000534, 20, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000563, 'OPERATIONS_GATEWAY_CONFIGURATION', 'OPERATIONS', 'MENU', 900000000000000562, 'API 路由管理', '/gateway-configuration', 'OPERATIONS_GATEWAY_CONFIGURATION', 900000000000000534, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000564, 'OPERATIONS_GATEWAY_CONFIGURATION_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000000563, '管理配置', NULL, NULL, 900000000000000535, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000565, 'OPERATIONS_RISK_MANAGEMENT', 'OPERATIONS', 'MENU', NULL, '风控管理', NULL, NULL, 900000000000000536, 30, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000566, 'OPERATIONS_RISK_RULE_CONFIGURATION', 'OPERATIONS', 'MENU', 900000000000000565, '规则配置', '/risk/rule-configuration', 'OPERATIONS_RISK_RULE_CONFIGURATION', 900000000000000536, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000567, 'OPERATIONS_RISK_RULE_SET_SAVE', 'OPERATIONS', 'BUTTON', 900000000000000566, '保存为新版本', NULL, NULL, 900000000000000537, 10, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000568, 'OPERATIONS_RISK_RULE_SET_FREEZE', 'OPERATIONS', 'BUTTON', 900000000000000566, '冻结版本', NULL, NULL, 900000000000000538, 20, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000569, 'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', 'OPERATIONS', 'BUTTON', 900000000000000566, '创建 TEST Candidate', NULL, NULL, 900000000000000539, 30, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
    (900000000000000570, 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE', 'OPERATIONS', 'BUTTON', 900000000000000566, '执行静态校验', NULL, NULL, 900000000000000540, 40, 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
    (900000000000001000, 'operations-auth-context', 'OPERATIONS', 'GET', '/api/operations/auth/v1/context', '/auth/v1/context', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001001, 'operations-auth-password', 'OPERATIONS', 'POST', '/api/operations/auth/v1/password', '/auth/v1/password', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001002, 'operations-bootstrap-administrator', 'OPERATIONS', 'POST', '/api/operations/auth/v1/bootstrap/administrator', '/auth/v1/bootstrap/administrator', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001003, 'operations-navigation', 'OPERATIONS', 'GET', '/api/operations/authorization/v1/navigation', '/authorization/v1/navigation', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001004, 'operations-account-list', 'OPERATIONS', 'GET', '/api/operations/iam/v1/accounts', '/iam/v1/accounts', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000520, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001005, 'operations-account-create', 'OPERATIONS', 'POST', '/api/operations/iam/v1/accounts', '/iam/v1/accounts', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000526, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001006, 'operations-account-disable', 'OPERATIONS', 'POST', '/api/operations/iam/v1/accounts/{accountId}/disable', '/iam/v1/accounts/{accountId}/disable', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000527, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001007, 'operations-account-roles', 'OPERATIONS', 'PUT', '/api/operations/iam/v1/accounts/{accountId}/roles', '/iam/v1/accounts/{accountId}/roles', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000528, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001008, 'operations-role-list', 'OPERATIONS', 'GET', '/api/operations/iam/v1/roles', '/iam/v1/roles', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000522, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001009, 'operations-audit-list', 'OPERATIONS', 'GET', '/api/operations/audit/v1/events', '/audit/v1/events', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000523, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001010, 'operations-role-permission-options', 'OPERATIONS', 'GET', '/api/operations/iam/v1/role-permission-options', '/iam/v1/role-permission-options', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000529, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001011, 'operations-role-detail', 'OPERATIONS', 'GET', '/api/operations/iam/v1/roles/{roleId}', '/iam/v1/roles/{roleId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000530, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001012, 'operations-role-create', 'OPERATIONS', 'POST', '/api/operations/iam/v1/roles', '/iam/v1/roles', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000529, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001013, 'operations-role-update', 'OPERATIONS', 'PUT', '/api/operations/iam/v1/roles/{roleId}', '/iam/v1/roles/{roleId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000530, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001014, 'operations-role-delete', 'OPERATIONS', 'DELETE', '/api/operations/iam/v1/roles/{roleId}', '/iam/v1/roles/{roleId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000531, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001015, 'operations-account-delete', 'OPERATIONS', 'DELETE', '/api/operations/iam/v1/accounts/{accountId}', '/iam/v1/accounts/{accountId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000532, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001016, 'operations-account-enable', 'OPERATIONS', 'POST', '/api/operations/iam/v1/accounts/{accountId}/enable', '/iam/v1/accounts/{accountId}/enable', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000533, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001017, 'operations-audit-detail', 'OPERATIONS', 'GET', '/api/operations/audit/v1/events/{auditEventId}', '/audit/v1/events/{auditEventId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000523, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001018, 'operations-gateway-target-list', 'OPERATIONS', 'GET', '/api/operations/gateway-admin/v1/target-services', '/gateway-admin/v1/target-services', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000534, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001019, 'operations-gateway-target-detail', 'OPERATIONS', 'GET', '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}', '/gateway-admin/v1/target-services/{targetServiceCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000534, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001020, 'operations-gateway-target-status', 'OPERATIONS', 'PATCH', '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}/status', '/gateway-admin/v1/target-services/{targetServiceCode}/status', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000535, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001021, 'operations-gateway-route-list', 'OPERATIONS', 'GET', '/api/operations/gateway-admin/v1/routes', '/gateway-admin/v1/routes', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000534, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001022, 'operations-gateway-route-detail', 'OPERATIONS', 'GET', '/api/operations/gateway-admin/v1/routes/{routeCode}', '/gateway-admin/v1/routes/{routeCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000534, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001023, 'operations-gateway-route-status', 'OPERATIONS', 'PATCH', '/api/operations/gateway-admin/v1/routes/{routeCode}/status', '/gateway-admin/v1/routes/{routeCode}/status', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000535, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
    (900000000000002000, 900000000000001000, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002001, 900000000000001000, 'origin', 'FORWARD_REQUEST'),
    (900000000000002002, 900000000000001001, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002003, 900000000000001001, 'origin', 'FORWARD_REQUEST'),
    (900000000000002004, 900000000000001001, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000002005, 900000000000001001, 'content-type', 'FORWARD_REQUEST'),
    (900000000000002008, 900000000000001002, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002009, 900000000000001002, 'origin', 'FORWARD_REQUEST'),
    (900000000000002010, 900000000000001002, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000002011, 900000000000001002, 'content-type', 'FORWARD_REQUEST'),
    (900000000000002014, 900000000000001003, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002015, 900000000000001003, 'origin', 'FORWARD_REQUEST'),
    (900000000000002016, 900000000000001004, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002017, 900000000000001004, 'origin', 'FORWARD_REQUEST'),
    (900000000000002018, 900000000000001005, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002019, 900000000000001005, 'origin', 'FORWARD_REQUEST'),
    (900000000000002020, 900000000000001005, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000002021, 900000000000001005, 'content-type', 'FORWARD_REQUEST'),
    (900000000000002024, 900000000000001005, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000002025, 900000000000001005, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000002026, 900000000000001006, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002027, 900000000000001006, 'origin', 'FORWARD_REQUEST'),
    (900000000000002028, 900000000000001006, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000002029, 900000000000001006, 'content-type', 'FORWARD_REQUEST'),
    (900000000000002032, 900000000000001006, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000002033, 900000000000001006, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000002034, 900000000000001007, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002035, 900000000000001007, 'origin', 'FORWARD_REQUEST'),
    (900000000000002036, 900000000000001007, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000002037, 900000000000001007, 'content-type', 'FORWARD_REQUEST'),
    (900000000000002040, 900000000000001007, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000002041, 900000000000001007, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000002042, 900000000000001008, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002043, 900000000000001008, 'origin', 'FORWARD_REQUEST'),
    (900000000000002044, 900000000000001009, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000002045, 900000000000001009, 'origin', 'FORWARD_REQUEST'),
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
    (900000000000005015, 900000000000001013, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005020, 900000000000001014, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005021, 900000000000001014, 'origin', 'FORWARD_REQUEST'),
    (900000000000005022, 900000000000001014, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005023, 900000000000001014, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005024, 900000000000001014, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005025, 900000000000001014, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005030, 900000000000001015, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005031, 900000000000001015, 'origin', 'FORWARD_REQUEST'),
    (900000000000005032, 900000000000001015, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005033, 900000000000001015, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005034, 900000000000001015, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005035, 900000000000001015, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005040, 900000000000001016, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005041, 900000000000001016, 'origin', 'FORWARD_REQUEST'),
    (900000000000005042, 900000000000001016, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005043, 900000000000001016, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005044, 900000000000001016, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005045, 900000000000001016, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005050, 900000000000001017, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005051, 900000000000001017, 'origin', 'FORWARD_REQUEST'),
    (900000000000005060, 900000000000001018, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005061, 900000000000001018, 'origin', 'FORWARD_REQUEST'),
    (900000000000005062, 900000000000001019, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005063, 900000000000001019, 'origin', 'FORWARD_REQUEST'),
    (900000000000005064, 900000000000001020, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005065, 900000000000001020, 'origin', 'FORWARD_REQUEST'),
    (900000000000005066, 900000000000001020, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005067, 900000000000001020, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005068, 900000000000001020, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005069, 900000000000001020, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005070, 900000000000001021, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005071, 900000000000001021, 'origin', 'FORWARD_REQUEST'),
    (900000000000005072, 900000000000001022, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005073, 900000000000001022, 'origin', 'FORWARD_REQUEST'),
    (900000000000005074, 900000000000001023, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005075, 900000000000001023, 'origin', 'FORWARD_REQUEST'),
    (900000000000005076, 900000000000001023, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005077, 900000000000001023, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005078, 900000000000001023, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005079, 900000000000001023, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
    (900000000000003000, 900000000000001000, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003001, 900000000000001000, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003002, 900000000000001000, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003003, 900000000000001000, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003004, 900000000000001000, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003005, 900000000000001000, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003006, 900000000000001001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003007, 900000000000001001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003008, 900000000000001001, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003009, 900000000000001001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003010, 900000000000001001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003011, 900000000000001001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003012, 900000000000001002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003013, 900000000000001002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003014, 900000000000001002, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003015, 900000000000001002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003016, 900000000000001002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003017, 900000000000001002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003018, 900000000000001003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003019, 900000000000001003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003020, 900000000000001003, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003021, 900000000000001003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003022, 900000000000001003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003023, 900000000000001003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003024, 900000000000001004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003025, 900000000000001004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003026, 900000000000001004, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003027, 900000000000001004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003028, 900000000000001004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003029, 900000000000001004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003030, 900000000000001005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003031, 900000000000001005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003032, 900000000000001005, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003033, 900000000000001005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003034, 900000000000001005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003035, 900000000000001005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003036, 900000000000001006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003037, 900000000000001006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003038, 900000000000001006, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003039, 900000000000001006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003040, 900000000000001006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003041, 900000000000001006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003042, 900000000000001007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003043, 900000000000001007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003044, 900000000000001007, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003045, 900000000000001007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003046, 900000000000001007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003047, 900000000000001007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003048, 900000000000001008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003049, 900000000000001008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003050, 900000000000001008, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003051, 900000000000001008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003052, 900000000000001008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003053, 900000000000001008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000003054, 900000000000001009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000003055, 900000000000001009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000003056, 900000000000001009, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000003057, 900000000000001009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000003058, 900000000000001009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000003059, 900000000000001009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
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
    (900000000000006023, 900000000000001013, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006030, 900000000000001014, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006031, 900000000000001014, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006032, 900000000000001014, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006033, 900000000000001014, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006034, 900000000000001014, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006035, 900000000000001014, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006040, 900000000000001015, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006041, 900000000000001015, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006042, 900000000000001015, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006043, 900000000000001015, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006044, 900000000000001015, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006045, 900000000000001015, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006050, 900000000000001016, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006051, 900000000000001016, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006052, 900000000000001016, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006053, 900000000000001016, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006054, 900000000000001016, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006055, 900000000000001016, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006060, 900000000000001017, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006061, 900000000000001017, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006062, 900000000000001017, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006063, 900000000000001017, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006064, 900000000000001017, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006065, 900000000000001017, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006070, 900000000000001018, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006071, 900000000000001018, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006072, 900000000000001018, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006073, 900000000000001018, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006074, 900000000000001018, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006075, 900000000000001018, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006076, 900000000000001019, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006077, 900000000000001019, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006078, 900000000000001019, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006079, 900000000000001019, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006080, 900000000000001019, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006081, 900000000000001019, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006082, 900000000000001020, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006083, 900000000000001020, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006084, 900000000000001020, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006085, 900000000000001020, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006086, 900000000000001020, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006087, 900000000000001020, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006088, 900000000000001021, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006089, 900000000000001021, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006090, 900000000000001021, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006091, 900000000000001021, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006092, 900000000000001021, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006093, 900000000000001021, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006094, 900000000000001022, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006095, 900000000000001022, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006096, 900000000000001022, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006097, 900000000000001022, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006098, 900000000000001022, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006099, 900000000000001022, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006100, 900000000000001023, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006101, 900000000000001023, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006102, 900000000000001023, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006103, 900000000000001023, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006104, 900000000000001023, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006105, 900000000000001023, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name) VALUES
    (900000000000004000, 900000000000001004, 'page'),
    (900000000000004001, 900000000000001004, 'size'),
    (900000000000004002, 900000000000001009, 'page'),
    (900000000000004003, 900000000000001009, 'size'),
    (900000000000004004, 900000000000001008, 'page'),
    (900000000000004005, 900000000000001008, 'size'),
    (900000000000004006, 900000000000001009, 'occurredFrom'),
    (900000000000004007, 900000000000001009, 'occurredTo'),
    (900000000000004008, 900000000000001009, 'actorId'),
    (900000000000004009, 900000000000001009, 'actionCode'),
    (900000000000004010, 900000000000001009, 'result'),
    (900000000000004011, 900000000000001009, 'requestId'),
    (900000000000004020, 900000000000001018, 'page'),
    (900000000000004021, 900000000000001018, 'size'),
    (900000000000004022, 900000000000001018, 'channel'),
    (900000000000004023, 900000000000001018, 'status'),
    (900000000000004024, 900000000000001018, 'targetServiceCode'),
    (900000000000004025, 900000000000001021, 'page'),
    (900000000000004026, 900000000000001021, 'size'),
    (900000000000004027, 900000000000001021, 'channel'),
    (900000000000004028, 900000000000001021, 'status'),
    (900000000000004029, 900000000000001021, 'targetServiceCode'),
    (900000000000004030, 900000000000001021, 'routeCode');

-- Risk Operations：仅用于已审阅的新库一次性手工初始化，全部 Route 与子项一同生效。
INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
    (900000000000001024, 'operations-risk-rule-authoring-context', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/authoring-context/{pointCode}', '/decision-rule-sets/v1/authoring-context/{pointCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001025, 'operations-risk-rule-set-list', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/rule-sets', '/decision-rule-sets/v1/rule-sets', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001026, 'operations-risk-rule-set-detail', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}', '/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001027, 'operations-risk-rule-set-save', 'OPERATIONS', 'POST', '/api/operations/decision-rule-sets/v1/rule-sets', '/decision-rule-sets/v1/rule-sets', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000537, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001028, 'operations-risk-rule-set-freeze', 'OPERATIONS', 'POST', '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze', '/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000538, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001029, 'operations-risk-test-candidate-list', 'OPERATIONS', 'GET', '/api/operations/decision-releases/v1/candidates', '/decision-releases/v1/candidates', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001030, 'operations-risk-test-candidate-detail', 'OPERATIONS', 'GET', '/api/operations/decision-releases/v1/candidates/{releaseId}', '/decision-releases/v1/candidates/{releaseId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001031, 'operations-risk-test-candidate-create', 'OPERATIONS', 'POST', '/api/operations/decision-releases/v1/candidates', '/decision-releases/v1/candidates', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000539, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time),
    (900000000000001032, 'operations-risk-test-candidate-validate', 'OPERATIONS', 'POST', '/api/operations/decision-releases/v1/candidates/{releaseId}/validate', '/decision-releases/v1/candidates/{releaseId}/validate', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000540, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
    (900000000000005100, 900000000000001024, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005101, 900000000000001024, 'origin', 'FORWARD_REQUEST'),
    (900000000000005102, 900000000000001025, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005103, 900000000000001025, 'origin', 'FORWARD_REQUEST'),
    (900000000000005104, 900000000000001026, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005105, 900000000000001026, 'origin', 'FORWARD_REQUEST'),
    (900000000000005106, 900000000000001027, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005107, 900000000000001027, 'origin', 'FORWARD_REQUEST'),
    (900000000000005108, 900000000000001027, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005109, 900000000000001027, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005110, 900000000000001027, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005111, 900000000000001027, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005112, 900000000000001028, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005113, 900000000000001028, 'origin', 'FORWARD_REQUEST'),
    (900000000000005114, 900000000000001028, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005115, 900000000000001028, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005116, 900000000000001028, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005117, 900000000000001028, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005118, 900000000000001029, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005119, 900000000000001029, 'origin', 'FORWARD_REQUEST'),
    (900000000000005120, 900000000000001030, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005121, 900000000000001030, 'origin', 'FORWARD_REQUEST'),
    (900000000000005122, 900000000000001031, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005123, 900000000000001031, 'origin', 'FORWARD_REQUEST'),
    (900000000000005124, 900000000000001031, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005125, 900000000000001031, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005126, 900000000000001031, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005127, 900000000000001031, 'idempotency-key', 'FORWARD_REQUEST'),
    (900000000000005128, 900000000000001032, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005129, 900000000000001032, 'origin', 'FORWARD_REQUEST'),
    (900000000000005130, 900000000000001032, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005131, 900000000000001032, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005132, 900000000000001032, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005133, 900000000000001032, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name) VALUES
    (900000000000004031, 900000000000001025, 'page'),
    (900000000000004032, 900000000000001025, 'size'),
    (900000000000004033, 900000000000001025, 'pointCode'),
    (900000000000004034, 900000000000001025, 'ruleSetCode'),
    (900000000000004035, 900000000000001025, 'status'),
    (900000000000004036, 900000000000001029, 'page'),
    (900000000000004037, 900000000000001029, 'size'),
    (900000000000004038, 900000000000001029, 'pointCode'),
    (900000000000004039, 900000000000001029, 'status');

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
    (900000000000006110, 900000000000001024, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006111, 900000000000001024, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006112, 900000000000001024, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006113, 900000000000001024, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006114, 900000000000001024, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006115, 900000000000001024, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006116, 900000000000001025, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006117, 900000000000001025, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006118, 900000000000001025, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006119, 900000000000001025, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006120, 900000000000001025, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006121, 900000000000001025, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006122, 900000000000001026, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006123, 900000000000001026, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006124, 900000000000001026, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006125, 900000000000001026, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006126, 900000000000001026, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006127, 900000000000001026, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006128, 900000000000001027, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006129, 900000000000001027, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006130, 900000000000001027, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006131, 900000000000001027, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006132, 900000000000001027, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006133, 900000000000001027, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006134, 900000000000001028, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006135, 900000000000001028, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006136, 900000000000001028, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006137, 900000000000001028, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006138, 900000000000001028, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006139, 900000000000001028, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006140, 900000000000001029, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006141, 900000000000001029, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006142, 900000000000001029, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006143, 900000000000001029, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006144, 900000000000001029, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006145, 900000000000001029, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006146, 900000000000001030, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006147, 900000000000001030, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006148, 900000000000001030, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006149, 900000000000001030, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006150, 900000000000001030, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006151, 900000000000001030, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006152, 900000000000001031, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006153, 900000000000001031, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006154, 900000000000001031, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006155, 900000000000001031, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006156, 900000000000001031, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006157, 900000000000001031, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
    (900000000000006158, 900000000000001032, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006159, 900000000000001032, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006160, 900000000000001032, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006161, 900000000000001032, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006162, 900000000000001032, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006163, 900000000000001032, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

-- 2026-09-11 规则集整体删除：新环境注册，Route/按钮保持 DISABLED，启用另行审核。
INSERT INTO t_permission
(permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
VALUES (900000000000000541, 'RISK_RULE_SET_DELETE', 'RISK_DECISION_CONFIGURATION', 'DELETE', 'HIGH',
        'ACTIVE', 1, @platform_initial_time, @platform_initial_time);
INSERT INTO t_role_permission
(role_permission_id, role_id, permission_id, status, version, create_time, update_time)
VALUES (900000000000000587, 900000000000000510, 900000000000000541, 'REVOKED', 2,
        @platform_initial_time, @platform_initial_time);
INSERT INTO t_console_resource
(resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key,
 permission_id, sort_order, status, version, create_time, update_time)
VALUES (900000000000000571, 'OPERATIONS_RISK_RULE_SET_DELETE', 'OPERATIONS', 'BUTTON', 900000000000000566,
        '删除规则集', NULL, NULL, 900000000000000541, 50, 'DISABLED', 1,
        @platform_initial_time, @platform_initial_time);
INSERT INTO t_gateway_route
(route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id,
 security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode,
 response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version,
 created_by, updated_by, create_time, update_time)
VALUES (900000000000001033, 'operations-risk-rule-set-delete', 'OPERATIONS', 'DELETE',
        '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetId}', '/decision-rule-sets/v1/rule-sets/{ruleSetId}',
        900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000541, NULL, 'DROP', 'NONE',
        'NONE',
        16384, 5000, 10000, 'DISABLED', 1, 1, 1,
        @platform_initial_time, @platform_initial_time);
INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
    (900000000000005140, 900000000000001033, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005141, 900000000000001033, 'origin', 'FORWARD_REQUEST'),
    (900000000000005142, 900000000000001033, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005143, 900000000000001033, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005144, 900000000000001033, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005145, 900000000000001033, 'idempotency-key', 'FORWARD_REQUEST');
INSERT INTO t_gateway_guard_rule
(guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
    (900000000000006164, 900000000000001033, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006165, 900000000000001033, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006166, 900000000000001033, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006167, 900000000000001033, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006168, 900000000000001033, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006169, 900000000000001033, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

-- RCO-16：规则集历史、发布引用与两类字典。仅注册，Route / Resource 保持 DISABLED。
-- 与 requirements/20260911/operations-console-风控规则集历史与字典管理/db/platform-support-service/registration.json 对齐。
INSERT INTO t_permission
(permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
  (900000000000012101, 'RISK_REJECTION_REASON_CREATE', 'RISK_REJECTION_REASON', 'CREATE', 'MEDIUM', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012102, 'RISK_REJECTION_REASON_UPDATE', 'RISK_REJECTION_REASON', 'UPDATE', 'MEDIUM', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012103, 'RISK_REJECTION_REASON_STATUS', 'RISK_REJECTION_REASON', 'CHANGE_STATUS', 'MEDIUM', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012104, 'RISK_SUBJECT_TAG_CREATE', 'RISK_SUBJECT_TAG', 'CREATE', 'MEDIUM', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012105, 'RISK_SUBJECT_TAG_UPDATE', 'RISK_SUBJECT_TAG', 'UPDATE', 'MEDIUM', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012106, 'RISK_SUBJECT_TAG_STATUS', 'RISK_SUBJECT_TAG', 'CHANGE_STATUS', 'MEDIUM', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);
INSERT INTO t_role_permission
(role_permission_id, role_id, permission_id, status, version, create_time, update_time) VALUES
  (900000000000012201, 900000000000000510, 900000000000012101, 'REVOKED', 2, @platform_initial_time, @platform_initial_time),
  (900000000000012202, 900000000000000510, 900000000000012102, 'REVOKED', 2, @platform_initial_time, @platform_initial_time),
  (900000000000012203, 900000000000000510, 900000000000012103, 'REVOKED', 2, @platform_initial_time, @platform_initial_time),
  (900000000000012204, 900000000000000510, 900000000000012104, 'REVOKED', 2, @platform_initial_time, @platform_initial_time),
  (900000000000012205, 900000000000000510, 900000000000012105, 'REVOKED', 2, @platform_initial_time, @platform_initial_time),
  (900000000000012206, 900000000000000510, 900000000000012106, 'REVOKED', 2, @platform_initial_time, @platform_initial_time);
INSERT INTO t_console_resource
(resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time) VALUES
  (900000000000012301, 'OPERATIONS_RISK_RULE_SET_HISTORY', 'OPERATIONS', 'BUTTON', 900000000000000566, '历史版本', NULL, NULL, 900000000000000536, 60, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012302, 'OPERATIONS_RISK_RULE_SET_REFERENCES', 'OPERATIONS', 'BUTTON', 900000000000000566, '引用关系', NULL, NULL, 900000000000000536, 70, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012303, 'OPERATIONS_RISK_REJECTION_REASONS', 'OPERATIONS', 'MENU', 900000000000000565, '拒绝原因', '/risk/rejection-reasons', 'OPERATIONS_RISK_REJECTION_REASONS', 900000000000000536, 20, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012304, 'OPERATIONS_RISK_SUBJECT_TAGS', 'OPERATIONS', 'MENU', 900000000000000565, '主体标签', '/risk/subject-tags', 'OPERATIONS_RISK_SUBJECT_TAGS', 900000000000000536, 30, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012305, 'OPERATIONS_RISK_REJECTION_REASON_CREATE', 'OPERATIONS', 'BUTTON', 900000000000012303, '新增原因', NULL, NULL, 900000000000012101, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012306, 'OPERATIONS_RISK_REJECTION_REASON_UPDATE', 'OPERATIONS', 'BUTTON', 900000000000012303, '编辑原因', NULL, NULL, 900000000000012102, 20, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012307, 'OPERATIONS_RISK_REJECTION_REASON_STATUS', 'OPERATIONS', 'BUTTON', 900000000000012303, '启停原因', NULL, NULL, 900000000000012103, 30, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012308, 'OPERATIONS_RISK_SUBJECT_TAG_CREATE', 'OPERATIONS', 'BUTTON', 900000000000012304, '新增标签', NULL, NULL, 900000000000012104, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012309, 'OPERATIONS_RISK_SUBJECT_TAG_UPDATE', 'OPERATIONS', 'BUTTON', 900000000000012304, '编辑标签', NULL, NULL, 900000000000012105, 20, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000012310, 'OPERATIONS_RISK_SUBJECT_TAG_STATUS', 'OPERATIONS', 'BUTTON', 900000000000012304, '启停标签', NULL, NULL, 900000000000012106, 30, 'DISABLED', 1, @platform_initial_time, @platform_initial_time);
INSERT INTO t_gateway_route
(route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000012001, 'operations-risk-rule-set-versions', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetId}/versions', '/decision-rule-sets/v1/rule-sets/{ruleSetId}/versions', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012002, 'operations-risk-rule-set-release-references', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetId}/release-references', '/decision-rule-sets/v1/rule-sets/{ruleSetId}/release-references', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012003, 'operations-risk-rejection-reason-list', 'OPERATIONS', 'GET', '/api/operations/decision-dictionaries/v1/rejection-reasons', '/decision-dictionaries/v1/rejection-reasons', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012004, 'operations-risk-rejection-reason-detail', 'OPERATIONS', 'GET', '/api/operations/decision-dictionaries/v1/rejection-reasons/{reasonCode}', '/decision-dictionaries/v1/rejection-reasons/{reasonCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012005, 'operations-risk-rejection-reason-create', 'OPERATIONS', 'POST', '/api/operations/decision-dictionaries/v1/rejection-reasons', '/decision-dictionaries/v1/rejection-reasons', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000012101, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012006, 'operations-risk-rejection-reason-update', 'OPERATIONS', 'PUT', '/api/operations/decision-dictionaries/v1/rejection-reasons/{reasonCode}', '/decision-dictionaries/v1/rejection-reasons/{reasonCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000012102, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012007, 'operations-risk-rejection-reason-status', 'OPERATIONS', 'POST', '/api/operations/decision-dictionaries/v1/rejection-reasons/{reasonCode}/status', '/decision-dictionaries/v1/rejection-reasons/{reasonCode}/status', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000012103, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012008, 'operations-risk-rejection-reason-references', 'OPERATIONS', 'GET', '/api/operations/decision-dictionaries/v1/rejection-reasons/{reasonCode}/references', '/decision-dictionaries/v1/rejection-reasons/{reasonCode}/references', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012009, 'operations-risk-subject-tag-list', 'OPERATIONS', 'GET', '/api/operations/decision-dictionaries/v1/subject-tags', '/decision-dictionaries/v1/subject-tags', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012010, 'operations-risk-subject-tag-detail', 'OPERATIONS', 'GET', '/api/operations/decision-dictionaries/v1/subject-tags/{tagCode}', '/decision-dictionaries/v1/subject-tags/{tagCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012011, 'operations-risk-subject-tag-create', 'OPERATIONS', 'POST', '/api/operations/decision-dictionaries/v1/subject-tags', '/decision-dictionaries/v1/subject-tags', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000012104, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012012, 'operations-risk-subject-tag-update', 'OPERATIONS', 'PUT', '/api/operations/decision-dictionaries/v1/subject-tags/{tagCode}', '/decision-dictionaries/v1/subject-tags/{tagCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000012105, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012013, 'operations-risk-subject-tag-status', 'OPERATIONS', 'POST', '/api/operations/decision-dictionaries/v1/subject-tags/{tagCode}/status', '/decision-dictionaries/v1/subject-tags/{tagCode}/status', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000012106, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000012014, 'operations-risk-subject-tag-references', 'OPERATIONS', 'GET', '/api/operations/decision-dictionaries/v1/subject-tags/{tagCode}/references', '/decision-dictionaries/v1/subject-tags/{tagCode}/references', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time);
INSERT INTO t_gateway_route_header
(route_header_id, route_id, header_name, header_use) VALUES
  (900000000000013001, 900000000000012001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013002, 900000000000012001, 'origin', 'FORWARD_REQUEST'),
  (900000000000013003, 900000000000012002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013004, 900000000000012002, 'origin', 'FORWARD_REQUEST'),
  (900000000000013005, 900000000000012003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013006, 900000000000012003, 'origin', 'FORWARD_REQUEST'),
  (900000000000013007, 900000000000012004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013008, 900000000000012004, 'origin', 'FORWARD_REQUEST'),
  (900000000000013009, 900000000000012005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013010, 900000000000012005, 'origin', 'FORWARD_REQUEST'),
  (900000000000013011, 900000000000012005, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000013012, 900000000000012005, 'content-type', 'FORWARD_REQUEST'),
  (900000000000013015, 900000000000012006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013016, 900000000000012006, 'origin', 'FORWARD_REQUEST'),
  (900000000000013017, 900000000000012006, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000013018, 900000000000012006, 'content-type', 'FORWARD_REQUEST'),
  (900000000000013021, 900000000000012007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013022, 900000000000012007, 'origin', 'FORWARD_REQUEST'),
  (900000000000013023, 900000000000012007, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000013024, 900000000000012007, 'content-type', 'FORWARD_REQUEST'),
  (900000000000013027, 900000000000012008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013028, 900000000000012008, 'origin', 'FORWARD_REQUEST'),
  (900000000000013029, 900000000000012009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013030, 900000000000012009, 'origin', 'FORWARD_REQUEST'),
  (900000000000013031, 900000000000012010, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013032, 900000000000012010, 'origin', 'FORWARD_REQUEST'),
  (900000000000013033, 900000000000012011, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013034, 900000000000012011, 'origin', 'FORWARD_REQUEST'),
  (900000000000013035, 900000000000012011, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000013036, 900000000000012011, 'content-type', 'FORWARD_REQUEST'),
  (900000000000013039, 900000000000012012, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013040, 900000000000012012, 'origin', 'FORWARD_REQUEST'),
  (900000000000013041, 900000000000012012, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000013042, 900000000000012012, 'content-type', 'FORWARD_REQUEST'),
  (900000000000013045, 900000000000012013, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013046, 900000000000012013, 'origin', 'FORWARD_REQUEST'),
  (900000000000013047, 900000000000012013, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000013048, 900000000000012013, 'content-type', 'FORWARD_REQUEST'),
  (900000000000013051, 900000000000012014, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000013052, 900000000000012014, 'origin', 'FORWARD_REQUEST');
INSERT INTO t_gateway_route_query_param
(route_query_param_id, route_id, parameter_name) VALUES
  (900000000000014001, 900000000000012001, 'page'),
  (900000000000014002, 900000000000012001, 'size'),
  (900000000000014003, 900000000000012001, 'maxVersionNo'),
  (900000000000014004, 900000000000012002, 'page'),
  (900000000000014005, 900000000000012002, 'size'),
  (900000000000014006, 900000000000012002, 'environmentCode'),
  (900000000000014007, 900000000000012002, 'status'),
  (900000000000014008, 900000000000012002, 'ruleSetVersionId'),
  (900000000000014009, 900000000000012003, 'page'),
  (900000000000014010, 900000000000012003, 'size'),
  (900000000000014011, 900000000000012003, 'keyword'),
  (900000000000014012, 900000000000012003, 'status'),
  (900000000000014013, 900000000000012008, 'page'),
  (900000000000014014, 900000000000012008, 'size'),
  (900000000000014015, 900000000000012008, 'scope'),
  (900000000000014016, 900000000000012009, 'page'),
  (900000000000014017, 900000000000012009, 'size'),
  (900000000000014018, 900000000000012009, 'keyword'),
  (900000000000014019, 900000000000012009, 'status'),
  (900000000000014021, 900000000000012014, 'page'),
  (900000000000014022, 900000000000012014, 'size'),
  (900000000000014023, 900000000000012014, 'scope');
INSERT INTO t_gateway_guard_rule
(guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
  (900000000000015001, 900000000000012001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015002, 900000000000012001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015003, 900000000000012001, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015004, 900000000000012001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015005, 900000000000012001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015006, 900000000000012001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015007, 900000000000012002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015008, 900000000000012002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015009, 900000000000012002, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015010, 900000000000012002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015011, 900000000000012002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015012, 900000000000012002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015013, 900000000000012003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015014, 900000000000012003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015015, 900000000000012003, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015016, 900000000000012003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015017, 900000000000012003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015018, 900000000000012003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015019, 900000000000012004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015020, 900000000000012004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015021, 900000000000012004, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015022, 900000000000012004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015023, 900000000000012004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015024, 900000000000012004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015025, 900000000000012005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015026, 900000000000012005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015027, 900000000000012005, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015028, 900000000000012005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015029, 900000000000012005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015030, 900000000000012005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015031, 900000000000012006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015032, 900000000000012006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015033, 900000000000012006, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015034, 900000000000012006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015035, 900000000000012006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015036, 900000000000012006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015037, 900000000000012007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015038, 900000000000012007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015039, 900000000000012007, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015040, 900000000000012007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015041, 900000000000012007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015042, 900000000000012007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015043, 900000000000012008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015044, 900000000000012008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015045, 900000000000012008, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015046, 900000000000012008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015047, 900000000000012008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015048, 900000000000012008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015049, 900000000000012009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015050, 900000000000012009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015051, 900000000000012009, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015052, 900000000000012009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015053, 900000000000012009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015054, 900000000000012009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015055, 900000000000012010, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015056, 900000000000012010, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015057, 900000000000012010, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015058, 900000000000012010, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015059, 900000000000012010, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015060, 900000000000012010, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015061, 900000000000012011, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015062, 900000000000012011, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015063, 900000000000012011, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015064, 900000000000012011, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015065, 900000000000012011, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015066, 900000000000012011, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015067, 900000000000012012, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015068, 900000000000012012, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015069, 900000000000012012, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015070, 900000000000012012, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015071, 900000000000012012, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015072, 900000000000012012, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015073, 900000000000012013, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015074, 900000000000012013, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015075, 900000000000012013, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015076, 900000000000012013, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015077, 900000000000012013, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015078, 900000000000012013, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000015079, 900000000000012014, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000015080, 900000000000012014, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000015081, 900000000000012014, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000015082, 900000000000012014, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000015083, 900000000000012014, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000015084, 900000000000012014, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);
UPDATE t_role SET version = version + 1, update_time = @platform_initial_time WHERE role_id = 900000000000000510;

-- RCO-17: dormant decision flow configuration routes, permissions and resources.
SET @rco17_time = UTC_TIMESTAMP(6);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
  (900000000000021101, 'RISK_DECISION_FLOW_SAVE', 'RISK_DECISION_FLOW', 'SAVE', 'MEDIUM', 'ACTIVE', 1, @rco17_time, @rco17_time),
  (900000000000021102, 'RISK_DECISION_FLOW_DELETE', 'RISK_DECISION_FLOW', 'DELETE', 'MEDIUM', 'ACTIVE', 1, @rco17_time, @rco17_time);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time) VALUES
  (900000000000021201, 900000000000000510, 900000000000021101, 'REVOKED', 1, @rco17_time, @rco17_time),
  (900000000000021202, 900000000000000510, 900000000000021102, 'REVOKED', 1, @rco17_time, @rco17_time);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time) VALUES
  (900000000000021301, 'OPERATIONS_RISK_DECISION_FLOWS', 'OPERATIONS', 'MENU', 900000000000000565, '决策流', '/risk/decision-flows', 'OPERATIONS_RISK_DECISION_FLOWS', 900000000000000536, 40, 'DISABLED', 1, @rco17_time, @rco17_time),
  (900000000000021302, 'OPERATIONS_RISK_DECISION_FLOW_SAVE', 'OPERATIONS', 'BUTTON', 900000000000021301, '保存配置', NULL, NULL, 900000000000021101, 10, 'DISABLED', 1, @rco17_time, @rco17_time),
  (900000000000021303, 'OPERATIONS_RISK_DECISION_FLOW_DELETE', 'OPERATIONS', 'BUTTON', 900000000000021301, '删除', NULL, NULL, 900000000000021102, 20, 'DISABLED', 1, @rco17_time, @rco17_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000021001, 'operations-risk-flow-authoring-context', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/authoring-context', '/decision-flows/v1/authoring-context', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021002, 'operations-risk-flow-list', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/flows', '/decision-flows/v1/flows', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021003, 'operations-risk-flow-save', 'OPERATIONS', 'POST', '/api/operations/decision-flows/v1/flows', '/decision-flows/v1/flows', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000021101, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021004, 'operations-risk-flow-validate', 'OPERATIONS', 'POST', '/api/operations/decision-flows/v1/flows/validate', '/decision-flows/v1/flows/validate', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000021101, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021005, 'operations-risk-flow-detail', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/flows/{flowId}', '/decision-flows/v1/flows/{flowId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021006, 'operations-risk-flow-history', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/flows/{flowId}/versions', '/decision-flows/v1/flows/{flowId}/versions', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021007, 'operations-risk-flow-version', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/flows/{flowId}/versions/{flowVersionId}', '/decision-flows/v1/flows/{flowId}/versions/{flowVersionId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021008, 'operations-risk-flow-delete', 'OPERATIONS', 'DELETE', '/api/operations/decision-flows/v1/flows/{flowId}', '/decision-flows/v1/flows/{flowId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000021102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021009, 'operations-risk-flow-rule-set-reference', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/rule-set-reference', '/decision-flows/v1/rule-set-reference', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time),
  (900000000000021010, 'operations-risk-flow-legacy-point', 'OPERATIONS', 'GET', '/api/operations/decision-flows/v1/legacy-point-versions/{pointVersionId}', '/decision-flows/v1/legacy-point-versions/{pointVersionId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco17_time, @rco17_time);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
  (900000000000022001, 900000000000021001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022002, 900000000000021001, 'origin', 'FORWARD_REQUEST'),
  (900000000000022003, 900000000000021002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022004, 900000000000021002, 'origin', 'FORWARD_REQUEST'),
  (900000000000022005, 900000000000021003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022006, 900000000000021003, 'origin', 'FORWARD_REQUEST'),
  (900000000000022007, 900000000000021003, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000022008, 900000000000021003, 'content-type', 'FORWARD_REQUEST'),
  (900000000000022009, 900000000000021003, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000022010, 900000000000021003, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000022011, 900000000000021004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022012, 900000000000021004, 'origin', 'FORWARD_REQUEST'),
  (900000000000022013, 900000000000021004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000022014, 900000000000021004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000022015, 900000000000021005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022016, 900000000000021005, 'origin', 'FORWARD_REQUEST'),
  (900000000000022017, 900000000000021006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022018, 900000000000021006, 'origin', 'FORWARD_REQUEST'),
  (900000000000022019, 900000000000021007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022020, 900000000000021007, 'origin', 'FORWARD_REQUEST'),
  (900000000000022021, 900000000000021008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022022, 900000000000021008, 'origin', 'FORWARD_REQUEST'),
  (900000000000022023, 900000000000021008, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000022024, 900000000000021008, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000022025, 900000000000021009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022026, 900000000000021009, 'origin', 'FORWARD_REQUEST'),
  (900000000000022027, 900000000000021010, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000022028, 900000000000021010, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name) VALUES
  (900000000000023001, 900000000000021001, 'pointCode'),
  (900000000000023002, 900000000000021002, 'pointCode'),
  (900000000000023003, 900000000000021002, 'flowCode'),
  (900000000000023004, 900000000000021002, 'keyword'),
  (900000000000023005, 900000000000021002, 'page'),
  (900000000000023006, 900000000000021002, 'size'),
  (900000000000023007, 900000000000021006, 'maxVersionNo'),
  (900000000000023008, 900000000000021006, 'page'),
  (900000000000023009, 900000000000021006, 'size'),
  (900000000000023010, 900000000000021008, 'expectedVersion'),
  (900000000000023011, 900000000000021009, 'sourceType'),
  (900000000000023012, 900000000000021009, 'sourceVersionId'),
  (900000000000023013, 900000000000021009, 'nodeKey');

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
  (900000000000024001, 900000000000021001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024002, 900000000000021001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024003, 900000000000021001, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024004, 900000000000021001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024005, 900000000000021001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024006, 900000000000021001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024007, 900000000000021002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024008, 900000000000021002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024009, 900000000000021002, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024010, 900000000000021002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024011, 900000000000021002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024012, 900000000000021002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024013, 900000000000021003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024014, 900000000000021003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024015, 900000000000021003, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024016, 900000000000021003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024017, 900000000000021003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024018, 900000000000021003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024019, 900000000000021004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024020, 900000000000021004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024021, 900000000000021004, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024022, 900000000000021004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024023, 900000000000021004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024024, 900000000000021004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024025, 900000000000021005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024026, 900000000000021005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024027, 900000000000021005, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024028, 900000000000021005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024029, 900000000000021005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024030, 900000000000021005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024031, 900000000000021006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024032, 900000000000021006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024033, 900000000000021006, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024034, 900000000000021006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024035, 900000000000021006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024036, 900000000000021006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024037, 900000000000021007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024038, 900000000000021007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024039, 900000000000021007, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024040, 900000000000021007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024041, 900000000000021007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024042, 900000000000021007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024043, 900000000000021008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024044, 900000000000021008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024045, 900000000000021008, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024046, 900000000000021008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024047, 900000000000021008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024048, 900000000000021008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024049, 900000000000021009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024050, 900000000000021009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024051, 900000000000021009, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024052, 900000000000021009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024053, 900000000000021009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024054, 900000000000021009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000024055, 900000000000021010, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000024056, 900000000000021010, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000024057, 900000000000021010, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000024058, 900000000000021010, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000024059, 900000000000021010, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000024060, 900000000000021010, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

UPDATE t_role SET version=version+1, update_time=@rco17_time WHERE role_id=900000000000000510;

-- RCO-19：统一变量管理入口，保持禁用状态，随单一初始化目录人工应用。
SET @rco19_time=UTC_TIMESTAMP(6);
SET @rco19_admin=900000000000000510;
SET @rco19_read=900000000000000536;
SET @rco19_parent=900000000000000565;
SET @rco19_target=900000000000000001;
INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
  (900000000000031101, 'RISK_VARIABLE_MANAGE', 'RISK_VARIABLE', 'MANAGE', 'MEDIUM', 'ACTIVE', 1, @rco19_time, @rco19_time),
  (900000000000031102, 'RISK_VARIABLE_DELETE', 'RISK_VARIABLE', 'DELETE', 'MEDIUM', 'ACTIVE', 1, @rco19_time, @rco19_time);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time) VALUES
  (900000000000031201, @rco19_admin, 900000000000031101, 'REVOKED', 1, @rco19_time, @rco19_time),
  (900000000000031202, @rco19_admin, 900000000000031102, 'REVOKED', 1, @rco19_time, @rco19_time);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time) VALUES
  (900000000000031301, 'OPERATIONS_RISK_VARIABLES', 'OPERATIONS', 'MENU', @rco19_parent, '变量管理', '/risk/variables', 'OPERATIONS_RISK_VARIABLES', @rco19_read, 50, 'DISABLED', 1, @rco19_time, @rco19_time),
  (900000000000031302, 'OPERATIONS_RISK_VARIABLE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000031301, '维护变量', NULL, NULL, 900000000000031101, 10, 'DISABLED', 1, @rco19_time, @rco19_time),
  (900000000000031303, 'OPERATIONS_RISK_VARIABLE_DELETE', 'OPERATIONS', 'BUTTON', 900000000000031301, '删除变量', NULL, NULL, 900000000000031102, 20, 'DISABLED', 1, @rco19_time, @rco19_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000031001, 'operations-risk-variable-manage-context', 'OPERATIONS', 'GET', '/api/operations/decision-variables/v1/authoring-context', '/decision-variables/v1/authoring-context', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @rco19_read, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031002, 'operations-risk-variable-list', 'OPERATIONS', 'GET', '/api/operations/decision-variables/v1/variables', '/decision-variables/v1/variables', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @rco19_read, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031003, 'operations-risk-variable-detail', 'OPERATIONS', 'GET', '/api/operations/decision-variables/v1/variables/{variableCode}', '/decision-variables/v1/variables/{variableCode}', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @rco19_read, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031004, 'operations-risk-variable-create', 'OPERATIONS', 'POST', '/api/operations/decision-variables/v1/variables', '/decision-variables/v1/variables', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000031101, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031005, 'operations-risk-variable-update', 'OPERATIONS', 'PUT', '/api/operations/decision-variables/v1/variables/{variableCode}', '/decision-variables/v1/variables/{variableCode}', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000031101, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031006, 'operations-risk-variable-delete', 'OPERATIONS', 'DELETE', '/api/operations/decision-variables/v1/variables/{variableCode}', '/decision-variables/v1/variables/{variableCode}', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000031102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031007, 'operations-risk-variable-members', 'OPERATIONS', 'GET', '/api/operations/decision-variables/v1/variables/{variableCode}/members', '/decision-variables/v1/variables/{variableCode}/members', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @rco19_read, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031008, 'operations-risk-variable-members-replace', 'OPERATIONS', 'PUT', '/api/operations/decision-variables/v1/variables/{variableCode}/members', '/decision-variables/v1/variables/{variableCode}/members', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000031101, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time),
  (900000000000031009, 'operations-risk-variable-references', 'OPERATIONS', 'GET', '/api/operations/decision-variables/v1/variables/{variableCode}/references', '/decision-variables/v1/variables/{variableCode}/references', @rco19_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @rco19_read, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rco19_time, @rco19_time);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
  (900000000000032001, 900000000000031001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032002, 900000000000031001, 'origin', 'FORWARD_REQUEST'),
  (900000000000032003, 900000000000031002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032004, 900000000000031002, 'origin', 'FORWARD_REQUEST'),
  (900000000000032005, 900000000000031003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032006, 900000000000031003, 'origin', 'FORWARD_REQUEST'),
  (900000000000032007, 900000000000031004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032008, 900000000000031004, 'origin', 'FORWARD_REQUEST'),
  (900000000000032009, 900000000000031004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000032010, 900000000000031004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000032011, 900000000000031005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032012, 900000000000031005, 'origin', 'FORWARD_REQUEST'),
  (900000000000032013, 900000000000031005, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000032014, 900000000000031005, 'content-type', 'FORWARD_REQUEST'),
  (900000000000032015, 900000000000031006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032016, 900000000000031006, 'origin', 'FORWARD_REQUEST'),
  (900000000000032017, 900000000000031007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032018, 900000000000031007, 'origin', 'FORWARD_REQUEST'),
  (900000000000032019, 900000000000031008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032020, 900000000000031008, 'origin', 'FORWARD_REQUEST'),
  (900000000000032021, 900000000000031008, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000032022, 900000000000031008, 'content-type', 'FORWARD_REQUEST'),
  (900000000000032023, 900000000000031009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000032024, 900000000000031009, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name) VALUES
  (900000000000033001, 900000000000031001, 'pointCode'),
  (900000000000033002, 900000000000031002, 'keyword'),
  (900000000000033003, 900000000000031002, 'sourceType'),
  (900000000000033004, 900000000000031002, 'valueType'),
  (900000000000033005, 900000000000031002, 'status'),
  (900000000000033006, 900000000000031002, 'pointCode'),
  (900000000000033007, 900000000000031002, 'usage'),
  (900000000000033008, 900000000000031002, 'nodeType'),
  (900000000000033009, 900000000000031002, 'codes'),
  (900000000000033010, 900000000000031002, 'page'),
  (900000000000033011, 900000000000031002, 'size'),
  (900000000000033012, 900000000000031006, 'expectedVersion'),
  (900000000000033013, 900000000000031007, 'keyword'),
  (900000000000033014, 900000000000031007, 'page'),
  (900000000000033015, 900000000000031007, 'size'),
  (900000000000033016, 900000000000031007, 'expectedVersion'),
  (900000000000033017, 900000000000031009, 'scope'),
  (900000000000033018, 900000000000031009, 'cursor'),
  (900000000000033019, 900000000000031009, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
  (900000000000034001, 900000000000031001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034002, 900000000000031001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034003, 900000000000031001, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034004, 900000000000031001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034005, 900000000000031001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034006, 900000000000031001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034007, 900000000000031002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034008, 900000000000031002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034009, 900000000000031002, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034010, 900000000000031002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034011, 900000000000031002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034012, 900000000000031002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034013, 900000000000031003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034014, 900000000000031003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034015, 900000000000031003, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034016, 900000000000031003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034017, 900000000000031003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034018, 900000000000031003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034019, 900000000000031004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034020, 900000000000031004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034021, 900000000000031004, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034022, 900000000000031004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034023, 900000000000031004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034024, 900000000000031004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034025, 900000000000031005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034026, 900000000000031005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034027, 900000000000031005, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034028, 900000000000031005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034029, 900000000000031005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034030, 900000000000031005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034031, 900000000000031006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034032, 900000000000031006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034033, 900000000000031006, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034034, 900000000000031006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034035, 900000000000031006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034036, 900000000000031006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034037, 900000000000031007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034038, 900000000000031007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034039, 900000000000031007, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034040, 900000000000031007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034041, 900000000000031007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034042, 900000000000031007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034043, 900000000000031008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034044, 900000000000031008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034045, 900000000000031008, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034046, 900000000000031008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034047, 900000000000031008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034048, 900000000000031008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000034049, 900000000000031009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000034050, 900000000000031009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000034051, 900000000000031009, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
  (900000000000034052, 900000000000031009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000034053, 900000000000031009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000034054, 900000000000031009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

UPDATE t_role SET version=version+1,update_time=@rco19_time WHERE role_id=@rco19_admin;

-- PLT-16/PLT-17：227 行注册，Route/Resource 停用、管理员授权 REVOKED。
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000041101, 'CONSOLE_RESOURCE_READ', 'CONSOLE_RESOURCE', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041102, 'CONSOLE_RESOURCE_MANAGE', 'CONSOLE_RESOURCE', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041103, 'PERMISSION_READ', 'PERMISSION', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041104, 'PERMISSION_MANAGE', 'PERMISSION', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000041201, 900000000000000510, 900000000000041101, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041202, 900000000000000510, 900000000000041102, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041203, 900000000000000510, 900000000000041103, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041204, 900000000000000510, 900000000000041104, 'REVOKED', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000041301, 'OPERATIONS_CONSOLE_RESOURCES', 'OPERATIONS', 'MENU', 900000000000000562, '菜单与按钮', '/console-resources', 'OPERATIONS_CONSOLE_RESOURCES', 900000000000041101, 20, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041302, 'OPERATIONS_CONSOLE_RESOURCE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000041301, '维护资源', NULL, NULL, 900000000000041102, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041303, 'OPERATIONS_PERMISSIONS', 'OPERATIONS', 'MENU', 900000000000000562, '权限目录', '/permissions', 'OPERATIONS_PERMISSIONS', 900000000000041103, 30, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000041304, 'OPERATIONS_PERMISSION_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000041303, '维护权限', NULL, NULL, 900000000000041104, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route (route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000041001, 'operations-gateway-target-create', 'OPERATIONS', 'POST', '/api/operations/gateway-admin/v1/target-services', '/gateway-admin/v1/target-services', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000535, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041002, 'operations-gateway-target-update', 'OPERATIONS', 'PUT', '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}', '/gateway-admin/v1/target-services/{targetServiceCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000535, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041003, 'operations-gateway-route-create', 'OPERATIONS', 'POST', '/api/operations/gateway-admin/v1/routes', '/gateway-admin/v1/routes', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000535, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041004, 'operations-gateway-route-update', 'OPERATIONS', 'PUT', '/api/operations/gateway-admin/v1/routes/{routeCode}', '/gateway-admin/v1/routes/{routeCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000535, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041005, 'operations-console-resource-list', 'OPERATIONS', 'GET', '/api/operations/console-admin/v1/resources', '/console-admin/v1/resources', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041006, 'operations-console-resource-detail', 'OPERATIONS', 'GET', '/api/operations/console-admin/v1/resources/{resourceCode}', '/console-admin/v1/resources/{resourceCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041007, 'operations-console-resource-create', 'OPERATIONS', 'POST', '/api/operations/console-admin/v1/resources', '/console-admin/v1/resources', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041008, 'operations-console-resource-update', 'OPERATIONS', 'PUT', '/api/operations/console-admin/v1/resources/{resourceCode}', '/console-admin/v1/resources/{resourceCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041009, 'operations-console-resource-status', 'OPERATIONS', 'PATCH', '/api/operations/console-admin/v1/resources/{resourceCode}/status', '/console-admin/v1/resources/{resourceCode}/status', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041010, 'operations-permission-list', 'OPERATIONS', 'GET', '/api/operations/permission-admin/v1/permissions', '/permission-admin/v1/permissions', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041011, 'operations-permission-detail', 'OPERATIONS', 'GET', '/api/operations/permission-admin/v1/permissions/{permissionCode}', '/permission-admin/v1/permissions/{permissionCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041012, 'operations-permission-create', 'OPERATIONS', 'POST', '/api/operations/permission-admin/v1/permissions', '/permission-admin/v1/permissions', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041104, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041013, 'operations-permission-update', 'OPERATIONS', 'PUT', '/api/operations/permission-admin/v1/permissions/{permissionCode}', '/permission-admin/v1/permissions/{permissionCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041104, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041014, 'operations-permission-status', 'OPERATIONS', 'PATCH', '/api/operations/permission-admin/v1/permissions/{permissionCode}/status', '/permission-admin/v1/permissions/{permissionCode}/status', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041104, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041015, 'operations-permission-references', 'OPERATIONS', 'GET', '/api/operations/permission-admin/v1/permissions/{permissionCode}/references', '/permission-admin/v1/permissions/{permissionCode}/references', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041016, 'operations-gateway-capabilities', 'OPERATIONS', 'GET', '/api/operations/gateway-admin/v1/capabilities', '/gateway-admin/v1/capabilities', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000534, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041017, 'operations-console-resource-capabilities', 'OPERATIONS', 'GET', '/api/operations/console-admin/v1/resource-capabilities', '/console-admin/v1/resource-capabilities', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000041101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time),
  (900000000000041018, 'operations-configuration-permission-options', 'OPERATIONS', 'GET', '/api/operations/permission-admin/v1/permission-options', '/permission-admin/v1/permission-options', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000042001, 900000000000041001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042002, 900000000000041001, 'origin', 'FORWARD_REQUEST'),
  (900000000000042003, 900000000000041001, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042004, 900000000000041001, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042005, 900000000000041001, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042006, 900000000000041001, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042007, 900000000000041002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042008, 900000000000041002, 'origin', 'FORWARD_REQUEST'),
  (900000000000042009, 900000000000041002, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042010, 900000000000041002, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042011, 900000000000041002, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042012, 900000000000041002, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042013, 900000000000041003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042014, 900000000000041003, 'origin', 'FORWARD_REQUEST'),
  (900000000000042015, 900000000000041003, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042016, 900000000000041003, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042017, 900000000000041003, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042018, 900000000000041003, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042019, 900000000000041004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042020, 900000000000041004, 'origin', 'FORWARD_REQUEST'),
  (900000000000042021, 900000000000041004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042022, 900000000000041004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042023, 900000000000041004, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042024, 900000000000041004, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042025, 900000000000041005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042026, 900000000000041005, 'origin', 'FORWARD_REQUEST'),
  (900000000000042027, 900000000000041006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042028, 900000000000041006, 'origin', 'FORWARD_REQUEST'),
  (900000000000042029, 900000000000041007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042030, 900000000000041007, 'origin', 'FORWARD_REQUEST'),
  (900000000000042031, 900000000000041007, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042032, 900000000000041007, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042033, 900000000000041007, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042034, 900000000000041007, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042035, 900000000000041008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042036, 900000000000041008, 'origin', 'FORWARD_REQUEST'),
  (900000000000042037, 900000000000041008, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042038, 900000000000041008, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042039, 900000000000041008, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042040, 900000000000041008, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042041, 900000000000041009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042042, 900000000000041009, 'origin', 'FORWARD_REQUEST'),
  (900000000000042043, 900000000000041009, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042044, 900000000000041009, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042045, 900000000000041009, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042046, 900000000000041009, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042047, 900000000000041010, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042048, 900000000000041010, 'origin', 'FORWARD_REQUEST'),
  (900000000000042049, 900000000000041011, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042050, 900000000000041011, 'origin', 'FORWARD_REQUEST'),
  (900000000000042051, 900000000000041012, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042052, 900000000000041012, 'origin', 'FORWARD_REQUEST'),
  (900000000000042053, 900000000000041012, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042054, 900000000000041012, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042055, 900000000000041012, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042056, 900000000000041012, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042057, 900000000000041013, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042058, 900000000000041013, 'origin', 'FORWARD_REQUEST'),
  (900000000000042059, 900000000000041013, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042060, 900000000000041013, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042061, 900000000000041013, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042062, 900000000000041013, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042063, 900000000000041014, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042064, 900000000000041014, 'origin', 'FORWARD_REQUEST'),
  (900000000000042065, 900000000000041014, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000042066, 900000000000041014, 'content-type', 'FORWARD_REQUEST'),
  (900000000000042067, 900000000000041014, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000042068, 900000000000041014, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000042069, 900000000000041015, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042070, 900000000000041015, 'origin', 'FORWARD_REQUEST'),
  (900000000000042071, 900000000000041016, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042072, 900000000000041016, 'origin', 'FORWARD_REQUEST'),
  (900000000000042073, 900000000000041017, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042074, 900000000000041017, 'origin', 'FORWARD_REQUEST'),
  (900000000000042075, 900000000000041018, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000042076, 900000000000041018, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000043001, 900000000000041010, 'page'),
  (900000000000043002, 900000000000041010, 'size'),
  (900000000000043003, 900000000000041010, 'code'),
  (900000000000043004, 900000000000041010, 'resourceType'),
  (900000000000043005, 900000000000041010, 'status'),
  (900000000000043006, 900000000000041010, 'riskLevel'),
  (900000000000043007, 900000000000041015, 'kind'),
  (900000000000043008, 900000000000041015, 'page'),
  (900000000000043009, 900000000000041015, 'size'),
  (900000000000043010, 900000000000041018, 'usage'),
  (900000000000043011, 900000000000041018, 'page'),
  (900000000000043012, 900000000000041018, 'size'),
  (900000000000043013, 900000000000041018, 'keyword');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000044001, 900000000000041001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044002, 900000000000041001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044003, 900000000000041001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044004, 900000000000041001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044005, 900000000000041001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044006, 900000000000041001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044007, 900000000000041002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044008, 900000000000041002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044009, 900000000000041002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044010, 900000000000041002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044011, 900000000000041002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044012, 900000000000041002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044013, 900000000000041003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044014, 900000000000041003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044015, 900000000000041003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044016, 900000000000041003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044017, 900000000000041003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044018, 900000000000041003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044019, 900000000000041004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044020, 900000000000041004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044021, 900000000000041004, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044022, 900000000000041004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044023, 900000000000041004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044024, 900000000000041004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044025, 900000000000041005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044026, 900000000000041005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044027, 900000000000041005, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044028, 900000000000041005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044029, 900000000000041005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044030, 900000000000041005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044031, 900000000000041006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044032, 900000000000041006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044033, 900000000000041006, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044034, 900000000000041006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044035, 900000000000041006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044036, 900000000000041006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044037, 900000000000041007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044038, 900000000000041007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044039, 900000000000041007, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044040, 900000000000041007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044041, 900000000000041007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044042, 900000000000041007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044043, 900000000000041008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044044, 900000000000041008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044045, 900000000000041008, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044046, 900000000000041008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044047, 900000000000041008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044048, 900000000000041008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044049, 900000000000041009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044050, 900000000000041009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044051, 900000000000041009, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044052, 900000000000041009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044053, 900000000000041009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044054, 900000000000041009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044055, 900000000000041010, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044056, 900000000000041010, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044057, 900000000000041010, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044058, 900000000000041010, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044059, 900000000000041010, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044060, 900000000000041010, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044061, 900000000000041011, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044062, 900000000000041011, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044063, 900000000000041011, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044064, 900000000000041011, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044065, 900000000000041011, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044066, 900000000000041011, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044067, 900000000000041012, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044068, 900000000000041012, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044069, 900000000000041012, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044070, 900000000000041012, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044071, 900000000000041012, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044072, 900000000000041012, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044073, 900000000000041013, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044074, 900000000000041013, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044075, 900000000000041013, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044076, 900000000000041013, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044077, 900000000000041013, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044078, 900000000000041013, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044079, 900000000000041014, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044080, 900000000000041014, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044081, 900000000000041014, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044082, 900000000000041014, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044083, 900000000000041014, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044084, 900000000000041014, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044085, 900000000000041015, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044086, 900000000000041015, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044087, 900000000000041015, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044088, 900000000000041015, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044089, 900000000000041015, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044090, 900000000000041015, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044091, 900000000000041016, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044092, 900000000000041016, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044093, 900000000000041016, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044094, 900000000000041016, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044095, 900000000000041016, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044096, 900000000000041016, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044097, 900000000000041017, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044098, 900000000000041017, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044099, 900000000000041017, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044100, 900000000000041017, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044101, 900000000000041017, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044102, 900000000000041017, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000044103, 900000000000041018, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000044104, 900000000000041018, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000044105, 900000000000041018, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000044106, 900000000000041018, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000044107, 900000000000041018, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000044108, 900000000000041018, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

-- Client API management: dormant registration; no Scope, Client or Credential seed.
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000051101, 'API_CREDENTIAL_READ', 'API_CREDENTIAL', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051102, 'API_CREDENTIAL_REVOKE', 'API_CREDENTIAL', 'REVOKE', 'CRITICAL', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051103, 'API_SCOPE_READ', 'API_SCOPE', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051104, 'API_SCOPE_MANAGE', 'API_SCOPE', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000051201, 900000000000000510, 900000000000051101, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051202, 900000000000000510, 900000000000051102, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051203, 900000000000000510, 900000000000051103, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051204, 900000000000000510, 900000000000051104, 'REVOKED', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000051301, 'OPERATIONS_API_CREDENTIALS', 'OPERATIONS', 'MENU', 900000000000000562, 'API 凭证', '/api-credentials', 'OPERATIONS_API_CREDENTIALS', 900000000000051101, 40, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051302, 'OPERATIONS_API_CREDENTIAL_REVOKE', 'OPERATIONS', 'BUTTON', 900000000000051301, '紧急撤销', NULL, NULL, 900000000000051102, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051303, 'OPERATIONS_API_SCOPES', 'OPERATIONS', 'MENU', 900000000000000562, 'API Scope', '/api-scopes', 'OPERATIONS_API_SCOPES', 900000000000051103, 50, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000051304, 'OPERATIONS_API_SCOPE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000051303, '维护 Scope', NULL, NULL, 900000000000051104, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route (route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000051001, 'operations-api-credential-list', 'OPERATIONS', 'GET', '/api/operations/api-credential-admin/v1/credentials', '/api-credential-admin/v1/credentials', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051002, 'operations-api-credential-detail', 'OPERATIONS', 'GET', '/api/operations/api-credential-admin/v1/credentials/{credentialId}', '/api-credential-admin/v1/credentials/{credentialId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051003, 'operations-api-credential-scopes', 'OPERATIONS', 'GET', '/api/operations/api-credential-admin/v1/credentials/{credentialId}/scopes', '/api-credential-admin/v1/credentials/{credentialId}/scopes', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051004, 'operations-api-credential-revoke', 'OPERATIONS', 'POST', '/api/operations/api-credential-admin/v1/credentials/{credentialId}/revoke', '/api-credential-admin/v1/credentials/{credentialId}/revoke', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051005, 'operations-api-scope-list', 'OPERATIONS', 'GET', '/api/operations/api-scope-admin/v1/scopes', '/api-scope-admin/v1/scopes', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051006, 'operations-api-scope-detail', 'OPERATIONS', 'GET', '/api/operations/api-scope-admin/v1/scopes/{scopeCode}', '/api-scope-admin/v1/scopes/{scopeCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051007, 'operations-api-scope-create', 'OPERATIONS', 'POST', '/api/operations/api-scope-admin/v1/scopes', '/api-scope-admin/v1/scopes', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051008, 'operations-api-scope-update', 'OPERATIONS', 'PUT', '/api/operations/api-scope-admin/v1/scopes/{scopeCode}', '/api-scope-admin/v1/scopes/{scopeCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000051009, 'operations-api-scope-options', 'OPERATIONS', 'GET', '/api/operations/api-scope-admin/v1/scope-options', '/api-scope-admin/v1/scope-options', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000534, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000052001, 900000000000051001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052002, 900000000000051001, 'origin', 'FORWARD_REQUEST'),
  (900000000000052003, 900000000000051002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052004, 900000000000051002, 'origin', 'FORWARD_REQUEST'),
  (900000000000052005, 900000000000051003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052006, 900000000000051003, 'origin', 'FORWARD_REQUEST'),
  (900000000000052007, 900000000000051004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052008, 900000000000051004, 'origin', 'FORWARD_REQUEST'),
  (900000000000052009, 900000000000051004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000052010, 900000000000051004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000052011, 900000000000051004, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000052012, 900000000000051004, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000052013, 900000000000051005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052014, 900000000000051005, 'origin', 'FORWARD_REQUEST'),
  (900000000000052015, 900000000000051006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052016, 900000000000051006, 'origin', 'FORWARD_REQUEST'),
  (900000000000052017, 900000000000051007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052018, 900000000000051007, 'origin', 'FORWARD_REQUEST'),
  (900000000000052019, 900000000000051007, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000052020, 900000000000051007, 'content-type', 'FORWARD_REQUEST'),
  (900000000000052021, 900000000000051007, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000052022, 900000000000051007, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000052023, 900000000000051008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052024, 900000000000051008, 'origin', 'FORWARD_REQUEST'),
  (900000000000052025, 900000000000051008, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000052026, 900000000000051008, 'content-type', 'FORWARD_REQUEST'),
  (900000000000052027, 900000000000051008, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000052028, 900000000000051008, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000052029, 900000000000051009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052030, 900000000000051009, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000053001, 900000000000051001, 'page'),
  (900000000000053002, 900000000000051001, 'size'),
  (900000000000053003, 900000000000051001, 'clientId'),
  (900000000000053004, 900000000000051001, 'clientName'),
  (900000000000053005, 900000000000051001, 'credentialId'),
  (900000000000053006, 900000000000051001, 'keyPrefix'),
  (900000000000053007, 900000000000051001, 'status'),
  (900000000000053008, 900000000000051001, 'scopeCode'),
  (900000000000053009, 900000000000051003, 'page'),
  (900000000000053010, 900000000000051003, 'size'),
  (900000000000053011, 900000000000051005, 'page'),
  (900000000000053012, 900000000000051005, 'size'),
  (900000000000053013, 900000000000051005, 'keyword'),
  (900000000000053014, 900000000000051006, 'referenceKind'),
  (900000000000053015, 900000000000051006, 'page'),
  (900000000000053016, 900000000000051006, 'size'),
  (900000000000053017, 900000000000051009, 'page'),
  (900000000000053018, 900000000000051009, 'size'),
  (900000000000053019, 900000000000051009, 'keyword'),
  (900000000000053020, 900000000000001021, 'requiredScopeCode'),
  (900000000000053021, 900000000000001009, 'resourceType'),
  (900000000000053022, 900000000000001009, 'resourceId');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000054001, 900000000000051001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054002, 900000000000051001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054003, 900000000000051001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054004, 900000000000051001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054005, 900000000000051001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054006, 900000000000051001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054007, 900000000000051002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054008, 900000000000051002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054009, 900000000000051002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054010, 900000000000051002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054011, 900000000000051002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054012, 900000000000051002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054013, 900000000000051003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054014, 900000000000051003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054015, 900000000000051003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054016, 900000000000051003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054017, 900000000000051003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054018, 900000000000051003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054019, 900000000000051004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054020, 900000000000051004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054021, 900000000000051004, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054022, 900000000000051004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054023, 900000000000051004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054024, 900000000000051004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054025, 900000000000051005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054026, 900000000000051005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054027, 900000000000051005, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054028, 900000000000051005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054029, 900000000000051005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054030, 900000000000051005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054031, 900000000000051006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054032, 900000000000051006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054033, 900000000000051006, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054034, 900000000000051006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054035, 900000000000051006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054036, 900000000000051006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054037, 900000000000051007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054038, 900000000000051007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054039, 900000000000051007, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054040, 900000000000051007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054041, 900000000000051007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054042, 900000000000051007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054043, 900000000000051008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054044, 900000000000051008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054045, 900000000000051008, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054046, 900000000000051008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054047, 900000000000051008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054048, 900000000000051008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054049, 900000000000051009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054050, 900000000000051009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054051, 900000000000051009, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054052, 900000000000051009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054053, 900000000000051009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054054, 900000000000051009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);



-- Tenant access management: dormant registration; no Tenant, Account or Member seed.
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000061101, 'TENANT_ACCESS_READ', 'ACCESS_TENANT', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000061102, 'TENANT_ACCESS_MANAGE', 'ACCESS_TENANT', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000061103, 'TENANT_MEMBER_READ', 'TENANT_MEMBER', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000061201, 900000000000000510, 900000000000061101, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000061202, 900000000000000510, 900000000000061102, 'REVOKED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000061203, 900000000000000510, 900000000000061103, 'REVOKED', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000061301, 'OPERATIONS_TENANT_ACCESS', 'OPERATIONS', 'MENU', NULL, '租户访问', '/tenant-access', 'OPERATIONS_TENANT_ACCESS', 900000000000061101, 25, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000061302, 'OPERATIONS_TENANT_ACCESS_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000061301, '安全暂停与恢复', NULL, NULL, 900000000000061102, 10, 'DISABLED', 1, @platform_initial_time, @platform_initial_time),
  (900000000000061303, 'OPERATIONS_TENANT_MEMBER_READ', 'OPERATIONS', 'BUTTON', 900000000000061301, '查看成员权限', NULL, NULL, 900000000000061103, 20, 'DISABLED', 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route (route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000061001, 'operations-tenant-access-list', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants', '/tenant-access-admin/v1/tenants', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061002, 'operations-tenant-access-detail', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}', '/tenant-access-admin/v1/tenants/{tenantId}', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061003, 'operations-tenant-access-security-status', 'OPERATIONS', 'POST', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/security-status', '/tenant-access-admin/v1/tenants/{tenantId}/security-status', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061004, 'operations-tenant-access-members', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members', '/tenant-access-admin/v1/tenants/{tenantId}/members', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061005, 'operations-tenant-access-member-detail', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061006, 'operations-tenant-access-member-roles', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/roles', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/roles', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061007, 'operations-tenant-access-member-permissions', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time),
  (900000000000061008, 'operations-tenant-access-permission-sources', 'OPERATIONS', 'GET', '/api/operations/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions/{permissionId}/sources', '/tenant-access-admin/v1/tenants/{tenantId}/members/{memberId}/permissions/{permissionId}/sources', @operations_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000061103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @platform_initial_actor_id, @platform_initial_actor_id, @platform_initial_time, @platform_initial_time);

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


COMMIT;

-- RCO-16 待发布权限绑定状态修正：与已审阅本机补丁一致，7 条绑定以 REVOKED/version=2 初始化。
-- 角色版本额外递增一次；开放对应 Route 时由人工发布 SQL 同事务激活绑定。
UPDATE t_role SET version=version+1 WHERE role_id=900000000000000510;

-- OPTIONAL OPERATOR-ONLY BOOTSTRAP (deliberately commented; never run by Java).
-- After supplying a UNIQUE strong password through a secure offline Argon2id tool,
-- replace the PHC placeholder and execute this complete transaction manually.
-- Never store the plaintext password or private key in SQL, this repository or logs.

-- START TRANSACTION;
-- INSERT INTO t_user_account(account_id,realm,account_type,login_name,status,consecutive_password_failures,
--   credential_version,auth_epoch,version,create_time,update_time)
-- VALUES(900000000000009001,'OPERATIONS','BOOTSTRAP_ADMIN','admin','ACTIVE',0,1,1,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
-- INSERT INTO t_password_credential(password_credential_id,account_id,password_verifier_phc,algorithm_code,
--   algorithm_version,memory_kib,iterations,parallelism,credential_version,force_change_flag,status,
--   password_changed_time,version,create_time,update_time)
-- VALUES(900000000000009002,900000000000009001,'<operator-generated Argon2id PHC>',
--   'ARGON2ID',19,19456,2,1,1,1,'ACTIVE',UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
-- COMMIT;
-- The ACTIVE key must match the externally injected local RSA private key.
-- Replace BOTH kid occurrences and the full public JWK. Public JWK shape is exactly
-- {"kty":"RSA","use":"sig","alg":"RS256","key_ops":["verify"],"kid":"<kid>","n":"<base64url>","e":"AQAB"}.
-- INSERT INTO t_jwt_signing_key(signing_key_id,realm,kid,algorithm,kms_key_ref,
--   public_jwk_schema_version,public_jwk_canonical_json,status,active_slot,not_before,version,create_time,update_time)
-- VALUES(900000000000009003,'OPERATIONS','<kid>','RS256','development:operations:<kid>',1,
--   '<operator-generated public JWK>','ACTIVE',1,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));

-- ---------------------------------------------------------------------------
-- Client authentication email (2026-10-15, requirements/20261015/platform-support-认证邮件接入Delivery).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- SES_PRIMARY is created with its region only: the sender address and the IAM key pair are entered in the
-- Operations console (requirements/20261015/operations-console-通知发送账号维护). Keep
-- notification.transient-template-codes = [CLIENT_EMAIL_VERIFICATION, CLIENT_PASSWORD_RESET].
-- ${link} is supplied in memory per send and is never stored. Rollback: delete the rows by the IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @notification_initial_time = UTC_TIMESTAMP(6);
SET @notification_ses_provider_id = 900000000000070001;
SET @client_email_verification_template_id = 900000000000070011;
SET @client_password_reset_template_id = 900000000000070012;

INSERT INTO t_notification_provider (
    provider_id, provider_code, medium, provider_type, status, region, version, create_time, update_time
) VALUES (
    @notification_ses_provider_id, 'SES_PRIMARY', 'EMAIL', 'AMAZON_SES', 'ACTIVE', 'me-central-1', 1,
    @notification_initial_time, @notification_initial_time
);

INSERT INTO t_notification_template (
    template_id, template_code, medium, status, version, create_time, update_time
) VALUES
    (@client_email_verification_template_id, 'CLIENT_EMAIL_VERIFICATION', 'EMAIL', 'ACTIVE', 1,
        @notification_initial_time, @notification_initial_time),
    (@client_password_reset_template_id, 'CLIENT_PASSWORD_RESET', 'EMAIL', 'ACTIVE', 1,
        @notification_initial_time, @notification_initial_time);

INSERT INTO t_notification_template_content (
    content_id, template_id, locale, subject_template, body_template, version, create_time, update_time
) VALUES
    (900000000000070021, @client_email_verification_template_id, 'zh-CN', 'PAYTECH · 验证您的邮箱',
        CONCAT('验证您的邮箱', CHAR(10), CHAR(10), '请打开以下链接，并在页面中确认操作：', CHAR(10), '${link}',
            CHAR(10), CHAR(10), '如果不是您发起的请求，请忽略此邮件。'),
        1, @notification_initial_time, @notification_initial_time),
    (900000000000070022, @client_password_reset_template_id, 'zh-CN', 'PAYTECH · 重置登录密码',
        CONCAT('重置登录密码', CHAR(10), CHAR(10), '请打开以下链接，并在页面中确认操作：', CHAR(10), '${link}',
            CHAR(10), CHAR(10), '如果不是您发起的请求，请忽略此邮件。'),
        1, @notification_initial_time, @notification_initial_time);

INSERT INTO t_notification_route (
    route_id, notification_template_id, primary_provider_id, fallback_provider_id, status, version,
    create_time, update_time
) VALUES
    (900000000000070031, @client_email_verification_template_id, @notification_ses_provider_id, NULL, 'ACTIVE', 1,
        @notification_initial_time, @notification_initial_time),
    (900000000000070032, @client_password_reset_template_id, @notification_ses_provider_id, NULL, 'ACTIVE', 1,
        @notification_initial_time, @notification_initial_time);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations notification management (2026-10-15, requirements/20261015/operations-console-通知管理).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menus/buttons/routes
-- DISABLED; activate them in a separate reviewed change. Requires the t_notification_request create_time index
-- (docs/database/sql/changes/2026-10-15-notification-request-create-time-index.sql).
-- Rows: permission 2, role_permission 2, console_resource 6, gateway_route 12, route_header 52, query_param 14,
-- guard_rule 72 (160). Rollback: delete the rows by the 900000000000086xxx-89xxx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @notification_admin_time = UTC_TIMESTAMP(6);
SET @notification_admin_actor_id = 1;
SET @notification_admin_target_id = 900000000000000001;
SET @notification_admin_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000086101, 'NOTIFICATION_READ', 'NOTIFICATION', 'READ', 'LOW', 'ACTIVE', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086102, 'NOTIFICATION_MANAGE', 'NOTIFICATION', 'MANAGE', 'HIGH', 'ACTIVE', 1, @notification_admin_time, @notification_admin_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000086201, @notification_admin_role_id, 900000000000086101, 'REVOKED', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086202, @notification_admin_role_id, 900000000000086102, 'REVOKED', 1, @notification_admin_time, @notification_admin_time);
INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000086301, 'OPERATIONS_NOTIFICATION', 'OPERATIONS', 'MENU', NULL, '通知管理', NULL, NULL, 900000000000086101, 40, 'DISABLED', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086302, 'OPERATIONS_NOTIFICATION_TEMPLATES', 'OPERATIONS', 'MENU', 900000000000086301, '通知模板', '/notification-templates', 'OPERATIONS_NOTIFICATION_TEMPLATES', 900000000000086101, 10, 'DISABLED', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086303, 'OPERATIONS_NOTIFICATION_PROVIDERS', 'OPERATIONS', 'MENU', 900000000000086301, '发送账号', '/notification-providers', 'OPERATIONS_NOTIFICATION_PROVIDERS', 900000000000086101, 20, 'DISABLED', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086304, 'OPERATIONS_NOTIFICATION_RECORDS', 'OPERATIONS', 'MENU', 900000000000086301, '发送记录', '/notification-records', 'OPERATIONS_NOTIFICATION_RECORDS', 900000000000086101, 30, 'DISABLED', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086305, 'OPERATIONS_NOTIFICATION_TEMPLATE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000086302, '维护模板', NULL, NULL, 900000000000086102, 10, 'DISABLED', 1, @notification_admin_time, @notification_admin_time),
  (900000000000086306, 'OPERATIONS_NOTIFICATION_PROVIDER_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000086303, '启停账号', NULL, NULL, 900000000000086102, 10, 'DISABLED', 1, @notification_admin_time, @notification_admin_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000086001, 'operations-notification-list-templates', 'OPERATIONS', 'GET', '/api/operations/notification-admin/v1/templates', '/notification-admin/v1/templates', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086002, 'operations-notification-template-detail', 'OPERATIONS', 'GET', '/api/operations/notification-admin/v1/templates/{templateCode}', '/notification-admin/v1/templates/{templateCode}', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086003, 'operations-notification-create-template', 'OPERATIONS', 'POST', '/api/operations/notification-admin/v1/templates', '/notification-admin/v1/templates', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086004, 'operations-notification-template-status', 'OPERATIONS', 'PATCH', '/api/operations/notification-admin/v1/templates/{templateCode}/status', '/notification-admin/v1/templates/{templateCode}/status', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086005, 'operations-notification-save-content', 'OPERATIONS', 'PUT', '/api/operations/notification-admin/v1/templates/{templateCode}/contents/{locale}', '/notification-admin/v1/templates/{templateCode}/contents/{locale}', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086006, 'operations-notification-preview', 'OPERATIONS', 'POST', '/api/operations/notification-admin/v1/template-preview', '/notification-admin/v1/template-preview', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086007, 'operations-notification-save-route', 'OPERATIONS', 'PUT', '/api/operations/notification-admin/v1/templates/{templateCode}/route', '/notification-admin/v1/templates/{templateCode}/route', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086008, 'operations-notification-route-status', 'OPERATIONS', 'PATCH', '/api/operations/notification-admin/v1/templates/{templateCode}/route/status', '/notification-admin/v1/templates/{templateCode}/route/status', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086009, 'operations-notification-list-providers', 'OPERATIONS', 'GET', '/api/operations/notification-admin/v1/providers', '/notification-admin/v1/providers', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086010, 'operations-notification-provider-status', 'OPERATIONS', 'PATCH', '/api/operations/notification-admin/v1/providers/{providerCode}/status', '/notification-admin/v1/providers/{providerCode}/status', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086011, 'operations-notification-list-requests', 'OPERATIONS', 'GET', '/api/operations/notification-admin/v1/requests', '/notification-admin/v1/requests', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time),
  (900000000000086012, 'operations-notification-request-detail', 'OPERATIONS', 'GET', '/api/operations/notification-admin/v1/requests/{requestId}', '/notification-admin/v1/requests/{requestId}', @notification_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @notification_admin_actor_id, @notification_admin_actor_id, @notification_admin_time, @notification_admin_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000087001, 900000000000086001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087002, 900000000000086001, 'origin', 'FORWARD_REQUEST'),
  (900000000000087003, 900000000000086002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087004, 900000000000086002, 'origin', 'FORWARD_REQUEST'),
  (900000000000087005, 900000000000086003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087006, 900000000000086003, 'origin', 'FORWARD_REQUEST'),
  (900000000000087007, 900000000000086003, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087008, 900000000000086003, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087009, 900000000000086003, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087010, 900000000000086003, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087011, 900000000000086004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087012, 900000000000086004, 'origin', 'FORWARD_REQUEST'),
  (900000000000087013, 900000000000086004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087014, 900000000000086004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087015, 900000000000086004, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087016, 900000000000086004, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087017, 900000000000086005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087018, 900000000000086005, 'origin', 'FORWARD_REQUEST'),
  (900000000000087019, 900000000000086005, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087020, 900000000000086005, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087021, 900000000000086005, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087022, 900000000000086005, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087023, 900000000000086006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087024, 900000000000086006, 'origin', 'FORWARD_REQUEST'),
  (900000000000087025, 900000000000086006, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087026, 900000000000086006, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087027, 900000000000086006, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087028, 900000000000086006, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087029, 900000000000086007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087030, 900000000000086007, 'origin', 'FORWARD_REQUEST'),
  (900000000000087031, 900000000000086007, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087032, 900000000000086007, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087033, 900000000000086007, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087034, 900000000000086007, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087035, 900000000000086008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087036, 900000000000086008, 'origin', 'FORWARD_REQUEST'),
  (900000000000087037, 900000000000086008, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087038, 900000000000086008, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087039, 900000000000086008, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087040, 900000000000086008, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087041, 900000000000086009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087042, 900000000000086009, 'origin', 'FORWARD_REQUEST'),
  (900000000000087043, 900000000000086010, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087044, 900000000000086010, 'origin', 'FORWARD_REQUEST'),
  (900000000000087045, 900000000000086010, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000087046, 900000000000086010, 'content-type', 'FORWARD_REQUEST'),
  (900000000000087047, 900000000000086010, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000087048, 900000000000086010, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000087049, 900000000000086011, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087050, 900000000000086011, 'origin', 'FORWARD_REQUEST'),
  (900000000000087051, 900000000000086012, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000087052, 900000000000086012, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000088001, 900000000000086001, 'page'),
  (900000000000088002, 900000000000086001, 'size'),
  (900000000000088003, 900000000000086001, 'keyword'),
  (900000000000088004, 900000000000086001, 'status'),
  (900000000000088005, 900000000000086009, 'page'),
  (900000000000088006, 900000000000086009, 'size'),
  (900000000000088007, 900000000000086011, 'page'),
  (900000000000088008, 900000000000086011, 'size'),
  (900000000000088009, 900000000000086011, 'sourceSystem'),
  (900000000000088010, 900000000000086011, 'sourceEventKey'),
  (900000000000088011, 900000000000086011, 'templateCode'),
  (900000000000088012, 900000000000086011, 'deliveryStatus'),
  (900000000000088013, 900000000000086011, 'createdFrom'),
  (900000000000088014, 900000000000086011, 'createdTo');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000089001, 900000000000086001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089002, 900000000000086001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089003, 900000000000086001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089004, 900000000000086001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089005, 900000000000086001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089006, 900000000000086001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089007, 900000000000086002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089008, 900000000000086002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089009, 900000000000086002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089010, 900000000000086002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089011, 900000000000086002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089012, 900000000000086002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089013, 900000000000086003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089014, 900000000000086003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089015, 900000000000086003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089016, 900000000000086003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089017, 900000000000086003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089018, 900000000000086003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089019, 900000000000086004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089020, 900000000000086004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089021, 900000000000086004, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089022, 900000000000086004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089023, 900000000000086004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089024, 900000000000086004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089025, 900000000000086005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089026, 900000000000086005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089027, 900000000000086005, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089028, 900000000000086005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089029, 900000000000086005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089030, 900000000000086005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089031, 900000000000086006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089032, 900000000000086006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089033, 900000000000086006, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089034, 900000000000086006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089035, 900000000000086006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089036, 900000000000086006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089037, 900000000000086007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089038, 900000000000086007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089039, 900000000000086007, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089040, 900000000000086007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089041, 900000000000086007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089042, 900000000000086007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089043, 900000000000086008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089044, 900000000000086008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089045, 900000000000086008, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089046, 900000000000086008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089047, 900000000000086008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089048, 900000000000086008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089049, 900000000000086009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089050, 900000000000086009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089051, 900000000000086009, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089052, 900000000000086009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089053, 900000000000086009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089054, 900000000000086009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089055, 900000000000086010, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089056, 900000000000086010, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089057, 900000000000086010, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089058, 900000000000086010, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089059, 900000000000086010, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089060, 900000000000086010, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089061, 900000000000086011, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089062, 900000000000086011, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089063, 900000000000086011, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089064, 900000000000086011, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089065, 900000000000086011, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089066, 900000000000086011, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000089067, 900000000000086012, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000089068, 900000000000086012, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000089069, 900000000000086012, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000089070, 900000000000086012, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000089071, 900000000000086012, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000089072, 900000000000086012, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Client Webhook management permissions (2026-10-15, requirements/20261015/platform-support-Webhook投递).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED. Console menus, buttons
-- and Gateway routes belong to the later Operations page requirement (reserved IDs 900000000000090300+).
-- Rows: permission 2, role_permission 2 (4). Rollback: delete the rows by the 9000000000000901xx/902xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @webhook_admin_time = UTC_TIMESTAMP(6);
SET @webhook_admin_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000090101, 'WEBHOOK_READ', 'WEBHOOK', 'READ', 'LOW', 'ACTIVE', 1, @webhook_admin_time, @webhook_admin_time),
  (900000000000090102, 'WEBHOOK_MANAGE', 'WEBHOOK', 'MANAGE', 'HIGH', 'ACTIVE', 1, @webhook_admin_time, @webhook_admin_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000090201, @webhook_admin_role_id, 900000000000090101, 'REVOKED', 1, @webhook_admin_time, @webhook_admin_time),
  (900000000000090202, @webhook_admin_role_id, 900000000000090102, 'REVOKED', 1, @webhook_admin_time, @webhook_admin_time);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations notification provider maintenance (2026-10-15, requirements/20261015/operations-console-通知发送账号维护).
-- Self-contained block: on an environment already initialized by the statements above (including the
-- notification management block), apply ONLY this block. Requires the t_notification_provider account columns
-- (docs/database/sql/changes/2026-10-15-notification-provider-account-fields.sql).
-- Dormant: permission ACTIVE, administrator grant REVOKED, buttons/routes DISABLED; activate separately.
-- Create/update reuse NOTIFICATION_MANAGE; credential replacement and test send need NOTIFICATION_CREDENTIAL_MANAGE.
-- Rows: permission 1, role_permission 1, console_resource 2, gateway_route 4, route_header 24, guard_rule 24 (56).
-- Rollback: delete the rows by the 900000000000096xxx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @provider_admin_time = UTC_TIMESTAMP(6);
SET @provider_admin_actor_id = 1;
SET @provider_admin_target_id = 900000000000000001;
SET @provider_admin_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000096101, 'NOTIFICATION_CREDENTIAL_MANAGE', 'NOTIFICATION', 'CREDENTIAL_MANAGE', 'HIGH', 'ACTIVE', 1, @provider_admin_time, @provider_admin_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000096201, @provider_admin_role_id, 900000000000096101, 'REVOKED', 1, @provider_admin_time, @provider_admin_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000096301, 'OPERATIONS_NOTIFICATION_PROVIDER_EDIT', 'OPERATIONS', 'BUTTON', 900000000000086303, '维护账号', NULL, NULL, 900000000000086102, 20, 'DISABLED', 1, @provider_admin_time, @provider_admin_time),
  (900000000000096302, 'OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL', 'OPERATIONS', 'BUTTON', 900000000000086303, '密钥与测试发送', NULL, NULL, 900000000000096101, 30, 'DISABLED', 1, @provider_admin_time, @provider_admin_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000096001, 'operations-notification-create-provider', 'OPERATIONS', 'POST', '/api/operations/notification-admin/v1/providers', '/notification-admin/v1/providers', @provider_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @provider_admin_actor_id, @provider_admin_actor_id, @provider_admin_time, @provider_admin_time),
  (900000000000096002, 'operations-notification-update-provider', 'OPERATIONS', 'PUT', '/api/operations/notification-admin/v1/providers/{providerCode}', '/notification-admin/v1/providers/{providerCode}', @provider_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000086102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @provider_admin_actor_id, @provider_admin_actor_id, @provider_admin_time, @provider_admin_time),
  (900000000000096003, 'operations-notification-provider-credential', 'OPERATIONS', 'PUT', '/api/operations/notification-admin/v1/providers/{providerCode}/credential', '/notification-admin/v1/providers/{providerCode}/credential', @provider_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000096101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @provider_admin_actor_id, @provider_admin_actor_id, @provider_admin_time, @provider_admin_time),
  (900000000000096004, 'operations-notification-provider-test-send', 'OPERATIONS', 'POST', '/api/operations/notification-admin/v1/providers/{providerCode}/test-send', '/notification-admin/v1/providers/{providerCode}/test-send', @provider_admin_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000096101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @provider_admin_actor_id, @provider_admin_actor_id, @provider_admin_time, @provider_admin_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000096401, 900000000000096001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000096402, 900000000000096001, 'origin', 'FORWARD_REQUEST'),
  (900000000000096403, 900000000000096001, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000096404, 900000000000096001, 'content-type', 'FORWARD_REQUEST'),
  (900000000000096405, 900000000000096001, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000096406, 900000000000096001, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000096407, 900000000000096002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000096408, 900000000000096002, 'origin', 'FORWARD_REQUEST'),
  (900000000000096409, 900000000000096002, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000096410, 900000000000096002, 'content-type', 'FORWARD_REQUEST'),
  (900000000000096411, 900000000000096002, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000096412, 900000000000096002, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000096413, 900000000000096003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000096414, 900000000000096003, 'origin', 'FORWARD_REQUEST'),
  (900000000000096415, 900000000000096003, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000096416, 900000000000096003, 'content-type', 'FORWARD_REQUEST'),
  (900000000000096417, 900000000000096003, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000096418, 900000000000096003, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000096419, 900000000000096004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000096420, 900000000000096004, 'origin', 'FORWARD_REQUEST'),
  (900000000000096421, 900000000000096004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000096422, 900000000000096004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000096423, 900000000000096004, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000096424, 900000000000096004, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000096501, 900000000000096001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000096502, 900000000000096001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000096503, 900000000000096001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000096504, 900000000000096001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000096505, 900000000000096001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000096506, 900000000000096001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000096507, 900000000000096002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000096508, 900000000000096002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000096509, 900000000000096002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000096510, 900000000000096002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000096511, 900000000000096002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000096512, 900000000000096002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000096513, 900000000000096003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000096514, 900000000000096003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000096515, 900000000000096003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000096516, 900000000000096003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000096517, 900000000000096003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000096518, 900000000000096003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000096519, 900000000000096004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000096520, 900000000000096004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000096521, 900000000000096004, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000096522, 900000000000096004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000096523, 900000000000096004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 5, 5, 60000, NULL),
  (900000000000096524, 900000000000096004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);


-- Client Console 接入自助（2026-10-16）：CLIENT 工作区开发者权限；首次 ACTIVE 投影时授予该工作区 TENANT_OWNER 角色。
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000058101, 'CLIENT_API_CREDENTIAL_READ', 'CLIENT_API_CREDENTIAL', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000058102, 'CLIENT_API_CREDENTIAL_MANAGE', 'CLIENT_API_CREDENTIAL', 'MANAGE', 'CRITICAL', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000058103, 'CLIENT_WEBHOOK_READ', 'CLIENT_WEBHOOK', 'READ', 'LOW', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time),
  (900000000000058104, 'CLIENT_WEBHOOK_MANAGE', 'CLIENT_WEBHOOK', 'MANAGE', 'HIGH', 'ACTIVE', 1, @platform_initial_time, @platform_initial_time);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations Webhook management console (2026-10-16, requirements/20261016/operations-console-Webhook管理).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the Client Webhook management permissions block (WEBHOOK_READ/MANAGE, 9000000000000901xx/902xx).
-- Dormant like earlier Operations features: menu, button and routes DISABLED; activate them in a separate
-- reviewed change. Rows: console_resource 2, gateway_route 10, route_header 44, query_param 12, guard_rule 60 (128).
-- Rollback: delete the rows by the 9000000000000903xx-907xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @webhook_console_time = UTC_TIMESTAMP(6);
SET @webhook_console_actor_id = 1;
SET @webhook_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000090301, 'OPERATIONS_WEBHOOKS', 'OPERATIONS', 'MENU', 900000000000000562, 'Webhook 管理', '/webhooks', 'OPERATIONS_WEBHOOKS', 900000000000090101, 60, 'DISABLED', 1, @webhook_console_time, @webhook_console_time),
  (900000000000090302, 'OPERATIONS_WEBHOOK_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000090301, '维护', NULL, NULL, 900000000000090102, 10, 'DISABLED', 1, @webhook_console_time, @webhook_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000090401, 'operations-webhook-list-clients', 'OPERATIONS', 'GET', '/api/operations/webhook-admin/v1/clients', '/webhook-admin/v1/clients', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090402, 'operations-webhook-list-endpoints', 'OPERATIONS', 'GET', '/api/operations/webhook-admin/v1/clients/{clientId}/endpoints', '/webhook-admin/v1/clients/{clientId}/endpoints', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090403, 'operations-webhook-create-endpoint', 'OPERATIONS', 'POST', '/api/operations/webhook-admin/v1/clients/{clientId}/endpoints', '/webhook-admin/v1/clients/{clientId}/endpoints', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090404, 'operations-webhook-update-endpoint', 'OPERATIONS', 'PUT', '/api/operations/webhook-admin/v1/endpoints/{endpointId}', '/webhook-admin/v1/endpoints/{endpointId}', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090405, 'operations-webhook-endpoint-status', 'OPERATIONS', 'PATCH', '/api/operations/webhook-admin/v1/endpoints/{endpointId}/status', '/webhook-admin/v1/endpoints/{endpointId}/status', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090406, 'operations-webhook-rotate-secret', 'OPERATIONS', 'POST', '/api/operations/webhook-admin/v1/endpoints/{endpointId}/signing-secret/rotations', '/webhook-admin/v1/endpoints/{endpointId}/signing-secret/rotations', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090407, 'operations-webhook-test-endpoint', 'OPERATIONS', 'POST', '/api/operations/webhook-admin/v1/endpoints/{endpointId}/tests', '/webhook-admin/v1/endpoints/{endpointId}/tests', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090408, 'operations-webhook-list-deliveries', 'OPERATIONS', 'GET', '/api/operations/webhook-admin/v1/deliveries', '/webhook-admin/v1/deliveries', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090409, 'operations-webhook-delivery-detail', 'OPERATIONS', 'GET', '/api/operations/webhook-admin/v1/deliveries/{deliveryId}', '/webhook-admin/v1/deliveries/{deliveryId}', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time),
  (900000000000090410, 'operations-webhook-replay-delivery', 'OPERATIONS', 'POST', '/api/operations/webhook-admin/v1/deliveries/{deliveryId}/replays', '/webhook-admin/v1/deliveries/{deliveryId}/replays', @webhook_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000090102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @webhook_console_actor_id, @webhook_console_actor_id, @webhook_console_time, @webhook_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000090501, 900000000000090401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090502, 900000000000090401, 'origin', 'FORWARD_REQUEST'),
  (900000000000090503, 900000000000090402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090504, 900000000000090402, 'origin', 'FORWARD_REQUEST'),
  (900000000000090505, 900000000000090403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090506, 900000000000090403, 'origin', 'FORWARD_REQUEST'),
  (900000000000090507, 900000000000090403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000090508, 900000000000090403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000090509, 900000000000090403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000090510, 900000000000090403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000090511, 900000000000090404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090512, 900000000000090404, 'origin', 'FORWARD_REQUEST'),
  (900000000000090513, 900000000000090404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000090514, 900000000000090404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000090515, 900000000000090404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000090516, 900000000000090404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000090517, 900000000000090405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090518, 900000000000090405, 'origin', 'FORWARD_REQUEST'),
  (900000000000090519, 900000000000090405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000090520, 900000000000090405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000090521, 900000000000090405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000090522, 900000000000090405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000090523, 900000000000090406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090524, 900000000000090406, 'origin', 'FORWARD_REQUEST'),
  (900000000000090525, 900000000000090406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000090526, 900000000000090406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000090527, 900000000000090406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000090528, 900000000000090406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000090529, 900000000000090407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090530, 900000000000090407, 'origin', 'FORWARD_REQUEST'),
  (900000000000090531, 900000000000090407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000090532, 900000000000090407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000090533, 900000000000090407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000090534, 900000000000090407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000090535, 900000000000090408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090536, 900000000000090408, 'origin', 'FORWARD_REQUEST'),
  (900000000000090537, 900000000000090409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090538, 900000000000090409, 'origin', 'FORWARD_REQUEST'),
  (900000000000090539, 900000000000090410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000090540, 900000000000090410, 'origin', 'FORWARD_REQUEST'),
  (900000000000090541, 900000000000090410, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000090542, 900000000000090410, 'content-type', 'FORWARD_REQUEST'),
  (900000000000090543, 900000000000090410, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000090544, 900000000000090410, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000090601, 900000000000090401, 'keyword'),
  (900000000000090602, 900000000000090401, 'page'),
  (900000000000090603, 900000000000090401, 'size'),
  (900000000000090604, 900000000000090408, 'clientId'),
  (900000000000090605, 900000000000090408, 'createdFrom'),
  (900000000000090606, 900000000000090408, 'createdTo'),
  (900000000000090607, 900000000000090408, 'page'),
  (900000000000090608, 900000000000090408, 'size'),
  (900000000000090609, 900000000000090408, 'endpointId'),
  (900000000000090610, 900000000000090408, 'eventType'),
  (900000000000090611, 900000000000090408, 'status'),
  (900000000000090612, 900000000000090408, 'eventId');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000090701, 900000000000090401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090702, 900000000000090401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090703, 900000000000090401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090704, 900000000000090401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090705, 900000000000090401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090706, 900000000000090401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090707, 900000000000090402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090708, 900000000000090402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090709, 900000000000090402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090710, 900000000000090402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090711, 900000000000090402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090712, 900000000000090402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090713, 900000000000090403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090714, 900000000000090403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090715, 900000000000090403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090716, 900000000000090403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090717, 900000000000090403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090718, 900000000000090403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090719, 900000000000090404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090720, 900000000000090404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090721, 900000000000090404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090722, 900000000000090404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090723, 900000000000090404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090724, 900000000000090404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090725, 900000000000090405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090726, 900000000000090405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090727, 900000000000090405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090728, 900000000000090405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090729, 900000000000090405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090730, 900000000000090405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090731, 900000000000090406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090732, 900000000000090406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090733, 900000000000090406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090734, 900000000000090406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090735, 900000000000090406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090736, 900000000000090406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090737, 900000000000090407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090738, 900000000000090407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090739, 900000000000090407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090740, 900000000000090407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090741, 900000000000090407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090742, 900000000000090407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090743, 900000000000090408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090744, 900000000000090408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090745, 900000000000090408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090746, 900000000000090408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090747, 900000000000090408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090748, 900000000000090408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090749, 900000000000090409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090750, 900000000000090409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090751, 900000000000090409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090752, 900000000000090409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090753, 900000000000090409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090754, 900000000000090409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000090755, 900000000000090410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000090756, 900000000000090410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000090757, 900000000000090410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000090758, 900000000000090410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000090759, 900000000000090410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000090760, 900000000000090410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Client Console team members and invitations (2026-10-17, requirements/20261017/client-console-团队成员与邀请).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the Client authentication email block (SES_PRIMARY, 900000000000070001).
-- Team permissions are granted per tenant by the first ACTIVE projection (four system roles, ClientTeamPermissions);
-- already-activated tenants are backfilled by the requirement's db/dml.sql, not here.
-- Keep notification.transient-template-codes including CLIENT_TENANT_INVITATION. ${link} is supplied in memory.
-- Rollback: delete the rows by the IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @client_team_initial_time = UTC_TIMESTAMP(6);
SET @client_tenant_invitation_template_id = 900000000000070013;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000058105, 'CLIENT_TEAM_READ', 'CLIENT_TEAM', 'READ', 'LOW', 'ACTIVE', 1, @client_team_initial_time, @client_team_initial_time),
  (900000000000058106, 'CLIENT_TEAM_MANAGE', 'CLIENT_TEAM', 'MANAGE', 'HIGH', 'ACTIVE', 1, @client_team_initial_time, @client_team_initial_time);

INSERT INTO t_notification_template (
    template_id, template_code, medium, status, version, create_time, update_time
) VALUES
    (@client_tenant_invitation_template_id, 'CLIENT_TENANT_INVITATION', 'EMAIL', 'ACTIVE', 1,
        @client_team_initial_time, @client_team_initial_time);

INSERT INTO t_notification_template_content (
    content_id, template_id, locale, subject_template, body_template, version, create_time, update_time
) VALUES
    (900000000000070023, @client_tenant_invitation_template_id, 'zh-CN', 'PAYTECH · 邀请您加入工作区',
        CONCAT('邀请您加入工作区', CHAR(10), CHAR(10), '您被邀请加入一个 PAYTECH Client 工作区。请打开以下链接，',
            '使用收到本邮件的邮箱登录或注册后接受邀请：', CHAR(10), '${link}', CHAR(10), CHAR(10),
            '邀请 7 天内有效。如果您不认识发起人，请忽略此邮件。'),
        1, @client_team_initial_time, @client_team_initial_time);

INSERT INTO t_notification_route (
    route_id, notification_template_id, primary_provider_id, fallback_provider_id, status, version,
    create_time, update_time
) VALUES
    (900000000000070033, @client_tenant_invitation_template_id, 900000000000070001, NULL, 'ACTIVE', 1,
        @client_team_initial_time, @client_team_initial_time);


-- 全平台参考数据（D41）：与 finance-initial-data.sql 相同的 USD 币种规则；固定 ID 1001 低于 Snowflake 范围。
INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance setup and ledger query console (2026-10-18, requirements/20261018/operations-console-财务基础资料与账本余额查询).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menus/button/routes
-- DISABLED; activate them in a separate reviewed change. BFF routes forward to ledger through operations-console-bff.
-- Rows: permission 2, role_permission 2, console_resource 5, gateway_route 16, route_header 56, query_param 27,
-- guard_rule 96 (204). Rollback: delete the rows by the 9000000000000971xx-977xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_console_time = UTC_TIMESTAMP(6);
SET @finance_console_actor_id = 1;
SET @finance_console_target_id = 900000000000000001;
SET @finance_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000097101, 'FINANCE_ACCOUNTING_READ', 'FINANCE_ACCOUNTING', 'READ', 'LOW', 'ACTIVE', 1, @finance_console_time, @finance_console_time),
  (900000000000097102, 'FINANCE_ACCOUNTING_MANAGE', 'FINANCE_ACCOUNTING', 'MANAGE', 'HIGH', 'ACTIVE', 1, @finance_console_time, @finance_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000097201, @finance_console_role_id, 900000000000097101, 'REVOKED', 1, @finance_console_time, @finance_console_time),
  (900000000000097202, @finance_console_role_id, 900000000000097102, 'REVOKED', 1, @finance_console_time, @finance_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000097301, 'OPERATIONS_FINANCE', 'OPERATIONS', 'MENU', NULL, '财务', NULL, NULL, 900000000000097101, 50, 'DISABLED', 1, @finance_console_time, @finance_console_time),
  (900000000000097302, 'OPERATIONS_FINANCE_SETUP', 'OPERATIONS', 'MENU', 900000000000097301, '账务基础资料', '/finance/setup', 'OPERATIONS_FINANCE_SETUP', 900000000000097101, 10, 'DISABLED', 1, @finance_console_time, @finance_console_time),
  (900000000000097303, 'OPERATIONS_FINANCE_LEDGER', 'OPERATIONS', 'MENU', 900000000000097301, '账本查询', '/finance/ledger', 'OPERATIONS_FINANCE_LEDGER', 900000000000097101, 20, 'DISABLED', 1, @finance_console_time, @finance_console_time),
  (900000000000097304, 'OPERATIONS_FINANCE_BALANCES', 'OPERATIONS', 'MENU', 900000000000097301, '余额查询', '/finance/balances', 'OPERATIONS_FINANCE_BALANCES', 900000000000097101, 30, 'DISABLED', 1, @finance_console_time, @finance_console_time),
  (900000000000097305, 'OPERATIONS_FINANCE_SETUP_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000097302, '维护', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_console_time, @finance_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000097401, 'operations-finance-entity-list', 'OPERATIONS', 'GET', '/api/operations/finance/finance-entities/v1', '/finance/finance-entities/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097402, 'operations-finance-entity-create', 'OPERATIONS', 'POST', '/api/operations/finance/finance-entities/v1', '/finance/finance-entities/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097403, 'operations-finance-calendar-list', 'OPERATIONS', 'GET', '/api/operations/finance/fiscal-calendars/v1', '/finance/fiscal-calendars/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097404, 'operations-finance-calendar-create', 'OPERATIONS', 'POST', '/api/operations/finance/fiscal-calendars/v1', '/finance/fiscal-calendars/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097405, 'operations-finance-period-list', 'OPERATIONS', 'GET', '/api/operations/finance/fiscal-calendars/v1/{fiscalCalendarId}/periods', '/finance/fiscal-calendars/v1/{fiscalCalendarId}/periods', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097406, 'operations-finance-period-create', 'OPERATIONS', 'POST', '/api/operations/finance/fiscal-calendars/v1/{fiscalCalendarId}/periods', '/finance/fiscal-calendars/v1/{fiscalCalendarId}/periods', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097407, 'operations-finance-period-open', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/open', '/finance/accounting-periods/v1/{accountingPeriodId}/open', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097408, 'operations-finance-book-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1', '/finance/ledger-books/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097409, 'operations-finance-book-create', 'OPERATIONS', 'POST', '/api/operations/finance/ledger-books/v1', '/finance/ledger-books/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097410, 'operations-finance-account-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/accounts', '/finance/ledger-books/v1/{ledgerBookId}/accounts', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097411, 'operations-finance-account-create', 'OPERATIONS', 'POST', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/accounts', '/finance/ledger-books/v1/{ledgerBookId}/accounts', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097412, 'operations-finance-journal-line-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/accounts/{accountId}/journal-lines', '/finance/ledger-books/v1/{ledgerBookId}/accounts/{accountId}/journal-lines', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097413, 'operations-finance-journal-entry-detail', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/journal-entries/{journalEntryId}', '/finance/ledger-books/v1/{ledgerBookId}/journal-entries/{journalEntryId}', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097414, 'operations-finance-chart-list', 'OPERATIONS', 'GET', '/api/operations/finance/chart-of-accounts/v1', '/finance/chart-of-accounts/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097415, 'operations-finance-chart-account-list', 'OPERATIONS', 'GET', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts', '/finance/chart-of-accounts/v1/{chartCode}/accounts', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time),
  (900000000000097416, 'operations-finance-currency-list', 'OPERATIONS', 'GET', '/api/operations/finance/currencies/v1', '/finance/currencies/v1', @finance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_console_actor_id, @finance_console_actor_id, @finance_console_time, @finance_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000097501, 900000000000097401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097502, 900000000000097401, 'origin', 'FORWARD_REQUEST'),
  (900000000000097503, 900000000000097402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097504, 900000000000097402, 'origin', 'FORWARD_REQUEST'),
  (900000000000097505, 900000000000097402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000097506, 900000000000097402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000097507, 900000000000097402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000097508, 900000000000097402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000097509, 900000000000097403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097510, 900000000000097403, 'origin', 'FORWARD_REQUEST'),
  (900000000000097511, 900000000000097404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097512, 900000000000097404, 'origin', 'FORWARD_REQUEST'),
  (900000000000097513, 900000000000097404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000097514, 900000000000097404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000097515, 900000000000097404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000097516, 900000000000097404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000097517, 900000000000097405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097518, 900000000000097405, 'origin', 'FORWARD_REQUEST'),
  (900000000000097519, 900000000000097406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097520, 900000000000097406, 'origin', 'FORWARD_REQUEST'),
  (900000000000097521, 900000000000097406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000097522, 900000000000097406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000097523, 900000000000097406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000097524, 900000000000097406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000097525, 900000000000097407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097526, 900000000000097407, 'origin', 'FORWARD_REQUEST'),
  (900000000000097527, 900000000000097407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000097528, 900000000000097407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000097529, 900000000000097407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000097530, 900000000000097407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000097531, 900000000000097408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097532, 900000000000097408, 'origin', 'FORWARD_REQUEST'),
  (900000000000097533, 900000000000097409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097534, 900000000000097409, 'origin', 'FORWARD_REQUEST'),
  (900000000000097535, 900000000000097409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000097536, 900000000000097409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000097537, 900000000000097409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000097538, 900000000000097409, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000097539, 900000000000097410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097540, 900000000000097410, 'origin', 'FORWARD_REQUEST'),
  (900000000000097541, 900000000000097411, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097542, 900000000000097411, 'origin', 'FORWARD_REQUEST'),
  (900000000000097543, 900000000000097411, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000097544, 900000000000097411, 'content-type', 'FORWARD_REQUEST'),
  (900000000000097545, 900000000000097411, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000097546, 900000000000097411, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000097547, 900000000000097412, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097548, 900000000000097412, 'origin', 'FORWARD_REQUEST'),
  (900000000000097549, 900000000000097413, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097550, 900000000000097413, 'origin', 'FORWARD_REQUEST'),
  (900000000000097551, 900000000000097414, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097552, 900000000000097414, 'origin', 'FORWARD_REQUEST'),
  (900000000000097553, 900000000000097415, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097554, 900000000000097415, 'origin', 'FORWARD_REQUEST'),
  (900000000000097555, 900000000000097416, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000097556, 900000000000097416, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000097601, 900000000000097401, 'status'),
  (900000000000097602, 900000000000097401, 'page'),
  (900000000000097603, 900000000000097401, 'size'),
  (900000000000097604, 900000000000097403, 'entityType'),
  (900000000000097605, 900000000000097403, 'entityId'),
  (900000000000097606, 900000000000097403, 'page'),
  (900000000000097607, 900000000000097403, 'size'),
  (900000000000097608, 900000000000097405, 'fiscalYear'),
  (900000000000097609, 900000000000097405, 'page'),
  (900000000000097610, 900000000000097405, 'size'),
  (900000000000097611, 900000000000097408, 'entityType'),
  (900000000000097612, 900000000000097408, 'entityId'),
  (900000000000097613, 900000000000097408, 'currency'),
  (900000000000097614, 900000000000097408, 'page'),
  (900000000000097615, 900000000000097408, 'size'),
  (900000000000097616, 900000000000097410, 'accountCode'),
  (900000000000097617, 900000000000097410, 'ownerType'),
  (900000000000097618, 900000000000097410, 'ownerId'),
  (900000000000097619, 900000000000097410, 'balanceBucket'),
  (900000000000097620, 900000000000097410, 'page'),
  (900000000000097621, 900000000000097410, 'size'),
  (900000000000097622, 900000000000097412, 'postingDateFrom'),
  (900000000000097623, 900000000000097412, 'postingDateTo'),
  (900000000000097624, 900000000000097412, 'page'),
  (900000000000097625, 900000000000097412, 'size'),
  (900000000000097626, 900000000000097415, 'postingAllowed'),
  (900000000000097627, 900000000000097416, 'status');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000097701, 900000000000097401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097702, 900000000000097401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097703, 900000000000097401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097704, 900000000000097401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097705, 900000000000097401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097706, 900000000000097401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097707, 900000000000097402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097708, 900000000000097402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097709, 900000000000097402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097710, 900000000000097402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097711, 900000000000097402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097712, 900000000000097402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097713, 900000000000097403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097714, 900000000000097403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097715, 900000000000097403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097716, 900000000000097403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097717, 900000000000097403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097718, 900000000000097403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097719, 900000000000097404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097720, 900000000000097404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097721, 900000000000097404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097722, 900000000000097404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097723, 900000000000097404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097724, 900000000000097404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097725, 900000000000097405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097726, 900000000000097405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097727, 900000000000097405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097728, 900000000000097405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097729, 900000000000097405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097730, 900000000000097405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097731, 900000000000097406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097732, 900000000000097406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097733, 900000000000097406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097734, 900000000000097406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097735, 900000000000097406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097736, 900000000000097406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097737, 900000000000097407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097738, 900000000000097407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097739, 900000000000097407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097740, 900000000000097407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097741, 900000000000097407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097742, 900000000000097407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097743, 900000000000097408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097744, 900000000000097408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097745, 900000000000097408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097746, 900000000000097408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097747, 900000000000097408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000097748, 900000000000097408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097749, 900000000000097409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097750, 900000000000097409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097751, 900000000000097409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097752, 900000000000097409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097753, 900000000000097409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097754, 900000000000097409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097755, 900000000000097410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097756, 900000000000097410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097757, 900000000000097410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097758, 900000000000097410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097759, 900000000000097410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000097760, 900000000000097410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097761, 900000000000097411, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097762, 900000000000097411, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097763, 900000000000097411, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097764, 900000000000097411, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097765, 900000000000097411, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097766, 900000000000097411, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097767, 900000000000097412, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097768, 900000000000097412, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097769, 900000000000097412, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097770, 900000000000097412, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097771, 900000000000097412, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000097772, 900000000000097412, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097773, 900000000000097413, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097774, 900000000000097413, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097775, 900000000000097413, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097776, 900000000000097413, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097777, 900000000000097413, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097778, 900000000000097413, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097779, 900000000000097414, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097780, 900000000000097414, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097781, 900000000000097414, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097782, 900000000000097414, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097783, 900000000000097414, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097784, 900000000000097414, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097785, 900000000000097415, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097786, 900000000000097415, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097787, 900000000000097415, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097788, 900000000000097415, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097789, 900000000000097415, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097790, 900000000000097415, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000097791, 900000000000097416, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000097792, 900000000000097416, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000097793, 900000000000097416, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000097794, 900000000000097416, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000097795, 900000000000097416, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000097796, 900000000000097416, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance balance query console (2026-10-18, requirements/20261018/operations-console-财务基础资料与账本余额查询).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101).
-- Dormant: routes DISABLED; activate them in a separate reviewed change. BFF routes forward to account.
-- Rows: gateway_route 2, route_header 4, query_param 4, guard_rule 12 (22).
-- Rollback: delete the rows by the 9000000000000984xx-987xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_balance_console_time = UTC_TIMESTAMP(6);
SET @finance_balance_console_actor_id = 1;
SET @finance_balance_console_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000098401, 'operations-finance-client-balance', 'OPERATIONS', 'GET', '/api/operations/finance/balances/v1/clients/{clientId}', '/finance/balances/v1/clients/{clientId}', @finance_balance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_balance_console_actor_id, @finance_balance_console_actor_id, @finance_balance_console_time, @finance_balance_console_time),
  (900000000000098402, 'operations-finance-partner-balance', 'OPERATIONS', 'GET', '/api/operations/finance/balances/v1/partners/{partnerId}', '/finance/balances/v1/partners/{partnerId}', @finance_balance_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_balance_console_actor_id, @finance_balance_console_actor_id, @finance_balance_console_time, @finance_balance_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000098501, 900000000000098401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000098502, 900000000000098401, 'origin', 'FORWARD_REQUEST'),
  (900000000000098503, 900000000000098402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000098504, 900000000000098402, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000098601, 900000000000098401, 'entityNo'),
  (900000000000098602, 900000000000098401, 'currency'),
  (900000000000098603, 900000000000098402, 'entityNo'),
  (900000000000098604, 900000000000098402, 'currency');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000098701, 900000000000098401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000098702, 900000000000098401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000098703, 900000000000098401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000098704, 900000000000098401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000098705, 900000000000098401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000098706, 900000000000098401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000098707, 900000000000098402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000098708, 900000000000098402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000098709, 900000000000098402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000098710, 900000000000098402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000098711, 900000000000098402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000098712, 900000000000098402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance subledger query console (2026-10-19, requirements/20261019/finance-account明细账与ledger控制科目重构).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, OPERATIONS_FINANCE
-- 900000000000097301); no new permission or role grant. Dormant: menu and routes DISABLED; activate them in a separate
-- reviewed change. BFF routes forward to account (OwnerAccountQueryApi, AccountPostingApi).
-- Rows: console_resource 1, gateway_route 3, route_header 6, query_param 11, guard_rule 18 (39).
-- Rollback: delete the rows by the 9000000000000993xx-997xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_subledger_console_time = UTC_TIMESTAMP(6);
SET @finance_subledger_console_actor_id = 1;
SET @finance_subledger_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000099301, 'OPERATIONS_FINANCE_SUBLEDGER', 'OPERATIONS', 'MENU', 900000000000097301, '明细账查询', '/finance/subledger', 'OPERATIONS_FINANCE_SUBLEDGER', 900000000000097101, 40, 'DISABLED', 1, @finance_subledger_console_time, @finance_subledger_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000099401, 'operations-finance-owner-accounts', 'OPERATIONS', 'GET', '/api/operations/finance/owner-accounts/v1', '/finance/owner-accounts/v1', @finance_subledger_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_subledger_console_actor_id, @finance_subledger_console_actor_id, @finance_subledger_console_time, @finance_subledger_console_time),
  (900000000000099402, 'operations-finance-owner-account-entries', 'OPERATIONS', 'GET', '/api/operations/finance/owner-accounts/v1/{accountId}/entries', '/finance/owner-accounts/v1/{accountId}/entries', @finance_subledger_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_subledger_console_actor_id, @finance_subledger_console_actor_id, @finance_subledger_console_time, @finance_subledger_console_time),
  (900000000000099403, 'operations-finance-account-posting', 'OPERATIONS', 'GET', '/api/operations/finance/account-postings/v1/{postingId}', '/finance/account-postings/v1/{postingId}', @finance_subledger_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_subledger_console_actor_id, @finance_subledger_console_actor_id, @finance_subledger_console_time, @finance_subledger_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000099501, 900000000000099401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000099502, 900000000000099401, 'origin', 'FORWARD_REQUEST'),
  (900000000000099503, 900000000000099402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000099504, 900000000000099402, 'origin', 'FORWARD_REQUEST'),
  (900000000000099505, 900000000000099403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000099506, 900000000000099403, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000099601, 900000000000099401, 'entityType'),
  (900000000000099602, 900000000000099401, 'entityId'),
  (900000000000099603, 900000000000099401, 'currency'),
  (900000000000099604, 900000000000099401, 'ownerType'),
  (900000000000099605, 900000000000099401, 'ownerId'),
  (900000000000099606, 900000000000099401, 'accountCode'),
  (900000000000099607, 900000000000099401, 'balanceBucket'),
  (900000000000099608, 900000000000099401, 'page'),
  (900000000000099609, 900000000000099401, 'size'),
  (900000000000099610, 900000000000099402, 'page'),
  (900000000000099611, 900000000000099402, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000099701, 900000000000099401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000099702, 900000000000099401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000099703, 900000000000099401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000099704, 900000000000099401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000099705, 900000000000099401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000099706, 900000000000099401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000099707, 900000000000099402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000099708, 900000000000099402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000099709, 900000000000099402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000099710, 900000000000099402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000099711, 900000000000099402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000099712, 900000000000099402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000099713, 900000000000099403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000099714, 900000000000099403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000099715, 900000000000099403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000099716, 900000000000099403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000099717, 900000000000099403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000099718, 900000000000099403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations commercial console (2026-10-19, requirements/20261019/commercial-六层产品定价模型, task E).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menus/buttons/routes
-- DISABLED; activate them in a separate reviewed change. BFF routes forward to commercial-service through operations-console-bff.
-- COMMERCIAL_PRICING_APPROVE is separate from COMMERCIAL_PRICING_MANAGE so that maker and checker can be different roles;
-- the parent menu 商务 is bound to COMMERCIAL_CONTRACT_READ, so a role that only holds pricing permissions also needs it to see the menu tree.
-- Rows: permission 5, role_permission 5, console_resource 8, gateway_route 24, route_header 128, query_param 5,
-- guard_rule 144 (319). ID block 95: 951xx permission, 952xx role_permission, 953xx console_resource, 954xx route,
-- 955xx-956xx route_header, 957xx query_param, 958xx-959xx guard_rule.
-- Rollback: delete the rows by the 900000000000095101-900000000000095999 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource, role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @commercial_console_time = UTC_TIMESTAMP(6);
SET @commercial_console_actor_id = 1;
SET @commercial_console_target_id = 900000000000000001;
SET @commercial_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000095101, 'COMMERCIAL_CONTRACT_READ', 'COMMERCIAL_CONTRACT', 'READ', 'LOW', 'ACTIVE', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095102, 'COMMERCIAL_CONTRACT_MANAGE', 'COMMERCIAL_CONTRACT', 'MANAGE', 'HIGH', 'ACTIVE', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095103, 'COMMERCIAL_PRICING_READ', 'COMMERCIAL_PRICING', 'READ', 'LOW', 'ACTIVE', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095104, 'COMMERCIAL_PRICING_MANAGE', 'COMMERCIAL_PRICING', 'MANAGE', 'HIGH', 'ACTIVE', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095105, 'COMMERCIAL_PRICING_APPROVE', 'COMMERCIAL_PRICING', 'APPROVE', 'HIGH', 'ACTIVE', 1, @commercial_console_time, @commercial_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000095201, @commercial_console_role_id, 900000000000095101, 'REVOKED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095202, @commercial_console_role_id, 900000000000095102, 'REVOKED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095203, @commercial_console_role_id, 900000000000095103, 'REVOKED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095204, @commercial_console_role_id, 900000000000095104, 'REVOKED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095205, @commercial_console_role_id, 900000000000095105, 'REVOKED', 1, @commercial_console_time, @commercial_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000095301, 'OPERATIONS_COMMERCIAL', 'OPERATIONS', 'MENU', NULL, '商务', NULL, NULL, 900000000000095101, 55, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095302, 'OPERATIONS_COMMERCIAL_AGREEMENTS', 'OPERATIONS', 'MENU', 900000000000095301, '合同', '/commercial/agreements', 'OPERATIONS_COMMERCIAL_AGREEMENTS', 900000000000095101, 10, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095303, 'OPERATIONS_COMMERCIAL_PRICING_PROFILES', 'OPERATIONS', 'MENU', 900000000000095301, '定价画像', '/commercial/pricing-profiles', 'OPERATIONS_COMMERCIAL_PRICING_PROFILES', 900000000000095103, 20, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095304, 'OPERATIONS_COMMERCIAL_TERMS', 'OPERATIONS', 'MENU', 900000000000095301, '价目', '/commercial/terms', 'OPERATIONS_COMMERCIAL_TERMS', 900000000000095103, 30, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095305, 'OPERATIONS_COMMERCIAL_AGREEMENT_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000095302, '维护', NULL, NULL, 900000000000095102, 10, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095306, 'OPERATIONS_COMMERCIAL_PROFILE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000095303, '维护', NULL, NULL, 900000000000095104, 10, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095307, 'OPERATIONS_COMMERCIAL_TERM_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000095304, '维护', NULL, NULL, 900000000000095104, 10, 'DISABLED', 1, @commercial_console_time, @commercial_console_time),
  (900000000000095308, 'OPERATIONS_COMMERCIAL_TERM_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000095304, '审批', NULL, NULL, 900000000000095105, 20, 'DISABLED', 1, @commercial_console_time, @commercial_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000095401, 'operations-commercial-agreement-detail', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/agreements/{agreementId}', '/commercial-admin/v1/agreements/{agreementId}', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095402, 'operations-commercial-agreement-create', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements', '/commercial-admin/v1/agreements', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095403, 'operations-commercial-agreement-submit', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/submit-for-signature', '/commercial-admin/v1/agreements/{agreementId}/submit-for-signature', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095404, 'operations-commercial-agreement-acceptance', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/parties/{partyId}/acceptance', '/commercial-admin/v1/agreements/{agreementId}/parties/{partyId}/acceptance', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095405, 'operations-commercial-agreement-activate', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/activate', '/commercial-admin/v1/agreements/{agreementId}/activate', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095406, 'operations-commercial-agreement-terminate', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/terminate', '/commercial-admin/v1/agreements/{agreementId}/terminate', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095407, 'operations-commercial-agreement-products', 'OPERATIONS', 'PUT', '/api/operations/commercial-admin/v1/agreements/{agreementId}/products', '/commercial-admin/v1/agreements/{agreementId}/products', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095408, 'operations-commercial-capability-create', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/capabilities', '/commercial-admin/v1/agreements/{agreementId}/capabilities', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095409, 'operations-commercial-capability-update', 'OPERATIONS', 'PUT', '/api/operations/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}', '/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095410, 'operations-commercial-profile-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/pricing-profiles', '/commercial-admin/v1/pricing-profiles', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095411, 'operations-commercial-profile-create', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/pricing-profiles', '/commercial-admin/v1/pricing-profiles', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095412, 'operations-commercial-profile-update', 'OPERATIONS', 'PUT', '/api/operations/commercial-admin/v1/pricing-profiles/{pricingProfileId}', '/commercial-admin/v1/pricing-profiles/{pricingProfileId}', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095413, 'operations-commercial-profile-activate', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/pricing-profiles/{pricingProfileId}/activate', '/commercial-admin/v1/pricing-profiles/{pricingProfileId}/activate', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095414, 'operations-commercial-profile-retire', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/pricing-profiles/{pricingProfileId}/retire', '/commercial-admin/v1/pricing-profiles/{pricingProfileId}/retire', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095415, 'operations-commercial-term-plan-create', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/plans', '/commercial-admin/v1/commercial-terms/plans', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095416, 'operations-commercial-term-version-create', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/plans/{planId}/versions', '/commercial-admin/v1/commercial-terms/plans/{planId}/versions', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095417, 'operations-commercial-term-prices', 'OPERATIONS', 'PUT', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/prices', '/commercial-admin/v1/commercial-terms/versions/{versionId}/prices', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095418, 'operations-commercial-term-submit', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/submit', '/commercial-admin/v1/commercial-terms/versions/{versionId}/submit', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095419, 'operations-commercial-term-approve', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/approve', '/commercial-admin/v1/commercial-terms/versions/{versionId}/approve', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095105, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095420, 'operations-commercial-term-reject', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/reject', '/commercial-admin/v1/commercial-terms/versions/{versionId}/reject', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095105, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095421, 'operations-commercial-term-schedule', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/schedule', '/commercial-admin/v1/commercial-terms/versions/{versionId}/schedule', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095422, 'operations-commercial-term-activate', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/activate', '/commercial-admin/v1/commercial-terms/versions/{versionId}/activate', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095104, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095423, 'operations-commercial-term-version-detail', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}', '/commercial-admin/v1/commercial-terms/versions/{versionId}', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time),
  (900000000000095424, 'operations-commercial-term-version-prices', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/commercial-terms/versions/{versionId}/prices', '/commercial-admin/v1/commercial-terms/versions/{versionId}/prices', @commercial_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_actor_id, @commercial_console_actor_id, @commercial_console_time, @commercial_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000095501, 900000000000095401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095502, 900000000000095401, 'origin', 'FORWARD_REQUEST'),
  (900000000000095503, 900000000000095402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095504, 900000000000095402, 'origin', 'FORWARD_REQUEST'),
  (900000000000095505, 900000000000095402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095506, 900000000000095402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095507, 900000000000095402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095508, 900000000000095402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095509, 900000000000095403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095510, 900000000000095403, 'origin', 'FORWARD_REQUEST'),
  (900000000000095511, 900000000000095403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095512, 900000000000095403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095513, 900000000000095403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095514, 900000000000095403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095515, 900000000000095404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095516, 900000000000095404, 'origin', 'FORWARD_REQUEST'),
  (900000000000095517, 900000000000095404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095518, 900000000000095404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095519, 900000000000095404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095520, 900000000000095404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095521, 900000000000095405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095522, 900000000000095405, 'origin', 'FORWARD_REQUEST'),
  (900000000000095523, 900000000000095405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095524, 900000000000095405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095525, 900000000000095405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095526, 900000000000095405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095527, 900000000000095406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095528, 900000000000095406, 'origin', 'FORWARD_REQUEST'),
  (900000000000095529, 900000000000095406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095530, 900000000000095406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095531, 900000000000095406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095532, 900000000000095406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095533, 900000000000095407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095534, 900000000000095407, 'origin', 'FORWARD_REQUEST'),
  (900000000000095535, 900000000000095407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095536, 900000000000095407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095537, 900000000000095407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095538, 900000000000095407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095539, 900000000000095408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095540, 900000000000095408, 'origin', 'FORWARD_REQUEST'),
  (900000000000095541, 900000000000095408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095542, 900000000000095408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095543, 900000000000095408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095544, 900000000000095408, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095545, 900000000000095409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095546, 900000000000095409, 'origin', 'FORWARD_REQUEST'),
  (900000000000095547, 900000000000095409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095548, 900000000000095409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095549, 900000000000095409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095550, 900000000000095409, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095551, 900000000000095410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095552, 900000000000095410, 'origin', 'FORWARD_REQUEST'),
  (900000000000095553, 900000000000095411, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095554, 900000000000095411, 'origin', 'FORWARD_REQUEST'),
  (900000000000095555, 900000000000095411, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095556, 900000000000095411, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095557, 900000000000095411, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095558, 900000000000095411, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095559, 900000000000095412, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095560, 900000000000095412, 'origin', 'FORWARD_REQUEST'),
  (900000000000095561, 900000000000095412, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095562, 900000000000095412, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095563, 900000000000095412, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095564, 900000000000095412, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095565, 900000000000095413, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095566, 900000000000095413, 'origin', 'FORWARD_REQUEST'),
  (900000000000095567, 900000000000095413, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095568, 900000000000095413, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095569, 900000000000095413, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095570, 900000000000095413, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095571, 900000000000095414, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095572, 900000000000095414, 'origin', 'FORWARD_REQUEST'),
  (900000000000095573, 900000000000095414, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095574, 900000000000095414, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095575, 900000000000095414, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095576, 900000000000095414, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095577, 900000000000095415, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095578, 900000000000095415, 'origin', 'FORWARD_REQUEST'),
  (900000000000095579, 900000000000095415, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095580, 900000000000095415, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095581, 900000000000095415, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095582, 900000000000095415, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095583, 900000000000095416, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095584, 900000000000095416, 'origin', 'FORWARD_REQUEST'),
  (900000000000095585, 900000000000095416, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095586, 900000000000095416, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095587, 900000000000095416, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095588, 900000000000095416, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095589, 900000000000095417, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095590, 900000000000095417, 'origin', 'FORWARD_REQUEST'),
  (900000000000095591, 900000000000095417, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095592, 900000000000095417, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095593, 900000000000095417, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095594, 900000000000095417, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095595, 900000000000095418, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095596, 900000000000095418, 'origin', 'FORWARD_REQUEST'),
  (900000000000095597, 900000000000095418, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095598, 900000000000095418, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095599, 900000000000095418, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095600, 900000000000095418, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095601, 900000000000095419, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095602, 900000000000095419, 'origin', 'FORWARD_REQUEST'),
  (900000000000095603, 900000000000095419, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095604, 900000000000095419, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095605, 900000000000095419, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095606, 900000000000095419, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095607, 900000000000095420, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095608, 900000000000095420, 'origin', 'FORWARD_REQUEST'),
  (900000000000095609, 900000000000095420, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095610, 900000000000095420, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095611, 900000000000095420, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095612, 900000000000095420, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095613, 900000000000095421, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095614, 900000000000095421, 'origin', 'FORWARD_REQUEST'),
  (900000000000095615, 900000000000095421, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095616, 900000000000095421, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095617, 900000000000095421, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095618, 900000000000095421, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095619, 900000000000095422, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095620, 900000000000095422, 'origin', 'FORWARD_REQUEST'),
  (900000000000095621, 900000000000095422, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095622, 900000000000095422, 'content-type', 'FORWARD_REQUEST'),
  (900000000000095623, 900000000000095422, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000095624, 900000000000095422, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000095625, 900000000000095423, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095626, 900000000000095423, 'origin', 'FORWARD_REQUEST'),
  (900000000000095627, 900000000000095424, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095628, 900000000000095424, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000095701, 900000000000095410, 'kind'),
  (900000000000095702, 900000000000095410, 'status'),
  (900000000000095703, 900000000000095424, 'entityNo'),
  (900000000000095704, 900000000000095424, 'termType'),
  (900000000000095705, 900000000000095424, 'currency');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000095801, 900000000000095401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095802, 900000000000095401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095803, 900000000000095401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095804, 900000000000095401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095805, 900000000000095401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095806, 900000000000095401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095807, 900000000000095402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095808, 900000000000095402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095809, 900000000000095402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095810, 900000000000095402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095811, 900000000000095402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095812, 900000000000095402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095813, 900000000000095403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095814, 900000000000095403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095815, 900000000000095403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095816, 900000000000095403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095817, 900000000000095403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095818, 900000000000095403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095819, 900000000000095404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095820, 900000000000095404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095821, 900000000000095404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095822, 900000000000095404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095823, 900000000000095404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095824, 900000000000095404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095825, 900000000000095405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095826, 900000000000095405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095827, 900000000000095405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095828, 900000000000095405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095829, 900000000000095405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095830, 900000000000095405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095831, 900000000000095406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095832, 900000000000095406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095833, 900000000000095406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095834, 900000000000095406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095835, 900000000000095406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095836, 900000000000095406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095837, 900000000000095407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095838, 900000000000095407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095839, 900000000000095407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095840, 900000000000095407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095841, 900000000000095407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095842, 900000000000095407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095843, 900000000000095408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095844, 900000000000095408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095845, 900000000000095408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095846, 900000000000095408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095847, 900000000000095408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095848, 900000000000095408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095849, 900000000000095409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095850, 900000000000095409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095851, 900000000000095409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095852, 900000000000095409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095853, 900000000000095409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095854, 900000000000095409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095855, 900000000000095410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095856, 900000000000095410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095857, 900000000000095410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095858, 900000000000095410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095859, 900000000000095410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095860, 900000000000095410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095861, 900000000000095411, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095862, 900000000000095411, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095863, 900000000000095411, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095864, 900000000000095411, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095865, 900000000000095411, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095866, 900000000000095411, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095867, 900000000000095412, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095868, 900000000000095412, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095869, 900000000000095412, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095870, 900000000000095412, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095871, 900000000000095412, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095872, 900000000000095412, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095873, 900000000000095413, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095874, 900000000000095413, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095875, 900000000000095413, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095876, 900000000000095413, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095877, 900000000000095413, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095878, 900000000000095413, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095879, 900000000000095414, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095880, 900000000000095414, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095881, 900000000000095414, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095882, 900000000000095414, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095883, 900000000000095414, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095884, 900000000000095414, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095885, 900000000000095415, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095886, 900000000000095415, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095887, 900000000000095415, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095888, 900000000000095415, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095889, 900000000000095415, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095890, 900000000000095415, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095891, 900000000000095416, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095892, 900000000000095416, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095893, 900000000000095416, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095894, 900000000000095416, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095895, 900000000000095416, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095896, 900000000000095416, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095897, 900000000000095417, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095898, 900000000000095417, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095899, 900000000000095417, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095900, 900000000000095417, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095901, 900000000000095417, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095902, 900000000000095417, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095903, 900000000000095418, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095904, 900000000000095418, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095905, 900000000000095418, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095906, 900000000000095418, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095907, 900000000000095418, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095908, 900000000000095418, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095909, 900000000000095419, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095910, 900000000000095419, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095911, 900000000000095419, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095912, 900000000000095419, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095913, 900000000000095419, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095914, 900000000000095419, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095915, 900000000000095420, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095916, 900000000000095420, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095917, 900000000000095420, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095918, 900000000000095420, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095919, 900000000000095420, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095920, 900000000000095420, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095921, 900000000000095421, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095922, 900000000000095421, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095923, 900000000000095421, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095924, 900000000000095421, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095925, 900000000000095421, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095926, 900000000000095421, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095927, 900000000000095422, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095928, 900000000000095422, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095929, 900000000000095422, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095930, 900000000000095422, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095931, 900000000000095422, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000095932, 900000000000095422, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095933, 900000000000095423, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095934, 900000000000095423, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095935, 900000000000095423, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095936, 900000000000095423, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095937, 900000000000095423, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095938, 900000000000095423, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095939, 900000000000095424, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095940, 900000000000095424, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095941, 900000000000095424, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095942, 900000000000095424, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095943, 900000000000095424, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095944, 900000000000095424, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);
-- Operations finance chart maintenance and ledger balance query console (2026-10-19, requirements/20261019/ledger-会计科目分级维护).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102) and the finance subledger block above (route 900000000000099401); no new permission, role grant or
-- console resource (chart maintenance reuses OPERATIONS_FINANCE_SETUP_MANAGE). Dormant: routes DISABLED; activate them in a separate
-- reviewed change. BFF routes forward to ledger (ChartOfAccountsApi, LedgerBookApi, TrialBalanceApi). The three extra
-- query parameters extend existing routes 900000000000097415, 900000000000097410 and 900000000000099401.
-- Rows: gateway_route 8, route_header 32, query_param 8, guard_rule 48 (96). The trial-balance run route and a ledger
-- MANAGE button are intentionally not registered in this requirement.
-- Rollback: delete the rows by the 9000000000000914xx-917xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_chart_console_time = UTC_TIMESTAMP(6);
SET @finance_chart_console_actor_id = 1;
SET @finance_chart_console_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000091401, 'operations-finance-chart-account-create', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts', '/finance/chart-of-accounts/v1/{chartCode}/accounts', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091402, 'operations-finance-chart-account-update', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/update', '/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/update', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091403, 'operations-finance-chart-account-disable', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/disable', '/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/disable', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091404, 'operations-finance-chart-account-enable', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/enable', '/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/enable', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091405, 'operations-finance-chart-account-change-list', 'OPERATIONS', 'GET', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/changes', '/finance/chart-of-accounts/v1/{chartCode}/changes', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091406, 'operations-finance-account-balance-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091407, 'operations-finance-trial-balance-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091408, 'operations-finance-trial-balance-detail', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000091501, 900000000000091401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091502, 900000000000091401, 'origin', 'FORWARD_REQUEST'),
  (900000000000091503, 900000000000091401, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091504, 900000000000091401, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091505, 900000000000091401, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091506, 900000000000091401, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091507, 900000000000091402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091508, 900000000000091402, 'origin', 'FORWARD_REQUEST'),
  (900000000000091509, 900000000000091402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091510, 900000000000091402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091511, 900000000000091402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091512, 900000000000091402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091513, 900000000000091403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091514, 900000000000091403, 'origin', 'FORWARD_REQUEST'),
  (900000000000091515, 900000000000091403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091516, 900000000000091403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091517, 900000000000091403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091518, 900000000000091403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091519, 900000000000091404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091520, 900000000000091404, 'origin', 'FORWARD_REQUEST'),
  (900000000000091521, 900000000000091404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091522, 900000000000091404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091523, 900000000000091404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091524, 900000000000091404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091525, 900000000000091405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091526, 900000000000091405, 'origin', 'FORWARD_REQUEST'),
  (900000000000091527, 900000000000091406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091528, 900000000000091406, 'origin', 'FORWARD_REQUEST'),
  (900000000000091529, 900000000000091407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091530, 900000000000091407, 'origin', 'FORWARD_REQUEST'),
  (900000000000091531, 900000000000091408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091532, 900000000000091408, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000091601, 900000000000091405, 'chartAccountId'),
  (900000000000091602, 900000000000091405, 'page'),
  (900000000000091603, 900000000000091405, 'size'),
  (900000000000091604, 900000000000091407, 'page'),
  (900000000000091605, 900000000000091407, 'size'),
  (900000000000091606, 900000000000097415, 'accountStatus'),
  (900000000000091607, 900000000000097410, 'accountNo'),
  (900000000000091608, 900000000000099401, 'accountNo');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000091701, 900000000000091401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091702, 900000000000091401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091703, 900000000000091401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091704, 900000000000091401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091705, 900000000000091401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091706, 900000000000091401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091707, 900000000000091402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091708, 900000000000091402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091709, 900000000000091402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091710, 900000000000091402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091711, 900000000000091402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091712, 900000000000091402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091713, 900000000000091403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091714, 900000000000091403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091715, 900000000000091403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091716, 900000000000091403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091717, 900000000000091403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091718, 900000000000091403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091719, 900000000000091404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091720, 900000000000091404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091721, 900000000000091404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091722, 900000000000091404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091723, 900000000000091404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091724, 900000000000091404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091725, 900000000000091405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091726, 900000000000091405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091727, 900000000000091405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091728, 900000000000091405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091729, 900000000000091405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091730, 900000000000091405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091731, 900000000000091406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091732, 900000000000091406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091733, 900000000000091406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091734, 900000000000091406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091735, 900000000000091406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000091736, 900000000000091406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091737, 900000000000091407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091738, 900000000000091407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091739, 900000000000091407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091740, 900000000000091407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091741, 900000000000091407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000091742, 900000000000091407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091743, 900000000000091408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091744, 900000000000091408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091745, 900000000000091408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091746, 900000000000091408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091747, 900000000000091408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000091748, 900000000000091408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations commercial console, second slice I1 (2026-10-20, requirements/20261020/commercial-运营控制台完善, line I1).
-- Self-contained block: on an environment already holding the commercial console block 95 above, apply ONLY this block.
-- Dormant like the block above: new menus and routes DISABLED; no new permission or grant (reuses COMMERCIAL_CONTRACT_READ /
-- COMMERCIAL_PRICING_READ). Navigation order becomes 工作台 / 合同 / 价目 / 费率预览 / 价格试算 / 定价画像 / 产品目录 / 变更历史:
-- the two UPDATEs move the existing 价目 (95304) to sort 20 and 定价画像 (95303) to sort 30.
-- 产品目录 and 变更历史 are page shells until commercial's catalog / history reads are wired (line I2 registers their routes).
-- The price simulation route is a POST query (read-only, COMMERCIAL_PRICING_READ, 8192-byte body, no Idempotency-Key).
-- Rows: console_resource 5 (+2 sort updates), gateway_route 3, route_header 8, query_param 3, guard_rule 18 (37).
-- IDs continue block 95: 95309-95313 console_resource, 95425-95427 route, 95629-95636 route_header, 95706-95708 query_param,
-- 95945-95962 guard_rule.
-- Rollback: delete guard_rule 95945-95962, query_param 95706-95708, route_header 95629-95636, gateway_route 95425-95427,
-- console_resource 95309-95313; restore sort_order 95303=20, 95304=30.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @commercial_console_i1_time = UTC_TIMESTAMP(6);
SET @commercial_console_i1_actor_id = 1;
SET @commercial_console_i1_target_id = 900000000000000001;

UPDATE t_console_resource SET sort_order = 20, version = version + 1, update_time = @commercial_console_i1_time
 WHERE resource_id = 900000000000095304 AND sort_order = 30;
UPDATE t_console_resource SET sort_order = 30, version = version + 1, update_time = @commercial_console_i1_time
 WHERE resource_id = 900000000000095303 AND sort_order = 20;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000095309, 'OPERATIONS_COMMERCIAL_WORKBENCH', 'OPERATIONS', 'MENU', 900000000000095301, '工作台', '/commercial/workbench', 'OPERATIONS_COMMERCIAL_WORKBENCH', 900000000000095101, 5, 'DISABLED', 1, @commercial_console_i1_time, @commercial_console_i1_time),
  (900000000000095310, 'OPERATIONS_COMMERCIAL_RATE_CARDS', 'OPERATIONS', 'MENU', 900000000000095301, '费率预览', '/commercial/rate-cards', 'OPERATIONS_COMMERCIAL_RATE_CARDS', 900000000000095103, 22, 'DISABLED', 1, @commercial_console_i1_time, @commercial_console_i1_time),
  (900000000000095311, 'OPERATIONS_COMMERCIAL_PRICE_SIMULATOR', 'OPERATIONS', 'MENU', 900000000000095301, '价格试算', '/commercial/price-simulator', 'OPERATIONS_COMMERCIAL_PRICE_SIMULATOR', 900000000000095103, 24, 'DISABLED', 1, @commercial_console_i1_time, @commercial_console_i1_time),
  (900000000000095312, 'OPERATIONS_COMMERCIAL_CATALOG', 'OPERATIONS', 'MENU', 900000000000095301, '产品目录', '/commercial/catalog', 'OPERATIONS_COMMERCIAL_CATALOG', 900000000000095101, 40, 'DISABLED', 1, @commercial_console_i1_time, @commercial_console_i1_time),
  (900000000000095313, 'OPERATIONS_COMMERCIAL_HISTORY', 'OPERATIONS', 'MENU', 900000000000095301, '变更历史', '/commercial/history', 'OPERATIONS_COMMERCIAL_HISTORY', 900000000000095101, 50, 'DISABLED', 1, @commercial_console_i1_time, @commercial_console_i1_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000095425, 'operations-commercial-dashboard', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/dashboard', '/commercial-admin/v1/dashboard', @commercial_console_i1_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i1_actor_id, @commercial_console_i1_actor_id, @commercial_console_i1_time, @commercial_console_i1_time),
  (900000000000095426, 'operations-commercial-rate-card', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/rate-cards', '/commercial-admin/v1/rate-cards', @commercial_console_i1_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i1_actor_id, @commercial_console_i1_actor_id, @commercial_console_i1_time, @commercial_console_i1_time),
  (900000000000095427, 'operations-commercial-price-simulate', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/commercial-prices/simulate', '/commercial-admin/v1/commercial-prices/simulate', @commercial_console_i1_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @commercial_console_i1_actor_id, @commercial_console_i1_actor_id, @commercial_console_i1_time, @commercial_console_i1_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000095629, 900000000000095425, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095630, 900000000000095425, 'origin', 'FORWARD_REQUEST'),
  (900000000000095631, 900000000000095426, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095632, 900000000000095426, 'origin', 'FORWARD_REQUEST'),
  (900000000000095633, 900000000000095427, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000095634, 900000000000095427, 'origin', 'FORWARD_REQUEST'),
  (900000000000095635, 900000000000095427, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000095636, 900000000000095427, 'content-type', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000095706, 900000000000095426, 'clientId'),
  (900000000000095707, 900000000000095426, 'asOf'),
  (900000000000095708, 900000000000095426, 'currency');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000095945, 900000000000095425, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095946, 900000000000095425, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095947, 900000000000095425, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095948, 900000000000095425, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095949, 900000000000095425, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095950, 900000000000095425, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095951, 900000000000095426, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095952, 900000000000095426, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095953, 900000000000095426, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095954, 900000000000095426, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095955, 900000000000095426, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095956, 900000000000095426, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000095957, 900000000000095427, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000095958, 900000000000095427, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000095959, 900000000000095427, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000095960, 900000000000095427, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000095961, 900000000000095427, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000095962, 900000000000095427, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations commercial console, second slice I2 (2026-10-20, requirements/20261020/commercial-运营控制台完善, line I2).
-- Self-contained block: on an environment already holding block 95 (and its I1 part) above, apply ONLY this block.
-- Dormant like the blocks above: 8 read-only GET routes DISABLED; no new permission, grant or menu (the 合同 / 价目 /
-- 工作台 / 产品目录 / 变更历史 menus already exist). Agreement list, product catalog and business-change history bind
-- COMMERCIAL_CONTRACT_READ (95101, same as their menus); plan list, version list and version diff bind COMMERCIAL_PRICING_READ (95103).
-- status is a repeatable query parameter on the agreement and version lists (the query allowlist checks names only).
-- Rows: gateway_route 8, route_header 16, query_param 31, guard_rule 48 (103).
-- ID block 93: 934xx route, 936xx route_header, 937xx query_param, 939xx guard_rule.
-- Rollback: delete guard_rule 93901-93948, query_param 93701-93731, route_header 93601-93616, gateway_route 93401-93408.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @commercial_console_i2_time = UTC_TIMESTAMP(6);
SET @commercial_console_i2_actor_id = 1;
SET @commercial_console_i2_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000093401, 'operations-commercial-agreement-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/agreements', '/commercial-admin/v1/agreements', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093402, 'operations-commercial-term-plan-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/commercial-terms/plans', '/commercial-admin/v1/commercial-terms/plans', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093403, 'operations-commercial-term-version-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/commercial-terms/versions', '/commercial-admin/v1/commercial-terms/versions', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093404, 'operations-commercial-term-version-diff', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/commercial-terms/versions/{baseVersionId}/diff/{targetVersionId}', '/commercial-admin/v1/commercial-terms/versions/{baseVersionId}/diff/{targetVersionId}', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093405, 'operations-commercial-product-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/products', '/commercial-admin/v1/products', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093406, 'operations-commercial-capability-catalog', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/products/capabilities', '/commercial-admin/v1/products/capabilities', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093407, 'operations-commercial-fee-event-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/products/fee-events', '/commercial-admin/v1/products/fee-events', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time),
  (900000000000093408, 'operations-commercial-business-change-list', 'OPERATIONS', 'GET', '/api/operations/commercial-admin/v1/business-changes', '/commercial-admin/v1/business-changes', @commercial_console_i2_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @commercial_console_i2_actor_id, @commercial_console_i2_actor_id, @commercial_console_i2_time, @commercial_console_i2_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000093601, 900000000000093401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093602, 900000000000093401, 'origin', 'FORWARD_REQUEST'),
  (900000000000093603, 900000000000093402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093604, 900000000000093402, 'origin', 'FORWARD_REQUEST'),
  (900000000000093605, 900000000000093403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093606, 900000000000093403, 'origin', 'FORWARD_REQUEST'),
  (900000000000093607, 900000000000093404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093608, 900000000000093404, 'origin', 'FORWARD_REQUEST'),
  (900000000000093609, 900000000000093405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093610, 900000000000093405, 'origin', 'FORWARD_REQUEST'),
  (900000000000093611, 900000000000093406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093612, 900000000000093406, 'origin', 'FORWARD_REQUEST'),
  (900000000000093613, 900000000000093407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093614, 900000000000093407, 'origin', 'FORWARD_REQUEST'),
  (900000000000093615, 900000000000093408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000093616, 900000000000093408, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000093701, 900000000000093401, 'clientId'),
  (900000000000093702, 900000000000093401, 'localPartnerId'),
  (900000000000093703, 900000000000093401, 'agreementType'),
  (900000000000093704, 900000000000093401, 'status'),
  (900000000000093705, 900000000000093401, 'entityNo'),
  (900000000000093706, 900000000000093401, 'agreementNo'),
  (900000000000093707, 900000000000093401, 'page'),
  (900000000000093708, 900000000000093401, 'size'),
  (900000000000093709, 900000000000093402, 'agreementId'),
  (900000000000093710, 900000000000093402, 'clientId'),
  (900000000000093711, 900000000000093402, 'localPartnerId'),
  (900000000000093712, 900000000000093402, 'termType'),
  (900000000000093713, 900000000000093402, 'status'),
  (900000000000093714, 900000000000093402, 'page'),
  (900000000000093715, 900000000000093402, 'size'),
  (900000000000093716, 900000000000093403, 'planId'),
  (900000000000093717, 900000000000093403, 'agreementId'),
  (900000000000093718, 900000000000093403, 'status'),
  (900000000000093719, 900000000000093403, 'effectiveFrom'),
  (900000000000093720, 900000000000093403, 'effectiveTo'),
  (900000000000093721, 900000000000093403, 'page'),
  (900000000000093722, 900000000000093403, 'size'),
  (900000000000093723, 900000000000093405, 'status'),
  (900000000000093724, 900000000000093406, 'productCode'),
  (900000000000093725, 900000000000093406, 'status'),
  (900000000000093726, 900000000000093407, 'productCode'),
  (900000000000093727, 900000000000093407, 'termType'),
  (900000000000093728, 900000000000093408, 'aggregateRootType'),
  (900000000000093729, 900000000000093408, 'aggregateRootId'),
  (900000000000093730, 900000000000093408, 'page'),
  (900000000000093731, 900000000000093408, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000093901, 900000000000093401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093902, 900000000000093401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093903, 900000000000093401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093904, 900000000000093401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093905, 900000000000093401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093906, 900000000000093401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093907, 900000000000093402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093908, 900000000000093402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093909, 900000000000093402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093910, 900000000000093402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093911, 900000000000093402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093912, 900000000000093402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093913, 900000000000093403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093914, 900000000000093403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093915, 900000000000093403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093916, 900000000000093403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093917, 900000000000093403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093918, 900000000000093403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093919, 900000000000093404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093920, 900000000000093404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093921, 900000000000093404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093922, 900000000000093404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093923, 900000000000093404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093924, 900000000000093404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093925, 900000000000093405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093926, 900000000000093405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093927, 900000000000093405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093928, 900000000000093405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093929, 900000000000093405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093930, 900000000000093405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093931, 900000000000093406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093932, 900000000000093406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093933, 900000000000093406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093934, 900000000000093406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093935, 900000000000093406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093936, 900000000000093406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093937, 900000000000093407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093938, 900000000000093407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093939, 900000000000093407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093940, 900000000000093407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093941, 900000000000093407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093942, 900000000000093407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000093943, 900000000000093408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000093944, 900000000000093408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000093945, 900000000000093408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000093946, 900000000000093408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000093947, 900000000000093408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000093948, 900000000000093408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance manual adjustment and billing fact console (2026-10-20, requirements/20261020/finance-人工调整与计费事实).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102, OPERATIONS_FINANCE 900000000000097301).
-- Adds FINANCE_ACCOUNTING_APPROVE (checker; maker keeps FINANCE_ACCOUNTING_MANAGE) and its PLATFORM_ADMIN grant (REVOKED).
-- Dormant: grant REVOKED, menu/buttons/routes DISABLED; activate them in a separate reviewed change. BFF routes forward to
-- ledger (ManualAdjustmentApi) and billing (BillingFactQueryApi, BillingFactCommandApi); operator identity comes from the
-- Gateway actor headers, never from the request body.
-- Rows: permission 1, role_permission 1, console_resource 3, gateway_route 10, route_header 44, query_param 15, guard_rule 60 (134).
-- ID block 120: 1201xx permission, 1202xx role_permission, 1203xx console_resource,
-- 1204xx route, 1205xx route_header, 1206xx query_param, 1207xx guard_rule.
-- Rollback: delete the rows by the 900000000000120101-900000000000120799 IDs below (children first: guard_rule,
-- query_param, route_header, gateway_route, console_resource, role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_adjustment_console_time = UTC_TIMESTAMP(6);
SET @finance_adjustment_console_actor_id = 1;
SET @finance_adjustment_console_target_id = 900000000000000001;
SET @finance_adjustment_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000120101, 'FINANCE_ACCOUNTING_APPROVE', 'FINANCE_ACCOUNTING', 'APPROVE', 'HIGH', 'ACTIVE', 1, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000120201, @finance_adjustment_console_role_id, 900000000000120101, 'REVOKED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000120301, 'OPERATIONS_FINANCE_ADJUSTMENTS', 'OPERATIONS', 'MENU', 900000000000097301, '调整', '/finance/adjustments', 'OPERATIONS_FINANCE_ADJUSTMENTS', 900000000000097101, 50, 'DISABLED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120302, 'OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000120301, '制单', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120303, 'OPERATIONS_FINANCE_ADJUSTMENTS_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000120301, '复核', NULL, NULL, 900000000000120101, 20, 'DISABLED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000120401, 'operations-finance-manual-adjustment-list', 'OPERATIONS', 'GET', '/api/operations/finance/manual-adjustments/v1', '/finance/manual-adjustments/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120402, 'operations-finance-manual-adjustment-submit', 'OPERATIONS', 'POST', '/api/operations/finance/manual-adjustments/v1', '/finance/manual-adjustments/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120403, 'operations-finance-manual-adjustment-detail', 'OPERATIONS', 'GET', '/api/operations/finance/manual-adjustments/v1/{manualAdjustmentId}', '/finance/manual-adjustments/v1/{manualAdjustmentId}', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120404, 'operations-finance-manual-adjustment-approve', 'OPERATIONS', 'POST', '/api/operations/finance/manual-adjustments/v1/{manualAdjustmentId}/approve', '/finance/manual-adjustments/v1/{manualAdjustmentId}/approve', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120405, 'operations-finance-manual-adjustment-reject', 'OPERATIONS', 'POST', '/api/operations/finance/manual-adjustments/v1/{manualAdjustmentId}/reject', '/finance/manual-adjustments/v1/{manualAdjustmentId}/reject', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120406, 'operations-finance-billing-fact-list', 'OPERATIONS', 'GET', '/api/operations/finance/billing-facts/v1', '/finance/billing-facts/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120407, 'operations-finance-billing-fact-submit', 'OPERATIONS', 'POST', '/api/operations/finance/billing-facts/v1', '/finance/billing-facts/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120408, 'operations-finance-billing-fact-detail', 'OPERATIONS', 'GET', '/api/operations/finance/billing-facts/v1/{factId}', '/finance/billing-facts/v1/{factId}', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120409, 'operations-finance-billing-fact-approve', 'OPERATIONS', 'POST', '/api/operations/finance/billing-facts/v1/{factId}/approve', '/finance/billing-facts/v1/{factId}/approve', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120410, 'operations-finance-billing-fact-void', 'OPERATIONS', 'POST', '/api/operations/finance/billing-facts/v1/{factId}/void', '/finance/billing-facts/v1/{factId}/void', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000120501, 900000000000120401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120502, 900000000000120401, 'origin', 'FORWARD_REQUEST'),
  (900000000000120503, 900000000000120402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120504, 900000000000120402, 'origin', 'FORWARD_REQUEST'),
  (900000000000120505, 900000000000120402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120506, 900000000000120402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120507, 900000000000120402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120508, 900000000000120402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120509, 900000000000120403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120510, 900000000000120403, 'origin', 'FORWARD_REQUEST'),
  (900000000000120511, 900000000000120404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120512, 900000000000120404, 'origin', 'FORWARD_REQUEST'),
  (900000000000120513, 900000000000120404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120514, 900000000000120404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120515, 900000000000120404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120516, 900000000000120404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120517, 900000000000120405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120518, 900000000000120405, 'origin', 'FORWARD_REQUEST'),
  (900000000000120519, 900000000000120405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120520, 900000000000120405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120521, 900000000000120405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120522, 900000000000120405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120523, 900000000000120406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120524, 900000000000120406, 'origin', 'FORWARD_REQUEST'),
  (900000000000120525, 900000000000120407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120526, 900000000000120407, 'origin', 'FORWARD_REQUEST'),
  (900000000000120527, 900000000000120407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120528, 900000000000120407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120529, 900000000000120407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120530, 900000000000120407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120531, 900000000000120408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120532, 900000000000120408, 'origin', 'FORWARD_REQUEST'),
  (900000000000120533, 900000000000120409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120534, 900000000000120409, 'origin', 'FORWARD_REQUEST'),
  (900000000000120535, 900000000000120409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120536, 900000000000120409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120537, 900000000000120409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120538, 900000000000120409, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120539, 900000000000120410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120540, 900000000000120410, 'origin', 'FORWARD_REQUEST'),
  (900000000000120541, 900000000000120410, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120542, 900000000000120410, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120543, 900000000000120410, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120544, 900000000000120410, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000120601, 900000000000120401, 'ledgerBookId'),
  (900000000000120602, 900000000000120401, 'accountingPeriodId'),
  (900000000000120603, 900000000000120401, 'status'),
  (900000000000120604, 900000000000120401, 'makerId'),
  (900000000000120605, 900000000000120401, 'page'),
  (900000000000120606, 900000000000120401, 'size'),
  (900000000000120607, 900000000000120406, 'entityNo'),
  (900000000000120608, 900000000000120406, 'currency'),
  (900000000000120609, 900000000000120406, 'clientId'),
  (900000000000120610, 900000000000120406, 'status'),
  (900000000000120611, 900000000000120406, 'factType'),
  (900000000000120612, 900000000000120406, 'createdFrom'),
  (900000000000120613, 900000000000120406, 'createdTo'),
  (900000000000120614, 900000000000120406, 'page'),
  (900000000000120615, 900000000000120406, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000120701, 900000000000120401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120702, 900000000000120401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120703, 900000000000120401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120704, 900000000000120401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120705, 900000000000120401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120706, 900000000000120401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120707, 900000000000120402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120708, 900000000000120402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120709, 900000000000120402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120710, 900000000000120402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120711, 900000000000120402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120712, 900000000000120402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120713, 900000000000120403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120714, 900000000000120403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120715, 900000000000120403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120716, 900000000000120403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120717, 900000000000120403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120718, 900000000000120403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120719, 900000000000120404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120720, 900000000000120404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120721, 900000000000120404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120722, 900000000000120404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120723, 900000000000120404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120724, 900000000000120404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120725, 900000000000120405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120726, 900000000000120405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120727, 900000000000120405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120728, 900000000000120405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120729, 900000000000120405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120730, 900000000000120405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120731, 900000000000120406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120732, 900000000000120406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120733, 900000000000120406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120734, 900000000000120406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120735, 900000000000120406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120736, 900000000000120406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120737, 900000000000120407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120738, 900000000000120407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120739, 900000000000120407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120740, 900000000000120407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120741, 900000000000120407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120742, 900000000000120407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120743, 900000000000120408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120744, 900000000000120408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120745, 900000000000120408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120746, 900000000000120408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120747, 900000000000120408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120748, 900000000000120408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120749, 900000000000120409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120750, 900000000000120409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120751, 900000000000120409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120752, 900000000000120409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120753, 900000000000120409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120754, 900000000000120409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120755, 900000000000120410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120756, 900000000000120410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120757, 900000000000120410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120758, 900000000000120410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120759, 900000000000120410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120760, 900000000000120410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance period close and trial balance run console (2026-10-20, requirements/20261020/finance-期间关账与试算).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102, OPERATIONS_FINANCE 900000000000097301, OPERATIONS_FINANCE_LEDGER 900000000000097303) and the
-- manual adjustment block above (FINANCE_ACCOUNTING_APPROVE 900000000000120101).
-- No new permission or role grant. Dormant: menu/buttons/routes DISABLED; activate them in a separate reviewed change.
-- BFF routes forward to ledger (PeriodCloseApi, TrialBalanceApi.runTrialBalance); the board route is a BFF composition of
-- the calendar period list and each period's latest close record. OPERATIONS_FINANCE_LEDGER_MANAGE is the trial-balance
-- run button on the existing ledger query page.
-- Rows: console_resource 4, gateway_route 8, route_header 36, query_param 3, guard_rule 48 (99).
-- ID block 121: 1213xx console_resource,
-- 1214xx route, 1215xx route_header, 1216xx query_param, 1217xx guard_rule.
-- Rollback: delete the rows by the 900000000000121301-900000000000121799 IDs below (children first: guard_rule,
-- query_param, route_header, gateway_route, console_resource).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_period_close_console_time = UTC_TIMESTAMP(6);
SET @finance_period_close_console_actor_id = 1;
SET @finance_period_close_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000121301, 'OPERATIONS_FINANCE_PERIOD_CLOSE', 'OPERATIONS', 'MENU', 900000000000097301, '期间关账', '/finance/period-close', 'OPERATIONS_FINANCE_PERIOD_CLOSE', 900000000000097101, 60, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121302, 'OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000121301, '发起', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121303, 'OPERATIONS_FINANCE_PERIOD_CLOSE_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000121301, '复核', NULL, NULL, 900000000000120101, 20, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121304, 'OPERATIONS_FINANCE_LEDGER_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000097303, '发起试算', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000121401, 'operations-finance-period-close-board', 'OPERATIONS', 'GET', '/api/operations/finance/period-close/v1/board', '/finance/period-close/v1/board', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121402, 'operations-finance-period-close-list', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes', '/finance/accounting-periods/v1/{accountingPeriodId}/closes', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121403, 'operations-finance-period-close-detail', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}', '/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121404, 'operations-finance-period-close-start', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes', '/finance/accounting-periods/v1/{accountingPeriodId}/closes', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121405, 'operations-finance-period-reopen-start', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/reopens', '/finance/accounting-periods/v1/{accountingPeriodId}/reopens', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121406, 'operations-finance-period-close-approve', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve', '/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121407, 'operations-finance-period-close-reject', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject', '/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121408, 'operations-finance-trial-balance-run', 'OPERATIONS', 'POST', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000121501, 900000000000121401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121502, 900000000000121401, 'origin', 'FORWARD_REQUEST'),
  (900000000000121503, 900000000000121402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121504, 900000000000121402, 'origin', 'FORWARD_REQUEST'),
  (900000000000121505, 900000000000121403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121506, 900000000000121403, 'origin', 'FORWARD_REQUEST'),
  (900000000000121507, 900000000000121404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121508, 900000000000121404, 'origin', 'FORWARD_REQUEST'),
  (900000000000121509, 900000000000121404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121510, 900000000000121404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121511, 900000000000121404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121512, 900000000000121404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121513, 900000000000121405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121514, 900000000000121405, 'origin', 'FORWARD_REQUEST'),
  (900000000000121515, 900000000000121405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121516, 900000000000121405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121517, 900000000000121405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121518, 900000000000121405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121519, 900000000000121406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121520, 900000000000121406, 'origin', 'FORWARD_REQUEST'),
  (900000000000121521, 900000000000121406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121522, 900000000000121406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121523, 900000000000121406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121524, 900000000000121406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121525, 900000000000121407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121526, 900000000000121407, 'origin', 'FORWARD_REQUEST'),
  (900000000000121527, 900000000000121407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121528, 900000000000121407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121529, 900000000000121407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121530, 900000000000121407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121531, 900000000000121408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121532, 900000000000121408, 'origin', 'FORWARD_REQUEST'),
  (900000000000121533, 900000000000121408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121534, 900000000000121408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121535, 900000000000121408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121536, 900000000000121408, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000121601, 900000000000121401, 'fiscalCalendarId'),
  (900000000000121602, 900000000000121402, 'page'),
  (900000000000121603, 900000000000121402, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000121701, 900000000000121401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121702, 900000000000121401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121703, 900000000000121401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121704, 900000000000121401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121705, 900000000000121401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000121706, 900000000000121401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121707, 900000000000121402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121708, 900000000000121402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121709, 900000000000121402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121710, 900000000000121402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121711, 900000000000121402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000121712, 900000000000121402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121713, 900000000000121403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121714, 900000000000121403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121715, 900000000000121403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121716, 900000000000121403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121717, 900000000000121403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000121718, 900000000000121403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121719, 900000000000121404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121720, 900000000000121404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121721, 900000000000121404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121722, 900000000000121404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121723, 900000000000121404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121724, 900000000000121404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121725, 900000000000121405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121726, 900000000000121405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121727, 900000000000121405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121728, 900000000000121405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121729, 900000000000121405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121730, 900000000000121405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121731, 900000000000121406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121732, 900000000000121406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121733, 900000000000121406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121734, 900000000000121406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121735, 900000000000121406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121736, 900000000000121406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121737, 900000000000121407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121738, 900000000000121407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121739, 900000000000121407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121740, 900000000000121407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121741, 900000000000121407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121742, 900000000000121407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121743, 900000000000121408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121744, 900000000000121408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121745, 900000000000121408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121746, 900000000000121408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121747, 900000000000121408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121748, 900000000000121408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance accounting rule bundle console (2026-10-20, requirements/20261020/finance-记账规则包维护).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102, OPERATIONS_FINANCE 900000000000097301) and the manual adjustment block above
-- (FINANCE_ACCOUNTING_APPROVE 900000000000120101).
-- No new permission or role grant. Dormant: menu/buttons/routes DISABLED; activate them in a separate reviewed change.
-- BFF routes forward to ledger (AccountingRuleBundleApi); bundles themselves are still defined only by initialization data.
-- Rows: console_resource 3, gateway_route 8, route_header 36, guard_rule 48 (95).
-- ID block 122: 1223xx console_resource,
-- 1224xx route, 1225xx route_header, 1227xx guard_rule.
-- Rollback: delete the rows by the 900000000000122301-900000000000122799 IDs below (children first: guard_rule,
-- route_header, gateway_route, console_resource).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_rule_console_time = UTC_TIMESTAMP(6);
SET @finance_rule_console_actor_id = 1;
SET @finance_rule_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000122301, 'OPERATIONS_FINANCE_ACCOUNTING_RULES', 'OPERATIONS', 'MENU', 900000000000097301, '记账规则', '/finance/accounting-rules', 'OPERATIONS_FINANCE_ACCOUNTING_RULES', 900000000000097101, 70, 'DISABLED', 1, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122302, 'OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000122301, '制单', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122303, 'OPERATIONS_FINANCE_ACCOUNTING_RULES_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000122301, '复核', NULL, NULL, 900000000000120101, 20, 'DISABLED', 1, @finance_rule_console_time, @finance_rule_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000122401, 'operations-finance-rule-bundle-list', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-rule-bundles/v1', '/finance/accounting-rule-bundles/v1', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122402, 'operations-finance-rule-bundle-detail', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}', '/finance/accounting-rule-bundles/v1/{bundleCode}', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122403, 'operations-finance-rule-version-detail', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122404, 'operations-finance-rule-version-create', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122405, 'operations-finance-rule-version-submit', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/submit', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/submit', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122406, 'operations-finance-rule-version-approve', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/approve', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/approve', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122407, 'operations-finance-rule-version-reject', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/reject', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/reject', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122408, 'operations-finance-rule-version-void', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000122501, 900000000000122401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122502, 900000000000122401, 'origin', 'FORWARD_REQUEST'),
  (900000000000122503, 900000000000122402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122504, 900000000000122402, 'origin', 'FORWARD_REQUEST'),
  (900000000000122505, 900000000000122403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122506, 900000000000122403, 'origin', 'FORWARD_REQUEST'),
  (900000000000122507, 900000000000122404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122508, 900000000000122404, 'origin', 'FORWARD_REQUEST'),
  (900000000000122509, 900000000000122404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122510, 900000000000122404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122511, 900000000000122404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122512, 900000000000122404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122513, 900000000000122405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122514, 900000000000122405, 'origin', 'FORWARD_REQUEST'),
  (900000000000122515, 900000000000122405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122516, 900000000000122405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122517, 900000000000122405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122518, 900000000000122405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122519, 900000000000122406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122520, 900000000000122406, 'origin', 'FORWARD_REQUEST'),
  (900000000000122521, 900000000000122406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122522, 900000000000122406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122523, 900000000000122406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122524, 900000000000122406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122525, 900000000000122407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122526, 900000000000122407, 'origin', 'FORWARD_REQUEST'),
  (900000000000122527, 900000000000122407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122528, 900000000000122407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122529, 900000000000122407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122530, 900000000000122407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122531, 900000000000122408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122532, 900000000000122408, 'origin', 'FORWARD_REQUEST'),
  (900000000000122533, 900000000000122408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122534, 900000000000122408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122535, 900000000000122408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122536, 900000000000122408, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000122701, 900000000000122401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122702, 900000000000122401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122703, 900000000000122401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122704, 900000000000122401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122705, 900000000000122401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000122706, 900000000000122401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122707, 900000000000122402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122708, 900000000000122402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122709, 900000000000122402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122710, 900000000000122402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122711, 900000000000122402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000122712, 900000000000122402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122713, 900000000000122403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122714, 900000000000122403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122715, 900000000000122403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122716, 900000000000122403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122717, 900000000000122403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000122718, 900000000000122403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122719, 900000000000122404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122720, 900000000000122404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122721, 900000000000122404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122722, 900000000000122404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122723, 900000000000122404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122724, 900000000000122404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122725, 900000000000122405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122726, 900000000000122405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122727, 900000000000122405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122728, 900000000000122405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122729, 900000000000122405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122730, 900000000000122405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122731, 900000000000122406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122732, 900000000000122406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122733, 900000000000122406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122734, 900000000000122406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122735, 900000000000122406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122736, 900000000000122406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122737, 900000000000122407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122738, 900000000000122407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122739, 900000000000122407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122740, 900000000000122407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122741, 900000000000122407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122742, 900000000000122407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122743, 900000000000122408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122744, 900000000000122408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122745, 900000000000122408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122746, 900000000000122408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122747, 900000000000122408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122748, 900000000000122408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
