-- 草稿（未执行）：Platform 注册块 __TAX_BLOCK__（财务 → 税务查询 + tax-service 文件写入路由），只在 mor_platform 执行，客户端须 utf8mb4。
-- 依据：technique.md 第 10.3、12、13 节；W4 阶段 4 交付。注册块编号阶段 6 查现网 mor_platform 后分配：把下方 __TAX_BLOCK__
-- 替换为块号（例如 142），未替换时本文件语法错误，不会误执行。
--
-- 前置：
--   1. operations-console-bff 新镜像（TaxOrderController、TaxClientConfiguration，环境变量 OPERATIONS_TAX_BASE_URL 指向 tax-service）、
--      operations-console-web 新镜像（/finance/tax 页面与 tax 命名空间语言包）、platform-support 新镜像（console-resource-capabilities.json
--      含 OPERATIONS_FINANCE_TAX）均已发布；tax-service 已部署。
--   2. 多语言管理中已创建 operations-console 的 tax 命名空间并导入、发布 en / zh-CN 键（不在本文件）。
--   3. 默认私有写入路由 (*, PRIVATE) 存在，且其扫描策略允许 application/pdf（tax 简化税票为 PDF）；否则在阶段 6 改用专用扫描策略再执行第 C 段。
--   4. 核对：SELECT COUNT(*) 各表 ID 在 900000000000000000 + __TAX_BLOCK__ * 1000 + (001—999) 内均为 0；权限码 TAX_VIEW、资源码
--      OPERATIONS_FINANCE_TAX、路由码 operations-finance-tax-*、(方法, 外部路径) 与写入路由 (tax-service, PRIVATE) 均未被占用。
--   5. Gateway 快照会编译 DISABLED 路由：执行前先离线编译本块，确认快照可编译。
--
-- 内容（休眠，与既有 Operations 功能一致）：权限 ACTIVE、管理员授权 REVOKED、菜单与路由 DISABLED；启用见文末第 D 段（另行审阅执行）。
--   A. 权限 TAX_VIEW（resource TAX，READ，LOW）：税务查询与税票下载；页面只读。授予平台管理员角色 900000000000000510（库中没有独立的
--      财务运营角色，财务各页均授予该角色）。
--   B. 菜单 税务查询 OPERATIONS_FINANCE_TAX，挂在 财务 900000000000097301 下，/finance/tax，排序 75（记账规则 70 之后、对账 80 之前）；
--      无按钮。Gateway 路由 2 条，转发 operations-console-bff 同路径（去掉 /api/operations）：
--        GET  /api/operations/tax/v1/orders/{orderNo}                                   → /tax/v1/orders/{orderNo}
--        POST /api/operations/tax/v1/orders/{orderNo}/documents/{documentId}/download  → /tax/v1/orders/{orderNo}/documents/{documentId}/download
--      下载路径带订单号：tax 只提供按订单读取单据（W4 报告偏离项 1）。下载无请求体（body 上限 0），仍转发 content-type（BFF 写入口要求 JSON
--      内容类型）与 idempotency-key（同文件记录下载，块 134）。两条路由都不接受 Query 参数。
--      限流沿用块 134：每条 SHARED_ROUTE 300/分钟 + 并发 50、CLIENT_IP 30/分钟 + 并发 3；读取 AUTHENTICATED_PRINCIPAL 30/分钟、下载 10/分钟，
--      并发 2。
--   C. 文件写入路由 (tax-service, PRIVATE)：桶、扫描策略与上传期限取自现有默认私有路由 (*, PRIVATE)；tax 以 reasonCode TAX_RECEIPT 上传
--      简化税票。reasonCode 是来源系统自定义的开放代码，不需要登记。
-- 行数：permission 1、role_permission 1、console_resource 1、gateway_route 2、route_header 8、guard_rule 12、file_storage_route 1，共 26 行。
-- ID 分段：@tax_id + 101 permission、+201 role_permission、+301 console_resource、+401—402 route、+501—508 route_header、
--   +701—712 guard_rule、+801 file_storage_route。
-- 回滚：见文末第 E 段，按 ID 先子后父删除。
-- ---------------------------------------------------------------------------
SET NAMES utf8mb4;

SET @tax_id = 900000000000000000 + __TAX_BLOCK__ * 1000;
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

-- 核对（只读）：
-- SELECT COUNT(*) FROM t_permission WHERE permission_id = @tax_id + 101;                                   -- 1
-- SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN @tax_id + 401 AND @tax_id + 402;            -- 2
-- SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN @tax_id + 501 AND @tax_id + 508; -- 8
-- SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN @tax_id + 701 AND @tax_id + 712;   -- 12
-- SELECT source_system, access_level, bucket_id, scan_policy_code FROM t_file_storage_route WHERE route_id = @tax_id + 801;

-- ---------------------------------------------------------------------------
-- D. 启用（另行审阅执行；先确认 Gateway 快照已包含本块且可编译）
-- START TRANSACTION;
-- UPDATE t_role_permission SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_permission_id = @tax_id + 201 AND status = 'REVOKED';
-- UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE resource_id = @tax_id + 301 AND status = 'DISABLED';
-- UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE route_id BETWEEN @tax_id + 401 AND @tax_id + 402 AND status = 'DISABLED';
-- UPDATE t_role SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_id = 900000000000000510;
-- COMMIT;
-- 预期影响 1、1、2、1 行（与块 136 test-activate.sql 同一写法）。

-- ---------------------------------------------------------------------------
-- E. 回滚（先子后父）
-- START TRANSACTION;
-- DELETE FROM t_file_storage_route WHERE route_id = @tax_id + 801;
-- DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN @tax_id + 701 AND @tax_id + 712;
-- DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN @tax_id + 501 AND @tax_id + 508;
-- DELETE FROM t_gateway_route WHERE route_id BETWEEN @tax_id + 401 AND @tax_id + 402;
-- DELETE FROM t_console_resource WHERE resource_id = @tax_id + 301;
-- DELETE FROM t_role_permission WHERE role_permission_id = @tax_id + 201;
-- DELETE FROM t_permission WHERE permission_id = @tax_id + 101;
-- COMMIT;
-- 回滚写入路由前确认 tax-service 尚未上传任何文件（t_file_upload.source_system = 'tax-service' 为 0），否则只停用不删除。
