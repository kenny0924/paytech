-- 彻底移除块 137 注册行（按 ID 段删除，子表先删），只在 mor_platform 执行，客户端须 utf8mb4。写法同块 136 回滚。
-- 前置：已执行 62-platform-block137-deactivate.sql（或从未激活）；ops-bff / ops-web 已回滚或确认不再需要三个运营入口。
-- 预期删除：guard_rule 66、query_param 17、route_header 38、gateway_route 11、console_resource 6（按钮先于菜单）、role_permission 6、
-- permission 6（共 150）。递增 PLATFORM_ADMIN 版本，使会话重新计算授权与导航（影响 1 行）。多语言 key / 命名空间不在本文件（见 release 说明）。
START TRANSACTION;
SET @relsnap_rollback_time = UTC_TIMESTAMP(6);
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000137701 AND 900000000000137799;
SELECT ROW_COUNT() AS guard_rules;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000137601 AND 900000000000137699;
SELECT ROW_COUNT() AS query_params;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000137501 AND 900000000000137599;
SELECT ROW_COUNT() AS route_headers;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000137401 AND 900000000000137411;
SELECT ROW_COUNT() AS routes;
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000137304 AND 900000000000137306;
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000137301 AND 900000000000137303;
SELECT ROW_COUNT() AS menus;
DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000137201 AND 900000000000137206;
SELECT ROW_COUNT() AS grants;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000137101 AND 900000000000137106;
SELECT ROW_COUNT() AS permissions;
UPDATE t_role SET version = version + 1, update_time = @relsnap_rollback_time WHERE role_id = 900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
