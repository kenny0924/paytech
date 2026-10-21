-- Operations 币种页（块 125）：mor-test 停用（test-activate.sql 的回滚，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 4 条路由、2 个资源置 DISABLED，2 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 4 / 2 / 2 / 1。注册行保留；
-- 如需彻底移除块 125，再按初始化 DML 块头的 ID 范围删除（子表先删）。
START TRANSACTION;
SET @ccy_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@ccy_deact_time
 WHERE route_id BETWEEN 900000000000125401 AND 900000000000125404 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@ccy_deact_time
 WHERE resource_id BETWEEN 900000000000125301 AND 900000000000125302 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@ccy_deact_time
 WHERE role_permission_id BETWEEN 900000000000125201 AND 900000000000125202 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@ccy_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
