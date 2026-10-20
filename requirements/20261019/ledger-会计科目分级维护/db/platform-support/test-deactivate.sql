-- ledger 会计科目分级维护：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 8 条路由恢复为休眠，并递增 PLATFORM_ADMIN 版本。预期影响行数：8 / 1。
-- FINANCE_ACCOUNTING_READ / MANAGE 授权属于 20261018 需求，不在此停用；既有路由 900000000000097415 / 900000000000097410 /
-- 900000000000099401 新增的 3 个查询参数保留（旧 BFF 忽略未声明参数）；96 行注册数据保留（休眠时对任何版本无影响），删除另行授权。
START TRANSACTION;
SET @fin_chart_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@fin_chart_deact_time
 WHERE route_id BETWEEN 900000000000091401 AND 900000000000091408 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_role SET version=version+1, update_time=@fin_chart_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
