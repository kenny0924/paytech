-- account 明细账查询：本机全链路激活（阶段 5，只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 的财务块 97 / 98（含 FINANCE_ACCOUNTING_READ 与 PLATFORM_ADMIN 授权）及其本机激活，
-- 并执行末尾「Operations finance subledger query console」块 99（1 个菜单、3 条路由）并 COMMIT。
-- 激活 3 条路由、1 个菜单资源；FINANCE_ACCOUNTING_READ 授权已由前序激活置 ACTIVE，不重复。
-- 递增角色版本使会话重新计算导航。预期影响行数：3 / 1 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @fin_subledger_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@fin_subledger_act_time
 WHERE route_id BETWEEN 900000000000099401 AND 900000000000099403 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@fin_subledger_act_time
 WHERE resource_id = 900000000000099301 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@fin_subledger_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED，version+1，并再次递增角色 900000000000000510 的 version。
