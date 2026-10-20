-- 财务控制台三项：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 26 条路由、10 个资源与 FINANCE_ACCOUNTING_APPROVE 授权恢复为休眠，并递增 PLATFORM_ADMIN 版本。
-- 预期影响行数：26 / 10 / 1 / 1。FINANCE_ACCOUNTING_READ / MANAGE 授权属于 20261018 需求，不在此停用；
-- 328 行注册数据保留（休眠时对任何版本无影响），删除另行授权。
START TRANSACTION;
SET @fin3_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@fin3_deact_time
 WHERE (route_id BETWEEN 900000000000120401 AND 900000000000120410
     OR route_id BETWEEN 900000000000121401 AND 900000000000121408
     OR route_id BETWEEN 900000000000122401 AND 900000000000122408) AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@fin3_deact_time
 WHERE (resource_id BETWEEN 900000000000120301 AND 900000000000120303
     OR resource_id BETWEEN 900000000000121301 AND 900000000000121304
     OR resource_id BETWEEN 900000000000122301 AND 900000000000122303) AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@fin3_deact_time
 WHERE role_permission_id = 900000000000120201 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@fin3_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
