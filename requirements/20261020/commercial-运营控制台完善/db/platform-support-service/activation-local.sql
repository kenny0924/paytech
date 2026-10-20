-- Operations 商务控制台第二切片 I1（工作台 / 费率预览 / 价格试算 / 产品目录 / 变更历史）：本机全链路激活，只写不执行
-- 只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4；mor-test 与其他环境另行授权，不沿用本文件。
-- 前置：已执行首切片块 95（及其 activation-local.sql）与 registration-dml.sql 的 I1 块并 COMMIT；operations-console-bff 已含 I1 端点。
-- 激活 3 条路由、5 个菜单资源；不新增权限与授权（复用 COMMERCIAL_CONTRACT_READ / COMMERCIAL_PRICING_READ），
-- 递增 PLATFORM_ADMIN 角色版本使会话重新计算菜单。
-- 预期影响行数：3 / 5 / 1。
START TRANSACTION;
SET @commercial_i1_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@commercial_i1_act_time
 WHERE route_id BETWEEN 900000000000095425 AND 900000000000095427 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@commercial_i1_act_time
 WHERE resource_id BETWEEN 900000000000095309 AND 900000000000095313 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@commercial_i1_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED，version+1，并再次递增角色 900000000000000510 的 version。
