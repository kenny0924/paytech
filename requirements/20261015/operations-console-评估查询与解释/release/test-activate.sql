-- Operations 风控评估查询与解释：mor-test 激活（阶段 6 评审稿，阶段 7 经授权执行）
-- 目标库：mor-test 的 mor_platform；用 mysql --default-character-set=utf8mb4 执行。
-- 前置：已执行 ../db/platform-support-service/registration-dml.sql（32 行）并 COMMIT；新 BFF 与 Web 已替换并 healthy。
-- 激活 3 条路由、1 个菜单资源、PLATFORM_ADMIN 的 1 个授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：3 / 1 / 1 / 1；在同一连接中核对后 COMMIT，任何不符则 ROLLBACK。撤销见 test-deactivate.sql。
START TRANSACTION;
SET @rev_act_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@rev_act_time
 WHERE route_id BETWEEN 900000000000111001 AND 900000000000111003 AND status='DISABLED';
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@rev_act_time
 WHERE resource_id=900000000000111301 AND status='DISABLED';
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@rev_act_time
 WHERE role_permission_id=900000000000111201 AND status='REVOKED';
UPDATE t_role SET version=version+1, update_time=@rev_act_time
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE';
-- 核对影响行数后 COMMIT；否则 ROLLBACK。
