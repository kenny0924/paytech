-- Reading copy only. The single execution source is the finance subledger console block (block 99, the last block) of
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql; do not apply this file separately.

-- ---------------------------------------------------------------------------
-- Operations finance subledger query console (2026-10-19, requirements/20261019/finance-account明细账与ledger控制科目重构).
-- Self-contained block: on an environment already initialized by the statements above, apply ONLY this block.
-- Requires the finance setup and ledger block above (FINANCE_ACCOUNTING_READ 900000000000097101, OPERATIONS_FINANCE
-- 900000000000097301); no new permission or role grant. Dormant: menu and routes DISABLED; activate them in a separate
-- reviewed change. BFF routes forward to account (OwnerAccountQueryApi, AccountPostingApi).
-- Rows: console_resource 1, gateway_route 3, route_header 6, query_param 11, guard_rule 18 (39).
-- Rollback: delete the rows by the 9000000000000993xx-997xx IDs below.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @finance_subledger_console_time = UTC_TIMESTAMP(6);
SET @finance_subledger_console_actor_id = 1;
SET @finance_subledger_console_target_id = 900000000000000001;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000099301, 'OPERATIONS_FINANCE_SUBLEDGER', 'OPERATIONS', 'MENU', 900000000000097301, '明细账查询', '/finance/subledger', 'OPERATIONS_FINANCE_SUBLEDGER', 900000000000097101, 40, 'DISABLED', 1, @finance_subledger_console_time, @finance_subledger_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000099401, 'operations-finance-owner-accounts', 'OPERATIONS', 'GET', '/api/operations/finance/owner-accounts/v1', '/finance/owner-accounts/v1', @finance_subledger_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_subledger_console_actor_id, @finance_subledger_console_actor_id, @finance_subledger_console_time, @finance_subledger_console_time),
  (900000000000099402, 'operations-finance-owner-account-entries', 'OPERATIONS', 'GET', '/api/operations/finance/owner-accounts/v1/{accountId}/entries', '/finance/owner-accounts/v1/{accountId}/entries', @finance_subledger_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_subledger_console_actor_id, @finance_subledger_console_actor_id, @finance_subledger_console_time, @finance_subledger_console_time),
  (900000000000099403, 'operations-finance-account-posting', 'OPERATIONS', 'GET', '/api/operations/finance/account-postings/v1/{postingId}', '/finance/account-postings/v1/{postingId}', @finance_subledger_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000097101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @finance_subledger_console_actor_id, @finance_subledger_console_actor_id, @finance_subledger_console_time, @finance_subledger_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000099501, 900000000000099401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000099502, 900000000000099401, 'origin', 'FORWARD_REQUEST'),
  (900000000000099503, 900000000000099402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000099504, 900000000000099402, 'origin', 'FORWARD_REQUEST'),
  (900000000000099505, 900000000000099403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000099506, 900000000000099403, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000099601, 900000000000099401, 'entityType'),
  (900000000000099602, 900000000000099401, 'entityId'),
  (900000000000099603, 900000000000099401, 'currency'),
  (900000000000099604, 900000000000099401, 'ownerType'),
  (900000000000099605, 900000000000099401, 'ownerId'),
  (900000000000099606, 900000000000099401, 'accountCode'),
  (900000000000099607, 900000000000099401, 'balanceBucket'),
  (900000000000099608, 900000000000099401, 'page'),
  (900000000000099609, 900000000000099401, 'size'),
  (900000000000099610, 900000000000099402, 'page'),
  (900000000000099611, 900000000000099402, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000099701, 900000000000099401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000099702, 900000000000099401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000099703, 900000000000099401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000099704, 900000000000099401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000099705, 900000000000099401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000099706, 900000000000099401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000099707, 900000000000099402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000099708, 900000000000099402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000099709, 900000000000099402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000099710, 900000000000099402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000099711, 900000000000099402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000099712, 900000000000099402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000099713, 900000000000099403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000099714, 900000000000099403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000099715, 900000000000099403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000099716, 900000000000099403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000099717, 900000000000099403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000099718, 900000000000099403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
