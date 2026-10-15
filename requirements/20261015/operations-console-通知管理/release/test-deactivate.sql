-- Operations 通知管理：mor-test 停用入口（回退用，只在 test mor_platform 执行，客户端须 utf8mb4）
-- 停用 12 条路由、6 个菜单/按钮、撤销 PLATFORM_ADMIN 的 2 个授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：12 / 6 / 2 / 1；先核对行数再 COMMIT。
START TRANSACTION;
SET @ntf_test_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@ntf_test_time
 WHERE route_id BETWEEN 900000000000086001 AND 900000000000086012 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@ntf_test_time
 WHERE resource_id BETWEEN 900000000000086301 AND 900000000000086306 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@ntf_test_time
 WHERE role_permission_id BETWEEN 900000000000086201 AND 900000000000086202 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@ntf_test_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
