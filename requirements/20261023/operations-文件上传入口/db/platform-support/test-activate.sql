-- 草稿（未执行）：块 136 启用，只在 mor_platform 执行，客户端须 utf8mb4。
-- 前置：registration-block136.sql（64 行）已执行并 COMMIT；platform-support、ops-bff、ops-web 新镜像已 healthy；
-- (operations-console-bff, PUBLIC) 写入规则已按 technique.md 第 9 节登记。
-- 启用 5 条路由、文件上传菜单与上传按钮、PLATFORM_ADMIN 的 FILE_UPLOAD_READ / FILE_UPLOAD_CREATE 授权，并递增角色版本
-- 使会话重新计算授权与导航。预期影响行数：5 / 2 / 2 / 1。回滚使用同目录 test-deactivate.sql（注册行保留）。
START TRANSACTION;
SET @file_upload_activation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@file_upload_activation_time
 WHERE route_id BETWEEN 900000000000136401 AND 900000000000136405 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@file_upload_activation_time
 WHERE resource_id BETWEEN 900000000000136301 AND 900000000000136302 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@file_upload_activation_time
 WHERE role_permission_id BETWEEN 900000000000136201 AND 900000000000136202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@file_upload_activation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
