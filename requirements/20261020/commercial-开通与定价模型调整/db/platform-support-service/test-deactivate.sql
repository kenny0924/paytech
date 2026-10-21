-- commercial 开通与定价模型调整（一期）：mor-test 停用（恢复用，只在 mor-test mor_platform 执行，platform_app，客户端须 utf8mb4）
-- 把 test-activate.sql 激活的 17 条路由、5 个资源与 3 个授权恢复为休眠，并递增 PLATFORM_ADMIN 版本。预期影响行数：17 / 5 / 3 / 1。
-- 不动首切片块 95、第二切片块 93 的路由、资源与授权（各自停用见其需求目录 release/test-deactivate.sql）。
-- 块 124 的 227 行注册数据保留（休眠时对任何版本的 Gateway / BFF 无影响）；删除另行授权（按 registration-dml.sql 头注释的 ID 段）。
-- 回退本期 BFF / Web 镜像前先执行本文件，避免新菜单指向旧 BFF 不存在的端点。

-- 0. 执行前核对（期望：ACTIVE 路由 17、ACTIVE 资源 5、ACTIVE 授权 3）
SELECT
 (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000124401 AND 900000000000124417 AND status='ACTIVE') AS routes_exp17,
 (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000124301 AND 900000000000124305 AND status='ACTIVE') AS resources_exp5,
 (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000124201 AND 900000000000124203 AND status='ACTIVE') AS grants_exp3,
 (SELECT version FROM t_role WHERE role_id=900000000000000510) AS admin_version;

START TRANSACTION;
SET @commercial_template_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@commercial_template_deact_time
 WHERE route_id BETWEEN 900000000000124401 AND 900000000000124417 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@commercial_template_deact_time
 WHERE resource_id BETWEEN 900000000000124301 AND 900000000000124305 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@commercial_template_deact_time
 WHERE role_permission_id BETWEEN 900000000000124201 AND 900000000000124203 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@commercial_template_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
