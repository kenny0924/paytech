-- 数据查询 · mor-test 激活后只读核对（阶段 7；root；不写任何数据）。actual ≠ expect 即停止并按 release.md 回滚。
SET NAMES utf8mb4;
SELECT '3 张数据查询表存在' AS item, COUNT(*) AS actual, 3 AS expect
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME IN ('t_data_source','t_query_template','t_query_template_version')
UNION ALL SELECT 't_data_source.database_name 可空', COUNT(*), 1 FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_data_source' AND COLUMN_NAME='database_name' AND IS_NULLABLE='YES'
UNION ALL SELECT '块 131 权限 ACTIVE', COUNT(*), 4 FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000131101 AND 900000000000131104 AND status='ACTIVE'
UNION ALL SELECT '块 131 管理员授权 ACTIVE', COUNT(*), 4 FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000131201 AND 900000000000131204 AND status='ACTIVE'
UNION ALL SELECT '块 131 菜单 + 按钮 ACTIVE', COUNT(*), 5 FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000131301 AND 900000000000131305 AND status='ACTIVE'
UNION ALL SELECT '块 131 路由 ACTIVE', COUNT(*), 18 FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000131401 AND 900000000000131418 AND status='ACTIVE'
UNION ALL SELECT '块 131 路由头', COUNT(*), 82 FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000131001 AND 900000000000131999
UNION ALL SELECT '块 131 查询参数', COUNT(*), 13 FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000131001 AND 900000000000131999
UNION ALL SELECT '块 131 限流规则', COUNT(*), 108 FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000131001 AND 900000000000131999
UNION ALL SELECT 'Vault 类型 ACTIVE', COUNT(*), 1 FROM mor_vault.t_vault_data_type WHERE data_type_id=9100000000000750 AND data_type_code='DATA_SOURCE_PASSWORD_V1' AND status='ACTIVE'
UNION ALL SELECT 'Vault 规则 ACTIVE', COUNT(*), 2 FROM mor_vault.t_vault_access_rule WHERE access_rule_id IN (9100000000000751, 9100000000000752) AND status='ACTIVE'
UNION ALL SELECT '验收被查库 3 表', COUNT(*), 3 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_data_query_fixture';
SELECT 'PLATFORM_ADMIN version（应为 precheck 值 + 1）' AS item, version FROM mor_platform.t_role WHERE role_id=900000000000000510;
