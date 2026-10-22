-- 工作流附件内嵌预览跟进 · mor-test 执行前只读核对（不写任何数据；root 或只读账号，客户端 utf8mb4）。
-- ok=1 表示符合期望；任一 ok=0 即停。
SELECT 'query_param 127030 absent' AS item, COUNT(*) AS actual, 0 AS expect, COUNT(*) = 0 AS ok
  FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id = 900000000000127030
UNION ALL SELECT 'route 127432 disposition absent', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route_query_param WHERE route_id = 900000000000127432 AND parameter_name = 'disposition'
UNION ALL SELECT 'route 127432 present and ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_route WHERE route_id = 900000000000127432 AND status = 'ACTIVE'
    AND route_code = 'operations-workflow-attachment-download' AND http_method = 'POST'
UNION ALL SELECT 'route 127432 query params now', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route_query_param WHERE route_id = 900000000000127432
UNION ALL SELECT 'block 127 query_param rows', COUNT(*), 29, COUNT(*) = 29
  FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999
UNION ALL SELECT 'block 127 gateway_route ACTIVE', COUNT(*), 32, COUNT(*) = 32
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000127001 AND 900000000000127999 AND status = 'ACTIVE';

SELECT route_id, route_code, http_method, external_path_template, status, version, update_time
  FROM mor_platform.t_gateway_route WHERE route_id = 900000000000127432;
