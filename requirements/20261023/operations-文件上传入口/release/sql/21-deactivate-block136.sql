-- 草稿（未执行）：块 136 停用（test-activate.sql 的回滚），只在 mor_platform 执行，客户端须 utf8mb4。
-- 把 5 条路由、2 个资源置 DISABLED，2 个授权置 REVOKED，并递增 PLATFORM_ADMIN 版本。预期 5 / 2 / 2 / 1。注册行保留；
-- 如需彻底移除块 136，再按 registration-block136.sql 块头的 ID 范围删除（子表先删）。
-- 只关闭文件上传页的运营入口；已上传的公开文件、写入规则、卡 BIN 上传与文件记录页不受影响。
START TRANSACTION;
SET @file_upload_deactivation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@file_upload_deactivation_time
 WHERE route_id BETWEEN 900000000000136401 AND 900000000000136405 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@file_upload_deactivation_time
 WHERE resource_id BETWEEN 900000000000136301 AND 900000000000136302 AND status='ACTIVE';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@file_upload_deactivation_time
 WHERE role_permission_id BETWEEN 900000000000136201 AND 900000000000136202 AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@file_upload_deactivation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
