-- 工作流附件内嵌预览跟进：本机 e2e 增量（只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4）。
-- 内容：#28 附件下载授权路由 900000000000127432 允许可选 Query disposition（INLINE | ATTACHMENT）；块 127 正式文件同步追加同一行。
-- 前提：已执行 40-workflow-file-routes.sql；库中尚无 route_query_param_id 900000000000127030。
-- 执行前备份；预期影响行数 1 / 1，不符时 ROLLBACK。语句带 DATABASE() 保护，库名不是 mor_platform_v2e2e 时影响 0 行。
-- 路由版本 +1 使 Gateway 快照重新发布。
-- 回滚：DELETE FROM t_gateway_route_query_param WHERE route_query_param_id = 900000000000127030;
--       UPDATE t_gateway_route SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE route_id = 900000000000127432;
START TRANSACTION;

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT * FROM (VALUES ROW(900000000000127030, 900000000000127432, 'disposition')) AS v
 WHERE DATABASE() = 'mor_platform_v2e2e';
SELECT ROW_COUNT() AS query_param_1;

UPDATE t_gateway_route SET version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE DATABASE() = 'mor_platform_v2e2e' AND route_id = 900000000000127432;
SELECT ROW_COUNT() AS route_1;
-- 核对两个数后 COMMIT，否则 ROLLBACK。
