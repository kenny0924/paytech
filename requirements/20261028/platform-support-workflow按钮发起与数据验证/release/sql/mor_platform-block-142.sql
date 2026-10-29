-- =====================================================================================================================
-- REQ-05 platform-support workflow 按钮发起与数据验证 · 注册块 142（插入，路由 DISABLED）
-- 目标库：Platform Support（mor_platform）。与 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql
-- 末尾“Workflow button start and data validation”块正文相同；已初始化的环境只执行本文件。由开发者审核后手工执行。
-- 执行前（technique.md 8.8 / 9.3）：
--   1) 在目标库复核块 142 未占用：
--      SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000142001 AND 900000000000142999;
--      SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000142001 AND 900000000000142999;
--      SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000142001 AND 900000000000142999;
--      SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000142001 AND 900000000000142999;
--      SELECT COUNT(*) FROM t_permission WHERE permission_id IN (900000000000127103, 900000000000127105);   -- 必须为 2
--   2) 离线编译 Gateway 快照（DISABLED 路由同样参与编译）。
-- 激活：registration-142-activate.sql；回滚：registration-142-rollback.sql。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;

SET @workflow_data_query_time = UTC_TIMESTAMP(6);
SET @workflow_data_query_actor_id = 1;
SET @workflow_data_query_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000142401, 'operations-workflow-data-query-check', 'OPERATIONS', 'POST', '/api/operations/workflow/v1/action-runs/{actionRunId}/data-query-check', '/workflow/v1/action-runs/{actionRunId}/data-query-check', @workflow_data_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000127105, NULL, 'DROP', 'NONE', 'NONE', 1024, 5000, 10000, 'DISABLED', 1, @workflow_data_query_actor_id, @workflow_data_query_actor_id, @workflow_data_query_time, @workflow_data_query_time),
  (900000000000142402, 'operations-workflow-data-query-template-list', 'OPERATIONS', 'GET', '/api/operations/workflow-admin/v1/data-query-templates', '/workflow-admin/v1/data-query-templates', @workflow_data_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000127103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @workflow_data_query_actor_id, @workflow_data_query_actor_id, @workflow_data_query_time, @workflow_data_query_time),
  (900000000000142403, 'operations-workflow-template-validation', 'OPERATIONS', 'POST', '/api/operations/workflow-admin/v1/action-templates/validation', '/workflow-admin/v1/action-templates/validation', @workflow_data_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000127103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @workflow_data_query_actor_id, @workflow_data_query_actor_id, @workflow_data_query_time, @workflow_data_query_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000142501, 900000000000142401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000142502, 900000000000142401, 'origin', 'FORWARD_REQUEST'),
  (900000000000142503, 900000000000142401, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000142504, 900000000000142401, 'content-type', 'FORWARD_REQUEST'),
  (900000000000142505, 900000000000142401, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000142506, 900000000000142401, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000142507, 900000000000142402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000142508, 900000000000142402, 'origin', 'FORWARD_REQUEST'),
  (900000000000142509, 900000000000142403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000142510, 900000000000142403, 'origin', 'FORWARD_REQUEST'),
  (900000000000142511, 900000000000142403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000142512, 900000000000142403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000142513, 900000000000142403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000142514, 900000000000142403, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000142001, 900000000000142402, 'keyword'),
  (900000000000142002, 900000000000142402, 'page'),
  (900000000000142003, 900000000000142402, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000142601, 900000000000142401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000142602, 900000000000142401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000142603, 900000000000142401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000142604, 900000000000142401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000142605, 900000000000142401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000142606, 900000000000142401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000142607, 900000000000142402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000142608, 900000000000142402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000142609, 900000000000142402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000142610, 900000000000142402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000142611, 900000000000142402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000142612, 900000000000142402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000142613, 900000000000142403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000142614, 900000000000142403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000142615, 900000000000142403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000142616, 900000000000142403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000142617, 900000000000142403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000142618, 900000000000142403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
