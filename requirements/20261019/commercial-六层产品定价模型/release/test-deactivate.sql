-- Operations 商务控制台（commercial 六层产品定价模型）：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 24 条路由、8 个资源、5 个授权恢复为休眠，并递增 PLATFORM_ADMIN 版本。预期影响行数：24 / 8 / 5 / 1。
-- 块 95 的 319 行注册数据保留（休眠时对任何版本的 Platform / Gateway / BFF 无影响）；删除另行授权（按块头注释的 ID 段子表先删）。
START TRANSACTION;
SET @commercial_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@commercial_deact_time
 WHERE route_id BETWEEN 900000000000095401 AND 900000000000095424 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@commercial_deact_time
 WHERE resource_id BETWEEN 900000000000095301 AND 900000000000095308 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@commercial_deact_time
 WHERE role_permission_id BETWEEN 900000000000095201 AND 900000000000095205 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@commercial_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
