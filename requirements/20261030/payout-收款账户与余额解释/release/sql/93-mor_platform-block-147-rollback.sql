-- =====================================================================================================================
-- payout 收款账户与余额解释（S1）· 注册块 147 · 回滚（按 ID 删除，子表先删）。
-- 块号 147：任务卡原写 146，但 146 已被 tax（requirements/20261029/tax-结账算税与事件登记）占用、145 = REQ-04、144 = 联合查询，本稿取 147；阶段 6 只读核对现网最大块号后替换（ID 区间 900000000000147001—900000000000147999）。
-- 保护：① 路由仍 ACTIVE 时拒绝（先执行 -deactivate.sql）；② 已有通知请求引用本块模板时拒绝——已受理的通知记录依赖模板与路由，
-- 此时只停用、不删除块。拒绝方式：标量子查询返回 2 行 → ERROR 1242，事务未开始、不做任何改动。
-- 本文件同时删除本块 8 个权限在任何角色上的授权（含 Client 系统角色回填行、矩阵授予行，以及运营在角色管理中组合的授权）。
-- Platform 新版本的 ClientTeamPermissions 矩阵在权限不存在时跳过授予（activePermissionIds 只取存在的权限码），回滚后不影响投影。
-- 重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

SELECT (SELECT 1 UNION ALL SELECT 2) AS `refuse_routes_active` FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419 AND status = 'ACTIVE' LIMIT 1;
SELECT (SELECT 1 UNION ALL SELECT 2) AS `refuse_notifications_exist` FROM t_notification_request WHERE notification_template_id BETWEEN 900000000000147801 AND 900000000000147804 LIMIT 1;

START TRANSACTION;
DELETE FROM t_notification_route WHERE route_id BETWEEN 900000000000147821 AND 900000000000147824;
DELETE FROM t_notification_template_content WHERE content_id BETWEEN 900000000000147811 AND 900000000000147818;
DELETE FROM t_notification_template WHERE template_id BETWEEN 900000000000147801 AND 900000000000147804;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000147601 AND 900000000000147714;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000147001 AND 900000000000147019;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000147501 AND 900000000000147571;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419;
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307 AND resource_type = 'BUTTON';
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307 AND parent_resource_id = 900000000000147301;
DELETE FROM t_console_resource WHERE resource_id = 900000000000147301;
DELETE FROM t_role_permission WHERE permission_id BETWEEN 900000000000147101 AND 900000000000147108;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000147101 AND 900000000000147108;
COMMIT;
