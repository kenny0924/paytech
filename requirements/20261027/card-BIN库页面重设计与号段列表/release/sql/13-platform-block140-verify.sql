-- 块 140 执行后只读核对。期望：route 1（10 之后 DISABLED v1，11 之后 ACTIVE v2）、header 2、query_param 6、guard 6，
-- 守卫与同形路由 132405 逐行相同（diff 行 0）。
SELECT route_id, route_code, status, version, permission_id, target_id FROM t_gateway_route WHERE route_id = 900000000000140401;
SELECT 'headers' AS t, COUNT(*) AS n FROM t_gateway_route_header WHERE route_id = 900000000000140401
UNION ALL SELECT 'query_params', COUNT(*) FROM t_gateway_route_query_param WHERE route_id = 900000000000140401
UNION ALL SELECT 'guards', COUNT(*) FROM t_gateway_guard_rule WHERE route_id = 900000000000140401
UNION ALL SELECT 'guard_shape_diff', COUNT(*) FROM (
  SELECT rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit, COUNT(*) c FROM (
    SELECT rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit FROM t_gateway_guard_rule WHERE route_id = 900000000000132405
    UNION ALL
    SELECT rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit FROM t_gateway_guard_rule WHERE route_id = 900000000000140401) u
  GROUP BY rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit HAVING c <> 2) d
UNION ALL SELECT 'routes_active', COUNT(*) FROM t_gateway_route WHERE status = 'ACTIVE';
