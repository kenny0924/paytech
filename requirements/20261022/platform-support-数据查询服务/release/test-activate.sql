-- Operations 数据查询（块 131）：mor-test 启用（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：changes/2026-10-22-data-query.sql、db/vault/dml.sql 与 sql/10-registration-block131.sql（234 行）已执行并 COMMIT；
-- platform-support、ops-bff、ops-web 新镜像已 healthy。
-- 启用 18 条路由、3 个菜单与 2 个按钮、PLATFORM_ADMIN 的 4 个数据查询授权，并递增角色版本使会话重新计算授权与导航。
-- 预期影响行数：18 / 5 / 4 / 1。回滚使用同目录 test-deactivate.sql（注册行保留）。
START TRANSACTION;
SET @dq_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@dq_act_time
 WHERE route_id BETWEEN 900000000000131401 AND 900000000000131418 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@dq_act_time
 WHERE resource_id BETWEEN 900000000000131301 AND 900000000000131305 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@dq_act_time
 WHERE role_permission_id BETWEEN 900000000000131201 AND 900000000000131204 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@dq_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
