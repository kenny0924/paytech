-- Operations 渠道控制台（块 92）：mor-test 启用（阶段 7 第 6 步，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：registration-dml.sql（块 92：权限 7、授权 7、资源 9、路由 31）已在 mor_platform 执行并 COMMIT；四个容器已 healthy。
-- 启用 31 条路由、9 个菜单/按钮资源、PLATFORM_ADMIN 的 7 个渠道授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：31 / 9 / 7 / 1。PLATFORM_ADMIN 版本 35 → 36（以执行时实测为准）。
START TRANSACTION;
SET @chn_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@chn_act_time
 WHERE route_id BETWEEN 900000000000092401 AND 900000000000092431 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@chn_act_time
 WHERE resource_id BETWEEN 900000000000092301 AND 900000000000092309 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@chn_act_time
 WHERE role_permission_id BETWEEN 900000000000092201 AND 900000000000092207 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@chn_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
