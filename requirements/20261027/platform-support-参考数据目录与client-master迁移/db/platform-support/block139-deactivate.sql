-- Platform 块 139 停用（启用的逆操作），只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block139.py 生成（block139-spec.json）。
-- 注册行保留。
-- 预期影响：路由 5、资源 1、授权 1、角色 1。
START TRANSACTION;
SET @b139_toggle_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@b139_toggle_time
 WHERE route_id BETWEEN 900000000000139401 AND 900000000000139405 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@b139_toggle_time
 WHERE resource_id BETWEEN 900000000000139301 AND 900000000000139301 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@b139_toggle_time
 WHERE role_permission_id BETWEEN 900000000000139201 AND 900000000000139201 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@b139_toggle_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
