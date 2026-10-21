-- 对账控制台详情页与渠道流水 · 回滚注册（只在需要彻底撤销时用；先执行 test-deactivate.sql）。目标库 mor_platform，root，utf8mb4。
-- 删除块 126 的 38 行（子表先删）：限流 18、Query 10、Header 6、路由 3、资源 1。仅删 DISABLED 的路由 / 资源，ACTIVE 时中止。
START TRANSACTION;
SELECT COUNT(*) INTO @b126_active FROM t_gateway_route WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND status = 'ACTIVE';
SELECT COUNT(*) + @b126_active INTO @b126_active FROM t_console_resource WHERE resource_id = 900000000000126301 AND status = 'ACTIVE';
SELECT IF(@b126_active = 0, 'ok', 'ABORT: block 126 still ACTIVE, run test-deactivate.sql first') AS precondition;
DELETE FROM t_gateway_guard_rule WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND @b126_active = 0;
SELECT ROW_COUNT() AS guard_rules_18;
DELETE FROM t_gateway_route_query_param WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND @b126_active = 0;
SELECT ROW_COUNT() AS query_params_10;
DELETE FROM t_gateway_route_header WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND @b126_active = 0;
SELECT ROW_COUNT() AS headers_6;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND status = 'DISABLED' AND @b126_active = 0;
SELECT ROW_COUNT() AS routes_3;
DELETE FROM t_console_resource WHERE resource_id = 900000000000126301 AND status = 'DISABLED' AND @b126_active = 0;
SELECT ROW_COUNT() AS resources_1;
COMMIT;
