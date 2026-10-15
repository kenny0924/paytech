-- Operations 通知发送账号维护：mor-test 停用入口（回退用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 4 条路由、2 个按钮置 DISABLED，PLATFORM_ADMIN 授权置 REVOKED，并递增角色版本。预期影响行数：4 / 2 / 1 / 1。
-- 不删除注册数据与账号表新列；已录入的密钥 Token 保留。
START TRANSACTION;
SET @prov_off_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@prov_off_time
 WHERE route_id BETWEEN 900000000000096001 AND 900000000000096004 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@prov_off_time
 WHERE resource_id BETWEEN 900000000000096301 AND 900000000000096302 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@prov_off_time
 WHERE role_permission_id = 900000000000096201 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@prov_off_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
