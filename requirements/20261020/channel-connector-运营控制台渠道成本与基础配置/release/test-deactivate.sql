-- Operations 渠道控制台（块 92）：mor-test 停用（回滚用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 31 条路由、9 个资源置 DISABLED，7 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 31 / 9 / 7 / 1。注册行保留。
START TRANSACTION;
SET @chn_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@chn_deact_time
 WHERE route_id BETWEEN 900000000000092401 AND 900000000000092431 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@chn_deact_time
 WHERE resource_id BETWEEN 900000000000092301 AND 900000000000092309 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@chn_deact_time
 WHERE role_permission_id BETWEEN 900000000000092201 AND 900000000000092207 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@chn_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
