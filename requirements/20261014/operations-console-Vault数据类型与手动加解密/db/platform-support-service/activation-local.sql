-- Operations 维护 Vault 数据类型与手动加解密：本机全链路激活（阶段 5，只在本机 mor_platform_e2e 执行）
-- 前置：已执行同目录 registration-dml.sql 并 COMMIT。
-- 激活 13 条路由、11 个菜单/按钮资源、PLATFORM_ADMIN 的 6 个授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：13 / 11 / 6 / 1。mor-test 与其他环境另行授权，不沿用本文件。
START TRANSACTION;
SET @vlt_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@vlt_act_time
 WHERE route_id BETWEEN 900000000000081001 AND 900000000000081013 AND status='DISABLED';
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@vlt_act_time
 WHERE resource_id BETWEEN 900000000000081301 AND 900000000000081311 AND status='DISABLED';
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@vlt_act_time
 WHERE role_permission_id BETWEEN 900000000000081201 AND 900000000000081206 AND status='REVOKED';
UPDATE t_role SET version=version+1, update_time=@vlt_act_time WHERE role_id=900000000000000510;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version。
