-- 对账控制台（块 123）：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 38 条路由、10 个资源与 3 条 PLATFORM_ADMIN 授权恢复为休眠，并递增 PLATFORM_ADMIN 版本。
-- 预期影响行数：38 / 10 / 3 / 1。512 行注册数据保留（休眠时对任何版本无影响），删除另行授权。
START TRANSACTION;
SET @recon_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@recon_deact_time
 WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@recon_deact_time
 WHERE resource_id BETWEEN 900000000000123301 AND 900000000000123310 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@recon_deact_time
 WHERE role_permission_id BETWEEN 900000000000123201 AND 900000000000123203 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@recon_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
