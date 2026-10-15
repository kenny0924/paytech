-- Operations 接入支付风控配置与执行发布：mor-test 激活（阶段 6 评审稿，阶段 7 经授权执行）
-- 目标库：mor-test 的 mor_platform（127.0.0.1:23306，platform_app）；用 mysql --default-character-set=utf8mb4 执行。
-- 前置：已执行 ../db/platform-support-service/registration-dml.sql（150 行）并 COMMIT；新 BFF 与 Web 已替换并 healthy。
--       决策流、统一变量、拒绝原因、规则集历史/删除在 mor-test 已按各自需求开放（2026-10-15 核对 28/13/8 ACTIVE），本文件不涉及。
-- 激活 11 条路由、5 个菜单/按钮资源、PLATFORM_ADMIN 的 4 个授权，并递增角色版本使会话重新计算授权（核对时 v23 → v24）。
-- 预期影响行数：11 / 5 / 4 / 1；在同一连接中核对后 COMMIT，任何不符则 ROLLBACK。撤销见 test-deactivate.sql。
START TRANSACTION;
SET @prc_act_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@prc_act_time
 WHERE route_id BETWEEN 900000000000101001 AND 900000000000101011 AND status='DISABLED';
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@prc_act_time
 WHERE resource_id BETWEEN 900000000000101301 AND 900000000000101305 AND status='DISABLED';
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@prc_act_time
 WHERE role_permission_id BETWEEN 900000000000101201 AND 900000000000101204 AND status='REVOKED';
UPDATE t_role SET version=version+1, update_time=@prc_act_time
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE';
-- 核对影响行数后 COMMIT；否则 ROLLBACK。
