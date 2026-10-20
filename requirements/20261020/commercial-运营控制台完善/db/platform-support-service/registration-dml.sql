-- Reading copy only. The single execution source is the Operations commercial console I1 block (ID block 95, second part) at the
-- end of paytech/platform-support/docs/database/sql/platform-support-initial-data.sql; do not apply this file separately.

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
