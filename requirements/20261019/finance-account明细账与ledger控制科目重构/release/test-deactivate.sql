-- account 明细账查询：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 3 条路由、1 个菜单资源恢复为休眠，并递增 PLATFORM_ADMIN 版本。预期影响行数：3 / 1 / 1。
-- FINANCE_ACCOUNTING_READ 授权属于 20261018 需求，不在此停用；39 行注册数据保留（休眠时对任何版本无影响），删除另行授权。
START TRANSACTION;
SET @fin_subledger_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@fin_subledger_deact_time
 WHERE route_id BETWEEN 900000000000099401 AND 900000000000099403 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@fin_subledger_deact_time
 WHERE resource_id = 900000000000099301 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@fin_subledger_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
