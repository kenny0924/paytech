-- account 明细账查询：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 财务块 97 / 98 并已激活（20261018 需求 release/test-activate.sql，FINANCE_ACCOUNTING_READ 授权 ACTIVE）；
-- 已执行末尾「Operations finance subledger query console」块 99（1 个菜单、3 条路由）并 COMMIT；
-- account、ledger、billing、Platform、Operations BFF、Operations Web 容器已换成候选镜像并 healthy。
-- 激活 3 条路由、1 个菜单资源；角色授权已存在不重复；递增 PLATFORM_ADMIN 版本使会话重新计算导航。
-- 预期影响行数：3 / 1 / 1。
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
