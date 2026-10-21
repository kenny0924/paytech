-- Operations 币种页（块 125）：mor-test 启用（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：changes/2026-10-20-currency-spec-version.sql 已执行；初始化 DML 末尾「Operations currency spec console」块
-- （块 125：权限 2、授权 2、资源 2、路由 4，共 54 行）已执行并 COMMIT；platform-support、ops-bff、ops-web 新镜像已 healthy。
-- 启用 4 条路由、1 个菜单与 1 个按钮、PLATFORM_ADMIN 的 2 个币种授权，并递增角色版本使会话重新计算授权与导航。
-- 预期影响行数：4 / 2 / 2 / 1。回滚使用同目录 test-deactivate.sql（注册行保留）。
START TRANSACTION;
SET @ccy_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@ccy_act_time
 WHERE route_id BETWEEN 900000000000125401 AND 900000000000125404 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@ccy_act_time
 WHERE resource_id BETWEEN 900000000000125301 AND 900000000000125302 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@ccy_act_time
 WHERE role_permission_id BETWEEN 900000000000125201 AND 900000000000125202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@ccy_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
