-- 数据查询 · mor-test 执行前只读核对（阶段 7 第 1 步；root；库名全限定，不写任何数据）。
-- 期望值写在每行 expect 列；任何一行 actual ≠ expect 即停止发布。
SET NAMES utf8mb4;
SELECT '3 张数据查询表不存在' AS item, COUNT(*) AS actual, 0 AS expect
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME IN ('t_data_source','t_query_template','t_query_template_version')
UNION ALL SELECT '块 131 权限为空', COUNT(*), 0 FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '块 131 授权为空', COUNT(*), 0 FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '块 131 资源为空', COUNT(*), 0 FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '块 131 路由为空', COUNT(*), 0 FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '块 131 路由头为空', COUNT(*), 0 FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '块 131 查询参数为空', COUNT(*), 0 FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '块 131 限流规则为空', COUNT(*), 0 FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000131000 AND 900000000000131999
UNION ALL SELECT '权限码不存在', COUNT(*), 0 FROM mor_platform.t_permission
   WHERE permission_code IN ('DATA_QUERY_READ','DATA_SOURCE_MANAGE','DATA_QUERY_TEMPLATE_MANAGE','DATA_QUERY_EXECUTE')
UNION ALL SELECT '资源码 / 组件键不存在', COUNT(*), 0 FROM mor_platform.t_console_resource
   WHERE resource_code IN ('OPERATIONS_DATA_QUERY','OPERATIONS_DATA_SOURCES','OPERATIONS_QUERY_TEMPLATES','OPERATIONS_DATA_SOURCE_MANAGE','OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE')
      OR component_key IN ('OPERATIONS_DATA_SOURCES','OPERATIONS_QUERY_TEMPLATES')
UNION ALL SELECT '外部路径 data-query 未被占用', COUNT(*), 0 FROM mor_platform.t_gateway_route
   WHERE external_path_template LIKE '/api/operations/data-query%'
UNION ALL SELECT '已用最大块号 < 131', CASE WHEN MAX(FLOOR(permission_id / 1000) - 900000000000000) < 131 THEN 1 ELSE 0 END, 1 FROM mor_platform.t_permission
UNION ALL SELECT '顶层菜单排序 65 未被占用', COUNT(*), 0 FROM mor_platform.t_console_resource
   WHERE channel='OPERATIONS' AND parent_resource_id IS NULL AND sort_order=65
UNION ALL SELECT 'PLATFORM_ADMIN 角色存在', COUNT(*), 1 FROM mor_platform.t_role WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN'
UNION ALL SELECT 'BFF Target 900000000000000001 存在', COUNT(*), 1 FROM mor_platform.t_gateway_target WHERE target_id=900000000000000001
UNION ALL SELECT 'Vault 类型与 ID 750—752 未占用', COUNT(*), 0 FROM mor_vault.t_vault_data_type
   WHERE data_type_code='DATA_SOURCE_PASSWORD_V1' OR data_type_id BETWEEN 9100000000000750 AND 9100000000000752
UNION ALL SELECT 'Vault 规则 ID 750—752 未占用', COUNT(*), 0 FROM mor_vault.t_vault_access_rule
   WHERE data_type_code='DATA_SOURCE_PASSWORD_V1' OR access_rule_id BETWEEN 9100000000000750 AND 9100000000000752
UNION ALL SELECT 'Vault 策略变更 ID 750 未占用', COUNT(*), 0 FROM mor_vault.t_vault_policy_change WHERE policy_change_id=9100000000000750
UNION ALL SELECT '验收被查库 mor_data_query_fixture 已建（2026-10-22 阶段 6 预建）', COUNT(*), 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='mor_data_query_fixture'
UNION ALL SELECT '验收账号 reader / writer 已建', COUNT(*), 2 FROM mysql.user WHERE user IN ('data_query_fixture_reader','data_query_fixture_writer');
-- 记录用（无期望值）：PLATFORM_ADMIN 当前角色版本（激活后应 +1）与 Vault 当前策略版本（Vault DML 后应 +1）。
SELECT 'PLATFORM_ADMIN version' AS item, version FROM mor_platform.t_role WHERE role_id=900000000000000510;
SELECT 'Vault policy version' AS item, MAX(policy_version_no) FROM mor_vault.t_vault_policy_change;
