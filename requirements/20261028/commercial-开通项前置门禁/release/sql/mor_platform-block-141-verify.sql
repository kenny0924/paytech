-- 块 141 核对（只读；注册后、激活后、停用后都可执行）。
-- 期望：route 3（休眠 DISABLED / 激活 ACTIVE）、header 18（每路由 6）、guard 18（每路由 6），属性与同形路由 95405 / 95409 一致。
SELECT 'block141_route' AS t, route_id, route_code, http_method, permission_id, body_limit_bytes, status, version
  FROM t_gateway_route WHERE route_id BETWEEN 900000000000141001 AND 900000000000141999 ORDER BY route_id;
SELECT 'block141_children' AS t,
  (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000141001 AND 900000000000141999) AS headers,
  (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000141001 AND 900000000000141999) AS guards,
  (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000141001 AND 900000000000141999) AS query_params;
-- 与同形路由 95409 的 header / guard 逐行比较：差异行数应为 0
SELECT 'header_shape_diff' AS t, COUNT(*) AS n FROM (
  SELECT r.route_id, h.header_name, h.header_use FROM t_gateway_route r
    JOIN t_gateway_route_header s ON s.route_id = 900000000000095409
    LEFT JOIN t_gateway_route_header h ON h.route_id = r.route_id AND h.header_name = s.header_name AND h.header_use = s.header_use
   WHERE r.route_id BETWEEN 900000000000141401 AND 900000000000141403 AND h.route_header_id IS NULL) x;
SELECT 'guard_shape_diff' AS t, COUNT(*) AS n FROM (
  SELECT r.route_id FROM t_gateway_route r
    JOIN t_gateway_guard_rule s ON s.route_id = 900000000000095409
    LEFT JOIN t_gateway_guard_rule g ON g.route_id = r.route_id AND g.rule_type = s.rule_type AND g.dimension = s.dimension
      AND g.capacity <=> s.capacity AND g.refill_tokens <=> s.refill_tokens AND g.refill_period_millis <=> s.refill_period_millis
      AND g.concurrency_limit <=> s.concurrency_limit
   WHERE r.route_id BETWEEN 900000000000141401 AND 900000000000141403 AND g.guard_rule_id IS NULL) x;
SELECT 'routes_by_status' AS t, status, COUNT(*) AS n FROM t_gateway_route GROUP BY status ORDER BY status;
