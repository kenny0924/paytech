-- 渠道流水页与详情读取（块 126）：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：块 123 已在 mor-test 激活；已核对块 126 未占用；已执行块 126（registration-dml.sql 的阅读副本）并 COMMIT；reconciliation、operations-console-bff、
-- operations-console-web 已是本需求版本。
-- 激活 3 条只读路由与 1 个菜单资源「渠道流水」；不涉及授权（复用已授予的 FINANCE_RECONCILIATION_READ），递增 PLATFORM_ADMIN
-- 角色版本使会话重新计算导航。
-- 预期影响行数：3 / 1 / 1。执行另行授权。
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
-- 回滚：执行 test-deactivate.sql。
