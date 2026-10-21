-- 币种页注册块 125（休眠）：mor-test 阶段 7 在 mor_platform 执行（root，客户端 utf8mb4）。
-- 内容逐字取自 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾「Operations currency spec console」块；
-- 自带事务，54 行。前置：changes/2026-10-20-currency-spec-version.sql 已执行；00-precheck 块 125 为空。
SET NAMES utf8mb4;

-- Operations currency spec console (2026-10-20, requirements/20261020/platform-support-币种主数据维护与读取缓存).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Requires t_currency_spec.version (changes/2026-10-20-currency-spec-version.sql) for the Platform code it activates.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menu/button/routes
-- DISABLED; activate them in a separate reviewed change (release/test-activate.sql). BFF routes forward to
-- operations-console-bff (/currency-admin/v1/currency-specs), which reads CurrencySpecQueryApi and writes
-- CurrencySpecManagementApi on platform-support-service; operator identity comes from the Gateway actor headers.
-- The page sits under 平台配置 (OPERATIONS_PLATFORM_CONFIGURATION 900000000000000562).
-- Rows: permission 2, role_permission 2, console_resource 2, gateway_route 4, route_header 20, guard_rule 24 (54).
-- ID block 125: 1251xx permission, 1252xx role_permission, 1253xx console_resource, 1254xx route,
-- 1255xx route_header, 1257xx guard_rule.
-- Rollback: delete the rows by the 900000000000125101-900000000000125799 IDs below (children first: guard_rule,
-- route_header, gateway_route, console_resource, role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @currency_spec_console_time = UTC_TIMESTAMP(6);
SET @currency_spec_console_actor_id = 1;
SET @currency_spec_console_target_id = 900000000000000001;
SET @currency_spec_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000125101, 'CURRENCY_SPEC_READ', 'CURRENCY_SPEC', 'READ', 'LOW', 'ACTIVE', 1, @currency_spec_console_time, @currency_spec_console_time),
  (900000000000125102, 'CURRENCY_SPEC_MANAGE', 'CURRENCY_SPEC', 'MANAGE', 'HIGH', 'ACTIVE', 1, @currency_spec_console_time, @currency_spec_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000125201, @currency_spec_console_role_id, 900000000000125101, 'REVOKED', 1, @currency_spec_console_time, @currency_spec_console_time),
  (900000000000125202, @currency_spec_console_role_id, 900000000000125102, 'REVOKED', 1, @currency_spec_console_time, @currency_spec_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000125301, 'OPERATIONS_CURRENCY_SPECS', 'OPERATIONS', 'MENU', 900000000000000562, '币种', '/currency-specs', 'OPERATIONS_CURRENCY_SPECS', 900000000000125101, 70, 'DISABLED', 1, @currency_spec_console_time, @currency_spec_console_time),
  (900000000000125302, 'OPERATIONS_CURRENCY_SPEC_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000125301, '维护', NULL, NULL, 900000000000125102, 10, 'DISABLED', 1, @currency_spec_console_time, @currency_spec_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000125401, 'operations-currency-spec-list', 'OPERATIONS', 'GET', '/api/operations/currency-admin/v1/currency-specs', '/currency-admin/v1/currency-specs', @currency_spec_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000125101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @currency_spec_console_actor_id, @currency_spec_console_actor_id, @currency_spec_console_time, @currency_spec_console_time),
  (900000000000125402, 'operations-currency-spec-create', 'OPERATIONS', 'POST', '/api/operations/currency-admin/v1/currency-specs', '/currency-admin/v1/currency-specs', @currency_spec_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000125102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @currency_spec_console_actor_id, @currency_spec_console_actor_id, @currency_spec_console_time, @currency_spec_console_time),
  (900000000000125403, 'operations-currency-spec-update', 'OPERATIONS', 'PUT', '/api/operations/currency-admin/v1/currency-specs/{currency}', '/currency-admin/v1/currency-specs/{currency}', @currency_spec_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000125102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @currency_spec_console_actor_id, @currency_spec_console_actor_id, @currency_spec_console_time, @currency_spec_console_time),
  (900000000000125404, 'operations-currency-spec-status', 'OPERATIONS', 'PATCH', '/api/operations/currency-admin/v1/currency-specs/{currency}/status', '/currency-admin/v1/currency-specs/{currency}/status', @currency_spec_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000125102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @currency_spec_console_actor_id, @currency_spec_console_actor_id, @currency_spec_console_time, @currency_spec_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000125501, 900000000000125401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000125502, 900000000000125401, 'origin', 'FORWARD_REQUEST'),
  (900000000000125503, 900000000000125402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000125504, 900000000000125402, 'origin', 'FORWARD_REQUEST'),
  (900000000000125505, 900000000000125402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000125506, 900000000000125402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000125507, 900000000000125402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000125508, 900000000000125402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000125509, 900000000000125403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000125510, 900000000000125403, 'origin', 'FORWARD_REQUEST'),
  (900000000000125511, 900000000000125403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000125512, 900000000000125403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000125513, 900000000000125403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000125514, 900000000000125403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000125515, 900000000000125404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000125516, 900000000000125404, 'origin', 'FORWARD_REQUEST'),
  (900000000000125517, 900000000000125404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000125518, 900000000000125404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000125519, 900000000000125404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000125520, 900000000000125404, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000125701, 900000000000125401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000125702, 900000000000125401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000125703, 900000000000125401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000125704, 900000000000125401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000125705, 900000000000125401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000125706, 900000000000125401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000125707, 900000000000125402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000125708, 900000000000125402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000125709, 900000000000125402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000125710, 900000000000125402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000125711, 900000000000125402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000125712, 900000000000125402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000125713, 900000000000125403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000125714, 900000000000125403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000125715, 900000000000125403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000125716, 900000000000125403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000125717, 900000000000125403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000125718, 900000000000125403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000125719, 900000000000125404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000125720, 900000000000125404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000125721, 900000000000125404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000125722, 900000000000125404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000125723, 900000000000125404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000125724, 900000000000125404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
