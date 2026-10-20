-- Reading copy only. The single execution sources are the Operations commercial console I1 block (ID block 95, second part) and I2 block (ID block 93) at the
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
