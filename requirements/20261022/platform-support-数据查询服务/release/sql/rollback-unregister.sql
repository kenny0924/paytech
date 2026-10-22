-- 数据查询 · mor-test 彻底回滚（先执行 test-deactivate.sql 并换回旧镜像，再执行本文件；root；客户端 utf8mb4）。
-- 顺序：块 131 注册行（子表先删）→ 3 张表（确认无需保留的数据源与模板）→ Vault 策略 → 验收被查库与账号。
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000131001 AND 900000000000131999;
DELETE FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000131001 AND 900000000000131999;
DELETE FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000131001 AND 900000000000131999;
DELETE FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000131001 AND 900000000000131999;
DELETE FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000131001 AND 900000000000131999;
DELETE FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000131001 AND 900000000000131999;
DELETE FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000131001 AND 900000000000131999;
UPDATE mor_platform.t_role SET version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_id=900000000000000510;
COMMIT;
DROP TABLE IF EXISTS mor_platform.t_query_template_version;
DROP TABLE IF EXISTS mor_platform.t_query_template;
DROP TABLE IF EXISTS mor_platform.t_data_source;
START TRANSACTION;
DELETE FROM mor_vault.t_vault_access_rule WHERE access_rule_id IN (9100000000000751, 9100000000000752);
DELETE FROM mor_vault.t_vault_data_type WHERE data_type_id = 9100000000000750;
DELETE FROM mor_vault.t_vault_policy_change WHERE policy_change_id = 9100000000000750;
COMMIT;
DROP USER IF EXISTS 'data_query_fixture_reader'@'%', 'data_query_fixture_writer'@'%';
DROP DATABASE IF EXISTS mor_data_query_fixture;
