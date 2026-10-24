-- 草稿（未执行）：彻底移除块 136 注册行（按 ID 段删除，子表先删），只在 mor_platform 执行，客户端须 utf8mb4。
-- 前置：已执行 21-deactivate-block136.sql（或从未激活），3 个服务已按 release.md 第 7 节回滚或确认不再需要文件上传入口。
-- 预期删除：guard_rule 30、query_param 5、route_header 18、gateway_route 5、console_resource 2、role_permission 2、permission 2（共 64）。
-- 曾经激活过时递增 PLATFORM_ADMIN 版本，使会话重新计算授权与导航（影响 1 行）。不动写入规则（见 release.md 第 7 节）。
START TRANSACTION;
SET @file_upload_rollback_time = UTC_TIMESTAMP(6);
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000136701 AND 900000000000136730;
SELECT ROW_COUNT() AS guard_rules;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000136601 AND 900000000000136605;
SELECT ROW_COUNT() AS query_params;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000136501 AND 900000000000136518;
SELECT ROW_COUNT() AS route_headers;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000136401 AND 900000000000136405;
SELECT ROW_COUNT() AS routes;
DELETE FROM t_console_resource WHERE resource_id IN (900000000000136302, 900000000000136301);
SELECT ROW_COUNT() AS resources;
DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000136201 AND 900000000000136202;
SELECT ROW_COUNT() AS grants;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000136101 AND 900000000000136102;
SELECT ROW_COUNT() AS permissions;
UPDATE t_role SET version = version + 1, update_time = @file_upload_rollback_time WHERE role_id = 900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
