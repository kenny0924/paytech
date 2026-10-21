-- 渠道流水页与详情读取（块 126）：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 3 条路由与 1 个资源恢复为休眠，并递增 PLATFORM_ADMIN 版本。
-- 预期影响行数：3 / 1 / 1。38 行注册数据保留（休眠时无影响），删除另行授权。
START TRANSACTION;
SET @recon_lines_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@recon_lines_deact_time
 WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@recon_lines_deact_time
 WHERE resource_id = 900000000000126301 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@recon_lines_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
