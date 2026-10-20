-- 目标库：mor_platform（Platform Support）。阅读副本，未执行；阶段 4 原样追加到唯一执行来源
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾（块 91），不要单独执行本文件。
-- 不可重复执行：固定主键重复插入会失败；回滚按下方 9000000000000914xx-917xx ID 段删除。

-- ---------------------------------------------------------------------------
-- Operations finance chart maintenance and ledger balance query console (2026-10-19, requirements/20261019/ledger-会计科目分级维护).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, FINANCE_ACCOUNTING_MANAGE
-- 900000000000097102) and the finance subledger block above (route 900000000000099401); no new permission, role grant or
-- console resource (chart maintenance reuses OPERATIONS_FINANCE_SETUP_MANAGE). Dormant: routes DISABLED; activate them in a separate
-- reviewed change. BFF routes forward to ledger (ChartOfAccountsApi, LedgerBookApi, TrialBalanceApi). The three extra
-- query parameters extend existing routes 900000000000097415, 900000000000097410 and 900000000000099401.
-- Rows: gateway_route 8, route_header 32, query_param 8, guard_rule 48 (96). The trial-balance run route and a ledger
-- MANAGE button are intentionally not registered in this requirement.
-- Rollback: delete the rows by the 9000000000000914xx-917xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_chart_console_time = UTC_TIMESTAMP(6);
SET @finance_chart_console_actor_id = 1;
SET @finance_chart_console_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000091401, 'operations-finance-chart-account-create', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts', '/finance/chart-of-accounts/v1/{chartCode}/accounts', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091402, 'operations-finance-chart-account-update', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/update', '/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/update', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091403, 'operations-finance-chart-account-disable', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/disable', '/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/disable', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091404, 'operations-finance-chart-account-enable', 'OPERATIONS', 'POST', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/enable', '/finance/chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/enable', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091405, 'operations-finance-chart-account-change-list', 'OPERATIONS', 'GET', '/api/operations/finance/chart-of-accounts/v1/{chartCode}/changes', '/finance/chart-of-accounts/v1/{chartCode}/changes', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091406, 'operations-finance-account-balance-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091407, 'operations-finance-trial-balance-list', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time),
  (900000000000091408, 'operations-finance-trial-balance-detail', 'OPERATIONS', 'GET', '/api/operations/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}', '/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}', @finance_chart_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_chart_console_actor_id, @finance_chart_console_actor_id, @finance_chart_console_time, @finance_chart_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000091501, 900000000000091401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091502, 900000000000091401, 'origin', 'FORWARD_REQUEST'),
  (900000000000091503, 900000000000091401, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091504, 900000000000091401, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091505, 900000000000091401, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091506, 900000000000091401, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091507, 900000000000091402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091508, 900000000000091402, 'origin', 'FORWARD_REQUEST'),
  (900000000000091509, 900000000000091402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091510, 900000000000091402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091511, 900000000000091402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091512, 900000000000091402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091513, 900000000000091403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091514, 900000000000091403, 'origin', 'FORWARD_REQUEST'),
  (900000000000091515, 900000000000091403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091516, 900000000000091403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091517, 900000000000091403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091518, 900000000000091403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091519, 900000000000091404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091520, 900000000000091404, 'origin', 'FORWARD_REQUEST'),
  (900000000000091521, 900000000000091404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000091522, 900000000000091404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000091523, 900000000000091404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000091524, 900000000000091404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000091525, 900000000000091405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091526, 900000000000091405, 'origin', 'FORWARD_REQUEST'),
  (900000000000091527, 900000000000091406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091528, 900000000000091406, 'origin', 'FORWARD_REQUEST'),
  (900000000000091529, 900000000000091407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091530, 900000000000091407, 'origin', 'FORWARD_REQUEST'),
  (900000000000091531, 900000000000091408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000091532, 900000000000091408, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000091601, 900000000000091405, 'chartAccountId'),
  (900000000000091602, 900000000000091405, 'page'),
  (900000000000091603, 900000000000091405, 'size'),
  (900000000000091604, 900000000000091407, 'page'),
  (900000000000091605, 900000000000091407, 'size'),
  (900000000000091606, 900000000000097415, 'accountStatus'),
  (900000000000091607, 900000000000097410, 'accountNo'),
  (900000000000091608, 900000000000099401, 'accountNo');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000091701, 900000000000091401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091702, 900000000000091401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091703, 900000000000091401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091704, 900000000000091401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091705, 900000000000091401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091706, 900000000000091401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091707, 900000000000091402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091708, 900000000000091402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091709, 900000000000091402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091710, 900000000000091402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091711, 900000000000091402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091712, 900000000000091402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091713, 900000000000091403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091714, 900000000000091403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091715, 900000000000091403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091716, 900000000000091403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091717, 900000000000091403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091718, 900000000000091403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091719, 900000000000091404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091720, 900000000000091404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091721, 900000000000091404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091722, 900000000000091404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091723, 900000000000091404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091724, 900000000000091404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091725, 900000000000091405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091726, 900000000000091405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091727, 900000000000091405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091728, 900000000000091405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091729, 900000000000091405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000091730, 900000000000091405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091731, 900000000000091406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091732, 900000000000091406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091733, 900000000000091406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091734, 900000000000091406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091735, 900000000000091406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000091736, 900000000000091406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091737, 900000000000091407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091738, 900000000000091407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091739, 900000000000091407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091740, 900000000000091407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091741, 900000000000091407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000091742, 900000000000091407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000091743, 900000000000091408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000091744, 900000000000091408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000091745, 900000000000091408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000091746, 900000000000091408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000091747, 900000000000091408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000091748, 900000000000091408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
