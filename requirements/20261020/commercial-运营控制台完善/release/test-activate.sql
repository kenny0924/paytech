-- commercial 运营控制台完善（第二切片 I1 + I2）：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，platform_app，客户端须 utf8mb4）
-- 前置：已执行 test-dml.sql（I1 续写块 95 与 I2 块 93）并 COMMIT；commercial-service、operations-console-bff、operations-console-web
-- 已换成候选镜像并 healthy（旧 BFF 没有这些端点，先开路由会得到 404）。
-- 由 activation-local.sql 的 I1、I2 两段合并为一个事务：激活 I1 的 3 条路由（工作台、费率预览、价格试算）与 5 个菜单
-- （工作台、费率预览、价格试算、产品目录、变更历史），I2 的 8 条只读 GET 路由；不新增权限与授权（复用 COMMERCIAL_CONTRACT_READ /
-- COMMERCIAL_PRICING_READ，首切片 test-activate.sql 已授予 PLATFORM_ADMIN）；只递增一次 PLATFORM_ADMIN 角色版本使会话重新计算菜单。
-- 预期影响行数：3 / 8 / 5 / 1；PLATFORM_ADMIN 版本 33 → 34（2026-10-20 只读核对值，发布时以第 0 段实测为准）。
-- 0. 执行前核对（期望：DISABLED 路由 3 + 8、DISABLED 资源 5）
SELECT
 (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000095425 AND 900000000000095427 AND status='DISABLED') AS i1_routes_exp3,
 (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000093401 AND 900000000000093408 AND status='DISABLED') AS i2_routes_exp8,
 (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000095309 AND 900000000000095313 AND status='DISABLED') AS i1_resources_exp5,
 (SELECT version FROM t_role WHERE role_id=900000000000000510) AS admin_version;
START TRANSACTION;
SET @commercial_ops2_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@commercial_ops2_act_time
 WHERE route_id BETWEEN 900000000000095425 AND 900000000000095427 AND status='DISABLED';
SELECT ROW_COUNT() AS i1_routes;
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@commercial_ops2_act_time
 WHERE route_id BETWEEN 900000000000093401 AND 900000000000093408 AND status='DISABLED';
SELECT ROW_COUNT() AS i2_routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@commercial_ops2_act_time
 WHERE resource_id BETWEEN 900000000000095309 AND 900000000000095313 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@commercial_ops2_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
