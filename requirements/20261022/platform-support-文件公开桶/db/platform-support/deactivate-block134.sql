-- 草稿（未执行）：块 134 停用（activate-block134.sql 的回滚），只在 mor_platform 执行，客户端须 utf8mb4。
-- 把 12 条路由、2 个资源置 DISABLED，2 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 12 / 2 / 2 / 1。注册行保留；
-- 如需彻底移除块 134，再按 registration-block134.sql 块头的 ID 范围删除（子表先删）。
-- 只关闭检查策略、桶对外地址编辑与文件记录的运营入口；块 130 的存储管理页与业务上传、下载不受影响。
START TRANSACTION;
SET @file_record_deactivation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@file_record_deactivation_time
 WHERE route_id BETWEEN 900000000000134401 AND 900000000000134412 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@file_record_deactivation_time
 WHERE resource_id BETWEEN 900000000000134301 AND 900000000000134302 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@file_record_deactivation_time
 WHERE role_permission_id BETWEEN 900000000000134201 AND 900000000000134202 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@file_record_deactivation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
