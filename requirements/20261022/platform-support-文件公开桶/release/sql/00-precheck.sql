-- 文件公开桶 · mor-test 执行前只读核对（阶段 6 执行一次、阶段 7 备份时再执行一次；root；库名全限定，不写任何数据）。
-- 第一组每行带 expect：任何一行 actual ≠ expect 即停止，查明是否有人改过 mor-test 或本包是否过期。
-- 第二组只记录（行数会随正常业务增长），与 release.md 第 4 节对照。
SET NAMES utf8mb4;

-- ===== 1. 结构：仍是本需求之前的 Schema，DDL 的 MODIFY 不会改变列类型 =====
SELECT 'S01 t_file_version 尚不存在' AS item, COUNT(*) AS actual, 0 AS expect
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_version'
UNION ALL SELECT 'S02 本需求 10 个新列都不存在', COUNT(*), 0
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND (
       (TABLE_NAME='t_file_object' AND COLUMN_NAME IN ('access_level','current_version_no'))
    OR (TABLE_NAME='t_file_upload' AND COLUMN_NAME IN ('access_level','replaces_file_id','replacement_no','base_object_version'))
    OR (TABLE_NAME='t_file_storage_bucket' AND COLUMN_NAME IN ('access_level','public_base_url'))
    OR (TABLE_NAME='t_file_storage_route' AND COLUMN_NAME='access_level')
    OR (TABLE_NAME='t_file_scan_result' AND COLUMN_NAME='file_version_no'))
UNION ALL SELECT 'S03 新索引 idx_file_create_time / idx_file_upload_replaces 不存在', COUNT(DISTINCT INDEX_NAME), 0
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_platform'
   AND INDEX_NAME IN ('idx_file_create_time','idx_file_upload_replaces')
UNION ALL SELECT 'S04 uk_upload_storage_target = provider,container,object_ref', COUNT(*), 1 FROM (
  SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) c FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_upload' AND INDEX_NAME='uk_upload_storage_target') x
  WHERE c='storage_provider,storage_container_ref,storage_object_ref'
UNION ALL SELECT 'S05 uk_file_storage_route_source = source_system', COUNT(*), 1 FROM (
  SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) c FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_storage_route' AND INDEX_NAME='uk_file_storage_route_source') x
  WHERE c='source_system'
UNION ALL SELECT 'S06 uk_file_scan_attempt = file_id,scan_requirement_id,attempt_no', COUNT(*), 1 FROM (
  SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) c FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_scan_result' AND INDEX_NAME='uk_file_scan_attempt') x
  WHERE c='file_id,scan_requirement_id,attempt_no'
UNION ALL SELECT 'S07 DDL MODIFY 的 11 列类型 / 可空与 DDL 相同（只改注释）', COUNT(*), 11
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND (
       (TABLE_NAME='t_file_object' AND (COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE) IN (
          ('storage_object_ref','varchar(255)','NO'), ('object_version','varchar(128)','NO'), ('content_hash','char(64)','NO'),
          ('size_bytes','bigint','NO'), ('mime_type','varchar(255)','NO'), ('storage_encryption_key_ref','varchar(255)','NO'),
          ('scan_policy_id','bigint','NO'), ('scan_status','varchar(64)','NO'), ('available_time','datetime(6)','YES')))
    OR (TABLE_NAME='t_file_storage_bucket' AND COLUMN_NAME='last_check_result' AND COLUMN_TYPE='varchar(64)' AND IS_NULLABLE='YES')
    OR (TABLE_NAME='t_file_storage_route' AND COLUMN_NAME='source_system' AND COLUMN_TYPE='varchar(64)' AND IS_NULLABLE='NO'))
UNION ALL SELECT 'S08 ADD COLUMN ... AFTER 的 7 个锚点列存在', COUNT(*), 7
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND (
       (TABLE_NAME='t_file_object' AND COLUMN_NAME IN ('reason_code','storage_object_ref'))
    OR (TABLE_NAME='t_file_upload' AND COLUMN_NAME IN ('reason_code','storage_object_ref'))
    OR (TABLE_NAME='t_file_storage_bucket' AND COLUMN_NAME='bucket_name')
    OR (TABLE_NAME='t_file_storage_route' AND COLUMN_NAME='source_system')
    OR (TABLE_NAME='t_file_scan_result' AND COLUMN_NAME='file_id'))
UNION ALL SELECT 'S09 mor_platform 表数', COUNT(*), 63 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'

-- ===== 2. 数据：回填与变宽唯一键成立 =====
UNION ALL SELECT 'D01 文件全部 AVAILABLE / ACTIVE（回填后全为 CURRENT）', COUNT(*), 0 FROM mor_platform.t_file_object
  WHERE scan_status <> 'AVAILABLE' OR object_status <> 'ACTIVE'
UNION ALL SELECT 'D02 文件引用的策略修订都存在', COUNT(*), 0 FROM mor_platform.t_file_object f
  WHERE NOT EXISTS (SELECT 1 FROM mor_platform.t_file_scan_policy p WHERE p.policy_id = f.scan_policy_id)
UNION ALL SELECT 'D03 文件 upload_id 非空者不重复（uk_file_version_upload）', COUNT(*), 0 FROM (
  SELECT upload_id FROM mor_platform.t_file_object WHERE upload_id IS NOT NULL GROUP BY upload_id HAVING COUNT(*) > 1) x
UNION ALL SELECT 'D04 回填目标 version_id=file_id 与现有任何表无关（新表空），file_id 唯一', COUNT(*), 0 FROM (
  SELECT file_id FROM mor_platform.t_file_object GROUP BY file_id HAVING COUNT(*) > 1) x
UNION ALL SELECT 'D05 进行中且未过期的上传', COUNT(*), 0 FROM mor_platform.t_file_upload
  WHERE status IN ('CREATED','UPLOADING') AND expires_time > UTC_TIMESTAMP(6)
UNION ALL SELECT 'D06 没有待执行的 FILE_SCAN 补偿事件（成功即删除；旧事件键 {fileId} 不跨版本）', COUNT(*), 0
  FROM mor_platform.t_compensation_event WHERE event_type = 'FILE_SCAN'
UNION ALL SELECT 'D07 写入路由 3 条：* / client-console-bff / operations-console-bff', COUNT(*), 3 FROM mor_platform.t_file_storage_route
  WHERE source_system IN ('*','client-console-bff','operations-console-bff')
UNION ALL SELECT 'D08 写入路由总数（回填为 PRIVATE 后 (来源, 级别) 仍唯一）', COUNT(*), 3 FROM mor_platform.t_file_storage_route
UNION ALL SELECT 'D09 默认路由 * → MINIO / mor-platform-files / CLIENT_ONBOARDING_FORMAT', COUNT(*), 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
  JOIN mor_platform.t_file_storage_instance i ON i.instance_id = b.instance_id
  WHERE r.source_system = '*' AND i.instance_code = 'MINIO' AND b.bucket_name = 'mor-platform-files'
   AND r.scan_policy_code = 'CLIENT_ONBOARDING_FORMAT'
UNION ALL SELECT 'D10 client-console-bff → S3_TEST / mor-test-platform-files-k7q2', COUNT(*), 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
  JOIN mor_platform.t_file_storage_instance i ON i.instance_id = b.instance_id
  WHERE r.source_system = 'client-console-bff' AND i.instance_code = 'S3_TEST' AND b.bucket_name = 'mor-test-platform-files-k7q2'
UNION ALL SELECT 'D11 operations-console-bff → MINIO / mor-platform-files / CARD_BIN_CSV（route 239087906591948800）', COUNT(*), 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
  WHERE r.route_id = 239087906591948800 AND r.source_system = 'operations-console-bff'
   AND b.bucket_name = 'mor-platform-files' AND r.scan_policy_code = 'CARD_BIN_CSV'
UNION ALL SELECT 'D12 实例 MINIO、S3_TEST 均 ACTIVE', COUNT(*), 2 FROM mor_platform.t_file_storage_instance
  WHERE instance_code IN ('MINIO','S3_TEST') AND status = 'ACTIVE'
UNION ALL SELECT 'D13 两个桶 ACTIVE 且最近检查 OK（回填为 PRIVATE）', COUNT(*), 2 FROM mor_platform.t_file_storage_bucket
  WHERE bucket_name IN ('mor-platform-files','mor-test-platform-files-k7q2') AND status = 'ACTIVE' AND last_check_result = 'OK'
UNION ALL SELECT 'D14 桶总数', COUNT(*), 2 FROM mor_platform.t_file_storage_bucket
UNION ALL SELECT 'D15 每条路由的策略代码都有 ACTIVE 修订', COUNT(*), 0 FROM mor_platform.t_file_storage_route r
  WHERE NOT EXISTS (SELECT 1 FROM mor_platform.t_file_scan_policy p WHERE p.policy_code = r.scan_policy_code AND p.status = 'ACTIVE')
UNION ALL SELECT 'D16 策略修订 2 个：CLIENT_ONBOARDING_FORMAT r1、CARD_BIN_CSV r1 均 ACTIVE', COUNT(*), 2 FROM mor_platform.t_file_scan_policy
  WHERE (policy_code, revision_no, status) IN (('CLIENT_ONBOARDING_FORMAT',1,'ACTIVE'),('CARD_BIN_CSV',1,'ACTIVE'))
UNION ALL SELECT 'D17 策略允许类型都在格式检查器可识别范围（pdf / png / jpeg / csv）', COUNT(*), 0 FROM mor_platform.t_file_scan_policy p,
  JSON_TABLE(p.allowed_mime_rules, '$.mimeTypes[*]' COLUMNS (mime VARCHAR(255) PATH '$')) m
  WHERE m.mime NOT IN ('application/pdf','image/png','image/jpeg','text/csv')
UNION ALL SELECT 'D18 每个策略修订恰好一条 PLATFORM_FORMAT / FORMAT 检查要求', COUNT(*), 0 FROM mor_platform.t_file_scan_policy p
  WHERE (SELECT COUNT(*) FROM mor_platform.t_file_scan_requirement q WHERE q.policy_id = p.policy_id
          AND q.scanner_code = 'PLATFORM_FORMAT' AND q.scan_type = 'FORMAT') <> 1

-- ===== 3. Gateway / 控制台注册：块 130 路径未改、块 134 空闲 =====
UNION ALL SELECT 'G01 块 130 写入规则编辑 / 删除仍为旧路径且 ACTIVE', COUNT(*), 2 FROM mor_platform.t_gateway_route
  WHERE route_id IN (900000000000130411, 900000000000130412) AND status = 'ACTIVE'
   AND external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}'
   AND downstream_path_template = '/storage-admin/v1/routes/{sourceSystem}'
UNION ALL SELECT 'G02 块 130 其余路由 ACTIVE（共 12）', COUNT(*), 12 FROM mor_platform.t_gateway_route
  WHERE route_id BETWEEN 900000000000130001 AND 900000000000130999 AND status = 'ACTIVE'
UNION ALL SELECT 'G03 块 134 ID 段在 7 张表均未占用', (
    (SELECT COUNT(*) FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000134001 AND 900000000000134999)
  + (SELECT COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000134001 AND 900000000000134999)
  + (SELECT COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000134001 AND 900000000000134999)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000134001 AND 900000000000134999)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000134001 AND 900000000000134999)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000134001 AND 900000000000134999)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000134001 AND 900000000000134999)), 0
UNION ALL SELECT 'G04 权限代码 FILE_RECORD_* 与资源动作 FILE_RECORD 未占用', COUNT(*), 0 FROM mor_platform.t_permission
  WHERE permission_code IN ('FILE_RECORD_READ','FILE_RECORD_DOWNLOAD') OR resource_type = 'FILE_RECORD'
UNION ALL SELECT 'G05 控制台资源代码未占用', COUNT(*), 0 FROM mor_platform.t_console_resource
  WHERE resource_code IN ('OPERATIONS_FILE_RECORDS','OPERATIONS_FILE_RECORD_DOWNLOAD')
UNION ALL SELECT 'G06 12 个路由代码未占用', COUNT(*), 0 FROM mor_platform.t_gateway_route
  WHERE route_code IN ('operations-file-storage-bucket-update','operations-file-scan-policy-list','operations-file-scan-policy-detail',
    'operations-file-scan-policy-create','operations-file-scan-policy-revision-create','operations-file-scan-policy-revision-update',
    'operations-file-scan-policy-revision-discard','operations-file-scan-policy-activate','operations-file-scan-policy-retire',
    'operations-file-record-list','operations-file-record-detail','operations-file-record-download')
UNION ALL SELECT 'G07 12 个 (方法, 外部路径) 与 2 个新路径 (PUT/DELETE routes/{s}/{l}) 未占用', COUNT(*), 0 FROM mor_platform.t_gateway_route
  WHERE (http_method, external_path_template) IN (
    ('PUT','/api/operations/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}'),
    ('GET','/api/operations/storage-admin/v1/scan-policies'), ('GET','/api/operations/storage-admin/v1/scan-policies/{policyCode}'),
    ('POST','/api/operations/storage-admin/v1/scan-policies'), ('POST','/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions'),
    ('PUT','/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}'),
    ('DELETE','/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}'),
    ('POST','/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}/activation'),
    ('POST','/api/operations/storage-admin/v1/scan-policies/{policyCode}/retirement'),
    ('GET','/api/operations/storage-admin/v1/files'), ('GET','/api/operations/storage-admin/v1/files/{fileId}'),
    ('POST','/api/operations/storage-admin/v1/files/{fileId}/download'),
    ('PUT','/api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}'),
    ('DELETE','/api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}'))
UNION ALL SELECT 'G08 依赖行存在：ops-bff Target、平台配置菜单 562、PLATFORM_ADMIN、FILE_STORAGE_READ/MANAGE', (
    (SELECT COUNT(*) FROM mor_platform.t_gateway_target WHERE target_id = 900000000000000001
       AND target_service_code = 'operations-console-bff' AND status = 'ACTIVE')
  + (SELECT COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id = 900000000000000562)
  + (SELECT COUNT(*) FROM mor_platform.t_role WHERE role_id = 900000000000000510 AND role_code = 'PLATFORM_ADMIN')
  + (SELECT COUNT(*) FROM mor_platform.t_permission WHERE permission_id IN (900000000000130101, 900000000000130102))), 5
UNION ALL SELECT 'G09 平台配置下 sort 85 空闲（文件记录插在存储管理 80 与卡 BIN 库 90 之间）', COUNT(*), 0 FROM mor_platform.t_console_resource
  WHERE parent_resource_id = 900000000000000562 AND sort_order = 85
UNION ALL SELECT 'G10 工作流附件内嵌预览 query_param 127030 仍在', COUNT(*), 1 FROM mor_platform.t_gateway_route_query_param
  WHERE route_query_param_id = 900000000000127030;

-- ===== 4. 记录用（无期望值） =====
SELECT 'R01 文件行数' AS item, COUNT(*) AS value FROM mor_platform.t_file_object
UNION ALL SELECT 'R02 上传行数', COUNT(*) FROM mor_platform.t_file_upload
UNION ALL SELECT 'R03 未完成上传（含已过期未收尾）', COUNT(*) FROM mor_platform.t_file_upload WHERE status <> 'COMPLETED'
UNION ALL SELECT 'R04 扫描结果行数', COUNT(*) FROM mor_platform.t_file_scan_result
UNION ALL SELECT 'R05 PLATFORM_ADMIN 角色版本', version FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL SELECT 'R06 块 130 两条路由的版本和', SUM(version) FROM mor_platform.t_gateway_route
  WHERE route_id IN (900000000000130411, 900000000000130412)
UNION ALL SELECT 'R07 FILE_DOWNLOAD_AUTHORIZE 审计条数', COUNT(*) FROM mor_platform.t_audit_event WHERE action_code = 'FILE_DOWNLOAD_AUTHORIZE'
UNION ALL SELECT 'R08 存储管理审计条数（OPERATIONS_FILE_STORAGE%）', COUNT(*) FROM mor_platform.t_audit_event
  WHERE action_code LIKE 'OPERATIONS\_FILE\_STORAGE%';
SELECT source_system, reason_code, storage_container_ref, scan_status, COUNT(*) AS files
  FROM mor_platform.t_file_object GROUP BY source_system, reason_code, storage_container_ref, scan_status
  ORDER BY source_system, reason_code, storage_container_ref;
