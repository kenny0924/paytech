-- Operations 存储管理页（块 130）：mor-test 启用（阶段 7 第 7 步，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：sql/01-platform-file-storage.sql 已执行；sql/10-registration-block130.sql（块 130：152 行）已执行并 COMMIT；
-- platform-support、ops-bff、ops-web 新镜像已 healthy。
-- 启用 12 条路由、1 个菜单与 1 个按钮、PLATFORM_ADMIN 的 2 个存储管理授权，并递增角色版本使会话重新计算授权与导航。
-- 语句与块 130 块头注释中的激活语句相同。预期影响行数：12 / 2 / 2 / 1。回滚使用同目录 test-deactivate.sql（注册行保留）。
START TRANSACTION;
SET @file_storage_activation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@file_storage_activation_time
 WHERE route_id BETWEEN 900000000000130401 AND 900000000000130412 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@file_storage_activation_time
 WHERE resource_id BETWEEN 900000000000130301 AND 900000000000130302 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@file_storage_activation_time
 WHERE role_permission_id BETWEEN 900000000000130201 AND 900000000000130202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@file_storage_activation_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
