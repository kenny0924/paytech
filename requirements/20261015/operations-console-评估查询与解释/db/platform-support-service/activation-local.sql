-- Operations 风控评估查询与解释：本机全链路激活（阶段 5，只在本机 mor_platform_e2e 执行）
-- 前置：已执行同目录 registration-dml.sql 并 COMMIT。mor-test 与其他环境另行授权，不沿用本文件。
-- 激活 3 条路由、1 个菜单资源、PLATFORM_ADMIN 的 1 个授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：3 / 1 / 1 / 1。
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
-- 撤销：同一组 ID 反向置 DISABLED / REVOKED（WHERE status='ACTIVE'），version+1，并再次递增 PLATFORM_ADMIN 角色 version。
