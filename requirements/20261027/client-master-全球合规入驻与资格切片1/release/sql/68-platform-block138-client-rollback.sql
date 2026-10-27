-- Platform 块 138 彻底移除（按 ID 段删除，子表先删、按钮先于菜单），只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 生成。
-- 前置：已执行 block138-deactivate.sql（或从未激活）。本文件同样恢复 client-onboarding-c03（若仍为 DISABLED）。
-- 预期删除：permission 0、role_permission 0、console_resource 0、gateway_route 5、route_header 13、query_param 4、guard_rule 30（共 52）；c03 0—1；角色 1。
START TRANSACTION;
SET @b138_rollback_time = UTC_TIMESTAMP(6);
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000138901 AND 900000000000138930;
SELECT ROW_COUNT() AS guard_rules;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000138651 AND 900000000000138654;
SELECT ROW_COUNT() AS query_params;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000138581 AND 900000000000138593;
SELECT ROW_COUNT() AS route_headers;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000138441 AND 900000000000138445;
SELECT ROW_COUNT() AS routes;
COMMIT;
