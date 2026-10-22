-- 回滚第一步：停用两个入口（mor_platform）。注册行与表保留。期望：块 133 路由 1；块 132 路由 9 / 菜单 2 / 授权 2；角色版本 +1。
-- 语句为 30、31 的反向（块 132 / 133 文件头 Deactivation / Rollback 说明）。不含 COMMIT：apply_sql.sh <备份目录> deactivate-dry / deactivate。
-- 之后等待 Gateway 快照刷新（≤60 秒）再回滚镜像。
START TRANSACTION;
SET @rb_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@rb_time
 WHERE DATABASE()='mor_platform' AND route_id = 900000000000133401 AND status='ACTIVE';
SET @rb_cb_routes = ROW_COUNT();
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@rb_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000132401 AND 900000000000132409 AND status='ACTIVE';
SET @rb_bin_routes = ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@rb_time
 WHERE DATABASE()='mor_platform' AND resource_id IN (900000000000132301,900000000000132302) AND status='ACTIVE';
SET @rb_bin_resources = ROW_COUNT();
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@rb_time
 WHERE DATABASE()='mor_platform' AND role_permission_id IN (900000000000132201,900000000000132202) AND status='ACTIVE';
SET @rb_bin_grants = ROW_COUNT();
UPDATE t_role SET version=version+1, update_time=@rb_time WHERE DATABASE()='mor_platform' AND role_id=900000000000000510;
SET @rb_role = ROW_COUNT();
SELECT @rb_cb_routes AS cb_routes, @rb_bin_routes AS bin_routes, @rb_bin_resources AS bin_resources, @rb_bin_grants AS bin_grants, @rb_role AS role;
-- 只激活了其中一个块时，另一块的数为 0 属正常；这里只拦截“超出期望”的情况。
SELECT IF(@rb_cb_routes<=1 AND @rb_bin_routes IN (0,9) AND @rb_bin_resources IN (0,2) AND @rb_bin_grants IN (0,2) AND @rb_role=1,
          'counts ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
