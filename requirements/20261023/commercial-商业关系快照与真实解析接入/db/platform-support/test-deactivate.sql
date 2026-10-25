-- 草稿（未执行）：块 137 停用（test-activate.sql 的回滚），只在 mor_platform 执行，客户端须 utf8mb4。
-- 把 11 条路由、6 个资源置 DISABLED，6 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 11 / 6 / 6 / 1。注册行保留；
-- 如需彻底移除块 137，再按 registration-block137.sql 块头的 ID 范围删除（子表先删）。
-- 只关闭主体资格、经营主体、Local Partner 三个运营入口；已登记的资格、收单资质与 Partner 角色数据及结账解析不受影响。
START TRANSACTION;
SET @relsnap_deactivation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@relsnap_deactivation_time
 WHERE route_id BETWEEN 900000000000137401 AND 900000000000137411 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@relsnap_deactivation_time
 WHERE resource_id BETWEEN 900000000000137301 AND 900000000000137306 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@relsnap_deactivation_time
 WHERE role_permission_id BETWEEN 900000000000137201 AND 900000000000137206 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@relsnap_deactivation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
