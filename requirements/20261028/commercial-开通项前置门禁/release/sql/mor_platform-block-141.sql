-- 未执行（2026-10-28 阶段 6 评审稿）：commercial：开通项前置门禁 REQ-01，目标库 mor_platform（platform-support-service）。
-- 一段，一个事务：注册块 141（运营合同详情页开通项"暂停 / 恢复 / 检查前置"的 BFF 路由），休眠写入（DISABLED），激活另行执行
-- （mor_platform-block-141-activate.sql）。
-- 不新增权限、菜单或按钮：沿用块 95 的 COMMERCIAL_CONTRACT_MANAGE（900000000000095102，暂停 / 恢复）与
-- COMMERCIAL_CONTRACT_READ（900000000000095101，检查前置；用户 2026-10-28 确认：有合同查看权限即可检查前置）。
-- 待办（阶段 8 / 研发）：同一块、同一 ID 需同步追加到 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾，
-- 本需求研发 worktree（Code-MoR-gate）尚未追加。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Operations commercial capability gate (2026-10-28, requirements/20261028/commercial-开通项前置门禁, technique.md 13.1 / 14).
-- Self-contained block: on an environment already holding block 95 (commercial agreement console), apply ONLY this block.
-- Dormant: routes DISABLED; activate them in a separate reviewed change after commercial / ops-bff are deployed.
-- The BFF routes forward to operations-console-bff on /commercial-admin/v1/agreements/..., which forwards to commercial-service
-- (/agreements/v1/{agreementId}/capabilities/{id}/suspension | /resumption, /agreements/v1/{agreementId}/prerequisite-checks);
-- operator identity comes from the Gateway actor headers.
-- Shape copies the existing commercial agreement POST routes 900000000000095405 (activate, bodiless POST) and
-- 900000000000095409 (capability update) one-for-one: OPERATIONS_JWT / HUMAN_PERMISSION, cookie DROP / NONE, idempotency NONE,
-- body limit 8192, receive 5000 ms, response 10000 ms; headers authorization (CORS_REQUEST_ALLOWED), origin (FORWARD_REQUEST),
-- content-type and idempotency-key (CORS_REQUEST_ALLOWED + FORWARD_REQUEST); guard rules SHARED_ROUTE 300/min + 50 concurrent,
-- CLIENT_IP 30/min + 3, AUTHENTICATED_PRINCIPAL 10/min + 2. No query parameters.
-- Rows: gateway_route 3, route_header 18, guard_rule 18 (39). ID block 141: 1414xx route, 1415xx route_header, 1416xx guard_rule.
-- Before applying, run mor_platform-block-141-precheck.sql on the live mor_platform (block range empty, route codes and
-- (method, path) unused, permissions / target / shape routes present).
-- Rollback: mor_platform-block-141-deactivate.sql (if active) then mor_platform-block-141-rollback.sql.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @gate_route_time = UTC_TIMESTAMP(6);
SET @gate_route_actor_id = 1;
SET @gate_route_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000141401, 'operations-commercial-capability-suspend', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/suspension', '/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/suspension', @gate_route_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @gate_route_actor_id, @gate_route_actor_id, @gate_route_time, @gate_route_time),
  (900000000000141402, 'operations-commercial-capability-resume', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/resumption', '/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/resumption', @gate_route_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @gate_route_actor_id, @gate_route_actor_id, @gate_route_time, @gate_route_time),
  (900000000000141403, 'operations-commercial-capability-prerequisite-check', 'OPERATIONS', 'POST', '/api/operations/commercial-admin/v1/agreements/{agreementId}/prerequisite-checks', '/commercial-admin/v1/agreements/{agreementId}/prerequisite-checks', @gate_route_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000095101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @gate_route_actor_id, @gate_route_actor_id, @gate_route_time, @gate_route_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000141501, 900000000000141401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000141502, 900000000000141401, 'origin', 'FORWARD_REQUEST'),
  (900000000000141503, 900000000000141401, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000141504, 900000000000141401, 'content-type', 'FORWARD_REQUEST'),
  (900000000000141505, 900000000000141401, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000141506, 900000000000141401, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000141511, 900000000000141402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000141512, 900000000000141402, 'origin', 'FORWARD_REQUEST'),
  (900000000000141513, 900000000000141402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000141514, 900000000000141402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000141515, 900000000000141402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000141516, 900000000000141402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000141521, 900000000000141403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000141522, 900000000000141403, 'origin', 'FORWARD_REQUEST'),
  (900000000000141523, 900000000000141403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000141524, 900000000000141403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000141525, 900000000000141403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000141526, 900000000000141403, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000141601, 900000000000141401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000141602, 900000000000141401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000141603, 900000000000141401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000141604, 900000000000141401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000141605, 900000000000141401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000141606, 900000000000141401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000141611, 900000000000141402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000141612, 900000000000141402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000141613, 900000000000141402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000141614, 900000000000141402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000141615, 900000000000141402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000141616, 900000000000141402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000141621, 900000000000141403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000141622, 900000000000141403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000141623, 900000000000141403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000141624, 900000000000141403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000141625, 900000000000141403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000141626, 900000000000141403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
