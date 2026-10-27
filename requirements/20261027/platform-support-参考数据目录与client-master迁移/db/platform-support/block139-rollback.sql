-- Platform 块 139 彻底移除（按 ID 段删除，子表先删、按钮先于菜单），只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block139.py 生成。
-- 前置：已执行 block139-deactivate.sql（或从未激活）。
-- 预期删除：permission 1、role_permission 1、console_resource 1、gateway_route 5、route_header 10、query_param 11、guard_rule 30（共 59）；角色 1。
START TRANSACTION;
SET @b139_rollback_time = UTC_TIMESTAMP(6);
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000139901 AND 900000000000139930;
SELECT ROW_COUNT() AS guard_rules;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000139601 AND 900000000000139611;
SELECT ROW_COUNT() AS query_params;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000139501 AND 900000000000139510;
SELECT ROW_COUNT() AS route_headers;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000139401 AND 900000000000139405;
SELECT ROW_COUNT() AS routes;
DELETE FROM t_console_resource WHERE resource_id IN (900000000000139301);
SELECT ROW_COUNT() AS menus;
DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000139201 AND 900000000000139201;
SELECT ROW_COUNT() AS grants;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000139101 AND 900000000000139101;
SELECT ROW_COUNT() AS permissions;
UPDATE t_role SET version=version+1, update_time=@b139_rollback_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
