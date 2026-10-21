-- 对账控制台（块 123）：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：财务块 97 已在 mor-test 激活；已执行本需求块 123 并 COMMIT（执行前已核对块 123 未占用）；
-- reconciliation、billing、operations-console-bff（含 OPERATIONS_RECONCILIATION_BASE_URL）、operations-console-web 已换成候选镜像并 healthy；Vault 策略 DML 已执行。
-- 激活 38 条路由、10 个菜单 / 按钮资源（对账分组 + 7 页 + 对账操作 / 对账审批 2 个按钮）、PLATFORM_ADMIN 的
-- FINANCE_RECONCILIATION_READ / _OPERATE / _APPROVE 三条授权，并递增角色版本使会话重新计算导航与授权。
-- 预期影响行数：38 / 10 / 3 / 1。执行另行授权。
START TRANSACTION;
SET @recon_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@recon_act_time
 WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@recon_act_time
 WHERE resource_id BETWEEN 900000000000123301 AND 900000000000123310 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@recon_act_time
 WHERE role_permission_id BETWEEN 900000000000123201 AND 900000000000123203 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@recon_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：执行 test-deactivate.sql。
