-- tax 阶段 5 本机联调专用（只用于 mor_platform_v2e2e）：运营 财务 → 税务查询 菜单、TAX_VIEW 权限（授予本机 PLATFORM_ADMIN 900000000000000510）
-- 与两条 Gateway 路由。内容与 db/platform-registration.sql 第 A、B 段相同，直接为 ACTIVE（相当于同时执行第 D 段）。
-- 不是阶段 6 注册块：本机专用号段 900000000001449xxx（本机 t_permission / t_role_permission / t_console_resource / t_gateway_route /
-- t_gateway_route_header / t_gateway_guard_rule 在该段均为 0 行，2026-10-29 核对）；正式块号阶段 6 查现网分配。
-- 第 C 段（tax-service 文件写入路由）由 W5 处理，本文件不含。
-- 回滚：同目录 30-ops-platform-tax-local-rollback.sql。
SET NAMES utf8mb4;

SET @tax_id = 900000000001449000;
SET @tax_console_time = UTC_TIMESTAMP(6);
SET @tax_console_actor_id = 1;
SET @tax_console_target_id = 900000000000000001;
SET @tax_console_role_id = 900000000000000510;
SET @tax_finance_menu_id = 900000000000097301;

START TRANSACTION;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (@tax_id + 101, 'TAX_VIEW', 'TAX', 'READ', 'LOW', 'ACTIVE', 1, @tax_console_time, @tax_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (@tax_id + 201, @tax_console_role_id, @tax_id + 101, 'ACTIVE', 1, @tax_console_time, @tax_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (@tax_id + 301, 'OPERATIONS_FINANCE_TAX', 'OPERATIONS', 'MENU', @tax_finance_menu_id, '税务查询', '/finance/tax', 'OPERATIONS_FINANCE_TAX', @tax_id + 101, 75, 'ACTIVE', 1, @tax_console_time, @tax_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (@tax_id + 401, 'operations-finance-tax-order-detail', 'OPERATIONS', 'GET', '/api/operations/tax/v1/orders/{orderNo}', '/tax/v1/orders/{orderNo}', @tax_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @tax_id + 101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, @tax_console_actor_id, @tax_console_actor_id, @tax_console_time, @tax_console_time),
  (@tax_id + 402, 'operations-finance-tax-document-download', 'OPERATIONS', 'POST', '/api/operations/tax/v1/orders/{orderNo}/documents/{documentId}/download', '/tax/v1/orders/{orderNo}/documents/{documentId}/download', @tax_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @tax_id + 101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'ACTIVE', 1, @tax_console_actor_id, @tax_console_actor_id, @tax_console_time, @tax_console_time);

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

-- 角色版本 +1，使已登录会话的权限快照失效（同 db/platform-registration.sql 第 D 段）。
UPDATE t_role SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_id = @tax_console_role_id;

COMMIT;

SELECT 'permission' t, COUNT(*) n FROM t_permission WHERE permission_id = @tax_id + 101
UNION ALL SELECT 'role_permission', COUNT(*) FROM t_role_permission WHERE role_permission_id = @tax_id + 201
UNION ALL SELECT 'console_resource', COUNT(*) FROM t_console_resource WHERE resource_id = @tax_id + 301
UNION ALL SELECT 'route', COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN @tax_id + 401 AND @tax_id + 402
UNION ALL SELECT 'route_header', COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN @tax_id + 501 AND @tax_id + 508
UNION ALL SELECT 'guard_rule', COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN @tax_id + 701 AND @tax_id + 712;
-- 预期 1、1、1、2、8、12。
