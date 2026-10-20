-- 财务控制台三项：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 财务块 97 / 98 / 99 / 91 并已激活（20261018、20261019 各需求 release/test-activate.sql）；
-- 已执行本需求块 120、121、122 并 COMMIT；ledger、billing、Platform、Operations BFF、Operations Web 容器已换成候选镜像并 healthy。
-- 激活 26 条路由、10 个菜单 / 按钮资源与 FINANCE_ACCOUNTING_APPROVE 授权；FINANCE_ACCOUNTING_READ / MANAGE 授权已存在不重复；
-- 递增 PLATFORM_ADMIN 版本使会话重新计算导航。预期影响行数：26 / 10 / 1 / 1。
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
