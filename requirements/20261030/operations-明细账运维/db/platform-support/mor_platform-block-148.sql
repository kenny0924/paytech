-- =====================================================================================================================
-- operations 明细账运维 · mor_platform 注册块 148 · 休眠写入。与 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql
-- 末尾的块 148 内容相同（2026-10-30 阶段 4 核对：本地初始化数据最大块 145，148 未出现在任何需求、paytech 或 release SQL 中；执行前
-- -precheck.sql 再确认现网区间为空）。
-- 内容：权限 FINANCE_SUBLEDGER_MANAGE ACTIVE、管理员授权 REVOKED、菜单 1 + 按钮 2 与 4 条路由 DISABLED。查看沿用 FINANCE_ACCOUNTING_READ。
-- 行数 1 / 1 / 3 / 4 / 16 / 2 / 24 = 51。重复执行被主键拒绝（ERROR 1062，事务内首条即失败、不留半成品）。
-- 请求头与 Guard 按现有模板每条路由写全（GET 2 头、写 6 头；每条路由 6 条 Guard），比技术方案第 8 节的估算（12 / 12）多，按模板为准。
-- Gateway 快照编译 DISABLED 行：执行前用候选 Platform JAR 离线编译核对快照大小（上限 4 MiB）。
-- 执行：sqlr mor_platform < 本文件；核对 mor_platform-block-148-verify.sql。
-- =====================================================================================================================
SET NAMES utf8mb4;

START TRANSACTION;

SET @b148_time = UTC_TIMESTAMP(6);
SET @b148_actor_id = 1;
SET @b148_role_id = 900000000000000510;
SET @b148_target_id = 900000000000000001;
SET @b148_finance_menu_id = 900000000000097301;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000148101, 'FINANCE_SUBLEDGER_MANAGE', 'FINANCE_SUBLEDGER', 'MANAGE', 'HIGH', 'ACTIVE', 1, @b148_time, @b148_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000148201, @b148_role_id, 900000000000148101, 'REVOKED', 1, @b148_time, @b148_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000148301, 'OPERATIONS_FINANCE_POSTING_OPS', 'OPERATIONS', 'MENU', @b148_finance_menu_id, '明细账运维', '/finance/posting-ops', 'OPERATIONS_FINANCE_POSTING_OPS', 900000000000097101, 45, 'DISABLED', 1, @b148_time, @b148_time),
  (900000000000148302, 'OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP', 'OPERATIONS', 'BUTTON', 900000000000148301, '追平', NULL, NULL, 900000000000148101, 10, 'DISABLED', 1, @b148_time, @b148_time),
  (900000000000148303, 'OPERATIONS_FINANCE_POSTING_OPS_REBUILD', 'OPERATIONS', 'BUTTON', 900000000000148301, '重建余额', NULL, NULL, 900000000000148101, 20, 'DISABLED', 1, @b148_time, @b148_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000148401, 'operations-finance-posting-progress', 'OPERATIONS', 'GET', '/api/operations/finance/posting-progress/v1/ledger-books/{ledgerBookId}', '/finance/posting-progress/v1/ledger-books/{ledgerBookId}', @b148_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b148_actor_id, @b148_actor_id, @b148_time, @b148_time),
  (900000000000148402, 'operations-finance-posting-stream-postings', 'OPERATIONS', 'GET', '/api/operations/finance/posting-streams/v1/{streamId}/postings', '/finance/posting-streams/v1/{streamId}/postings', @b148_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b148_actor_id, @b148_actor_id, @b148_time, @b148_time),
  (900000000000148403, 'operations-finance-posting-stream-catch-up', 'OPERATIONS', 'POST', '/api/operations/finance/posting-streams/v1/{streamId}/catch-up', '/finance/posting-streams/v1/{streamId}/catch-up', @b148_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000148101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b148_actor_id, @b148_actor_id, @b148_time, @b148_time),
  (900000000000148404, 'operations-finance-posting-rebuild', 'OPERATIONS', 'POST', '/api/operations/finance/posting-rebuilds/v1', '/finance/posting-rebuilds/v1', @b148_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000148101, NULL, 'DROP', 'NONE', 'NONE', 1024, 5000, 10000, 'DISABLED', 1, @b148_actor_id, @b148_actor_id, @b148_time, @b148_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000148501, 900000000000148401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000148502, 900000000000148401, 'origin', 'FORWARD_REQUEST'),
  (900000000000148503, 900000000000148402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000148504, 900000000000148402, 'origin', 'FORWARD_REQUEST'),
  (900000000000148505, 900000000000148403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000148506, 900000000000148403, 'origin', 'FORWARD_REQUEST'),
  (900000000000148507, 900000000000148403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000148508, 900000000000148403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000148509, 900000000000148403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000148510, 900000000000148403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000148511, 900000000000148404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000148512, 900000000000148404, 'origin', 'FORWARD_REQUEST'),
  (900000000000148513, 900000000000148404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000148514, 900000000000148404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000148515, 900000000000148404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000148516, 900000000000148404, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000148601, 900000000000148402, 'fromSequence'),
  (900000000000148602, 900000000000148402, 'limit');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000148701, 900000000000148401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000148702, 900000000000148401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000148703, 900000000000148401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000148704, 900000000000148401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000148705, 900000000000148401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000148706, 900000000000148401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000148707, 900000000000148402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000148708, 900000000000148402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000148709, 900000000000148402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000148710, 900000000000148402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000148711, 900000000000148402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000148712, 900000000000148402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000148713, 900000000000148403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000148714, 900000000000148403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000148715, 900000000000148403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000148716, 900000000000148403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000148717, 900000000000148403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000148718, 900000000000148403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 1),
  (900000000000148719, 900000000000148404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000148720, 900000000000148404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000148721, 900000000000148404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000148722, 900000000000148404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000148723, 900000000000148404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000148724, 900000000000148404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 1);

COMMIT;

-- 预期：上面 7 条 INSERT 依次影响 1、1、3、4、16、2、24 行；任一不符时 ROLLBACK。
