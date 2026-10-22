-- 工作流附件内嵌预览跟进 · mor-test 执行后只读核对。ok=1 表示符合。路由版本期望 = 00-precheck 记录值 + 1（阶段 6 记录为 2，执行后 3）。
SELECT 'query_param 127030 present' AS item, COUNT(*) AS actual, 1 AS expect, COUNT(*) = 1 AS ok
  FROM mor_platform.t_gateway_route_query_param
 WHERE route_query_param_id = 900000000000127030 AND route_id = 900000000000127432 AND parameter_name = 'disposition'
UNION ALL SELECT 'block 127 query_param rows', COUNT(*), 30, COUNT(*) = 30
  FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'route 127432 ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_route WHERE route_id = 900000000000127432 AND status = 'ACTIVE'
UNION ALL SELECT 'block 127 gateway_route ACTIVE', COUNT(*), 32, COUNT(*) = 32
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999 AND status = 'ACTIVE';

SELECT route_id, status, version, update_time FROM mor_platform.t_gateway_route WHERE route_id = 900000000000127432;
