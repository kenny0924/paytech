-- 发布副本（3DS 全链路 + 卡 BIN 库合并发布，阶段 6）。目标库：mor_platform。来源：requirements/20261022/card-BIN库与卡属性/db/platform-support/dml.sql（SHA-256 3c0afbdb286f4ac3，BIN 需求 db）。
-- 以下为来源文件第 3 段（第 288—447 行，注册块 132，休眠）逐字副本，未改动业务语句。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 3 段：Operations card BIN library console（2026-10-22，requirements/20261022/card-BIN库与卡属性）。
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menus and routes DISABLED;
-- activate them in a separate reviewed change. BFF routes forward to operations-console-bff on /card-admin/v1/bin-library/...,
-- which calls card-service (/card/v1/bin-library/...) and, for uploads, Platform InternalFileApi with reasonCode
-- CARD_BIN_LIBRARY_IMPORT; operator identity comes from the Gateway actor headers.
-- Permissions: CARD_BIN_LIBRARY_READ (LOW: version list/detail/errors, activation history, BIN lookup),
-- CARD_BIN_LIBRARY_MANAGE (HIGH: upload, create version, activate/rollback).
-- Menu: 卡 BIN 库 /card-bin-library under 平台配置 (900000000000000562, sort 90); button 维护 gates the write actions.
-- Routes: 9; headers and guard rules copy block 131 (GET: authorization + origin; POST: plus content-type and
-- idempotency-key; guard rules SHARED_ROUTE 300/min + 50 concurrent, CLIENT_IP 30/min + 3, principal 30/min (writes 10/min) + 2).
-- The CSV body never passes Gateway: the browser PUTs it straight to object storage with the Platform-issued authorization.
-- Rows: permission 2, role_permission 2, console_resource 2, gateway_route 9, route_header 34, query_param 8,
-- guard_rule 54 (111). ID block 132: 1321xx permission, 1322xx role_permission, 1323xx console_resource, 1324xx route,
-- 1325xx route_header, 1326xx query_param, 1327xx-1328xx guard_rule, 1329xx file scan policy (第 2 段).
-- Before applying, re-check on the live mor_platform that no row in 900000000000132001-900000000000132999 exists and that
-- the permission codes, resource codes, route codes and (method, external path) pairs above are unused.
-- Rollback: delete the rows by the 900000000000132001-900000000000132899 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource, role_permission, permission).
-- Activation (separate reviewed change, after Card/ops-bff/ops-web are deployed):
--   UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_permission_id IN (900000000000132201,900000000000132202);
--   UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE resource_id IN (900000000000132301,900000000000132302);
--   UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id BETWEEN 900000000000132401 AND 900000000000132409;
--   then republish the Gateway Snapshot.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @card_bin_console_time = UTC_TIMESTAMP(6);
SET @card_bin_console_actor_id = 1;
SET @card_bin_console_target_id = 900000000000000001;
SET @card_bin_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000132101, 'CARD_BIN_LIBRARY_READ', 'CARD_BIN_LIBRARY', 'READ', 'LOW', 'ACTIVE', 1, @card_bin_console_time, @card_bin_console_time),
  (900000000000132102, 'CARD_BIN_LIBRARY_MANAGE', 'CARD_BIN_LIBRARY', 'MANAGE', 'HIGH', 'ACTIVE', 1, @card_bin_console_time, @card_bin_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000132201, @card_bin_console_role_id, 900000000000132101, 'REVOKED', 1, @card_bin_console_time, @card_bin_console_time),
  (900000000000132202, @card_bin_console_role_id, 900000000000132102, 'REVOKED', 1, @card_bin_console_time, @card_bin_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000132301, 'OPERATIONS_CARD_BIN_LIBRARY', 'OPERATIONS', 'MENU', 900000000000000562, '卡 BIN 库', '/card-bin-library', 'OPERATIONS_CARD_BIN_LIBRARY', 900000000000132101, 90, 'DISABLED', 1, @card_bin_console_time, @card_bin_console_time),
  (900000000000132302, 'OPERATIONS_CARD_BIN_LIBRARY_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000132301, '维护', NULL, NULL, 900000000000132102, 10, 'DISABLED', 1, @card_bin_console_time, @card_bin_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000132401, 'operations-card-bin-version-list', 'OPERATIONS', 'GET', '/api/operations/card-admin/v1/bin-library/versions', '/card-admin/v1/bin-library/versions', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132402, 'operations-card-bin-version-detail', 'OPERATIONS', 'GET', '/api/operations/card-admin/v1/bin-library/versions/{versionNo}', '/card-admin/v1/bin-library/versions/{versionNo}', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132403, 'operations-card-bin-version-errors', 'OPERATIONS', 'GET', '/api/operations/card-admin/v1/bin-library/versions/{versionNo}/errors', '/card-admin/v1/bin-library/versions/{versionNo}/errors', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132404, 'operations-card-bin-activation-list', 'OPERATIONS', 'GET', '/api/operations/card-admin/v1/bin-library/activations', '/card-admin/v1/bin-library/activations', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132405, 'operations-card-bin-lookup', 'OPERATIONS', 'GET', '/api/operations/card-admin/v1/bin-library/bins/{bin}', '/card-admin/v1/bin-library/bins/{bin}', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132406, 'operations-card-bin-upload-start', 'OPERATIONS', 'POST', '/api/operations/card-admin/v1/bin-library/uploads', '/card-admin/v1/bin-library/uploads', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132407, 'operations-card-bin-upload-complete', 'OPERATIONS', 'POST', '/api/operations/card-admin/v1/bin-library/uploads/{uploadId}/completion', '/card-admin/v1/bin-library/uploads/{uploadId}/completion', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132408, 'operations-card-bin-version-create', 'OPERATIONS', 'POST', '/api/operations/card-admin/v1/bin-library/versions', '/card-admin/v1/bin-library/versions', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time),
  (900000000000132409, 'operations-card-bin-version-activate', 'OPERATIONS', 'POST', '/api/operations/card-admin/v1/bin-library/versions/{versionNo}/activation', '/card-admin/v1/bin-library/versions/{versionNo}/activation', @card_bin_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @card_bin_console_actor_id, @card_bin_console_actor_id, @card_bin_console_time, @card_bin_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000132501, 900000000000132401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132502, 900000000000132401, 'origin', 'FORWARD_REQUEST'),
  (900000000000132503, 900000000000132402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132504, 900000000000132402, 'origin', 'FORWARD_REQUEST'),
  (900000000000132505, 900000000000132403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132506, 900000000000132403, 'origin', 'FORWARD_REQUEST'),
  (900000000000132507, 900000000000132404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132508, 900000000000132404, 'origin', 'FORWARD_REQUEST'),
  (900000000000132509, 900000000000132405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132510, 900000000000132405, 'origin', 'FORWARD_REQUEST'),
  (900000000000132511, 900000000000132406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132512, 900000000000132406, 'origin', 'FORWARD_REQUEST'),
  (900000000000132513, 900000000000132406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000132514, 900000000000132406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000132515, 900000000000132406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000132516, 900000000000132406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000132517, 900000000000132407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132518, 900000000000132407, 'origin', 'FORWARD_REQUEST'),
  (900000000000132519, 900000000000132407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000132520, 900000000000132407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000132521, 900000000000132407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000132522, 900000000000132407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000132523, 900000000000132408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132524, 900000000000132408, 'origin', 'FORWARD_REQUEST'),
  (900000000000132525, 900000000000132408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000132526, 900000000000132408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000132527, 900000000000132408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000132528, 900000000000132408, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000132529, 900000000000132409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000132530, 900000000000132409, 'origin', 'FORWARD_REQUEST'),
  (900000000000132531, 900000000000132409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000132532, 900000000000132409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000132533, 900000000000132409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000132534, 900000000000132409, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000132601, 900000000000132401, 'status'),
  (900000000000132602, 900000000000132401, 'page'),
  (900000000000132603, 900000000000132401, 'size'),
  (900000000000132604, 900000000000132403, 'page'),
  (900000000000132605, 900000000000132403, 'size'),
  (900000000000132606, 900000000000132404, 'page'),
  (900000000000132607, 900000000000132404, 'size'),
  (900000000000132608, 900000000000132405, 'versionNo');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000132701, 900000000000132401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132702, 900000000000132401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132703, 900000000000132401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132704, 900000000000132401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132705, 900000000000132401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000132706, 900000000000132401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132707, 900000000000132402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132708, 900000000000132402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132709, 900000000000132402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132710, 900000000000132402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132711, 900000000000132402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000132712, 900000000000132402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132713, 900000000000132403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132714, 900000000000132403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132715, 900000000000132403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132716, 900000000000132403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132717, 900000000000132403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000132718, 900000000000132403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132719, 900000000000132404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132720, 900000000000132404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132721, 900000000000132404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132722, 900000000000132404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132723, 900000000000132404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000132724, 900000000000132404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132725, 900000000000132405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132726, 900000000000132405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132727, 900000000000132405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132728, 900000000000132405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132729, 900000000000132405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000132730, 900000000000132405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132731, 900000000000132406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132732, 900000000000132406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132733, 900000000000132406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132734, 900000000000132406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132735, 900000000000132406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000132736, 900000000000132406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132737, 900000000000132407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132738, 900000000000132407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132739, 900000000000132407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132740, 900000000000132407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132741, 900000000000132407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000132742, 900000000000132407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132743, 900000000000132408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132744, 900000000000132408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132745, 900000000000132408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132746, 900000000000132408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132747, 900000000000132408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000132748, 900000000000132408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000132749, 900000000000132409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000132750, 900000000000132409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000132751, 900000000000132409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000132752, 900000000000132409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000132753, 900000000000132409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000132754, 900000000000132409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
