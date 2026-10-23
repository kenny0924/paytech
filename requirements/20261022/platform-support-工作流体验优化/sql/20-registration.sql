-- 工作流体验优化 S1（技术方案 4.1、4.3）：块 127 注册增量。
--   · 新增菜单 900000000000127311「我发起的」（/workflow/started，组件 OPERATIONS_WORKFLOW_STARTED，权限 WORKFLOW_TASK_HANDLE
--     900000000000127105，与「我的待办」相同，不新增授权）；环境中块 127 已激活，本行直接 ACTIVE。
--   · 删除重新验证接口 #20 的路由 900000000000127429（operations-workflow-action-reverify）及其 6 行 route_header
--     （900000000000127613—127618）与 6 行 guard_rule（900000000000127869—127874），共 13 行。
--   · 递增 PLATFORM_ADMIN 角色（900000000000000510）version，使会话重新计算菜单。
-- 内容同块 127 正式文件 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql（405 行版本）。
-- 待执行，需另行授权；审阅后由操作员手工执行，应用与测试不执行本文件。客户端须 utf8mb4。
--
-- 目标库参数：下方 @target_db。mor-test 为 mor_platform；本机 e2e 改为 mor_platform_v2e2e。库名不符时影响 0 行，guard 报错。
-- 执行时机：与去掉 #20 的 Platform / ops-bff 及带「我发起的」页面的 ops-web 同一发布窗口；先发布镜像再执行本文件
--   （旧 ops-web 不认识 OPERATIONS_WORKFLOW_STARTED；路由删除后旧前端的「重新验证」得到 404，新版已不调用）。
-- 前置（只读）：
--   SELECT COUNT(*) FROM t_console_resource WHERE resource_id = 900000000000127311;                    -- 0
--   SELECT status FROM t_console_resource WHERE resource_id = 900000000000127301;                      -- 父菜单存在（ACTIVE）
--   SELECT COUNT(*) FROM t_gateway_route WHERE route_id = 900000000000127429;                          -- 1
--   SELECT COUNT(*) FROM t_gateway_route_header WHERE route_id = 900000000000127429;                   -- 6
--   SELECT COUNT(*) FROM t_gateway_guard_rule WHERE route_id = 900000000000127429;                     -- 6
--   SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_id = 900000000000127429;              -- 0
-- 期望影响行数：resource 1、guard_rule 6、route_header 6、route 1、role 1。本文件不含 COMMIT：
--   · 试运行：执行后断开即回滚，只看各数；
--   · 提交：在本文件后追加 COMMIT；guard 不符时报错，客户端停止，未提交的事务随断开回滚。
-- 执行后核对：块 127 合计 405 行（permission 7、role_permission 7、console_resource 11、gateway_route 31、route_header 130、
--   query_param 30、guard_rule 186、notification template / content / route 各 1）；Gateway 快照重新发布后不含 127429。
-- 回滚（先回退镜像）：
--   DELETE FROM t_console_resource WHERE DATABASE() = @target_db AND resource_id = 900000000000127311;
--   按下方「回滚插入」重新插入 13 行（状态 ACTIVE，与激活后的环境一致），再递增角色 900000000000000510 的 version。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';

START TRANSACTION;
SET @wfx_time = UTC_TIMESTAMP(6);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name,
  frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT * FROM (VALUES ROW(900000000000127311, 'OPERATIONS_WORKFLOW_STARTED', 'OPERATIONS', 'MENU', 900000000000127301,
  '我发起的', '/workflow/started', 'OPERATIONS_WORKFLOW_STARTED', 900000000000127105, 15, 'ACTIVE', 1, @wfx_time,
  @wfx_time)) AS v
 WHERE DATABASE() = @target_db;
SET @wfx_resource = ROW_COUNT();

DELETE FROM t_gateway_guard_rule
 WHERE DATABASE() = @target_db AND route_id = 900000000000127429
   AND guard_rule_id BETWEEN 900000000000127869 AND 900000000000127874;
SET @wfx_guard = ROW_COUNT();
DELETE FROM t_gateway_route_header
 WHERE DATABASE() = @target_db AND route_id = 900000000000127429
   AND route_header_id BETWEEN 900000000000127613 AND 900000000000127618;
SET @wfx_header = ROW_COUNT();
DELETE FROM t_gateway_route
 WHERE DATABASE() = @target_db AND route_id = 900000000000127429 AND route_code = 'operations-workflow-action-reverify';
SET @wfx_route = ROW_COUNT();

UPDATE t_role SET version = version + 1, update_time = @wfx_time
 WHERE DATABASE() = @target_db AND role_id = 900000000000000510;
SET @wfx_role = ROW_COUNT();

SELECT DATABASE() AS target, @wfx_resource AS resource_1, @wfx_guard AS guard_rules_6, @wfx_header AS route_headers_6,
       @wfx_route AS route_1, @wfx_role AS role_1;
-- guard：任一数不符时本语句报错（子查询多行），mysql 客户端停止，未提交的事务随断开回滚。
SELECT IF(@wfx_resource = 1 AND @wfx_guard = 6 AND @wfx_header = 6 AND @wfx_route = 1 AND @wfx_role = 1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;

-- 回滚插入（仅回滚时手工执行；与删除前块 127 内容相同，状态取激活后的 ACTIVE）：
-- INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template,
--   target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode,
--   response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status,
--   version, created_by, updated_by, create_time, update_time) VALUES
--   (900000000000127429, 'operations-workflow-action-reverify', 'OPERATIONS', 'POST',
--    '/api/operations/workflow/v1/action-runs/{actionRunId}/verification', '/workflow/v1/action-runs/{actionRunId}/verification',
--    900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000127105, NULL, 'DROP', 'NONE', 'NONE', 1024, 5000,
--    10000, 'ACTIVE', 1, 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
-- INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
--   (900000000000127613, 900000000000127429, 'authorization', 'CORS_REQUEST_ALLOWED'),
--   (900000000000127614, 900000000000127429, 'origin', 'FORWARD_REQUEST'),
--   (900000000000127615, 900000000000127429, 'content-type', 'CORS_REQUEST_ALLOWED'),
--   (900000000000127616, 900000000000127429, 'content-type', 'FORWARD_REQUEST'),
--   (900000000000127617, 900000000000127429, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
--   (900000000000127618, 900000000000127429, 'idempotency-key', 'FORWARD_REQUEST');
-- INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens,
--   refill_period_millis, concurrency_limit) VALUES
--   (900000000000127869, 900000000000127429, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
--   (900000000000127870, 900000000000127429, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
--   (900000000000127871, 900000000000127429, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
--   (900000000000127872, 900000000000127429, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
--   (900000000000127873, 900000000000127429, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
--   (900000000000127874, 900000000000127429, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);
