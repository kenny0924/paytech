-- Operations 接入支付风控配置与执行发布：mor-test 停用入口（恢复用，仅在阶段 7 触发恢复条件时经授权执行）
-- 目标库：mor-test 的 mor_platform；用 mysql --default-character-set=utf8mb4 执行。
-- 把 test-activate.sql 激活的 11 条路由、5 个资源置 DISABLED，4 个授权置 REVOKED，并递增 PLATFORM_ADMIN 角色版本。
-- 注册行保留（不删除）；以后重新启用直接再执行 test-activate.sql。
-- 预期影响行数：11 / 5 / 4 / 1；在同一连接中核对后 COMMIT，任何不符则 ROLLBACK。
START TRANSACTION;
SET @prc_deact_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@prc_deact_time
 WHERE route_id BETWEEN 900000000000101001 AND 900000000000101011 AND status='ACTIVE';
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@prc_deact_time
 WHERE resource_id BETWEEN 900000000000101301 AND 900000000000101305 AND status='ACTIVE';
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@prc_deact_time
 WHERE role_permission_id BETWEEN 900000000000101201 AND 900000000000101204 AND status='ACTIVE';
UPDATE t_role SET version=version+1, update_time=@prc_deact_time
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE';
-- 核对影响行数后 COMMIT；否则 ROLLBACK。
