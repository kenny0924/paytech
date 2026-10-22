-- 工作流注册块 127：彻底移除（可选，回滚第三步；先执行 90 并完成镜像回滚）。只在 mor-test mor_platform 执行。
-- 按块 127 头注释：子表先删。期望：guard_rule 192、query_param 29、route_header 136、gateway_route 32、console_resource 10、
-- role_permission 7、permission 7、notification_route 1、template_content 1、template 1（合计 416）。
-- 若工作流已发出过通知，t_notification_request 等投递记录引用模板 900000000000127081：先核对再决定是否保留模板三行。
-- 本文件不含 COMMIT：确认各数后由执行者追加 COMMIT（apply_sql.sh <B> remove-block127）。
START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE DATABASE()='mor_platform' AND guard_rule_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS guard_rules_192;
DELETE FROM t_gateway_route_query_param WHERE DATABASE()='mor_platform' AND route_query_param_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS query_params_29;
DELETE FROM t_gateway_route_header WHERE DATABASE()='mor_platform' AND route_header_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS route_headers_136;
DELETE FROM t_gateway_route WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS routes_32;
DELETE FROM t_console_resource WHERE DATABASE()='mor_platform' AND resource_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS resources_10;
DELETE FROM t_role_permission WHERE DATABASE()='mor_platform' AND role_permission_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS grants_7;
DELETE FROM t_permission WHERE DATABASE()='mor_platform' AND permission_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS permissions_7;
DELETE FROM t_notification_route WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS notification_routes_1;
DELETE FROM t_notification_template_content WHERE DATABASE()='mor_platform' AND content_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS template_contents_1;
DELETE FROM t_notification_template WHERE DATABASE()='mor_platform' AND template_id BETWEEN 900000000000127001 AND 900000000000127999;
SELECT ROW_COUNT() AS templates_1;
