-- 文件多存储并存第二段 · mor-test 执行前只读核对（阶段 7 第 1、2 步；root；库名全限定，不写任何数据）。
-- 期望值写在每行 expect 列；任何一行 actual ≠ expect 即停止，查明是否有人改过 mor-test。
SET NAMES utf8mb4;
SELECT 'CCT t_application_document 尚无 file_address' AS item, COUNT(*) AS actual, 0 AS expect
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document' AND COLUMN_NAME='file_address'
UNION ALL SELECT 'CCT file_id 仍为 NOT NULL', COUNT(*), 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND COLUMN_NAME='file_id' AND IS_NULLABLE='NO'
UNION ALL SELECT 'CCT 旧唯一键 uk_app_doc_client/partner/entity 存在', COUNT(DISTINCT INDEX_NAME), 3
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND INDEX_NAME IN ('uk_app_doc_client','uk_app_doc_partner','uk_app_doc_entity')
UNION ALL SELECT 'CCT 新唯一键 *_addr 不存在', COUNT(DISTINCT INDEX_NAME), 0
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND INDEX_NAME LIKE 'uk\_app\_doc\_%\_addr'
UNION ALL SELECT 'CCT 材料行数', COUNT(*), 52 FROM mor_client_master.t_application_document
UNION ALL SELECT 'CCT 材料引用的文件全部在 Platform', COUNT(*), 0 FROM mor_client_master.t_application_document d
  WHERE NOT EXISTS (SELECT 1 FROM mor_platform.t_file_object f WHERE f.file_id = d.file_id)
UNION ALL SELECT 'Platform t_file_upload 三个 owner 列仍在', COUNT(*), 3
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_upload'
   AND COLUMN_NAME IN ('owner_system','owner_type','owner_ref_id')
UNION ALL SELECT 'Platform t_file_owner_ref 存在', COUNT(*), 1
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_owner_ref'
UNION ALL SELECT 'Platform t_file_owner_ref 行数', COUNT(*), 55 FROM mor_platform.t_file_owner_ref
UNION ALL SELECT 'Platform 上传行数', COUNT(*), 55 FROM mor_platform.t_file_upload
UNION ALL SELECT 'Platform 文件行数', COUNT(*), 55 FROM mor_platform.t_file_object
UNION ALL SELECT '文件全部 AVAILABLE 且 ACTIVE', COUNT(*), 0 FROM mor_platform.t_file_object
  WHERE scan_status <> 'AVAILABLE' OR object_status <> 'ACTIVE'
UNION ALL SELECT '进行中的上传', COUNT(*), 0 FROM mor_platform.t_file_upload WHERE status <> 'COMPLETED'
UNION ALL SELECT '默认写入路由 * → MINIO / mor-platform-files ACTIVE', COUNT(*), 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
  JOIN mor_platform.t_file_storage_instance i ON i.instance_id = b.instance_id
  WHERE r.source_system = '*' AND i.instance_code = 'MINIO' AND i.status = 'ACTIVE'
   AND b.bucket_name = 'mor-platform-files' AND b.status = 'ACTIVE'
UNION ALL SELECT '没有 client-console-bff 的精确路由（走默认路由）', COUNT(*), 0
  FROM mor_platform.t_file_storage_route WHERE source_system = 'client-console-bff'
UNION ALL SELECT 'mor_platform 表数', COUNT(*), 63 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'
UNION ALL SELECT '入驻审核证据引用的文件', COUNT(DISTINCT e.file_id), 52
  FROM mor_compliance.t_dd_evidence_ref e JOIN mor_platform.t_file_object f ON f.file_id = e.file_id
UNION ALL SELECT 'Client 入驻文件 4 条 Gateway 路由 ACTIVE', COUNT(*), 4 FROM mor_platform.t_gateway_route
  WHERE external_path_template LIKE '/api/client/onboarding-files/v1/%' AND status = 'ACTIVE'
UNION ALL SELECT 'O10 审核下载路由 ACTIVE', COUNT(*), 1 FROM mor_platform.t_gateway_route
  WHERE external_path_template = '/api/operations/client-onboarding-admin/v1/cases/{caseId}/files/{fileId}/download' AND status = 'ACTIVE'
UNION ALL SELECT '新审计动作 FILE_DOWNLOAD_AUTHORIZE 尚无记录', COUNT(*), 0 FROM mor_platform.t_audit_event
  WHERE action_code = 'FILE_DOWNLOAD_AUTHORIZE';
-- 记录用（无期望值）
SELECT a.status AS item, COUNT(*) AS value FROM mor_client_master.t_client_application a GROUP BY a.status
UNION ALL SELECT 'OPERATIONS_REVIEW_FILE_DOWNLOAD 审计条数', COUNT(*) FROM mor_platform.t_audit_event
  WHERE action_code = 'OPERATIONS_REVIEW_FILE_DOWNLOAD'
UNION ALL SELECT 'CCT 材料的不同 file_id', COUNT(DISTINCT file_id) FROM mor_client_master.t_application_document;
