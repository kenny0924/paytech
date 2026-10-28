-- 块 140 注册回退（子表先删），只在 mor_platform 执行。前置：路由已是 DISABLED（先执行 12）；未停用时本脚本拒绝执行。
-- 预期删除：guard_rule 6、query_param 6、route_header 2、route 1（共 15 行）。
START TRANSACTION;
SET @b140_active = (SELECT COUNT(*) FROM t_gateway_route WHERE route_id = 900000000000140401 AND status = 'ACTIVE');
-- 路由仍 ACTIVE 时用子查询制造 ERROR 1242 中止（与块 139 预检同一手法），避免删掉在用路由。
SELECT IF(@b140_active = 0, 0, (SELECT 1 UNION ALL SELECT 2)) AS must_be_disabled;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000140701 AND 900000000000140706 AND route_id = 900000000000140401;
SELECT ROW_COUNT() AS guard_rules;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000140601 AND 900000000000140606 AND route_id = 900000000000140401;
SELECT ROW_COUNT() AS query_params;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000140501 AND 900000000000140502 AND route_id = 900000000000140401;
SELECT ROW_COUNT() AS route_headers;
DELETE FROM t_gateway_route WHERE route_id = 900000000000140401;
SELECT ROW_COUNT() AS routes;
COMMIT;
