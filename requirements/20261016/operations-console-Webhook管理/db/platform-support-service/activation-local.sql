-- Operations Webhook 管理：本机全链路激活（阶段 5，只在本机 mor_platform_e2e 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 末尾的「Client Webhook management permissions」块与「Operations Webhook management console」块并 COMMIT。
-- 激活 10 条路由、2 个菜单/按钮资源、PLATFORM_ADMIN 的 2 个 Webhook 授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：10 / 2 / 2 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @whk_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@whk_act_time
 WHERE route_id BETWEEN 900000000000090401 AND 900000000000090410 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@whk_act_time
 WHERE resource_id BETWEEN 900000000000090301 AND 900000000000090302 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@whk_act_time
 WHERE role_permission_id BETWEEN 900000000000090201 AND 900000000000090202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@whk_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version。
