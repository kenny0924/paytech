-- 草稿（未执行）：块 134 启用，只在 mor_platform 执行，客户端须 utf8mb4。
-- 前置：registration-block134.sql（157 行）已执行并 COMMIT；platform-support、ops-bff、ops-web 新镜像已 healthy。
-- 启用 12 条路由、文件记录菜单与下载按钮、PLATFORM_ADMIN 的 FILE_RECORD_READ / FILE_RECORD_DOWNLOAD 授权，并递增角色版本
-- 使会话重新计算授权与导航。预期影响行数：12 / 2 / 2 / 1。回滚使用同目录 deactivate-block134.sql（注册行保留）。
START TRANSACTION;
SET @file_record_activation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@file_record_activation_time
 WHERE route_id BETWEEN 900000000000134401 AND 900000000000134412 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@file_record_activation_time
 WHERE resource_id BETWEEN 900000000000134301 AND 900000000000134302 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@file_record_activation_time
 WHERE role_permission_id BETWEEN 900000000000134201 AND 900000000000134202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@file_record_activation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
