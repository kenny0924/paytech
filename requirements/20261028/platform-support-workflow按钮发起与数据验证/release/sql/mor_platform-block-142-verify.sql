-- REQ-05 注册块 142 只读核对（runbook ⑧）：期望 routes 3、headers 14、queries 3、guards 18；激活前 DISABLED 3，激活后 ACTIVE 3。
SET NAMES utf8mb4;
SELECT (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000142001 AND 900000000000142999) AS routes,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000142001 AND 900000000000142999) AS headers,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000142001 AND 900000000000142999) AS queries,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000142001 AND 900000000000142999) AS guards;
SELECT route_id, route_code, http_method, external_path_template, permission_id, idempotency_mode, status, version
FROM t_gateway_route WHERE route_id BETWEEN 900000000000142401 AND 900000000000142403 ORDER BY route_id;
