-- ledger 会计科目分级维护：本机全链路激活（阶段 5，只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 的财务块 97 / 98 / 99（含 FINANCE_ACCOUNTING_READ / MANAGE 与 PLATFORM_ADMIN 授权）及其本机激活，
-- 并执行「Operations finance chart maintenance and ledger balance query console」块 91（8 条路由、8 个查询参数；不含试算运行路由与按钮）并 COMMIT。
-- 激活 8 条路由（无新增 Console Resource）；两项财务授权已由前序激活置 ACTIVE，不重复。
-- 块 91 给既有路由 900000000000097415 / 900000000000097410 / 900000000000099401 增加了查询参数，这里递增其 version，
-- 使 Gateway Snapshot 重新生成时一定带上新参数（只改 version，不改状态）。
-- 递增角色版本使会话重新计算导航。预期影响行数：8 / 3 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @fin_chart_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@fin_chart_act_time
 WHERE route_id BETWEEN 900000000000091401 AND 900000000000091408 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_gateway_route SET version=version+1, update_time=@fin_chart_act_time
 WHERE route_id IN (900000000000097415, 900000000000097410, 900000000000099401);
SELECT ROW_COUNT() AS extended_routes;
UPDATE t_role SET version=version+1, update_time=@fin_chart_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED，version+1，并再次递增角色 900000000000000510 的 version；
-- 既有路由新增的 3 个查询参数保留（旧 BFF 忽略未声明参数），删除另行授权。
