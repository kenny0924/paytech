-- Operations 维护 Vault 数据类型与手动加解密：mor-test 激活（阶段 6/7，只在 mor-test 的 mor_platform 执行）
-- 字符集：用 mysql --default-character-set=utf8mb4 执行。
-- 前置：已执行 ../db/platform-support-service/registration-dml.sql 并 COMMIT（新行 DISABLED/REVOKED，version=1）。
-- 激活 13 条路由、11 个菜单/按钮资源、PLATFORM_ADMIN 的 6 个授权（v1→v2），并递增 PLATFORM_ADMIN 版本（2026-10-15 执行时 v22→v23）。
-- 本文件不含 COMMIT：核对影响行数 13/11/6/1 后手工 COMMIT，不符则 ROLLBACK。回退见 test-deactivate.sql。
START TRANSACTION;
SET @vlt_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@vlt_act_time
 WHERE route_id BETWEEN 900000000000081001 AND 900000000000081013 AND status='DISABLED' AND version=1;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@vlt_act_time
 WHERE resource_id BETWEEN 900000000000081301 AND 900000000000081311 AND status='DISABLED' AND version=1;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@vlt_act_time
 WHERE role_permission_id BETWEEN 900000000000081201 AND 900000000000081206 AND status='REVOKED' AND version=1;
UPDATE t_role SET version=version+1, update_time=@vlt_act_time WHERE role_id=900000000000000510 AND status='ACTIVE';
