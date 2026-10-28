-- REQ-01 阶段 5 本机联调专用（只用于 mor_platform_v2e2e）：为 3 个新运营端点登记 Gateway 路由，照抄同形态的
-- operations-commercial-capability-create（POST，ACTIVE）的属性、6 条请求头与 6 条 Guard。
-- 不是阶段 6 注册块：正式 ID 号段、Console Resource 与 Platform 初始化数据在阶段 6 另行编写。本地 ID 号段 9000000000013904xx-9000000000013906xx。
-- 权限：suspension / resumption = COMMERCIAL_CONTRACT_MANAGE（900000000000095102）；prerequisite-checks = COMMERCIAL_CONTRACT_READ（900000000000095101）。
-- 回滚：同目录 20-platform-gate-routes-local-rollback.sql。
SET NAMES utf8mb4;
START TRANSACTION;
SET @src = 900000000000095408;
SET @now = UTC_TIMESTAMP(6);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id,
  security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode,
  idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by,
  create_time, update_time)
SELECT x.route_id, x.route_code, r.channel, 'POST', x.external_path, x.downstream_path, r.target_id, r.security_profile_code,
  r.authorization_mode, x.permission_id, r.required_scope_code, r.request_cookie_mode, r.response_cookie_mode, r.idempotency_mode,
  r.body_limit_bytes, r.body_receive_timeout_millis, r.response_timeout_millis, 'ACTIVE', 1, 1, 1, @now, @now
FROM t_gateway_route r JOIN (
  SELECT 900000000001390401 AS route_id, 'operations-commercial-capability-suspend' AS route_code,
    '/api/operations/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/suspension' AS external_path,
    '/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/suspension' AS downstream_path,
    900000000000095102 AS permission_id
  UNION ALL SELECT 900000000001390402, 'operations-commercial-capability-resume',
    '/api/operations/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/resumption',
    '/commercial-admin/v1/agreements/{agreementId}/capabilities/{agreementCapabilityId}/resumption', 900000000000095102
  UNION ALL SELECT 900000000001390403, 'operations-commercial-capability-prerequisite-check',
    '/api/operations/commercial-admin/v1/agreements/{agreementId}/prerequisite-checks',
    '/commercial-admin/v1/agreements/{agreementId}/prerequisite-checks', 900000000000095101
) x WHERE r.route_id = @src;

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000001390500 + n.k * 10 + ROW_NUMBER() OVER (PARTITION BY n.k ORDER BY h.route_header_id), n.route_id, h.header_name, h.header_use
FROM t_gateway_route_header h JOIN (SELECT 1 k, 900000000001390401 route_id UNION ALL SELECT 2, 900000000001390402
  UNION ALL SELECT 3, 900000000001390403) n WHERE h.route_id = @src;

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000001390600 + n.k * 10 + ROW_NUMBER() OVER (PARTITION BY n.k ORDER BY g.guard_rule_id), n.route_id, g.rule_type, g.dimension,
  g.capacity, g.refill_tokens, g.refill_period_millis, g.concurrency_limit
FROM t_gateway_guard_rule g JOIN (SELECT 1 k, 900000000001390401 route_id UNION ALL SELECT 2, 900000000001390402
  UNION ALL SELECT 3, 900000000001390403) n WHERE g.route_id = @src;
COMMIT;
