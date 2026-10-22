-- Operations 数据查询（块 131）：mor-test 停用（test-activate.sql 的回滚，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 18 条路由、5 个资源置 DISABLED，4 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 18 / 5 / 4 / 1。注册行保留；
-- 如需彻底移除块 131，执行 sql/rollback-unregister.sql。
START TRANSACTION;
SET @dq_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@dq_deact_time
 WHERE route_id BETWEEN 900000000000131401 AND 900000000000131418 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@dq_deact_time
 WHERE resource_id BETWEEN 900000000000131301 AND 900000000000131305 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@dq_deact_time
 WHERE role_permission_id BETWEEN 900000000000131201 AND 900000000000131204 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@dq_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
