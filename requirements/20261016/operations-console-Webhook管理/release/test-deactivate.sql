-- Operations Webhook 管理：mor-test 停用入口（回退用）。只把本需求的 10 条路由与 2 个菜单/按钮改回 DISABLED，
-- 并递增 PLATFORM_ADMIN 版本；Webhook 权限、授权与投递功能不受影响。预期影响行数：10 / 2 / 1。
START TRANSACTION;
SET @whk_off_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@whk_off_time
 WHERE route_id BETWEEN 900000000000090401 AND 900000000000090410 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@whk_off_time
 WHERE resource_id BETWEEN 900000000000090301 AND 900000000000090302 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@whk_off_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
