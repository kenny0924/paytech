-- 需求：payment-3DS全链路（requirements/20261022/payment-3DS全链路）
-- 目标库：mor_platform（Platform Support 自有库）
-- 文件性质：未执行草案（阶段 3 评审稿）。阶段 4 追加到 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql
--   作为自包含块 133；需求级执行以本文件为准，执行前须经用户授权。
-- ---------------------------------------------------------------------------
-- Platform ID block 133 (2026-10-22, payment-3DS全链路): public CHANNEL_CALLBACK ingress for channel payment result
-- notifications. One new Gateway Target (channel-connector-service on the CHANNEL_CALLBACK channel), one Route
-- POST /api/channel-callbacks/v1/{channelCode}/{channelAccountId} -> /channel-callbacks/v1/{channelCode}/{channelAccountId},
-- security profile / authorization mode CHANNEL_CALLBACK (no authentication at Gateway; channel-connector verifies the
-- channel signature), no permission or scope, cookies DROP / NONE, idempotency NONE, body limit 65536 bytes.
-- Forwarded request headers: sign (channel signature), content-type. Guards: SHARED_ROUTE rate 600/min and concurrency 50
-- (required), CLIENT_IP rate 120/min. No console resource, permission or role grant: this is a machine-to-machine route.
-- Rows: gateway_target 1, gateway_route 1, route_header 2, guard_rule 3 (7). ID block 133: 1334xx route, 1335xx route_header,
-- 1337xx guard_rule, 1338xx gateway_target (8xx is used because the 1xx-7xx convention has no target slot).
-- The Target is ACTIVE (it is inert without an ACTIVE Route); the Route is inserted DISABLED.
-- Before applying, re-check on the live mor_platform that no row in 900000000000133001-900000000000133999 exists, that
-- target_service_code 'channel-connector-service' and route code 'channel-payment-callback' are unused, and that
-- (POST, /api/channel-callbacks/v1/{channelCode}/{channelAccountId}) is unused.
-- Activation (after the new channel-connector and api-gateway are deployed and the Gateway Snapshot is republished):
--   UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
--   WHERE route_id = 900000000000133401 AND status = 'DISABLED';
-- Deactivation: the same statement with status 'DISABLED' / 'ACTIVE' swapped.
-- Rollback: delete by IDs 900000000000133001-900000000000133999 (children first: guard_rule, route_header, gateway_route,
-- gateway_target), then republish the Gateway Snapshot.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @channel_callback_time = UTC_TIMESTAMP(6);
SET @channel_callback_actor_id = 1;
SET @channel_callback_target_id = 900000000000133801;

INSERT INTO t_gateway_target (target_id,target_service_code,discovery_service_name,channel,status,version,created_by,updated_by,create_time,update_time) VALUES
  (@channel_callback_target_id, 'channel-connector-service', 'channel-connector-service', 'CHANNEL_CALLBACK', 'ACTIVE', 1, @channel_callback_actor_id, @channel_callback_actor_id, @channel_callback_time, @channel_callback_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000133401, 'channel-payment-callback', 'CHANNEL_CALLBACK', 'POST', '/api/channel-callbacks/v1/{channelCode}/{channelAccountId}', '/channel-callbacks/v1/{channelCode}/{channelAccountId}', @channel_callback_target_id, 'CHANNEL_CALLBACK', 'CHANNEL_CALLBACK', NULL, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 5000, 'DISABLED', 1, @channel_callback_actor_id, @channel_callback_actor_id, @channel_callback_time, @channel_callback_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000133501, 900000000000133401, 'sign', 'FORWARD_REQUEST'),
  (900000000000133502, 900000000000133401, 'content-type', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000133701, 900000000000133401, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000133702, 900000000000133401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000133703, 900000000000133401, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  -- 阶段 4 补充：Gateway 要求 CLIENT_IP 限流同时有并发上限，否则激活后整份快照被拒
  (900000000000133704, 900000000000133401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 20);

COMMIT;
