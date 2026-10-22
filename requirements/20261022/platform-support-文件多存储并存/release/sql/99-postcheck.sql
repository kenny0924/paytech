-- 文件多存储并存 · mor-test 激活后只读核对（阶段 7 第 7 步；root；不写任何数据）。actual ≠ expect 即停止并按 release.md 第 6 节回滚。
-- 存储实例、桶与路由的登记结果另由 98-storage-registration-check.sql 核对（第 8 步之后）。
SET NAMES utf8mb4;
SELECT '存储三表存在' AS item, COUNT(*) AS actual, 3 AS expect
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'
   AND TABLE_NAME IN ('t_file_storage_instance','t_file_storage_bucket','t_file_storage_route')
UNION ALL SELECT 'mor_platform 表数（47 + 3）', COUNT(*), 50 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'
UNION ALL SELECT 'source_system、reason_code 两表四列 NOT NULL', COUNT(*), 4
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME IN ('t_file_upload','t_file_object')
   AND COLUMN_NAME IN ('source_system','reason_code') AND IS_NULLABLE='NO' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=64
UNION ALL SELECT 't_file_upload 租户 / 上传人 / Owner 五列可空', COUNT(*), 5
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_upload'
   AND COLUMN_NAME IN ('tenant_id','uploader_id','owner_system','owner_type','owner_ref_id') AND IS_NULLABLE='YES'
UNION ALL SELECT 'idx_file_source(source_system, reason_code, create_time)', COUNT(*), 3
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_object' AND INDEX_NAME='idx_file_source'
UNION ALL SELECT '三个唯一键与路由桶索引', COUNT(DISTINCT INDEX_NAME), 4
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_platform'
   AND INDEX_NAME IN ('uk_file_storage_instance_code','uk_file_storage_bucket','uk_file_storage_route_source','idx_file_storage_route_bucket')
UNION ALL SELECT '回填后旧上传全部 platform-support / CLIENT_ONBOARDING_MATERIAL', COUNT(*), 0 FROM mor_platform.t_file_upload
  WHERE create_time < '2026-10-22' AND NOT (source_system='platform-support' AND reason_code='CLIENT_ONBOARDING_MATERIAL')
UNION ALL SELECT '回填后旧文件全部 platform-support / CLIENT_ONBOARDING_MATERIAL', COUNT(*), 0 FROM mor_platform.t_file_object
  WHERE create_time < '2026-10-22' AND NOT (source_system='platform-support' AND reason_code='CLIENT_ONBOARDING_MATERIAL')
UNION ALL SELECT '块 130 权限 ACTIVE', COUNT(*), 2 FROM mor_platform.t_permission
  WHERE permission_id BETWEEN 900000000000130101 AND 900000000000130102 AND status='ACTIVE'
UNION ALL SELECT '块 130 管理员授权 ACTIVE', COUNT(*), 2 FROM mor_platform.t_role_permission
  WHERE role_permission_id BETWEEN 900000000000130201 AND 900000000000130202 AND status='ACTIVE'
UNION ALL SELECT '块 130 菜单 + 按钮 ACTIVE', COUNT(*), 2 FROM mor_platform.t_console_resource
  WHERE resource_id BETWEEN 900000000000130301 AND 900000000000130302 AND status='ACTIVE'
UNION ALL SELECT '块 130 菜单挂在平台配置 562 下', COUNT(*), 1 FROM mor_platform.t_console_resource
  WHERE resource_id=900000000000130301 AND parent_resource_id=900000000000000562 AND component_key='OPERATIONS_FILE_STORAGE' AND frontend_route='/file-storage'
UNION ALL SELECT '块 130 路由 ACTIVE', COUNT(*), 12 FROM mor_platform.t_gateway_route
  WHERE route_id BETWEEN 900000000000130401 AND 900000000000130412 AND status='ACTIVE'
UNION ALL SELECT '块 130 路由头（含 11 修正 2 行）', COUNT(*), 64 FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '块 130 限流规则', COUNT(*), 72 FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT 'Vault 数据类型 ACTIVE（REFERENCE / SECRET / JSON）', COUNT(*), 1 FROM mor_vault.t_vault_data_type
  WHERE data_type_id=9100000000000740 AND data_type_code='FILE_STORAGE_CREDENTIAL_V1' AND token_mode='REFERENCE'
   AND data_class='SECRET' AND value_format='JSON' AND status='ACTIVE'
UNION ALL SELECT 'Vault 规则 PROTECT / FILE_STORAGE_SETUP、REVEAL / FILE_STORAGE_ACCESS ACTIVE', COUNT(*), 2 FROM mor_vault.t_vault_access_rule
  WHERE caller_service='platform-support-service' AND data_type_code='FILE_STORAGE_CREDENTIAL_V1' AND status='ACTIVE'
   AND ((access_rule_id=9100000000000741 AND action='PROTECT' AND purpose_code='FILE_STORAGE_SETUP')
     OR (access_rule_id=9100000000000742 AND action='REVEAL' AND purpose_code='FILE_STORAGE_ACCESS'))
UNION ALL SELECT 'Vault 策略版本（precheck 18 + 1）', MAX(policy_version_no), 19 FROM mor_vault.t_vault_policy_change;
SELECT 'PLATFORM_ADMIN version（应为 precheck 值 + 1）' AS item, version AS value FROM mor_platform.t_role WHERE role_id=900000000000000510
UNION ALL SELECT 't_file_upload 行数（≥ precheck）', COUNT(*) FROM mor_platform.t_file_upload
UNION ALL SELECT 't_file_object 行数（≥ precheck）', COUNT(*) FROM mor_platform.t_file_object;
