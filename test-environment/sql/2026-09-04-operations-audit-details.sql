-- 一次性手工增量：审计列表筛选及只读详情。
-- 前置：现有 20 条 Operations Route；新增 Route/Header/Guard/Query ID 均未占用。
-- 复用 OPERATIONS_AUDIT_READ，不增加角色授权或修改审计事实，不重放 Schema/Bootstrap。
SET NAMES utf8mb4;
START TRANSACTION;
SET @platform_initial_time = UTC_TIMESTAMP(6);

SELECT route_id FROM t_gateway_route WHERE route_id=900000000000001009 FOR UPDATE;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
    (900000000000001017, 'operations-audit-detail', 'OPERATIONS', 'GET', '/api/operations/audit/v1/events/{auditEventId}', '/audit/v1/events/{auditEventId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000523, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'ACTIVE', 1, 1, 1, @platform_initial_time, @platform_initial_time);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
    (900000000000005050, 900000000000001017, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005051, 900000000000001017, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
    (900000000000006060, 900000000000001017, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006061, 900000000000001017, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006062, 900000000000001017, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006063, 900000000000001017, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006064, 900000000000001017, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006065, 900000000000001017, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name) VALUES
    (900000000000004006, 900000000000001009, 'occurredFrom'),
    (900000000000004007, 900000000000001009, 'occurredTo'),
    (900000000000004008, 900000000000001009, 'actorId'),
    (900000000000004009, 900000000000001009, 'actionCode'),
    (900000000000004010, 900000000000001009, 'result'),
    (900000000000004011, 900000000000001009, 'requestId');

UPDATE t_gateway_route SET version=version+1,update_time=@platform_initial_time
WHERE route_id=900000000000001009 AND route_code='operations-audit-list' AND status='ACTIVE';

COMMIT;

SELECT route_code,http_method,external_path_template,permission_id FROM t_gateway_route
WHERE route_id IN (900000000000001009,900000000000001017);
