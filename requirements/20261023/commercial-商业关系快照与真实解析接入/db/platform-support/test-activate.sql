-- 草稿（未执行）：块 137 启用，只在 mor_platform 执行，客户端须 utf8mb4。
-- 前置：registration-block137.sql（150 行）已执行并 COMMIT；client-master、compliance、operations-console-bff、
-- operations-console-web 新镜像已 healthy；Gateway 快照已按休眠 / 激活两态离线编译通过。
-- 启用 11 条路由、3 个菜单与 3 个按钮、PLATFORM_ADMIN 的 6 个授权，并递增角色版本使会话重新计算授权与导航。
-- 预期影响行数：11 / 6 / 6 / 1。回滚使用同目录 test-deactivate.sql（注册行保留）。
START TRANSACTION;
SET @relsnap_activation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@relsnap_activation_time
 WHERE route_id BETWEEN 900000000000137401 AND 900000000000137411 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@relsnap_activation_time
 WHERE resource_id BETWEEN 900000000000137301 AND 900000000000137306 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@relsnap_activation_time
 WHERE role_permission_id BETWEEN 900000000000137201 AND 900000000000137206 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@relsnap_activation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
