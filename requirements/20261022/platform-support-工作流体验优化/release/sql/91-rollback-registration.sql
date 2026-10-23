-- 工作流体验优化 · 回滚块 127 注册增量（内容取自 ../../sql/20-registration.sql 文件头「回滚」与文末「回滚插入」段）。客户端须 utf8mb4。
-- 删除菜单 127311「我发起的」，重新插入重新验证路由 127429 及 6 行 route_header、6 行 guard_rule（列值与 2026-10-23 05:55Z
-- 读到的现网行逐列相同；现网 version 为 2，重插取 3 使版本单调递增），
-- 递增 PLATFORM_ADMIN（510）version 使会话重新计算菜单。期望影响行数：resource 1、route 1、header 6、guard 6、role 1。
-- 执行时机：先回退 ops-web（旧前端没有「我发起的」页面也能工作，但新前端在菜单消失后「我发起的」不可用），
--   通常与镜像回滚同一窗口；路由恢复后旧前端的「重新验证」可用（需要旧 Platform / ops-bff）。
-- 本文件不含 COMMIT：apply_sql.sh rollback-reg-dry 断开即回滚；rollback-reg 追加 COMMIT。guard 不符时报错停止。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';

START TRANSACTION;
SET @wfx_time = UTC_TIMESTAMP(6);

DELETE FROM t_console_resource WHERE DATABASE() = @target_db AND resource_id = 900000000000127311
   AND resource_code = 'OPERATIONS_WORKFLOW_STARTED';
SET @wfx_resource = ROW_COUNT();

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template,
  target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode,
  response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status,
  version, created_by, updated_by, create_time, update_time)
SELECT * FROM (VALUES ROW(900000000000127429, 'operations-workflow-action-reverify', 'OPERATIONS', 'POST',
  '/api/operations/workflow/v1/action-runs/{actionRunId}/verification', '/workflow/v1/action-runs/{actionRunId}/verification',
  900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000127105, NULL, 'DROP', 'NONE', 'NONE', 1024, 5000,
  10000, 'ACTIVE', 3, 1, 1, @wfx_time, @wfx_time)) AS v
 WHERE DATABASE() = @target_db;
SET @wfx_route = ROW_COUNT();

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT * FROM (VALUES
  ROW(900000000000127613, 900000000000127429, 'authorization', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000127614, 900000000000127429, 'origin', 'FORWARD_REQUEST'),
  ROW(900000000000127615, 900000000000127429, 'content-type', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000127616, 900000000000127429, 'content-type', 'FORWARD_REQUEST'),
  ROW(900000000000127617, 900000000000127429, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000127618, 900000000000127429, 'idempotency-key', 'FORWARD_REQUEST')) AS v
 WHERE DATABASE() = @target_db;
SET @wfx_header = ROW_COUNT();

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens,
  refill_period_millis, concurrency_limit)
SELECT * FROM (VALUES
  ROW(900000000000127869, 900000000000127429, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  ROW(900000000000127870, 900000000000127429, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  ROW(900000000000127871, 900000000000127429, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  ROW(900000000000127872, 900000000000127429, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  ROW(900000000000127873, 900000000000127429, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  ROW(900000000000127874, 900000000000127429, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2)) AS v
 WHERE DATABASE() = @target_db;
SET @wfx_guard = ROW_COUNT();

UPDATE t_role SET version = version + 1, update_time = @wfx_time
 WHERE DATABASE() = @target_db AND role_id = 900000000000000510;
SET @wfx_role = ROW_COUNT();

SELECT DATABASE() AS target, @wfx_resource AS resource_1, @wfx_route AS route_1, @wfx_header AS route_headers_6,
       @wfx_guard AS guard_rules_6, @wfx_role AS role_1;
SELECT IF(@wfx_resource = 1 AND @wfx_route = 1 AND @wfx_header = 6 AND @wfx_guard = 6 AND @wfx_role = 1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
