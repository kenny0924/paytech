-- Operations 风控评估查询与解释：mor-test 撤销激活（回退用；只改状态，不删除注册行）
-- 目标库：mor-test 的 mor_platform。预期影响行数：3 / 1 / 1 / 1；核对后 COMMIT。
START TRANSACTION;
SET @rev_deact_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@rev_deact_time
 WHERE route_id BETWEEN 900000000000111001 AND 900000000000111003 AND status='ACTIVE';
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@rev_deact_time
 WHERE resource_id=900000000000111301 AND status='ACTIVE';
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@rev_deact_time
 WHERE role_permission_id=900000000000111201 AND status='ACTIVE';
UPDATE t_role SET version=version+1, update_time=@rev_deact_time
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE';
-- 核对影响行数后 COMMIT；否则 ROLLBACK。
