-- 财务控制台三项（人工调整与计费事实 / 期间关账与试算 / 记账规则包）：本机全链路激活（阶段 5，只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 的财务块 97 / 98 / 99 / 91 及其本机激活（FINANCE_ACCOUNTING_READ / MANAGE 的 PLATFORM_ADMIN 授权已 ACTIVE），
-- 并执行本需求块 120、121、122（registration-dml.sql 的阅读副本）并 COMMIT。
-- 激活 26 条路由（块 120：10，块 121：8，块 122：8）、10 个菜单 / 按钮资源（含账本查询页的「发起试算」按钮 101304）、
-- PLATFORM_ADMIN 的 FINANCE_ACCOUNTING_APPROVE 授权（100201），并递增角色版本使会话重新计算导航与授权。
-- 预期影响行数：26 / 10 / 1 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @fin3_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@fin3_act_time
 WHERE (route_id BETWEEN 900000000000120401 AND 900000000000120410
     OR route_id BETWEEN 900000000000121401 AND 900000000000121408
     OR route_id BETWEEN 900000000000122401 AND 900000000000122408) AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@fin3_act_time
 WHERE (resource_id BETWEEN 900000000000120301 AND 900000000000120303
     OR resource_id BETWEEN 900000000000121301 AND 900000000000121304
     OR resource_id BETWEEN 900000000000122301 AND 900000000000122303) AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@fin3_act_time
 WHERE role_permission_id = 900000000000120201 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@fin3_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version（见 test-deactivate.sql 的语句）。
