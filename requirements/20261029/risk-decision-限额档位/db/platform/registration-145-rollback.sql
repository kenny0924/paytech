-- =====================================================================================================================
-- REQ-04 注册块 145 · 回滚（按 ID 删除，子表先删；路由已 ACTIVE 时删除后重新发布 Gateway 快照）
-- 先回退 operations-console-web 与 operations-console-bff，再执行本文件；若运营已把四项权限组合进角色，
-- 本文件同时删除这些角色授权行（t_role_permission 中引用 1451xx 权限的全部行）。重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000145601 AND 900000000000145666;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000145001 AND 900000000000145006;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000145501 AND 900000000000145546;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000145401 AND 900000000000145411;
DELETE FROM t_console_resource WHERE resource_id IN (900000000000145302, 900000000000145303, 900000000000145304);
DELETE FROM t_console_resource WHERE resource_id = 900000000000145301;
DELETE FROM t_role_permission WHERE permission_id BETWEEN 900000000000145101 AND 900000000000145104;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000145101 AND 900000000000145104;
COMMIT;
