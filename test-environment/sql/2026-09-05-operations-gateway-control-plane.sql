-- 手工增量：Operations「平台配置 > API 路由管理」V1。
--
-- 执行边界：
-- 1. 仅用于已完成 2026-09-04 Operations 目录增量的 mor-test；不重放 Schema、全量初始化或 Bootstrap。
-- 2. 使用默认遇错停止的 MySQL 批处理客户端，不使用 --force；任何唯一键冲突都必须使事务失败并回滚。
-- 3. 本脚本可重复执行：完全相同的数据不会重复插入，也不会恢复人工停用的状态或推进版本。
-- 4. 应用代码、容器初始化和测试不得自动执行本脚本。

SET NAMES utf8mb4;

START TRANSACTION;

SET @gateway_admin_time = UTC_TIMESTAMP(6);
SET @gateway_admin_actor_id = 1;
SET @gateway_admin_read_permission_id = 900000000000000534;
SET @gateway_admin_manage_permission_id = 900000000000000535;
SET @gateway_admin_target_id = (
    SELECT target_id FROM t_gateway_target
    WHERE target_id = 900000000000000001
      AND target_service_code = 'operations-console-bff'
      AND discovery_service_name = 'operations-console-bff'
      AND channel = 'OPERATIONS'
);
SET @gateway_admin_role_id = (
    SELECT role_id FROM t_role
    WHERE role_id = 900000000000000510
      AND role_code = 'PLATFORM_ADMIN'
      AND tenant_scope = 'PLATFORM'
      AND tenant_id IS NULL
      AND scope_key = 'PLATFORM'
      AND system_role = 1
);
SET @gateway_viewer_role_id = (
    SELECT role_id FROM t_role
    WHERE role_id = 900000000000000511
      AND role_code = 'OPERATIONS_VIEWER'
      AND tenant_scope = 'PLATFORM'
      AND tenant_id IS NULL
      AND scope_key = 'PLATFORM'
      AND system_role = 1
);

-- 锁定本次目录依赖的稳定 Root；缺失或身份不匹配会令变量为 NULL，后续 NOT NULL 约束使事务失败。
SELECT target_id
FROM t_gateway_target
WHERE target_id = @gateway_admin_target_id
  AND target_service_code = 'operations-console-bff'
  AND discovery_service_name = 'operations-console-bff'
  AND channel = 'OPERATIONS'
FOR UPDATE;

SELECT role_id
FROM t_role
WHERE role_id IN (@gateway_admin_role_id, @gateway_viewer_role_id)
  AND tenant_scope = 'PLATFORM'
  AND tenant_id IS NULL
  AND scope_key = 'PLATFORM'
  AND system_role = 1
FOR UPDATE;

INSERT INTO t_permission (
    permission_id, permission_code, resource_type, action_code, risk_level,
    status, version, create_time, update_time
)
SELECT @gateway_admin_read_permission_id, 'GATEWAY_CONFIG_READ', 'GATEWAY_CONFIGURATION', 'READ', 'LOW',
       'ACTIVE', 1, @gateway_admin_time, @gateway_admin_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_id = @gateway_admin_read_permission_id
      AND permission_code = 'GATEWAY_CONFIG_READ'
      AND resource_type = 'GATEWAY_CONFIGURATION'
      AND action_code = 'READ'
      AND risk_level = 'LOW'
);

INSERT INTO t_permission (
    permission_id, permission_code, resource_type, action_code, risk_level,
    status, version, create_time, update_time
)
SELECT @gateway_admin_manage_permission_id, 'GATEWAY_CONFIG_MANAGE', 'GATEWAY_CONFIGURATION', 'MANAGE', 'HIGH',
       'ACTIVE', 1, @gateway_admin_time, @gateway_admin_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_id = @gateway_admin_manage_permission_id
      AND permission_code = 'GATEWAY_CONFIG_MANAGE'
      AND resource_type = 'GATEWAY_CONFIGURATION'
      AND action_code = 'MANAGE'
      AND risk_level = 'HIGH'
);

SET @gateway_admin_grants_added = 0;

INSERT INTO t_role_permission (
    role_permission_id, role_id, permission_id, status, version, create_time, update_time
)
SELECT 900000000000000578, @gateway_admin_role_id, @gateway_admin_read_permission_id,
       'ACTIVE', 1, @gateway_admin_time, @gateway_admin_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission
    WHERE role_permission_id = 900000000000000578
      AND role_id = @gateway_admin_role_id
      AND permission_id = @gateway_admin_read_permission_id
);
SET @gateway_admin_grants_added = @gateway_admin_grants_added + ROW_COUNT();

INSERT INTO t_role_permission (
    role_permission_id, role_id, permission_id, status, version, create_time, update_time
)
SELECT 900000000000000579, @gateway_admin_role_id, @gateway_admin_manage_permission_id,
       'ACTIVE', 1, @gateway_admin_time, @gateway_admin_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission
    WHERE role_permission_id = 900000000000000579
      AND role_id = @gateway_admin_role_id
      AND permission_id = @gateway_admin_manage_permission_id
);
SET @gateway_admin_grants_added = @gateway_admin_grants_added + ROW_COUNT();

SET @gateway_viewer_grants_added = 0;

INSERT INTO t_role_permission (
    role_permission_id, role_id, permission_id, status, version, create_time, update_time
)
SELECT 900000000000000580, @gateway_viewer_role_id, @gateway_admin_read_permission_id,
       'ACTIVE', 1, @gateway_admin_time, @gateway_admin_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission
    WHERE role_permission_id = 900000000000000580
      AND role_id = @gateway_viewer_role_id
      AND permission_id = @gateway_admin_read_permission_id
);
SET @gateway_viewer_grants_added = @gateway_viewer_grants_added + ROW_COUNT();

UPDATE t_role
SET version = version + 1,
    update_time = @gateway_admin_time
WHERE role_id = @gateway_admin_role_id
  AND @gateway_admin_grants_added > 0;

UPDATE t_role
SET version = version + 1,
    update_time = @gateway_admin_time
WHERE role_id = @gateway_viewer_role_id
  AND @gateway_viewer_grants_added > 0;

INSERT INTO t_console_resource (
    resource_id, resource_code, channel, resource_type, parent_resource_id,
    display_name, frontend_route, component_key, permission_id, sort_order,
    status, version, create_time, update_time
)
SELECT seed.resource_id, seed.resource_code, seed.channel, seed.resource_type, seed.parent_resource_id,
       seed.display_name, seed.frontend_route, seed.component_key, seed.permission_id, seed.sort_order,
       'ACTIVE', 1, @gateway_admin_time, @gateway_admin_time
FROM (
    SELECT 900000000000000562 AS resource_id, 'OPERATIONS_PLATFORM_CONFIGURATION' AS resource_code,
           'OPERATIONS' AS channel, 'MENU' AS resource_type, NULL AS parent_resource_id,
           '平台配置' AS display_name, NULL AS frontend_route, NULL AS component_key,
           900000000000000534 AS permission_id, 20 AS sort_order
    UNION ALL
    SELECT 900000000000000563, 'OPERATIONS_GATEWAY_CONFIGURATION', 'OPERATIONS', 'MENU',
           900000000000000562, 'API 路由管理', '/gateway-configuration',
           'OPERATIONS_GATEWAY_CONFIGURATION', 900000000000000534, 10
    UNION ALL
    SELECT 900000000000000564, 'OPERATIONS_GATEWAY_CONFIGURATION_MANAGE', 'OPERATIONS', 'BUTTON',
           900000000000000563, '管理配置', NULL, NULL, 900000000000000535, 10
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource existing
    WHERE existing.resource_id = seed.resource_id
      AND existing.resource_code = seed.resource_code
      AND existing.channel = seed.channel
      AND existing.resource_type = seed.resource_type
      AND (existing.parent_resource_id <=> seed.parent_resource_id)
      AND existing.display_name = seed.display_name
      AND (existing.frontend_route <=> seed.frontend_route)
      AND (existing.component_key <=> seed.component_key)
      AND existing.permission_id = seed.permission_id
      AND existing.sort_order = seed.sort_order
);

INSERT INTO t_gateway_route (
    route_id, route_code, channel, http_method, external_path_template, downstream_path_template,
    target_id, security_profile_code, authorization_mode, permission_id, required_scope_code,
    request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis,
    response_timeout_millis, status, version, created_by, updated_by, create_time, update_time
)
SELECT seed.route_id, seed.route_code, 'OPERATIONS', seed.http_method,
       seed.external_path_template, seed.downstream_path_template, @gateway_admin_target_id,
       'OPERATIONS_JWT', 'HUMAN_PERMISSION', seed.permission_id, NULL,
       'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1,
       @gateway_admin_actor_id, @gateway_admin_actor_id, @gateway_admin_time, @gateway_admin_time
FROM (
    SELECT 900000000000001018 AS route_id, 'operations-gateway-target-list' AS route_code,
           'GET' AS http_method,
           '/api/operations/gateway-admin/v1/target-services' AS external_path_template,
           '/gateway-admin/v1/target-services' AS downstream_path_template,
           900000000000000534 AS permission_id
    UNION ALL
    SELECT 900000000000001019, 'operations-gateway-target-detail', 'GET',
           '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}',
           '/gateway-admin/v1/target-services/{targetServiceCode}', 900000000000000534
    UNION ALL
    SELECT 900000000000001020, 'operations-gateway-target-status', 'PATCH',
           '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}/status',
           '/gateway-admin/v1/target-services/{targetServiceCode}/status', 900000000000000535
    UNION ALL
    SELECT 900000000000001021, 'operations-gateway-route-list', 'GET',
           '/api/operations/gateway-admin/v1/routes',
           '/gateway-admin/v1/routes', 900000000000000534
    UNION ALL
    SELECT 900000000000001022, 'operations-gateway-route-detail', 'GET',
           '/api/operations/gateway-admin/v1/routes/{routeCode}',
           '/gateway-admin/v1/routes/{routeCode}', 900000000000000534
    UNION ALL
    SELECT 900000000000001023, 'operations-gateway-route-status', 'PATCH',
           '/api/operations/gateway-admin/v1/routes/{routeCode}/status',
           '/gateway-admin/v1/routes/{routeCode}/status', 900000000000000535
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route existing
    WHERE existing.route_id = seed.route_id
      AND existing.route_code = seed.route_code
      AND existing.channel = 'OPERATIONS'
      AND existing.http_method = seed.http_method
      AND existing.external_path_template = seed.external_path_template
      AND existing.downstream_path_template = seed.downstream_path_template
      AND existing.target_id = @gateway_admin_target_id
      AND existing.security_profile_code = 'OPERATIONS_JWT'
      AND existing.authorization_mode = 'HUMAN_PERMISSION'
      AND existing.permission_id = seed.permission_id
      AND existing.required_scope_code IS NULL
      AND existing.request_cookie_mode = 'DROP'
      AND existing.response_cookie_mode = 'NONE'
      AND existing.body_limit_bytes = 16384
      AND existing.body_receive_timeout_millis = 5000
      AND existing.response_timeout_millis = 10000
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT seed.route_header_id, seed.route_id, seed.header_name, seed.header_use
FROM (
    SELECT 900000000000005060 AS route_header_id, 900000000000001018 AS route_id,
           'authorization' AS header_name, 'CORS_REQUEST_ALLOWED' AS header_use
    UNION ALL SELECT 900000000000005061, 900000000000001018, 'origin', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005062, 900000000000001019, 'authorization', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005063, 900000000000001019, 'origin', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005064, 900000000000001020, 'authorization', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005065, 900000000000001020, 'origin', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005066, 900000000000001020, 'content-type', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005067, 900000000000001020, 'content-type', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005068, 900000000000001020, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005069, 900000000000001020, 'idempotency-key', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005070, 900000000000001021, 'authorization', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005071, 900000000000001021, 'origin', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005072, 900000000000001022, 'authorization', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005073, 900000000000001022, 'origin', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005074, 900000000000001023, 'authorization', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005075, 900000000000001023, 'origin', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005076, 900000000000001023, 'content-type', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005077, 900000000000001023, 'content-type', 'FORWARD_REQUEST'
    UNION ALL SELECT 900000000000005078, 900000000000001023, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
    UNION ALL SELECT 900000000000005079, 900000000000001023, 'idempotency-key', 'FORWARD_REQUEST'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header existing
    WHERE existing.route_header_id = seed.route_header_id
      AND existing.route_id = seed.route_id
      AND existing.header_name = seed.header_name
      AND existing.header_use = seed.header_use
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT seed.route_query_param_id, seed.route_id, seed.parameter_name
FROM (
    SELECT 900000000000004020 AS route_query_param_id, 900000000000001018 AS route_id, 'page' AS parameter_name
    UNION ALL SELECT 900000000000004021, 900000000000001018, 'size'
    UNION ALL SELECT 900000000000004022, 900000000000001018, 'channel'
    UNION ALL SELECT 900000000000004023, 900000000000001018, 'status'
    UNION ALL SELECT 900000000000004024, 900000000000001018, 'targetServiceCode'
    UNION ALL SELECT 900000000000004025, 900000000000001021, 'page'
    UNION ALL SELECT 900000000000004026, 900000000000001021, 'size'
    UNION ALL SELECT 900000000000004027, 900000000000001021, 'channel'
    UNION ALL SELECT 900000000000004028, 900000000000001021, 'status'
    UNION ALL SELECT 900000000000004029, 900000000000001021, 'targetServiceCode'
    UNION ALL SELECT 900000000000004030, 900000000000001021, 'routeCode'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param existing
    WHERE existing.route_query_param_id = seed.route_query_param_id
      AND existing.route_id = seed.route_id
      AND existing.parameter_name = seed.parameter_name
);

INSERT INTO t_gateway_guard_rule (
    guard_rule_id, route_id, rule_type, dimension,
    capacity, refill_tokens, refill_period_millis, concurrency_limit
)
SELECT 900000000000006070 + ((route_seed.route_sequence - 1) * 6) + rule_seed.rule_sequence,
       route_seed.route_id, rule_seed.rule_type, rule_seed.dimension,
       rule_seed.capacity, rule_seed.refill_tokens, rule_seed.refill_period_millis,
       rule_seed.concurrency_limit
FROM (
    SELECT 1 AS route_sequence, 900000000000001018 AS route_id
    UNION ALL SELECT 2, 900000000000001019
    UNION ALL SELECT 3, 900000000000001020
    UNION ALL SELECT 4, 900000000000001021
    UNION ALL SELECT 5, 900000000000001022
    UNION ALL SELECT 6, 900000000000001023
) AS route_seed
CROSS JOIN (
    SELECT 0 AS rule_sequence, 'RATE_LIMIT' AS rule_type, 'SHARED_ROUTE' AS dimension,
           300 AS capacity, 300 AS refill_tokens, 60000 AS refill_period_millis, NULL AS concurrency_limit
    UNION ALL SELECT 1, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
    UNION ALL SELECT 2, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
    UNION ALL SELECT 3, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
    UNION ALL SELECT 4, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
    UNION ALL SELECT 5, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
) AS rule_seed
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule existing
    WHERE existing.guard_rule_id = 900000000000006070 + ((route_seed.route_sequence - 1) * 6) + rule_seed.rule_sequence
      AND existing.route_id = route_seed.route_id
      AND existing.rule_type = rule_seed.rule_type
      AND existing.dimension = rule_seed.dimension
      AND (existing.capacity <=> rule_seed.capacity)
      AND (existing.refill_tokens <=> rule_seed.refill_tokens)
      AND (existing.refill_period_millis <=> rule_seed.refill_period_millis)
      AND (existing.concurrency_limit <=> rule_seed.concurrency_limit)
);

COMMIT;

-- 操作员复核：应返回 2 项 Permission、3 项 Console Resource、6 条 Route；每条 Route 有 6 条 Guard。
SELECT permission_id, permission_code, resource_type, action_code, risk_level, status
FROM t_permission
WHERE permission_code IN ('GATEWAY_CONFIG_READ', 'GATEWAY_CONFIG_MANAGE')
ORDER BY permission_id;

SELECT resource_id, resource_code, resource_type, parent_resource_id, display_name,
       frontend_route, component_key, permission_id, status, version
FROM t_console_resource
WHERE resource_code IN (
    'OPERATIONS_PLATFORM_CONFIGURATION',
    'OPERATIONS_GATEWAY_CONFIGURATION',
    'OPERATIONS_GATEWAY_CONFIGURATION_MANAGE'
)
ORDER BY resource_id;

SELECT route.route_id, route.route_code, route.http_method, route.external_path_template,
       route.downstream_path_template, route.permission_id, route.status, route.version,
       COUNT(DISTINCT header.route_header_id) AS header_count,
       COUNT(DISTINCT query_param.route_query_param_id) AS query_parameter_count,
       COUNT(DISTINCT guard.guard_rule_id) AS guard_count
FROM t_gateway_route route
LEFT JOIN t_gateway_route_header header ON header.route_id = route.route_id
LEFT JOIN t_gateway_route_query_param query_param ON query_param.route_id = route.route_id
LEFT JOIN t_gateway_guard_rule guard ON guard.route_id = route.route_id
WHERE route.route_id BETWEEN 900000000000001018 AND 900000000000001023
GROUP BY route.route_id, route.route_code, route.http_method, route.external_path_template,
         route.downstream_path_template, route.permission_id, route.status, route.version
ORDER BY route.route_id;

-- 回滚说明（仅在这些 Route 尚未承接流量，且没有基于新权限产生角色/审计/幂等业务事实时使用）：
-- 1. START TRANSACTION，并按 route_id 先删除 t_gateway_guard_rule、t_gateway_route_query_param、t_gateway_route_header。
-- 2. 删除 route_id 900000000000001018..900000000000001023。
-- 3. 按子到父顺序删除 resource_id 900000000000000564、563、562。
-- 4. 删除 role_permission_id 900000000000000578..580；仅对确实删除了 Grant 的角色推进一次 version。
-- 5. 删除 permission_id 900000000000000534、535，最后 COMMIT，并重新查询当前 Gateway Snapshot。
