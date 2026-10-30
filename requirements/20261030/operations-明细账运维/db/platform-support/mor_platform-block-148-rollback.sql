-- =====================================================================================================================
-- operations 明细账运维 · 注册块 148 · 回滚（按 ID 删除，子表先删）。
-- 保护：路由仍 ACTIVE 时拒绝（先执行 -deactivate.sql）。拒绝方式：标量子查询返回 2 行 → ERROR 1242，事务未开始、不做任何改动。
-- 若运营已把 FINANCE_SUBLEDGER_MANAGE 组合进其他角色，本文件一并删除这些授权行。重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

SELECT (SELECT 1 UNION ALL SELECT 2) AS `refuse_routes_active` FROM t_gateway_route WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404 AND status = 'ACTIVE' LIMIT 1;

START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000148701 AND 900000000000148724;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000148601 AND 900000000000148602;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000148501 AND 900000000000148516;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404;
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000148301 AND 900000000000148303;
DELETE FROM t_role_permission WHERE permission_id = 900000000000148101;
DELETE FROM t_permission WHERE permission_id = 900000000000148101;
COMMIT;
