-- 币种主数据 · mor-test 激活后只读核对（阶段 7 第 6 步；root；不写任何数据）。actual ≠ expect 即停止并按 release.md 第 6 节回滚。
SET NAMES utf8mb4;
SELECT 'platform version 列 INT NOT NULL DEFAULT 1' AS item, COUNT(*) AS actual, 1 AS expect
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_currency_spec' AND COLUMN_NAME='version'
   AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1'
   AND COLUMN_COMMENT='币种规则乐观并发版本，从 1 开始，每次维护成功加一。'
UNION ALL SELECT 'platform 表注释已更新', COUNT(*), 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_currency_spec'
   AND TABLE_COMMENT='保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 唯一持有，ledger、billing 经内部 HTTP 读取并在进程内缓存快照。'
UNION ALL SELECT 'USD 版本 1 且规则不变', COUNT(*), 1 FROM mor_platform.t_currency_spec WHERE currency='USD' AND minor_unit=2 AND rounding_mode='HALF_UP' AND status='ACTIVE' AND version=1
UNION ALL SELECT '块 125 权限 ACTIVE', COUNT(*), 2 FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000125101 AND 900000000000125102 AND status='ACTIVE'
UNION ALL SELECT '块 125 管理员授权 ACTIVE', COUNT(*), 2 FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000125201 AND 900000000000125202 AND status='ACTIVE'
UNION ALL SELECT '块 125 菜单 + 按钮 ACTIVE', COUNT(*), 2 FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000125301 AND 900000000000125302 AND status='ACTIVE'
UNION ALL SELECT '块 125 菜单挂在平台配置 562 下', COUNT(*), 1 FROM mor_platform.t_console_resource WHERE resource_id=900000000000125301 AND parent_resource_id=900000000000000562 AND component_key='OPERATIONS_CURRENCY_SPECS' AND frontend_route='/currency-specs'
UNION ALL SELECT '块 125 路由 ACTIVE', COUNT(*), 4 FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000125401 AND 900000000000125404 AND status='ACTIVE'
UNION ALL SELECT '块 125 路由头', COUNT(*), 20 FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000125501 AND 900000000000125520
UNION ALL SELECT '块 125 限流规则', COUNT(*), 24 FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000125701 AND 900000000000125724
UNION ALL SELECT 'ledger 副本仍在（删除是最后一步）', COUNT(*), 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_ledger' AND TABLE_NAME='t_currency_spec'
UNION ALL SELECT 'billing 副本仍在（删除是最后一步）', COUNT(*), 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_billing' AND TABLE_NAME='t_currency_spec';
SELECT 'PLATFORM_ADMIN version（应为 precheck 值 + 1）' AS item, version FROM mor_platform.t_role WHERE role_id=900000000000000510;
