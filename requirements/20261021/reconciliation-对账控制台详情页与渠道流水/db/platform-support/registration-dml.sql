-- 目标库：mor_platform（Platform Support）。阅读副本，未执行；阶段 4 已原样追加到唯一执行来源
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾（块 126，位于商务块 124 之后），不要单独执行本文件。
-- 原计划用块 125；货币主数据会话已在 mor-test 占用块 125，2026-10-21 改用块 126。
-- 共 38 行（资源 1、路由 3、Header 6、Query 10、限流 18）；不新增权限与授权，复用块 123 的 FINANCE_RECONCILIATION_READ
-- （900000000000123101）与对账分组菜单（900000000000123301）。
-- 不可重复执行：固定主键重复插入会失败；回滚按块头注释的 ID 段删除（子表先删）。
-- 执行前核对块 126（900000000000126000—900000000000126999）与 3 个路由码、资源码未被占用：2026-10-21 只读核对本机
-- mor_platform_v2e2e、mor_platform 与 mor-test mor_platform 均为 0 行；块 123 父菜单与权限在 mor_platform_v2e2e、mor-test 存在
-- （本机 mor_platform 未注册块 123，须先执行块 123）。

-- ---------------------------------------------------------------------------
-- Operations finance reconciliation channel lines page and detail reads (2026-10-21,
-- requirements/20261021/reconciliation-对账控制台详情页与渠道流水, backend line).
-- Self-contained block: on an environment already holding the reconciliation console block 123 above, apply ONLY this block.
-- Dormant like block 123: menu and routes DISABLED; activation is a separate reviewed change (activation-local.sql /
-- test-activate.sql of that requirement). No new permission and no role grant: the new menu 渠道流水 (sort 45, after 平台流水
-- under the 对账 group 900000000000123301) and the three read-only routes reuse FINANCE_RECONCILIATION_READ
-- (900000000000123101). Routes: cross-statement channel statement line search, bank statement header and bank statement line
-- detail; headers, query whitelist and guard rules copy the block 123 GET routes. Detail pages of the other reconciliation
-- objects are frontend sub-routes of the existing menus and need no registration.
-- Rows: console_resource 1, gateway_route 3, route_header 6, query_param 10, guard_rule 18 (38). ID block 126: 1263xx
-- console_resource, 1264xx route, 1265xx route_header, 1260xx query_param, 1267xx guard_rule.
-- Rollback: delete the rows by the 900000000000126001-900000000000126999 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_recon_lines_time = UTC_TIMESTAMP(6);
SET @finance_recon_lines_actor_id = 1;
SET @finance_recon_lines_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000126301, 'OPERATIONS_FINANCE_RECON_CHANNEL_LINES', 'OPERATIONS', 'MENU', 900000000000123301, '渠道流水', '/finance/reconciliation/channel-lines', 'OPERATIONS_FINANCE_RECON_CHANNEL_LINES', 900000000000123101, 45, 'DISABLED', 1, @finance_recon_lines_time, @finance_recon_lines_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000126401, 'operations-finance-recon-channel-line-search', 'OPERATIONS', 'GET', '/api/operations/finance/reconciliation/v1/channel-statement-lines', '/finance/reconciliation/v1/channel-statement-lines', @finance_recon_lines_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000123101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_recon_lines_actor_id, @finance_recon_lines_actor_id, @finance_recon_lines_time, @finance_recon_lines_time),
  (900000000000126402, 'operations-finance-recon-bank-statement-detail', 'OPERATIONS', 'GET', '/api/operations/finance/reconciliation/v1/bank-statements/{bankStatementId}', '/finance/reconciliation/v1/bank-statements/{bankStatementId}', @finance_recon_lines_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000123101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_recon_lines_actor_id, @finance_recon_lines_actor_id, @finance_recon_lines_time, @finance_recon_lines_time),
  (900000000000126403, 'operations-finance-recon-bank-statement-line-detail', 'OPERATIONS', 'GET', '/api/operations/finance/reconciliation/v1/bank-statement-lines/{bankStatementLineId}', '/finance/reconciliation/v1/bank-statement-lines/{bankStatementLineId}', @finance_recon_lines_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000123101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_recon_lines_actor_id, @finance_recon_lines_actor_id, @finance_recon_lines_time, @finance_recon_lines_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000126501, 900000000000126401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000126502, 900000000000126401, 'origin', 'FORWARD_REQUEST'),
  (900000000000126503, 900000000000126402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000126504, 900000000000126402, 'origin', 'FORWARD_REQUEST'),
  (900000000000126505, 900000000000126403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000126506, 900000000000126403, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000126001, 900000000000126401, 'clearingAccountId'),
  (900000000000126002, 900000000000126401, 'statementStatus'),
  (900000000000126003, 900000000000126401, 'matchStatus'),
  (900000000000126004, 900000000000126401, 'lineType'),
  (900000000000126005, 900000000000126401, 'referenceType'),
  (900000000000126006, 900000000000126401, 'externalTransactionId'),
  (900000000000126007, 900000000000126401, 'transactionDateFrom'),
  (900000000000126008, 900000000000126401, 'transactionDateTo'),
  (900000000000126009, 900000000000126401, 'page'),
  (900000000000126010, 900000000000126401, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000126701, 900000000000126401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000126702, 900000000000126401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000126703, 900000000000126401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000126704, 900000000000126401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000126705, 900000000000126401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000126706, 900000000000126401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000126707, 900000000000126402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000126708, 900000000000126402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000126709, 900000000000126402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000126710, 900000000000126402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000126711, 900000000000126402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000126712, 900000000000126402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000126713, 900000000000126403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000126714, 900000000000126403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000126715, 900000000000126403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000126716, 900000000000126403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000126717, 900000000000126403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000126718, 900000000000126403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
