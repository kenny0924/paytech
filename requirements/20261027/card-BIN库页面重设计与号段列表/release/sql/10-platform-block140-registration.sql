-- 未执行（2026-10-27 阶段 4）：card：BIN 库页面重设计与号段列表，目标库 mor_platform（platform-support-service）。
-- 一段，一个事务：注册块 140（运营页面「卡 BIN 库」号段列表的 BFF 路由），休眠写入，激活另行执行。
-- 阶段 4 已同步追加到 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾（同一块、同一 ID）。
-- 不新增权限、菜单或按钮：沿用块 132 的 CARD_BIN_LIBRARY_READ（900000000000132101）。
-- Platform 运营账号显示名接口（GET /operations-accounts/v1/labels）为 BFF → Platform 内部调用，不经 Gateway，不注册路由。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Operations card BIN range list (2026-10-27, requirements/20261027/card-BIN库页面重设计与号段列表, technique.md 3.4).
-- Self-contained block: on an environment already holding block 132 (card BIN library console), apply ONLY this block.
-- Dormant like block 132: route DISABLED; activate it in a separate reviewed change. The BFF route forwards to
-- operations-console-bff on /card-admin/v1/bin-library/versions/{versionNo}/ranges, which forwards to card-service
-- (/card/v1/bin-library/versions/{versionNo}/ranges); operator identity comes from the Gateway actor headers.
-- Permission: existing CARD_BIN_LIBRARY_READ (900000000000132101, block 132); no new permission, menu or button.
-- Shape copies route 900000000000132405 (operations-card-bin-lookup) one-for-one: GET, body limit 0, headers
-- authorization (CORS_REQUEST_ALLOWED) + origin (FORWARD_REQUEST); guard rules SHARED_ROUTE 300/min + 50 concurrent,
-- CLIENT_IP 30/min + 3, AUTHENTICATED_PRINCIPAL 30/min + 2. Query whitelist: binPrefix, cardBrand, fundingType,
-- issuerCountry, page, size.
-- Rows: gateway_route 1, route_header 2, query_param 6, guard_rule 6 (15). ID block 140: 1404xx route, 1405xx route_header,
-- 1406xx query_param, 1407xx guard_rule.
-- Before applying, re-check on the live mor_platform that no row in 900000000000140001-900000000000140999 exists, that
-- route 900000000000132405 and permission 900000000000132101 exist, and that the route code operations-card-bin-range-list
-- and the pair (GET, /api/operations/card-admin/v1/bin-library/versions/{versionNo}/ranges) are unused. Repository check
-- on 2026-10-27: block 140 and the route code / (method, path) appear in no other requirement, paytech or local-environment file.
-- Rollback (children first):
--   DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000140701 AND 900000000000140706;
--   DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000140601 AND 900000000000140606;
--   DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000140501 AND 900000000000140502;
--   DELETE FROM t_gateway_route WHERE route_id = 900000000000140401;
--   (if the route was already ACTIVE, republish the Gateway Snapshot after the deletion.)
-- Activation (separate reviewed change, after Card/ops-bff/ops-web are deployed):
--   UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id = 900000000000140401;
--   then republish the Gateway Snapshot.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @card_bin_range_time = UTC_TIMESTAMP(6);
SET @card_bin_range_actor_id = 1;
SET @card_bin_range_target_id = 900000000000000001;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000140401, 'operations-card-bin-range-list', 'OPERATIONS', 'GET', '/api/operations/card-admin/v1/bin-library/versions/{versionNo}/ranges', '/card-admin/v1/bin-library/versions/{versionNo}/ranges', @card_bin_range_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000132101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @card_bin_range_actor_id, @card_bin_range_actor_id, @card_bin_range_time, @card_bin_range_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000140501, 900000000000140401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000140502, 900000000000140401, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000140601, 900000000000140401, 'binPrefix'),
  (900000000000140602, 900000000000140401, 'cardBrand'),
  (900000000000140603, 900000000000140401, 'fundingType'),
  (900000000000140604, 900000000000140401, 'issuerCountry'),
  (900000000000140605, 900000000000140401, 'page'),
  (900000000000140606, 900000000000140401, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000140701, 900000000000140401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000140702, 900000000000140401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000140703, 900000000000140401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000140704, 900000000000140401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000140705, 900000000000140401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000140706, 900000000000140401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
