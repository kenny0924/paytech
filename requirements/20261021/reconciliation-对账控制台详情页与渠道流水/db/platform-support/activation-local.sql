-- 渠道流水页与详情读取（块 126）：本机全链路激活（阶段 5，只在本机 Platform 库执行，客户端须 utf8mb4）
-- 前置：块 123 已注册并激活；已执行块 126（registration-dml.sql 的阅读副本）并 COMMIT；reconciliation、operations-console-bff、
-- operations-console-web 已是本需求版本。
-- 激活 3 条只读路由与 1 个菜单资源「渠道流水」；不涉及授权（复用已授予的 FINANCE_RECONCILIATION_READ），递增 PLATFORM_ADMIN
-- 角色版本使会话重新计算导航。
-- 预期影响行数：3 / 1 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @recon_lines_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@recon_lines_act_time
 WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@recon_lines_act_time
 WHERE resource_id = 900000000000126301 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@recon_lines_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：执行与 test-deactivate.sql 相同的语句（同一组 ID 置 DISABLED，version+1，再次递增角色版本）。
