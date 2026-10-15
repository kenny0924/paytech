-- 回退 test-activate.sql：同一组 ID 置回 DISABLED/REVOKED（版本只增不减），并递增 PLATFORM_ADMIN 版本使会话重新计算授权。
-- 字符集：用 mysql --default-character-set=utf8mb4 执行。前提：激活后这些行未被其他变更改动（version=2）。
-- 本文件不含 COMMIT：核对影响行数 13/11/6/1 后手工 COMMIT，不符则 ROLLBACK。
START TRANSACTION;
SET @vlt_deact_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@vlt_deact_time
 WHERE route_id BETWEEN 900000000000081001 AND 900000000000081013 AND status='ACTIVE' AND version=2;
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@vlt_deact_time
 WHERE resource_id BETWEEN 900000000000081301 AND 900000000000081311 AND status='ACTIVE' AND version=2;
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@vlt_deact_time
 WHERE role_permission_id BETWEEN 900000000000081201 AND 900000000000081206 AND status='ACTIVE' AND version=2;
UPDATE t_role SET version=version+1, update_time=@vlt_deact_time WHERE role_id=900000000000000510 AND status='ACTIVE';
