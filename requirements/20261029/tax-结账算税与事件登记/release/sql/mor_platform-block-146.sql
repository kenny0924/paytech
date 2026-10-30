-- =====================================================================================================================
-- tax 结账算税与事件登记 · mor_platform 注册块 146 · 休眠写入（runbook ⑤）。由 db/platform-registration.sql 第 A—C 段生成，
-- 只把 __TAX_BLOCK__ 替换为 146（2026-10-29 跨会话约定：mor-test 144 = 联合查询（design-discuss-19）、145 = REQ-04（design-discuss-b8）、146 = 本需求；阶段 6 只读核对现网最大块 143、146 区间为空；执行前 -precheck.sql 再确认，见 stage6-report D-6）。
-- 内容：权限 TAX_VIEW ACTIVE、管理员授权 REVOKED、菜单与 2 条路由 DISABLED、tax-service 私有文件写入路由（立即生效，不经 Gateway 快照）。
-- 行数 1 / 1 / 1 / 2 / 8 / 12 / 1 = 26。重复执行被主键拒绝（ERROR 1062，事务内首条即失败、不留半成品）。
-- Gateway 快照编译 DISABLED 行：阶段 6 已用候选 Platform JAR 的生产编译器离线编译通过（evidence/rehearsal-*/）。
-- 执行：sqlr mor_platform < 本文件；核对 mor_platform-block-146-verify.sql。
-- =====================================================================================================================
SET NAMES utf8mb4;

SET @tax_id = 900000000000000000 + 146 * 1000;
SET @tax_console_time = UTC_TIMESTAMP(6);
SET @tax_console_actor_id = 1;
SET @tax_console_target_id = 900000000000000001;
SET @tax_console_role_id = 900000000000000510;
SET @tax_finance_menu_id = 900000000000097301;

START TRANSACTION;

-- A. 权限与管理员授权（休眠）
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (@tax_id + 101, 'TAX_VIEW', 'TAX', 'READ', 'LOW', 'ACTIVE', 1, @tax_console_time, @tax_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (@tax_id + 201, @tax_console_role_id, @tax_id + 101, 'REVOKED', 1, @tax_console_time, @tax_console_time);

-- B. 菜单与 Gateway 路由（休眠）
INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (@tax_id + 301, 'OPERATIONS_FINANCE_TAX', 'OPERATIONS', 'MENU', @tax_finance_menu_id, '税务查询', '/finance/tax', 'OPERATIONS_FINANCE_TAX', @tax_id + 101, 75, 'DISABLED', 1, @tax_console_time, @tax_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (@tax_id + 401, 'operations-finance-tax-order-detail', 'OPERATIONS', 'GET', '/api/operations/tax/v1/orders/{orderNo}', '/tax/v1/orders/{orderNo}', @tax_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @tax_id + 101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @tax_console_actor_id, @tax_console_actor_id, @tax_console_time, @tax_console_time),
  (@tax_id + 402, 'operations-finance-tax-document-download', 'OPERATIONS', 'POST', '/api/operations/tax/v1/orders/{orderNo}/documents/{documentId}/download', '/tax/v1/orders/{orderNo}/documents/{documentId}/download', @tax_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @tax_id + 101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @tax_console_actor_id, @tax_console_actor_id, @tax_console_time, @tax_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (@tax_id + 501, @tax_id + 401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (@tax_id + 502, @tax_id + 401, 'origin', 'FORWARD_REQUEST'),
  (@tax_id + 503, @tax_id + 402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (@tax_id + 504, @tax_id + 402, 'origin', 'FORWARD_REQUEST'),
  (@tax_id + 505, @tax_id + 402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (@tax_id + 506, @tax_id + 402, 'content-type', 'FORWARD_REQUEST'),
  (@tax_id + 507, @tax_id + 402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (@tax_id + 508, @tax_id + 402, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (@tax_id + 701, @tax_id + 401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (@tax_id + 702, @tax_id + 401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (@tax_id + 703, @tax_id + 401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (@tax_id + 704, @tax_id + 401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (@tax_id + 705, @tax_id + 401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (@tax_id + 706, @tax_id + 401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (@tax_id + 707, @tax_id + 402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (@tax_id + 708, @tax_id + 402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (@tax_id + 709, @tax_id + 402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (@tax_id + 710, @tax_id + 402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (@tax_id + 711, @tax_id + 402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (@tax_id + 712, @tax_id + 402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

-- C. tax-service 私有文件写入路由：复制默认私有路由的桶、扫描策略与上传期限；默认路由不存在时插入 0 行，须停下核对。
INSERT INTO t_file_storage_route (route_id, source_system, access_level, bucket_id, scan_policy_code, max_upload_seconds, version, create_time, update_time)
SELECT @tax_id + 801, 'tax-service', 'PRIVATE', bucket_id, scan_policy_code, max_upload_seconds, 1, @tax_console_time, @tax_console_time
FROM t_file_storage_route WHERE source_system = '*' AND access_level = 'PRIVATE';

-- 预期：上面 7 条语句依次影响 1、1、1、2、8、12、1 行；任一不符时 ROLLBACK。
COMMIT;
