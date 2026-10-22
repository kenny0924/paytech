-- Operations 存储管理页（块 130）：mor-test 停用（test-activate.sql 的回滚，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 把 12 条路由、2 个资源置 DISABLED，2 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 12 / 2 / 2 / 1。注册行保留；
-- 如需彻底移除块 130，再按 sql/10-registration-block130.sql 块头的 ID 范围删除（子表先删）。
-- 只关闭运营入口；已登记的存储实例、桶与路由不受影响，入驻上传与审核下载照常。
START TRANSACTION;
SET @file_storage_deactivation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@file_storage_deactivation_time
 WHERE route_id BETWEEN 900000000000130401 AND 900000000000130412 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@file_storage_deactivation_time
 WHERE resource_id BETWEEN 900000000000130301 AND 900000000000130302 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@file_storage_deactivation_time
 WHERE role_permission_id BETWEEN 900000000000130201 AND 900000000000130202 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@file_storage_deactivation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
