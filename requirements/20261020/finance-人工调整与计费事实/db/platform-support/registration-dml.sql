-- 目标库：mor_platform（Platform Support）。阅读副本，未执行；阶段 4 已原样追加到唯一执行来源
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾（块 120、121、122，按此顺序），不要单独执行本文件。
-- 块 121、122 依赖块 120 的 FINANCE_ACCOUNTING_APPROVE（900000000000120101）。三块共 328 行（134 / 99 / 95）。
-- 不可重复执行：固定主键重复插入会失败；回滚按各块头注释的 ID 段删除（子表先删）。

-- ---------------------------------------------------------------------------
-- Operations finance manual adjustment and billing fact console (2026-10-20, requirements/20261020/finance-人工调整与计费事实).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102, OPERATIONS_FINANCE 900000000000097301).
-- Adds FINANCE_ACCOUNTING_APPROVE (checker; maker keeps FINANCE_ACCOUNTING_MANAGE) and its PLATFORM_ADMIN grant (REVOKED).
-- Dormant: grant REVOKED, menu/buttons/routes DISABLED; activate them in a separate reviewed change. BFF routes forward to
-- ledger (ManualAdjustmentApi) and billing (BillingFactQueryApi, BillingFactCommandApi); operator identity comes from the
-- Gateway actor headers, never from the request body.
-- Rows: permission 1, role_permission 1, console_resource 3, gateway_route 10, route_header 44, query_param 15, guard_rule 60 (134).
-- ID block 120: 1201xx permission, 1202xx role_permission, 1203xx console_resource,
-- 1204xx route, 1205xx route_header, 1206xx query_param, 1207xx guard_rule.
-- Rollback: delete the rows by the 900000000000120101-900000000000120799 IDs below (children first: guard_rule,
-- query_param, route_header, gateway_route, console_resource, role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_adjustment_console_time = UTC_TIMESTAMP(6);
SET @finance_adjustment_console_actor_id = 1;
SET @finance_adjustment_console_target_id = 900000000000000001;
SET @finance_adjustment_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000120101, 'FINANCE_ACCOUNTING_APPROVE', 'FINANCE_ACCOUNTING', 'APPROVE', 'HIGH', 'ACTIVE', 1, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000120201, @finance_adjustment_console_role_id, 900000000000120101, 'REVOKED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000120301, 'OPERATIONS_FINANCE_ADJUSTMENTS', 'OPERATIONS', 'MENU', 900000000000097301, '调整', '/finance/adjustments', 'OPERATIONS_FINANCE_ADJUSTMENTS', 900000000000097101, 50, 'DISABLED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120302, 'OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000120301, '制单', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120303, 'OPERATIONS_FINANCE_ADJUSTMENTS_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000120301, '复核', NULL, NULL, 900000000000120101, 20, 'DISABLED', 1, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000120401, 'operations-finance-manual-adjustment-list', 'OPERATIONS', 'GET', '/api/operations/finance/manual-adjustments/v1', '/finance/manual-adjustments/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120402, 'operations-finance-manual-adjustment-submit', 'OPERATIONS', 'POST', '/api/operations/finance/manual-adjustments/v1', '/finance/manual-adjustments/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120403, 'operations-finance-manual-adjustment-detail', 'OPERATIONS', 'GET', '/api/operations/finance/manual-adjustments/v1/{manualAdjustmentId}', '/finance/manual-adjustments/v1/{manualAdjustmentId}', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120404, 'operations-finance-manual-adjustment-approve', 'OPERATIONS', 'POST', '/api/operations/finance/manual-adjustments/v1/{manualAdjustmentId}/approve', '/finance/manual-adjustments/v1/{manualAdjustmentId}/approve', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120405, 'operations-finance-manual-adjustment-reject', 'OPERATIONS', 'POST', '/api/operations/finance/manual-adjustments/v1/{manualAdjustmentId}/reject', '/finance/manual-adjustments/v1/{manualAdjustmentId}/reject', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120406, 'operations-finance-billing-fact-list', 'OPERATIONS', 'GET', '/api/operations/finance/billing-facts/v1', '/finance/billing-facts/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120407, 'operations-finance-billing-fact-submit', 'OPERATIONS', 'POST', '/api/operations/finance/billing-facts/v1', '/finance/billing-facts/v1', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120408, 'operations-finance-billing-fact-detail', 'OPERATIONS', 'GET', '/api/operations/finance/billing-facts/v1/{factId}', '/finance/billing-facts/v1/{factId}', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120409, 'operations-finance-billing-fact-approve', 'OPERATIONS', 'POST', '/api/operations/finance/billing-facts/v1/{factId}/approve', '/finance/billing-facts/v1/{factId}/approve', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time),
  (900000000000120410, 'operations-finance-billing-fact-void', 'OPERATIONS', 'POST', '/api/operations/finance/billing-facts/v1/{factId}/void', '/finance/billing-facts/v1/{factId}/void', @finance_adjustment_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_adjustment_console_actor_id, @finance_adjustment_console_actor_id, @finance_adjustment_console_time, @finance_adjustment_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000120501, 900000000000120401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120502, 900000000000120401, 'origin', 'FORWARD_REQUEST'),
  (900000000000120503, 900000000000120402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120504, 900000000000120402, 'origin', 'FORWARD_REQUEST'),
  (900000000000120505, 900000000000120402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120506, 900000000000120402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120507, 900000000000120402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120508, 900000000000120402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120509, 900000000000120403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120510, 900000000000120403, 'origin', 'FORWARD_REQUEST'),
  (900000000000120511, 900000000000120404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120512, 900000000000120404, 'origin', 'FORWARD_REQUEST'),
  (900000000000120513, 900000000000120404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120514, 900000000000120404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120515, 900000000000120404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120516, 900000000000120404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120517, 900000000000120405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120518, 900000000000120405, 'origin', 'FORWARD_REQUEST'),
  (900000000000120519, 900000000000120405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120520, 900000000000120405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120521, 900000000000120405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120522, 900000000000120405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120523, 900000000000120406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120524, 900000000000120406, 'origin', 'FORWARD_REQUEST'),
  (900000000000120525, 900000000000120407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120526, 900000000000120407, 'origin', 'FORWARD_REQUEST'),
  (900000000000120527, 900000000000120407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120528, 900000000000120407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120529, 900000000000120407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120530, 900000000000120407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120531, 900000000000120408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120532, 900000000000120408, 'origin', 'FORWARD_REQUEST'),
  (900000000000120533, 900000000000120409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120534, 900000000000120409, 'origin', 'FORWARD_REQUEST'),
  (900000000000120535, 900000000000120409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120536, 900000000000120409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120537, 900000000000120409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120538, 900000000000120409, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000120539, 900000000000120410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000120540, 900000000000120410, 'origin', 'FORWARD_REQUEST'),
  (900000000000120541, 900000000000120410, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000120542, 900000000000120410, 'content-type', 'FORWARD_REQUEST'),
  (900000000000120543, 900000000000120410, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000120544, 900000000000120410, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000120601, 900000000000120401, 'ledgerBookId'),
  (900000000000120602, 900000000000120401, 'accountingPeriodId'),
  (900000000000120603, 900000000000120401, 'status'),
  (900000000000120604, 900000000000120401, 'makerId'),
  (900000000000120605, 900000000000120401, 'page'),
  (900000000000120606, 900000000000120401, 'size'),
  (900000000000120607, 900000000000120406, 'entityNo'),
  (900000000000120608, 900000000000120406, 'currency'),
  (900000000000120609, 900000000000120406, 'clientId'),
  (900000000000120610, 900000000000120406, 'status'),
  (900000000000120611, 900000000000120406, 'factType'),
  (900000000000120612, 900000000000120406, 'createdFrom'),
  (900000000000120613, 900000000000120406, 'createdTo'),
  (900000000000120614, 900000000000120406, 'page'),
  (900000000000120615, 900000000000120406, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000120701, 900000000000120401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120702, 900000000000120401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120703, 900000000000120401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120704, 900000000000120401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120705, 900000000000120401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120706, 900000000000120401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120707, 900000000000120402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120708, 900000000000120402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120709, 900000000000120402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120710, 900000000000120402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120711, 900000000000120402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120712, 900000000000120402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120713, 900000000000120403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120714, 900000000000120403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120715, 900000000000120403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120716, 900000000000120403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120717, 900000000000120403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120718, 900000000000120403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120719, 900000000000120404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120720, 900000000000120404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120721, 900000000000120404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120722, 900000000000120404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120723, 900000000000120404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120724, 900000000000120404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120725, 900000000000120405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120726, 900000000000120405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120727, 900000000000120405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120728, 900000000000120405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120729, 900000000000120405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120730, 900000000000120405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120731, 900000000000120406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120732, 900000000000120406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120733, 900000000000120406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120734, 900000000000120406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120735, 900000000000120406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120736, 900000000000120406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120737, 900000000000120407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120738, 900000000000120407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120739, 900000000000120407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120740, 900000000000120407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120741, 900000000000120407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120742, 900000000000120407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120743, 900000000000120408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120744, 900000000000120408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120745, 900000000000120408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120746, 900000000000120408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120747, 900000000000120408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000120748, 900000000000120408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120749, 900000000000120409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120750, 900000000000120409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120751, 900000000000120409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120752, 900000000000120409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120753, 900000000000120409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120754, 900000000000120409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000120755, 900000000000120410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000120756, 900000000000120410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000120757, 900000000000120410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000120758, 900000000000120410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000120759, 900000000000120410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000120760, 900000000000120410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance period close and trial balance run console (2026-10-20, requirements/20261020/finance-期间关账与试算).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102, OPERATIONS_FINANCE 900000000000097301, OPERATIONS_FINANCE_LEDGER 900000000000097303) and the
-- manual adjustment block above (FINANCE_ACCOUNTING_APPROVE 900000000000120101).
-- No new permission or role grant. Dormant: menu/buttons/routes DISABLED; activate them in a separate reviewed change.
-- BFF routes forward to ledger (PeriodCloseApi, TrialBalanceApi.runTrialBalance); the board route is a BFF composition of
-- the calendar period list and each period's latest close record. OPERATIONS_FINANCE_LEDGER_MANAGE is the trial-balance
-- run button on the existing ledger query page.
-- Rows: console_resource 4, gateway_route 8, route_header 36, query_param 3, guard_rule 48 (99).
-- ID block 121: 1213xx console_resource,
-- 1214xx route, 1215xx route_header, 1216xx query_param, 1217xx guard_rule.
-- Rollback: delete the rows by the 900000000000121301-900000000000121799 IDs below (children first: guard_rule,
-- query_param, route_header, gateway_route, console_resource).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_period_close_console_time = UTC_TIMESTAMP(6);
SET @finance_period_close_console_actor_id = 1;
SET @finance_period_close_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000121301, 'OPERATIONS_FINANCE_PERIOD_CLOSE', 'OPERATIONS', 'MENU', 900000000000097301, '期间关账', '/finance/period-close', 'OPERATIONS_FINANCE_PERIOD_CLOSE', 900000000000097101, 60, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121302, 'OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000121301, '发起', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121303, 'OPERATIONS_FINANCE_PERIOD_CLOSE_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000121301, '复核', NULL, NULL, 900000000000120101, 20, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121304, 'OPERATIONS_FINANCE_LEDGER_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000097303, '发起试算', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_period_close_console_time, @finance_period_close_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000121401, 'operations-finance-period-close-board', 'OPERATIONS', 'GET', '/api/operations/finance/period-close/v1/board', '/finance/period-close/v1/board', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121402, 'operations-finance-period-close-list', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes', '/finance/accounting-periods/v1/{accountingPeriodId}/closes', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121403, 'operations-finance-period-close-detail', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}', '/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121404, 'operations-finance-period-close-start', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes', '/finance/accounting-periods/v1/{accountingPeriodId}/closes', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121405, 'operations-finance-period-reopen-start', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/reopens', '/finance/accounting-periods/v1/{accountingPeriodId}/reopens', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121406, 'operations-finance-period-close-approve', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve', '/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121407, 'operations-finance-period-close-reject', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject', '/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time),
  (900000000000121408, 'operations-finance-trial-balance-run', 'OPERATIONS', 'POST', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', @finance_period_close_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_period_close_console_actor_id, @finance_period_close_console_actor_id, @finance_period_close_console_time, @finance_period_close_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000121501, 900000000000121401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121502, 900000000000121401, 'origin', 'FORWARD_REQUEST'),
  (900000000000121503, 900000000000121402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121504, 900000000000121402, 'origin', 'FORWARD_REQUEST'),
  (900000000000121505, 900000000000121403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121506, 900000000000121403, 'origin', 'FORWARD_REQUEST'),
  (900000000000121507, 900000000000121404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121508, 900000000000121404, 'origin', 'FORWARD_REQUEST'),
  (900000000000121509, 900000000000121404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121510, 900000000000121404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121511, 900000000000121404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121512, 900000000000121404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121513, 900000000000121405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121514, 900000000000121405, 'origin', 'FORWARD_REQUEST'),
  (900000000000121515, 900000000000121405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121516, 900000000000121405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121517, 900000000000121405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121518, 900000000000121405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121519, 900000000000121406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121520, 900000000000121406, 'origin', 'FORWARD_REQUEST'),
  (900000000000121521, 900000000000121406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121522, 900000000000121406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121523, 900000000000121406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121524, 900000000000121406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121525, 900000000000121407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121526, 900000000000121407, 'origin', 'FORWARD_REQUEST'),
  (900000000000121527, 900000000000121407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121528, 900000000000121407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121529, 900000000000121407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121530, 900000000000121407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000121531, 900000000000121408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000121532, 900000000000121408, 'origin', 'FORWARD_REQUEST'),
  (900000000000121533, 900000000000121408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000121534, 900000000000121408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000121535, 900000000000121408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000121536, 900000000000121408, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000121601, 900000000000121401, 'fiscalCalendarId'),
  (900000000000121602, 900000000000121402, 'page'),
  (900000000000121603, 900000000000121402, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000121701, 900000000000121401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121702, 900000000000121401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121703, 900000000000121401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121704, 900000000000121401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121705, 900000000000121401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000121706, 900000000000121401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121707, 900000000000121402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121708, 900000000000121402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121709, 900000000000121402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121710, 900000000000121402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121711, 900000000000121402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000121712, 900000000000121402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121713, 900000000000121403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121714, 900000000000121403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121715, 900000000000121403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121716, 900000000000121403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121717, 900000000000121403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000121718, 900000000000121403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121719, 900000000000121404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121720, 900000000000121404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121721, 900000000000121404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121722, 900000000000121404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121723, 900000000000121404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121724, 900000000000121404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121725, 900000000000121405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121726, 900000000000121405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121727, 900000000000121405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121728, 900000000000121405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121729, 900000000000121405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121730, 900000000000121405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121731, 900000000000121406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121732, 900000000000121406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121733, 900000000000121406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121734, 900000000000121406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121735, 900000000000121406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121736, 900000000000121406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121737, 900000000000121407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121738, 900000000000121407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121739, 900000000000121407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121740, 900000000000121407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121741, 900000000000121407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121742, 900000000000121407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000121743, 900000000000121408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000121744, 900000000000121408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000121745, 900000000000121408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000121746, 900000000000121408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000121747, 900000000000121408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000121748, 900000000000121408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance accounting rule bundle console (2026-10-20, requirements/20261020/finance-记账规则包维护).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102, OPERATIONS_FINANCE 900000000000097301) and the manual adjustment block above
-- (FINANCE_ACCOUNTING_APPROVE 900000000000120101).
-- No new permission or role grant. Dormant: menu/buttons/routes DISABLED; activate them in a separate reviewed change.
-- BFF routes forward to ledger (AccountingRuleBundleApi); bundles themselves are still defined only by initialization data.
-- Rows: console_resource 3, gateway_route 8, route_header 36, guard_rule 48 (95).
-- ID block 122: 1223xx console_resource,
-- 1224xx route, 1225xx route_header, 1227xx guard_rule.
-- Rollback: delete the rows by the 900000000000122301-900000000000122799 IDs below (children first: guard_rule,
-- route_header, gateway_route, console_resource).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_rule_console_time = UTC_TIMESTAMP(6);
SET @finance_rule_console_actor_id = 1;
SET @finance_rule_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000122301, 'OPERATIONS_FINANCE_ACCOUNTING_RULES', 'OPERATIONS', 'MENU', 900000000000097301, '记账规则', '/finance/accounting-rules', 'OPERATIONS_FINANCE_ACCOUNTING_RULES', 900000000000097101, 70, 'DISABLED', 1, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122302, 'OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000122301, '制单', NULL, NULL, 900000000000097102, 10, 'DISABLED', 1, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122303, 'OPERATIONS_FINANCE_ACCOUNTING_RULES_APPROVE', 'OPERATIONS', 'BUTTON', 900000000000122301, '复核', NULL, NULL, 900000000000120101, 20, 'DISABLED', 1, @finance_rule_console_time, @finance_rule_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000122401, 'operations-finance-rule-bundle-list', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-rule-bundles/v1', '/finance/accounting-rule-bundles/v1', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122402, 'operations-finance-rule-bundle-detail', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}', '/finance/accounting-rule-bundles/v1/{bundleCode}', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122403, 'operations-finance-rule-version-detail', 'OPERATIONS', 'GET', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122404, 'operations-finance-rule-version-create', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122405, 'operations-finance-rule-version-submit', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/submit', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/submit', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122406, 'operations-finance-rule-version-approve', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/approve', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/approve', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122407, 'operations-finance-rule-version-reject', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/reject', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/reject', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000120101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time),
  (900000000000122408, 'operations-finance-rule-version-void', 'OPERATIONS', 'POST', '/api/operations/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void', '/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void', @finance_rule_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_rule_console_actor_id, @finance_rule_console_actor_id, @finance_rule_console_time, @finance_rule_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000122501, 900000000000122401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122502, 900000000000122401, 'origin', 'FORWARD_REQUEST'),
  (900000000000122503, 900000000000122402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122504, 900000000000122402, 'origin', 'FORWARD_REQUEST'),
  (900000000000122505, 900000000000122403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122506, 900000000000122403, 'origin', 'FORWARD_REQUEST'),
  (900000000000122507, 900000000000122404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122508, 900000000000122404, 'origin', 'FORWARD_REQUEST'),
  (900000000000122509, 900000000000122404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122510, 900000000000122404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122511, 900000000000122404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122512, 900000000000122404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122513, 900000000000122405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122514, 900000000000122405, 'origin', 'FORWARD_REQUEST'),
  (900000000000122515, 900000000000122405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122516, 900000000000122405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122517, 900000000000122405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122518, 900000000000122405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122519, 900000000000122406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122520, 900000000000122406, 'origin', 'FORWARD_REQUEST'),
  (900000000000122521, 900000000000122406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122522, 900000000000122406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122523, 900000000000122406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122524, 900000000000122406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122525, 900000000000122407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122526, 900000000000122407, 'origin', 'FORWARD_REQUEST'),
  (900000000000122527, 900000000000122407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122528, 900000000000122407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122529, 900000000000122407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122530, 900000000000122407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000122531, 900000000000122408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000122532, 900000000000122408, 'origin', 'FORWARD_REQUEST'),
  (900000000000122533, 900000000000122408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000122534, 900000000000122408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000122535, 900000000000122408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000122536, 900000000000122408, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000122701, 900000000000122401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122702, 900000000000122401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122703, 900000000000122401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122704, 900000000000122401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122705, 900000000000122401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000122706, 900000000000122401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122707, 900000000000122402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122708, 900000000000122402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122709, 900000000000122402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122710, 900000000000122402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122711, 900000000000122402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000122712, 900000000000122402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122713, 900000000000122403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122714, 900000000000122403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122715, 900000000000122403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122716, 900000000000122403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122717, 900000000000122403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000122718, 900000000000122403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122719, 900000000000122404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122720, 900000000000122404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122721, 900000000000122404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122722, 900000000000122404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122723, 900000000000122404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122724, 900000000000122404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122725, 900000000000122405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122726, 900000000000122405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122727, 900000000000122405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122728, 900000000000122405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122729, 900000000000122405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122730, 900000000000122405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122731, 900000000000122406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122732, 900000000000122406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122733, 900000000000122406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122734, 900000000000122406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122735, 900000000000122406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122736, 900000000000122406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122737, 900000000000122407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122738, 900000000000122407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122739, 900000000000122407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122740, 900000000000122407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122741, 900000000000122407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122742, 900000000000122407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000122743, 900000000000122408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000122744, 900000000000122408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000122745, 900000000000122408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000122746, 900000000000122408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000122747, 900000000000122408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000122748, 900000000000122408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
