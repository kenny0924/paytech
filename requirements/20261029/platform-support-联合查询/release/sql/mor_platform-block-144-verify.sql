-- 联合查询注册块 144 · 只读核对（休眠后：路由 / 菜单 DISABLED；激活后：ACTIVE）。期望 key_types 26、resources 2、routes 8、headers 36、queries 5、guards 48。
SELECT (SELECT COUNT(*) FROM t_query_key_type WHERE key_type_id BETWEEN 900000000000144101 AND 900000000000144126) AS key_types,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id IN (900000000000144301, 900000000000144302)) AS resources,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408) AS routes,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000144501 AND 900000000000144536) AS headers,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000144601 AND 900000000000144605) AS queries,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000144701 AND 900000000000144748) AS guards;
SELECT status, COUNT(*) AS routes FROM t_gateway_route WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408 GROUP BY status;
SELECT resource_id, resource_code, parent_resource_id, status FROM t_console_resource WHERE resource_id IN (900000000000144301, 900000000000144302) ORDER BY 1;
SELECT status, COUNT(*) AS routes FROM t_gateway_route GROUP BY status ORDER BY status;
