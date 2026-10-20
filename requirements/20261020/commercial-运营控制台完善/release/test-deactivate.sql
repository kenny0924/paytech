-- commercial 运营控制台完善（第二切片 I1 + I2）：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，platform_app，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 3 + 8 条路由与 5 个菜单恢复为休眠，并递增 PLATFORM_ADMIN 版本。预期影响行数：3 / 8 / 5 / 1。
-- 不动首切片块 95 的 24 条路由、8 个资源、5 个授权（首切片停用见 requirements/20261019/commercial-六层产品定价模型/release/test-deactivate.sql）。
-- test-dml.sql 的 140 行注册数据保留（休眠时对任何版本的 Gateway / BFF 无影响）；删除另行授权。
-- 菜单排序调整（价目 20、定价画像 30）不回退：两个菜单仍 ACTIVE，只是顺序互换；如需恢复见 test-dml.sql 头注释。
START TRANSACTION;
SET @commercial_ops2_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@commercial_ops2_deact_time
 WHERE route_id BETWEEN 900000000000095425 AND 900000000000095427 AND status='ACTIVE';
SELECT ROW_COUNT() AS i1_routes;
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@commercial_ops2_deact_time
 WHERE route_id BETWEEN 900000000000093401 AND 900000000000093408 AND status='ACTIVE';
SELECT ROW_COUNT() AS i2_routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@commercial_ops2_deact_time
 WHERE resource_id BETWEEN 900000000000095309 AND 900000000000095313 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role SET version=version+1, update_time=@commercial_ops2_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
