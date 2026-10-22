-- 工作流附件内嵌预览跟进 · mor-test 回滚：删除 query_param 900000000000127030，路由 900000000000127432 version 再 +1。期望 1 / 1。
-- 不含 COMMIT：scripts/apply_sql.sh <B> rollback-dry / rollback（后者追加 COMMIT）。
-- 何时执行：镜像已按 compose-rollback.yaml 回到 7cb30402 / a5b9a17c 之后（旧 Platform 忽略多余 Query，保留此行也无害；
-- 删除后 Gateway 会拒绝带 disposition 的请求，所以先确认 ops-web 不发 disposition）。
START TRANSACTION;
SET @wfi_time = UTC_TIMESTAMP(6);
DELETE FROM t_gateway_route_query_param
 WHERE DATABASE() = 'mor_platform' AND route_query_param_id = 900000000000127030
   AND route_id = 900000000000127432 AND parameter_name = 'disposition';
SET @wfi_param = ROW_COUNT();
UPDATE t_gateway_route SET version = version + 1, update_time = @wfi_time
 WHERE DATABASE() = 'mor_platform' AND route_id = 900000000000127432;
SET @wfi_route = ROW_COUNT();
SELECT @wfi_param AS query_param_1, @wfi_route AS route_1;
SELECT IF(@wfi_param = 1 AND @wfi_route = 1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
