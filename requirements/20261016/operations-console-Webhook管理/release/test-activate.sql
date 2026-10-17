-- Operations Webhook 管理：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 末尾「Operations Webhook management console」块（128 行）并 COMMIT；
-- Webhook 权限与 PLATFORM_ADMIN 授权已在 2026-10-16 Webhook 投递发布中启用（9000000000000902xx 为 ACTIVE）。
-- 激活 10 条路由、2 个菜单/按钮资源，并递增 PLATFORM_ADMIN 版本使会话重新计算导航。预期影响行数：10 / 2 / 1。
START TRANSACTION;
SET @whk_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@whk_act_time
 WHERE route_id BETWEEN 900000000000090401 AND 900000000000090410 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@whk_act_time
 WHERE resource_id BETWEEN 900000000000090301 AND 900000000000090302 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@whk_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
