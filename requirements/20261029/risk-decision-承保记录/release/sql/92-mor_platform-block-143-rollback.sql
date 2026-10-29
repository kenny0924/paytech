-- =====================================================================================================================
-- REQ-03 注册块 143 · 回滚（按 ID 删除，子表先删；路由已 ACTIVE 时删除后重新发布 Gateway 快照）
-- 先回退 operations-console-web 与 operations-console-bff，再执行本文件；若运营已把三项权限组合进角色，
-- 本文件同时删除这些角色授权行（t_role_permission 中引用 1431xx 权限的全部行）。重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000143601 AND 900000000000143672;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000143001 AND 900000000000143008;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000143501 AND 900000000000143552;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000143401 AND 900000000000143412;
DELETE FROM t_console_resource WHERE resource_id IN (900000000000143302, 900000000000143303);
DELETE FROM t_console_resource WHERE resource_id = 900000000000143301;
DELETE FROM t_role_permission WHERE permission_id BETWEEN 900000000000143101 AND 900000000000143103;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000143101 AND 900000000000143103;
COMMIT;
