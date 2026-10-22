-- 文件多存储并存 · mor-test 执行前只读核对（阶段 7 第 1、2 步；root；库名全限定，不写任何数据）。
-- 期望值写在每行 expect 列；任何一行 actual ≠ expect 即停止发布并查明原因（其他会话可能已改动 mor-test）。
SET NAMES utf8mb4;
SELECT '存储三表不存在' AS item, COUNT(*) AS actual, 0 AS expect
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'
   AND TABLE_NAME IN ('t_file_storage_instance','t_file_storage_bucket','t_file_storage_route')
UNION ALL SELECT 't_file_upload / t_file_object 尚无 source_system、reason_code', COUNT(*), 0
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME IN ('t_file_upload','t_file_object')
   AND COLUMN_NAME IN ('source_system','reason_code')
UNION ALL SELECT 't_file_upload 租户 / 上传人 / Owner 五列仍为 NOT NULL', COUNT(*), 5
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_upload'
   AND COLUMN_NAME IN ('tenant_id','uploader_id','owner_system','owner_type','owner_ref_id') AND IS_NULLABLE='NO'
UNION ALL SELECT '加列定位列 uploader_id、upload_id 存在', COUNT(*), 2
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform'
   AND ((TABLE_NAME='t_file_upload' AND COLUMN_NAME='uploader_id') OR (TABLE_NAME='t_file_object' AND COLUMN_NAME='upload_id'))
UNION ALL SELECT 'mor_platform 表数', COUNT(*), 47 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'
UNION ALL SELECT '没有进行中的上传（全部 COMPLETED）', COUNT(*), 0 FROM mor_platform.t_file_upload WHERE status <> 'COMPLETED'
UNION ALL SELECT '上传记录全部在 MINIO / mor-platform-files', COUNT(*), 0 FROM mor_platform.t_file_upload
  WHERE storage_provider <> 'MINIO' OR storage_container_ref <> 'mor-platform-files'
UNION ALL SELECT '文件记录全部在 MINIO / mor-platform-files', COUNT(*), 0 FROM mor_platform.t_file_object
  WHERE storage_provider <> 'MINIO' OR storage_container_ref <> 'mor-platform-files'
UNION ALL SELECT '扫描未完成的文件', COUNT(*), 0 FROM mor_platform.t_file_object WHERE scan_status <> 'AVAILABLE'
UNION ALL SELECT '待执行补偿任务（含 FILE_SCAN）', COUNT(*), 0 FROM mor_platform.t_compensation_event
UNION ALL SELECT '扫描策略 CLIENT_ONBOARDING_FORMAT ACTIVE', COUNT(*), 1 FROM mor_platform.t_file_scan_policy
  WHERE policy_code='CLIENT_ONBOARDING_FORMAT' AND status='ACTIVE'
UNION ALL SELECT '块 130 权限为空', COUNT(*), 0 FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '块 130 授权为空', COUNT(*), 0 FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '块 130 资源为空', COUNT(*), 0 FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '块 130 路由为空', COUNT(*), 0 FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '块 130 路由头为空', COUNT(*), 0 FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '块 130 限流规则为空', COUNT(*), 0 FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000130000 AND 900000000000130999
UNION ALL SELECT '权限码 FILE_STORAGE_* 不存在', COUNT(*), 0 FROM mor_platform.t_permission WHERE permission_code LIKE 'FILE\_STORAGE\_%'
UNION ALL SELECT '组件键 OPERATIONS_FILE_STORAGE* 不存在', COUNT(*), 0 FROM mor_platform.t_console_resource
  WHERE component_key='OPERATIONS_FILE_STORAGE' OR resource_code LIKE 'OPERATIONS\_FILE\_STORAGE%'
UNION ALL SELECT '外部路径 /api/operations/storage-admin 未被占用', COUNT(*), 0 FROM mor_platform.t_gateway_route
  WHERE external_path_template LIKE '/api/operations/storage-admin/%'
UNION ALL SELECT '已用最大路由块号（127—129、131 为他人保留）', MAX(FLOOR(route_id / 1000) - 900000000000000), 126
  FROM mor_platform.t_gateway_route WHERE route_id >= 900000000000000000
UNION ALL SELECT '父菜单 平台配置 562 ACTIVE', COUNT(*), 1 FROM mor_platform.t_console_resource WHERE resource_id=900000000000000562 AND status='ACTIVE'
UNION ALL SELECT 'PLATFORM_ADMIN 角色存在', COUNT(*), 1 FROM mor_platform.t_role WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN'
UNION ALL SELECT 'Vault 数据类型 FILE_STORAGE_CREDENTIAL_V1 不存在', COUNT(*), 0 FROM mor_vault.t_vault_data_type
  WHERE data_type_code='FILE_STORAGE_CREDENTIAL_V1' OR data_type_id=9100000000000740
UNION ALL SELECT 'Vault 规则 ID 741—742 与该类型规则不存在', COUNT(*), 0 FROM mor_vault.t_vault_access_rule
  WHERE access_rule_id IN (9100000000000741, 9100000000000742) OR data_type_code='FILE_STORAGE_CREDENTIAL_V1'
UNION ALL SELECT 'Vault 策略变更 ID 740 不存在', COUNT(*), 0 FROM mor_vault.t_vault_policy_change WHERE policy_change_id=9100000000000740
UNION ALL SELECT 'Vault 当前策略版本', MAX(policy_version_no), 18 FROM mor_vault.t_vault_policy_change;
-- 记录用（无期望值）：行数与角色版本，激活后 PLATFORM_ADMIN 应 +1；迁移回填后两表行数不变。
SELECT 'PLATFORM_ADMIN version' AS item, version AS value FROM mor_platform.t_role WHERE role_id=900000000000000510
UNION ALL SELECT 't_file_upload 行数', COUNT(*) FROM mor_platform.t_file_upload
UNION ALL SELECT 't_file_object 行数', COUNT(*) FROM mor_platform.t_file_object
UNION ALL SELECT 't_file_owner_ref 行数', COUNT(*) FROM mor_platform.t_file_owner_ref
UNION ALL SELECT '入驻审核证据引用的文件（mor_compliance.t_dd_evidence_ref）', COUNT(DISTINCT e.file_id)
  FROM mor_compliance.t_dd_evidence_ref e JOIN mor_platform.t_file_object f ON f.file_id = e.file_id;
