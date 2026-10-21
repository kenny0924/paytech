-- 币种主数据 · mor-test 执行前只读核对（阶段 7 第 1 步；root；库名全限定，不写任何数据）。
-- 期望值写在每行 expect 列；任何一行 actual ≠ expect 即停止发布。
SET NAMES utf8mb4;
SELECT 'platform version 列不存在' AS item, COUNT(*) AS actual, 0 AS expect
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_currency_spec' AND COLUMN_NAME='version'
UNION ALL SELECT '块 125 权限为空', COUNT(*), 0 FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000125000 AND 900000000000125999
UNION ALL SELECT '块 125 授权为空', COUNT(*), 0 FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000125000 AND 900000000000125999
UNION ALL SELECT '块 125 资源为空', COUNT(*), 0 FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000125000 AND 900000000000125999
UNION ALL SELECT '块 125 路由为空', COUNT(*), 0 FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000125000 AND 900000000000125999
UNION ALL SELECT '块 125 路由头为空', COUNT(*), 0 FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000125000 AND 900000000000125999
UNION ALL SELECT '块 125 限流规则为空', COUNT(*), 0 FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000125000 AND 900000000000125999
UNION ALL SELECT '权限码 CURRENCY_SPEC_* 不存在', COUNT(*), 0 FROM mor_platform.t_permission WHERE permission_code LIKE 'CURRENCY\_SPEC\_%'
UNION ALL SELECT '组件键 OPERATIONS_CURRENCY_SPECS 不存在', COUNT(*), 0 FROM mor_platform.t_console_resource WHERE component_key='OPERATIONS_CURRENCY_SPECS'
UNION ALL SELECT '已用最大块号', MAX(FLOOR(permission_id / 1000) - 900000000000000), 124 FROM mor_platform.t_permission
UNION ALL SELECT '父菜单 平台配置 562 ACTIVE', COUNT(*), 1 FROM mor_platform.t_console_resource WHERE resource_id=900000000000000562 AND status='ACTIVE'
UNION ALL SELECT 'PLATFORM_ADMIN 角色存在', COUNT(*), 1 FROM mor_platform.t_role WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN'
UNION ALL SELECT 'platform 币种规则只有 USD 一行', COUNT(*), 1 FROM mor_platform.t_currency_spec
UNION ALL SELECT 'platform USD 2 位 HALF_UP ACTIVE', COUNT(*), 1 FROM mor_platform.t_currency_spec WHERE currency='USD' AND minor_unit=2 AND rounding_mode='HALF_UP' AND status='ACTIVE'
UNION ALL SELECT 'ledger 副本只有 USD 一行', COUNT(*), 1 FROM mor_ledger.t_currency_spec WHERE currency='USD' AND minor_unit=2 AND rounding_mode='HALF_UP' AND status='ACTIVE'
UNION ALL SELECT 'billing 副本只有 USD 一行', COUNT(*), 1 FROM mor_billing.t_currency_spec WHERE currency='USD' AND minor_unit=2 AND rounding_mode='HALF_UP' AND status='ACTIVE'
UNION ALL SELECT '验收币种 XTS 不存在', COUNT(*), 0 FROM mor_platform.t_currency_spec WHERE currency='XTS';
-- 记录用（无期望值）：PLATFORM_ADMIN 当前角色版本，激活后应 +1。
SELECT 'PLATFORM_ADMIN version' AS item, version FROM mor_platform.t_role WHERE role_id=900000000000000510;
