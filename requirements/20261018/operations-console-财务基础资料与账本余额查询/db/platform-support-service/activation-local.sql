-- Operations 财务基础资料、账本与余额查询：本机全链路激活（阶段 5，只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 末尾的「Operations finance accounting」块 97（权限、授权、菜单、16 条路由）与块 98（2 条余额路由）并 COMMIT。
-- 激活 18 条路由、5 个菜单/按钮资源、PLATFORM_ADMIN 的 2 个财务授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：18 / 5 / 2 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @fin_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@fin_act_time
 WHERE route_id BETWEEN 900000000000097401 AND 900000000000098402 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@fin_act_time
 WHERE resource_id BETWEEN 900000000000097301 AND 900000000000097305 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@fin_act_time
 WHERE role_permission_id BETWEEN 900000000000097201 AND 900000000000097202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@fin_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version。
