-- 工作流（块 127）：mor-test 停用（回滚第一步，只在 mor-test mor_platform 执行，客户端 utf8mb4）。注册行保留。
-- 期望影响行数：32 / 10 / 7 / 1。用法同 test-activate.sql：apply_sql.sh <B> deactivate-dry（看数，断开回滚）/ deactivate（追加 COMMIT）。
-- 镜像回滚到旧 Platform 之前必须先执行本文件：旧 Platform / ops-web 没有 OPERATIONS_WORKFLOW_* 组件与工作流接口。
START TRANSACTION;
SET @wf_deactivation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@wf_deactivation_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000127401 AND 900000000000127432 AND status='ACTIVE';
SET @wf_routes = ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@wf_deactivation_time
 WHERE DATABASE()='mor_platform' AND resource_id BETWEEN 900000000000127301 AND 900000000000127310 AND status='ACTIVE';
SET @wf_resources = ROW_COUNT();
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@wf_deactivation_time
 WHERE DATABASE()='mor_platform' AND role_permission_id BETWEEN 900000000000127201 AND 900000000000127207 AND status='ACTIVE';
SET @wf_grants = ROW_COUNT();
UPDATE t_role SET version=version+1, update_time=@wf_deactivation_time
 WHERE DATABASE()='mor_platform' AND role_id=900000000000000510;
SET @wf_role = ROW_COUNT();
SELECT @wf_routes AS routes_32, @wf_resources AS resources_10, @wf_grants AS grants_7, @wf_role AS role_1;
SELECT IF(@wf_routes=32 AND @wf_resources=10 AND @wf_grants=7 AND @wf_role=1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
